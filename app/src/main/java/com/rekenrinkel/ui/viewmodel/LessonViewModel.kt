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
    
    // PATCH 1: Expliciete completion stage - gekoppeld aan huidige oefening
    private var currentCompletionStage = CompletionStage.NOT_STARTED
    private var currentStageExerciseId: String? = null  // Voor validatie dat stage bij juiste oefening hoort

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
        currentCompletionStage = CompletionStage.NOT_STARTED
        currentStageExerciseId = currentExercise.id

        // PATCH 8: Debug logging
        android.util.Log.d("LessonViewModel", "Starting completion for exercise ${currentExercise.id}, type: ${currentExercise.type}, mode: $mode")

        val needsFeedback = mode == CompletionMode.FEEDBACK_THEN_ADVANCE
        val skipMasteryUpdate = mode == CompletionMode.DIRECT_CONTINUE &&
            currentExercise.type == com.rekenrinkel.domain.model.ExerciseType.WORKED_EXAMPLE

        try {
            // PATCH 2: Stage-based completion - elke stap checkt of hij al gedaan is
            
            // === STAP 1: Resultaat loggen (idempotent) ===
            if (state.completionStage < CompletionStage.RESULT_LOGGED) {
                android.util.Log.d("LessonViewModel", "Step 1: Logging result for ${currentExercise.id}")
                val newResults = state.results + result
                _uiState.update { 
                    it.copy(
                        results = newResults,
                        completionStage = CompletionStage.RESULT_LOGGED,
                        completionStageExerciseId = currentExercise.id
                    )
                }
                currentCompletionStage = CompletionStage.RESULT_LOGGED
                android.util.Log.d("LessonViewModel", "Step 1: Result logged, stage: $currentCompletionStage")
            } else {
                android.util.Log.d("LessonViewModel", "Step 1: SKIP - result already logged")
            }

            // === STAP 2: Progress/mastery update (idempotent via repository) ===
            var outcome: ExerciseOutcome? = null
            if (!skipMasteryUpdate && state.completionStage < CompletionStage.PROGRESS_UPDATED) {
                try {
                    android.util.Log.d("LessonViewModel", "Step 2: Updating progress for ${currentExercise.id}")
                    val currentProgress = progressRepository.getOrCreateProgress(currentExercise.skillId)
                    outcome = lessonEngine.processExerciseResult(result, currentProgress)
                    progressRepository.updateProgress(outcome.updatedProgress)
                    _uiState.update { 
                        it.copy(completionStage = CompletionStage.PROGRESS_UPDATED)
                    }
                    currentCompletionStage = CompletionStage.PROGRESS_UPDATED
                    android.util.Log.d("LessonViewModel", "Step 2: Progress updated, stage: $currentCompletionStage")
                } catch (e: Exception) {
                    android.util.Log.e("LessonViewModel", "Step 2 FAILED: Progress update", e)
                    // Fout in progress update - log maar ga door, outcome blijft null
                }
            } else if (skipMasteryUpdate) {
                android.util.Log.d("LessonViewModel", "Step 2: SKIP - mastery update skipped for worked example")
            } else {
                android.util.Log.d("LessonViewModel", "Step 2: SKIP - progress already updated")
            }

            // === STAP 3: Rewards update (idempotent via repository) ===
            if (!skipMasteryUpdate && state.completionStage < CompletionStage.REWARDS_APPLIED) {
                try {
                    android.util.Log.d("LessonViewModel", "Step 3: Applying rewards for ${currentExercise.id}")
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
                    currentCompletionStage = CompletionStage.REWARDS_APPLIED
                    android.util.Log.d("LessonViewModel", "Step 3: Rewards applied, stage: $currentCompletionStage")
                } catch (e: Exception) {
                    android.util.Log.e("LessonViewModel", "Step 3 FAILED: Rewards update", e)
                    // Fout in rewards update - log maar ga door
                }
            } else if (skipMasteryUpdate) {
                android.util.Log.d("LessonViewModel", "Step 3: SKIP - rewards skipped for worked example")
            } else {
                android.util.Log.d("LessonViewModel", "Step 3: SKIP - rewards already applied")
            }

            // === STAP 4: Prepare advance - UI state updaten ===
            if (state.completionStage < CompletionStage.READY_TO_ADVANCE) {
                android.util.Log.d("LessonViewModel", "Step 4: Preparing advance for ${currentExercise.id}")
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
                currentCompletionStage = CompletionStage.READY_TO_ADVANCE
                android.util.Log.d("LessonViewModel", "Step 4: Ready to advance, stage: $currentCompletionStage")
            } else {
                android.util.Log.d("LessonViewModel", "Step 4: SKIP - already ready to advance")
            }

            // === STAP 5: Advance / Complete ===
            if (state.completionStage < CompletionStage.DONE) {
                android.util.Log.d("LessonViewModel", "Step 5: Advancing from ${currentExercise.id}")
                if (needsFeedback) {
                    delay(feedbackDurationMs)
                }
                advanceToNextExercise()
                _uiState.update { it.copy(completionStage = CompletionStage.DONE) }
                currentCompletionStage = CompletionStage.DONE
                android.util.Log.d("LessonViewModel", "Step 5: Advance completed, stage: DONE")
            } else {
                android.util.Log.d("LessonViewModel", "Step 5: SKIP - already done")
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
        android.util.Log.e("LessonViewModel", "Completion stage at failure: ${state.completionStage} (exercise: ${state.completionStageExerciseId})")

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
        currentCompletionStage = CompletionStage.NOT_STARTED
        currentStageExerciseId = null

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
        android.util.Log.d("LessonViewModel", "UI completion stage: ${state.completionStage} (exercise: ${state.completionStageExerciseId})")
        android.util.Log.d("LessonViewModel", "Private completion stage: $currentCompletionStage (exercise: $currentStageExerciseId)")
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

        when (recoveryAction) {
            RecoveryAction.CONTINUE_TO_NEXT -> {
                // Veilige continue naar volgende oefening
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
                    currentCompletionStage = CompletionStage.NOT_STARTED
                    currentStageExerciseId = null
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
                            failureContext = null,
                            completionStage = CompletionStage.NOT_STARTED,
                            completionStageExerciseId = null
                        )
                    }
                    currentlyCompletingExerciseId = null
                    currentCompletionStage = CompletionStage.NOT_STARTED
                    currentStageExerciseId = null
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
                            failureContext = null,
                            completionStage = CompletionStage.NOT_STARTED,
                            completionStageExerciseId = null
                        )
                    }
                    currentlyCompletingExerciseId = null
                    currentCompletionStage = CompletionStage.NOT_STARTED
                    currentStageExerciseId = null
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
     * PATCH 3 & 6: Bepaal veilige recovery actie op basis van completionStage (bron van waarheid)
     * 
     * Recovery logica per completionStage:
     * - NOT_STARTED: Oefening nog niet begonnen → veilig om opnieuw te proberen of te skippen
     * - RESULT_LOGGED: Resultaat al gelogd → niet opnieuw loggen, progress verder afwerken of skippen
     * - PROGRESS_UPDATED: Progress al geupdate → rewards/advance verder afwerken
     * - REWARDS_APPLIED: Rewards al toegepast → direct advance
     * - READY_TO_ADVANCE: Al klaar om door te gaan → direct advanceToNextExercise()
     * - DONE: Al afgerond → niets meer doen
     */
    private fun determineRecoveryAction(
        failureContext: FailureContext?,
        completionStage: CompletionStage
    ): RecoveryAction {
        val stage = failureContext?.stage ?: FailureStage.UNKNOWN
        val exerciseType = failureContext?.exerciseType

        // PATCH 3: Stage-gebaseerde recovery als primaire logica
        return when (completionStage) {
            CompletionStage.DONE -> {
                // Oefening is al volledig afgerond, alleen advance doen
                RecoveryAction.CONTINUE_TO_NEXT
            }
            CompletionStage.READY_TO_ADVANCE -> {
                // UI staat klaar, alleen advance uitvoeren
                RecoveryAction.CONTINUE_TO_NEXT
            }
            CompletionStage.REWARDS_APPLIED -> {
                // Rewards zijn al toegepast, direct advance is veilig
                RecoveryAction.CONTINUE_TO_NEXT
            }
            CompletionStage.PROGRESS_UPDATED -> {
                // Progress is geupdate maar rewards mogelijk niet
                // Worked example: geen rewards nodig
                // Normale oefening: rewards zijn nice-to-have, advance is veilig
                RecoveryAction.CONTINUE_TO_NEXT
            }
            CompletionStage.RESULT_LOGGED -> {
                // Resultaat is gelogd, progress moet nog
                // Als dit een retry zou zijn, zouden we result dubbel loggen
                // Dus beter om door te gaan naar volgende (progress wordt niet dubbel geupdate door repository)
                when (exerciseType) {
                    ExerciseType.WORKED_EXAMPLE -> RecoveryAction.CONTINUE_TO_NEXT
                    else -> RecoveryAction.CONTINUE_TO_NEXT  // Progress update is idempotent via repository
                }
            }
            CompletionStage.NOT_STARTED -> {
                // Oefening nog niet begonnen, meeste opties open
                when (exerciseType) {
                    ExerciseType.WORKED_EXAMPLE -> {
                        // Worked example: geen retry nodig, altijd door
                        RecoveryAction.CONTINUE_TO_NEXT
                    }
                    else -> {
                        // Bij NOT_STARTED en een error: waarschijnlijk ging iets mis voor we begonnen
                        // Veiligste is om te skippen naar volgende
                        when (stage) {
                            FailureStage.RESULT_LOGGING -> RecoveryAction.SKIP_TO_NEXT
                            FailureStage.UNKNOWN -> RecoveryAction.SKIP_TO_NEXT
                            else -> RecoveryAction.CONTINUE_TO_NEXT
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