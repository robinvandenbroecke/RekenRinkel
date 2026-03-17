package com.rekenrinkel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.repository.ProfileRepository
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.*
import com.rekenrinkel.domain.model.*
import com.rekenrinkel.domain.model.UserProfile as ProfileModel
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
                        lessonPlan = lessonPlan
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
    private fun currentCompletionState(): Pair<CompletionStage, String?> {
        val state = _uiState.value
        return Pair(state.completionStage, state.completionStageExerciseId)
    }

    /**
     * PATCH 4 & 5: Helper om te valideren dat stage bij huidige oefening hoort
     */
    private fun isStageValidForExercise(exerciseId: String): Boolean {
        val (_, stageExerciseId) = currentCompletionState()
        return stageExerciseId == null || stageExerciseId == exerciseId
    }

    private suspend fun finishCurrentExercise(
        result: DetailedExerciseResult,
        mode: CompletionMode,
        feedbackDurationMs: Long = 800
    ) {
        // PATCH 4: Geen stale state - altijd actuele state gebruiken
        val currentExercise = _uiState.value.currentExercise ?: run {
            handleCompletionFailure("Geen huidige oefening", FailureStage.UNKNOWN)
            return
        }

        // PATCH 3: Harde DONE guard - als oefening al volledig afgerond is, geen side effects
        val currentStage = _uiState.value.completionStage
        if (currentStage == CompletionStage.DONE) {
            android.util.Log.w("LessonViewModel", "[COMPLETION] BLOCKED - Exercise ${currentExercise.id} already DONE, no side effects allowed")
            return
        }

        // PATCH 4 & 5: Check of oefening al definitief afgehandeld is
        if (handledExerciseIds.contains(currentExercise.id)) {
            android.util.Log.d("LessonViewModel", "[COMPLETION] Exercise ${currentExercise.id} already handled, skipping")
            return
        }

        // PATCH 4 & 5: Valideer dat stage bij huidige oefening hoort
        if (!isStageValidForExercise(currentExercise.id)) {
            android.util.Log.w("LessonViewModel", "[COMPLETION] Stage mismatch detected, resetting to NOT_STARTED")
            _uiState.update { 
                it.copy(
                    completionStage = CompletionStage.NOT_STARTED,
                    completionStageExerciseId = currentExercise.id
                )
            }
        }

        // PATCH 2: Harde completion guard
        if (currentlyCompletingExerciseId == currentExercise.id) {
            android.util.Log.w("LessonViewModel", "[COMPLETION] BLOCKED - Exercise ${currentExercise.id} already being processed")
            return
        }
        currentlyCompletingExerciseId = currentExercise.id
        // NOTE: currentCompletionStage en currentStageExerciseId niet meer als private vars
        // UI state (_uiState.value.completionStage etc.) is de enige bron van waarheid

        // PATCH 4 & 7: Versterkte debug logging - gebruik actuele state
        val (initialStage, initialStageExerciseId) = currentCompletionState()
        android.util.Log.d("LessonViewModel", "[COMPLETION] START exercise=${currentExercise.id}, type=${currentExercise.type}, mode=$mode, initialStage=$initialStage, stageExerciseId=$initialStageExerciseId")

        // PATCH 2: Expliciete mode-afhandeling per completion type
        val needsFeedback = mode == CompletionMode.FEEDBACK_THEN_ADVANCE
        
        // PATCH 1 & 2: Skip en Worked Example slaan mastery over
        val skipMasteryUpdate = when (mode) {
            CompletionMode.DIRECT_CONTINUE -> true  // Worked example
            CompletionMode.SKIP_ADVANCE -> true      // Skip - PATCH 1
            CompletionMode.FEEDBACK_THEN_ADVANCE -> false  // Normale oefening
        }
        
        // PATCH 1: Skip heeft semantisch andere betekenis dan worked example
        val isSkip = mode == CompletionMode.SKIP_ADVANCE
        if (isSkip) {
            android.util.Log.d("LessonViewModel", "[COMPLETION] SKIP mode - exercise ${currentExercise.id} skipped with penalty")
        }

        try {
            // PATCH 4 & 5: Stage-based completion met actuele state - GEEN stale state gebruik
            // Elke stap leest de actuele state opnieuw uit na updates
            
            // === STAP 1: Resultaat loggen (idempotent) ===
            // PATCH 4: Altijd actuele state gebruiken via helper
            val (stage1, _) = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 1 CHECK: currentStage=$stage1, target=RESULT_LOGGED")
            if (stage1 < CompletionStage.RESULT_LOGGED) {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 1 EXEC: Logging result for ${currentExercise.id}")
                val currentResults = _uiState.value.results
                val newResults = currentResults + result
                _uiState.update { 
                    it.copy(
                        results = newResults,
                        completionStage = CompletionStage.RESULT_LOGGED,
                        completionStageExerciseId = currentExercise.id
                    )
                }
                // UI state is leidend - private var niet meer bijwerken
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 1 DONE: stage=RESULT_LOGGED")
            } else {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 1 SKIP: result already logged")
            }

            // === STAP 2: Progress/mastery update (idempotent via repository) ===
            // PATCH 4: Actuele state ophalen via helper
            val (stage2, _) = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 2 CHECK: currentStage=$stage2, target=PROGRESS_UPDATED")
            var outcome: ExerciseOutcome? = null
            var progressUpdateFailed = false  // PATCH 4: expliciete failure tracking
            
            if (!skipMasteryUpdate && stage2 < CompletionStage.PROGRESS_UPDATED) {
                try {
                    android.util.Log.d("LessonViewModel", "[COMPLETION] Step 2 EXEC: Updating progress for ${currentExercise.id}")
                    val currentProgress = progressRepository.getOrCreateProgress(currentExercise.skillId)
                    outcome = lessonEngine.processExerciseResult(result, currentProgress)
                    progressRepository.updateProgress(outcome.updatedProgress)
                    _uiState.update {
                        it.copy(completionStage = CompletionStage.PROGRESS_UPDATED)
                    }
                    // UI state is leidend - private var niet meer bijwerken
                    android.util.Log.d("LessonViewModel", "[COMPLETION] Step 2 DONE: stage=PROGRESS_UPDATED")
                } catch (e: Exception) {
                    // PATCH 4 & 7: Progress failure expliciet markeren
                    progressUpdateFailed = true
                    android.util.Log.e("LessonViewModel", "[COMPLETION] Step 2 FAILED: Progress update failed, marking degraded completion", e)
                    outcome = null
                }
            } else if (skipMasteryUpdate) {
                val reason = if (isSkip) "skip" else "worked example"
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 2 SKIP: mastery update skipped for $reason")
            } else {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 2 SKIP: progress already updated")
            }

            // === STAP 3: Rewards update (idempotent via repository) ===
            // PATCH 4: Actuele state ophalen via helper
            val (stage3, _) = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 3 CHECK: currentStage=$stage3, target=REWARDS_APPLIED")
            var rewardsUpdateFailed = false  // PATCH 4: expliciete failure tracking

            if (!skipMasteryUpdate && stage3 < CompletionStage.REWARDS_APPLIED) {
                try {
                    android.util.Log.d("LessonViewModel", "[COMPLETION] Step 3 EXEC: Applying rewards for ${currentExercise.id}")
                    if (outcome != null) {
                        val currentRewards = profileRepository.getRewards()
                        val updatedRewards = currentRewards
                            .addXp(outcome.xpEarned)
                            .updateStreak()

                        val newBadges = lessonEngine.checkBadges(
                            outcome,
                            currentRewards,
                            currentExercise.skillId
                        )

                        val finalRewards = newBadges.fold(updatedRewards) { rewards, badge ->
                            rewards.addBadge(badge)
                        }
                        profileRepository.updateRewards(finalRewards)
                    }
                    _uiState.update {
                        it.copy(completionStage = CompletionStage.REWARDS_APPLIED)
                    }
                    // UI state is leidend - private var niet meer bijwerken
                    android.util.Log.d("LessonViewModel", "[COMPLETION] Step 3 DONE: stage=REWARDS_APPLIED")
                } catch (e: Exception) {
                    // PATCH 4 & 7: Rewards failure expliciet markeren
                    rewardsUpdateFailed = true
                    android.util.Log.e("LessonViewModel", "[COMPLETION] Step 3 FAILED: Rewards update failed, marking degraded completion", e)
                }
            } else if (skipMasteryUpdate) {
                val reason = if (isSkip) "skip" else "worked example"
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 3 SKIP: rewards skipped for $reason")
            } else {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 3 SKIP: rewards already applied")
            }

            // === STAP 4: Prepare advance - UI state updaten ===
            // PATCH 4: Actuele state ophalen via helper
            val (stage4, _) = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 4 CHECK: currentStage=$stage4, target=READY_TO_ADVANCE")
            if (stage4 < CompletionStage.READY_TO_ADVANCE) {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 4 EXEC: Preparing advance for ${currentExercise.id}")

                // PATCH 4 & 7: Log expliciet als we in gedegradeerde state zitten
                if (progressUpdateFailed || rewardsUpdateFailed) {
                    android.util.Log.w("LessonViewModel", "[COMPLETION] Step 4 WARNING: degraded completion (progressFailed=$progressUpdateFailed, rewardsFailed=$rewardsUpdateFailed)")
                }
                
                val xpEarned = outcome?.xpEarned ?: 0
                val badges = outcome?.let { o ->
                    lessonEngine.checkBadges(o, profileRepository.getRewards(), currentExercise.skillId)
                } ?: emptyList()
                
                _uiState.update {
                    it.copy(
                        stepState = if (needsFeedback) LessonStepState.FEEDBACK else LessonStepState.ADVANCING,
                        lastAnswerCorrect = result.isCorrect,
                        xpEarnedThisLesson = it.xpEarnedThisLesson + xpEarned,
                        badgesEarnedThisLesson = it.badgesEarnedThisLesson + badges,
                        difficultyChanged = outcome?.let { o -> if (o.difficultyChanged) o.newDifficultyTier else null },
                        completionStage = CompletionStage.READY_TO_ADVANCE
                    )
                }
                // UI state is leidend - private var niet meer bijwerken
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 4 DONE: stage=READY_TO_ADVANCE, xpEarned=$xpEarned")
            } else {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 4 SKIP: already ready to advance")
            }

            // === STAP 5: Advance / Complete ===
            // PATCH 4: Actuele state ophalen via helper
            val (stage5, _) = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 5 CHECK: currentStage=$stage5, target=DONE")
            if (stage5 < CompletionStage.DONE) {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 5 EXEC: Advancing from ${currentExercise.id}")
                if (needsFeedback) {
                    delay(feedbackDurationMs)
                }
                advanceToNextExercise()

                // PATCH 3: DONE zetten alleen als advance succesvol was
                // advanceToNextExercise() reset de stage voor de volgende oefening
                // Dus we hoeven hier niets meer te zetten
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 5 DONE: exercise=${currentExercise.id} fully completed")
            } else {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Step 5 SKIP: already done")
            }

            android.util.Log.d("LessonViewModel", "[COMPLETION] FINISH: exercise=${currentExercise.id} completed successfully")

        } catch (e: Exception) {
            // PATCH 4, 7 & 8: FAIL-SAFE - Geen stil terug naar SHOWING
            android.util.Log.e("LessonViewModel", "[COMPLETION] FAILED: exercise=${currentExercise.id} failed with ${e.message}", e)
            handleCompletionFailure(e.message ?: "Onbekende fout", FailureStage.UNKNOWN, currentExercise)
        }
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
                rewardsApplied = state.completionStage >= CompletionStage.REWARDS_APPLIED
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

        // PATCH 5 & 8: Markeer huidige oefening als definitief afgehandeld
        currentExercise?.let { exercise ->
            handledExerciseIds.add(exercise.id)
            android.util.Log.d("LessonViewModel", "Exercise ${exercise.id} marked as handled")
        }

        _uiState.update { it.copy(stepState = LessonStepState.ADVANCING) }

        // PATCH 2: Reset completion guard bij advance
        currentlyCompletingExerciseId = null
        // NOTE: currentCompletionStage en currentStageExerciseId niet meer als private vars

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

        // PATCH 5: Guard - als al DONE, negeren
        if (state.completionStage == CompletionStage.DONE) {
            android.util.Log.w("LessonViewModel", "submitAnswer ignored - exercise ${currentExercise.id} already DONE")
            return
        }

        // PATCH 6: Guard met expliciete state
        if (state.stepState != LessonStepState.SHOWING) return

        _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }

        val responseTimeMs = System.currentTimeMillis() - exerciseStartTime

        viewModelScope.launch {
            val isCorrect = exerciseValidator.validate(currentExercise, answer)

            val result = DetailedExerciseResult(
                exerciseId = currentExercise.id,
                skillId = currentExercise.skillId,
                isCorrect = isCorrect,
                responseTimeMs = responseTimeMs,
                givenAnswer = answer,
                correctAnswer = currentExercise.correctAnswer,
                difficultyTier = currentExercise.difficulty,
                representationUsed = currentExercise.visualData?.type?.name ?: "ABSTRACT",
                errorType = if (!isCorrect) determineErrorType(currentExercise, answer) else null
            )

            // PATCH 3: Gebruik expliciete completion mode
            finishCurrentExercise(result, mode = CompletionMode.FEEDBACK_THEN_ADVANCE)
        }
    }

    /**
     * PATCH 2: WORKED_EXAMPLE - direct en expliciet
     * Geen validatie, geen feedback-overlay, direct advance
     */
    fun continueWorkedExample() {
        val state = _uiState.value
        val currentExercise = state.currentExercise ?: return

        // PATCH 5: Guard - als al DONE, negeren
        if (state.completionStage == CompletionStage.DONE) {
            android.util.Log.w("LessonViewModel", "continueWorkedExample ignored - exercise ${currentExercise.id} already DONE")
            return
        }

        // Guard: alleen voor WORKED_EXAMPLE
        if (currentExercise.type != com.rekenrinkel.domain.model.ExerciseType.WORKED_EXAMPLE) {
            // Als het per ongeluk een ander type is, behandel als normale oefening
            submitAnswer("[worked_fallback]")
            return
        }

        // PATCH 6: Guard met expliciete state
        if (state.stepState != LessonStepState.SHOWING) return

        _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }

        viewModelScope.launch {
            val result = DetailedExerciseResult(
                exerciseId = currentExercise.id,
                skillId = currentExercise.skillId,
                isCorrect = true, // WORKED_EXAMPLE telt altijd als "gezien"
                responseTimeMs = System.currentTimeMillis() - exerciseStartTime,
                givenAnswer = "[worked_example_viewed]",
                correctAnswer = currentExercise.correctAnswer,
                difficultyTier = currentExercise.difficulty,
                representationUsed = "WORKED_EXAMPLE"
            )

            // PATCH 3: Gebruik expliciete completion mode
            finishCurrentExercise(result, mode = CompletionMode.DIRECT_CONTINUE)
        }
    }

    /**
     * PATCH 6: Sla oefening over - eenvoudig en niet-blokkerend
     * Geen feedback, direct door naar volgende oefening
     */
    fun skipExercise() {
        val state = _uiState.value
        val currentExercise = state.currentExercise ?: return

        // PATCH 5: Guard - als al DONE, negeren
        if (state.completionStage == CompletionStage.DONE) {
            android.util.Log.w("LessonViewModel", "skipExercise ignored - exercise ${currentExercise.id} already DONE")
            return
        }

        // PATCH 6: Guard met expliciete state
        if (state.stepState != LessonStepState.SHOWING) return

        _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }

        viewModelScope.launch {
            val result = DetailedExerciseResult(
                exerciseId = currentExercise.id,
                skillId = currentExercise.skillId,
                isCorrect = false,
                responseTimeMs = 30_000, // Skip penalty
                givenAnswer = "[skipped]",
                correctAnswer = currentExercise.correctAnswer,
                difficultyTier = currentExercise.difficulty,
                representationUsed = "SKIPPED"
            )

            // PATCH 1: Skip gebruikt eigen SKIP_ADVANCE mode
            finishCurrentExercise(result, mode = CompletionMode.SKIP_ADVANCE)
        }
    }

    /**
     * PATCH 4 & 6: Verder gaan na een fout - context-aware recovery
     * Gebruikt failure context om veilige recovery te bepalen
     */
    fun continueAfterError() {
        val state = _uiState.value
        val failureContext = state.failureContext

        // PATCH 8: Debug logging - alleen UI state gebruiken
        android.util.Log.d("LessonViewModel", "continueAfterError called")
        android.util.Log.d("LessonViewModel", "Failure stage: ${failureContext?.stage?.name ?: "null"}")
        android.util.Log.d("LessonViewModel", "Completion stage: ${state.completionStage} (exercise: ${state.completionStageExerciseId})")
        android.util.Log.d("LessonViewModel", "Exercise type: ${failureContext?.exerciseType}")

        // PATCH 3 & 6: Gebruik UI state als bron van waarheid voor recovery
        // De private var kan verouderd zijn bij configuration changes
        val effectiveStage = state.completionStage
        val stageBelongsToCurrentExercise = state.completionStageExerciseId == failureContext?.exerciseId
        
        // PATCH 3: Als de stage niet bij de huidige oefening hoort, reset naar NOT_STARTED
        val recoveryStage = if (stageBelongsToCurrentExercise) effectiveStage else CompletionStage.NOT_STARTED
        
        android.util.Log.d("LessonViewModel", "Effective recovery stage: $recoveryStage (belongs to current: $stageBelongsToCurrentExercise)")

        // PATCH 6 & 9: Recovery afhankelijk van exercise type en completion stage
        val recoveryAction = determineRecoveryAction(failureContext, recoveryStage)
        android.util.Log.d("LessonViewModel", "Recovery action: $recoveryAction")

        // PATCH 8: Log recovery execution
        android.util.Log.d("LessonViewModel", "[RECOVERY] Executing action: $recoveryAction")
        
        when (recoveryAction) {
            RecoveryAction.RETRY_CURRENT -> {
                // PATCH 7: Probeer huidige opnieuw (alleen als NOT_STARTED)
                android.util.Log.d("LessonViewModel", "[RECOVERY] RETRY_CURRENT - resetting to SHOWING")
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.SHOWING,
                            error = null,
                            failureContext = null,
                            completionStage = CompletionStage.NOT_STARTED,
                            completionStageExerciseId = null
                        )
                    }
                    currentlyCompletingExerciseId = null
                    // NOTE: UI state is leidend
                }
            }
            
            RecoveryAction.CONTINUE_REMAINING_STEPS -> {
                // PATCH 5: Voltooi alleen resterende stappen (result al gelogd)
                // Gebruik specifieke helper, niet blind finishCurrentExercise
                android.util.Log.d("LessonViewModel", "[RECOVERY] CONTINUE_REMAINING_STEPS - completing remaining steps only")
                viewModelScope.launch {
                    val currentExercise = state.currentExercise
                    if (currentExercise != null) {
                        completeRemainingSteps(currentExercise, state)
                    } else {
                        advanceToNextExercise()
                    }
                }
            }
            
            RecoveryAction.ADVANCE_TO_NEXT -> {
                // PATCH 5: ADVANCE_TO_NEXT alleen als we zeker zijn dat side effects al gedaan zijn
                // Dit mag alleen gebruikt worden als:
                // - REWARDS_APPLIED of READY_TO_ADVANCE bereikt is (geen dubbele rewards)
                // - of als het een WORKED_EXAMPLE/SKIP is (geen rewards nodig)
                val canSafelyAdvance = recoveryStage >= CompletionStage.REWARDS_APPLIED ||
                    failureContext?.exerciseType == ExerciseType.WORKED_EXAMPLE
                
                if (!canSafelyAdvance) {
                    android.util.Log.w("LessonViewModel", "[RECOVERY] ADVANCE_TO_NEXT blocked - stage=$recoveryStage, type=${failureContext?.exerciseType}, using CONTINUE_REMAINING_STEPS instead")
                    // Fallback naar veiliger recovery
                    viewModelScope.launch {
                        val currentExercise = state.currentExercise
                        if (currentExercise != null) {
                            completeRemainingSteps(currentExercise, state)
                        } else {
                            advanceToNextExercise()
                        }
                    }
                    return
                }
                
                android.util.Log.d("LessonViewModel", "[RECOVERY] ADVANCE_TO_NEXT - safe advance (stage=$recoveryStage)")
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.ADVANCING,
                            error = null,
                            failureContext = null,
                            completionStage = CompletionStage.NOT_STARTED,
                            completionStageExerciseId = null
                        )
                    }
                    currentlyCompletingExerciseId = null
                    advanceToNextExercise()
                }
            }
            
            RecoveryAction.COMPLETE_ADVANCE -> {
                // PATCH 3: DONE was bereikt, oefening is volledig afgehandeld
                // Markeer als handled en doe veilige advance
                android.util.Log.d("LessonViewModel", "[RECOVERY] COMPLETE_ADVANCE - exercise fully done, completing advance")
                failureContext?.let { handledExerciseIds.add(it.exerciseId) }
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.ADVANCING,
                            error = null,
                            failureContext = null,
                            completionStage = CompletionStage.NOT_STARTED,
                            completionStageExerciseId = null
                        )
                    }
                    currentlyCompletingExerciseId = null
                    // NOTE: UI state is leidend
                    advanceToNextExercise()
                }
            }
            
            RecoveryAction.SKIP_TO_NEXT -> {
                // Markeer huidige als handled en skip
                android.util.Log.d("LessonViewModel", "[RECOVERY] SKIP_TO_NEXT - marking as handled and advancing")
                failureContext?.let { handledExerciseIds.add(it.exerciseId) }
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.ADVANCING,
                            error = null,
                            failureContext = null,
                            completionStage = CompletionStage.NOT_STARTED,
                            completionStageExerciseId = null
                        )
                    }
                    currentlyCompletingExerciseId = null
                    // NOTE: UI state is leidend
                    advanceToNextExercise()
                }
            }

            RecoveryAction.COMPLETE_LESSON -> {
                // PATCH 7: Les voltooien (als alles DONE is)
                android.util.Log.d("LessonViewModel", "[RECOVERY] COMPLETE_LESSON - completing lesson")
                completeLesson()
            }
            
            RecoveryAction.RETURN_HOME -> {
                // Meest veilige fallback
                android.util.Log.d("LessonViewModel", "[RECOVERY] RETURN_HOME - navigating back")
                _uiState.update {
                    it.copy(
                        stepState = LessonStepState.SHOWING,
                        error = null,
                        failureContext = null
                    )
                }
                viewModelScope.launch {
                    _navigation.emit(LessonNavigationEvent.BackToHome)
                }
            }
        }
        
        android.util.Log.d("LessonViewModel", "[RECOVERY] Action execution completed")
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
                // PATCH 4: Progress is geupdate maar rewards mogelijk niet
                // Dit is een gedegradeerde state - log dit expliciet
                android.util.Log.d("LessonViewModel", "[RECOVERY] Stage=PROGRESS_UPDATED → gedegradeerde completion, rewards missen mogelijk")
                when (exerciseType) {
                    ExerciseType.WORKED_EXAMPLE -> RecoveryAction.ADVANCE_TO_NEXT
                    else -> RecoveryAction.CONTINUE_REMAINING_STEPS
                }
            }
            CompletionStage.RESULT_LOGGED -> {
                // PATCH 4: Resultaat is gelogd maar progress/rewards niet
                // Dit is een gedegradeerde state - log dit expliciet
                android.util.Log.d("LessonViewModel", "[RECOVERY] Stage=RESULT_LOGGED → gedegradeerde completion, progress/rewards missen")
                when (exerciseType) {
                    ExerciseType.WORKED_EXAMPLE -> RecoveryAction.ADVANCE_TO_NEXT
                    else -> RecoveryAction.CONTINUE_REMAINING_STEPS
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