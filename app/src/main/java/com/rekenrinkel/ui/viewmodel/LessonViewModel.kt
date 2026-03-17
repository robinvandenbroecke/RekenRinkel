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

    private var exerciseStartTime: Long = 0
    private var currentlyCompletingExerciseId: String? = null
    private val completedExerciseIds = mutableSetOf<String>()

    fun startLesson() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val profile = profileRepository.getProfile().firstOrNull()
                    ?.let { ProfileModel(name = it.name, age = it.age, theme = it.theme) }
                    ?: ProfileModel()
                val isPremium = settingsDataStore.premiumUnlocked.first()
                val lessonPlan = lessonEngine.buildLesson(profile, isPremium)
                _uiState.update {
                    it.copy(
                        exercises = lessonPlan.exercises,
                        currentIndex = 0,
                        results = emptyList(),
                        isActive = true,
                        isLoading = false,
                        stepState = LessonStepState.SHOWING,
                        currentPhase = determinePhase(0, lessonPlan),
                        completionStage = CompletionStage.NOT_STARTED,
                        completionStageExerciseId = null
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

    enum class CompletionMode {
        FEEDBACK_THEN_ADVANCE,
        DIRECT_CONTINUE,
        SKIP_ADVANCE
    }

    private fun currentExerciseId(): String? = _uiState.value.currentExercise?.id
    private fun currentStepState(): LessonStepState = _uiState.value.stepState

    private fun isExerciseCompleted(exerciseId: String): Boolean {
        val state = _uiState.value
        return state.completionStageExerciseId == exerciseId && state.completionStage == CompletionStage.DONE
                || completedExerciseIds.contains(exerciseId)
    }

    private fun markStage(stage: CompletionStage, exerciseId: String) {
        _uiState.update { it.copy(completionStage = stage, completionStageExerciseId = exerciseId) }
    }

    private suspend fun finishCurrentExercise(
        result: DetailedExerciseResult,
        mode: CompletionMode,
        feedbackDelayMs: Long = 800
    ) {
        val exerciseId = result.exerciseId

        // Guard: already done
        if (isExerciseCompleted(exerciseId)) {
            android.util.Log.w("LessonViewModel", "finishCurrentExercise blocked - $exerciseId already done")
            return
        }

        // Guard: already processing
        if (currentlyCompletingExerciseId == exerciseId) {
            android.util.Log.w("LessonViewModel", "finishCurrentExercise blocked - $exerciseId already processing")
            return
        }
        currentlyCompletingExerciseId = exerciseId

        val needsFeedback = mode == CompletionMode.FEEDBACK_THEN_ADVANCE
        val shouldUpdateMastery = mode == CompletionMode.FEEDBACK_THEN_ADVANCE

        try {
            // Step 1: Log result
            val state1 = _uiState.value
            if (state1.completionStageExerciseId != exerciseId || state1.completionStage < CompletionStage.RESULT_LOGGED) {
                _uiState.update {
                    it.copy(
                        results = it.results + result,
                        completionStage = CompletionStage.RESULT_LOGGED,
                        completionStageExerciseId = exerciseId
                    )
                }
            }

            // Step 2: Update progress (only for normal answers)
            var outcome: ExerciseOutcome? = null
            if (shouldUpdateMastery) {
                val state2 = _uiState.value
                if (state2.completionStage == CompletionStage.RESULT_LOGGED) {
                    try {
                        val currentProgress = progressRepository.getOrCreateProgress(result.skillId)
                        outcome = lessonEngine.processExerciseResult(result, currentProgress)
                        progressRepository.updateProgress(outcome.updatedProgress)
                        markStage(CompletionStage.PROGRESS_UPDATED, exerciseId)
                    } catch (e: Exception) {
                        throw StepFailure(FailureStage.PROGRESS_UPDATE, e)
                    }
                }
            }

            // Step 3: Apply rewards (only for normal answers)
            if (shouldUpdateMastery) {
                val state3 = _uiState.value
                if (state3.completionStage == CompletionStage.PROGRESS_UPDATED) {
                    try {
                        val finalOutcome = outcome ?: run {
                            val currentProgress = progressRepository.getOrCreateProgress(result.skillId)
                            lessonEngine.processExerciseResult(result, currentProgress)
                        }
                        outcome = finalOutcome
                        val currentRewards = profileRepository.getRewards()
                        val updatedRewards = currentRewards.addXp(finalOutcome.xpEarned).updateStreak()
                        val newBadges = lessonEngine.checkBadges(finalOutcome, currentRewards, result.skillId)
                        val finalRewards = newBadges.fold(updatedRewards) { r, b -> r.addBadge(b) }
                        profileRepository.updateRewards(finalRewards)
                        markStage(CompletionStage.REWARDS_APPLIED, exerciseId)
                    } catch (e: Exception) {
                        throw StepFailure(FailureStage.REWARD_UPDATE, e)
                    }
                }
            }

            // Step 4: Prepare advance
            val state4 = _uiState.value
            val canAdvance = when {
                mode == CompletionMode.SKIP_ADVANCE || mode == CompletionMode.DIRECT_CONTINUE ->
                    state4.completionStage == CompletionStage.RESULT_LOGGED
                else -> state4.completionStage == CompletionStage.REWARDS_APPLIED
            }
            if (canAdvance) {
                val xpEarned = if (shouldUpdateMastery) outcome?.xpEarned ?: 0 else 0
                val badges = if (shouldUpdateMastery && outcome != null) {
                    lessonEngine.checkBadges(outcome, profileRepository.getRewards(), result.skillId)
                } else emptyList()

                _uiState.update {
                    it.copy(
                        stepState = if (needsFeedback) LessonStepState.FEEDBACK else LessonStepState.ADVANCING,
                        lastAnswerCorrect = result.isCorrect,
                        xpEarnedThisLesson = it.xpEarnedThisLesson + xpEarned,
                        badgesEarnedThisLesson = it.badgesEarnedThisLesson + badges,
                        completionStage = CompletionStage.READY_TO_ADVANCE,
                        completionStageExerciseId = exerciseId
                    )
                }
            }

            // Step 5: Advance
            val state5 = _uiState.value
            if (state5.completionStage == CompletionStage.READY_TO_ADVANCE && state5.completionStageExerciseId == exerciseId) {
                if (needsFeedback) {
                    delay(feedbackDelayMs)
                }
                // Verify still on same exercise before marking done
                if (currentExerciseId() == exerciseId) {
                    completedExerciseIds.add(exerciseId)
                    markStage(CompletionStage.DONE, exerciseId)
                    advanceToNextExercise()
                }
            }
        } catch (e: StepFailure) {
            handleFailure(e.cause?.message ?: e.message ?: "Unknown error", e.stage, exerciseId)
        } catch (e: Exception) {
            handleFailure(e.message ?: "Unknown error", FailureStage.UNKNOWN, exerciseId)
        } finally {
            currentlyCompletingExerciseId = null
        }
    }

    private class StepFailure(
        val stage: FailureStage,
        cause: Throwable
    ) : RuntimeException(cause)

    private fun handleFailure(errorMessage: String, stage: FailureStage, exerciseId: String) {
        android.util.Log.e("LessonViewModel", "Failure in $exerciseId at ${stage.name}: $errorMessage")
        _uiState.update { state ->
            state.copy(
                stepState = LessonStepState.ERROR,
                error = "Oefening afhandeling mislukt: $errorMessage",
                failureContext = FailureContext(
                    errorMessage = errorMessage,
                    exerciseId = exerciseId,
                    exerciseType = state.currentExercise?.type ?: ExerciseType.TYPED_NUMERIC,
                    currentIndex = state.currentIndex,
                    stage = stage,
                    completionStage = state.completionStage,
                    completionStageExerciseId = state.completionStageExerciseId
                )
            )
        }
        currentlyCompletingExerciseId = null
    }

    private fun advanceToNextExercise() {
        val currentState = _uiState.value
        val nextIndex = currentState.currentIndex + 1

        _uiState.update { it.copy(stepState = LessonStepState.ADVANCING) }
        currentlyCompletingExerciseId = null

        if (nextIndex >= currentState.exercises.size) {
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
        }
    }

    fun submitAnswer(answer: String) {
        val exercise = _uiState.value.currentExercise ?: return
        if (isExerciseCompleted(exercise.id)) return
        if (currentStepState() != LessonStepState.SHOWING) return

        _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }
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
                finishCurrentExercise(result, mode = CompletionMode.FEEDBACK_THEN_ADVANCE)
            } catch (e: Exception) {
                handleFailure(e.message ?: "Unknown error", FailureStage.RESULT_LOGGING, exercise.id)
            }
        }
    }

    fun continueWorkedExample() {
        val exercise = _uiState.value.currentExercise ?: return
        if (isExerciseCompleted(exercise.id)) return
        if (currentStepState() != LessonStepState.SHOWING) return
        if (exercise.type != ExerciseType.WORKED_EXAMPLE) return

        _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }

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
            finishCurrentExercise(result, mode = CompletionMode.DIRECT_CONTINUE)
        }
    }

    fun skipExercise() {
        val exercise = _uiState.value.currentExercise ?: return
        if (isExerciseCompleted(exercise.id)) return
        if (currentStepState() != LessonStepState.SHOWING) return

        _uiState.update { it.copy(stepState = LessonStepState.PROCESSING) }

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
            finishCurrentExercise(result, mode = CompletionMode.SKIP_ADVANCE)
        }
    }

    fun continueAfterError() {
        val state = _uiState.value
        val failureContext = state.failureContext
        val exercise = state.currentExercise

        if (exercise == null || failureContext == null) {
            viewModelScope.launch { _navigation.emit(LessonNavigationEvent.BackToHome) }
            return
        }

        val completionStage = state.completionStage
        val stageExerciseId = state.completionStageExerciseId

        // If DONE or READY_TO_ADVANCE, just advance
        if (stageExerciseId == exercise.id &&
            (completionStage == CompletionStage.DONE || completionStage == CompletionStage.READY_TO_ADVANCE)
        ) {
            viewModelScope.launch {
                completedExerciseIds.add(exercise.id)
                advanceToNextExercise()
            }
            return
        }

        // If REWARDS_APPLIED, safe to advance
        if (stageExerciseId == exercise.id && completionStage == CompletionStage.REWARDS_APPLIED) {
            viewModelScope.launch {
                markStage(CompletionStage.DONE, exercise.id)
                completedExerciseIds.add(exercise.id)
                advanceToNextExercise()
            }
            return
        }

        // If RESULT_LOGGED and worked example, safe to advance
        if (stageExerciseId == exercise.id &&
            completionStage == CompletionStage.RESULT_LOGGED &&
            exercise.type == ExerciseType.WORKED_EXAMPLE
        ) {
            viewModelScope.launch {
                markStage(CompletionStage.DONE, exercise.id)
                completedExerciseIds.add(exercise.id)
                advanceToNextExercise()
            }
            return
        }

        // Otherwise, try to complete remaining steps
        viewModelScope.launch {
            completeRemainingSteps(exercise)
        }
    }

    private suspend fun completeRemainingSteps(exercise: Exercise) {
        val exerciseId = exercise.id
        val state = _uiState.value

        // Find the result for this exercise
        val result = state.results.lastOrNull { it.exerciseId == exerciseId }

        if (result == null) {
            // No result logged, just skip
            completedExerciseIds.add(exerciseId)
            advanceToNextExercise()
            return
        }

        try {
            // Only update progress if not already done and not worked example
            if (exercise.type != ExerciseType.WORKED_EXAMPLE) {
                val currentStage = _uiState.value.completionStage
                if (currentStage == CompletionStage.RESULT_LOGGED) {
                    val currentProgress = progressRepository.getOrCreateProgress(exercise.skillId)
                    val outcome = lessonEngine.processExerciseResult(result, currentProgress)
                    progressRepository.updateProgress(outcome.updatedProgress)
                    markStage(CompletionStage.PROGRESS_UPDATED, exerciseId)
                }

                val updatedStage = _uiState.value.completionStage
                if (updatedStage == CompletionStage.PROGRESS_UPDATED) {
                    val currentProgress = progressRepository.getOrCreateProgress(exercise.skillId)
                    val outcome = lessonEngine.processExerciseResult(result, currentProgress)
                    val currentRewards = profileRepository.getRewards()
                    val updatedRewards = currentRewards.addXp(outcome.xpEarned).updateStreak()
                    val newBadges = lessonEngine.checkBadges(outcome, currentRewards, exercise.skillId)
                    val finalRewards = newBadges.fold(updatedRewards) { r, b -> r.addBadge(b) }
                    profileRepository.updateRewards(finalRewards)
                    markStage(CompletionStage.REWARDS_APPLIED, exerciseId)
                }
            }

            markStage(CompletionStage.DONE, exerciseId)
            completedExerciseIds.add(exerciseId)
            advanceToNextExercise()
        } catch (e: Exception) {
            // On error, just advance anyway to avoid getting stuck
            completedExerciseIds.add(exerciseId)
            advanceToNextExercise()
        }
    }

    private fun completeLesson() {
        viewModelScope.launch {
            val state = _uiState.value
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

    fun skipLesson() {
        viewModelScope.launch {
            _navigation.emit(LessonNavigationEvent.BackToHome)
        }
    }

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

enum class LessonStepState {
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

enum class FailureStage {
    RESULT_LOGGING,
    PROGRESS_UPDATE,
    REWARD_UPDATE,
    STATE_UPDATE,
    ADVANCE,
    UNKNOWN
}

data class FailureContext(
    val errorMessage: String,
    val exerciseId: String,
    val exerciseType: ExerciseType,
    val currentIndex: Int,
    val stage: FailureStage,
    val timestamp: Long = System.currentTimeMillis(),
    val completionStage: CompletionStage? = null,
    val completionStageExerciseId: String? = null
)

data class LessonUiState(
    val exercises: List<Exercise> = emptyList(),
    val currentIndex: Int = 0,
    val results: List<DetailedExerciseResult> = emptyList(),
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val stepState: LessonStepState = LessonStepState.SHOWING,
    val lastAnswerCorrect: Boolean? = null,
    val currentPhase: LessonPhase = LessonPhase.FOCUS,
    val xpEarnedThisLesson: Int = 0,
    val badgesEarnedThisLesson: List<Badge> = emptyList(),
    val difficultyChanged: Int? = null,
    val error: String? = null,
    val failureContext: FailureContext? = null,
    val lessonPlan: LessonPlan? = null,
    val completionStage: CompletionStage = CompletionStage.NOT_STARTED,
    val completionStageExerciseId: String? = null
) {
    val currentExercise: Exercise? = exercises.getOrNull(currentIndex)
    val totalExercises: Int = exercises.size
    val progress: Float = if (totalExercises > 0) currentIndex.toFloat() / totalExercises else 0f
    val showFeedback: Boolean get() = stepState == LessonStepState.FEEDBACK
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