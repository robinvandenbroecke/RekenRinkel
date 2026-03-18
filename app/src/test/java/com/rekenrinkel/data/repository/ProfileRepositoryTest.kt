package com.rekenrinkel.data.repository

import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.local.dao.FakeProfileDao
import com.rekenrinkel.domain.content.CpaPhase
import com.rekenrinkel.domain.model.StartingBand
import com.rekenrinkel.domain.model.Theme
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryTest {

    private lateinit var dao: FakeProfileDao
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var repository: ProfileRepository

    @Before
    fun setup() {
        dao = FakeProfileDao()
        settingsDataStore = mockk(relaxed = true)
        io.mockk.coEvery { settingsDataStore.setProfileName(any()) } just runs
        io.mockk.coEvery { settingsDataStore.setTheme(any()) } just runs
        repository = ProfileRepository(dao, settingsDataStore)
    }

    @Test
    fun `createProfileWithStartConfig persists age band and start config`() = runTest {
        repository.createProfileWithStartConfig(
            name = "Robin",
            age = 7,
            theme = Theme.DINOSAURS,
            startingBand = StartingBand.EARLY_ARITHMETIC,
            startSkills = listOf("foundation_number_bonds_10", "arithmetic_add_10_concrete"),
            startCpaPhase = CpaPhase.CONCRETE
        )

        val profile = repository.getProfile().first()
        assertNotNull(profile)
        assertEquals(7, profile?.age)
        assertEquals(StartingBand.EARLY_ARITHMETIC, profile?.startingBand)
        assertEquals(listOf("foundation_number_bonds_10", "arithmetic_add_10_concrete"), profile?.placementAnalysisResult?.startSkills)
        assertEquals(CpaPhase.CONCRETE, profile?.placementAnalysisResult?.startCpaPhase)
        coVerify(exactly = 1) { settingsDataStore.setProfileName("Robin") }
        coVerify(exactly = 1) { settingsDataStore.setTheme(Theme.DINOSAURS) }
    }

    @Test
    fun `updateRewards persists xp and streak`() = runTest {
        val created = repository.createProfile("Mila", 6, Theme.CARS)
        val updatedRewards = created.rewards.addXp(25).updateStreak()

        repository.updateRewards(updatedRewards)

        val stored = repository.getProfileSync()!!
        assertEquals(25, stored.totalXp)
        assertEquals(1, stored.currentStreak)
    }

    @Test
    fun `clearAll removes persisted profile`() = runTest {
        repository.createProfile("Mila", 6, Theme.CARS)

        repository.clearAll()

        assertNull(repository.getProfileSync())
    }
}
