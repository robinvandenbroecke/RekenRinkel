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

    private fun currentExerciseOrNull(): Exercise? = _uiState.value.currentExercise

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
        val startExercise = currentExerciseOrNull() ?: run {
            handleCompletionFailure("Geen huidige oefening", FailureStage.UNKNOWN)
            return
        }
        val exerciseId = result.exerciseId

        if (startExercise.id != exerciseId) {
            handleCompletionFailure("Exercise mismatch tijdens completion", FailureStage.UNKNOWN, startExercise)
            return
        }

        if (isCompletionDoneForCurrentExercise(exerciseId) || handledExerciseIds.contains(exerciseId)) {
            android.util.Log.w("LessonViewModel", "[COMPLETION] BLOCKED - Exercise $exerciseId already finished")
            return
        }

        if (!isStageValidForExercise(exerciseId)) {
            android.util.Log.w("LessonViewModel", "[COMPLETION] Stage mismatch detected, resetting to NOT_STARTED for $exerciseId")
            markCompletionStage(CompletionStage.NOT_STARTED, exerciseId)
        }

        if (currentlyCompletingExerciseId == exerciseId) {
            android.util.Log.w("LessonViewModel", "[COMPLETION] BLOCKED - Exercise $exerciseId already being processed")
            return
        }
        currentlyCompletingExerciseId = exerciseId

        val needsFeedback = mode == CompletionMode.FEEDBACK_THEN_ADVANCE
        val isSkip = mode == CompletionMode.SKIP_ADVANCE
        val isWorkedExample = mode == CompletionMode.DIRECT_CONTINUE
        val shouldUpdateProgress = mode == CompletionMode.FEEDBACK_THEN_ADVANCE
        val shouldApplyRewards = mode == CompletionMode.FEEDBACK_THEN_ADVANCE

        var outcome: ExerciseOutcome? = null

        try {
            val completion1 = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 1 CHECK: currentStage=${completion1.stage}, target=RESULT_LOGGED")
            if (completion1.stage < CompletionStage.RESULT_LOGGED) {
                _uiState.update {
                    it.copy(
                        results = it.results + result,
                        completionStage = CompletionStage.RESULT_LOGGED,
                        completionStageExerciseId = exerciseId
                    )
                }
            }

            val completion2 = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 2 CHECK: currentStage=${completion2.stage}, target=PROGRESS_UPDATED")
            if (shouldUpdateProgress && completion2.stage == CompletionStage.RESULT_LOGGED) {
                try {
                    val currentProgress = progressRepository.getOrCreateProgress(result.skillId)
                    outcome = lessonEngine.processExerciseResult(result, currentProgress)
                    progressRepository.updateProgress(outcome.updatedProgress)
                    markCompletionStage(CompletionStage.PROGRESS_UPDATED, exerciseId)
                } catch (e: Exception) {
                    throw CompletionStepFailure(FailureStage.PROGRESS_UPDATE, e)
                }
            }

            val completion3 = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 3 CHECK: currentStage=${completion3.stage}, target=REWARDS_APPLIED")
            if (shouldApplyRewards && completion3.stage == CompletionStage.PROGRESS_UPDATED) {
                try {
                    val safeOutcome = outcome ?: run {
                        val currentProgress = progressRepository.getOrCreateProgress(result.skillId)
                        lessonEngine.processExerciseResult(result, currentProgress)
                    }
                    outcome = safeOutcome
                    val currentRewards = profileRepository.getRewards()
                    val updatedRewards = currentRewards.addXp(safeOutcome.xpEarned).updateStreak()
                    val newBadges = lessonEngine.checkBadges(safeOutcome, currentRewards, result.skillId)
                    val finalRewards = newBadges.fold(updatedRewards) { rewards, badge -> rewards.addBadge(badge) }
                    profileRepository.updateRewards(finalRewards)
                    markCompletionStage(CompletionStage.REWARDS_APPLIED, exerciseId)
                } catch (e: Exception) {
                    throw CompletionStepFailure(FailureStage.REWARD_UPDATE, e)
                }
            }

            val completion4 = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 4 CHECK: currentStage=${completion4.stage}, target=READY_TO_ADVANCE")
            val canPrepareAdvance = when {
                isSkip -> completion4.stage == CompletionStage.RESULT_LOGGED
                isWorkedExample -> completion4.stage == CompletionStage.RESULT_LOGGED
                else -> completion4.stage == CompletionStage.REWARDS_APPLIED
            }
            if (canPrepareAdvance) {
                val xpEarned = if (shouldApplyRewards) outcome?.xpEarned ?: 0 else 0
                val badges = if (shouldApplyRewards && outcome != null) {
                    lessonEngine.checkBadges(outcome, profileRepository.getRewards(), result.skillId)
                } else {
                    emptyList()
                }
                _uiState.update {
                    it.copy(
                        stepState = if (needsFeedback) LessonStepState.FEEDBACK else LessonStepState.ADVANCING,
                        lastAnswerCorrect = result.isCorrect,
                        xpEarnedThisLesson = it.xpEarnedThisLesson + xpEarned,
                        badgesEarnedThisLesson = it.badgesEarnedThisLesson + badges,
                        difficultyChanged = outcome?.let { o -> if (o.difficultyChanged) o.newDifficultyTier else null },
                        completionStage = CompletionStage.READY_TO_ADVANCE,
                        completionStageExerciseId = exerciseId
                    )
                }
            }

            val completion5 = currentCompletionState()
            android.util.Log.d("LessonViewModel", "[COMPLETION] Step 5 CHECK: currentStage=${completion5.stage}, target=DONE")
            if (completion5.stage == CompletionStage.READY_TO_ADVANCE) {
                if (needsFeedback) {
                    delay(feedbackDurationMs)
                }

                val latestExercise = currentExerciseOrNull()
                if (latestExercise?.id != exerciseId) {
                    currentlyCompletingExerciseId = null
                    return
                }

                markCompletionStage(CompletionStage.DONE, exerciseId)
                advanceToNextExercise(exerciseId)
            }
        } catch (e: CompletionStepFailure) {
            handleCompletionFailure(
                e.cause?.message ?: e.message ?: "Onbekende fout",
                e.stage,
                currentExerciseOrNull() ?: startExercise
            )
        } catch (e: Exception) {
            android.util.Log.e("LessonViewModel", "[COMPLETION] FAILED: exercise=$exerciseId failed with ${e.message}", e)
            handleCompletionFailure(e.message ?: "Onbekende fout", FailureStage.UNKNOWN, currentExerciseOrNull() ?: startExercise)
        } finally {
            currentlyCompletingExerciseId = null
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
        val currentExercise = exercise ?: currentExerciseOrNull()
        val completion = currentCompletionState()
        val currentIndex = _uiState.value.currentIndex

        android.util.Log.e("LessonViewModel", "[FAILURE] Exercise ${currentExercise?.id ?: "unknown"} failed at ${stage.name}: $errorMessage")
        android.util.Log.e("LessonViewModel", "[FAILURE] Stage: ${completion.stage}, exercise: ${completion.exerciseId}, index: $currentIndex")

        val progressFailed = stage == FailureStage.PROGRESS_UPDATE ||
            (stage == FailureStage.UNKNOWN && completion.stage == CompletionStage.RESULT_LOGGED)
        val rewardsFailed = stage == FailureStage.REWARD_UPDATE ||
            (stage == FailureStage.UNKNOWN && completion.stage == CompletionStage.PROGRESS_UPDATED)

        val failureContext = currentExercise?.let {
            FailureContext(
                errorMessage = errorMessage,
                exerciseId = it.id,
                exerciseType = it.type,
                currentIndex = currentIndex,
                stage = stage,
                completionStage = completion.stage,
                completionStageExerciseId = completion.exerciseId,
                resultLogged = completion.stage >= CompletionStage.RESULT_LOGGED,
                progressUpdated = completion.stage >= CompletionStage.PROGRESS_UPDATED,
                rewardsApplied = completion.stage >= CompletionStage.REWARDS_APPLIED,
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
    private fun advanceToNextExercise(completedExerciseId: String? = null) {
        val currentState = _uiState.value
        val nextIndex = currentState.currentIndex + 1
        val exerciseIdToHandle = completedExerciseId ?: currentExerciseOrNull()?.id

        exerciseIdToHandle?.let { exerciseId ->
            handledExerciseIds.add(exerciseId)
            android.util.Log.d("LessonViewModel", "Exercise $exerciseId marked as handled")
        }

        _uiState.update { it.copy(stepState = LessonStepState.ADVANCING) }
        currentlyCompletingExerciseId = null

        android.util.Log.d("LessonViewModel", "Advancing from index ${currentState.currentIndex} to $nextIndex")

        if (nextIndex >= currentState.exercises.size) {
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
                    completionStageExerciseId = null,
                    error = null,
                    failureContext = null
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
        val currentExercise = currentExerciseOrNull() ?: return
        val stepState = _uiState.value.stepState

        if (isCompletionDoneForCurrentExercise(currentExercise.id)) {
            android.util.Log.w("LessonViewModel", "submitAnswer ignored - exercise ${currentExercise.id} already DONE")
            return
        }

        if (stepState != LessonStepState.SHOWING) return

        _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }

        val responseTimeMs = System.currentTimeMillis() - exerciseStartTime

        viewModelScope.launch {
            try {
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

                finishCurrentExercise(result, mode = CompletionMode.FEEDBACK_THEN_ADVANCE)
            } catch (e: Exception) {
                handleCompletionFailure(e.message ?: "Onbekende fout", FailureStage.RESULT_LOGGING, currentExercise)
            }
        }
    }

    /**
     * PATCH 2: WORKED_EXAMPLE - direct en expliciet
     * Geen validatie, geen feedback-overlay, direct advance
     */
    fun continueWorkedExample() {
        val currentExercise = currentExerciseOrNull() ?: return
        val stepState = _uiState.value.stepState

        if (isCompletionDoneForCurrentExercise(currentExercise.id)) {
            android.util.Log.w("LessonViewModel", "continueWorkedExample ignored - exercise ${currentExercise.id} already DONE")
            return
        }

        if (currentExercise.type != com.rekenrinkel.domain.model.ExerciseType.WORKED_EXAMPLE) {
            submitAnswer("[worked_fallback]")
            return
        }

        if (stepState != LessonStepState.SHOWING) return

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
        val currentExercise = currentExerciseOrNull() ?: return
        val stepState = _uiState.value.stepState

        if (isCompletionDoneForCurrentExercise(currentExercise.id)) {
            android.util.Log.w("LessonViewModel", "skipExercise ignored - exercise ${currentExercise.id} already DONE")
            return
        }

        if (stepState != LessonStepState.SHOWING) return

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
        val failureContext = _uiState.value.failureContext
        val completion = currentCompletionState()

        android.util.Log.d("LessonViewModel", "continueAfterError called")
        android.util.Log.d("LessonViewModel", "Failure stage: ${failureContext?.stage?.name ?: "null"}")
        android.util.Log.d("LessonViewModel", "Completion stage: ${completion.stage} (exercise: ${completion.exerciseId})")
        android.util.Log.d("LessonViewModel", "Exercise type: ${failureContext?.exerciseType}")

        val stageBelongsToCurrentExercise = completion.exerciseId == failureContext?.exerciseId
        val recoveryStage = if (stageBelongsToCurrentExercise) completion.stage else CompletionStage.NOT_STARTED
        
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
                android.util.Log.d("LessonViewModel", "[RECOVERY] CONTINUE_REMAINING_STEPS - completing remaining steps only")
                viewModelScope.launch {
                    val currentExercise = currentExerciseOrNull()
                    if (currentExercise != null) {
                        completeRemainingSteps(currentExercise)
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
                        val currentExercise = currentExerciseOrNull()
                        if (currentExercise != null) {
                            completeRemainingSteps(currentExercise)
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
                android.util.Log.d("LessonViewModel", "[RECOVERY] Stage=RESULT_LOGGED → result logged, progress/rewards pending")
                if (exerciseType == ExerciseType.WORKED_EXAMPLE) {
                    android.util.Log.d("LessonViewModel", "[RECOVERY] WORKED_EXAMPLE at RESULT_LOGGED → direct ADVANCE_TO_NEXT")
                    RecoveryAction.ADVANCE_TO_NEXT
                } else {
                    android.util.Log.d("LessonViewModel", "[RECOVERY] NORMAL/GUIDED at RESULT_LOGGED → CONTINUE_REMAINING_STEPS")
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
    private suspend fun completeRemainingSteps(exercise: Exercise) {
        android.util.Log.d("LessonViewModel", "[RECOVERY] completeRemainingSteps for ${exercise.id}")

        val completionStart = currentCompletionState()
        if (completionStart.exerciseId != null && completionStart.exerciseId != exercise.id) {
            markCompletionStage(CompletionStage.NOT_STARTED, exercise.id)
        }

        try {
            val lastResult = _uiState.value.results.lastOrNull { it.exerciseId == exercise.id }

            val completion2 = currentCompletionState()
            if (completion2.stage == CompletionStage.RESULT_LOGGED && exercise.type != ExerciseType.WORKED_EXAMPLE) {
                if (lastResult != null) {
                    val currentProgress = progressRepository.getOrCreateProgress(exercise.skillId)
                    val outcome = lessonEngine.processExerciseResult(lastResult, currentProgress)
                    progressRepository.updateProgress(outcome.updatedProgress)
                    markCompletionStage(CompletionStage.PROGRESS_UPDATED, exercise.id)
                }
            }

            val completion3 = currentCompletionState()
            if (completion3.stage == CompletionStage.PROGRESS_UPDATED && exercise.type != ExerciseType.WORKED_EXAMPLE) {
                if (lastResult != null) {
                    val currentProgress = progressRepository.getOrCreateProgress(exercise.skillId)
                    val outcome = lessonEngine.processExerciseResult(lastResult, currentProgress)
                    val currentRewards = profileRepository.getRewards()
                    val updatedRewards = currentRewards.addXp(outcome.xpEarned).updateStreak()
                    val newBadges = lessonEngine.checkBadges(outcome, currentRewards, exercise.skillId)
                    val finalRewards = newBadges.fold(updatedRewards) { r, b -> r.addBadge(b) }
                    profileRepository.updateRewards(finalRewards)
                    markCompletionStage(CompletionStage.REWARDS_APPLIED, exercise.id)
                }
            }

            val completion4 = currentCompletionState()
            if (
                completion4.stage == CompletionStage.READY_TO_ADVANCE ||
                completion4.stage == CompletionStage.REWARDS_APPLIED ||
                (exercise.type == ExerciseType.WORKED_EXAMPLE && completion4.stage == CompletionStage.RESULT_LOGGED)
            ) {
                if (completion4.stage != CompletionStage.READY_TO_ADVANCE) {
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.ADVANCING,
                            completionStage = CompletionStage.READY_TO_ADVANCE,
                            completionStageExerciseId = exercise.id
                        )
                    }
                }
                markCompletionStage(CompletionStage.DONE, exercise.id)
                advanceToNextExercise(exercise.id)
                return
            }

            advanceToNextExercise(exercise.id)
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