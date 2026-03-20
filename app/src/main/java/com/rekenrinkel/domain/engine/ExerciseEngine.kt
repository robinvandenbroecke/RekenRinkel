package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.content.ContentRepository
import com.rekenrinkel.domain.content.DidacticRule
import com.rekenrinkel.domain.content.ErrorType
import com.rekenrinkel.domain.model.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Engine voor het genereren van didactisch consistente oefeningen.
 * Gebruikt ContentRepository voor configuratie en regels.
 */
class ExerciseEngine {
    
    private val random = Random(System.currentTimeMillis())
    
    /**
     * Genereer een worked example voor een skill (uitgewerkt voorbeeld met uitleg)
     */
    fun generateWorkedExample(skillId: String, difficulty: Int): Exercise {
        val baseExercise = generateExercise(skillId, difficulty)
        return baseExercise.copy(
            type = ExerciseType.WORKED_EXAMPLE,
            hint = generateWorkedExampleHint(baseExercise),
            isScaffolded = true
        )
    }

    /**
     * Genereer een guided exercise (begeleide oefening met tussenstappen)
     */
    fun generateGuidedExercise(skillId: String, difficulty: Int): Exercise {
        val baseExercise = generateExercise(skillId, difficulty)
        return baseExercise.copy(
            type = ExerciseType.GUIDED_PRACTICE,
            hint = generateGuidedHint(baseExercise),
            isScaffolded = true
        )
    }

    private fun generateWorkedExampleHint(exercise: Exercise): String {
        val hintText = exercise.hint ?: "Let op de stappen"
        return when (exercise.type) {
            ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC -> {
                "Laten we dit samen bekijken:\n" +
                "${exercise.question}\n" +
                "Het antwoord is ${exercise.correctAnswer}.\n" +
                "Let op: $hintText"
            }
            else -> "Dit is een voorbeeld. Het antwoord is: ${exercise.correctAnswer}"
        }
    }

    private fun generateGuidedHint(exercise: Exercise): String {
        val hintText = exercise.hint ?: "Probeer het stap voor stap"
        return when (exercise.type) {
            ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC -> {
                "Probeer zelf eerst:\n" +
                "${exercise.question}\n" +
                "Tip: $hintText\n" +
                "(Je krijgt hulp als je er niet uit komt)"
            }
            else -> hintText
        }
    }

    // PATCH 8: Recent gegenereerde oefeningen bijhouden om duplicaten te voorkomen
    private val recentExercises = mutableMapOf<String, MutableSet<String>>()
    private val maxRecentMemory = 5  // Onthoud laatste 5 oefeningen per skill

    /**
     * PATCH 8: Check of een oefening recent is gegenereerd (duplicaat)
     */
    private fun isRecentDuplicate(skillId: String, question: String): Boolean {
        val recent = recentExercises.getOrPut(skillId) { mutableSetOf() }
        return recent.contains(question.hashCode().toString())
    }

    /**
     * PATCH 8: Markeer een oefening als recent gegenereerd
     */
    private fun markAsRecent(skillId: String, question: String) {
        val recent = recentExercises.getOrPut(skillId) { mutableSetOf() }
        recent.add(question.hashCode().toString())
        // Houd alleen de laatste maxRecentMemory bij
        if (recent.size > maxRecentMemory) {
            recent.remove(recent.first())
        }
    }

    /**
     * Genereer een oefening voor een specifieke skill met het juiste difficulty level.
     * Deze methode gebruikt de content configuratie om didactisch correcte oefeningen te genereren.
     * PATCH 8: Voorkomt directe duplicaten van identieke items.
     */
    fun generateExercise(skillId: String, difficulty: Int): Exercise {
        val config = ContentRepository.getConfig(skillId)
            ?: return generateFallbackExercise(skillId, difficulty)
        
        // Clamp difficulty binnen de toegestane grenzen
        val clampedDifficulty = difficulty.coerceIn(config.minDifficulty, config.maxDifficulty)
        
        return when (skillId) {
            // FOUNDATION
            "foundation_subitize_5" -> generateNumberImages(config, clampedDifficulty)
            "foundation_number_images_5" -> generateNumberImages(config, clampedDifficulty)
            "foundation_counting" -> generateNumberImages(config, clampedDifficulty)
            "foundation_number_bonds_5" -> generateSplits(config, clampedDifficulty, 5)
            "foundation_number_bonds_10" -> generateSplits(config, clampedDifficulty, 10)
            "foundation_number_bonds_20" -> generateSplits(config, clampedDifficulty, 20)
            "foundation_splits_10" -> generateSplits(config, clampedDifficulty, 10)
            "foundation_splits_20" -> generateSplits(config, clampedDifficulty, 20)
            
            // ARITHMETIC - Aggregatie skills (gebruiken abstracte logica)
            "arithmetic_add_10" -> generateAddition(config, clampedDifficulty, maxSum = 10, useBridge = false)
            "arithmetic_sub_10" -> generateSubtraction(config, clampedDifficulty, max = 10, useBridge = false)
            "arithmetic_add_20" -> generateAddition(config, clampedDifficulty, maxSum = 20, useBridge = false)
            "arithmetic_sub_20" -> generateSubtraction(config, clampedDifficulty, max = 20, useBridge = false)
            "arithmetic_bridge_add" -> generateAddition(config, clampedDifficulty, maxSum = 18, useBridge = true)
            "arithmetic_bridge_sub" -> generateSubtraction(config, clampedDifficulty, max = 18, useBridge = true)
            
            // ARITHMETIC - CPA varianten (gebruiken zelfde logica als aggregatie)
            "arithmetic_add_10_concrete" -> generateAddition(config, clampedDifficulty, maxSum = 10, useBridge = false)
            "arithmetic_add_10_pictorial" -> generateAddition(config, clampedDifficulty, maxSum = 10, useBridge = false)
            "arithmetic_add_10_abstract" -> generateAddition(config, clampedDifficulty, maxSum = 10, useBridge = false)
            "arithmetic_sub_10_concrete" -> generateSubtraction(config, clampedDifficulty, max = 10, useBridge = false)
            "arithmetic_sub_10_pictorial" -> generateSubtraction(config, clampedDifficulty, max = 10, useBridge = false)
            "arithmetic_sub_10_abstract" -> generateSubtraction(config, clampedDifficulty, max = 10, useBridge = false)
            
            // PATTERNS
            "patterns_doubles" -> generateDoubles(config, clampedDifficulty)
            "patterns_halves" -> generateHalves(config, clampedDifficulty)
            "patterns_count_2" -> generateSkipCounting(config, clampedDifficulty, step = 2)
            "patterns_count_5" -> generateSkipCounting(config, clampedDifficulty, step = 5)
            "patterns_count_10" -> generateSkipCounting(config, clampedDifficulty, step = 10)
            
            // ADVANCED
            "advanced_compare_100" -> generateComparison(config, clampedDifficulty)
            "advanced_place_value" -> generatePlaceValue(config, clampedDifficulty)
            "advanced_groups" -> generateGroups(config, clampedDifficulty)
            "advanced_table_2" -> generateTable(config, clampedDifficulty, multiplier = 2)
            "advanced_table_5" -> generateTable(config, clampedDifficulty, multiplier = 5)
            "advanced_table_10" -> generateTable(config, clampedDifficulty, multiplier = 10)
            
            else -> generateFallbackExercise(skillId, clampedDifficulty)
        }.let { exercise ->
            // PATCH 8: Voorkom directe duplicaten
            var attempts = 0
            var result = exercise
            while (isRecentDuplicate(skillId, result.question) && attempts < 3) {
                // Genereer opnieuw als het een duplicaat is (max 3 pogingen)
                result = when (skillId) {
                    "foundation_subitize_5", "foundation_number_images_5", "foundation_counting" -> generateNumberImages(config!!, clampedDifficulty)
                    "foundation_number_bonds_5", "foundation_splits_10" -> generateSplits(config!!, clampedDifficulty, 10)
                    "foundation_number_bonds_10" -> generateSplits(config!!, clampedDifficulty, 10)
                    "foundation_number_bonds_20", "foundation_splits_20" -> generateSplits(config!!, clampedDifficulty, 20)
                    // Aggregatie skills en CPA varianten gebruiken zelfde logica
                    "arithmetic_add_10", "arithmetic_add_10_concrete", "arithmetic_add_10_pictorial", "arithmetic_add_10_abstract" -> generateAddition(config!!, clampedDifficulty, maxSum = 10, useBridge = false)
                    "arithmetic_sub_10", "arithmetic_sub_10_concrete", "arithmetic_sub_10_pictorial", "arithmetic_sub_10_abstract" -> generateSubtraction(config!!, clampedDifficulty, max = 10, useBridge = false)
                    "arithmetic_add_20" -> generateAddition(config!!, clampedDifficulty, maxSum = 20, useBridge = false)
                    "arithmetic_sub_20" -> generateSubtraction(config!!, clampedDifficulty, max = 20, useBridge = false)
                    "arithmetic_bridge_add" -> generateAddition(config!!, clampedDifficulty, maxSum = 18, useBridge = true)
                    "arithmetic_bridge_sub" -> generateSubtraction(config!!, clampedDifficulty, max = 18, useBridge = true)
                    "patterns_doubles" -> generateDoubles(config!!, clampedDifficulty)
                    "patterns_halves" -> generateHalves(config!!, clampedDifficulty)
                    "patterns_count_2" -> generateSkipCounting(config!!, clampedDifficulty, step = 2)
                    "patterns_count_5" -> generateSkipCounting(config!!, clampedDifficulty, step = 5)
                    "patterns_count_10" -> generateSkipCounting(config!!, clampedDifficulty, step = 10)
                    "advanced_compare_100" -> generateComparison(config!!, clampedDifficulty)
                    "advanced_place_value" -> generatePlaceValue(config!!, clampedDifficulty)
                    "advanced_groups" -> generateGroups(config!!, clampedDifficulty)
                    "advanced_table_2" -> generateTable(config!!, clampedDifficulty, multiplier = 2)
                    "advanced_table_5" -> generateTable(config!!, clampedDifficulty, multiplier = 5)
                    "advanced_table_10" -> generateTable(config!!, clampedDifficulty, multiplier = 10)
                    else -> generateFallbackExercise(skillId, clampedDifficulty)
                }
                attempts++
            }
            markAsRecent(skillId, result.question)
            result
        }
    }
    
    // ============================================
    // FOUNDATION SKILLS
    // ============================================
    
    /**
     * Getalbeelden tot 5 - Altijd visueel met correcte dot representatie
     */
    private fun generateNumberImages(config: com.rekenrinkel.domain.content.SkillContentConfig, difficulty: Int): Exercise {
        // Genereer 1-5, geen 0
        val count = random.nextInt(1, 6)
        
        // Variatie in dot patronen afhankelijk van difficulty
        val visualType = when (difficulty) {
            1 -> VisualType.DOTS // Eenvoudige stippen
            2 -> if (random.nextBoolean()) VisualType.DOTS else VisualType.BLOCKS
            else -> VisualType.BLOCKS // Blokken voor hogere difficulty
        }
        
        return Exercise(
            skillId = config.skillId,
            type = ExerciseType.VISUAL_QUANTITY,
            difficulty = difficulty,
            question = "Hoeveel ${if (visualType == VisualType.DOTS) "stippen" else "blokjes"} zie je?",
            visualData = VisualData(type = visualType, count = count),
            correctAnswer = count.toString(),
            distractors = generateNumericDistractors(count, 1, 5),
            hint = "Tel rustig: 1, 2, 3..."
        )
    }
    
    /**
     * Splitsingen - Met visuele ondersteuning en betekenisvolle combinaties
     */
    private fun generateSplits(
        config: com.rekenrinkel.domain.content.SkillContentConfig,
        difficulty: Int,
        max: Int
    ): Exercise {
        // Genereer een getal binnen de grenzen
        val valueRange = config.rules.valueRange
        val minVal = valueRange?.min ?: 1
        val maxVal = valueRange?.max ?: max
        val total = random.nextInt(
            max(3, minVal),
            min(max + 1, maxVal + 1)
        )
        
        // Genereer een betekenisvolle splitsing
        val minPart = config.rules.find<DidacticRule.MinPartValue>()?.min ?: 1
        val maxPart = config.rules.find<DidacticRule.MaxPartValue>()?.max ?: (total - 1)
        
        val part1 = random.nextInt(minPart, min(maxPart + 1, total))
        val part2 = total - part1
        
        // Bij hogere difficulty: missing number ipv twee getallen
        val useMissingNumber = difficulty >= 3 && random.nextBoolean()
        
        return if (useMissingNumber) {
            val missingFirst = random.nextBoolean()
            val question = if (missingFirst) "? + $part2 = $total" else "$part1 + ? = $total"
            val correct = if (missingFirst) part1 else part2
            
            Exercise(
                skillId = config.skillId,
                type = ExerciseType.MISSING_NUMBER,
                difficulty = difficulty,
                question = question,
                correctAnswer = correct.toString(),
                distractors = generateNumericDistractors(correct, 1, total - 1),
                hint = "$part1 + $part2 = $total"
            )
        } else {
            Exercise(
                skillId = config.skillId,
                type = ExerciseType.VISUAL_GROUPS,
                difficulty = difficulty,
                question = "$total = ? + ?",
                visualData = VisualData(type = VisualType.GROUPS, groups = listOf(part1, part2)),
                correctAnswer = "$part1 en $part2",
                distractors = generateSplitDistractors(total, part1, part2),
                hint = "Kijk naar de groepjes"
            )
        }
    }
    
    // ============================================
    // ARITHMETIC SKILLS
    // ============================================
    
    /**
     * Optellen - Didactisch correct, geen triviale gevallen
     */
    private fun generateAddition(
        config: com.rekenrinkel.domain.content.SkillContentConfig,
        difficulty: Int,
        maxSum: Int,
        useBridge: Boolean
    ): Exercise {
        var a: Int
        var b: Int

        if (useBridge) {
            // Brug over 10: beide < 10, som > 10, som <= maxSum
            do {
                a = random.nextInt(2, 10) // Geen 0 of 1
                b = random.nextInt(2, 10)
            } while (a + b <= 10 || a + b > maxSum || !requiresBridgeOver10(a, b))
        } else {
            // Geen brug: som binnen limiet, beide termen >= 1
            do {
                a = random.nextInt(1, maxSum)
                b = random.nextInt(1, maxSum - a + 1)
            } while (a + b > maxSum || requiresBridgeOver10(a, b) || a == 0 || b == 0)
        }
        
        val correct = a + b
        val useVisual = difficulty <= 2
        
        return Exercise(
            skillId = config.skillId,
            type = if (useVisual) ExerciseType.VISUAL_GROUPS else ExerciseType.TYPED_NUMERIC,
            difficulty = difficulty,
            question = "$a + $b = ?",
            visualData = if (useVisual) VisualData(
                type = VisualType.GROUPS,
                groups = listOf(a, b)
            ) else null,
            correctAnswer = correct.toString(),
            distractors = generateNumericDistractors(correct, max(1, correct - 5), correct + 5),
            hint = if (useBridge) "Eerst naar 10: $a + ${10 - a} = 10, dan nog ${b - (10 - a)}" else null
        )
    }
    
    /**
     * Aftrekken - Geen negatieve resultaten, geen triviale gevallen
     */
    private fun generateSubtraction(
        config: com.rekenrinkel.domain.content.SkillContentConfig,
        difficulty: Int,
        max: Int,
        useBridge: Boolean
    ): Exercise {
        var a: Int
        var b: Int

        if (useBridge) {
            // Brug over 10: a > 10, b < 10, resultaat < 10, en echt brug nodig
            do {
                a = random.nextInt(11, min(max + 1, 19))
                b = random.nextInt(2, 10)
            } while (a - b >= 10 || a % 10 >= b || a == 0 || b == 0)
        } else {
            // Geen brug: a > b, beide >= 1
            a = random.nextInt(2, max + 1)
            b = random.nextInt(1, a) // Zorg dat b < a, dus resultaat > 0
        }
        
        val correct = a - b
        val useVisual = difficulty <= 2
        
        return Exercise(
            skillId = config.skillId,
            type = if (useVisual) ExerciseType.VISUAL_GROUPS else ExerciseType.TYPED_NUMERIC,
            difficulty = difficulty,
            question = "$a - $b = ?",
            visualData = if (useVisual) VisualData(
                type = VisualType.GROUPS,
                firstNumber = a,
                secondNumber = b
            ) else null,
            correctAnswer = correct.toString(),
            distractors = generateNumericDistractors(correct, max(0, correct - 3), correct + 3),
            hint = if (useBridge) "Eerst terug naar 10: $a - ${a - 10} = 10, dan nog $b - ${a - 10}" else null
        )
    }
    
    // ============================================
    // PATTERN SKILLS
    // ============================================
    
    /**
     * Dubbelen - Altijd correcte dubbelen
     */
    private fun generateDoubles(config: com.rekenrinkel.domain.content.SkillContentConfig, difficulty: Int): Exercise {
        val maxVal = config.rules.valueRange?.max ?: 20
        val max = min(10, maxVal / 2)
        val n = random.nextInt(1, max + 1)
        val correct = n * 2
        
        return Exercise(
            skillId = config.skillId,
            type = if (difficulty <= 2) ExerciseType.VISUAL_GROUPS else ExerciseType.TYPED_NUMERIC,
            difficulty = difficulty,
            question = "$n + $n = ?",
            visualData = if (difficulty <= 2) VisualData(
                type = VisualType.GROUPS,
                groups = listOf(n, n)
            ) else null,
            correctAnswer = correct.toString(),
            distractors = generateNumericDistractors(correct, 2, 20),
            hint = "Dubbelen is twee keer hetzelfde getal"
        )
    }
    
    /**
     * Helften - Alleen op even getallen
     */
    private fun generateHalves(config: com.rekenrinkel.domain.content.SkillContentConfig, difficulty: Int): Exercise {
        // Alleen even getallen
        val maxVal = config.rules.valueRange?.max ?: 20
        val n = (random.nextInt(1, maxVal / 2 + 1) * 2).coerceAtLeast(2)
        val correct = n / 2
        
        return Exercise(
            skillId = config.skillId,
            type = if (difficulty <= 2) ExerciseType.VISUAL_GROUPS else ExerciseType.TYPED_NUMERIC,
            difficulty = difficulty,
            question = "De helft van $n = ?",
            visualData = if (difficulty <= 2) VisualData(
                type = VisualType.GROUPS,
                groups = listOf(correct, correct)
            ) else null,
            correctAnswer = correct.toString(),
            distractors = generateNumericDistractors(correct, 1, 10).filter { it.toIntOrNull() != n }, // Niet het originele getal
            hint = "Verdeel in twee gelijke stukken"
        )
    }
    
    /**
     * Skip counting - Logische reeksen
     */
    private fun generateSkipCounting(
        config: com.rekenrinkel.domain.content.SkillContentConfig,
        difficulty: Int,
        step: Int
    ): Exercise {
        val maxVal = config.rules.valueRange?.max ?: 100
        val maxStart = maxVal - (step * 4)
        val start = step * random.nextInt(1, maxStart / step + 1)
        
        val sequence = listOf(
            start,
            start + step,
            start + 2 * step,
            start + 3 * step
        )
        
        // Bij difficulty 1: laatste nummer ontbreekt
        // Bij hogere difficulty: midden nummer ontbreekt
        val missingIndex = when (difficulty) {
            1 -> 3
            2 -> random.nextInt(2, 4)
            else -> random.nextInt(1, 4)
        }
        
        val correct = sequence[missingIndex]
        val displaySeq = sequence.mapIndexed { i, v -> 
            if (i == missingIndex) "?" else v.toString() 
        }
        
        return Exercise(
            skillId = config.skillId,
            type = ExerciseType.SIMPLE_SEQUENCE,
            difficulty = difficulty,
            question = "Wat komt er in het lege vakje?\n${displaySeq.joinToString(", ")}",
            correctAnswer = correct.toString(),
            distractors = generateNumericDistractors(correct, start, sequence.last()),
            hint = "Tel steeds $step erbij"
        )
    }
    
    // ============================================
    // ADVANCED SKILLS
    // ============================================
    
    /**
     * Vergelijken - Duidelijke vraagstelling (VEILIG)
     * Gebruikt kandidaatlijst om ongeldige random ranges te voorkomen
     */
    private fun generateComparison(config: com.rekenrinkel.domain.content.SkillContentConfig, difficulty: Int): Exercise {
        // Bij hogere difficulty: getallen dichter bij elkaar
        val range = when (difficulty) {
            3 -> 10..100
            4 -> 20..100
            else -> 1..50
        }

        // Zorg voor betekenisvolle verschillen
        val minDiff = when (difficulty) {
            3 -> 15
            4 -> 10
            else -> 20
        }

        // Genereer eerst a, dan bouw een lijst van geldige b-kandidaten
        val a = random.nextInt(range.first, range.last + 1)

        // Bouw lijst van alle geldige b-waarden
        val candidates = mutableListOf<Int>()

        // b < a - minDiff (b is significant kleiner)
        val lowerMax = a - minDiff
        if (lowerMax >= range.first) {
            candidates.addAll((range.first..lowerMax).toList())
        }

        // b > a + minDiff (b is significant groter)
        val upperMin = a + minDiff
        if (upperMin <= range.last) {
            candidates.addAll((upperMin..range.last).toList())
        }

        // Fallback: als er geen kandidaten zijn, verruim de criteria
        val b = if (candidates.isNotEmpty()) {
            candidates.random(random)
        } else {
            // Geen significant verschil mogelijk: kies willekeurig uit hele range
            // maar zorg dat b != a
            var candidate = random.nextInt(range.first, range.last + 1)
            while (candidate == a && range.last > range.first) {
                candidate = random.nextInt(range.first, range.last + 1)
            }
            candidate
        }

        val correct = when {
            a > b -> ">"
            a < b -> "<"
            else -> "="
        }

        return Exercise(
            skillId = config.skillId,
            type = ExerciseType.COMPARE_NUMBERS,
            difficulty = difficulty,
            question = "Welk symbool hoort hier?\n$a __ $b",
            visualData = VisualData(type = VisualType.COMPARISON, firstNumber = a, secondNumber = b),
            options = listOf("<", "=", ">"),
            correctAnswer = correct,
            hint = "Kijk naar de tientallen eerst"
        )
    }
    
    /**
     * Tientallen en eenheden
     */
    private fun generatePlaceValue(config: com.rekenrinkel.domain.content.SkillContentConfig, difficulty: Int): Exercise {
        val tens = random.nextInt(1, 10)
        val ones = random.nextInt(0, 10)
        val number = tens * 10 + ones
        
        return Exercise(
            skillId = config.skillId,
            type = ExerciseType.MISSING_NUMBER,
            difficulty = difficulty,
            question = "$number = ? tientallen en ? eenheden",
            correctAnswer = "$tens en $ones",
            distractors = listOf(
                "$ones en $tens", // Omgedraaid
                "${tens + 1} en $ones", // Verkeerde tiental
                "$tens en ${(ones + 1) % 10}" // Verkeerde eenheid
            ),
            hint = "$tens × 10 + $ones = $number"
        )
    }
    
    /**
     * Groepjes - Als concrete vermenigvuldiging
     */
    private fun generateGroups(config: com.rekenrinkel.domain.content.SkillContentConfig, difficulty: Int): Exercise {
        val maxGroups = config.rules.find<DidacticRule.MaxGroups>()?.max ?: 5
        val maxPerGroup = config.rules.find<DidacticRule.MaxPerGroup>()?.max ?: 5
        
        val groups = random.nextInt(2, maxGroups + 1)
        val perGroup = random.nextInt(2, maxPerGroup + 1)
        val total = groups * perGroup
        
        return Exercise(
            skillId = config.skillId,
            type = ExerciseType.VISUAL_GROUPS,
            difficulty = difficulty,
            question = "$groups groepjes van $perGroup is hoeveel samen?",
            visualData = VisualData(type = VisualType.GROUPS, groups = List(groups) { perGroup }),
            correctAnswer = total.toString(),
            distractors = generateNumericDistractors(total, max(2, total - 5), total + 5),
            hint = "Tel alle groepjes bij elkaar op"
        )
    }
    
    /**
     * Tafels - Als groepjes + formeel
     */
    private fun generateTable(
        config: com.rekenrinkel.domain.content.SkillContentConfig,
        difficulty: Int,
        multiplier: Int
    ): Exercise {
        val n = random.nextInt(1, 11)
        val correct = n * multiplier
        
        // Bij lage difficulty: visueel als groepjes
        // Bij hogere: alleen formeel
        val useVisual = difficulty <= 3
        
        return Exercise(
            skillId = config.skillId,
            type = if (useVisual) ExerciseType.VISUAL_GROUPS else ExerciseType.TYPED_NUMERIC,
            difficulty = difficulty,
            question = if (useVisual) "$n × $multiplier = ?" else "$n keer $multiplier = ?",
            visualData = if (useVisual) VisualData(
                type = VisualType.GROUPS,
                groups = List(n) { multiplier }
            ) else null,
            correctAnswer = correct.toString(),
            distractors = generateNumericDistractors(correct, multiplier, multiplier * 10),
            hint = "$n groepjes van $multiplier"
        )
    }
    
    // ============================================
    // HELPER METHODS
    // ============================================
    
    /**
     * Check of optelling brug over 10 vereist
     */
    private fun requiresBridgeOver10(a: Int, b: Int): Boolean {
        return (a % 10) + (b % 10) >= 10
    }
    
    /**
     * Genereer numerieke distractors zonder duplicates
     */
    private fun generateNumericDistractors(correct: Int, min: Int, max: Int): List<String> {
        val distractors = mutableSetOf<String>()
        val safeMin = min.coerceAtLeast(0)
        val safeMax = max.coerceAtLeast(safeMin + 5)
        
        // Off-by-one errors (meest voorkomende fout)
        if (correct > safeMin) distractors.add((correct - 1).toString())
        if (correct < safeMax) distractors.add((correct + 1).toString())
        
        // Off-by-two errors
        if (correct > safeMin + 1) distractors.add((correct - 2).toString())
        if (correct < safeMax - 1) distractors.add((correct + 2).toString())
        
        // Random waarden binnen bereik
        var attempts = 0
        while (distractors.size < 3 && attempts < 20) {
            val d = random.nextInt(safeMin, safeMax + 1)
            if (d != correct) {
                distractors.add(d.toString())
            }
            attempts++
        }
        
        return distractors.take(3).toList()
    }
    
    /**
     * Genereer distractors voor splitsingen
     */
    private fun generateSplitDistractors(total: Int, correct1: Int, correct2: Int): List<String> {
        val distractors = mutableSetOf<String>()
        
        // Genereer verkeerde splitsingen
        var attempts = 0
        while (distractors.size < 3 && attempts < 20) {
            val p1 = random.nextInt(1, total)
            val p2 = total - p1
            
            // Geen correcte of symmetrische varianten
            if (p1 != correct1 && p1 != correct2 && p1 <= p2) {
                distractors.add("$p1 en $p2")
            }
            attempts++
        }
        
        return distractors.take(3).toList()
    }
    
    /**
     * Fallback voor onbekende skills
     */
    private fun generateFallbackExercise(skillId: String, difficulty: Int): Exercise {
        val a = random.nextInt(1, 10)
        val b = random.nextInt(1, 10)
        
        return Exercise(
            skillId = skillId,
            type = ExerciseType.TYPED_NUMERIC,
            difficulty = difficulty,
            question = "$a + $b = ?",
            correctAnswer = (a + b).toString(),
            distractors = generateNumericDistractors(a + b, 1, 20)
        )
    }
    
    // ============================================
    // ERROR TYPE DETECTION
    // ============================================
    
    /**
     * Detecteer het fouttype op basis van gegeven antwoord vs correct antwoord
     * Dit helpt bij gerichte remediëring
     */
    fun detectErrorType(
        skillId: String,
        givenAnswer: String,
        correctAnswer: String,
        exercise: Exercise
    ): com.rekenrinkel.domain.content.ErrorType {
        // Parse antwoorden naar getallen indien mogelijk
        val givenNum = givenAnswer.toIntOrNull()
        val correctNum = correctAnswer.toIntOrNull()
        
        // Specifieke checks per skill type
        return when {
            // Bridge 10 errors: check of het een tipfout is bij brug over 10
            skillId.contains("bridge") && givenNum != null && correctNum != null -> {
                val diff = kotlin.math.abs(givenNum - correctNum)
                when {
                    diff == 10 -> com.rekenrinkel.domain.content.ErrorType.BRIDGE_10_ERROR
                    diff == 1 -> com.rekenrinkel.domain.content.ErrorType.BOND_ERROR
                    else -> com.rekenrinkel.domain.content.ErrorType.CALCULATION_ERROR
                }
            }
            
            // Bond errors: off-by-one bij number bonds
            skillId.contains("bond") || skillId.contains("splits") -> {
                if (givenNum != null && correctNum != null && kotlin.math.abs(givenNum - correctNum) <= 2) {
                    com.rekenrinkel.domain.content.ErrorType.BOND_ERROR
                } else {
                    com.rekenrinkel.domain.content.ErrorType.CALCULATION_ERROR
                }
            }
            
            // Place value errors: antwoord is 10x te groot/klein of verkeerde tientallen
            skillId.contains("place_value") && givenNum != null && correctNum != null -> {
                when {
                    givenNum * 10 == correctNum || givenNum == correctNum * 10 -> 
                        com.rekenrinkel.domain.content.ErrorType.PLACE_VALUE_ERROR
                    kotlin.math.abs(givenNum - correctNum) % 10 == 0 -> 
                        com.rekenrinkel.domain.content.ErrorType.PLACE_VALUE_ERROR
                    else -> com.rekenrinkel.domain.content.ErrorType.CALCULATION_ERROR
                }
            }
            
            // Counting errors: vaak bij visual quantity
            skillId.contains("count") || skillId.contains("subitize") -> {
                com.rekenrinkel.domain.content.ErrorType.COUNTING_ERROR
            }
            
            // Grouping errors: bij vermenigvuldigen/arrays
            skillId.contains("groups") || skillId.contains("table") -> {
                if (givenNum != null && correctNum != null) {
                    val ratio = if (correctNum > 0) givenNum.toFloat() / correctNum else 0f
                    if (ratio in 0.4f..0.6f || ratio in 1.5f..2.5f) {
                        com.rekenrinkel.domain.content.ErrorType.GROUPING_ERROR
                    } else {
                        com.rekenrinkel.domain.content.ErrorType.CALCULATION_ERROR
                    }
                } else {
                    com.rekenrinkel.domain.content.ErrorType.CALCULATION_ERROR
                }
            }
            
            // Sequence errors: bij skip counting
            skillId.contains("count_") -> {
                com.rekenrinkel.domain.content.ErrorType.SEQUENCE_ERROR
            }
            
            // Default: algemene rekenfout
            else -> com.rekenrinkel.domain.content.ErrorType.CALCULATION_ERROR
        }
    }
}