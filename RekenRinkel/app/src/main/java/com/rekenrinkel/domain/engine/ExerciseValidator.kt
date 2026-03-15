package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.model.Exercise
import com.rekenrinkel.domain.model.ExerciseType

/**
 * Validator voor oefeningantwoorden
 */
class ExerciseValidator {
    
    /**
     * Valideer een antwoord voor een oefening
     */
    fun validate(exercise: Exercise, givenAnswer: String): Boolean {
        return when (exercise.type) {
            ExerciseType.MULTIPLE_CHOICE -> validateMultipleChoice(exercise, givenAnswer)
            ExerciseType.TYPED_NUMERIC -> validateTypedNumeric(exercise, givenAnswer)
            ExerciseType.MISSING_NUMBER -> validateMissingNumber(exercise, givenAnswer)
            ExerciseType.VISUAL_QUANTITY -> validateVisualQuantity(exercise, givenAnswer)
            ExerciseType.VISUAL_GROUPS -> validateVisualGroups(exercise, givenAnswer)
            ExerciseType.SIMPLE_SEQUENCE -> validateSimpleSequence(exercise, givenAnswer)
            ExerciseType.COMPARE_NUMBERS -> validateCompareNumbers(exercise, givenAnswer)
            ExerciseType.NUMBER_LINE_CLICK -> validateNumberLineClick(exercise, givenAnswer)
        }
    }
    
    private fun validateMultipleChoice(exercise: Exercise, answer: String): Boolean {
        return normalize(answer) == normalize(exercise.correctAnswer)
    }
    
    private fun validateTypedNumeric(exercise: Exercise, answer: String): Boolean {
        val given = answer.trim().toIntOrNull()
        val correct = exercise.correctAnswer.toIntOrNull()
        return given == correct
    }
    
    private fun validateMissingNumber(exercise: Exercise, answer: String): Boolean {
        return normalize(answer) == normalize(exercise.correctAnswer)
    }
    
    private fun validateVisualQuantity(exercise: Exercise, answer: String): Boolean {
        val given = answer.trim().toIntOrNull()
        val correct = exercise.correctAnswer.toIntOrNull()
        return given == correct
    }
    
    private fun validateVisualGroups(exercise: Exercise, answer: String): Boolean {
        return normalize(answer) == normalize(exercise.correctAnswer)
    }
    
    private fun validateSimpleSequence(exercise: Exercise, answer: String): Boolean {
        val given = answer.trim().toIntOrNull()
        val correct = exercise.correctAnswer.toIntOrNull()
        return given == correct
    }
    
    private fun validateCompareNumbers(exercise: Exercise, answer: String): Boolean {
        return normalize(answer) == normalize(exercise.correctAnswer)
    }
    
    private fun validateNumberLineClick(exercise: Exercise, answer: String): Boolean {
        val given = answer.trim().toIntOrNull()
        val correct = exercise.correctAnswer.toIntOrNull()
        return given == correct
    }
    
    private fun normalize(input: String): String {
        return input.lowercase().replace(" ", "").replace("en", "").trim()
    }
    
    /**
     * Formatteer een correct antwoord voor weergave
     */
    fun formatCorrectAnswer(exercise: Exercise): String {
        return exercise.correctAnswer
    }
}