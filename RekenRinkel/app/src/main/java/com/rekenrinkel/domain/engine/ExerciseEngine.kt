package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.model.*
import java.util.*
import kotlin.random.Random

/**
 * Engine voor het genereren van oefeningen
 */
class ExerciseEngine {
    
    private val random = Random(System.currentTimeMillis())
    
    /**
     * Genereer een oefening voor een specifieke skill
     */
    fun generateExercise(skillId: String, difficulty: Int): Exercise {
        return when (skillId) {
            // FOUNDATION
            "foundation_number_images_5" -> generateNumberImages(difficulty)
            "foundation_splits_10" -> generateSplits(10, difficulty)
            "foundation_splits_20" -> generateSplits(20, difficulty)
            
            // ARITHMETIC
            "arithmetic_add_10" -> generateAddition(10, difficulty, useBridge = false)
            "arithmetic_sub_10" -> generateSubtraction(10, difficulty, useBridge = false)
            "arithmetic_add_20" -> generateAddition(20, difficulty, useBridge = false)
            "arithmetic_sub_20" -> generateSubtraction(20, difficulty, useBridge = false)
            "arithmetic_bridge_add" -> generateAddition(20, difficulty, useBridge = true)
            "arithmetic_bridge_sub" -> generateSubtraction(20, difficulty, useBridge = true)
            
            // PATTERNS
            "patterns_doubles" -> generateDoubles(difficulty)
            "patterns_halves" -> generateHalves(difficulty)
            "patterns_count_2" -> generateSkipCounting(2, difficulty)
            "patterns_count_5" -> generateSkipCounting(5, difficulty)
            "patterns_count_10" -> generateSkipCounting(10, difficulty)
            
            // ADVANCED
            "advanced_compare_100" -> generateComparison(difficulty)
            "advanced_place_value" -> generatePlaceValue(difficulty)
            "advanced_groups" -> generateGroups(difficulty)
            "advanced_table_2" -> generateTable(2, difficulty)
            "advanced_table_5" -> generateTable(5, difficulty)
            "advanced_table_10" -> generateTable(10, difficulty)
            
            else -> generateSimpleAddition(difficulty)
        }
    }
    
    /**
     * Getalbeelden tot 5 - VISUEEL
     */
    private fun generateNumberImages(difficulty: Int): Exercise {
        val count = random.nextInt(1, 6)
        
        return Exercise(
            skillId = "foundation_number_images_5",
            type = ExerciseType.VISUAL_QUANTITY,
            difficulty = difficulty,
            question = "Hoeveel stippen zie je?",
            visualData = VisualData(type = VisualType.DOTS, count = count),
            correctAnswer = count.toString(),
            distractors = generateDistractors(count, 1, 5)
        )
    }
    
    /**
     * Splitsingen
     */
    private fun generateSplits(max: Int, difficulty: Int): Exercise {
        val total = random.nextInt(3, max + 1)
        val part1 = random.nextInt(1, total)
        val part2 = total - part1
        
        return Exercise(
            skillId = if (max == 10) "foundation_splits_10" else "foundation_splits_20",
            type = ExerciseType.VISUAL_GROUPS,
            difficulty = difficulty,
            question = "$total = ? + ?",
            visualData = VisualData(type = VisualType.GROUPS, groups = listOf(part1, part2)),
            correctAnswer = "$part1 en $part2",
            distractors = generateSplitDistractors(total, part1, part2)
        )
    }
    
    /**
     * Optellen - met of zonder brug
     */
    private fun generateAddition(max: Int, difficulty: Int, useBridge: Boolean): Exercise {
        var a: Int
        var b: Int
        
        if (useBridge) {
            // Brug over 10: som > 10 en a < 10, b < 10
            do {
                a = random.nextInt(2, 10)
                b = random.nextInt(2, 10)
            } while (a + b <= 10 || a + b > max || a >= 10 || b >= 10)
        } else {
            // Geen brug
            do {
                a = random.nextInt(1, max)
                b = random.nextInt(1, max - a + 1)
            } while (useBridge && (a % 10 + b % 10 >= 10))
        }
        
        val correct = a + b
        val skillId = when {
            useBridge -> "arithmetic_bridge_add"
            max == 10 -> "arithmetic_add_10"
            else -> "arithmetic_add_20"
        }
        
        return Exercise(
            skillId = skillId,
            type = if (difficulty <= 2) ExerciseType.VISUAL_GROUPS else ExerciseType.TYPED_NUMERIC,
            difficulty = difficulty,
            question = "$a + $b = ?",
            visualData = if (difficulty <= 2) VisualData(
                type = VisualType.GROUPS,
                firstNumber = a,
                secondNumber = b
            ) else null,
            correctAnswer = correct.toString(),
            distractors = generateDistractors(correct, 0, max)
        )
    }
    
    /**
     * Aftrekken - met of zonder brug
     */
    private fun generateSubtraction(max: Int, difficulty: Int, useBridge: Boolean): Exercise {
        var a: Int
        var b: Int
        
        if (useBridge) {
            // Brug over 10: a > 10, b < 10, resultaat < 10
            do {
                a = random.nextInt(11, max + 1)
                b = random.nextInt(2, 10)
            } while (a - b >= 10 || a % 10 >= b)
        } else {
            // Geen brug
            a = random.nextInt(2, max + 1)
            b = random.nextInt(1, a)
        }
        
        val correct = a - b
        val skillId = when {
            useBridge -> "arithmetic_bridge_sub"
            max == 10 -> "arithmetic_sub_10"
            else -> "arithmetic_sub_20"
        }
        
        return Exercise(
            skillId = skillId,
            type = ExerciseType.TYPED_NUMERIC,
            difficulty = difficulty,
            question = "$a - $b = ?",
            visualData = if (difficulty <= 2) VisualData(
                type = VisualType.GROUPS,
                firstNumber = a,
                secondNumber = b
            ) else null,
            correctAnswer = correct.toString(),
            distractors = generateDistractors(correct, 0, max)
        )
    }
    
    /**
     * Dubbelen
     */
    private fun generateDoubles(difficulty: Int): Exercise {
        val n = random.nextInt(1, 11)
        val correct = n * 2
        
        return Exercise(
            skillId = "patterns_doubles",
            type = ExerciseType.VISUAL_GROUPS,
            difficulty = difficulty,
            question = "$n + $n = ?",
            visualData = VisualData(type = VisualType.GROUPS, groups = listOf(n, n)),
            correctAnswer = correct.toString(),
            distractors = generateDistractors(correct, 2, 20)
        )
    }
    
    /**
     * Helften
     */
    private fun generateHalves(difficulty: Int): Exercise {
        val n = random.nextInt(2, 21, 2) // Alleen even getallen
        val correct = n / 2
        
        return Exercise(
            skillId = "patterns_halves",
            type = ExerciseType.VISUAL_GROUPS,
            difficulty = difficulty,
            question = "De helft van $n = ?",
            visualData = VisualData(type = VisualType.GROUPS, groups = listOf(correct, correct)),
            correctAnswer = correct.toString(),
            distractors = generateDistractors(correct, 1, 10)
        )
    }
    
    /**
     * Skip counting
     */
    private fun generateSkipCounting(step: Int, difficulty: Int): Exercise {
        val start = step * random.nextInt(1, 5)
        val sequence = listOf(start, start + step, start + 2 * step, start + 3 * step)
        val missingIndex = random.nextInt(1, 4)
        val correct = sequence[missingIndex]
        
        val skillId = when (step) {
            2 -> "patterns_count_2"
            5 -> "patterns_count_5"
            else -> "patterns_count_10"
        }
        
        val displaySeq = sequence.mapIndexed { i, v -> if (i == missingIndex) "?" else v.toString() }
        
        return Exercise(
            skillId = skillId,
            type = ExerciseType.SIMPLE_SEQUENCE,
            difficulty = difficulty,
            question = "Wat komt hier? ${displaySeq.joinToString(", ")}",
            correctAnswer = correct.toString(),
            distractors = generateDistractors(correct, start, start + 4 * step)
        )
    }
    
    /**
     * Vergelijken
     */
    private fun generateComparison(difficulty: Int): Exercise {
        val a = random.nextInt(1, 101)
        val b = random.nextInt(1, 101)
        
        val correct = when {
            a > b -> ">"
            a < b -> "<"
            else -> "="
        }
        
        return Exercise(
            skillId = "advanced_compare_100",
            type = ExerciseType.COMPARE_NUMBERS,
            difficulty = difficulty,
            question = "$a ? $b",
            visualData = VisualData(type = VisualType.COMPARISON, firstNumber = a, secondNumber = b),
            options = listOf("<", "=", ">"),
            correctAnswer = correct
        )
    }
    
    /**
     * Tientallen en eenheden
     */
    private fun generatePlaceValue(difficulty: Int): Exercise {
        val tens = random.nextInt(1, 10)
        val ones = random.nextInt(0, 10)
        val number = tens * 10 + ones
        
        return Exercise(
            skillId = "advanced_place_value",
            type = ExerciseType.MISSING_NUMBER,
            difficulty = difficulty,
            question = "$number = ? tientallen en ? eenheden",
            correctAnswer = "$tens en $ones",
            distractors = generatePlaceValueDistractors(tens, ones)
        )
    }
    
    /**
     * Groepjes (vermenigvuldiging als groepjes)
     */
    private fun generateGroups(difficulty: Int): Exercise {
        val groups = random.nextInt(2, 6)
        val perGroup = random.nextInt(2, 6)
        val total = groups * perGroup
        
        return Exercise(
            skillId = "advanced_groups",
            type = ExerciseType.VISUAL_GROUPS,
            difficulty = difficulty,
            question = "$groups groepjes van $perGroup = ?",
            visualData = VisualData(type = VisualType.GROUPS, groups = List(groups) { perGroup }),
            correctAnswer = total.toString(),
            distractors = generateDistractors(total, 2, 25)
        )
    }
    
    /**
     * Tafels
     */
    private fun generateTable(multiplier: Int, difficulty: Int): Exercise {
        val n = random.nextInt(1, 11)
        val correct = n * multiplier
        
        val skillId = when (multiplier) {
            2 -> "advanced_table_2"
            5 -> "advanced_table_5"
            else -> "advanced_table_10"
        }
        
        return Exercise(
            skillId = skillId,
            type = ExerciseType.VISUAL_GROUPS,
            difficulty = difficulty,
            question = "$n × $multiplier = ?",
            visualData = VisualData(type = VisualType.GROUPS, groups = List(n) { multiplier }),
            correctAnswer = correct.toString(),
            distractors = generateDistractors(correct, multiplier, multiplier * 10)
        )
    }
    
    // Helper functies
    
    private fun generateSimpleAddition(difficulty: Int): Exercise {
        return generateAddition(10, difficulty, useBridge = false)
    }
    
    private fun generateDistractors(correct: Int, min: Int, max: Int): List<String> {
        val distractors = mutableSetOf<String>()
        
        // Off-by-one errors
        if (correct > min) distractors.add((correct - 1).toString())
        if (correct < max) distractors.add((correct + 1).toString())
        
        // Off-by-two errors
        if (correct > min + 1) distractors.add((correct - 2).toString())
        if (correct < max - 1) distractors.add((correct + 2).toString())
        
        // Random values within range
        while (distractors.size < 3) {
            val d = random.nextInt(min, max + 1)
            if (d != correct) distractors.add(d.toString())
        }
        
        return distractors.take(3)
    }
    
    private fun generateSplitDistractors(total: Int, correct1: Int, correct2: Int): List<String> {
        val distractors = mutableSetOf<String>()
        
        // Wrong splits
        while (distractors.size < 3) {
            val p1 = random.nextInt(1, total)
            val p2 = total - p1
            if (p1 != correct1 && p1 != correct2) {
                distractors.add("$p1 en $p2")
            }
        }
        
        return distractors.take(3).toList()
    }
    
    private fun generatePlaceValueDistractors(tens: Int, ones: Int): List<String> {
        return listOf(
            "$ones en $tens",
            "${tens + 1} en $ones",
            "$tens en ${ones + 1}"
        )
    }
    
    private fun Random.nextInt(from: Int, until: Int, step: Int): Int {
        val range = (until - from) / step
        return from + nextInt(range) * step
    }
}