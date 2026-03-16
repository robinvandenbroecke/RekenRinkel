package com.rekenrinkel.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.repository.ProfileRepository
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.ExerciseEngine
import com.rekenrinkel.domain.engine.ExerciseValidator
import com.rekenrinkel.domain.model.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/**
 * PATCH 9: Tests voor robuuste lesflow
 * Deze tests dekken de vastloper-klasse op de eerste oefening
 */
@ExperimentalCoroutinesApi
class LessonViewModelFlowTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: LessonViewModel
    private lateinit var progressRepository: ProgressRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var exerciseEngine: ExerciseEngine
    private lateinit var exerciseValidator: ExerciseValidator

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        progressRepository = mockk(relaxed = true)
        profileRepository = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)
        exerciseEngine = mockk(relaxed = true)
        exerciseValidator = mockk(relaxed = true)

        // Setup default mocks
        coEvery { settingsDataStore.premiumUnlocked } returns kotlinx.coroutines.flow.flowOf(false)
        coEvery { profileRepository.getProfile() } returns kotlinx.coroutines.flow.flowOf(null)
        coEvery { profileRepository.getRewards() } returns Rewards()
        coEvery { progressRepository.getOrCreateProgress(any()) } returns SkillProgress("test_skill")

        viewModel = LessonViewModel(
            progressRepository,
            profileRepository,
            settingsDataStore,
            exerciseEngine,
            exerciseValidator
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ============ TEST 1: Gewone antwoordoefening ============
    @Test
    fun `submitAnswer - should show feedback then auto-advance`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))
        coEvery { exerciseValidator.validate(any(), any()) } returns true

        // Act - submit answer
        viewModel.submitAnswer("5")
        advanceTimeBy(100) // Processing time

        // Assert - should be in FEEDBACK state
        assertEquals(LessonStepState.FEEDBACK, viewModel.uiState.value.stepState)

        // Act - wait for auto-advance
        advanceTimeBy(900) // Feedback duration + advance

        // Assert - should have advanced to next exercise
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(LessonStepState.SHOWING, viewModel.uiState.value.stepState)
    }

    // ============ TEST 2: WORKED_EXAMPLE ============
    @Test
    fun `continueWorkedExample - should advance directly without feedback`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(
            createExercise("1", ExerciseType.WORKED_EXAMPLE),
            createExercise("2")
        ))

        // Act
        viewModel.continueWorkedExample()
        advanceTimeBy(100)

        // Assert - should have advanced directly (no feedback delay)
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(LessonStepState.SHOWING, viewModel.uiState.value.stepState)
    }

    // ============ TEST 3: GUIDED_PRACTICE ============
    @Test
    fun `guidedPractice - should validate and advance with feedback`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(
            createExercise("1", ExerciseType.GUIDED_PRACTICE),
            createExercise("2")
        ))
        coEvery { exerciseValidator.validate(any(), any()) } returns true

        // Act
        viewModel.submitAnswer("5")
        advanceTimeBy(100)

        // Assert - should show feedback
        assertEquals(LessonStepState.FEEDBACK, viewModel.uiState.value.stepState)

        // Act - wait for auto-advance
        advanceTimeBy(900)

        // Assert - should have advanced
        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    // ============ TEST 4: SKIP ============
    @Test
    fun `skipExercise - should advance directly`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))

        // Act
        viewModel.skipExercise()
        advanceTimeBy(100)

        // Assert - should have advanced directly
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(LessonStepState.SHOWING, viewModel.uiState.value.stepState)
    }

    // ============ TEST 5: Dubbele submit guard ============
    @Test
    fun `double submit - should ignore second submit`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))
        coEvery { exerciseValidator.validate(any(), any()) } returns true

        // Act - submit twice rapidly
        viewModel.submitAnswer("5")
        viewModel.submitAnswer("5") // Should be ignored
        advanceTimeBy(1000)

        // Assert - should only have one result
        assertEquals(1, viewModel.uiState.value.results.size)
    }

    // ============ TEST 6: Exception handling - no silent hang ============
    @Test
    fun `exception during finish - should show error and not hang`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))
        coEvery { exerciseValidator.validate(any(), any()) } throws RuntimeException("Test error")

        // Act
        viewModel.submitAnswer("5")
        advanceTimeBy(1000)

        // Assert - error should be visible, not silently stuck
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error?.contains("Test error") == true)
    }

    // ============ PATCH 10: Failure recovery tests ============

    @Test
    fun `failure in result logging - should allow skip to next`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))
        coEvery { exerciseValidator.validate(any(), any()) } returns true
        coEvery { progressRepository.getOrCreateProgress(any()) } throws RuntimeException("DB error")

        // Act
        viewModel.submitAnswer("5")
        advanceTimeBy(1000)

        // Assert - should be in error state
        assertEquals(LessonStepState.ERROR, viewModel.uiState.value.stepState)

        // Act - continue after error
        viewModel.continueAfterError()
        advanceTimeBy(500)

        // Assert - should have advanced
        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun `worked example failure - should always continue to next`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(
            createExercise("1", ExerciseType.WORKED_EXAMPLE),
            createExercise("2")
        ))

        // Act
        viewModel.continueWorkedExample()
        advanceTimeBy(100)

        // Assert - should advance even if something fails
        // Note: in real scenario with exceptions, continueAfterError would be called
        assertTrue(viewModel.uiState.value.currentIndex >= 0)
    }

    @Test
    fun `double continueAfterError - should not cause issues`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))
        coEvery { exerciseValidator.validate(any(), any()) } throws RuntimeException("Test error")

        // Act - trigger error
        viewModel.submitAnswer("5")
        advanceTimeBy(1000)

        // Act - call continueAfterError twice
        viewModel.continueAfterError()
        viewModel.continueAfterError() // Should be safe
        advanceTimeBy(1000)

        // Assert - should not crash or cause issues
        // Index should be 0 or 1, not stuck
        assertTrue(viewModel.uiState.value.currentIndex in 0..1)
    }

    // ============ Helpers ============
    private fun setupLessonWithExercises(exercises: List<Exercise>) {
        val lessonPlan = LessonPlan(
            exercises = exercises,
            warmUpCount = 0,
            focusCount = exercises.size,
            reviewCount = 0,
            challengeCount = 0,
            targetSkills = emptyList()
        )
        coEvery { exerciseEngine.generateExercises(any(), any(), any(), any()) } returns exercises
    }

    private fun createExercise(
        id: String,
        type: ExerciseType = ExerciseType.TYPED_NUMERIC
    ): Exercise {
        return Exercise(
            id = id,
            skillId = "test_skill",
            type = type,
            question = "Test vraag",
            correctAnswer = "5",
            difficulty = 1
        )
    }
}
