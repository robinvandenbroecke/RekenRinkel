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
 * PATCH 1 (V1 FREEZE): Strakke, betrouwbare LessonViewModel
 * 
 * KERNPRINCIPES:
 * 1. Geen enkele oefening wordt dubbel verwerkt
 * 2. State wijzigt alleen via goed gedefinieerde stappen
 * 3. Geen stale state - altijd actuele _uiState.value reads
 * 4. Duidelijke error handling zonder "hang"
 * 5. Skip werkt altijd correct
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
     * Verwerk een antwoord - met strikte guards tegen dubbele submit
     */
    fun submitAnswer(answer: String) {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        
        // Guard: les niet actief of al in verwerking
        if (!state.isActive || state.stepState != LessonStepState.SHOWING) {
            android.util.Log.w("LessonViewModel", "submitAnswer ignored - wrong state: ${state.stepState}")
            return
        }
        
        // Guard: oefening al voltooid
        if (isExerciseCompleted(exercise.id)) {
            android.util.Log.w("LessonViewModel", "submitAnswer ignored - exercise already completed")
            return
        }

        viewModelScope.launch {
            // Direct naar PROCESSING state om dubbele submits te voorkomen
            _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }
            
            try {
                val isCorrect = exerciseValidator.validate(exercise, answer)
                val responseTime = System.currentTimeMillis() - (state.exerciseStartTime ?: System.currentTimeMillis())
                
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
                
                // Verwerk completion met juiste mode
                finishCurrentExercise(
                    result = result,
                    mode = if (isCorrect) CompletionMode.FEEDBACK_THEN_ADVANCE else CompletionMode.FEEDBACK_THEN_ADVANCE
                )
            } catch (e: Exception) {
                android.util.Log.e("LessonViewModel", "Error in submitAnswer", e)
                _uiState.update { 
                    it.copy(
                        stepState = LessonStepState.ERROR,
                        error = "Antwoord kon niet worden verwerkt"
                    )
                }
            }
        }
    }

    /**
     * Skip huidige oefening - altijd direct advance, nooit valideren
     */
    fun skipExercise() {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        
        // Guard: les niet actief of al in verwerking
        if (!state.isActive || state.stepState != LessonStepState.SHOWING) {
            android.util.Log.w("LessonViewModel", "skipExercise ignored - wrong state")
            return
        }
        
        // Guard: oefening al voltooid
        if (isExerciseCompleted(exercise.id)) {
            android.util.Log.w("LessonViewModel", "skipExercise ignored - already completed")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }
            
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
            
            // Skip gebruikt eigen completion-mode: geen validator, geen feedback, directe veilige advance
            finishCurrentExercise(
                result = result,
                mode = CompletionMode.SKIP_ADVANCE
            )
        }
    }

    /**
     * Worked example: gebruiker klikt "Begrepen, verder"
     */
    fun continueWorkedExample() {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        
        if (!state.isActive || state.stepState != LessonStepState.SHOWING) return
        if (isExerciseCompleted(exercise.id)) return

        viewModelScope.launch {
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
            
            finishCurrentExercise(
                result = result,
                mode = CompletionMode.DIRECT_CONTINUE
            )
        }
    }

    /**
     * ROBUUSTE LESSONFLOW: Stage-based completion met expliciete state machine
     * 
     * KERNPRINCIPES:
     * 1. Elke stage is idempotent - meerdere calls met zelfde exerciseId zijn veilig
     * 2. DONE betekent: alle side effects uitgevoerd, exercise definitief afgesloten
     * 3. Advance gebeurt alleen als huidige exercise DONE is
     * 4. Recovery kan alleen vanaf ERROR state, en markeert huidige exercise als DONE
     * 
     * Completion stages: NOT_STARTED → RESULT_LOGGED → PROGRESS_UPDATED → REWARDS_APPLIED → READY_TO_ADVANCE → DONE
     * Failure stages: RESULT_LOGGING, PROGRESS_UPDATE, REWARD_UPDATE, ADVANCE, UNKNOWN
     */
    private suspend fun finishCurrentExercise(
        result: DetailedExerciseResult,
        mode: CompletionMode
    ) {
        val exerciseId = result.exerciseId

        // HARDE GUARD: al voltooid = direct return, geen side effects
        if (isExerciseCompleted(exerciseId)) {
            android.util.Log.w("LessonViewModel", "[COMPLETION] Exercise $exerciseId already completed - ignoring")
            return
        }

        // HARDE GUARD: al aan het verwerken = direct return, voorkomt race conditions
        if (currentlyCompletingExerciseId == exerciseId) {
            android.util.Log.w("LessonViewModel", "[COMPLETION] Exercise $exerciseId already processing - ignoring")
            return
        }
        currentlyCompletingExerciseId = exerciseId

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
            }

            // STAGE 2 & 3: Update progress en rewards (alleen voor FEEDBACK_THEN_ADVANCE mode)
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
                }
            }

            // STAGE 4: Ready to advance - alle side effects zijn nu veilig uitgevoerd
            if (currentCompletionState() < CompletionStage.READY_TO_ADVANCE) {
                _uiState.update { it.copy(completionStage = CompletionStage.READY_TO_ADVANCE) }
                android.util.Log.d("LessonViewModel", "[COMPLETION] Stage READY_TO_ADVANCE for $exerciseId")
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
        } finally {
            currentlyCompletingExerciseId = null
        }
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
     * Ga verder na error - markeert huidige exercise als "completed" zonder resultaat
     * zodat we verder kunnen naar de volgende oefening.
     * 
     * Dit is een "graceful degradation" - gebruiker mist de voortgang van deze oefening,
     * maar kan wel verder met de les.
     */
    fun continueAfterError() {
        viewModelScope.launch {
            val currentExerciseId = currentUiState().currentExercise?.id
            if (currentExerciseId != null) {
                // Markeer als completed zonder resultaat te loggen
                // (resultaat was al gelogd als we in RESULT_LOGGED of verder waren)
                completedExerciseIds.add(currentExerciseId)
                android.util.Log.w("LessonViewModel", "[RECOVERY] Skipping exercise $currentExerciseId after error")
            }
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

    private fun currentUiState(): LessonUiState = _uiState.value

    private fun currentExerciseOrNull(): Exercise? = currentUiState().currentExercise

    private fun currentCompletionState(): CompletionStage = currentUiState().completionStage

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