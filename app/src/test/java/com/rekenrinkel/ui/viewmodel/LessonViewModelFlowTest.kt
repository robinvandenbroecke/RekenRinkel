package com.rekenrinkel.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.repository.ProfileRepository
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.*
import com.rekenrinkel.domain.model.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/**
 * PATCH 1-9: Tests voor robuuste lesflow
 * Geactualiseerd naar huidige model- en enginecontract
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

        // Setup default mocks voor Flows
        every { settingsDataStore.premiumUnlocked } returns flowOf(false)
        every { profileRepository.getProfile() } returns flowOf(
            Profile(name = "Test", age = 8, theme = Theme.DINOSAURS)
        )
        coEvery { profileRepository.getRewards() } returns Rewards()
        coEvery { progressRepository.getOrCreateProgress(any()) } returns SkillProgress("test_skill")
        coEvery { progressRepository.getAllProgress() } returns flowOf(emptyList())
        coEvery { progressRepository.updateProgress(any()) } just Runs
        coEvery { profileRepository.updateRewards(any()) } just Runs

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
        // Arrange - setup lesson met 2 oefeningen
        val exercises = listOf(
            createExercise("1"),
            createExercise("2")
        )
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true

        // Start de les
        viewModel.startLesson()
        advanceTimeBy(500)

        // Act - submit answer
        viewModel.submitAnswer("5")
        advanceTimeBy(100)

        // Assert - should be in FEEDBACK state
        assertEquals(LessonStepState.FEEDBACK, viewModel.uiState.value.stepState)

        // Act - wait for auto-advance
        advanceTimeBy(1000)

        // Assert - should have advanced to next exercise
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(LessonStepState.SHOWING, viewModel.uiState.value.stepState)
    }

    // ============ TEST 2: WORKED_EXAMPLE ============
    @Test
    fun `continueWorkedExample - should advance directly without feedback`() = runTest {
        // Arrange
        val exercises = listOf(
            createExercise("1", ExerciseType.WORKED_EXAMPLE),
            createExercise("2")
        )
        setupLessonWithExercises(exercises)

        // Start lesson
        viewModel.startLesson()
        advanceTimeBy(500)

        // Act
        viewModel.continueWorkedExample()
        advanceTimeBy(100)

        // Assert - should have advanced directly
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(LessonStepState.SHOWING, viewModel.uiState.value.stepState)
    }

    // ============ TEST 3: GUIDED_PRACTICE ============
    @Test
    fun `guidedPractice - should validate and advance with feedback`() = runTest {
        // Arrange
        val exercises = listOf(
            createExercise("1", ExerciseType.GUIDED_PRACTICE),
            createExercise("2")
        )
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true

        // Start lesson
        viewModel.startLesson()
        advanceTimeBy(500)

        // Act
        viewModel.submitAnswer("5")
        advanceTimeBy(100)

        // Assert - should show feedback
        assertEquals(LessonStepState.FEEDBACK, viewModel.uiState.value.stepState)

        // Act - wait for auto-advance
        advanceTimeBy(1000)

        // Assert - should have advanced
        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    // ============ TEST 4: SKIP ============
    @Test
    fun `skipExercise - should advance directly`() = runTest {
        // Arrange
        val exercises = listOf(
            createExercise("1"),
            createExercise("2")
        )
        setupLessonWithExercises(exercises)

        // Start lesson
        viewModel.startLesson()
        advanceTimeBy(500)

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
        val exercises = listOf(
            createExercise("1"),
            createExercise("2")
        )
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true

        // Start lesson
        viewModel.startLesson()
        advanceTimeBy(500)

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
        val exercises = listOf(
            createExercise("1"),
            createExercise("2")
        )
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } throws RuntimeException("Test error")

        // Start lesson
        viewModel.startLesson()
        advanceTimeBy(500)

        // Act
        viewModel.submitAnswer("5")
        advanceTimeBy(1000)

        // Assert - error should be visible, not silently stuck
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error?.contains("Test error") == true)
    }

    // ============ TEST 7: Error recovery ============
    @Test
    fun `error after result logged - recovery should not log again`() = runTest {
        // Arrange
        val exercises = listOf(
            createExercise("1"),
            createExercise("2")
        )
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true
        
        // Simulate: result logged, then error in progress update
        var callCount = 0
        coEvery { progressRepository.getOrCreateProgress(any()) } answers {
            callCount++
            if (callCount == 1) throw RuntimeException("Simulated error")
            SkillProgress("test_skill")
        }

        // Start lesson
        viewModel.startLesson()
        advanceTimeBy(500)

        // Act - submit (will fail at progress update)
        viewModel.submitAnswer("5")
        advanceTimeBy(500)

        // Assert - should be in error state
        assertEquals(LessonStepState.ERROR, viewModel.uiState.value.stepState)
        
        // Reset mock for recovery
        coEvery { progressRepository.getOrCreateProgress(any()) } returns SkillProgress("test_skill")

        // Act - recover
        viewModel.continueAfterError()
        advanceTimeBy(1000)

        // Assert - should have advanced, results should not be duplicated
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(1, viewModel.uiState.value.results.size)
    }

    // ============ TEST 8: Completion stages ============
    @Test
    fun `normal exercise - should go through all completion stages`() = runTest {
        // Arrange
        val exercises = listOf(
            createExercise("1"),
            createExercise("2")
        )
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true

        // Start lesson
        viewModel.startLesson()
        advanceTimeBy(500)

        // Assert - initial state
        assertEquals(CompletionStage.NOT_STARTED, viewModel.uiState.value.completionStage)

        // Act - submit answer
        viewModel.submitAnswer("5")
        advanceTimeBy(1500)

        // Assert - should have completed first exercise
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(CompletionStage.NOT_STARTED, viewModel.uiState.value.completionStage)
    }

    // ============ TEST 9: Stale snapshot test ============
    @Test
    fun `completion stages use actual state not stale snapshot`() = runTest {
        // Arrange
        val exercises = listOf(
            createExercise("1"),
            createExercise("2")
        )
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true

        // Start lesson
        viewModel.startLesson()
        advanceTimeBy(500)

        // Act - submit answer
        viewModel.submitAnswer("5")
        advanceTimeBy(50)
        
        // Assert - stage moet RESULT_LOGGED zijn (niet NOT_STARTED)
        val stageAfterSubmit = viewModel.uiState.value.completionStage
        assertTrue(
            "Stage should be at least RESULT_LOGGED after submit, but was $stageAfterSubmit",
            stageAfterSubmit >= CompletionStage.RESULT_LOGGED
        )
        
        // Laat completion afmaken
        advanceTimeBy(1500)
        
        // Assert - oefening moet volledig afgerond zijn
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(1, viewModel.uiState.value.results.size)
    }

    // ============ TEST 10: No double side effects ============
    @Test
    fun `no double side effects when stage already advanced`() = runTest {
        // Arrange
        val exercises = listOf(
            createExercise("1"),
            createExercise("2")
        )
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true

        // Start lesson
        viewModel.startLesson()
        advanceTimeBy(500)

        // Act - submit
        viewModel.submitAnswer("5")
        advanceTimeBy(50)
        
        // Probeer nog een submit (moet geblokkeerd worden)
        viewModel.submitAnswer("5")
        advanceTimeBy(50)
        
        // Assert - maar één resultaat
        assertEquals(1, viewModel.uiState.value.results.size)
        
        // Laat completion afmaken
        advanceTimeBy(1500)
        
        // Assert - nog steeds maar één resultaat
        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    // ============ Helpers ============
    private fun setupLessonWithExercises(exercises: List<Exercise>) {
        // Mock exerciseEngine om de exercises te genereren
        // LessonEngine roept generateExercise aan met specifieke skill IDs
        exercises.forEach { exercise ->
            every { 
                exerciseEngine.generateExercise(any(), any()) 
            } returns exercise
            every { 
                exerciseEngine.generateWorkedExample(any(), any()) 
            } returns exercise
            every { 
                exerciseEngine.generateGuidedExercise(any(), any()) 
            } returns exercise
        }
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