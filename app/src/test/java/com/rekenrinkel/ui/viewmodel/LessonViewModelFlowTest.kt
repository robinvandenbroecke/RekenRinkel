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

        // PATCH 5: Mock android.util.Log om "Method not mocked" errors te voorkomen
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0  // 3-arg versie met Throwable
        every { android.util.Log.d(any(), any<String>()) } returns 0
        every { android.util.Log.i(any(), any<String>()) } returns 0

        progressRepository = mockk(relaxed = true)
        profileRepository = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)
        exerciseEngine = mockk(relaxed = true)
        exerciseValidator = mockk(relaxed = true)

        every { settingsDataStore.premiumUnlocked } returns flowOf(false)
        mockProfile(age = 8)
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
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `submitAnswer - regular exercise flow with explicit sequence`() = runTest {
        // EXPLICIETE LESSON SEQUENCE: warm-up (regular) → focus (guided) → review (regular)
        val warmUp = createExercise("warmup", ExerciseType.TYPED_NUMERIC)
        val focusGuided = createExercise("focus_guided", ExerciseType.GUIDED_PRACTICE)
        val review1 = createExercise("review_1", ExerciseType.TYPED_NUMERIC)
        
        setupExactLessonSequence(
            warmupRegular = warmUp,
            focusItems = listOf(LessonItem.Guided(focusGuided)),
            reviewRegular = listOf(review1),
            challengeRegular = createExercise("challenge", ExerciseType.TYPED_NUMERIC)
        )
        every { exerciseValidator.validate(any(), any()) } returns true

        viewModel.startLesson()
        advanceTimeBy(500)

        // Start: warm-up exercise showing
        assertEquals(0, viewModel.uiState.value.currentIndex)
        assertEquals("warmup", viewModel.uiState.value.currentExercise?.id)
        assertEquals(LessonStepState.SHOWING, viewModel.uiState.value.stepState)

        // Submit answer
        viewModel.submitAnswer("5")
        advanceTimeBy(100)

        // Should show feedback
        assertEquals(LessonStepState.FEEDBACK, viewModel.uiState.value.stepState)

        advanceTimeBy(1000)

        // Advanced to guided practice
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals("focus_guided", viewModel.uiState.value.currentExercise?.id)
        assertEquals(ExerciseType.GUIDED_PRACTICE, viewModel.uiState.value.currentExercise?.type)
        assertEquals(LessonStepState.SHOWING, viewModel.uiState.value.stepState)
    }

    @Test
    fun `continueWorkedExample - should advance directly without feedback`() = runTest {
        // EXPLICIETE LESSON SEQUENCE met worked example voor jonger kind (age 6)
        mockProfile(age = 6)
        val warmUp = createExercise("warmup", ExerciseType.TYPED_NUMERIC)
        val focusWorked = createExercise("focus_worked", ExerciseType.WORKED_EXAMPLE)
        val focusGuided = createExercise("focus_guided", ExerciseType.GUIDED_PRACTICE)
        val review1 = createExercise("review_1", ExerciseType.TYPED_NUMERIC)
        
        setupExactLessonSequence(
            warmupRegular = warmUp,
            focusItems = listOf(
                LessonItem.Worked(focusWorked),
                LessonItem.Guided(focusGuided)
            ),
            reviewRegular = listOf(review1),
            challengeRegular = createExercise("challenge", ExerciseType.TYPED_NUMERIC)
        )

        viewModel.startLesson()
        advanceTimeBy(500)

        // Skip warm-up to get to worked example
        viewModel.skipExercise()
        advanceTimeBy(100)
        
        // Now at worked example
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals("focus_worked", viewModel.uiState.value.currentExercise?.id)
        assertEquals(ExerciseType.WORKED_EXAMPLE, viewModel.uiState.value.currentExercise?.type)

        // Continue worked example - should advance directly without feedback
        viewModel.continueWorkedExample()
        advanceTimeBy(100)

        // Advanced to guided practice
        assertEquals(2, viewModel.uiState.value.currentIndex)
        assertEquals("focus_guided", viewModel.uiState.value.currentExercise?.id)
        assertEquals(ExerciseType.GUIDED_PRACTICE, viewModel.uiState.value.currentExercise?.type)
        assertEquals(LessonStepState.SHOWING, viewModel.uiState.value.stepState)
    }

    @Test
    fun `guidedPractice - should validate and advance with feedback`() = runTest {
        // EXPLICIETE LESSON SEQUENCE: warm-up → worked → guided → review
        mockProfile(age = 6)
        val warmUp = createExercise("warmup", ExerciseType.TYPED_NUMERIC)
        val focusWorked = createExercise("focus_worked", ExerciseType.WORKED_EXAMPLE)
        val focusGuided = createExercise("focus_guided", ExerciseType.GUIDED_PRACTICE)
        val review1 = createExercise("review_1", ExerciseType.TYPED_NUMERIC)
        
        setupExactLessonSequence(
            warmupRegular = warmUp,
            focusItems = listOf(
                LessonItem.Worked(focusWorked),
                LessonItem.Guided(focusGuided)
            ),
            reviewRegular = listOf(review1),
            challengeRegular = createExercise("challenge", ExerciseType.TYPED_NUMERIC)
        )
        every { exerciseValidator.validate(any(), any()) } returns true

        viewModel.startLesson()
        advanceTimeBy(500)

        // Navigate to worked example
        viewModel.skipExercise()
        advanceTimeBy(100)
        viewModel.continueWorkedExample()
        advanceTimeBy(100)
        
        // Now at guided practice
        assertEquals(2, viewModel.uiState.value.currentIndex)
        assertEquals("focus_guided", viewModel.uiState.value.currentExercise?.id)
        assertEquals(ExerciseType.GUIDED_PRACTICE, viewModel.uiState.value.currentExercise?.type)

        // Submit answer on guided practice
        viewModel.submitAnswer("5")
        advanceTimeBy(100)

        // Should show feedback (guided practice uses normal feedback flow)
        assertEquals(LessonStepState.FEEDBACK, viewModel.uiState.value.stepState)

        advanceTimeBy(1000)

        // Advanced to next exercise
        assertEquals(3, viewModel.uiState.value.currentIndex)
        assertEquals(LessonStepState.SHOWING, viewModel.uiState.value.stepState)
    }

    @Test
    fun `skipExercise - should advance directly without feedback`() = runTest {
        // EXPLICIETE LESSON SEQUENCE met twee regular exercises
        val ex1 = createExercise("ex1", ExerciseType.TYPED_NUMERIC)
        val ex2 = createExercise("ex2", ExerciseType.TYPED_NUMERIC)
        
        setupExactLessonSequence(
            warmupRegular = ex1,
            focusItems = listOf(LessonItem.Regular(ex2)),
            reviewRegular = emptyList(),
            challengeRegular = createExercise("challenge", ExerciseType.TYPED_NUMERIC)
        )

        viewModel.startLesson()
        advanceTimeBy(500)

        // Start at first exercise
        assertEquals(0, viewModel.uiState.value.currentIndex)
        assertEquals("ex1", viewModel.uiState.value.currentExercise?.id)

        // Skip should advance directly without feedback
        viewModel.skipExercise()
        advanceTimeBy(100)

        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals("ex2", viewModel.uiState.value.currentExercise?.id)
        assertEquals(LessonStepState.SHOWING, viewModel.uiState.value.stepState)
    }

    @Test
    fun `skipExercise - should log exactly one result without duplicates`() = runTest {
        // EXPLICIETE LESSON SEQUENCE
        val ex1 = createExercise("ex1", ExerciseType.TYPED_NUMERIC)
        val ex2 = createExercise("ex2", ExerciseType.TYPED_NUMERIC)
        
        setupExactLessonSequence(
            warmupRegular = ex1,
            focusItems = listOf(LessonItem.Regular(ex2)),
            reviewRegular = emptyList(),
            challengeRegular = createExercise("challenge", ExerciseType.TYPED_NUMERIC)
        )

        viewModel.startLesson()
        advanceTimeBy(500)

        // Skip first exercise
        viewModel.skipExercise()
        advanceTimeBy(100)

        // Exactly one result logged
        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals("ex1", viewModel.uiState.value.results.first().exerciseId)
        assertEquals("[skipped]", viewModel.uiState.value.results.first().givenAnswer)

        // Advance completes
        advanceTimeBy(1000)

        // Still exactly one result, now at next exercise
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(1, viewModel.uiState.value.results.size)
        
        // Skip second exercise
        viewModel.skipExercise()
        advanceTimeBy(100)
        
        // Now two results
        assertEquals(2, viewModel.uiState.value.results.size)
    }

    @Test
    fun `double submit - should ignore second submit and log only once`() = runTest {
        // EXPLICIETE LESSON SEQUENCE
        val ex1 = createExercise("ex1", ExerciseType.TYPED_NUMERIC)
        val ex2 = createExercise("ex2", ExerciseType.TYPED_NUMERIC)
        
        setupExactLessonSequence(
            warmupRegular = ex1,
            focusItems = listOf(LessonItem.Regular(ex2)),
            reviewRegular = emptyList(),
            challengeRegular = createExercise("challenge", ExerciseType.TYPED_NUMERIC)
        )
        every { exerciseValidator.validate(any(), any()) } returns true

        viewModel.startLesson()
        advanceTimeBy(500)

        // Double submit
        viewModel.submitAnswer("5")
        viewModel.submitAnswer("5")  // Should be ignored
        advanceTimeBy(1500)

        // Only one result logged
        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun `exception during validation - should show error and allow recovery`() = runTest {
        // EXPLICIETE LESSON SEQUENCE
        val ex1 = createExercise("ex1", ExerciseType.TYPED_NUMERIC)
        val ex2 = createExercise("ex2", ExerciseType.TYPED_NUMERIC)
        
        setupExactLessonSequence(
            warmupRegular = ex1,
            focusItems = listOf(LessonItem.Regular(ex2)),
            reviewRegular = emptyList(),
            challengeRegular = createExercise("challenge", ExerciseType.TYPED_NUMERIC)
        )
        every { exerciseValidator.validate(any(), any()) } throws RuntimeException("Test error")

        viewModel.startLesson()
        advanceTimeBy(500)

        // Submit triggers error
        viewModel.submitAnswer("5")
        advanceTimeBy(1000)

        // Error state shown
        assertNotNull(viewModel.uiState.value.error)
        assertEquals(LessonStepState.ERROR, viewModel.uiState.value.stepState)
        
        // Current exercise unchanged (for recovery context)
        assertEquals("ex1", viewModel.uiState.value.currentExercise?.id)
    }

    @Test
    fun `error during progress update - recovery should not double log result`() = runTest {
        // EXPLICIETE LESSON SEQUENCE
        val ex1 = createExercise("ex1", ExerciseType.TYPED_NUMERIC)
        val ex2 = createExercise("ex2", ExerciseType.TYPED_NUMERIC)
        
        setupExactLessonSequence(
            warmupRegular = ex1,
            focusItems = listOf(LessonItem.Regular(ex2)),
            reviewRegular = emptyList(),
            challengeRegular = createExercise("challenge", ExerciseType.TYPED_NUMERIC)
        )
        every { exerciseValidator.validate(any(), any()) } returns true
        
        // First progress update fails
        var callCount = 0
        coEvery { progressRepository.getOrCreateProgress(any()) } answers {
            callCount++
            if (callCount == 1) throw RuntimeException("Simulated error")
            SkillProgress("test_skill")
        }

        viewModel.startLesson()
        advanceTimeBy(500)

        // Submit triggers error during progress update
        viewModel.submitAnswer("5")
        advanceTimeBy(500)

        assertEquals(LessonStepState.ERROR, viewModel.uiState.value.stepState)
        
        // Fix the mock for recovery
        coEvery { progressRepository.getOrCreateProgress(any()) } returns SkillProgress("test_skill")

        // Recover
        viewModel.continueAfterError()
        advanceTimeBy(1000)

        // Advanced to next, still only one result
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals("ex1", viewModel.uiState.value.results.first().exerciseId)
    }

    @Test
    fun `completion stages progression - NOT_STARTED to DONE`() = runTest {
        // EXPLICIETE LESSON SEQUENCE
        val ex1 = createExercise("ex1", ExerciseType.TYPED_NUMERIC)
        val ex2 = createExercise("ex2", ExerciseType.TYPED_NUMERIC)
        
        setupExactLessonSequence(
            warmupRegular = ex1,
            focusItems = listOf(LessonItem.Regular(ex2)),
            reviewRegular = emptyList(),
            challengeRegular = createExercise("challenge", ExerciseType.TYPED_NUMERIC)
        )
        every { exerciseValidator.validate(any(), any()) } returns true

        viewModel.startLesson()
        advanceTimeBy(500)

        // Initial state
        assertEquals(CompletionStage.NOT_STARTED, viewModel.uiState.value.completionStage)
        assertNull(viewModel.uiState.value.completionStageExerciseId)

        // Submit answer
        viewModel.submitAnswer("5")
        advanceTimeBy(50)

        // Result logged stage
        assertEquals(CompletionStage.RESULT_LOGGED, viewModel.uiState.value.completionStage)
        assertEquals("ex1", viewModel.uiState.value.completionStageExerciseId)

        // Complete flow
        advanceTimeBy(1500)

        // Exercise done, advanced to next
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals("ex2", viewModel.uiState.value.currentExercise?.id)
        // New exercise resets completion stage
        assertEquals(CompletionStage.NOT_STARTED, viewModel.uiState.value.completionStage)
    }

    @Test
    fun `no double side effects on rapid submit`() = runTest {
        // EXPLICIETE LESSON SEQUENCE
        val ex1 = createExercise("ex1", ExerciseType.TYPED_NUMERIC)
        val ex2 = createExercise("ex2", ExerciseType.TYPED_NUMERIC)
        
        setupExactLessonSequence(
            warmupRegular = ex1,
            focusItems = listOf(LessonItem.Regular(ex2)),
            reviewRegular = emptyList(),
            challengeRegular = createExercise("challenge", ExerciseType.TYPED_NUMERIC)
        )
        every { exerciseValidator.validate(any(), any()) } returns true

        viewModel.startLesson()
        advanceTimeBy(500)

        // Rapid double submit
        viewModel.submitAnswer("5")
        viewModel.submitAnswer("5")  // Should be ignored
        advanceTimeBy(1500)

        // No double side effects
        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals(1, viewModel.uiState.value.currentIndex)
        
        // Verify progress update only called once
        coVerify(exactly = 1) { progressRepository.updateProgress(any()) }
    }

    @Test
    fun `normal exercise flow - complete lesson lifecycle`() = runTest {
        // EXPLICIETE LESSON SEQUENCE met minimale les
        val ex1 = createExercise("ex1", ExerciseType.TYPED_NUMERIC)
        val ex2 = createExercise("ex2", ExerciseType.TYPED_NUMERIC)
        
        setupExactLessonSequence(
            warmupRegular = ex1,
            focusItems = listOf(LessonItem.Regular(ex2)),
            reviewRegular = emptyList(),
            challengeRegular = createExercise("challenge", ExerciseType.TYPED_NUMERIC)
        )
        every { exerciseValidator.validate(any(), any()) } returns true

        viewModel.startLesson()
        advanceTimeBy(500)

        // Complete first exercise
        assertEquals(CompletionStage.NOT_STARTED, viewModel.uiState.value.completionStage)
        viewModel.submitAnswer("5")
        advanceTimeBy(1500)

        // Advanced to second
        assertEquals(1, viewModel.uiState.value.currentIndex)
        
        // Complete second exercise
        viewModel.submitAnswer("5")
        advanceTimeBy(1500)
        
        // Should have 2 results
        assertEquals(2, viewModel.uiState.value.results.size)
    }

    /**
     * Sealed class voor expliciete lesson items - maakt de sequence leesbaar en type-veilig.
     */
    private sealed class LessonItem {
        abstract val exercise: Exercise
        data class Regular(override val exercise: Exercise) : LessonItem()
        data class Worked(override val exercise: Exercise) : LessonItem()
        data class Guided(override val exercise: Exercise) : LessonItem()
    }

    /**
     * PRODUCTIETROUWE TEST HELPER - Exacte LessonEngine-aanroepvolgorde
     * 
     * Deze helper bouwt een lesson die exact overeenkomt met hoe LessonEngine.buildLesson() werkt:
     * 1. Warm-up: altijd regular exercise (difficulty 1)
     * 2. Focus block: mix van worked/guided/regular afhankelijk van CPA-fase en leeftijd
     * 3. Review: altijd regular exercises
     * 4. Challenge: altijd regular exercise
     * 
     * Geen padding, geen fallback - elke aanroep moet expliciet voorzien zijn.
     */
    private fun setupExactLessonSequence(
        warmupRegular: Exercise,
        focusItems: List<LessonItem>,
        reviewRegular: List<Exercise>,
        challengeRegular: Exercise
    ) {
        // Bouw de volledige sequence in exacte LessonEngine-volgorde
        val sequence = mutableListOf<LessonItem>()
        
        // 1. Warm-up: altijd regular
        sequence.add(LessonItem.Regular(warmupRegular))
        
        // 2. Focus items: in de volgorde zoals ze in de lesson zitten
        sequence.addAll(focusItems)
        
        // 3. Review items: altijd regular
        reviewRegular.forEach { sequence.add(LessonItem.Regular(it)) }
        
        // 4. Challenge: altijd regular
        sequence.add(LessonItem.Regular(challengeRegular))
        
        // Maak queues per generator type voor strikte ordering
        val regularQueue = sequence.filterIsInstance<LessonItem.Regular>().map { it.exercise }.toMutableList()
        val workedQueue = sequence.filterIsInstance<LessonItem.Worked>().map { it.exercise }.toMutableList()
        val guidedQueue = sequence.filterIsInstance<LessonItem.Guided>().map { it.exercise }.toMutableList()
        
        // Strict mocks - elke onverwachte call faalt de test
        every { exerciseEngine.generateExercise(any(), any()) } answers {
            regularQueue.removeFirstOrNull()
                ?: error("Unexpected generateExercise call - sequence exhausted. " +
                        "Expected ${sequence.count { it is LessonItem.Regular }} regular calls.")
        }

        every { exerciseEngine.generateWorkedExample(any(), any()) } answers {
            workedQueue.removeFirstOrNull()
                ?: error("Unexpected generateWorkedExample call - sequence exhausted. " +
                        "Expected ${sequence.count { it is LessonItem.Worked }} worked calls.")
        }

        every { exerciseEngine.generateGuidedExercise(any(), any()) } answers {
            guidedQueue.removeFirstOrNull()
                ?: error("Unexpected generateGuidedExercise call - sequence exhausted. " +
                        "Expected ${sequence.count { it is LessonItem.Guided }} guided calls.")
        }
    }

    /**
     * Legacy overload - NIET GEBRUIKEN voor nieuwe tests
     * Alleen voor backward compatibility met bestaande tests.
     */
    @Deprecated("Gebruik setupExactLessonSequence voor nieuwe tests", ReplaceWith("setupExactLessonSequence"))
    private fun setupLessonWithExercises(
        regularExercises: List<Exercise>,
        workedExamples: List<Exercise> = emptyList(),
        guidedExercises: List<Exercise> = emptyList()
    ) {
        val workedQueue = workedExamples.toMutableList()
        val guidedQueue = guidedExercises.toMutableList()
        val regularQueue = regularExercises.toMutableList()

        every { exerciseEngine.generateWorkedExample(any(), any()) } answers {
            workedQueue.removeFirstOrNull()
        }

        every { exerciseEngine.generateGuidedExercise(any(), any()) } answers {
            guidedQueue.removeFirstOrNull()
        }

        every { exerciseEngine.generateExercise(any(), any()) } answers {
            regularQueue.removeFirstOrNull()
        }
    }

    /**
     * Legacy overload - NIET GEBRUIKEN voor nieuwe tests
     */
    @Deprecated("Gebruik setupExactLessonSequence voor nieuwe tests", ReplaceWith("setupExactLessonSequence"))
    private fun setupLessonWithExercises(exercises: List<Exercise>) {
        require(exercises.isNotEmpty())
        val warmup = exercises.first()
        val nextRegular = exercises.getOrElse(1) { createExercise("focus_regular_1") }
        setupExactLessonSequence(
            warmupRegular = warmup,
            focusItems = listOf(
                LessonItem.Guided(createExercise("focus_guided", ExerciseType.GUIDED_PRACTICE)),
                LessonItem.Regular(nextRegular)
            ),
            reviewRegular = listOf(
                createExercise("review_1"),
                createExercise("review_2")
            ),
            challengeRegular = createExercise("challenge")
        )
    }

    private fun mockProfile(age: Int) {
        every { profileRepository.getProfile() } returns flowOf(
            Profile(name = "Test", age = age, theme = Theme.DINOSAURS)
        )
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