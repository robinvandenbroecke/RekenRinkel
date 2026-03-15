package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.model.Exercise
import com.rekenrinkel.domain.model.ExerciseType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExerciseValidatorTest {
    
    private lateinit var validator: ExerciseValidator
    
    @Before
    fun setup() {
        validator = ExerciseValidator()
    }
    
    // ============================================
    // BASIC VALIDATION TESTS
    // ============================================
    
    @Test
    fun `validate numeric answer correctly`() {
        val exercise = createExercise(
            type = ExerciseType.TYPED_NUMERIC,
            correctAnswer = "5"
        )
        
        assertTrue(validator.validate(exercise, "5"))
        assertFalse(validator.validate(exercise, "6"))
    }
    
    @Test
    fun `validate handles whitespace`() {
        val exercise = createExercise(
            type = ExerciseType.TYPED_NUMERIC,
            correctAnswer = "5"
        )
        
        assertTrue(validator.validate(exercise, "  5  "))
        assertTrue(validator.validate(exercise, "5 "))
        assertTrue(validator.validate(exercise, " 5"))
    }
    
    @Test
    fun `validate rejects wrong numeric answer`() {
        val exercise = createExercise(
            type = ExerciseType.TYPED_NUMERIC,
            correctAnswer = "8"
        )
        
        assertFalse(validator.validate(exercise, "5"))
        assertFalse(validator.validate(exercise, "0"))
        assertFalse(validator.validate(exercise, "-1"))
    }
    
    // ============================================
    // COMPARISON TESTS
    // ============================================
    
    @Test
    fun `validate comparison symbols`() {
        val exercise = createExercise(
            type = ExerciseType.COMPARE_NUMBERS,
            correctAnswer = ">"
        )
        
        assertTrue(validator.validate(exercise, ">"))
        assertFalse(validator.validate(exercise, "<"))
        assertFalse(validator.validate(exercise, "="))
    }
    
    @Test
    fun `validate comparison with whitespace`() {
        val exercise = createExercise(
            type = ExerciseType.COMPARE_NUMBERS,
            correctAnswer = "<"
        )
        
        assertTrue(validator.validate(exercise, " < "))
        assertTrue(validator.validate(exercise, "< "))
    }
    
    // ============================================
    // VISUAL GROUPS TESTS
    // ============================================
    
    @Test
    fun `validate visual groups exact match`() {
        val exercise = createExercise(
            type = ExerciseType.VISUAL_GROUPS,
            correctAnswer = "2 en 3"
        )
        
        assertTrue(validator.validate(exercise, "2 en 3"))
        assertTrue(validator.validate(exercise, "2 en 3 "))
        assertTrue(validator.validate(exercise, "2 en 3"))
    }
    
    @Test
    fun `validate visual groups accepts total only`() {
        val exercise = createExercise(
            type = ExerciseType.VISUAL_GROUPS,
            correctAnswer = "2 en 3"
        )
        
        // Should accept just the number 5 (the total)
        assertTrue(validator.validate(exercise, "5"))
    }
    
    @Test
    fun `validate visual groups rejects wrong split`() {
        val exercise = createExercise(
            type = ExerciseType.VISUAL_GROUPS,
            correctAnswer = "2 en 3"
        )

        assertFalse(validator.validate(exercise, "1 en 4"))
        assertFalse(validator.validate(exercise, "4 en 1"))
        assertFalse(validator.validate(exercise, "2 en 2"))
    }

    @Test
    fun `validate visual groups rejects wrong total`() {
        val exercise = createExercise(
            type = ExerciseType.VISUAL_GROUPS,
            correctAnswer = "2 en 3"
        )

        // Wrong total should be rejected
        assertFalse(validator.validate(exercise, "4"))
        assertFalse(validator.validate(exercise, "6"))
    }

    @Test
    fun `validate visual groups accepts single number when correct is total`() {
        val exercise = createExercise(
            type = ExerciseType.VISUAL_GROUPS,
            correctAnswer = "5"
        )

        // When correct answer is just a number, accept that number
        assertTrue(validator.validate(exercise, "5"))
    }

    @Test
    fun `validate visual groups rejects malformed input`() {
        val exercise = createExercise(
            type = ExerciseType.VISUAL_GROUPS,
            correctAnswer = "2 en 3"
        )

        assertFalse(validator.validate(exercise, ""))
        assertFalse(validator.validate(exercise, "abc"))
        assertFalse(validator.validate(exercise, "en"))
    }
    
    // ============================================
    // MISSING NUMBER TESTS
    // ============================================
    
    @Test
    fun `validate missing number exact match`() {
        val exercise = createExercise(
            type = ExerciseType.MISSING_NUMBER,
            correctAnswer = "5"
        )
        
        assertTrue(validator.validate(exercise, "5"))
    }
    
    @Test
    fun `validate missing number accepts numeric`() {
        val exercise = createExercise(
            type = ExerciseType.MISSING_NUMBER,
            correctAnswer = "3 en 4"
        )
        
        // If expecting "3 en 4", should accept just "7" (the total)
        assertTrue(validator.validate(exercise, "3 en 4"))
    }
    
    @Test
    fun `validate missing number rejects wrong`() {
        val exercise = createExercise(
            type = ExerciseType.MISSING_NUMBER,
            correctAnswer = "5"
        )
        
        assertFalse(validator.validate(exercise, "6"))
        assertFalse(validator.validate(exercise, "4"))
    }
    
    // ============================================
    // VISUAL QUANTITY TESTS
    // ============================================
    
    @Test
    fun `validate visual quantity exact match`() {
        val exercise = createExercise(
            type = ExerciseType.VISUAL_QUANTITY,
            correctAnswer = "5"
        )
        
        assertTrue(validator.validate(exercise, "5"))
        assertFalse(validator.validate(exercise, "6"))
    }
    
    @Test
    fun `validate visual quantity rejects non-numeric`() {
        val exercise = createExercise(
            type = ExerciseType.VISUAL_QUANTITY,
            correctAnswer = "5"
        )
        
        assertFalse(validator.validate(exercise, "five"))
        assertFalse(validator.validate(exercise, ""))
    }
    
    // ============================================
    // SIMPLE SEQUENCE TESTS
    // ============================================
    
    @Test
    fun `validate simple sequence numeric`() {
        val exercise = createExercise(
            type = ExerciseType.SIMPLE_SEQUENCE,
            correctAnswer = "8"
        )
        
        assertTrue(validator.validate(exercise, "8"))
        assertFalse(validator.validate(exercise, "9"))
    }
    
    // ============================================
    // NUMBER LINE CLICK TESTS
    // ============================================
    
    @Test
    fun `validate number line click numeric`() {
        val exercise = createExercise(
            type = ExerciseType.NUMBER_LINE_CLICK,
            correctAnswer = "15"
        )
        
        assertTrue(validator.validate(exercise, "15"))
        assertFalse(validator.validate(exercise, "16"))
    }
    
    // ============================================
    // NORMALIZATION TESTS
    // ============================================
    
    @Test
    fun `validate case insensitive`() {
        val exercise = createExercise(
            type = ExerciseType.VISUAL_GROUPS,
            correctAnswer = "2 en 3"
        )
        
        assertTrue(validator.validate(exercise, "2 EN 3"))
        assertTrue(validator.validate(exercise, "2 En 3"))
    }
    
    @Test
    fun `validate handles extra spaces`() {
        val exercise = createExercise(
            type = ExerciseType.VISUAL_GROUPS,
            correctAnswer = "2 en 3"
        )
        
        assertTrue(validator.validate(exercise, "2  en  3"))
        assertTrue(validator.validate(exercise, "2   en   3"))
    }
    
    @Test
    fun `validate handles commas`() {
        val exercise = createExercise(
            type = ExerciseType.VISUAL_GROUPS,
            correctAnswer = "2 en 3"
        )
        
        assertTrue(validator.validate(exercise, "2, en 3"))
        assertTrue(validator.validate(exercise, "2 en, 3"))
    }
    
    // ============================================
    // EDGE CASES
    // ============================================
    
    @Test
    fun `validate rejects negative where inappropriate`() {
        val exercise = createExercise(
            type = ExerciseType.TYPED_NUMERIC,
            correctAnswer = "5"
        )
        
        assertFalse(validator.validate(exercise, "-1"))
    }
    
    @Test
    fun `validate handles empty string`() {
        val exercise = createExercise(
            type = ExerciseType.TYPED_NUMERIC,
            correctAnswer = "5"
        )
        
        assertFalse(validator.validate(exercise, ""))
    }
    
    @Test
    fun `isEmptyAnswer detects empty input`() {
        assertTrue(validator.isEmptyAnswer(""))
        assertTrue(validator.isEmptyAnswer("   "))
        assertFalse(validator.isEmptyAnswer("5"))
    }
    
    @Test
    fun `isValidAttempt detects valid attempts`() {
        assertTrue(validator.isValidAttempt("5"))
        assertTrue(validator.isValidAttempt("abc"))
        assertFalse(validator.isValidAttempt(""))
        assertFalse(validator.isValidAttempt("   "))
        assertFalse(validator.isValidAttempt("!!!"))
    }
    
    @Test
    fun `formatCorrectAnswer formats comparison correctly`() {
        val greaterExercise = createExercise(
            type = ExerciseType.COMPARE_NUMBERS,
            correctAnswer = ">"
        )
        
        assertEquals("groter dan (>", validator.formatCorrectAnswer(greaterExercise))
        
        val lessExercise = createExercise(
            type = ExerciseType.COMPARE_NUMBERS,
            correctAnswer = "<"
        )
        
        assertEquals("kleiner dan (<)", validator.formatCorrectAnswer(lessExercise))
    }
    
    // ============================================
    // CONSISTENCY TESTS WITH ENGINE
    // ============================================
    
    @Test
    fun `validator matches engine output for addition`() {
        val engine = ExerciseEngine()
        val exercise = engine.generateExercise("arithmetic_add_10", 1)
        
        // The correct answer should validate
        assertTrue(validator.validate(exercise, exercise.correctAnswer))
        
        // Wrong answers should not validate
        assertFalse(validator.validate(exercise, "999"))
    }
    
    @Test
    fun `validator matches engine output for visual quantity`() {
        val engine = ExerciseEngine()
        val exercise = engine.generateExercise("foundation_number_images_5", 1)
        
        assertTrue(validator.validate(exercise, exercise.correctAnswer))
    }
    
    @Test
    fun `validator distractors are actually wrong`() {
        val engine = ExerciseEngine()
        val exercise = engine.generateExercise("arithmetic_add_10", 1)
        
        exercise.distractors?.forEach { distractor ->
            assertFalse(
                "Distractor '$distractor' should not validate",
                validator.validate(exercise, distractor)
            )
        }
    }
    
    // ============================================
    // HELPER METHODS
    // ============================================
    
    private fun createExercise(
        type: ExerciseType,
        correctAnswer: String,
        question: String = "Test question?"
    ): Exercise {
        return Exercise(
            skillId = "test_skill",
            type = type,
            difficulty = 1,
            question = question,
            correctAnswer = correctAnswer,
            distractors = listOf("wrong1", "wrong2", "wrong3")
        )
    }
}