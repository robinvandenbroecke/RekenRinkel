package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.model.ExerciseType
import com.rekenrinkel.domain.model.SkillDefinitions
import org.junit.Test
import org.junit.Assert.*

class ExerciseEngineTest {
    
    private val engine = ExerciseEngine()
    
    @Test
    fun `generateExercise creates valid number images`() {
        val exercise = engine.generateExercise("foundation_number_images_5", 1)
        
        assertEquals("foundation_number_images_5", exercise.skillId)
        assertEquals(ExerciseType.VISUAL_QUANTITY, exercise.type)
        assertNotNull(exercise.visualData?.count)
        assertTrue(exercise.visualData?.count in 1..5)
    }
    
    @Test
    fun `generateExercise creates valid addition`() {
        val exercise = engine.generateExercise("arithmetic_add_10", 1)
        
        assertEquals("arithmetic_add_10", exercise.skillId)
        val correct = exercise.correctAnswer.toInt()
        assertTrue(correct in 2..10)
    }
    
    @Test
    fun `generateExercise creates valid subtraction`() {
        val exercise = engine.generateExercise("arithmetic_sub_10", 1)
        
        assertEquals("arithmetic_sub_10", exercise.skillId)
        val correct = exercise.correctAnswer.toInt()
        assertTrue(correct in 0..9)
    }
    
    @Test
    fun `bridge over 10 exercises have correct sum`() {
        val exercise = engine.generateExercise("arithmetic_bridge_add", 3)
        
        // Extract numbers from question (format: "a + b = ?")
        val match = Regex("""(\d+) \+ (\d+)""").find(exercise.question)
        assertNotNull(match)
        
        val a = match!!.groupValues[1].toInt()
        val b = match.groupValues[2].toInt()
        val sum = exercise.correctAnswer.toInt()
        
        assertEquals(a + b, sum)
        assertTrue(a < 10 && b < 10) // Both under 10
        assertTrue(sum > 10) // Sum over 10
    }
    
    @Test
    fun `halves exercise uses even numbers`() {
        val exercise = engine.generateExercise("patterns_halves", 1)
        
        // Extract number from question (format: "De helft van n = ?")
        val match = Regex("""De helft van (\d+)""").find(exercise.question)
        assertNotNull(match)
        
        val n = match!!.groupValues[1].toInt()
        assertTrue(n % 2 == 0) // Even number
        assertTrue(n in 2..20)
    }
    
    @Test
    fun `skip counting has valid sequence`() {
        val exercise = engine.generateExercise("patterns_count_2", 2)
        
        // Should have correct answer as integer
        val correct = exercise.correctAnswer.toIntOrNull()
        assertNotNull(correct)
        
        // Should be even for count by 2
        assertTrue(correct!! % 2 == 0)
    }
    
    @Test
    fun `comparison exercise has valid options`() {
        val exercise = engine.generateExercise("advanced_compare_100", 3)
        
        assertEquals(ExerciseType.COMPARE_NUMBERS, exercise.type)
        assertNotNull(exercise.options)
        assertTrue(exercise.options!!.contains("<"))
        assertTrue(exercise.options.contains(">"))
        assertTrue(exercise.options.contains("="))
    }
    
    @Test
    fun `groups exercise has correct total`() {
        val exercise = engine.generateExercise("advanced_groups", 3)
        
        // Extract from question (format: "n groepjes van m = ?")
        val match = Regex("""(\d+) groepjes van (\d+)""").find(exercise.question)
        assertNotNull(match)
        
        val groups = match!!.groupValues[1].toInt()
        val perGroup = match.groupValues[2].toInt()
        val total = exercise.correctAnswer.toInt()
        
        assertEquals(groups * perGroup, total)
    }
    
    @Test
    fun `table exercises have correct product`() {
        val exercise = engine.generateExercise("advanced_table_5", 3)
        
        // Extract from question (format: "n × 5 = ?")
        val match = Regex("""(\d+) × (\d+)""").find(exercise.question)
        assertNotNull(match)
        
        val n = match!!.groupValues[1].toInt()
        val table = match.groupValues[2].toInt()
        val product = exercise.correctAnswer.toInt()
        
        assertEquals(n * table, product)
    }
    
    @Test
    fun `distractors are not duplicates`() {
        val exercise = engine.generateExercise("arithmetic_add_10", 1)
        
        if (!exercise.distractors.isNullOrEmpty()) {
            val allAnswers = exercise.distractors + exercise.correctAnswer
            assertEquals(allAnswers.size, allAnswers.toSet().size)
        }
    }
    
    @Test
    fun `distractors do not include correct answer twice`() {
        val exercise = engine.generateExercise("arithmetic_add_10", 1)
        
        if (!exercise.distractors.isNullOrEmpty()) {
            assertFalse(exercise.distractors.contains(exercise.correctAnswer))
        }
    }
}