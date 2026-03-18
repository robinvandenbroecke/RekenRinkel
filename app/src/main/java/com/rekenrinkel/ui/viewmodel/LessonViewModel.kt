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
            
            // Skip gebruikt DIRECT_CONTINUE - geen feedback, geen XP update
            finishCurrentExercise(
                result = result,
                mode = CompletionMode.DIRECT_CONTINUE
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
     * V1 FREEZE: Strakke finishCurrentExercise - geen stale state, geen dubbele side effects
     */
    private suspend fun finishCurrentExercise(
        result: DetailedExerciseResult,
        mode: CompletionMode
    ) {
        val exerciseId = result.exerciseId

        // Guard: al voltooid
        if (isExerciseCompleted(exerciseId)) {
            android.util.Log.w("LessonViewModel", "finishCurrentExercise blocked - already done")
            return
        }

        // Guard: al aan het verwerken
        if (currentlyCompletingExerciseId == exerciseId) {
            android.util.Log.w("LessonViewModel", "finishCurrentExercise blocked - already processing")
            return
        }
        currentlyCompletingExerciseId = exerciseId

        try {
            // Stap 1: Log resultaat
            if (_uiState.value.completionStageExerciseId != exerciseId ||
                _uiState.value.completionStage < CompletionStage.RESULT_LOGGED) {
                _uiState.update {
                    it.copy(
                        results = it.results + result,
                        completionStage = CompletionStage.RESULT_LOGGED,
                        completionStageExerciseId = exerciseId
                    )
                }
            }

            // Stap 2: Update progress (alleen voor normale antwoorden, niet skip/worked)
            if (mode == CompletionMode.FEEDBACK_THEN_ADVANCE) {
                try {
                    val currentProgress = progressRepository.getOrCreateProgress(result.skillId)
                    val outcome = lessonEngine.processExerciseResult(result, currentProgress)
                    progressRepository.updateProgress(outcome.updatedProgress)
                    
                    // Update rewards
                    val currentRewards = profileRepository.getRewards()
                    val updatedRewards = currentRewards.addXp(outcome.xpEarned).updateStreak()
                    profileRepository.updateRewards(updatedRewards)
                    
                    _uiState.update {
                        it.copy(
                            xpEarnedThisLesson = it.xpEarnedThisLesson + outcome.xpEarned,
                            lastAnswerCorrect = result.isCorrect
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LessonViewModel", "Error updating progress", e)
                    // Ga naar ERROR state - gebruiker kan zelf verder klikken
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.ERROR,
                            error = "Voortgang kon niet worden opgeslagen"
                        )
                    }
                    currentlyCompletingExerciseId = null
                    return
                }
            }

            // Stap 3: Toon feedback of ga direct door
            when (mode) {
                CompletionMode.FEEDBACK_THEN_ADVANCE -> {
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.FEEDBACK,
                            lastAnswerCorrect = result.isCorrect
                        )
                    }
                    // Delay dan advance
                    delay(1000)
                    advanceToNextExercise()
                }
                CompletionMode.DIRECT_CONTINUE -> {
                    advanceToNextExercise()
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("LessonViewModel", "Error in finishCurrentExercise", e)
            handleFailure("Er ging iets mis", FailureStage.UNKNOWN, exerciseId)
        } finally {
            currentlyCompletingExerciseId = null
        }
    }

    /**
     * Advance naar volgende oefening of finish les
     */
    private suspend fun advanceToNextExercise() {
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1
        
        if (nextIndex >= state.exercises.size) {
            // Les klaar
            finishLesson()
        } else {
            // Volgende oefening
            val nextExercise = state.exercises[nextIndex]
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
        }
    }

    /**
     * Finish les en navigeer naar result screen
     */
    private suspend fun finishLesson() {
        val state = _uiState.value
        
        // Maak session result
        val sessionResult = SessionResult(
            exercises = state.results.map { r ->
                ExerciseResult(
                    exerciseId = r.exerciseId,
                    skillId = r.skillId,
                    isCorrect = r.isCorrect,
                    responseTimeMs = r.responseTimeMs,
                    givenAnswer = r.givenAnswer
                )
            },
            xpEarned = state.xpEarnedThisLesson
        )
        
        _uiState.update { it.copy(isActive = false) }
        
        // Emit navigation event
        _navigation.emit(LessonNavigationEvent.LessonComplete(
            result = sessionResult,
            xpTotal = state.xpEarnedThisLesson,
            badges = emptyList() // V1: simplified rewards
        ))
    }

    /**
     * Error handling - toon fout maar laat gebruiker door kunnen gaan
     */
    private fun handleFailure(message: String, stage: FailureStage, exerciseId: String) {
        _uiState.update {
            it.copy(
                stepState = LessonStepState.ERROR,
                error = "$message (${stage.name})"
            )
        }
    }

    /**
     * Ga verder na error
     */
    fun continueAfterError() {
        viewModelScope.launch {
            advanceToNextExercise()
        }
    }

    private fun isExerciseCompleted(exerciseId: String): Boolean {
        return completedExerciseIds.contains(exerciseId)
    }

    private fun currentExerciseId(): String? {
        return _uiState.value.currentExercise?.id
    }

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
    ERROR
}

enum class CompletionStage {
    NOT_STARTED,
    RESULT_LOGGED
}

enum class CompletionMode {
    FEEDBACK_THEN_ADVANCE,
    DIRECT_CONTINUE
}

enum class FailureStage {
    VALIDATION,
    PROGRESS_UPDATE,
    REWARD_UPDATE,
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