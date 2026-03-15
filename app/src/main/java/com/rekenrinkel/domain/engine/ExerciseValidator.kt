package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.model.Exercise
import com.rekenrinkel.domain.model.ExerciseType

/**
 * Validator voor oefeningantwoorden.
 * Zorgt voor consistente validatie over alle oefentypes heen.
 */
class ExerciseValidator {
    
    /**
     * Valideer een antwoord voor een oefening.
     * Returned true als het antwoord correct is, false otherwise.
     */
    fun validate(exercise: Exercise, givenAnswer: String): Boolean {
        val normalizedGiven = normalizeInput(givenAnswer)
        val normalizedCorrect = normalizeInput(exercise.correctAnswer)
        
        return when (exercise.type) {
            ExerciseType.MULTIPLE_CHOICE -> 
                normalizedGiven == normalizedCorrect
            
            ExerciseType.TYPED_NUMERIC -> 
                validateNumeric(normalizedGiven, normalizedCorrect)
            
            ExerciseType.MISSING_NUMBER -> 
                validateMissingNumber(normalizedGiven, normalizedCorrect)
            
            ExerciseType.VISUAL_QUANTITY -> 
                validateVisualQuantity(normalizedGiven, normalizedCorrect)
            
            ExerciseType.VISUAL_GROUPS -> 
                validateVisualGroups(normalizedGiven, normalizedCorrect)
            
            ExerciseType.SIMPLE_SEQUENCE -> 
                validateNumeric(normalizedGiven, normalizedCorrect)
            
            ExerciseType.COMPARE_NUMBERS -> 
                normalizedGiven == normalizedCorrect
            
            ExerciseType.NUMBER_LINE_CLICK -> 
                validateNumeric(normalizedGiven, normalizedCorrect)
        }
    }
    
    /**
     * Valideer numeriek antwoord
     */
    private fun validateNumeric(given: String, correct: String): Boolean {
        // Probeer als integer
        val givenInt = given.toIntOrNull()
        val correctInt = correct.toIntOrNull()
        
        if (givenInt != null && correctInt != null) {
            return givenInt == correctInt
        }
        
        // Fallback: string vergelijking
        return given == correct
    }
    
    /**
     * Valideer visual quantity - moet exacte count zijn
     */
    private fun validateVisualQuantity(given: String, correct: String): Boolean {
        val givenInt = given.toIntOrNull()
        val correctInt = correct.toIntOrNull()
        
        return givenInt != null && correctInt != null && givenInt == correctInt
    }
    
    /**
     * Valideer visual groups - kan "a en b" of alleen getal (totaal) zijn
     */
    private fun validateVisualGroups(given: String, correct: String): Boolean {
        // Exacte match
        if (given == correct) return true

        val normalizedGiven = normalizeInput(given)
        val normalizedCorrect = normalizeInput(correct)

        if (normalizedGiven == normalizedCorrect) return true

        // Check of beide splits zijn met dezelfde getallen (bijv. "2 en 3" == "3 en 2")
        val givenNumbers = extractNumbers(normalizedGiven).sorted()
        val correctNumbers = extractNumbers(normalizedCorrect).sorted()
        if (givenNumbers.isNotEmpty() && givenNumbers == correctNumbers) return true

        // Check of correct antwoord een split is (bijv. "2 en 3")
        // en gebruiker heeft het totaal ingevoerd (bijv. "5")
        val correctTotal = calculateTotal(normalizedCorrect)
        val givenInt = normalizedGiven.toIntOrNull() ?: extractNumber(normalizedGiven)

        return givenInt != null && correctTotal != null && givenInt == correctTotal
    }

    /**
     * Bereken het totaal van een gesplitst antwoord (bijv. "2 en 3" -> 5)
     */
    private fun calculateTotal(normalizedInput: String): Int? {
        // Split op spaties (normalizeInput vervangt " en " door " ")
        val parts = normalizedInput.split(" ").filter { it.isNotEmpty() }
        val numbers = parts.mapNotNull { it.toIntOrNull() }

        return if (numbers.isNotEmpty()) numbers.sum() else null
    }
    
    /**
     * Valideer missing number - exacte match of numerieke equivalent
     */
    private fun validateMissingNumber(given: String, correct: String): Boolean {
        // Exacte match
        if (given == correct) return true
        
        // Numerieke match (voor "5" vs "5 en 0" bijv.)
        val givenInt = given.toIntOrNull()
        val correctInt = correct.toIntOrNull()
        
        if (givenInt != null && correctInt != null) {
            return givenInt == correctInt
        }
        
        // Check voor "X en Y" format
        return normalizeInput(given) == normalizeInput(correct)
    }
    
    /**
     * Normaliseer input voor vergelijking
     */
    private fun normalizeInput(input: String): String {
        return input
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ") // Multi-spaces naar single space
            .replace(" en ", " ")
            .replace(",", "")
            .replace(".", "")
            .trim()
    }
    
    /**
     * Extract een enkel getal uit een string
     */
    private fun extractNumber(input: String): Int? {
        // Zoek het eerste getal in de string
        val regex = Regex("\\d+")
        val match = regex.find(input)
        return match?.value?.toIntOrNull()
    }

    /**
     * Extract alle getallen uit een string
     */
    private fun extractNumbers(input: String): List<Int> {
        val regex = Regex("\\d+")
        return regex.findAll(input).map { it.value.toIntOrNull() }.filterNotNull().toList()
    }
    
    /**
     * Formatteer een correct antwoord voor weergave in feedback
     */
    fun formatCorrectAnswer(exercise: Exercise): String {
        return when (exercise.type) {
            ExerciseType.COMPARE_NUMBERS -> {
                when (exercise.correctAnswer) {
                    ">" -> "groter dan (>"
                    "<" -> "kleiner dan (<)"
                    "=" -> "gelijk aan (=)"
                    else -> exercise.correctAnswer
                }
            }
            else -> exercise.correctAnswer
        }
    }
    
    /**
     * Check of een antwoord leeg is of alleen whitespace
     */
    fun isEmptyAnswer(answer: String): Boolean {
        return answer.trim().isEmpty()
    }
    
    /**
     * Check of een antwoord lijkt op een poging (niet leeg en niet alleen speciale chars)
     */
    fun isValidAttempt(answer: String): Boolean {
        val trimmed = answer.trim()
        if (trimmed.isEmpty()) return false
        
        // Moet minstens een letter of cijfer bevatten
        return trimmed.any { it.isLetterOrDigit() }
    }
}