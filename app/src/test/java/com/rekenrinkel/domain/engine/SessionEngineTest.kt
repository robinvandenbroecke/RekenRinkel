package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.content.ContentRepository
import com.rekenrinkel.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SessionEngineTest {
    
    @Test
    fun `calculateXp with speed bonus`() {
        val engine = SessionEngine(ExerciseEngine(), FakeProgressRepository())
        
        val results = listOf(
            ExerciseResult("1", "skill1", true, 1000, "5"),  // Snel: +15
            ExerciseResult("2", "skill2", true, 2000, "8"),  // Normaal: +10
            ExerciseResult("3", "skill3", false, 5000, "3")  // Fout: 0
        )
        
        val xp = engine.calculateXp(results)
        
        // 10 base + 10 speed + 10 base + 5 speed + 0 = 35
        assertTrue("XP should include speed bonuses", xp > 20)
    }
    
    @Test
    fun `calculateStars uses correct thresholds`() {
        val engine = SessionEngine(ExerciseEngine(), FakeProgressRepository())
        
        assertEquals(3, engine.calculateStars(0.9f))
        assertEquals(2, engine.calculateStars(0.8f))
        assertEquals(2, engine.calculateStars(0.75f))
        assertEquals(1, engine.calculateStars(0.74f))
        assertEquals(1, engine.calculateStars(0.5f))
        assertEquals(0, engine.calculateStars(0.49f))
    }
    
    @Test
    fun `isSkillUnlocked checks prerequisites`() = runBlocking {
        val repo = FakeProgressRepository()
        val engine = SessionEngine(ExerciseEngine(), repo)
        
        // Without mastering prerequisites
        assertFalse(engine.isSkillUnlocked("arithmetic_add_10"))
        
        // After mastering prerequisite
        repo.addMockProgress("foundation_number_images_5", 60)
        assertTrue(engine.isSkillUnlocked("arithmetic_add_10"))
    }
    
    @Test
    fun `bridge over 10 requires bridge conditions`() {
        val engine = ExerciseEngine()
        
        repeat(20) {
            val exercise = engine.generateExercise("arithmetic_bridge_add", 3)
            
            val match = Regex("""(\d+) \+ (\d+)""").find(exercise.question)
            assertNotNull(match)
            
            val a = match!!.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            
            // Both under 10
            assertTrue("a should be < 10", a < 10)
            assertTrue("b should be < 10", b < 10)
            
            // Sum over 10
            assertTrue("Sum should be > 10", a + b > 10)
            
            // Requires bridge
            assertTrue("Should require bridge", (a % 10) + (b % 10) >= 10)
        }
    }
    
    @Test
    fun `session respects skill mix ratio`() = runBlocking {
        val repo = FakeProgressRepository()
        val engine = SessionEngine(ExerciseEngine(), repo)
        
        // Seed with some progress
        repo.addMockProgress("foundation_number_images_5", 80) // Mastered
        repo.addMockProgress("foundation_splits_10", 30)      // Practicing
        repo.addMockProgress("arithmetic_add_10", 10)         // Emerging
        
        val session = engine.buildSession(isPremiumUnlocked = false)
        
        // Should have 8 exercises
        assertEquals(8, session.size)
        
        // Should have variety (not all same skill)
        val uniqueSkills = session.map { it.skillId }.toSet().size
        assertTrue("Should have skill variety", uniqueSkills > 1)
    }
    
    @Test
    fun `getalbeelden nooit nul`() {
        val engine = ExerciseEngine()
        
        repeat(30) {
            val exercise = engine.generateExercise("foundation_number_images_5", 1)
            val count = exercise.visualData?.count
            assertNotNull("Count should not be null", count)
            assertTrue("Count should be >= 1", count!! >= 1)
            assertTrue("Count should be <= 5", count <= 5)
        }
    }
    
    @Test
    fun `generator validator contract match`() {
        val engine = ExerciseEngine()
        val validator = ExerciseValidator()
        
        // Test for each exercise type
        val testTypes = listOf(
            "foundation_number_images_5",
            "foundation_splits_10",
            "arithmetic_add_10",
            "arithmetic_sub_10",
            "patterns_doubles",
            "patterns_halves",
            "patterns_count_2",
            "advanced_compare_100"
        )
        
        testTypes.forEach { skillId ->
            val exercise = engine.generateExercise(skillId, 2)
            
            // Correct answer should validate
            assertTrue(
                "Correct answer should validate for $skillId",
                validator.validate(exercise, exercise.correctAnswer)
            )
            
            // Wrong answer should not validate
            assertFalse(
                "Wrong answer should not validate for $skillId",
                validator.validate(exercise, "999999")
            )
        }
    }
}

/**
 * Fake repository for testing
 */
class FakeProgressRepository : com.rekenrinkel.data.repository.ProgressRepository(
    com.rekenrinkel.data.local.dao.FakeSkillProgressDao()
) {
    private val mockProgress = mutableMapOf<String, SkillProgress>()
    
    fun addMockProgress(skillId: String, masteryScore: Int) {
        mockProgress[skillId] = SkillProgress(
            skillId = skillId,
            masteryScore = masteryScore,
            correctAnswers = masteryScore / 10,
            wrongAnswers = 10 - masteryScore / 10
        )
    }
    
    override suspend fun getAllProgress(): List<SkillProgress> {
        return mockProgress.values.toList()
    }
    
    override suspend fun getOrCreateProgress(skillId: String): SkillProgress {
        return mockProgress[skillId] ?: SkillProgress(skillId = skillId)
    }
}