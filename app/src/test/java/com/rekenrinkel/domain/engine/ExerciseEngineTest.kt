package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.content.ContentRepository
import com.rekenrinkel.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExerciseEngineTest {
    
    private lateinit var engine: ExerciseEngine
    
    @Before
    fun setup() {
        engine = ExerciseEngine()
    }
    
    // ============================================
    // FOUNDATION TESTS
    // ============================================
    
    @Test
    fun `generateExercise creates valid number images`() {
        val exercise = engine.generateExercise("foundation_number_images_5", 1)
        
        assertEquals("foundation_number_images_5", exercise.skillId)
        assertEquals(ExerciseType.VISUAL_QUANTITY, exercise.type)
        assertNotNull(exercise.visualData)
        assertNotNull(exercise.visualData?.count)
        
        val count = exercise.visualData?.count
        assertNotNull(count)
        assertTrue("Count $count should be in 1..5", count in 1..5)
        assertEquals(count.toString(), exercise.correctAnswer)
    }
    
    @Test
    fun `number images never generates zero`() {
        repeat(20) {
            val exercise = engine.generateExercise("foundation_number_images_5", 1)
            val count = exercise.visualData?.count
            assertTrue("Should not be zero", count != 0)
        }
    }
    
    @Test
    fun `generateExercise creates valid splits`() {
        val exercise = engine.generateExercise("foundation_splits_10", 2)
        
        assertTrue(exercise.type == ExerciseType.VISUAL_GROUPS || exercise.type == ExerciseType.MISSING_NUMBER)
        
        if (exercise.type == ExerciseType.VISUAL_GROUPS) {
            assertNotNull(exercise.visualData?.groups)
            val groups = exercise.visualData?.groups
            assertNotNull(groups)
            assertEquals(2, groups?.size)
            assertEquals(groups?.sum(), groups?.let { it[0] + it[1] })
        }
    }
    
    // ============================================
    // ARITHMETIC TESTS
    // ============================================
    
    @Test
    fun `addition within bounds`() {
        repeat(10) {
            val exercise = engine.generateExercise("arithmetic_add_10", 1)
            
            // Extract numbers from question
            val match = Regex("""(\d+) \+ (\d+)""").find(exercise.question)
            assertNotNull(match)
            
            val a = match!!.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            val sum = exercise.correctAnswer.toInt()
            
            assertEquals("$a + $b should equal $sum", a + b, sum)
            assertTrue("Sum $sum should be <= 10", sum <= 10)
            assertTrue("Sum $sum should be >= 2", sum >= 2)
        }
    }
    
    @Test
    fun `addition to 20 no bridge required when configured`() {
        repeat(10) {
            val exercise = engine.generateExercise("arithmetic_add_20", 2)
            
            val match = Regex("""(\d+) \+ (\d+)""").find(exercise.question)
            assertNotNull(match)
            
            val a = match!!.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            
            // No bridge required for this skill
            assertTrue("Should not require bridge", (a % 10) + (b % 10) < 10)
        }
    }
    
    @Test
    fun `bridge addition requires actual bridge`() {
        repeat(10) {
            val exercise = engine.generateExercise("arithmetic_bridge_add", 3)
            
            val match = Regex("""(\d+) \+ (\d+)""").find(exercise.question)
            assertNotNull(match)
            
            val a = match!!.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            
            // Both should be under 10
            assertTrue("a ($a) should be < 10", a < 10)
            assertTrue("b ($b) should be < 10", b < 10)
            
            // Sum should be over 10
            assertTrue("Sum (${a + b}) should be > 10", a + b > 10)
            
            // Should require bridge
            assertTrue("Should require bridge over 10", (a % 10) + (b % 10) >= 10)
        }
    }
    
    @Test
    fun `subtraction never produces negative`() {
        repeat(10) {
            val exercise = engine.generateExercise("arithmetic_sub_10", 1)
            
            val match = Regex("""(\d+) - (\d+)""").find(exercise.question)
            assertNotNull(match)
            
            val a = match!!.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            val result = exercise.correctAnswer.toInt()
            
            assertTrue("$a - $b should be >= 0", a >= b)
            assertTrue("$a - $b should equal $result", a - b == result)
            assertTrue("Result should be >= 0", result >= 0)
        }
    }
    
    @Test
    fun `bridge subtraction is correct`() {
        repeat(10) {
            val exercise = engine.generateExercise("arithmetic_bridge_sub", 3)
            
            val match = Regex("""(\d+) - (\d+)""").find(exercise.question)
            assertNotNull(match)
            
            val a = match!!.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            val result = exercise.correctAnswer.toInt()
            
            // Minuend should be > 10
            assertTrue("a ($a) should be > 10", a > 10)
            // Subtrahend should be < 10
            assertTrue("b ($b) should be < 10", b < 10)
            // Result should be < 10
            assertTrue("Result ($result) should be < 10", result < 10)
            // Should require bridge
            assertTrue("Should require bridge", a % 10 < b)
        }
    }
    
    // ============================================
    // PATTERN TESTS
    // ============================================
    
    @Test
    fun `doubles are correct`() {
        repeat(10) {
            val exercise = engine.generateExercise("patterns_doubles", 1)
            
            val match = Regex("""(\d+) \+ (\d+)""").find(exercise.question)
            assertNotNull(match)
            
            val a = match!!.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            
            // Should be same number
            assertEquals("Should be doubles", a, b)
            
            val result = exercise.correctAnswer.toInt()
            assertEquals("Double should be correct", a * 2, result)
        }
    }
    
    @Test
    fun `halves are only on even numbers`() {
        repeat(10) {
            val exercise = engine.generateExercise("patterns_halves", 1)
            
            val match = Regex("""De helft van (\d+)""").find(exercise.question)
            assertNotNull(match)
            
            val n = match!!.groupValues[1].toInt()
            
            // Should be even
            assertTrue("$n should be even", n % 2 == 0)
            
            val result = exercise.correctAnswer.toInt()
            assertEquals("Half should be correct", n / 2, result)
        }
    }
    
    @Test
    fun `skip counting is consistent`() {
        val exercise = engine.generateExercise("patterns_count_2", 2)
        
        assertEquals(ExerciseType.SIMPLE_SEQUENCE, exercise.type)
        
        val correct = exercise.correctAnswer.toInt()
        assertNotNull(correct)
        
        // Should be even for count by 2
        assertTrue("Should be even", correct % 2 == 0)
    }
    
    // ============================================
    // ADVANCED TESTS
    // ============================================
    
    @Test
    fun `comparison has valid options`() {
        val exercise = engine.generateExercise("advanced_compare_100", 3)
        
        assertEquals(ExerciseType.COMPARE_NUMBERS, exercise.type)
        assertNotNull(exercise.options)
        assertTrue("Should have options", exercise.options!!.size >= 3)
        assertTrue("Should contain <", exercise.options!!.contains("<"))
        assertTrue("Should contain >", exercise.options!!.contains(">"))
    }
    
    @Test
    fun `place value is correct`() {
        val exercise = engine.generateExercise("advanced_place_value", 3)
        
        val match = Regex("""(\d+) = \? tientallen en \? eenheden""").find(exercise.question)
        assertNotNull(match)
        
        val number = match!!.groupValues[1].toInt()
        
        // Parse answer "X en Y"
        val answerMatch = Regex("""(\d+) en (\d+)""").find(exercise.correctAnswer)
        assertNotNull(answerMatch)
        
        val tens = answerMatch!!.groupValues[1].toInt()
        val ones = answerMatch.groupValues[2].toInt()
        
        assertEquals("Place value should be correct", tens * 10 + ones, number)
    }
    
    @Test
    fun `groups multiplication is correct`() {
        val exercise = engine.generateExercise("advanced_groups", 3)
        
        val match = Regex("""(\d+) groepjes van (\d+)""").find(exercise.question)
        assertNotNull(match)
        
        val groups = match!!.groupValues[1].toInt()
        val perGroup = match.groupValues[2].toInt()
        val result = exercise.correctAnswer.toInt()
        
        assertEquals("Groups should multiply correctly", groups * perGroup, result)
    }
    
    @Test
    fun `table multiplication is correct`() {
        val exercise = engine.generateExercise("advanced_table_5", 3)
        
        val match = Regex("""(\d+) [×x] (\d+)""").find(exercise.question)
        assertNotNull(match)
        
        val n = match!!.groupValues[1].toInt()
        val multiplier = match.groupValues[2].toInt()
        val result = exercise.correctAnswer.toInt()
        
        assertEquals("Table should be correct", n * multiplier, result)
    }
    
    // ============================================
    // GENERAL TESTS
    // ============================================
    
    @Test
    fun `distractors never include correct answer`() {
        val exercise = engine.generateExercise("arithmetic_add_10", 1)
        
        if (!exercise.distractors.isNullOrEmpty()) {
            assertFalse(
                "Distractors should not contain correct answer",
                exercise.distractors.contains(exercise.correctAnswer)
            )
        }
    }
    
    @Test
    fun `distractors have no duplicates`() {
        val exercise = engine.generateExercise("arithmetic_add_10", 1)
        
        if (!exercise.distractors.isNullOrEmpty()) {
            val unique = exercise.distractors.toSet()
            assertEquals(
                "Distractors should have no duplicates",
                exercise.distractors.size,
                unique.size
            )
        }
    }
    
    @Test
    fun `exercise difficulty is respected`() {
        val testDifficulty = 3
        val exercise = engine.generateExercise("arithmetic_add_20", testDifficulty)
        
        assertEquals(testDifficulty, exercise.difficulty)
    }
    
    @Test
    fun `all free skills can generate exercises`() {
        val freeSkills = ContentRepository.getFreeConfigs()
        
        freeSkills.forEach { config ->
            try {
                val exercise = engine.generateExercise(config.skillId, config.minDifficulty)
                assertNotNull("Should generate exercise for ${config.skillId}", exercise)
                assertEquals(config.skillId, exercise.skillId)
            } catch (e: Exception) {
                fail("Failed to generate exercise for ${config.skillId}: ${e.message}")
            }
        }
    }
    
    @Test
    fun `visual data matches exercise type`() {
        val exercise = engine.generateExercise("foundation_number_images_5", 1)
        
        if (exercise.type == ExerciseType.VISUAL_QUANTITY) {
            assertNotNull("Visual quantity should have count", exercise.visualData?.count)
        }
        
        if (exercise.type == ExerciseType.VISUAL_GROUPS) {
            assertTrue(
                "Visual groups should have groups or first/second",
                exercise.visualData?.groups != null || 
                (exercise.visualData?.firstNumber != null && exercise.visualData?.secondNumber != null)
            )
        }
    }
}