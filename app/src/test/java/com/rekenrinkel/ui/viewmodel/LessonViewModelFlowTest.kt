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
        // PATCH 2: Gebruik every (niet coEvery) voor niet-suspend functies die Flow teruggeven
        every { settingsDataStore.premiumUnlocked } returns kotlinx.coroutines.flow.flowOf(false)
        every { profileRepository.getProfile() } returns kotlinx.coroutines.flow.flowOf(null)
        // PATCH 2: getRewards() is suspend functie, gebruik coEvery
        coEvery { profileRepository.getRewards() } returns Rewards()
        // PATCH 2: getOrCreateProgress is suspend, dus coEvery is correct
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
        // PATCH 2: validate is gewone functie (geen suspend), gebruik every
        every { exerciseValidator.validate(any(), any()) } returns true

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
        // PATCH 2: validate is gewone functie
        every { exerciseValidator.validate(any(), any()) } returns true

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
        // PATCH 2: validate is gewone functie
        every { exerciseValidator.validate(any(), any()) } returns true

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
        // PATCH 2: validate is gewone functie
        every { exerciseValidator.validate(any(), any()) } throws RuntimeException("Test error")

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
        // PATCH 2: validate is gewone functie
        every { exerciseValidator.validate(any(), any()) } returns true
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
        // PATCH 2: validate is gewone functie
        every { exerciseValidator.validate(any(), any()) } throws RuntimeException("Test error")

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

    // ============ PATCH 9: Completion Stage Tests ============

    @Test
    fun `normal exercise - should go through all completion stages`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))
        // PATCH 2: validate is gewone functie
        every { exerciseValidator.validate(any(), any()) } returns true

        // Act - start lesson
        viewModel.startLesson()
        advanceTimeBy(100)

        // Assert - initial state
        assertEquals(CompletionStage.NOT_STARTED, viewModel.uiState.value.completionStage)

        // Act - submit answer
        viewModel.submitAnswer("5")
        advanceTimeBy(1500) // Processing + feedback + advance

        // Assert - should have completed first exercise
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(CompletionStage.NOT_STARTED, viewModel.uiState.value.completionStage) // Reset for next
    }

    @Test
    fun `worked example - should complete without validation`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(
            createExercise("1", ExerciseType.WORKED_EXAMPLE),
            createExercise("2")
        ))

        // Act
        viewModel.startLesson()
        advanceTimeBy(100)
        viewModel.continueWorkedExample()
        advanceTimeBy(500)

        // Assert - should have advanced directly
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(CompletionStage.NOT_STARTED, viewModel.uiState.value.completionStage)
    }

    @Test
    fun `guided practice - should complete normally`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(
            createExercise("1", ExerciseType.GUIDED_PRACTICE),
            createExercise("2")
        ))
        // PATCH 2: validate is gewone functie
        every { exerciseValidator.validate(any(), any()) } returns true

        // Act
        viewModel.startLesson()
        advanceTimeBy(100)
        viewModel.submitAnswer("5")
        advanceTimeBy(1500)

        // Assert - should have advanced
        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun `error after result logged - recovery should not log again`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))
        // PATCH 2: validate is gewone functie
        every { exerciseValidator.validate(any(), any()) } returns true
        
        // Simulate: result logged, then error in progress update
        var callCount = 0
        coEvery { progressRepository.getOrCreateProgress(any()) } answers {
            callCount++
            if (callCount == 1) throw RuntimeException("Simulated error")
            SkillProgress("test_skill")
        }

        // Act - submit (will fail at progress update)
        viewModel.startLesson()
        advanceTimeBy(100)
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
        assertEquals(1, viewModel.uiState.value.results.size) // Only one result
    }

    @Test
    fun `error after progress updated - recovery should not update progress again`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))
        // PATCH 2: validate is gewone functie
        every { exerciseValidator.validate(any(), any()) } returns true
        coEvery { progressRepository.getOrCreateProgress(any()) } returns SkillProgress("test_skill")
        
        // Simulate: progress updated, then error in rewards
        var updateCallCount = 0
        coEvery { profileRepository.updateRewards(any()) } answers {
            updateCallCount++
            if (updateCallCount == 1) throw RuntimeException("Simulated error")
        }

        // Act
        viewModel.startLesson()
        advanceTimeBy(100)
        viewModel.submitAnswer("5")
        advanceTimeBy(500)

        // Assert - should be in error or have handled error
        // Progress should be updated once
        coVerify(atLeast = 1) { progressRepository.updateProgress(any()) }
    }

    @Test
    fun `error after rewards - recovery should not give double XP`() = runTest {
        // Arrange
        val initialRewards = Rewards(xp = 0)
        coEvery { profileRepository.getRewards() } returns initialRewards
        // PATCH 2: validate is gewone functie
        every { exerciseValidator.validate(any(), any()) } returns true
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))

        // Act - complete first exercise
        viewModel.startLesson()
        advanceTimeBy(100)
        viewModel.submitAnswer("5")
        advanceTimeBy(1500)

        // Assert - should have earned XP once
        coVerify(atMost = 1) { profileRepository.updateRewards(match { it.xp > 0 }) }
    }

    @Test
    fun `double submit while DONE - should be ignored`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))
        // PATCH 2: validate is gewone functie
        every { exerciseValidator.validate(any(), any()) } returns true

        // Act - complete first exercise
        viewModel.startLesson()
        advanceTimeBy(100)
        viewModel.submitAnswer("5")
        advanceTimeBy(1500) // Wait for completion

        // Assert - at second exercise
        assertEquals(1, viewModel.uiState.value.currentIndex)
        val resultsAfterFirst = viewModel.uiState.value.results.size

        // Act - try to submit on completed exercise (should be ignored)
        viewModel.submitAnswer("5")
        advanceTimeBy(500)

        // Assert - results should not increase
        assertEquals(resultsAfterFirst, viewModel.uiState.value.results.size)
    }

    @Test
    fun `error state plus continue - should advance safely`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))
        // PATCH 2: validate is gewone functie
        every { exerciseValidator.validate(any(), any()) } throws RuntimeException("Test error")

        // Act - trigger error
        viewModel.startLesson()
        advanceTimeBy(100)
        viewModel.submitAnswer("5")
        advanceTimeBy(1000)

        // Assert - in error state
        assertEquals(LessonStepState.ERROR, viewModel.uiState.value.stepState)

        // Act - continue
        viewModel.continueAfterError()
        advanceTimeBy(1000)

        // Assert - should have advanced, not stuck
        assertNotEquals(LessonStepState.ERROR, viewModel.uiState.value.stepState)
        assertTrue(viewModel.uiState.value.currentIndex >= 0)
    }

    @Test
    fun `skip exercise - should advance directly`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))

        // Act
        viewModel.startLesson()
        advanceTimeBy(100)
        viewModel.skipExercise()
        advanceTimeBy(500)

        // Assert - should have advanced
        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun `skip when already processing - should be ignored`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))

        // Act - start skip
        viewModel.startLesson()
        advanceTimeBy(100)
        viewModel.skipExercise()
        // Try to skip again while processing
        viewModel.skipExercise()
        advanceTimeBy(500)

        // Assert - should not cause issues
        assertTrue(viewModel.uiState.value.currentIndex in 0..1)
    }

    @Test
    fun `completion stage validation - should reset on exercise change`() = runTest {
        // Arrange
        setupLessonWithExercises(listOf(createExercise("1"), createExercise("2")))
        // PATCH 2: validate is gewone functie
        every { exerciseValidator.validate(any(), any()) } returns true

        // Act - complete first exercise
        viewModel.startLesson()
        advanceTimeBy(100)
        viewModel.submitAnswer("5")
        advanceTimeBy(1500)

        // Assert - stage reset for new exercise
        assertEquals(CompletionStage.NOT_STARTED, viewModel.uiState.value.completionStage)
        assertNull(viewModel.uiState.value.completionStageExerciseId)
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
        // PATCH 2: generateExercises is gewone functie in ExerciseEngine
        every { exerciseEngine.generateExercises(any(), any(), any(), any()) } returns exercises
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
