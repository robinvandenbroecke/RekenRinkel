package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.model.Exercise
import com.rekenrinkel.domain.model.ExerciseType
import kotlin.math.abs

/**
 * Analyzes wrong answers and returns error-specific hints in Dutch.
 */
object ErrorAnalysis {

    /**
     * Analyze a wrong answer and return a targeted hint.
     * Returns null if no specific pattern is detected.
     */
    fun analyzeError(exercise: Exercise, givenAnswer: String): String? {
        val givenNum = givenAnswer.toIntOrNull() ?: return null
        val correctNum = exercise.correctAnswer.toIntOrNull() ?: return null
        val diff = givenNum - correctNum

        // Off by 1
        if (abs(diff) == 1) {
            return "Tel nog een keer, begin bij 1"
        }

        // Off by 10 (place value error)
        if (abs(diff) == 10) {
            return "Let op de tientallen en eenheden!"
        }

        // Reversed digits (e.g. 31 vs 13)
        if (correctNum >= 10 && givenNum >= 10) {
            val correctStr = correctNum.toString()
            val givenStr = givenNum.toString()
            if (correctStr.length == 2 && givenStr.length == 2 &&
                correctStr[0] == givenStr[1] && correctStr[1] == givenStr[0]
            ) {
                return "Kijk goed: welk cijfer staat vooraan?"
            }
        }

        // Bridge error: for addition crossing 10, off by 1 in the wrong direction
        val addMatch = Regex("""(\d+) \+ (\d+)""").find(exercise.question)
        if (addMatch != null) {
            val a = addMatch.groupValues[1].toInt()
            val b = addMatch.groupValues[2].toInt()
            val sum = a + b
            if (sum > 10 && a < 10 && b < 10 && givenNum == sum - 1) {
                return "Bijna! Vergeet de brug over 10 niet"
            }
        }

        // Adjacent table fact (multiplication)
        val mulMatch = Regex("""(\d+) [×x] (\d+)""").find(exercise.question)
            ?: Regex("""(\d+) keer (\d+)""").find(exercise.question)
        if (mulMatch != null) {
            val n = mulMatch.groupValues[1].toInt()
            val m = mulMatch.groupValues[2].toInt()
            // Check if answer matches an adjacent table fact
            if (givenNum == (n - 1) * m || givenNum == (n + 1) * m) {
                val wrongN = if (givenNum == (n - 1) * m) n - 1 else n + 1
                return "Dat is $wrongN × $m. We zoeken $n × $m"
            }
            if (givenNum == n * (m - 1) || givenNum == n * (m + 1)) {
                val wrongM = if (givenNum == n * (m - 1)) m - 1 else m + 1
                return "Dat is $n × $wrongM. We zoeken $n × $m"
            }
        }

        // Group question adjacent fact
        val groupMatch = Regex("""(\d+) groepjes van (\d+)""").find(exercise.question)
        if (groupMatch != null) {
            val groups = groupMatch.groupValues[1].toInt()
            val perGroup = groupMatch.groupValues[2].toInt()
            if (givenNum == (groups - 1) * perGroup || givenNum == (groups + 1) * perGroup) {
                return "Tel het aantal groepjes nog eens"
            }
        }

        return null
    }
}
