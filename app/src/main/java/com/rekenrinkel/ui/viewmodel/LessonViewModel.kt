package com.rekenrinkel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.repository.ProfileRepository
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.*
import com.rekenrinkel.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * OPTIE B - PRODUCTIE ARCHITECTUUR: Stage-based LessonViewModel
 * 
 * Deze ViewModel implementeert een strikte state machine voor lesson flow:
 * 
 * LessonStepState (UI states):
 * - SHOWING → PROCESSING → FEEDBACK → ADVANCING → SHOWING (next)
 * - ERROR kan op elk moment optreden, met recovery
 * 
 * CompletionStage (interne flow stages):
 * NOT_STARTED → RESULT_LOGGED → PROGRESS_UPDATED → REWARDS_APPLIED → READY_TO_ADVANCE → DONE
 * 
 * CompletionMode (per exercise type):
 * - FEEDBACK_THEN_ADVANCE: normale oefeningen (result + progress + rewards + feedback)
 * - DIRECT_CONTINUE: worked examples (result only, direct advance)
 * - SKIP_ADVANCE: skipped exercises (result only, no progress/rewards)
 * 
 * FailureStage (error context):
 * VALIDATION, RESULT_LOGGING, PROGRESS_UPDATE, REWARD_UPDATE, ADVANCE, UNKNOWN
 * 
 * KERNPRINCIPES:
 * 1. DONE moet altijd bereikt worden vóór advance naar volgende oefening
 * 2. Elke stage is idempotent - meerdere calls zijn veilig
 * 3. Geen dubbele side effects door strikte stage guards
 * 4. Recovery evalueert per stage wat al gebeurd is
 * 5. Skip heeft strikte semantiek: exact één result, geen validator, direct advance
 */
class LessonViewModel(
    private val progressRepository: ProgressRepository,
    private val profileRepository: ProfileRepository,
    private val settingsDataStore: SettingsDataStore,
    private val exerciseEngine: ExerciseEngine,
    private val exerciseValidator: ExerciseValidator
) : ViewModel() {

    private val lessonEngine = LessonEngine(exerciseEngine, progressRepository)

    // State
    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    // Navigation events
    private val _navigation = MutableSharedFlow<LessonNavigationEvent>()
    val navigation: SharedFlow<LessonNavigationEvent> = _navigation.asSharedFlow()

    // Guards tegen dubbele verwerking
    private val completedExerciseIds = mutableSetOf<String>()
    private var currentlyCompletingExerciseId: String? = null
    private var lessonStarted = false

    /**
     * Start een les - alleen als nog niet gestart
     */
    fun startLesson() {
        if (lessonStarted || _uiState.value.isLoading) return
        lessonStarted = true
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val profile = profileRepository.getProfile().first()
                    ?: throw IllegalStateException("No profile found")
                val userProfile = UserProfile(
                    name = profile.name,
                    age = profile.age,
                    theme = profile.theme
                )
                val lesson = lessonEngine.buildLesson(userProfile)
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isActive = true,
                        exercises = lesson.exercises,
                        currentIndex = 0,
                        currentExercise = lesson.exercises.firstOrNull(),
                        results = emptyList(),
                        stepState = LessonStepState.SHOWING,
                        completionStage = CompletionStage.NOT_STARTED,
                        completionStageExerciseId = null,
                        error = null
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("LessonViewModel", "Failed to start lesson", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Kon les niet starten: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Start timer voor huidige oefening
     */
    fun startExerciseTimer() {
        _uiState.update {
            it.copy(exerciseStartTime = System.currentTimeMillis())
        }
    }

    /**
     * PATCH 5-7: Volledig idempotente entrypoints
     * 
     * IDEMPOTENTIE GARANTIES:
     * - currentlyCompletingExerciseId guard blokkeert dubbele calls
     * - completedExerciseIds guard blokkeert re-verwerking
     * - CompletionStage guards blokkeren re-verwerking per stage
     * 
     * STAGE GEDREVEN SIDE EFFECTS:
     * - NOT_STARTED: alles nog mogelijk
     * - RESULT_LOGGED: nooit opnieuw result loggen
     * - PROGRESS_UPDATED: nooit opnieuw progress
     * - REWARDS_APPLIED: nooit opnieuw rewards/XP/stars/badges
     * - READY_TO_ADVANCE: alleen nog veilige finalize/advance
     * - DONE: niets meer
     *
     * SEMANTIEK:
     * 1. Valideer antwoord
     * 2. Log resultaat
     * 3. Update progress
     * 4. Update rewards
     * 5. Toon feedback
     * 6. Advance na delay
     * 
     * DUBBELE SUBMIT PREVENTIE:
     * - currentlyCompletingExerciseId guard (eerste check)
     * - State check: alleen in SHOWING state
     * - Processing guard: direct naar PROCESSING state
     * - Completion guard: exercise mag niet al DONE zijn
     */
    fun submitAnswer(answer: String) {
        // Entry-guards met actuele state
        val exercise = currentExerciseOrNull()
        if (exercise == null) {
            android.util.Log.w("LessonViewModel", "submitAnswer ignored - no current exercise")
            return
        }

        // PATCH 1: Harde guards - check currentlyCompletingExerciseId eerst
        if (currentlyCompletingExerciseId == exercise.id) {
            android.util.Log.w("LessonViewModel", "submitAnswer ignored - exercise ${exercise.id} currently being processed")
            return
        }

        // Guard: les niet actief
        if (!currentUiState().isActive) {
            android.util.Log.w("LessonViewModel", "submitAnswer ignored - lesson not active")
            return
        }

        // Guard: alleen toestaan in SHOWING state
        if (currentStepState() != LessonStepState.SHOWING) {
            android.util.Log.w("LessonViewModel", "submitAnswer ignored - wrong state: ${currentStepState()}")
            return
        }

        // Guard: oefening al voltooid (completedExerciseIds check)
        if (isExerciseCompleted(exercise.id)) {
            android.util.Log.w("LessonViewModel", "submitAnswer ignored - exercise already completed")
            return
        }

        // Guard: oefening al DONE (completionStage check)
        if (currentCompletionStageExerciseId() == exercise.id &&
            currentCompletionState() == CompletionStage.DONE) {
            android.util.Log.w("LessonViewModel", "submitAnswer ignored - exercise already DONE")
            return
        }

        // Guard: oefening al in completion flow
        if (currentCompletionStageExerciseId() == exercise.id &&
            currentCompletionState() >= CompletionStage.RESULT_LOGGED) {
            android.util.Log.w("LessonViewModel", "submitAnswer ignored - exercise already being processed")
            return
        }

        viewModelScope.launch {
            // PATCH 2: Zet currentlyCompletingExerciseId vroeg, vóór side effects
            currentlyCompletingExerciseId = exercise.id

            // Direct naar PROCESSING state om dubbele submits te voorkomen
            _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }

            try {
                // Re-read startTime actueel voor accurate meting
                val startTime = currentExerciseStartTime()
                val responseTime = System.currentTimeMillis() - (startTime ?: System.currentTimeMillis())

                val isCorrect = exerciseValidator.validate(exercise, answer)

                val result = DetailedExerciseResult(
                    exerciseId = exercise.id,
                    skillId = exercise.skillId,
                    isCorrect = isCorrect,
                    givenAnswer = answer,
                    correctAnswer = exercise.correctAnswer,
                    responseTimeMs = responseTime,
                    difficultyTier = exercise.difficulty,
                    representationUsed = exercise.visualData?.type?.name ?: "SYMBOLS"
                )

                // Verwerk completion met FEEDBACK_THEN_ADVANCE mode
                finishCurrentExercise(
                    result = result,
                    mode = CompletionMode.FEEDBACK_THEN_ADVANCE
                )
            } catch (e: Exception) {
                android.util.Log.e("LessonViewModel", "Error in submitAnswer", e)
                _uiState.update {
                    it.copy(
                        stepState = LessonStepState.ERROR,
                        error = "Antwoord kon niet worden verwerkt"
                    )
                }
            } finally {
                // PATCH 4: Reset guard op semantisch juiste plek
                // Reset pas na succesvolle completion (DONE) of expliciete error
                if (currentCompletionState() == CompletionStage.DONE ||
                    currentStepState() == LessonStepState.ERROR) {
                    currentlyCompletingExerciseId = null
                    android.util.Log.d("LessonViewModel", "[GUARD] Reset after submit for exercise ${exercise.id}")
                }
            }
        }
    }

    /**
     * PATCH 3: Dubbele user-actions blijven geblokkeerd
     * - Tweede submitAnswer() op zelfde item → return
     * - Tweede skipExercise() op zelfde item → return  
     * - Tweede continueWorkedExample() op zelfde item → return
     * 
     * PATCH 5: Skip volledig idempotent
     *
     * IDEMPOTENTIE GARANTIES:
     * - currentlyCompletingExerciseId guard blokkeert dubbele skip calls
     * - completedExerciseIds guard blokkeert re-skip
     * - SKIP_ADVANCE mode: geen progress/rewards side effects
     *
     * VERPLICHTE EIGENSCHAPPEN:
     * 1. Exact één result wordt gelogd (givenAnswer = "[skipped]")
     * 2. Geen validator aanroep
     * 3. Geen gewone feedback semantiek (geen FEEDBACK state)
     * 4. Directe veilige advance
     * 5. Geen progress/rewards side effects (SKIP_ADVANCE mode)
     * 6. DONE na correcte afhandeling
     *
     * SKIPPED exercises tellen mee voor les-voortgang maar niet voor statistieken.
     */
    fun skipExercise() {
        // Entry-guards met actuele state
        val exercise = currentExerciseOrNull()
        if (exercise == null) {
            android.util.Log.w("LessonViewModel", "skipExercise ignored - no current exercise")
            return
        }

        // PATCH 1: Harde guard - check currentlyCompletingExerciseId eerst
        if (currentlyCompletingExerciseId == exercise.id) {
            android.util.Log.w("LessonViewModel", "skipExercise ignored - exercise ${exercise.id} currently being processed")
            return
        }

        // Guard: les niet actief
        if (!currentUiState().isActive) {
            android.util.Log.w("LessonViewModel", "skipExercise ignored - lesson not active")
            return
        }

        // Guard: al in verwerking
        val currentStep = currentStepState()
        if (currentStep == LessonStepState.PROCESSING || currentStep == LessonStepState.ADVANCING) {
            android.util.Log.w("LessonViewModel", "skipExercise ignored - already processing")
            return
        }

        // Guard: niet in SHOWING state
        if (currentStep != LessonStepState.SHOWING) {
            android.util.Log.w("LessonViewModel", "skipExercise ignored - wrong state: $currentStep")
            return
        }

        // Guard: oefening al voltooid
        if (isExerciseCompleted(exercise.id)) {
            android.util.Log.w("LessonViewModel", "skipExercise ignored - already completed")
            return
        }

        viewModelScope.launch {
            // PATCH 2: Zet currentlyCompletingExerciseId vroeg, vóór side effects
            currentlyCompletingExerciseId = exercise.id

            // Direct naar PROCESSING om race conditions te voorkomen
            _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }

            // Skip resultaat: altijd incorrect, geen tijd, geen representatie
            val result = DetailedExerciseResult(
                exerciseId = exercise.id,
                skillId = exercise.skillId,
                isCorrect = false,
                givenAnswer = "[skipped]",
                correctAnswer = exercise.correctAnswer,
                responseTimeMs = 0,
                difficultyTier = exercise.difficulty,
                representationUsed = "SKIP"
            )

            android.util.Log.d("LessonViewModel", "[SKIP] Processing skip for exercise ${exercise.id}")

            try {
                // Skip gebruikt SKIP_ADVANCE mode: log result, maar geen progress/rewards, direct advance
                finishCurrentExercise(
                    result = result,
                    mode = CompletionMode.SKIP_ADVANCE
                )
            } finally {
                // PATCH 4: Reset guard na completion of error
                if (currentCompletionState() == CompletionStage.DONE ||
                    currentStepState() == LessonStepState.ERROR) {
                    currentlyCompletingExerciseId = null
                    android.util.Log.d("LessonViewModel", "[GUARD] Reset after skip for exercise ${exercise.id}")
                }
            }
        }
    }

    /**
     * Worked example: gebruiker klikt "Begrepen, verder"
     *
     * SEMANTIEK:
     * 1. Geen validatie nodig (worked example = demonstratie)
     * 2. Log resultaat als "gezien"
     * 3. Geen progress/rewards (DIRECT_CONTINUE mode)
     * 4. Direct advance zonder feedback delay
     * 5. DONE na afhandeling
     *
     * Worked examples zijn didactische demonstraties, geen te evalueren antwoorden.
     */
    fun continueWorkedExample() {
        // Entry-guards met actuele state
        val exercise = currentExerciseOrNull()
        if (exercise == null) {
            android.util.Log.w("LessonViewModel", "continueWorkedExample ignored - no current exercise")
            return
        }

        // PATCH 1: Harde guard - check currentlyCompletingExerciseId eerst
        if (currentlyCompletingExerciseId == exercise.id) {
            android.util.Log.w("LessonViewModel", "continueWorkedExample ignored - exercise ${exercise.id} currently being processed")
            return
        }

        // Guard: les niet actief
        if (!currentUiState().isActive) {
            android.util.Log.w("LessonViewModel", "continueWorkedExample ignored - lesson not active")
            return
        }

        // Guard: alleen in SHOWING state
        if (currentStepState() != LessonStepState.SHOWING) {
            android.util.Log.w("LessonViewModel", "continueWorkedExample ignored - wrong state: ${currentStepState()}")
            return
        }

        // Guard: oefening al voltooid
        if (isExerciseCompleted(exercise.id)) {
            android.util.Log.w("LessonViewModel", "continueWorkedExample ignored - already completed")
            return
        }

        // Guard: oefening moet van type WORKED_EXAMPLE zijn
        if (exercise.type != ExerciseType.WORKED_EXAMPLE) {
            android.util.Log.w("LessonViewModel", "continueWorkedExample ignored - not a worked example")
            return
        }

        viewModelScope.launch {
            // PATCH 2: Zet currentlyCompletingExerciseId vroeg, vóór side effects
            currentlyCompletingExerciseId = exercise.id

            _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }

            val result = DetailedExerciseResult(
                exerciseId = exercise.id,
                skillId = exercise.skillId,
                isCorrect = true, // Worked example telt als "gezien"
                givenAnswer = "[worked_example]",
                correctAnswer = exercise.correctAnswer,
                responseTimeMs = 0,
                difficultyTier = exercise.difficulty,
                representationUsed = "WORKED_EXAMPLE"
            )
            
            android.util.Log.d("LessonViewModel", "[WORKED] Continuing worked example ${exercise.id}")

            try {
                // DIRECT_CONTINUE: log result, maar geen progress/rewards, direct advance
                finishCurrentExercise(
                    result = result,
                    mode = CompletionMode.DIRECT_CONTINUE
                )
            } finally {
                // PATCH 4: Reset guard na completion of error
                if (currentCompletionState() == CompletionStage.DONE ||
                    currentStepState() == LessonStepState.ERROR) {
                    currentlyCompletingExerciseId = null
                    android.util.Log.d("LessonViewModel", "[GUARD] Reset after worked for exercise ${exercise.id}")
                }
            }
        }
    }

    /**
     * ROBUUSTE LESSONFLOW: Stage-based completion met harde side-effect afscherming
     * 
     * KERNPRINCIPES:
     * 1. Elke stage is idempotent - meerdere calls met zelfde exerciseId zijn veilig
     * 2. Onder RESULT_LOGGED: result wordt niet opnieuw gelogd
     * 3. Onder PROGRESS_UPDATED: progress wordt niet opnieuw geüpdatet
     * 4. Onder REWARDS_APPLIED: rewards worden niet opnieuw toegepast
     * 5. Onder READY_TO_ADVANCE: alleen nog veilige advance mogelijk
     * 6. Onder DONE: niets meer - exercise is definitief afgesloten
     * 7. Advance gebeurt alleen als huidige exercise DONE is
     * 8. Recovery evalueert per stage wat al gebeurd is
     * 
     * Completion stages: NOT_STARTED → RESULT_LOGGED → PROGRESS_UPDATED → REWARDS_APPLIED → READY_TO_ADVANCE → DONE
     * Failure stages: VALIDATION, RESULT_LOGGING, PROGRESS_UPDATE, REWARD_UPDATE, ADVANCE, UNKNOWN
     */
    private suspend fun finishCurrentExercise(
        result: DetailedExerciseResult,
        mode: CompletionMode
    ) {
        val exerciseId = result.exerciseId

        // HARDE GUARD: onder DONE = direct return, niets meer doen
        if (currentCompletionState() == CompletionStage.DONE && 
            currentUiState().completionStageExerciseId == exerciseId) {
            android.util.Log.w("LessonViewModel", "[COMPLETION] Exercise $exerciseId already DONE - no action")
            return
        }

        // HARDE GUARD: al voltooid = direct return, geen side effects
        if (isExerciseCompleted(exerciseId)) {
            android.util.Log.w("LessonViewModel", "[COMPLETION] Exercise $exerciseId already completed - ignoring")
            return
        }

        // PATCH 1-2: currentlyCompletingExerciseId wordt door entrypoints beheerd
        // De entrypoints (submitAnswer, skipExercise, continueWorkedExample) zetten de guard
        // Alleen resetten we hier in het finally block na completion

        try {
            // STAGE 1: Log resultaat (idempotent - alleen als nog niet gelogd voor deze exercise)
            val stateBeforeLog = currentUiState()
            if (stateBeforeLog.completionStageExerciseId != exerciseId ||
                stateBeforeLog.completionStage < CompletionStage.RESULT_LOGGED) {
                
                _uiState.update {
                    it.copy(
                        results = it.results + result,
                        completionStage = CompletionStage.RESULT_LOGGED,
                        completionStageExerciseId = exerciseId
                    )
                }
                android.util.Log.d("LessonViewModel", "[COMPLETION] Stage RESULT_LOGGED for $exerciseId")
            } else {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Stage RESULT_LOGGED skipped - already logged for $exerciseId")
            }

            // STAGE 2 & 3: Update progress en rewards (alleen voor FEEDBACK_THEN_ADVANCE mode)
            // SKIP_ADVANCE en DIRECT_CONTINUE slaan progress/rewards over voor snelle flow
            if (mode == CompletionMode.FEEDBACK_THEN_ADVANCE) {
                
                // STAGE 2: Progress update (idempotent - alleen als nog niet geüpdatet)
                if (currentCompletionState() < CompletionStage.PROGRESS_UPDATED) {
                    try {
                        val currentProgress = progressRepository.getOrCreateProgress(result.skillId)
                        val outcome = lessonEngine.processExerciseResult(result, currentProgress)
                        progressRepository.updateProgress(outcome.updatedProgress)
                        
                        _uiState.update {
                            it.copy(completionStage = CompletionStage.PROGRESS_UPDATED)
                        }
                        android.util.Log.d("LessonViewModel", "[COMPLETION] Stage PROGRESS_UPDATED for $exerciseId")
                    } catch (e: Exception) {
                        android.util.Log.e("LessonViewModel", "[FAILURE] Progress update failed", e)
                        handleFailure("Voortgang kon niet worden opgeslagen", FailureStage.PROGRESS_UPDATE, exerciseId)
                        return  // Exit zonder advance - gebruiker kan recovery proberen
                    }
                } else {
                    android.util.Log.d("LessonViewModel", "[COMPLETION] Stage PROGRESS_UPDATED skipped - already done for $exerciseId")
                }

                // STAGE 3: Rewards update (idempotent - alleen als nog niet toegepast)
                if (currentCompletionState() < CompletionStage.REWARDS_APPLIED) {
                    try {
                        val currentRewards = profileRepository.getRewards()
                        val xpEarned = calculateXpFromResult(result)
                        val updatedRewards = currentRewards.addXp(xpEarned).updateStreak()
                        profileRepository.updateRewards(updatedRewards)
                        
                        _uiState.update {
                            it.copy(
                                completionStage = CompletionStage.REWARDS_APPLIED,
                                xpEarnedThisLesson = it.xpEarnedThisLesson + xpEarned,
                                lastAnswerCorrect = result.isCorrect
                            )
                        }
                        android.util.Log.d("LessonViewModel", "[COMPLETION] Stage REWARDS_APPLIED for $exerciseId")
                    } catch (e: Exception) {
                        android.util.Log.e("LessonViewModel", "[FAILURE] Rewards update failed", e)
                        handleFailure("Rewards konden niet worden opgeslagen", FailureStage.REWARD_UPDATE, exerciseId)
                        return  // Exit zonder advance - gebruiker kan recovery proberen
                    }
                } else {
                    android.util.Log.d("LessonViewModel", "[COMPLETION] Stage REWARDS_APPLIED skipped - already done for $exerciseId")
                }
            } else {
                // SKIP_ADVANCE of DIRECT_CONTINUE: sla progress/rewards over
                android.util.Log.d("LessonViewModel", "[COMPLETION] Progress/Rewards skipped for mode $mode")
            }

            // STAGE 4: Ready to advance - alle side effects zijn nu veilig uitgevoerd
            // Alleen updaten als nog niet bereikt
            if (currentCompletionState() < CompletionStage.READY_TO_ADVANCE) {
                _uiState.update { it.copy(completionStage = CompletionStage.READY_TO_ADVANCE) }
                android.util.Log.d("LessonViewModel", "[COMPLETION] Stage READY_TO_ADVANCE for $exerciseId")
            } else {
                android.util.Log.d("LessonViewModel", "[COMPLETION] Stage READY_TO_ADVANCE already set for $exerciseId")
            }

            // STAGE 5: DONE - exercise is definitief afgesloten, advance is nu veilig
            // Dit is het ENIGE moment waarop we de exercise als completed markeren
            completedExerciseIds.add(exerciseId)
            _uiState.update {
                it.copy(
                    completionStage = CompletionStage.DONE,
                    completionStageExerciseId = exerciseId,
                    lastAnswerCorrect = result.isCorrect
                )
            }
            android.util.Log.d("LessonViewModel", "[COMPLETION] Stage DONE for $exerciseId - advance allowed")

            // STAGE 6: UI state transition en advance (alleen als DONE bereikt)
            when (mode) {
                CompletionMode.FEEDBACK_THEN_ADVANCE -> {
                    _uiState.update { it.copy(stepState = LessonStepState.FEEDBACK) }
                    delay(1000)
                    
                    // Alleen naar ADVANCING als we nog steeds DONE zijn voor deze exercise
                    val stateBeforeAdvance = currentUiState()
                    if (stateBeforeAdvance.completionStageExerciseId == exerciseId && 
                        stateBeforeAdvance.completionStage == CompletionStage.DONE) {
                        _uiState.update { it.copy(stepState = LessonStepState.ADVANCING) }
                    }
                    
                    advanceToNextExercise(exerciseId)
                }
                CompletionMode.DIRECT_CONTINUE,
                CompletionMode.SKIP_ADVANCE -> {
                    // Direct naar ADVANCING zonder feedback delay
                    val stateBeforeAdvance = currentUiState()
                    if (stateBeforeAdvance.completionStageExerciseId == exerciseId && 
                        stateBeforeAdvance.completionStage == CompletionStage.DONE) {
                        _uiState.update { it.copy(stepState = LessonStepState.ADVANCING) }
                    }
                    
                    advanceToNextExercise(exerciseId)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LessonViewModel", "[FAILURE] Unexpected error in finishCurrentExercise", e)
            handleFailure("Er ging iets mis", FailureStage.UNKNOWN, exerciseId)
        }
        // PATCH 1: GEEN finally block met onvoorwaardelijke reset
        // De guard wordt alleen gereset in entrypoints wanneer item echt klaar is
    }

    /**
     * Bereken XP uit een exercise result
     */
    private fun calculateXpFromResult(result: DetailedExerciseResult): Int {
        val baseXp = if (result.isCorrect) 10 else 0
        val speedBonus = if (result.isCorrect && result.responseTimeMs < 3000) 5 else 0
        return baseXp + speedBonus
    }

    /**
     * Advance naar volgende oefening of finish les
     * 
     * HARDE EIS: advance gebeurt alleen als:
     * 1. De exerciseId matcht met de huidige completionStageExerciseId
     * 2. De completionStage == DONE
     * 
     * Dit voorkomt:
     * - Stale advances van eerdere exercises
     * - Double advances bij rapid UI events
     * - Advances tijdens error recovery
     */
    private suspend fun advanceToNextExercise(completedExerciseId: String) {
        val currentState = currentUiState()
        
        // HARDE GUARD: alleen advance als huidige exercise definitief DONE is
        if (currentState.completionStageExerciseId != completedExerciseId) {
            android.util.Log.w("LessonViewModel", "[ADVANCE] BLOCKED - exerciseId mismatch. " +
                    "Expected: $completedExerciseId, Actual: ${currentState.completionStageExerciseId}")
            return
        }
        
        if (currentState.completionStage != CompletionStage.DONE) {
            android.util.Log.w("LessonViewModel", "[ADVANCE] BLOCKED - not DONE yet. " +
                    "Current stage: ${currentState.completionStage}")
            return
        }

        val nextIndex = currentState.currentIndex + 1
        val exercises = currentState.exercises

        if (nextIndex >= exercises.size) {
            finishLesson()
        } else {
            val nextExercise = exercises[nextIndex]
            _uiState.update {
                it.copy(
                    currentIndex = nextIndex,
                    currentExercise = nextExercise,
                    stepState = LessonStepState.SHOWING,
                    completionStage = CompletionStage.NOT_STARTED,
                    completionStageExerciseId = null,
                    exerciseStartTime = System.currentTimeMillis()
                )
            }
            android.util.Log.d("LessonViewModel", "[ADVANCE] Advanced to exercise $nextIndex: ${nextExercise.id}")
        }
    }

    /**
     * Finish les en navigeer naar result screen
     */
    private suspend fun finishLesson() {
        val results = currentUiState().results
        val xpEarned = currentUiState().xpEarnedThisLesson

        val sessionResult = SessionResult(
            exercises = results.map { r ->
                ExerciseResult(
                    exerciseId = r.exerciseId,
                    skillId = r.skillId,
                    isCorrect = r.isCorrect,
                    responseTimeMs = r.responseTimeMs,
                    givenAnswer = r.givenAnswer
                )
            },
            xpEarned = xpEarned
        )

        _uiState.update { it.copy(isActive = false, stepState = LessonStepState.COMPLETED) }

        _navigation.emit(LessonNavigationEvent.LessonComplete(
            result = sessionResult,
            xpTotal = xpEarned,
            badges = emptyList() // V1: simplified rewards
        ))
    }

    /**
     * Error handling - toon fout maar laat gebruiker door kunnen gaan
     * 
     * BELANGRIJK: De huidige exercise wordt NIET als completed gemarkeerd bij een error.
     * Dit voorkomt dat een halve completion als "klaar" wordt beschouwd.
     * Bij recovery wordt de exercise overgeslagen zonder resultaat te loggen.
     */
    private fun handleFailure(message: String, stage: FailureStage, exerciseId: String) {
        _uiState.update {
            it.copy(
                stepState = LessonStepState.ERROR,
                error = "$message (${stage.name})"
            )
        }
        android.util.Log.e("LessonViewModel", "[ERROR] Exercise $exerciseId failed at stage $stage: $message")
    }

    /**
     * PATCH 6: Recovery exact stage-gedreven
     *
     * IDEMPOTENTIE GARANTIES:
     * - RESULT_LOGGED al bereikt: result nooit opnieuw loggen
     * - PROGRESS_UPDATED al bereikt: progress nooit opnieuw updaten
     * - REWARDS_APPLIED al bereikt: rewards nooit opnieuw toekennen
     * - READY_TO_ADVANCE al bereikt: direct veilige finalize/advance
     * - DONE al bereikt: geen side effects meer
     *
     * RECOVERY LOGICA per stage:
     * - NOT_STARTED: geen side effects gedaan, log result nu alsnog, daarna advance
     * - RESULT_LOGGED: alleen result gelogd, skip progress/rewards, direct advance
     * - PROGRESS_UPDATED: result + progress gedaan, skip rewards, direct advance
     * - REWARDS_APPLIED: alles gedaan, direct advance
     * - READY_TO_ADVANCE of DONE: direct advance
     *
     * BELANGRIJK: recovery doet nooit dubbele side effects. Als een stage al bereikt is,
     * wordt die overgeslagen.
     */
    fun continueAfterError() {
        viewModelScope.launch {
            val state = currentUiState()
            val currentExercise = state.currentExercise
            val currentExerciseId = currentExercise?.id
            val currentStage = state.completionStage
            
            if (currentExerciseId == null) {
                android.util.Log.e("LessonViewModel", "[RECOVERY] No current exercise to recover")
                return@launch
            }
            
            android.util.Log.w("LessonViewModel", "[RECOVERY] Recovering exercise $currentExerciseId from stage $currentStage")
            
            // Markeer als completed zodat we niet opnieuw proberen
            completedExerciseIds.add(currentExerciseId)
            
            when (currentStage) {
                CompletionStage.NOT_STARTED -> {
                    // Niets gedaan yet - log result nu alsnog zodat er een record is
                    // Dit voorkomt dat de oefening volledig verloren gaat
                    val result = DetailedExerciseResult(
                        exerciseId = currentExerciseId,
                        skillId = currentExercise.skillId,
                        isCorrect = false,
                        givenAnswer = "[error_recovery]",
                        correctAnswer = currentExercise.correctAnswer,
                        responseTimeMs = 0,
                        difficultyTier = currentExercise.difficulty,
                        representationUsed = "ERROR_RECOVERY"
                    )
                    _uiState.update {
                        it.copy(
                            results = it.results + result,
                            completionStage = CompletionStage.DONE,
                            completionStageExerciseId = currentExerciseId,
                            lastAnswerCorrect = false
                        )
                    }
                    android.util.Log.d("LessonViewModel", "[RECOVERY] Logged recovery result for $currentExerciseId")
                }
                CompletionStage.RESULT_LOGGED,
                CompletionStage.PROGRESS_UPDATED,
                CompletionStage.REWARDS_APPLIED,
                CompletionStage.READY_TO_ADVANCE -> {
                    // Side effects al (deels) gedaan, markeer als DONE
                    _uiState.update {
                        it.copy(
                            completionStage = CompletionStage.DONE,
                            completionStageExerciseId = currentExerciseId,
                            lastAnswerCorrect = false
                        )
                    }
                    android.util.Log.d("LessonViewModel", "[RECOVERY] Marked $currentExerciseId as DONE (was at $currentStage)")
                }
                CompletionStage.DONE -> {
                    // Al klaar, niets doen
                    android.util.Log.d("LessonViewModel", "[RECOVERY] Exercise $currentExerciseId already DONE")
                }
            }
            
            // Reset error state en advance
            advanceAfterError()
        }
    }

    /**
     * Advance na error - reset state en ga naar volgende
     */
    private suspend fun advanceAfterError() {
        val currentState = currentUiState()
        val nextIndex = currentState.currentIndex + 1
        val exercises = currentState.exercises

        if (nextIndex >= exercises.size) {
            finishLesson()
        } else {
            val nextExercise = exercises[nextIndex]
            _uiState.update {
                it.copy(
                    currentIndex = nextIndex,
                    currentExercise = nextExercise,
                    stepState = LessonStepState.SHOWING,
                    completionStage = CompletionStage.NOT_STARTED,
                    completionStageExerciseId = null,
                    error = null,
                    exerciseStartTime = System.currentTimeMillis()
                )
            }
            android.util.Log.d("LessonViewModel", "[RECOVERY] Advanced to exercise $nextIndex: ${nextExercise.id}")
        }
    }

    private fun isExerciseCompleted(exerciseId: String): Boolean {
        return completedExerciseIds.contains(exerciseId)
    }

    // Actuele state helpers - voorkomen stale snapshots
    private fun currentUiState(): LessonUiState = _uiState.value

    private fun currentExerciseOrNull(): Exercise? = currentUiState().currentExercise

    private fun currentStepState(): LessonStepState = currentUiState().stepState

    private fun currentCompletionState(): CompletionStage = currentUiState().completionStage

    private fun currentCompletionStageExerciseId(): String? = currentUiState().completionStageExerciseId

    private fun currentExerciseStartTime(): Long? = currentUiState().exerciseStartTime

    private fun currentIndex(): Int = currentUiState().currentIndex

    private fun currentLastAnswerCorrect(): Boolean? = currentUiState().lastAnswerCorrect

    private fun currentExerciseId(): String? = currentExerciseOrNull()?.id

    override fun onCleared() {
        super.onCleared()
        lessonStarted = false
    }
}

/**
 * V1 FREEZE: Vereenvoudigde UI State
 */
data class LessonUiState(
    val isLoading: Boolean = false,
    val isActive: Boolean = false,
    val exercises: List<Exercise> = emptyList(),
    val currentIndex: Int = 0,
    val currentExercise: Exercise? = null,
    val results: List<DetailedExerciseResult> = emptyList(),
    val stepState: LessonStepState = LessonStepState.IDLE,
    val completionStage: CompletionStage = CompletionStage.NOT_STARTED,
    val completionStageExerciseId: String? = null,
    val exerciseStartTime: Long? = null,
    val lastAnswerCorrect: Boolean? = null,
    val xpEarnedThisLesson: Int = 0,
    val error: String? = null
) {
    // Computed properties voor backwards compatibility
    val totalExercises: Int get() = exercises.size
    val showFeedback: Boolean get() = stepState == LessonStepState.FEEDBACK
}

enum class LessonStepState {
    IDLE,
    SHOWING,
    PROCESSING,
    FEEDBACK,
    ADVANCING,
    ERROR,
    COMPLETED
}

enum class CompletionStage {
    NOT_STARTED,
    RESULT_LOGGED,
    PROGRESS_UPDATED,
    REWARDS_APPLIED,
    READY_TO_ADVANCE,
    DONE
}

enum class CompletionMode {
    FEEDBACK_THEN_ADVANCE,
    DIRECT_CONTINUE,
    SKIP_ADVANCE
}

enum class FailureStage {
    VALIDATION,
    RESULT_LOGGING,
    PROGRESS_UPDATE,
    REWARD_UPDATE,
    ADVANCE,
    UNKNOWN
}

sealed class LessonNavigationEvent {
    data class LessonComplete(
        val result: SessionResult,
        val xpTotal: Int,
        val badges: List<Badge>
    ) : LessonNavigationEvent()
    
    object BackToHome : LessonNavigationEvent()
}