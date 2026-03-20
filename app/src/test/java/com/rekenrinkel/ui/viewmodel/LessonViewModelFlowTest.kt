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

    @Test
    fun `submitAnswer - should show feedback then auto-advance`() = runTest {
        val exercises = listOf(createExercise("1"), createExercise("2"))
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true

        viewModel.startLesson()
        advanceTimeBy(500)

        viewModel.submitAnswer("5")
        advanceTimeBy(100)

        assertEquals(LessonStepState.FEEDBACK, viewModel.uiState.value.stepState)

        advanceTimeBy(1000)

        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(LessonStepState.SHOWING, viewModel.uiState.value.stepState)
    }

    @Test
    fun `continueWorkedExample - should advance directly without feedback`() = runTest {
        val exercises = listOf(
            createExercise("1", ExerciseType.WORKED_EXAMPLE),
            createExercise("2")
        )
        setupLessonWithExercises(exercises)

        viewModel.startLesson()
        advanceTimeBy(500)

        viewModel.continueWorkedExample()
        advanceTimeBy(100)

        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(LessonStepState.SHOWING, viewModel.uiState.value.stepState)
    }

    @Test
    fun `guidedPractice - should validate and advance with feedback`() = runTest {
        val exercises = listOf(
            createExercise("1", ExerciseType.GUIDED_PRACTICE),
            createExercise("2")
        )
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true

        viewModel.startLesson()
        advanceTimeBy(500)

        viewModel.submitAnswer("5")
        advanceTimeBy(100)

        assertEquals(LessonStepState.FEEDBACK, viewModel.uiState.value.stepState)

        advanceTimeBy(1000)

        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun `skipExercise - should advance directly`() = runTest {
        val exercises = listOf(createExercise("1"), createExercise("2"))
        setupLessonWithExercises(exercises)

        viewModel.startLesson()
        advanceTimeBy(500)

        viewModel.skipExercise()
        advanceTimeBy(100)

        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(LessonStepState.SHOWING, viewModel.uiState.value.stepState)
    }

    @Test
    fun `skipExercise - should log exactly one result`() = runTest {
        val exercises = listOf(createExercise("1"), createExercise("2"))
        setupLessonWithExercises(exercises)

        viewModel.startLesson()
        advanceTimeBy(500)

        viewModel.skipExercise()
        advanceTimeBy(100)

        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals("[skipped]", viewModel.uiState.value.results.first().givenAnswer)

        advanceTimeBy(1000)

        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(1, viewModel.uiState.value.results.size)
    }

    @Test
    fun `double submit - should ignore second submit`() = runTest {
        val exercises = listOf(createExercise("1"), createExercise("2"))
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true

        viewModel.startLesson()
        advanceTimeBy(500)

        viewModel.submitAnswer("5")
        viewModel.submitAnswer("5")
        advanceTimeBy(1500)

        assertEquals(1, viewModel.uiState.value.results.size)
    }

    @Test
    fun `exception during finish - should show error`() = runTest {
        val exercises = listOf(createExercise("1"), createExercise("2"))
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } throws RuntimeException("Test error")

        viewModel.startLesson()
        advanceTimeBy(500)

        viewModel.submitAnswer("5")
        advanceTimeBy(1000)

        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `error after result logged - recovery should not log again`() = runTest {
        val exercises = listOf(createExercise("1"), createExercise("2"))
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true
        
        var callCount = 0
        coEvery { progressRepository.getOrCreateProgress(any()) } answers {
            callCount++
            if (callCount == 1) throw RuntimeException("Simulated error")
            SkillProgress("test_skill")
        }

        viewModel.startLesson()
        advanceTimeBy(500)

        viewModel.submitAnswer("5")
        advanceTimeBy(500)

        assertEquals(LessonStepState.ERROR, viewModel.uiState.value.stepState)
        
        coEvery { progressRepository.getOrCreateProgress(any()) } returns SkillProgress("test_skill")

        viewModel.continueAfterError()
        advanceTimeBy(1000)

        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(1, viewModel.uiState.value.results.size)
    }

    @Test
    fun `completion stages use actual state`() = runTest {
        val exercises = listOf(createExercise("1"), createExercise("2"))
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true

        viewModel.startLesson()
        advanceTimeBy(500)

        assertEquals(CompletionStage.NOT_STARTED, viewModel.uiState.value.completionStage)

        viewModel.submitAnswer("5")
        advanceTimeBy(50)

        assertTrue(viewModel.uiState.value.completionStage >= CompletionStage.RESULT_LOGGED)

        advanceTimeBy(1500)

        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun `no double side effects`() = runTest {
        val exercises = listOf(createExercise("1"), createExercise("2"))
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true

        viewModel.startLesson()
        advanceTimeBy(500)

        viewModel.submitAnswer("5")
        viewModel.submitAnswer("5")
        advanceTimeBy(1500)

        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun `normal exercise - should go through all completion stages`() = runTest {
        val exercises = listOf(createExercise("1"), createExercise("2"))
        setupLessonWithExercises(exercises)
        every { exerciseValidator.validate(any(), any()) } returns true

        viewModel.startLesson()
        advanceTimeBy(500)

        assertEquals(CompletionStage.NOT_STARTED, viewModel.uiState.value.completionStage)

        viewModel.submitAnswer("5")
        advanceTimeBy(1500)

        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    private fun setupLessonWithExercises(exercises: List<Exercise>) {
        val workedQueue = ArrayDeque(exercises.filter { it.type == ExerciseType.WORKED_EXAMPLE })
        val guidedQueue = ArrayDeque(exercises.filter { it.type == ExerciseType.GUIDED_PRACTICE })
        val regularQueue = ArrayDeque(exercises.filter { 
            it.type != ExerciseType.WORKED_EXAMPLE && it.type != ExerciseType.GUIDED_PRACTICE 
        })

        every { exerciseEngine.generateWorkedExample(any(), any()) } answers {
            workedQueue.removeFirstOrNull() ?: error("Unexpected generateWorkedExample call")
        }
        every { exerciseEngine.generateGuidedExercise(any(), any()) } answers {
            guidedQueue.removeFirstOrNull() ?: error("Unexpected generateGuidedExercise call")
        }
        every { exerciseEngine.generateExercise(any(), any()) } answers {
            regularQueue.removeFirstOrNull() ?: error("Unexpected generateExercise call")
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