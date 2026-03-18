package com.rekenrinkel.data.repository

import com.rekenrinkel.data.local.dao.FakeSkillProgressDao
import com.rekenrinkel.domain.content.CpaPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressRepositoryTest {

    private lateinit var dao: FakeSkillProgressDao
    private lateinit var repository: ProgressRepository

    @Before
    fun setup() {
        dao = FakeSkillProgressDao()
        repository = ProgressRepository(dao)
    }

    @Test
    fun `getOrCreateProgress persists and returns same record on second read`() = runTest {
        val created = repository.getOrCreateProgress("foundation_number_bonds_10")
        val loaded = repository.getOrCreateProgress("foundation_number_bonds_10")

        assertEquals("foundation_number_bonds_10", created.skillId)
        assertEquals(created.skillId, loaded.skillId)
        assertEquals(1, repository.getAllProgress().first().size)
    }

    @Test
    fun `updateCpaPhase persists current phase`() = runTest {
        repository.getOrCreateProgress("arithmetic_add_10_concrete")

        repository.updateCpaPhase("arithmetic_add_10_concrete", CpaPhase.PICTORIAL)

        val progress = repository.getProgress("arithmetic_add_10_concrete").first()
        assertNotNull(progress)
        assertEquals(CpaPhase.PICTORIAL, progress?.currentCpaPhase)
    }

    @Test
    fun `recordResult updates mastery difficulty and counters once`() = runTest {
        repository.recordResult("foundation_counting", isCorrect = true, responseTimeMs = 1500)

        val progress = repository.getProgress("foundation_counting").first()!!
        assertEquals(1, progress.correctCount)
        assertEquals(0, progress.incorrectCount)
        assertTrue(progress.masteryScore > 0)
        assertTrue(progress.currentDifficultyTier >= 1)
    }

    @Test
    fun `clearAll resets persisted skill progress`() = runTest {
        repository.recordResult("foundation_counting", isCorrect = true, responseTimeMs = 1500)
        repository.recordResult("foundation_subitize_5", isCorrect = false, responseTimeMs = 3000)

        repository.clearAll()

        assertTrue(repository.getAllProgress().first().isEmpty())
    }
}
