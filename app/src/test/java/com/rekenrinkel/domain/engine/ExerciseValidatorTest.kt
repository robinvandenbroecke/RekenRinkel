package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.model.Exercise
import com.rekenrinkel.domain.model.ExerciseType
import org.junit.Test
import org.junit.Assert.*

class ExerciseValidatorTest {
    
    private val validator = ExerciseValidator()
    
    @Test
    fun `validate numeric answer correctly`() {
        val exercise = Exercise(
            skillId = "test",
            type = ExerciseType.TYPED_NUMERIC,
            difficulty = 1,
            question = "2 + 2 = ?",
            correctAnswer = "4"
        )
        
        assertTrue(validator.validate(exercise, "4"))
        assertFalse(validator.validate(exercise, "5"))
    }
    
    @Test
    fun `validate handles whitespace`() {
        val exercise = Exercise(
            skillId = "test",
            type = ExerciseType.TYPED_NUMERIC,
            difficulty = 1,
            question = "2 + 2 = ?",
            correctAnswer = "4"
        )
        
        assertTrue(validator.validate(exercise, "  4  "))
    }
    
    @Test
    fun `validate comparison symbols`() {
        val exercise = Exercise(
            skillId = "test",
            type = ExerciseType.COMPARE_NUMBERS,
            difficulty = 1,
            question = "5 ? 3",
            options = listOf("<", "=", ">"),
            correctAnswer = ">"
        )
        
        assertTrue(validator.validate(exercise, ">"))
        assertFalse(validator.validate(exercise, "<"))
        assertFalse(validator.validate(exercise, "="))
    }
    
    @Test
    fun `validate case insensitive`() {
        val exercise = Exercise(
            skillId = "test",
            type = ExerciseType.VISUAL_GROUPS,
            difficulty = 1,
            question = "5 = ?",
            correctAnswer = "2 en 3"
        )
        
        assertTrue(validator.validate(exercise, "2 en 3"))
        assertTrue(validator.validate(exercise, "2 EN 3"))
    }
    
    @Test
    fun `validate rejects negative answers where inappropriate`() {
        val exercise = Exercise(
            skillId = "test",
            type = ExerciseType.TYPED_NUMERIC,
            difficulty = 1,
            question = "5 - 3 = ?",
            correctAnswer = "2"
        )
        
        assertFalse(validator.validate(exercise, "-1"))
    }
}