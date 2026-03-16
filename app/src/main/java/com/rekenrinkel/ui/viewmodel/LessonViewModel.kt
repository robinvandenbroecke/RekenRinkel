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
    private var currentlyCompletingExerciseId: String? = null
    
    // PATCH 5: Set van definitief afgehandelde oefeningen
    private val handledExerciseIds = mutableSetOf<String>()
    
    // PATCH 1: Expliciete completion status
    private var currentCompletionStatus = CompletionStatus.NOT_STARTED

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
     * PATCH 3: Expliciete completion modes
     */
    enum class CompletionMode {
        DIRECT_CONTINUE,      // Geen feedback, direct door (worked example, skip)
        FEEDBACK_THEN_ADVANCE // Toon feedback, wacht, dan door (normale antwoorden)
    }

    /**
     * PATCH 1: Expliciete completion status voor veilige recovery
     */
    enum class CompletionStatus {
        NOT_STARTED,        // Nog niet begonnen met afhandeling
        RESULT_SAVED,       // Resultaat is opgeslagen
        PROGRESS_UPDATED,   // Progress/mastery is geupdate
        REWARDS_UPDATED,    // Rewards zijn geupdate
        READY_TO_ADVANCE    // Klaar om naar volgende oefening te gaan
    }

    /**
     * PATCH 1-3 & 8: Centrale helper voor het afronden van een oefening - FAIL-SAFE.
     * Alle paden (submit, worked, skip) eindigen hier.
     * Deze functie regelt ook de feedback delay en auto-advance.
     *
     * BELANGRIJK: Bij exceptions gaan we NOOIT meer stil terug naar SHOWING.
     * We gaan naar ERROR state met expliciete failure context.
     */
    private suspend fun finishCurrentExercise(
        result: DetailedExerciseResult,
        mode: CompletionMode,
        feedbackDurationMs: Long = 800
    ) {
        val state = _uiState.value
        val currentExercise = state.currentExercise ?: run {
            handleCompletionFailure("Geen huidige oefening", FailureStage.UNKNOWN)
            return
        }

        // PATCH 5: Check of oefening al definitief afgehandeld is
        if (handledExerciseIds.contains(currentExercise.id)) {
            android.util.Log.d("LessonViewModel", "Exercise ${currentExercise.id} already handled, skipping")
            return
        }

        // PATCH 2: Harde completion guard
        if (currentlyCompletingExerciseId == currentExercise.id) {
            android.util.Log.d("LessonViewModel", "Exercise ${currentExercise.id} currently being processed, skipping")
            return // Deze oefening wordt al verwerkt
        }
        currentlyCompletingExerciseId = currentExercise.id
        currentCompletionStatus = CompletionStatus.NOT_STARTED

        // PATCH 8: Debug logging
        android.util.Log.d("LessonViewModel", "Starting completion for exercise ${currentExercise.id}, type: ${currentExercise.type}, mode: $mode")

        val needsFeedback = mode == CompletionMode.FEEDBACK_THEN_ADVANCE
        val skipMasteryUpdate = mode == CompletionMode.DIRECT_CONTINUE &&
            currentExercise.type == com.rekenrinkel.domain.model.ExerciseType.WORKED_EXAMPLE

        try {
            // PATCH 3 & 8: Stap 1 - Resultaat opslaan
            currentCompletionStatus = CompletionStatus.NOT_STARTED
            android.util.Log.d("LessonViewModel", "Step 1: Saving result for ${currentExercise.id}")
            
            var newResults: List<DetailedExerciseResult>
            try {
                newResults = state.results + result
                currentCompletionStatus = CompletionStatus.RESULT_SAVED
                android.util.Log.d("LessonViewModel", "Step 1: Result saved, status: $currentCompletionStatus")
            } catch (e: Exception) {
                android.util.Log.e("LessonViewModel", "Step 1 FAILED: Result logging", e)
                handleCompletionFailure("Resultaat aanmaken mislukt", FailureStage.RESULT_LOGGING, currentExercise)
                return
            }

            // PATCH 3 & 8: Stap 2 - Progress/mastery update (indien niet geskipped/worked)
            var outcome: ExerciseOutcome? = null
            if (!skipMasteryUpdate) {
                try {
                    android.util.Log.d("LessonViewModel", "Step 2: Updating progress for ${currentExercise.id}")
                    val currentProgress = progressRepository.getOrCreateProgress(currentExercise.skillId)
                    outcome = lessonEngine.processExerciseResult(result, currentProgress)
                    progressRepository.updateProgress(outcome.updatedProgress)
                    currentCompletionStatus = CompletionStatus.PROGRESS_UPDATED
                    android.util.Log.d("LessonViewModel", "Step 2: Progress updated, status: $currentCompletionStatus")
                } catch (e: Exception) {
                    android.util.Log.e("LessonViewModel", "Step 2 FAILED: Progress update", e)
                    // Fout in progress update - log maar ga door
                    outcome = null
                }

                // PATCH 3 & 8: Stap 3 - Rewards update
                try {
                    android.util.Log.d("LessonViewModel", "Step 3: Updating rewards for ${currentExercise.id}")
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
                        currentCompletionStatus = CompletionStatus.REWARDS_UPDATED
                        android.util.Log.d("LessonViewModel", "Step 3: Rewards updated, status: $currentCompletionStatus")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LessonViewModel", "Step 3 FAILED: Rewards update", e)
                    // Fout in rewards update - log maar ga door
                }
            }

            // PATCH 3 & 8: Stap 4 - UI state update
            try {
                android.util.Log.d("LessonViewModel", "Step 4: Updating UI state for ${currentExercise.id}")
                _uiState.update {
                    it.copy(
                        results = newResults,
                        stepState = if (needsFeedback) LessonStepState.FEEDBACK else LessonStepState.ADVANCING,
                        lastAnswerCorrect = result.isCorrect,
                        xpEarnedThisLesson = it.xpEarnedThisLesson + (outcome?.xpEarned ?: 0),
                        badgesEarnedThisLesson = it.badgesEarnedThisLesson + (outcome?.let { o ->
                            lessonEngine.checkBadges(o, profileRepository.getRewards(), currentExercise.skillId)
                        } ?: emptyList()),
                        difficultyChanged = outcome?.let { if (it.difficultyChanged) it.newDifficultyTier else null }
                    )
                }
                currentCompletionStatus = CompletionStatus.READY_TO_ADVANCE
                android.util.Log.d("LessonViewModel", "Step 4: UI state updated, status: $currentCompletionStatus")
            } catch (e: Exception) {
                android.util.Log.e("LessonViewModel", "Step 4 FAILED: State update", e)
                handleCompletionFailure("State update mislukt", FailureStage.STATE_UPDATE, currentExercise)
                return
            }

            // PATCH 3 & 8: Stap 5 - Advance
            try {
                android.util.Log.d("LessonViewModel", "Step 5: Advancing from ${currentExercise.id}")
                if (needsFeedback) {
                    delay(feedbackDurationMs)
                }
                advanceToNextExercise()
                android.util.Log.d("LessonViewModel", "Step 5: Advance completed for ${currentExercise.id}")
            } catch (e: Exception) {
                android.util.Log.e("LessonViewModel", "Step 5 FAILED: Advance", e)
                handleCompletionFailure("Advance mislukt", FailureStage.ADVANCE, currentExercise)
            }

        } catch (e: Exception) {
            // PATCH 1-2 & 8: FAIL-SAFE - Geen stil terug naar SHOWING
            android.util.Log.e("LessonViewModel", "Completion failed for ${currentExercise.id}", e)
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

        // PATCH 8: Debug logging
        android.util.Log.e("LessonViewModel", "Completion FAILURE for ${currentExercise?.id ?: "unknown"} at stage ${stage.name}: $errorMessage")
        android.util.Log.e("LessonViewModel", "Completion status at failure: $currentCompletionStatus")

        // PATCH 2: Maak expliciete failure context
        val failureContext = currentExercise?.let {
            FailureContext(
                errorMessage = errorMessage,
                exerciseId = it.id,
                exerciseType = it.type,
                currentIndex = state.currentIndex,
                stage = stage
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
        currentCompletionStatus = CompletionStatus.NOT_STARTED

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
                    currentPhase = determinePhase(nextIndex, it)
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

            // PATCH 3 & 6: Skip = DIRECT_CONTINUE mode
            finishCurrentExercise(result, mode = CompletionMode.DIRECT_CONTINUE)
        }
    }

    /**
     * PATCH 4 & 6: Verder gaan na een fout - context-aware recovery
     * Gebruikt failure context om veilige recovery te bepalen
     */
    fun continueAfterError() {
        val state = _uiState.value
        val failureContext = state.failureContext

        // PATCH 8: Debug logging
        android.util.Log.d("LessonViewModel", "continueAfterError called")
        android.util.Log.d("LessonViewModel", "Failure stage: ${failureContext?.stage?.name ?: "null"}")
        android.util.Log.d("LessonViewModel", "Current completion status: $currentCompletionStatus")
        android.util.Log.d("LessonViewModel", "Exercise type: ${failureContext?.exerciseType}")

        // PATCH 6 & 9: Recovery afhankelijk van exercise type en failure stage
        val recoveryAction = determineRecoveryAction(failureContext, currentCompletionStatus)
        android.util.Log.d("LessonViewModel", "Recovery action: $recoveryAction")

        when (recoveryAction) {
            RecoveryAction.CONTINUE_TO_NEXT -> {
                // Veilige continue naar volgende oefening
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.ADVANCING,
                            error = null,
                            failureContext = null
                        )
                    }
                    advanceToNextExercise()
                }
            }
            RecoveryAction.SKIP_TO_NEXT -> {
                // Markeer huidige als handled en skip
                failureContext?.let { handledExerciseIds.add(it.exerciseId) }
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.ADVANCING,
                            error = null,
                            failureContext = null
                        )
                    }
                    currentlyCompletingExerciseId = null
                    advanceToNextExercise()
                }
            }
            RecoveryAction.RETRY_CURRENT -> {
                // Probeer huidige opnieuw (alleen als veilig)
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.SHOWING,
                            error = null,
                            failureContext = null
                        )
                    }
                    currentlyCompletingExerciseId = null
                    currentCompletionStatus = CompletionStatus.NOT_STARTED
                }
            }
            RecoveryAction.RETURN_TO_HOME -> {
                // Meest veilige fallback
                _uiState.update {
                    it.copy(
                        stepState = LessonStepState.SHOWING,
                        error = "Kan niet verder, ga terug naar home",
                        failureContext = null
                    )
                }
                viewModelScope.launch {
                    _navigation.emit(LessonNavigationEvent.BackToHome)
                }
            }
        }
    }

    /**
     * PATCH 6 & 9: Bepaal veilige recovery actie op basis van context
     */
    private fun determineRecoveryAction(
        failureContext: FailureContext?,
        completionStatus: CompletionStatus
    ): RecoveryAction {
        val stage = failureContext?.stage ?: FailureStage.UNKNOWN
        val exerciseType = failureContext?.exerciseType

        // PATCH 9: Type-specifieke handling
        when (exerciseType) {
            ExerciseType.WORKED_EXAMPLE -> {
                // Worked example: geen retry nodig, altijd door
                return RecoveryAction.CONTINUE_TO_NEXT
            }
            ExerciseType.GUIDED_PRACTICE -> {
                // Guided practice: als result al saved, door naar volgende
                return if (completionStatus >= CompletionStatus.RESULT_SAVED) {
                    RecoveryAction.CONTINUE_TO_NEXT
                } else {
                    RecoveryAction.SKIP_TO_NEXT
                }
            }
            else -> {
                // Normale antwoorden: context-afhankelijk
                return when (stage) {
                    FailureStage.RESULT_LOGGING -> {
                        // Result niet gelukt - skip veilig
                        RecoveryAction.SKIP_TO_NEXT
                    }
                    FailureStage.PROGRESS_UPDATE,
                    FailureStage.REWARD_UPDATE -> {
                        // Progress/rewards mislukt maar result saved - continue
                        RecoveryAction.CONTINUE_TO_NEXT
                    }
                    FailureStage.STATE_UPDATE,
                    FailureStage.ADVANCE -> {
                        // State was mogelijk deels geupdate - continue
                        RecoveryAction.CONTINUE_TO_NEXT
                    }
                    FailureStage.UNKNOWN -> {
                        // Onbekende fout - meest veilige optie
                        if (completionStatus >= CompletionStatus.RESULT_SAVED) {
                            RecoveryAction.CONTINUE_TO_NEXT
                        } else {
                            RecoveryAction.SKIP_TO_NEXT
                        }
                    }
                }
            }
        }
    }

    /**
     * PATCH 6: Mogelijke recovery acties
     */
    enum class RecoveryAction {
        CONTINUE_TO_NEXT,   // Ga veilig door naar volgende oefening
        SKIP_TO_NEXT,       // Skip huidige en ga door
        RETRY_CURRENT,      // Probeer huidige opnieuw (alleen als veilig)
        RETURN_TO_HOME      // Terug naar home (meest veilige fallback)
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
 * PATCH 2: Failure context voor veilige recovery
 */
data class FailureContext(
    val errorMessage: String,
    val exerciseId: String,
    val exerciseType: ExerciseType,
    val currentIndex: Int,
    val stage: FailureStage,
    val timestamp: Long = System.currentTimeMillis()
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
    val lessonPlan: LessonPlan? = null
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