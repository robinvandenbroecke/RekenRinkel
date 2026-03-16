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

    // ============ PATCH 1: UNIFORME OEFEN-AFRONDINGSFLOW ============

    /**
     * PATCH 1: Centrale helper voor het afronden van een oefening.
     * Alle paden (submit, worked, skip) eindigen hier.
     */
    private suspend fun finishCurrentExercise(
        result: DetailedExerciseResult,
        skipMasteryUpdate: Boolean = false,
        needsFeedback: Boolean = true
    ) {
        val state = _uiState.value
        val currentExercise = state.currentExercise ?: return

        try {
            // 1. Update progress/mastery (indien niet geskipped)
            if (!skipMasteryUpdate) {
                val currentProgress = progressRepository.getOrCreateProgress(currentExercise.skillId)
                val outcome = lessonEngine.processExerciseResult(result, currentProgress)
                progressRepository.updateProgress(outcome.updatedProgress)

                // 2. Update rewards (XP, streak, badges)
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

                // 3. Update UI state met resultaten
                val newResults = state.results + result
                _uiState.update {
                    it.copy(
                        results = newResults,
                        stepState = if (needsFeedback) LessonStepState.FEEDBACK else LessonStepState.ADVANCING,
                        lastAnswerCorrect = result.isCorrect,
                        xpEarnedThisLesson = it.xpEarnedThisLesson + outcome.xpEarned,
                        badgesEarnedThisLesson = it.badgesEarnedThisLesson + newBadges,
                        difficultyChanged = if (outcome.difficultyChanged) outcome.newDifficultyTier else null
                    )
                }
            } else {
                // Skip: alleen resultaat opslaan, geen mastery/rewards update
                val newResults = state.results + result
                _uiState.update {
                    it.copy(
                        results = newResults,
                        stepState = if (needsFeedback) LessonStepState.FEEDBACK else LessonStepState.ADVANCING,
                        lastAnswerCorrect = result.isCorrect
                    )
                }
            }

            // 4. Advance beslissing
            if (!needsFeedback) {
                // Direct advance (voor worked example)
                advanceToNextExercise()
            }
            // Anders: wacht op expliciete nextExercise() call via onFeedbackComplete

        } catch (e: Exception) {
            _uiState.update { it.copy(stepState = LessonStepState.SHOWING, error = e.message) }
        }
    }

    /**
     * PATCH 1: Helper voor direct naar volgende oefening gaan
     */
    private fun advanceToNextExercise() {
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1

        _uiState.update { it.copy(stepState = LessonStepState.ADVANCING) }

        if (nextIndex >= state.exercises.size) {
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

            // Gebruik uniforme flow
            finishCurrentExercise(result, skipMasteryUpdate = false, needsFeedback = true)
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

            // Gebruik uniforme flow met direct advance
            finishCurrentExercise(result, skipMasteryUpdate = true, needsFeedback = false)
            // needsFeedback = false triggert automatisch advanceToNextExercise()
        }
    }

    /**
     * PATCH 1: Sla oefening over
     * Gebruikt de uniforme finishCurrentExercise helper
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

            // Gebruik uniforme flow
            finishCurrentExercise(result, skipMasteryUpdate = true, needsFeedback = true)
        }
    }

    /**
     * PATCH 4: Eenduidige advance - aangeroepen na feedback
     * Gebruikt dezelfde advanceToNextExercise als worked example
     */
    fun nextExercise() {
        advanceToNextExercise()
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
 * PATCH 6: Expliciete lesstep state
 */
enum class LessonStepState {
    SHOWING,      // Item wordt getoond, wacht op input
    PROCESSING,   // Bezig met verwerken
    FEEDBACK,     // Feedback wordt getoond
    ADVANCING,    // Bezig met naar volgende gaan
    COMPLETED     // Les voltooid
}

data class LessonUiState(
    val exercises: List<Exercise> = emptyList(),
    val currentIndex: Int = 0,
    val results: List<DetailedExerciseResult> = emptyList(),
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val stepState: LessonStepState = LessonStepState.SHOWING,  // PATCH 6
    val lastAnswerCorrect: Boolean? = null,
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
    
    // PATCH 6: Backwards compatibility
    val showFeedback: Boolean get() = stepState == LessonStepState.FEEDBACK
    val isProcessing: Boolean get() = stepState == LessonStepState.PROCESSING || stepState == LessonStepState.ADVANCING
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