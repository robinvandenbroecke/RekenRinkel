package com.rekenrinkel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.repository.ProfileRepository
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.*
import com.rekenrinkel.domain.model.*
import com.rekenrinkel.domain.model.UserProfile as ProfileModel
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
                        showFeedback = false,
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

    /**
     * Verwerk een antwoord met XP en mastery tracking
     */
    fun submitAnswer(answer: String) {
        val state = _uiState.value
        val currentExercise = state.currentExercise ?: return

        // Guard: prevent double submit
        if (state.showFeedback || state.isProcessing) return

        _uiState.update { it.copy(isProcessing = true) }

        val responseTimeMs = System.currentTimeMillis() - exerciseStartTime

        viewModelScope.launch {
            try {
                // Valideer antwoord
                val isCorrect = exerciseValidator.validate(currentExercise, answer)

                // Haal huidige progress op (enhanced model)
                val currentProgress = progressRepository.getOrCreateProgress(currentExercise.skillId)

                // Verwerk resultaat met LessonEngine
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

                val outcome = lessonEngine.processExerciseResult(result, currentProgress)

                // Update progress in database
                progressRepository.updateProgress(outcome.updatedProgress)

                // Update rewards (XP, streak, badges)
                val currentRewards = profileRepository.getRewards()
                val updatedRewards = currentRewards
                    .addXp(outcome.xpEarned)
                    .updateStreak()

                // Check voor badges
                val newBadges = lessonEngine.checkBadges(
                    outcome,
                    currentRewards,
                    currentExercise.skillId // Gebruik skillId als placeholder voor name
                )

                val finalRewards = newBadges.fold(updatedRewards) { rewards, badge ->
                    rewards.addBadge(badge)
                }

                profileRepository.updateRewards(finalRewards)

                // Update UI state
                val newResults = state.results + result
                _uiState.update {
                    it.copy(
                        results = newResults,
                        showFeedback = true,
                        lastAnswerCorrect = isCorrect,
                        isProcessing = false,
                        xpEarnedThisLesson = it.xpEarnedThisLesson + outcome.xpEarned,
                        badgesEarnedThisLesson = it.badgesEarnedThisLesson + newBadges,
                        difficultyChanged = if (outcome.difficultyChanged) outcome.newDifficultyTier else null
                    )
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = e.message) }
            }
        }
    }

    /**
     * Ga verder bij WORKED_EXAMPLE - geen validatie, alleen logging
     */
    fun continueWorkedExample() {
        val state = _uiState.value
        val currentExercise = state.currentExercise ?: return

        // Guard: alleen voor WORKED_EXAMPLE
        if (currentExercise.type != com.rekenrinkel.domain.model.ExerciseType.WORKED_EXAMPLE) return

        _uiState.update { it.copy(isProcessing = true) }

        viewModelScope.launch {
            try {
                // Minimale logging zonder answer-validatie
                val workedResult = DetailedExerciseResult(
                    exerciseId = currentExercise.id,
                    skillId = currentExercise.skillId,
                    isCorrect = true, // WORKED_EXAMPLE telt altijd als "gezien"
                    responseTimeMs = System.currentTimeMillis() - exerciseStartTime,
                    givenAnswer = "[worked_example_viewed]",
                    correctAnswer = currentExercise.correctAnswer,
                    difficultyTier = currentExercise.difficulty,
                    representationUsed = "WORKED_EXAMPLE"
                )

                // Sla op in results
                val newResults = state.results + workedResult
                _uiState.update {
                    it.copy(
                        results = newResults,
                        showFeedback = false, // Geen feedback overlay voor worked example
                        lastAnswerCorrect = null,
                        isProcessing = false
                    )
                }

                // Direct doorgaan naar volgende oefening
                nextExercise()

            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = e.message) }
            }
        }
    }

    /**
     * Sla oefening over
     */
    fun skipExercise() {
        val state = _uiState.value
        val currentExercise = state.currentExercise ?: return

        if (state.showFeedback || state.isProcessing) return

        _uiState.update { it.copy(isProcessing = true) }

        viewModelScope.launch {
            try {
                val skippedResult = DetailedExerciseResult(
                    exerciseId = currentExercise.id,
                    skillId = currentExercise.skillId,
                    isCorrect = false,
                    responseTimeMs = 30_000, // Skip penalty
                    givenAnswer = "[skipped]",
                    correctAnswer = currentExercise.correctAnswer,
                    difficultyTier = currentExercise.difficulty,
                    representationUsed = "SKIPPED"
                )

                // Sla skip op maar zonder XP
                val currentProgress = progressRepository.getOrCreateProgress(currentExercise.skillId)

                val outcome = lessonEngine.processExerciseResult(skippedResult, currentProgress)
                progressRepository.updateProgress(outcome.updatedProgress)

                val newResults = state.results + skippedResult
                _uiState.update {
                    it.copy(
                        results = newResults,
                        showFeedback = true,
                        lastAnswerCorrect = false,
                        isProcessing = false
                    )
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = e.message) }
            }
        }
    }

    /**
     * Ga naar volgende oefening
     */
    fun nextExercise() {
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1

        if (nextIndex >= state.exercises.size) {
            completeLesson()
        } else {
            _uiState.update {
                it.copy(
                    currentIndex = nextIndex,
                    showFeedback = false,
                    lastAnswerCorrect = null,
                    difficultyChanged = null,
                    currentPhase = determinePhase(nextIndex, it)
                )
            }
            exerciseStartTime = System.currentTimeMillis()
        }
    }

    /**
     * Voltooi de les
     */
    private fun completeLesson() {
        viewModelScope.launch {
            val state = _uiState.value

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

data class LessonUiState(
    val exercises: List<Exercise> = emptyList(),
    val currentIndex: Int = 0,
    val results: List<DetailedExerciseResult> = emptyList(),
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val showFeedback: Boolean = false,
    val lastAnswerCorrect: Boolean? = null,
    val isProcessing: Boolean = false,
    val currentPhase: LessonPhase = LessonPhase.FOCUS,
    val xpEarnedThisLesson: Int = 0,
    val badgesEarnedThisLesson: List<Badge> = emptyList(),
    val difficultyChanged: Int? = null,
    val error: String? = null,
    val lessonPlan: LessonPlan? = null
) {
    val currentExercise: Exercise? = exercises.getOrNull(currentIndex)
    val totalExercises: Int = exercises.size
    val progress: Float = if (totalExercises > 0) currentIndex.toFloat() / totalExercises else 0f
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