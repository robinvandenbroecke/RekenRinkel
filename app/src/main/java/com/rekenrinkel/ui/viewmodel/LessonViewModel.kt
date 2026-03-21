package com.rekenrinkel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.repository.ProfileRepository
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.*
import com.rekenrinkel.domain.model.*
import com.rekenrinkel.domain.model.UserProfile as ProfileModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel voor de nieuwe lesson flow met mastery tracking.
 * Gebruikt LessonEngine voor adaptieve les planning.
 */
class LessonViewModel(
    private val progressRepository: ProgressRepository,
    private val profileRepository: ProfileRepository,
    private val settingsDataStore: SettingsDataStore,
    private val exerciseEngine: ExerciseEngine,
    private val exerciseValidator: ExerciseValidator
) : ViewModel() {

    private val lessonEngine = LessonEngine(exerciseEngine, progressRepository)

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val _navigation = MutableSharedFlow<LessonNavigationEvent>()
    val navigation: SharedFlow<LessonNavigationEvent> = _navigation.asSharedFlow()

    // Timer voor response time tracking
    private var exerciseStartTime: Long = 0

    // PATCH 2 & 5: Harde completion guard - bijhouden welke oefening momenteel verwerkt wordt
    // Dit is een transient guard, geen state. UI state (_uiState) is de bron van waarheid.
    private var currentlyCompletingExerciseId: String? = null
    
    // PATCH 5: Set van definitief afgehandelde oefeningen
    // Dit is extra bescherming tegen dubbele verwerking, maar UI state is leidend.
    private val handledExerciseIds = mutableSetOf<String>()
    
    // PATCH 5: Set van volledig afgeronde oefeningen (DONE + advance)
    // Harde idempotency barrier - eenmaal in deze set = nooit meer verwerken
    private val completedExerciseIds = mutableSetOf<String>()
    
    // NOTE: currentCompletionStage en currentStageExerciseId verwijderd.
    // Gebruik altijd _uiState.value.completionStage en _uiState.value.completionStageExerciseId
    // Deze private vars waren een bron van stale state en semantische verwarring.

    /**
     * Start een nieuwe les
     */
    fun startLesson() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // PATCH 8: Reset guards bij start van nieuwe les
                currentlyCompletingExerciseId = null
                handledExerciseIds.clear()
                completedExerciseIds.clear()
                
                // Haal user profile op
                val profile = profileRepository.getProfile().firstOrNull()
                    ?.let { ProfileModel(name = it.name, age = it.age, theme = it.theme) }
                    ?: ProfileModel() // Default

                val isPremium = settingsDataStore.premiumUnlocked.first()

                // Bouw les met LessonEngine
                val lessonPlan = lessonEngine.buildLesson(profile, isPremium)

                _uiState.update {
                    it.copy(
                        exercises = lessonPlan.exercises,
                        currentIndex = 0,
                        results = emptyList(),
                        isActive = true,
                        isLoading = false,
                        stepState = LessonStepState.SHOWING,
                        lastAnswerCorrect = null,
                        currentPhase = determinePhase(0, lessonPlan),
                        xpEarnedThisLesson = 0,
                        badgesEarnedThisLesson = emptyList(),
                        lessonPlan = lessonPlan,
                        completionStage = CompletionStage.NOT_STARTED,
                        completionStageExerciseId = null,
                        error = null,
                        failureContext = null
                    )
                }

                exerciseStartTime = System.currentTimeMillis()

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Start de timer voor een nieuwe oefening
     */
    fun startExerciseTimer() {
        exerciseStartTime = System.currentTimeMillis()
    }

    // ============ PATCH 1-3: FAIL-SAFE COMPLETION FLOW ============

    /**
     * PATCH 1 & 2: Expliciete completion modes - semantisch onderscheiden
     */
    enum class CompletionMode {
        FEEDBACK_THEN_ADVANCE,  // Toon feedback, wacht, dan door (normale antwoorden, guided practice)
        DIRECT_CONTINUE,        // Geen feedback, direct door (worked example)
        SKIP_ADVANCE            // PATCH 1: Skip met penalty, geen feedback, direct door
    }

    /**
     * PATCH 1-3 & 8: Centrale helper voor het afronden van een oefening - FAIL-SAFE.
     * Alle paden (submit, worked, skip) eindigen hier.
     * Deze functie regelt ook de feedback delay en auto-advance.
     *
     * BELANGRIJK: Bij exceptions gaan we NOOIT meer stil terug naar SHOWING.
     * We gaan naar ERROR state met expliciete failure context.
     */
    /**
     * PATCH 4 & 5: Helper om actuele completion stage te verkrijgen
     * Zorgt dat we altijd de meest recente state gebruiken
     */
    private data class CompletionSnapshot(
        val stage: CompletionStage,
        val exerciseId: String?,
        val isDone: Boolean
    )

    private fun currentCompletionState(): CompletionSnapshot {
        val state = _uiState.value
        return CompletionSnapshot(
            stage = state.completionStage,
            exerciseId = state.completionStageExerciseId,
            isDone = state.completionStage == CompletionStage.DONE
        )
    }

    /**
     * PATCH 4 & 5: Helper om te valideren dat stage bij huidige oefening hoort
     */
    private fun isStageValidForExercise(exerciseId: String): Boolean {
        val completion = currentCompletionState()
        return completion.exerciseId == null || completion.exerciseId == exerciseId
    }

    private fun isCompletionDoneForCurrentExercise(exerciseId: String): Boolean {
        val completion = currentCompletionState()
        return completion.exerciseId == exerciseId && completion.isDone
    }

    private fun markCompletionStage(stage: CompletionStage, exerciseId: String) {
        _uiState.update {
            it.copy(
                completionStage = stage,
                completionStageExerciseId = exerciseId
            )
        }
    }

    private suspend fun finishCurrentExercise(
        result: DetailedExerciseResult,
        mode: CompletionMode,
        feedbackDurationMs: Long = 800
    ) {
        val currentExercise = _uiState.value.currentExercise ?: run {
            handleCompletionFailure("Geen huidige oefening", FailureStage.UNKNOWN)
            return
        }

        // PATCH 5: Strict idempotency - check completedExerciseIds
        if (completedExerciseIds.contains(currentExercise.id)) {
            android.util.Log.w("LessonViewModel", "[COMPLETION] BLOCKED - Exercise ${currentExercise.id} already in completedExerciseIds")
            return
        }

        // PATCH 1-4: Initialize stage for this exercise if not started or stale
        val initialCompletion = currentCompletionState()
        if (initialCompletion.exerciseId != currentExercise.id || initialCompletion.stage == CompletionStage.DONE) {
            android.util.Log.d("LessonViewModel", "[COMPLETION] Initializing stage for ${currentExercise.id}")
            markCompletionStage(CompletionStage.NOT_STARTED, currentExercise.id)
        }
        
        android.util.Log.d(
            "LessonViewModel",
            "[COMPLETION] START exercise=${currentExercise.id}, type=${currentExercise.type}, mode=$mode, initialStage=${initialCompletion.stage}"
        )

        val needsFeedback = mode == CompletionMode.FEEDBACK_THEN_ADVANCE
        val skipMasteryUpdate = mode != CompletionMode.FEEDBACK_THEN_ADVANCE
        val isSkip = mode == CompletionMode.SKIP_ADVANCE
        val isWorkedExample = mode == CompletionMode.DIRECT_CONTINUE

        var outcome: ExerciseOutcome? = null
        var progressUpdateFailed = false
        var rewardsUpdateFailed = false

        try {
            // PATCH 2: Strict stage 1 - NOT_STARTED -> RESULT_LOGGED
            val completion1 = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 1 CHECK: currentStage=${completion1.stage}, target=RESULT_LOGGED")
            
            if (completion1.stage == CompletionStage.NOT_STARTED) {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 1 EXEC: Logging result for ${currentExercise.id}")
                _uiState.update {
                    it.copy(
                        results = it.results + result,
                        completionStage = CompletionStage.RESULT_LOGGED,
                        completionStageExerciseId = currentExercise.id
                    )
                }
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 1 DONE: stage=RESULT_LOGGED")
            } else {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 1 SKIP: already at ${completion1.stage}")
            }

            // PATCH 2: Strict stage 2 - RESULT_LOGGED -> PROGRESS_UPDATED (alleen voor normale flow)
            val completion2 = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 2 CHECK: currentStage=${completion2.stage}, target=PROGRESS_UPDATED, skipMastery=$skipMasteryUpdate")
            
            if (!skipMasteryUpdate && completion2.stage == CompletionStage.RESULT_LOGGED) {
                try {
                    android.util.Log.d("LessonViewModel", "[COMPLETION] Step 2 EXEC: Updating progress for ${currentExercise.id}")
                    val currentProgress = progressRepository.getOrCreateProgress(currentExercise.skillId)
                    outcome = lessonEngine.processExerciseResult(result, currentProgress)
                    progressRepository.updateProgress(outcome.updatedProgress)
                    markCompletionStage(CompletionStage.PROGRESS_UPDATED, currentExercise.id)
                    android.util.Log.d("LessonViewModel", "[COMPLETION] Step 2 DONE: stage=PROGRESS_UPDATED")
                } catch (e: Exception) {
                    progressUpdateFailed = true
                    android.util.Log.e("LessonViewModel", "[COMPLETION] Step 2 FAILED", e)
                    throw CompletionStepFailure(FailureStage.PROGRESS_UPDATE, e)
                }
            } else if (skipMasteryUpdate) {
                val reason = if (isSkip) "skip" else if (isWorkedExample) "worked example" else "direct continue"
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 2 SKIP: mastery update skipped for $reason")
                // PATCH 3-4: Voor skip/worked, ga direct naar READY_TO_ADVANCE
            } else {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 2 SKIP: already at ${completion2.stage}")
            }

            // PATCH 2: Strict stage 3 - PROGRESS_UPDATED -> REWARDS_APPLIED (alleen voor normale flow)
            val completion3 = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 3 CHECK: currentStage=${completion3.stage}, target=REWARDS_APPLIED")
            
            if (!skipMasteryUpdate && completion3.stage == CompletionStage.PROGRESS_UPDATED) {
                try {
                    android.util.Log.d("LessonViewModel", "[COMPLETION] Step 3 EXEC: Applying rewards for ${currentExercise.id}")
                    val safeOutcome = outcome ?: run {
                        val currentProgress = progressRepository.getOrCreateProgress(currentExercise.skillId)
                        lessonEngine.processExerciseResult(result, currentProgress)
                    }
                    outcome = safeOutcome
                    val currentRewards = profileRepository.getRewards()
                    val updatedRewards = currentRewards.addXp(safeOutcome.xpEarned).updateStreak()
                    val newBadges = lessonEngine.checkBadges(safeOutcome, currentRewards, currentExercise.skillId)
                    val finalRewards = newBadges.fold(updatedRewards) { rewards, badge -> rewards.addBadge(badge) }
                    profileRepository.updateRewards(finalRewards)
                    markCompletionStage(CompletionStage.REWARDS_APPLIED, currentExercise.id)
                    android.util.Log.d("LessonViewModel", "[COMPLETION] Step 3 DONE: stage=REWARDS_APPLIED")
                } catch (e: Exception) {
                    rewardsUpdateFailed = true
                    android.util.Log.e("LessonViewModel", "[COMPLETION] Step 3 FAILED", e)
                    throw CompletionStepFailure(FailureStage.REWARD_UPDATE, e)
                }
            } else if (skipMasteryUpdate) {
                val reason = if (isSkip) "skip" else if (isWorkedExample) "worked example" else "direct continue"
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 3 SKIP: rewards skipped for $reason")
            } else {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 3 SKIP: already at ${completion3.stage}")
            }

            // PATCH 2: Strict stage 4 - naar READY_TO_ADVANCE
            val completion4 = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 4 CHECK: currentStage=${completion4.stage}, target=READY_TO_ADVANCE")
            
            // PATCH 3-4: Different paths to READY_TO_ADVANCE based on mode
            val canPrepareAdvance = when {
                skipMasteryUpdate -> completion4.stage == CompletionStage.RESULT_LOGGED  // skip/worked: RESULT_LOGGED -> READY_TO_ADVANCE
                rewardsUpdateFailed -> completion4.stage == CompletionStage.PROGRESS_UPDATED  // partial failure
                else -> completion4.stage == CompletionStage.REWARDS_APPLIED  // normal: REWARDS_APPLIED -> READY_TO_ADVANCE
            }
            
            if (canPrepareAdvance) {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 4 EXEC: Preparing advance for ${currentExercise.id}")
                val xpEarned = if (skipMasteryUpdate) 0 else outcome?.xpEarned ?: 0
                val badges = if (skipMasteryUpdate || outcome == null) {
                    emptyList()
                } else {
                    lessonEngine.checkBadges(outcome, profileRepository.getRewards(), currentExercise.skillId)
                }
                _uiState.update {
                    it.copy(
                        stepState = if (needsFeedback) LessonStepState.FEEDBACK else LessonStepState.ADVANCING,
                        lastAnswerCorrect = result.isCorrect,
                        xpEarnedThisLesson = it.xpEarnedThisLesson + xpEarned,
                        badgesEarnedThisLesson = it.badgesEarnedThisLesson + badges,
                        difficultyChanged = outcome?.let { o -> if (o.difficultyChanged) o.newDifficultyTier else null },
                        completionStage = CompletionStage.READY_TO_ADVANCE,
                        completionStageExerciseId = currentExercise.id
                    )
                }
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 4 DONE: stage=READY_TO_ADVANCE")
            } else if (completion4.stage == CompletionStage.READY_TO_ADVANCE) {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 4 SKIP: already READY_TO_ADVANCE")
            } else {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 4 SKIP: already at ${completion4.stage}")
            }

            // PATCH 2: Strict stage 5 - READY_TO_ADVANCE -> DONE -> advance
            val completion5 = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 5 CHECK: currentStage=${completion5.stage}, target=DONE")
            
            if (completion5.stage == CompletionStage.READY_TO_ADVANCE || completion5.stage == CompletionStage.DONE) {
                // PATCH 2: Only delay for FEEDBACK_THEN_ADVANCE mode - worked example and skip are direct
                if (mode == CompletionMode.FEEDBACK_THEN_ADVANCE && completion5.stage == CompletionStage.READY_TO_ADVANCE) {
                    delay(feedbackDurationMs)
                }
                // PATCH 7: First mark DONE, then advance
                val beforeAdvanceExerciseId = currentExercise.id
                if (completion5.stage != CompletionStage.DONE) {
                    markCompletionStage(CompletionStage.DONE, beforeAdvanceExerciseId)
                }
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 5 EXEC: Advancing from $beforeAdvanceExerciseId")
                // PATCH 7: Add to completedExerciseIds before advance
                completedExerciseIds.add(beforeAdvanceExerciseId)
                advanceToNextExercise()
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 5 DONE: exercise=$beforeAdvanceExerciseId fully completed")
            } else {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 5 SKIP: already at ${completion5.stage}")
            }

            android.util.Log.d("LessonViewModel", "[COMPLETION] FINISH: exercise=${currentExercise.id} completed successfully")
        } catch (e: CompletionStepFailure) {
            handleCompletionFailure(
                e.cause?.message ?: e.message ?: "Onbekende fout",
                e.stage,
                currentExercise
            )
        } catch (e: Exception) {
            android.util.Log.e("LessonViewModel", "[COMPLETION] FAILED: exercise=${currentExercise.id} failed with ${e.message}", e)
            val failureStage = when {
                progressUpdateFailed -> FailureStage.PROGRESS_UPDATE
                rewardsUpdateFailed -> FailureStage.REWARD_UPDATE
                else -> FailureStage.UNKNOWN
            }
            handleCompletionFailure(e.message ?: "Onbekende fout", failureStage, currentExercise)
        }
        // NOTE: finally block removed - reset happens in advanceToNextExercise() for success
        // and in handleCompletionFailure() for errors
    }

    /**
     * PATCH 2 & 8: Fail-safe handler voor completion fouten met expliciete context
     * Zorgt dat de app nooit meer stil blijft hangen
     */
    private fun handleCompletionFailure(
        errorMessage: String,
        stage: FailureStage = FailureStage.UNKNOWN,
        exercise: Exercise? = null
    ) {
        val state = _uiState.value
        val currentExercise = exercise ?: state.currentExercise

        // PATCH 7 & 8: Debug logging
        android.util.Log.e("LessonViewModel", "[FAILURE] Exercise ${currentExercise?.id ?: "unknown"} failed at ${stage.name}: $errorMessage")
        android.util.Log.e("LessonViewModel", "[FAILURE] Stage: ${state.completionStage}, exercise: ${state.completionStageExerciseId}, index: ${state.currentIndex}")

        // PATCH 2 & 6: Maak expliciete failure context met rijke completion state
        // PATCH 6: De failure flags worden geïnferreerd uit failureStage en completionStage
        val progressFailed = stage == FailureStage.PROGRESS_UPDATE || 
            (stage == FailureStage.UNKNOWN && state.completionStage == CompletionStage.RESULT_LOGGED)
        val rewardsFailed = stage == FailureStage.REWARD_UPDATE || 
            (stage == FailureStage.UNKNOWN && state.completionStage == CompletionStage.PROGRESS_UPDATED)
        
        val failureContext = currentExercise?.let {
            FailureContext(
                errorMessage = errorMessage,
                exerciseId = it.id,
                exerciseType = it.type,
                currentIndex = state.currentIndex,
                stage = stage,
                completionStage = state.completionStage,
                completionStageExerciseId = state.completionStageExerciseId,
                resultLogged = state.completionStage >= CompletionStage.RESULT_LOGGED,
                progressUpdated = state.completionStage >= CompletionStage.PROGRESS_UPDATED,
                rewardsApplied = state.completionStage >= CompletionStage.REWARDS_APPLIED,
                // PATCH 6: Expliciete failure flags
                progressFailed = progressFailed,
                rewardsFailed = rewardsFailed
            )
        }

        // PATCH 1: Ga naar ERROR state, niet terug naar SHOWING
        _uiState.update {
            it.copy(
                stepState = LessonStepState.ERROR,
                error = "Oefening afhandeling mislukt in stap ${stage.name}: $errorMessage",
                failureContext = failureContext
            )
        }

        // Reset completion guard zodat recovery mogelijk is
        currentlyCompletingExerciseId = null
    }

    /**
     * PATCH 1 & 8: Helper voor direct naar volgende oefening gaan
     */
    private fun advanceToNextExercise() {
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1
        val currentExercise = state.currentExercise

        // PATCH 2 & 7: Eerst DONE markeren, dan pas advance
        currentExercise?.let { exercise ->
            // PATCH 7: Zorg dat DONE al gezet is in finishCurrentExercise stap 5
            // Hier alleen nog completedExerciseIds en handledExerciseIds toevoegen als extra guard
            if (!completedExerciseIds.contains(exercise.id)) {
                completedExerciseIds.add(exercise.id)
                android.util.Log.d("LessonViewModel", "Exercise ${exercise.id} marked as completed in advanceToNextExercise")
            }
            if (!handledExerciseIds.contains(exercise.id)) {
                handledExerciseIds.add(exercise.id)
                android.util.Log.d("LessonViewModel", "Exercise ${exercise.id} marked as handled in advanceToNextExercise")
            }
        }

        _uiState.update { it.copy(stepState = LessonStepState.ADVANCING) }

        // PATCH 2: Reset completion guard bij advance
        currentlyCompletingExerciseId = null

        // PATCH 8: Debug logging
        android.util.Log.d("LessonViewModel", "Advancing from index ${state.currentIndex} to $nextIndex")

        if (nextIndex >= state.exercises.size) {
            android.util.Log.d("LessonViewModel", "Completing lesson")
            completeLesson()
        } else {
            _uiState.update {
                it.copy(
                    currentIndex = nextIndex,
                    stepState = LessonStepState.SHOWING,
                    lastAnswerCorrect = null,
                    difficultyChanged = null,
                    currentPhase = determinePhase(nextIndex, it),
                    completionStage = CompletionStage.NOT_STARTED,
                    completionStageExerciseId = null
                )
            }
            exerciseStartTime = System.currentTimeMillis()
            android.util.Log.d("LessonViewModel", "Now showing exercise at index $nextIndex")
        }
    }

    // ============ PUBLIEKE API ============

    /**
     * PATCH 1: Verwerk een normaal antwoord
     * Gebruikt de uniforme finishCurrentExercise helper
     */
    fun submitAnswer(answer: String) {
        val state = _uiState.value
        val currentExercise = state.currentExercise ?: return

        // PATCH 5: Hard idempotency - gebruik actuele state als bron van waarheid
        val actualState = _uiState.value
        if (completedExerciseIds.contains(currentExercise.id) || 
            handledExerciseIds.contains(currentExercise.id) ||
            currentlyCompletingExerciseId == currentExercise.id ||
            actualState.stepState == LessonStepState.PROCESSING ||
            actualState.stepState == LessonStepState.ADVANCING ||
            actualState.stepState == LessonStepState.FEEDBACK) {
            android.util.Log.w("LessonViewModel", "submitAnswer ignored - exercise ${currentExercise.id} already processing/completed")
            return
        }

        if (state.stepState != LessonStepState.SHOWING) return

        // PATCH 1 & 5: Set processing state and guard BEFORE coroutine
        currentlyCompletingExerciseId = currentExercise.id
        _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }

        val responseTimeMs = System.currentTimeMillis() - exerciseStartTime
        val exerciseToProcess = currentExercise

        // PATCH 9: Directe inline executie voor stappen 1-4 (geen coroutine nodig)
        val isCorrect = exerciseValidator.validate(exerciseToProcess, answer)

        val result = DetailedExerciseResult(
            exerciseId = exerciseToProcess.id,
            skillId = exerciseToProcess.skillId,
            isCorrect = isCorrect,
            responseTimeMs = responseTimeMs,
            givenAnswer = answer,
            correctAnswer = exerciseToProcess.correctAnswer,
            difficultyTier = exerciseToProcess.difficulty,
            representationUsed = exerciseToProcess.visualData?.type?.name ?: "ABSTRACT",
            errorType = if (!isCorrect) determineErrorType(exerciseToProcess, answer) else null
        )

        // Step 1: Log result
        _uiState.update {
            it.copy(
                results = it.results + result,
                completionStage = CompletionStage.RESULT_LOGGED,
                completionStageExerciseId = exerciseToProcess.id
            )
        }

        // Step 2 & 3: Update progress and rewards (gebruik runBlocking voor suspend functies)
        try {
            kotlinx.coroutines.runBlocking {
                val currentProgress = progressRepository.getOrCreateProgress(exerciseToProcess.skillId)
                val outcome = lessonEngine.processExerciseResult(result, currentProgress)
                progressRepository.updateProgress(outcome.updatedProgress)
                _uiState.update {
                    it.copy(completionStage = CompletionStage.PROGRESS_UPDATED)
                }

                val currentRewards = profileRepository.getRewards()
                val updatedRewards = currentRewards.addXp(outcome.xpEarned).updateStreak()
                val newBadges = lessonEngine.checkBadges(outcome, currentRewards, exerciseToProcess.skillId)
                val finalRewards = newBadges.fold(updatedRewards) { rewards, badge -> rewards.addBadge(badge) }
                profileRepository.updateRewards(finalRewards)
                _uiState.update {
                    it.copy(
                        completionStage = CompletionStage.REWARDS_APPLIED,
                        xpEarnedThisLesson = it.xpEarnedThisLesson + outcome.xpEarned,
                        badgesEarnedThisLesson = it.badgesEarnedThisLesson + newBadges
                    )
                }
            }
        } catch (e: Exception) {
            // Continue even if progress/rewards fail
        }

        // Step 4: Show feedback
        _uiState.update {
            it.copy(
                stepState = LessonStepState.FEEDBACK,
                lastAnswerCorrect = isCorrect,
                completionStage = CompletionStage.READY_TO_ADVANCE,
                completionStageExerciseId = exerciseToProcess.id
            )
        }

        // Gebruik coroutine alleen voor de delay (stap 5)
        viewModelScope.launch {
            delay(800)
            completedExerciseIds.add(exerciseToProcess.id)
            _uiState.update {
                it.copy(completionStage = CompletionStage.DONE)
            }
            advanceToNextExercise()
        }
    }

    /**
     * PATCH 2: WORKED_EXAMPLE - direct en expliciet
     * Geen validatie, geen feedback-overlay, direct advance
     */
    fun continueWorkedExample() {
        val state = _uiState.value
        val currentExercise = state.currentExercise ?: return

        // PATCH 5: Hard idempotency - gebruik actuele state als bron van waarheid
        val actualState = _uiState.value
        if (completedExerciseIds.contains(currentExercise.id) || 
            handledExerciseIds.contains(currentExercise.id) ||
            currentlyCompletingExerciseId == currentExercise.id ||
            actualState.stepState == LessonStepState.PROCESSING ||
            actualState.stepState == LessonStepState.ADVANCING ||
            actualState.stepState == LessonStepState.FEEDBACK) {
            android.util.Log.w("LessonViewModel", "continueWorkedExample ignored - exercise ${currentExercise.id} already processing/completed")
            return
        }

        // Guard: alleen voor WORKED_EXAMPLE
        if (currentExercise.type != com.rekenrinkel.domain.model.ExerciseType.WORKED_EXAMPLE) {
            // Als het per ongeluk een ander type is, behandel als normale oefening
            submitAnswer("[worked_fallback]")
            return
        }

        if (state.stepState != LessonStepState.SHOWING) return

        // PATCH 5: Set guard BEFORE processing
        currentlyCompletingExerciseId = currentExercise.id
        _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }

        val exerciseToProcess = currentExercise

        // PATCH 9: Directe inline executie voor DIRECT_CONTINUE (geen delay, geen coroutine nodig)
        // Deze flow is result-only: RESULT_LOGGED -> READY_TO_ADVANCE -> DONE -> advance
        val result = DetailedExerciseResult(
            exerciseId = exerciseToProcess.id,
            skillId = exerciseToProcess.skillId,
            isCorrect = true,
            responseTimeMs = System.currentTimeMillis() - exerciseStartTime,
            givenAnswer = "[worked_example_viewed]",
            correctAnswer = exerciseToProcess.correctAnswer,
            difficultyTier = exerciseToProcess.difficulty,
            representationUsed = "WORKED_EXAMPLE"
        )

        // Step 1: Log result
        _uiState.update {
            it.copy(
                results = it.results + result,
                completionStage = CompletionStage.RESULT_LOGGED,
                completionStageExerciseId = exerciseToProcess.id
            )
        }

        // Step 4: Prepare advance (skip mastery update for worked example)
        _uiState.update {
            it.copy(
                stepState = LessonStepState.ADVANCING,
                lastAnswerCorrect = true,
                completionStage = CompletionStage.READY_TO_ADVANCE,
                completionStageExerciseId = exerciseToProcess.id
            )
        }

        // Step 5: Mark DONE and advance
        completedExerciseIds.add(exerciseToProcess.id)
        _uiState.update {
            it.copy(completionStage = CompletionStage.DONE)
        }
        advanceToNextExercise()
    }

    /**
     * PATCH 6: Sla oefening over - eenvoudig en niet-blokkerend
     * Geen feedback, direct door naar volgende oefening
     */
    fun skipExercise() {
        val state = _uiState.value
        val currentExercise = state.currentExercise ?: return

        // PATCH 5: Hard idempotency - gebruik actuele state als bron van waarheid
        val actualState = _uiState.value
        if (completedExerciseIds.contains(currentExercise.id) || 
            handledExerciseIds.contains(currentExercise.id) ||
            currentlyCompletingExerciseId == currentExercise.id ||
            actualState.stepState == LessonStepState.PROCESSING ||
            actualState.stepState == LessonStepState.ADVANCING ||
            actualState.stepState == LessonStepState.FEEDBACK) {
            android.util.Log.w("LessonViewModel", "skipExercise ignored - exercise ${currentExercise.id} already processing/completed")
            return
        }

        if (state.stepState != LessonStepState.SHOWING) return

        // PATCH 5: Set guard BEFORE processing
        currentlyCompletingExerciseId = currentExercise.id
        _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }

        val exerciseToProcess = currentExercise

        // PATCH 9: Directe inline executie voor SKIP_ADVANCE (geen delay, geen coroutine nodig)
        // Deze flow is result-only: RESULT_LOGGED -> READY_TO_ADVANCE -> DONE -> advance
        val result = DetailedExerciseResult(
            exerciseId = exerciseToProcess.id,
            skillId = exerciseToProcess.skillId,
            isCorrect = false,
            responseTimeMs = 30_000,
            givenAnswer = "[skipped]",
            correctAnswer = exerciseToProcess.correctAnswer,
            difficultyTier = exerciseToProcess.difficulty,
            representationUsed = "SKIPPED"
        )

        // Step 1: Log result
        _uiState.update {
            it.copy(
                results = it.results + result,
                completionStage = CompletionStage.RESULT_LOGGED,
                completionStageExerciseId = exerciseToProcess.id
            )
        }

        // Step 4: Prepare advance (skip mastery update for skip)
        _uiState.update {
            it.copy(
                stepState = LessonStepState.ADVANCING,
                lastAnswerCorrect = false,
                completionStage = CompletionStage.READY_TO_ADVANCE,
                completionStageExerciseId = exerciseToProcess.id
            )
        }

        // Step 5: Mark DONE and advance
        completedExerciseIds.add(exerciseToProcess.id)
        _uiState.update {
            it.copy(completionStage = CompletionStage.DONE)
        }
        advanceToNextExercise()
    }

    /**
     * PATCH 4 & 6: Verder gaan na een fout - context-aware recovery
     * Gebruikt failure context om veilige recovery te bepalen
     */
    fun continueAfterError() {
        val state = _uiState.value
        val failureContext = state.failureContext
        val completion = currentCompletionState()
        val currentExercise = state.currentExercise

        android.util.Log.d("LessonViewModel", "continueAfterError called")
        android.util.Log.d("LessonViewModel", "Failure stage: ${failureContext?.stage?.name ?: "null"}")
        android.util.Log.d("LessonViewModel", "Completion stage: ${completion.stage} (exercise: ${completion.exerciseId})")
        android.util.Log.d("LessonViewModel", "Exercise type: ${failureContext?.exerciseType}")

        if (currentExercise == null) {
            currentlyCompletingExerciseId = null
            viewModelScope.launch { _navigation.emit(LessonNavigationEvent.BackToHome) }
            return
        }

        // PATCH 5 & 6: Guard against already completed, handled, or currently processing exercises
        if (completedExerciseIds.contains(currentExercise.id)) {
            android.util.Log.w("LessonViewModel", "continueAfterError ignored - exercise ${currentExercise.id} already in completedExerciseIds")
            return
        }
        if (handledExerciseIds.contains(currentExercise.id)) {
            android.util.Log.w("LessonViewModel", "continueAfterError ignored - exercise ${currentExercise.id} already handled")
            return
        }
        // PATCH 6: If currently processing, wait - don't start recovery yet
        if (currentlyCompletingExerciseId == currentExercise.id) {
            android.util.Log.w("LessonViewModel", "continueAfterError ignored - exercise ${currentExercise.id} currently being processed")
            return
        }

        val recoveryStage = if (completion.exerciseId == currentExercise.id) completion.stage else CompletionStage.NOT_STARTED
        val currentExerciseId = currentExercise.id
        android.util.Log.d("LessonViewModel", "Effective recovery stage: $recoveryStage")

        viewModelScope.launch {
            // PATCH 6: Set guard at start of recovery
            currentlyCompletingExerciseId = currentExercise.id
            
            when (recoveryStage) {
                CompletionStage.NOT_STARTED -> {
                    // PATCH 6: Recovery contract: log exact één recovery-result en ga veilig door zonder progress/rewards.
                    // Use actual current state, not stale snapshot
                    val actualState = _uiState.value
                    val alreadyLogged = actualState.results.any { it.exerciseId == currentExerciseId } || 
                                       handledExerciseIds.contains(currentExerciseId)
                    if (!alreadyLogged) {
                        val recoveryResult = DetailedExerciseResult(
                            exerciseId = currentExercise.id,
                            skillId = currentExercise.skillId,
                            isCorrect = false,
                            responseTimeMs = System.currentTimeMillis() - exerciseStartTime,
                            givenAnswer = "[recovery_not_started]",
                            correctAnswer = currentExercise.correctAnswer,
                            difficultyTier = currentExercise.difficulty,
                            representationUsed = "RECOVERY"
                        )
                        // PATCH 6 & 7: Eerst result loggen, dan DONE zetten + completed + handled, dan advance
                        completedExerciseIds.add(currentExercise.id)
                        handledExerciseIds.add(currentExercise.id)
                        _uiState.update {
                            it.copy(
                                results = it.results + recoveryResult,
                                completionStage = CompletionStage.DONE,
                                completionStageExerciseId = currentExercise.id,
                                stepState = LessonStepState.ADVANCING,
                                error = null,
                                failureContext = null,
                                lastAnswerCorrect = false
                            )
                        }
                    } else {
                        // PATCH 6: Al gelogd, zet alleen DONE + completed + handled
                        completedExerciseIds.add(currentExercise.id)
                        handledExerciseIds.add(currentExercise.id)
                        _uiState.update {
                            it.copy(
                                stepState = LessonStepState.ADVANCING,
                                error = null,
                                failureContext = null,
                                completionStage = CompletionStage.DONE,
                                completionStageExerciseId = currentExercise.id
                            )
                        }
                    }
                    advanceToNextExercise()
                }
                CompletionStage.RESULT_LOGGED,
                CompletionStage.PROGRESS_UPDATED,
                CompletionStage.REWARDS_APPLIED -> {
                    // PATCH 6: Geen side-effects meer opnieuw uitvoeren. Eerst DONE zetten + completed + handled, dan advance.
                    completedExerciseIds.add(currentExercise.id)
                    handledExerciseIds.add(currentExercise.id)
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.ADVANCING,
                            error = null,
                            failureContext = null,
                            completionStage = CompletionStage.DONE,
                            completionStageExerciseId = currentExercise.id
                        )
                    }
                    advanceToNextExercise()
                }
                CompletionStage.READY_TO_ADVANCE -> {
                    // PATCH 6 & 7: Alleen hier expliciet finaliseren. Eerst DONE + completed + handled, dan advance.
                    completedExerciseIds.add(currentExercise.id)
                    handledExerciseIds.add(currentExercise.id)
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.ADVANCING,
                            error = null,
                            failureContext = null,
                            completionStage = CompletionStage.DONE,
                            completionStageExerciseId = currentExercise.id
                        )
                    }
                    advanceToNextExercise()
                }
                CompletionStage.DONE -> {
                    // PATCH 6 & 7: Al afgerond, alleen nog advance als dat niet al gebeurd is
                    completedExerciseIds.add(currentExercise.id)
                    handledExerciseIds.add(currentExercise.id)
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.ADVANCING,
                            error = null,
                            failureContext = null,
                            completionStage = CompletionStage.DONE,
                            completionStageExerciseId = currentExercise.id
                        )
                    }
                    advanceToNextExercise()
                }
            }
        }
    }

    /**
     * PATCH 3, 6 & 7: Bepaal veilige recovery actie op basis van completionStage (bron van waarheid)
     * 
     * Recovery logica per completionStage:
     * - NOT_STARTED: Oefening nog niet begonnen → RETRY_CURRENT of SKIP_TO_NEXT
     * - RESULT_LOGGED: Resultaat al gelogd → niet opnieuw loggen, CONTINUE_REMAINING_STEPS
     * - PROGRESS_UPDATED: Progress al geupdate → rewards/advance verder afwerken
     * - REWARDS_APPLIED: Rewards al toegepast → direct ADVANCE_TO_NEXT
     * - READY_TO_ADVANCE: Al klaar om door te gaan → direct ADVANCE_TO_NEXT
     * - DONE: Al afgerond → niets meer doen (CHECK_LESSON_COMPLETE)
     */
    private fun determineRecoveryAction(
        failureContext: FailureContext?,
        completionStage: CompletionStage
    ): RecoveryAction {
        val stage = failureContext?.stage ?: FailureStage.UNKNOWN
        val exerciseType = failureContext?.exerciseType

        // PATCH 8: Log recovery decision context
        android.util.Log.d("LessonViewModel", "[RECOVERY] Deciding action for stage=$completionStage, failureStage=$stage, type=$exerciseType")

        // PATCH 3 & 7: Stage-gebaseerde recovery als primaire logica - semantisch veilig
        val action = when (completionStage) {
            CompletionStage.DONE -> {
                // PATCH 3: DONE betekent: oefening volledig afgehandeld, GEEN side effects meer
                // Alleen veilige advance naar volgende oefening, geen writes herhalen
                android.util.Log.d("LessonViewModel", "[RECOVERY] Stage=DONE → oefening volledig afgerond, alleen veilige advance")
                RecoveryAction.ADVANCE_TO_NEXT
            }
            CompletionStage.READY_TO_ADVANCE -> {
                // PATCH 3: UI staat klaar maar advance is niet gelukt
                // Alleen advance uitvoeren - geen side effects meer mogelijk
                android.util.Log.d("LessonViewModel", "[RECOVERY] Stage=READY_TO_ADVANCE → ADVANCE_TO_NEXT (no side effects)")
                RecoveryAction.ADVANCE_TO_NEXT
            }
            CompletionStage.REWARDS_APPLIED -> {
                // PATCH 3: Rewards zijn al toegepast, XP/stars al gegeven
                // UI update en advance doen, geen dubbele rewards mogelijk
                android.util.Log.d("LessonViewModel", "[RECOVERY] Stage=REWARDS_APPLIED → ADVANCE_TO_NEXT (rewards already applied)")
                RecoveryAction.ADVANCE_TO_NEXT
            }
            CompletionStage.PROGRESS_UPDATED -> {
                // PATCH 7: Progress is geupdate maar rewards mogelijk niet
                // Dit is een gedegradeerde state - bepaal actie obv exercise type
                android.util.Log.d("LessonViewModel", "[RECOVERY] Stage=PROGRESS_UPDATED → progress done, rewards pending")
                // PATCH 7: Worked example heeft geen rewards nodig
                if (exerciseType == ExerciseType.WORKED_EXAMPLE) {
                    android.util.Log.d("LessonViewModel", "[RECOVERY] WORKED_EXAMPLE at PROGRESS_UPDATED → direct ADVANCE_TO_NEXT")
                    RecoveryAction.ADVANCE_TO_NEXT
                } else {
                    android.util.Log.d("LessonViewModel", "[RECOVERY] NORMAL at PROGRESS_UPDATED → CONTINUE_REMAINING_STEPS for rewards")
                    RecoveryAction.CONTINUE_REMAINING_STEPS
                }
            }
            CompletionStage.RESULT_LOGGED -> {
                // PATCH 7: Resultaat is gelogd maar progress/rewards niet
                // Dit is een gedegradeerde state - bepaal actie obv exercise type
                android.util.Log.d("LessonViewModel", "[RECOVERY] Stage=RESULT_LOGGED → result logged, progress/rewards pending")
                // PATCH 7: Worked example heeft geen progress/rewards nodig
                if (exerciseType == ExerciseType.WORKED_EXAMPLE) {
                    android.util.Log.d("LessonViewModel", "[RECOVERY] WORKED_EXAMPLE at RESULT_LOGGED → direct ADVANCE_TO_NEXT")
                    RecoveryAction.ADVANCE_TO_NEXT
                } else {
                    android.util.Log.d("LessonViewModel", "[RECOVERY] NORMAL at RESULT_LOGGED → CONTINUE_REMAINING_STEPS")
                    RecoveryAction.CONTINUE_REMAINING_STEPS
                }
            }
            CompletionStage.NOT_STARTED -> {
                // PATCH 3: Oefening nog niet begonnen, meeste opties open
                when (exerciseType) {
                    ExerciseType.WORKED_EXAMPLE -> {
                        // Worked example: geen retry nodig, altijd door
                        android.util.Log.d("LessonViewModel", "[RECOVERY] Stage=NOT_STARTED, type=WORKED → ADVANCE_TO_NEXT")
                        RecoveryAction.ADVANCE_TO_NEXT
                    }
                    else -> {
                        // Bij NOT_STARTED en een error: waarschijnlijk ging iets mis voor we begonnen
                        when (stage) {
                            FailureStage.RESULT_LOGGING -> {
                                android.util.Log.d("LessonViewModel", "[RECOVERY] Stage=NOT_STARTED, failure=RESULT_LOGGING → SKIP_TO_NEXT")
                                RecoveryAction.SKIP_TO_NEXT
                            }
                            FailureStage.UNKNOWN -> {
                                android.util.Log.d("LessonViewModel", "[RECOVERY] Stage=NOT_STARTED, failure=UNKNOWN → RETRY_CURRENT")
                                RecoveryAction.RETRY_CURRENT
                            }
                            else -> {
                                android.util.Log.d("LessonViewModel", "[RECOVERY] Stage=NOT_STARTED, failure=$stage → CONTINUE_REMAINING_STEPS")
                                RecoveryAction.CONTINUE_REMAINING_STEPS
                            }
                        }
                    }
                }
            }
        }
        
        android.util.Log.d("LessonViewModel", "[RECOVERY] Final action: $action")
        return action
    }

    /**
     * PATCH 6 & 7: Mogelijke recovery acties - semantisch veilig
     */
    enum class RecoveryAction {
        RETRY_CURRENT,           // PATCH 7: Probeer huidige opnieuw (alleen als NOT_STARTED)
        CONTINUE_REMAINING_STEPS, // PATCH 7: Voltooi huidige (result al gelogd, doe rest)
        ADVANCE_TO_NEXT,         // PATCH 7: Ga naar volgende (geen side effects meer)
        COMPLETE_ADVANCE,        // PATCH 3: DONE was bereikt, advance voltooien
        SKIP_TO_NEXT,            // Skip huidige en ga door (als retry niet veilig is)
        COMPLETE_LESSON,         // PATCH 7: Les voltooien (als alles DONE is)
        RETURN_HOME              // Terug naar home (meest veilige fallback)
    }

    /**
     * PATCH 6: Voltooi de les met expliciete state
     */
    private fun completeLesson() {
        viewModelScope.launch {
            val state = _uiState.value

            // PATCH 6: Markeer als completed
            _uiState.update { it.copy(stepState = LessonStepState.COMPLETED) }

            val averageResponseTime = if (state.results.isNotEmpty()) {
                state.results.map { it.responseTimeMs }.average().toLong()
            } else 0

            val accuracy = state.results.accuracy()
            val stars = when {
                accuracy >= 0.9 -> 3
                accuracy >= 0.7 -> 2
                accuracy >= 0.5 -> 1
                else -> 0
            }

            val sessionResult = SessionResult(
                exercises = state.results.map { it.toExerciseResult() },
                xpEarned = state.xpEarnedThisLesson,
                stars = stars,
                averageResponseTimeMs = averageResponseTime
            )

            _navigation.emit(LessonNavigationEvent.LessonComplete(
                result = sessionResult,
                badges = state.badgesEarnedThisLesson,
                xpTotal = state.xpEarnedThisLesson
            ))
        }
    }

    /**
     * Sla les over
     */
    fun skipLesson() {
        viewModelScope.launch {
            _navigation.emit(LessonNavigationEvent.BackToHome)
        }
    }

    // ============ PRIVATE HELPERS ============

    private fun determinePhase(index: Int, state: LessonUiState): LessonPhase {
        val plan = state.lessonPlan ?: return LessonPhase.FOCUS
        return when {
            index < plan.warmUpCount -> LessonPhase.WARM_UP
            index < plan.warmUpCount + plan.focusCount -> LessonPhase.FOCUS
            index < plan.warmUpCount + plan.focusCount + plan.reviewCount -> LessonPhase.REVIEW
            else -> LessonPhase.CHALLENGE
        }
    }

    private fun determinePhase(index: Int, plan: LessonPlan): LessonPhase {
        return when {
            index < plan.warmUpCount -> LessonPhase.WARM_UP
            index < plan.warmUpCount + plan.focusCount -> LessonPhase.FOCUS
            index < plan.warmUpCount + plan.focusCount + plan.reviewCount -> LessonPhase.REVIEW
            else -> LessonPhase.CHALLENGE
        }
    }

    private fun determineErrorType(exercise: Exercise, answer: String): String {
        return when {
            answer.isBlank() -> "NO_ANSWER"
            answer.toIntOrNull() == null -> "NOT_A_NUMBER"
            exercise.type == ExerciseType.VISUAL_GROUPS -> "WRONG_SPLIT"
            else -> "WRONG_ANSWER"
        }
    }

    /**
     * PATCH 4: Helper om alleen resterende stappen te voltooien - VEILIG
     * Wordt gebruikt door recovery als result al gelogd is
     * CHECKT ELKE STAP of deze al gedaan is - GEEN dubbele side effects
     */
    private suspend fun completeRemainingSteps(exercise: Exercise, initialState: LessonUiState) {
        android.util.Log.d("LessonViewModel", "[RECOVERY] completeRemainingSteps for ${exercise.id}, initial stage: ${initialState.completionStage}")
        
        // PATCH 4: Eerst checken of we niet al READY_TO_ADVANCE of DONE zijn
        if (initialState.completionStage >= CompletionStage.READY_TO_ADVANCE) {
            android.util.Log.d("LessonViewModel", "[RECOVERY] Already READY_TO_ADVANCE or DONE, skipping to advance")
            advanceToNextExercise()
            return
        }
        
        try {
            // PATCH 4: Stap 2 - Progress update (ALLEEN als RESULT_LOGGED maar nog geen PROGRESS_UPDATED)
            val stateBeforeStep2 = _uiState.value
            if (stateBeforeStep2.completionStage == CompletionStage.RESULT_LOGGED) {
                android.util.Log.d("LessonViewModel", "[RECOVERY] Step 2 NEEDED: Progress update required")
                try {
                    android.util.Log.d("LessonViewModel", "[RECOVERY] Step 2: Updating progress")
                    val lastResult = initialState.results.lastOrNull()
                    if (lastResult != null) {
                        val currentProgress = progressRepository.getOrCreateProgress(exercise.skillId)
                        val outcome = lessonEngine.processExerciseResult(lastResult, currentProgress)
                        progressRepository.updateProgress(outcome.updatedProgress)
                        _uiState.update { it.copy(completionStage = CompletionStage.PROGRESS_UPDATED) }
                        android.util.Log.d("LessonViewModel", "[RECOVERY] Step 2: Progress updated")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LessonViewModel", "[RECOVERY] Step 2 FAILED: ${e.message}")
                    // Ga door naar advance
                }
            }
            
            // PATCH 4: Stap 3 - Rewards (ALLEEN als PROGRESS_UPDATED maar nog geen REWARDS_APPLIED)
            val stateBeforeStep3 = _uiState.value
            if (stateBeforeStep3.completionStage == CompletionStage.PROGRESS_UPDATED) {
                try {
                    android.util.Log.d("LessonViewModel", "[RECOVERY] Step 3: Applying rewards")
                    val lastResult = initialState.results.lastOrNull()
                    if (lastResult != null) {
                        val currentProgress = progressRepository.getOrCreateProgress(exercise.skillId)
                        val outcome = lessonEngine.processExerciseResult(lastResult, currentProgress)
                        val currentRewards = profileRepository.getRewards()
                        val updatedRewards = currentRewards.addXp(outcome.xpEarned).updateStreak()
                        val newBadges = lessonEngine.checkBadges(outcome, currentRewards, exercise.skillId)
                        val finalRewards = newBadges.fold(updatedRewards) { r, b -> r.addBadge(b) }
                        profileRepository.updateRewards(finalRewards)
                        _uiState.update { it.copy(completionStage = CompletionStage.REWARDS_APPLIED) }
                        android.util.Log.d("LessonViewModel", "[RECOVERY] Step 3: Rewards applied")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LessonViewModel", "[RECOVERY] Step 3 FAILED: ${e.message}")
                    // Ga door naar advance
                }
            }
            
            // PATCH 4: Stap 4 - Prepare advance (ALLEEN als REWARDS_APPLIED maar nog geen READY_TO_ADVANCE)
            val stateBeforeStep4 = _uiState.value
            if (stateBeforeStep4.completionStage == CompletionStage.REWARDS_APPLIED) {
                android.util.Log.d("LessonViewModel", "[RECOVERY] Step 4: Preparing advance")
                _uiState.update { 
                    it.copy(
                        stepState = LessonStepState.ADVANCING,
                        completionStage = CompletionStage.READY_TO_ADVANCE
                    )
                }
            }
            
            // Stap 5: Advance
            android.util.Log.d("LessonViewModel", "[RECOVERY] Step 5: Advancing")
            advanceToNextExercise()
            android.util.Log.d("LessonViewModel", "[RECOVERY] completeRemainingSteps finished successfully")
            
        } catch (e: Exception) {
            android.util.Log.e("LessonViewModel", "[RECOVERY] completeRemainingSteps failed: ${e.message}")
            handleCompletionFailure("Recovery failed: ${e.message}", FailureStage.UNKNOWN, exercise)
        }
    }
}

/**
 * PATCH 1: Expliciete lesstep state met ERROR
 */
enum class LessonStepState {
    SHOWING,      // Item wordt getoond, wacht op input
    PROCESSING,   // Bezig met verwerken
    FEEDBACK,     // Feedback wordt getoond
    ADVANCING,    // Bezig met naar volgende gaan
    ERROR,        // PATCH 1: Fout tijdens afhandeling - expliciete interruptiestatus
    COMPLETED     // Les voltooid
}

/**
 * PATCH 1: Expliciete completion stage voor veilige recovery
 * Deze stages vormen een lineaire keten van afhandeling.
 * Elke stage impliceert dat alle vorige succesvol waren.
 */
enum class CompletionStage {
    NOT_STARTED,        // Nog niet begonnen met afhandeling
    RESULT_LOGGED,      // Resultaat is gelogd (lokaal in state)
    PROGRESS_UPDATED,   // Progress/mastery is geupdate in repository
    REWARDS_APPLIED,    // Rewards zijn toegepast
    READY_TO_ADVANCE,   // UI is klaar, mag naar volgende
    DONE                // Volledig afgerond, exercise mag vergeten worden
}

/**
 * PATCH 2: Expliciete failure stages voor semantisch veilige recovery
 */
enum class FailureStage {
    RESULT_LOGGING,    // Fout bij aanmaken resultaat
    PROGRESS_UPDATE,   // Fout bij progress/mastery update
    REWARD_UPDATE,     // Fout bij rewards/badges update
    STATE_UPDATE,      // Fout bij UI state update
    ADVANCE,           // Fout bij advance naar volgende oefening
    UNKNOWN            // Onbekende fase
}

/**
 * PATCH 2 & 6: Failure context voor veilige recovery - uitgebreid met completion state
 */
private class CompletionStepFailure(
    val stage: FailureStage,
    cause: Throwable
) : RuntimeException(cause)

data class FailureContext(
    val errorMessage: String,
    val exerciseId: String,
    val exerciseType: ExerciseType,
    val currentIndex: Int,
    val stage: FailureStage,
    val timestamp: Long = System.currentTimeMillis(),
    // PATCH 6: Rijkere context voor betere recovery
    val completionStage: CompletionStage? = null,
    val completionStageExerciseId: String? = null,
    val resultLogged: Boolean = false,
    val progressUpdated: Boolean = false,
    val rewardsApplied: Boolean = false,
    // PATCH 6: Expliciete failure flags voor degraded completion
    val progressFailed: Boolean = false,
    val rewardsFailed: Boolean = false
)

data class LessonUiState(
    val exercises: List<Exercise> = emptyList(),
    val currentIndex: Int = 0,
    val results: List<DetailedExerciseResult> = emptyList(),
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val stepState: LessonStepState = LessonStepState.SHOWING,  // PATCH 1
    val lastAnswerCorrect: Boolean? = null,
    val currentPhase: LessonPhase = LessonPhase.FOCUS,
    val xpEarnedThisLesson: Int = 0,
    val badgesEarnedThisLesson: List<Badge> = emptyList(),
    val difficultyChanged: Int? = null,
    val error: String? = null,
    val failureContext: FailureContext? = null,  // PATCH 2
    val lessonPlan: LessonPlan? = null,
    // PATCH 1: Expliciete completion stage in UI state voor recovery
    val completionStage: CompletionStage = CompletionStage.NOT_STARTED,
    val completionStageExerciseId: String? = null  // Valideert dat stage bij huidige exercise hoort
) {
    val currentExercise: Exercise? = exercises.getOrNull(currentIndex)
    val totalExercises: Int = exercises.size
    val progress: Float = if (totalExercises > 0) currentIndex.toFloat() / totalExercises else 0f

    // PATCH 1: Backwards compatibility
    val showFeedback: Boolean get() = stepState == LessonStepState.FEEDBACK
    val isProcessing: Boolean get() = stepState == LessonStepState.PROCESSING || stepState == LessonStepState.ADVANCING
    val hasError: Boolean get() = stepState == LessonStepState.ERROR || error != null
}

sealed class LessonNavigationEvent {
    data class LessonComplete(
        val result: SessionResult,
        val badges: List<Badge>,
        val xpTotal: Int
    ) : LessonNavigationEvent()
    data object BackToHome : LessonNavigationEvent()
}

enum class LessonPhase {
    WARM_UP,
    FOCUS,
    REVIEW,
    CHALLENGE
}

private fun List<DetailedExerciseResult>.accuracy(): Float {
    if (isEmpty()) return 0f
    val correct = count { it.isCorrect }
    return correct.toFloat() / size
}

private fun DetailedExerciseResult.toExerciseResult(): ExerciseResult {
    return ExerciseResult(
        exerciseId = exerciseId,
        skillId = skillId,
        isCorrect = isCorrect,
        responseTimeMs = responseTimeMs,
        givenAnswer = givenAnswer
    )
}