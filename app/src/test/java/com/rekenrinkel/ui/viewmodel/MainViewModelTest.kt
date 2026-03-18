package com.rekenrinkel.ui.viewmodel

import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.local.dao.FakeProfileDao
import com.rekenrinkel.data.local.dao.FakeSkillProgressDao
import com.rekenrinkel.data.repository.ProfileRepository
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.content.CpaPhase
import com.rekenrinkel.domain.engine.ExerciseEngine
import com.rekenrinkel.domain.engine.LessonEngine
import com.rekenrinkel.domain.engine.SessionEngine
import com.rekenrinkel.domain.model.StartingBand
import com.rekenrinkel.domain.model.Theme
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var profileRepository: ProfileRepository
    private lateinit var progressRepository: ProgressRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val profileDao = FakeProfileDao()
        val progressDao = FakeSkillProgressDao()
        settingsDataStore = mockk(relaxed = true)

        val soundFlow = MutableStateFlow(true)
        val premiumFlow = MutableStateFlow(false)
        val einkFlow = MutableStateFlow(false)
        val onboardingFlow = MutableStateFlow(false)

        io.mockk.every { settingsDataStore.soundEnabled } returns soundFlow
        io.mockk.every { settingsDataStore.premiumUnlocked } returns premiumFlow
        io.mockk.every { settingsDataStore.einkModeEnabled } returns einkFlow
        io.mockk.every { settingsDataStore.onboardingCompleted } returns onboardingFlow
        coEvery { settingsDataStore.setProfileName(any()) } just runs
        coEvery { settingsDataStore.setTheme(any()) } just runs
        coEvery { settingsDataStore.setOnboardingCompleted(any()) } just runs
        coEvery { settingsDataStore.setPremiumUnlocked(any()) } just runs
        coEvery { settingsDataStore.setSoundEnabled(any()) } just runs
        coEvery { settingsDataStore.setEinkModeEnabled(any()) } just runs

        profileRepository = ProfileRepository(profileDao, settingsDataStore)
        progressRepository = ProgressRepository(progressDao)
        val exerciseEngine = ExerciseEngine()
        val lessonEngine = LessonEngine(exerciseEngine, progressRepository)
        val sessionEngine = SessionEngine(exerciseEngine, progressRepository)

        viewModel = MainViewModel(
            profileRepository = profileRepository,
            progressRepository = progressRepository,
            exerciseEngine = exerciseEngine,
            lessonEngine = lessonEngine,
            sessionEngine = sessionEngine,
            settingsDataStore = settingsDataStore
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `age 5 onboarding starts in foundation concrete`() = runTest {
        viewModel.completeOnboarding("Test", 5, Theme.DINOSAURS)
        advanceUntilIdle()

        val profile = profileRepository.getProfile().first()!!
        assertEquals(StartingBand.FOUNDATION, profile.startingBand)
        assertEquals(CpaPhase.CONCRETE, profile.placementAnalysisResult?.startCpaPhase)
        assertTrue(profile.placementAnalysisResult?.startSkills?.contains("foundation_subitize_5") == true)
    }

    @Test
    fun `age 7 onboarding starts in early arithmetic pictorial`() = runTest {
        viewModel.completeOnboarding("Test", 7, Theme.CARS)
        advanceUntilIdle()

        val profile = profileRepository.getProfile().first()!!
        assertEquals(StartingBand.EARLY_ARITHMETIC, profile.startingBand)
        assertEquals(CpaPhase.PICTORIAL, profile.placementAnalysisResult?.startCpaPhase)
        assertTrue(profile.placementAnalysisResult?.startSkills?.contains("arithmetic_add_10_concrete") == true)
        assertTrue(profile.placementAnalysisResult?.startSkills?.contains("patterns_doubles") == true)
    }

    @Test
    fun `age 10 onboarding starts in extended abstract`() = runTest {
        viewModel.completeOnboarding("Test", 10, Theme.SPACE)
        advanceUntilIdle()

        val profile = profileRepository.getProfile().first()!!
        assertEquals(StartingBand.EXTENDED, profile.startingBand)
        assertEquals(CpaPhase.ABSTRACT, profile.placementAnalysisResult?.startCpaPhase)
        assertTrue(profile.placementAnalysisResult?.startSkills?.contains("advanced_groups") == true)
        assertTrue(profile.placementAnalysisResult?.startSkills?.contains("advanced_place_value") == true)
    }
}
