package com.rekenrinkel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.repository.ProfileRepository
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.*
import com.rekenrinkel.domain.engine.ErrorAnalysis
import com.rekenrinkel.domain.model.*
import com.rekenrinkel.domain.model.UserProfile as ProfileModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Simplified LessonViewModel — clean state machine with auto-advance.
 */
class LessonViewModel(
    private val progressRepository: ProgressRepository,
    private val profileRepository: ProfileRepository,
    private val settingsDataStore: SettingsDataStore,
    private val exerciseEngine: ExerciseEngine,
    private val exerciseValidator: ExerciseValidator,
    private val lessonEngine: LessonEngine? = null
) : ViewModel() {

    private val effectiveLessonEngine: LessonEngine = lessonEngine ?: LessonEngine(exerciseEngine, progressRepository)

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val _navigation = MutableSharedFlow<LessonNavigationEvent>()
    val navigation: SharedFlow<LessonNavigationEvent> = _navigation.asSharedFlow()

    private var exerciseStartTime: Long = 0
    private var currentProcessingId: String? = null

    fun startLesson() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                currentProcessingId = null
                val profile = profileRepository.getProfile().firstOrNull()
                    ?.let { ProfileModel(name = it.name, age = it.age, theme = it.theme) }
                    ?: ProfileModel()
                val isPremium = settingsDataStore.premiumUnlocked.first()
                val lessonPlan = effectiveLessonEngine.buildLesson(profile, isPremium)

                _uiState.update {
                    it.copy(
                        exercises = lessonPlan.exercises,
                        currentIndex = 0,
                        results = emptyList(),
                        isActive = true,
                        isLoading = false,
                        stepState = LessonStepState.SHOWING,
                        lastAnswerCorrect = null,
                        xpEarnedThisLesson = 0,
                        badgesEarnedThisLesson = emptyList(),
                        lessonPlan = lessonPlan,
                        profileAge = profile.age,
                        error = null
                    )
                }
                exerciseStartTime = System.currentTimeMillis()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun startExerciseTimer() {
        exerciseStartTime = System.currentTimeMillis()
    }

    fun submitAnswer(answer: String) {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        if (state.stepState != LessonStepState.SHOWING) return
        if (currentProcessingId == exercise.id) return
        currentProcessingId = exercise.id

        val responseTimeMs = System.currentTimeMillis() - exerciseStartTime

        viewModelScope.launch {
            try {
                val isCorrect = exerciseValidator.validate(exercise, answer)
                val result = DetailedExerciseResult(
                    exerciseId = exercise.id,
                    skillId = exercise.skillId,
                    isCorrect = isCorrect,
                    responseTimeMs = responseTimeMs,
                    givenAnswer = answer,
                    correctAnswer = exercise.correctAnswer,
                    difficultyTier = exercise.difficulty,
                    representationUsed = exercise.visualData?.type?.name ?: "ABSTRACT",
                    errorType = if (!isCorrect) determineErrorType(exercise, answer) else null
                )

                // Log result
                _uiState.update { it.copy(results = it.results + result) }

                // Update progress & rewards
                val xpEarned = updateProgressAndRewards(exercise, result)

                if (isCorrect) {
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.FEEDBACK_CORRECT,
                            lastAnswerCorrect = true,
                            errorHint = null,
                            xpEarnedThisLesson = it.xpEarnedThisLesson + xpEarned,
                            wrongAttempts = 0
                        )
                    }
                    delay(800L)
                    advanceToNext()
                } else {
                    // Wrong: show feedback briefly, then let them retry
                    val attempts = _uiState.value.wrongAttempts + 1
                    val hint = ErrorAnalysis.analyzeError(exercise, answer)
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.FEEDBACK_WRONG,
                            lastAnswerCorrect = false,
                            errorHint = hint,
                            wrongAttempts = attempts
                        )
                    }
                    delay(1200L)
                    // Back to SHOWING so they can retry (with hint visible)
                    _uiState.update {
                        it.copy(
                            stepState = LessonStepState.SHOWING,
                            showHint = true,
                            showRevealAnswer = attempts >= 3
                        )
                    }
                    currentProcessingId = null // Allow re-submission
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(stepState = LessonStepState.ERROR, error = e.message)
                }
            }
        }
    }

    fun continueWorkedExample() {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        if (exercise.type != ExerciseType.WORKED_EXAMPLE) return
        if (state.stepState != LessonStepState.SHOWING) return
        if (currentProcessingId == exercise.id) return
        currentProcessingId = exercise.id

        viewModelScope.launch {
            val result = DetailedExerciseResult(
                exerciseId = exercise.id,
                skillId = exercise.skillId,
                isCorrect = true,
                responseTimeMs = System.currentTimeMillis() - exerciseStartTime,
                givenAnswer = "[worked_example_viewed]",
                correctAnswer = exercise.correctAnswer,
                difficultyTier = exercise.difficulty,
                representationUsed = "WORKED_EXAMPLE"
            )
            _uiState.update { it.copy(results = it.results + result) }
            advanceToNext()
        }
    }

    /**
     * Reveal the correct answer (cheat button after 3 wrong attempts)
     */
    fun revealAnswer() {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        
        viewModelScope.launch {
            // Show the correct answer
            _uiState.update {
                it.copy(
                    stepState = LessonStepState.FEEDBACK_WRONG,
                    errorHint = "Het antwoord is: ${exercise.correctAnswer}",
                    lastAnswerCorrect = false
                )
            }
            delay(2000L)
            
            // Log as incorrect and advance
            val result = DetailedExerciseResult(
                exerciseId = exercise.id,
                skillId = exercise.skillId,
                isCorrect = false,
                responseTimeMs = System.currentTimeMillis() - exerciseStartTime,
                givenAnswer = "[revealed]",
                correctAnswer = exercise.correctAnswer,
                difficultyTier = exercise.difficulty,
                representationUsed = "REVEALED"
            )
            _uiState.update { it.copy(results = it.results + result, wrongAttempts = 0) }
            advanceToNext()
        }
    }
    
    fun skipExercise() {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        if (state.stepState != LessonStepState.SHOWING) return
        if (currentProcessingId == exercise.id) return
        currentProcessingId = exercise.id

        viewModelScope.launch {
            val result = DetailedExerciseResult(
                exerciseId = exercise.id,
                skillId = exercise.skillId,
                isCorrect = false,
                responseTimeMs = 30_000,
                givenAnswer = "[skipped]",
                correctAnswer = exercise.correctAnswer,
                difficultyTier = exercise.difficulty,
                representationUsed = "SKIPPED"
            )
            _uiState.update { it.copy(results = it.results + result) }
            advanceToNext()
        }
    }

    fun continueAfterError() {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            advanceToNext()
        }
    }

    fun skipLesson() {
        viewModelScope.launch {
            _navigation.emit(LessonNavigationEvent.BackToHome)
        }
    }

    // ============ PRIVATE ============

    private suspend fun updateProgressAndRewards(
        exercise: Exercise,
        result: DetailedExerciseResult
    ): Int {
        return try {
            val progress = progressRepository.getOrCreateProgress(exercise.skillId)
            val outcome = effectiveLessonEngine.processExerciseResult(result, progress)
            progressRepository.updateProgress(outcome.updatedProgress)

            val currentRewards = profileRepository.getRewards()
            val updatedRewards = currentRewards.addXp(outcome.xpEarned).updateStreak()
            val newBadges = effectiveLessonEngine.checkBadges(outcome, currentRewards, exercise.skillId)
            val finalRewards = newBadges.fold(updatedRewards) { r, b -> r.addBadge(b) }
            profileRepository.updateRewards(finalRewards)

            _uiState.update {
                it.copy(badgesEarnedThisLesson = it.badgesEarnedThisLesson + newBadges)
            }
            outcome.xpEarned
        } catch (e: Exception) {
            android.util.Log.e("LessonViewModel", "Progress/rewards update failed: ${e.message}")
            0 // Non-fatal: continue even if progress update fails
        }
    }

    private fun advanceToNext() {
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1
        currentProcessingId = null

        if (nextIndex >= state.exercises.size) {
            completeLesson()
        } else {
            _uiState.update {
                it.copy(
                    currentIndex = nextIndex,
                    stepState = LessonStepState.SHOWING,
                    lastAnswerCorrect = null,
                    errorHint = null,
                    wrongAttempts = 0,
                    showHint = false,
                    showRevealAnswer = false,
                    error = null
                )
            }
            exerciseStartTime = System.currentTimeMillis()
        }
    }

    private fun completeLesson() {
        viewModelScope.launch {
            _uiState.update { it.copy(stepState = LessonStepState.COMPLETE) }
            val state = _uiState.value
            val accuracy = state.results.let { results ->
                if (results.isEmpty()) 0f else results.count { it.isCorrect }.toFloat() / results.size
            }
            val stars = when {
                accuracy >= 0.9f -> 3
                accuracy >= 0.7f -> 2
                accuracy >= 0.5f -> 1
                else -> 0
            }
            val avgTime = state.results.map { it.responseTimeMs }.average().toLong()

            _navigation.emit(
                LessonNavigationEvent.LessonComplete(
                    result = SessionResult(
                        exercises = state.results.map {
                            ExerciseResult(it.exerciseId, it.skillId, it.isCorrect, it.responseTimeMs, it.givenAnswer)
                        },
                        xpEarned = state.xpEarnedThisLesson,
                        stars = stars,
                        averageResponseTimeMs = avgTime
                    ),
                    badges = state.badgesEarnedThisLesson,
                    xpTotal = state.xpEarnedThisLesson
                )
            )
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
 * Lesson step states — simplified from the original 8-patch version.
 */
enum class LessonStepState {
    SHOWING,
    FEEDBACK_CORRECT,
    FEEDBACK_WRONG,
    ADVANCING,
    COMPLETE,
    ERROR;

    // Backwards compat
    companion object {
        val FEEDBACK get() = FEEDBACK_CORRECT
        val PROCESSING get() = ADVANCING
        val COMPLETED get() = COMPLETE
    }
}

/**
 * Kept for test compatibility — maps to simple internal state now.
 */
enum class CompletionStage {
    NOT_STARTED,
    RESULT_LOGGED,
    PROGRESS_UPDATED,
    REWARDS_APPLIED,
    READY_TO_ADVANCE,
    DONE
}

data class FailureContext(
    val errorMessage: String,
    val exerciseId: String,
    val exerciseType: ExerciseType,
    val currentIndex: Int,
    val stage: FailureStage = FailureStage.UNKNOWN,
    val timestamp: Long = System.currentTimeMillis(),
    val completionStage: CompletionStage? = null,
    val completionStageExerciseId: String? = null,
    val resultLogged: Boolean = false,
    val progressUpdated: Boolean = false,
    val rewardsApplied: Boolean = false,
    val progressFailed: Boolean = false,
    val rewardsFailed: Boolean = false
)

enum class FailureStage {
    RESULT_LOGGING,
    PROGRESS_UPDATE,
    REWARD_UPDATE,
    STATE_UPDATE,
    ADVANCE,
    UNKNOWN
}

data class LessonUiState(
    val exercises: List<Exercise> = emptyList(),
    val currentIndex: Int = 0,
    val results: List<DetailedExerciseResult> = emptyList(),
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val stepState: LessonStepState = LessonStepState.SHOWING,
    val lastAnswerCorrect: Boolean? = null,
    val xpEarnedThisLesson: Int = 0,
    val badgesEarnedThisLesson: List<Badge> = emptyList(),
    val difficultyChanged: Int? = null,
    val errorHint: String? = null,
    val wrongAttempts: Int = 0,
    val showHint: Boolean = false,
    val showRevealAnswer: Boolean = false,
    val error: String? = null,
    val failureContext: FailureContext? = null,
    val lessonPlan: LessonPlan? = null,
    val profileAge: Int = 6,
    // Keep for compat but not used internally
    val completionStage: CompletionStage = CompletionStage.NOT_STARTED,
    val completionStageExerciseId: String? = null
) {
    val currentExercise: Exercise? = exercises.getOrNull(currentIndex)
    val totalExercises: Int = exercises.size
    val progress: Float = if (totalExercises > 0) currentIndex.toFloat() / totalExercises else 0f
    val showFeedback: Boolean get() = stepState == LessonStepState.FEEDBACK_CORRECT || stepState == LessonStepState.FEEDBACK_WRONG
    val isProcessing: Boolean get() = stepState == LessonStepState.ADVANCING
    val hasError: Boolean get() = stepState == LessonStepState.ERROR || error != null
    val currentPhase: LessonPhase get() {
        val plan = lessonPlan ?: return LessonPhase.FOCUS
        return when {
            currentIndex < plan.warmUpCount -> LessonPhase.WARM_UP
            currentIndex < plan.warmUpCount + plan.focusCount -> LessonPhase.FOCUS
            currentIndex < plan.warmUpCount + plan.focusCount + plan.reviewCount -> LessonPhase.REVIEW
            else -> LessonPhase.CHALLENGE
        }
    }
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
