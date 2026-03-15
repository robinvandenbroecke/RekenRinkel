package com.rekenrinkel.domain.content

import com.rekenrinkel.domain.model.ExerciseType
import com.rekenrinkel.domain.model.SkillCategory

/**
 * Didactische configuratie voor een skill.
 * Definieert regels voor het genereren van oefeningen.
 */
data class SkillContentConfig(
    val skillId: String,
    val name: String,
    val description: String,
    val category: SkillCategory,
    val isPremium: Boolean = false,
    val prerequisites: List<String> = emptyList(),
    val minDifficulty: Int = 1,
    val maxDifficulty: Int = 5,
    val allowedExerciseTypes: List<ExerciseType>,
    val generatorRules: GeneratorRules,
    val hintStrategy: HintStrategy = HintStrategy.NONE
)

/**
 * Regels voor het genereren van oefeningen per difficulty level
 */
data class GeneratorRules(
    val minValue: Int,
    val maxValue: Int,
    val allowZero: Boolean = false,
    val allowNegative: Boolean = false,
    val specificRules: Map<String, String> = emptyMap()
)

/**
 * Strategie voor het tonen van hints
 */
enum class HintStrategy {
    NONE,
    VISUAL,
    STEP_BY_STEP,
    COUNTING_AID
}

/**
 * Content repository met alle skill configuraties
 */
object ContentRepository {
    
    private val configs = listOf(
        // FOUNDATION
        SkillContentConfig(
            skillId = "foundation_number_images_5",
            name = "Getalbeelden tot 5",
            description = "Visueel herkennen van hoeveelheden tot 5",
            category = SkillCategory.FOUNDATION,
            isPremium = false,
            prerequisites = emptyList(),
            minDifficulty = 1,
            maxDifficulty = 3,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_QUANTITY),
            generatorRules = GeneratorRules(
                minValue = 1,
                maxValue = 5,
                allowZero = false,
                allowNegative = false
            ),
            hintStrategy = HintStrategy.VISUAL
        ),
        
        SkillContentConfig(
            skillId = "foundation_splits_10",
            name = "Splitsingen tot 10",
            description = "Getallen splitsen in twee delen tot 10",
            category = SkillCategory.FOUNDATION,
            isPremium = false,
            prerequisites = listOf("foundation_number_images_5"),
            minDifficulty = 1,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.MISSING_NUMBER),
            generatorRules = GeneratorRules(
                minValue = 3,
                maxValue = 10,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "minPart" to "1",
                    "maxPart" to "9"
                )
            ),
            hintStrategy = HintStrategy.VISUAL
        ),
        
        SkillContentConfig(
            skillId = "foundation_splits_20",
            name = "Splitsingen tot 20",
            description = "Getallen splitsen in twee delen tot 20",
            category = SkillCategory.FOUNDATION,
            isPremium = true,
            prerequisites = listOf("foundation_splits_10", "arithmetic_add_10"),
            minDifficulty = 2,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.MISSING_NUMBER, ExerciseType.TYPED_NUMERIC),
            generatorRules = GeneratorRules(
                minValue = 11,
                maxValue = 20,
                allowZero = false,
                allowNegative = false
            ),
            hintStrategy = HintStrategy.STEP_BY_STEP
        ),
        
        // ARITHMETIC
        SkillContentConfig(
            skillId = "arithmetic_add_10",
            name = "Optellen tot 10",
            description = "Optellen van getallen met resultaat tot 10",
            category = SkillCategory.ARITHMETIC,
            isPremium = false,
            prerequisites = listOf("foundation_number_images_5"),
            minDifficulty = 1,
            maxDifficulty = 3,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            generatorRules = GeneratorRules(
                minValue = 1,
                maxValue = 10,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "maxSum" to "10",
                    "allowBridge" to "false"
                )
            ),
            hintStrategy = HintStrategy.VISUAL
        ),
        
        SkillContentConfig(
            skillId = "arithmetic_sub_10",
            name = "Aftrekken tot 10",
            description = "Aftrekken van getallen tot 10",
            category = SkillCategory.ARITHMETIC,
            isPremium = false,
            prerequisites = listOf("foundation_number_images_5", "arithmetic_add_10"),
            minDifficulty = 1,
            maxDifficulty = 3,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            generatorRules = GeneratorRules(
                minValue = 1,
                maxValue = 10,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "minResult" to "1",
                    "allowBridge" to "false"
                )
            ),
            hintStrategy = HintStrategy.VISUAL
        ),
        
        SkillContentConfig(
            skillId = "arithmetic_add_20",
            name = "Optellen tot 20",
            description = "Optellen van getallen met resultaat tot 20",
            category = SkillCategory.ARITHMETIC,
            isPremium = true,
            prerequisites = listOf("arithmetic_add_10", "foundation_splits_10"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            generatorRules = GeneratorRules(
                minValue = 11,
                maxValue = 20,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "maxSum" to "20",
                    "allowBridge" to "false"
                )
            ),
            hintStrategy = HintStrategy.STEP_BY_STEP
        ),
        
        SkillContentConfig(
            skillId = "arithmetic_sub_20",
            name = "Aftrekken tot 20",
            description = "Aftrekken van getallen tot 20",
            category = SkillCategory.ARITHMETIC,
            isPremium = true,
            prerequisites = listOf("arithmetic_sub_10", "arithmetic_add_20"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            generatorRules = GeneratorRules(
                minValue = 11,
                maxValue = 20,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "minResult" to "1"
                )
            ),
            hintStrategy = HintStrategy.STEP_BY_STEP
        ),
        
        SkillContentConfig(
            skillId = "arithmetic_bridge_add",
            name = "Brug over 10 (optellen)",
            description = "Optellen met brug over 10",
            category = SkillCategory.ARITHMETIC,
            isPremium = true,
            prerequisites = listOf("arithmetic_add_20", "foundation_splits_10"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.MISSING_NUMBER),
            generatorRules = GeneratorRules(
                minValue = 2,
                maxValue = 9,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "requireBridge" to "true",
                    "maxSum" to "18",
                    "bothUnder10" to "true"
                )
            ),
            hintStrategy = HintStrategy.STEP_BY_STEP
        ),
        
        SkillContentConfig(
            skillId = "arithmetic_bridge_sub",
            name = "Brug over 10 (aftrekken)",
            description = "Aftrekken met brug over 10",
            category = SkillCategory.ARITHMETIC,
            isPremium = true,
            prerequisites = listOf("arithmetic_sub_20", "foundation_splits_10", "arithmetic_bridge_add"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.MISSING_NUMBER),
            generatorRules = GeneratorRules(
                minValue = 11,
                maxValue = 18,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "requireBridge" to "true",
                    "subtrahendUnder10" to "true",
                    "resultUnder10" to "true"
                )
            ),
            hintStrategy = HintStrategy.STEP_BY_STEP
        ),
        
        // PATTERNS
        SkillContentConfig(
            skillId = "patterns_doubles",
            name = "Dubbelen tot 20",
            description = "Dubbelen van getallen tot 20",
            category = SkillCategory.PATTERNS,
            isPremium = false,
            prerequisites = listOf("arithmetic_add_10"),
            minDifficulty = 1,
            maxDifficulty = 3,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            generatorRules = GeneratorRules(
                minValue = 1,
                maxValue = 10,
                allowZero = false,
                allowNegative = false
            ),
            hintStrategy = HintStrategy.VISUAL
        ),
        
        SkillContentConfig(
            skillId = "patterns_halves",
            name = "Helften tot 20",
            description = "Helften van even getallen tot 20",
            category = SkillCategory.PATTERNS,
            isPremium = false,
            prerequisites = listOf("patterns_doubles"),
            minDifficulty = 1,
            maxDifficulty = 3,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            generatorRules = GeneratorRules(
                minValue = 2,
                maxValue = 20,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "onlyEven" to "true"
                )
            ),
            hintStrategy = HintStrategy.VISUAL
        ),
        
        SkillContentConfig(
            skillId = "patterns_count_2",
            name = "Tellen per 2",
            description = "Tellen met sprongen van 2",
            category = SkillCategory.PATTERNS,
            isPremium = true,
            prerequisites = listOf("patterns_doubles", "foundation_splits_10"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.SIMPLE_SEQUENCE, ExerciseType.MISSING_NUMBER),
            generatorRules = GeneratorRules(
                minValue = 2,
                maxValue = 20,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "step" to "2",
                    "sequenceLength" to "4"
                )
            ),
            hintStrategy = HintStrategy.COUNTING_AID
        ),
        
        SkillContentConfig(
            skillId = "patterns_count_5",
            name = "Tellen per 5",
            description = "Tellen met sprongen van 5",
            category = SkillCategory.PATTERNS,
            isPremium = true,
            prerequisites = listOf("patterns_count_2"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.SIMPLE_SEQUENCE, ExerciseType.MISSING_NUMBER),
            generatorRules = GeneratorRules(
                minValue = 5,
                maxValue = 50,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "step" to "5",
                    "sequenceLength" to "4"
                )
            ),
            hintStrategy = HintStrategy.COUNTING_AID
        ),
        
        SkillContentConfig(
            skillId = "patterns_count_10",
            name = "Tellen per 10",
            description = "Tellen met sprongen van 10",
            category = SkillCategory.PATTERNS,
            isPremium = true,
            prerequisites = listOf("patterns_count_5"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.SIMPLE_SEQUENCE, ExerciseType.MISSING_NUMBER),
            generatorRules = GeneratorRules(
                minValue = 10,
                maxValue = 100,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "step" to "10",
                    "sequenceLength" to "4"
                )
            ),
            hintStrategy = HintStrategy.COUNTING_AID
        ),
        
        // ADVANCED
        SkillContentConfig(
            skillId = "advanced_compare_100",
            name = "Vergelijken tot 100",
            description = "Getallen vergelijken tot 100",
            category = SkillCategory.ADVANCED,
            isPremium = true,
            prerequisites = listOf("arithmetic_add_20", "patterns_count_10"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.COMPARE_NUMBERS),
            generatorRules = GeneratorRules(
                minValue = 1,
                maxValue = 100,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "allowEqual" to "true"
                )
            ),
            hintStrategy = HintStrategy.VISUAL
        ),
        
        SkillContentConfig(
            skillId = "advanced_place_value",
            name = "Tientallen en eenheden",
            description = "Tientallen en eenheden herkennen",
            category = SkillCategory.ADVANCED,
            isPremium = true,
            prerequisites = listOf("advanced_compare_100", "patterns_count_10"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.MISSING_NUMBER),
            generatorRules = GeneratorRules(
                minValue = 10,
                maxValue = 99,
                allowZero = true,
                allowNegative = false
            ),
            hintStrategy = HintStrategy.VISUAL
        ),
        
        SkillContentConfig(
            skillId = "advanced_groups",
            name = "Vermenigvuldigen als groepjes",
            description = "Groepjes tellen als introductie tot vermenigvuldigen",
            category = SkillCategory.ADVANCED,
            isPremium = true,
            prerequisites = listOf("patterns_doubles", "patterns_count_2"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            generatorRules = GeneratorRules(
                minValue = 2,
                maxValue = 5,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "maxGroups" to "5",
                    "maxPerGroup" to "5"
                )
            ),
            hintStrategy = HintStrategy.VISUAL
        ),
        
        SkillContentConfig(
            skillId = "advanced_table_2",
            name = "Tafel van 2",
            description = "De tafel van 2",
            category = SkillCategory.ADVANCED,
            isPremium = true,
            prerequisites = listOf("advanced_groups", "patterns_count_2"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            generatorRules = GeneratorRules(
                minValue = 1,
                maxValue = 10,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "multiplier" to "2"
                )
            ),
            hintStrategy = HintStrategy.VISUAL
        ),
        
        SkillContentConfig(
            skillId = "advanced_table_5",
            name = "Tafel van 5",
            description = "De tafel van 5",
            category = SkillCategory.ADVANCED,
            isPremium = true,
            prerequisites = listOf("advanced_groups", "patterns_count_5", "advanced_table_2"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            generatorRules = GeneratorRules(
                minValue = 1,
                maxValue = 10,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "multiplier" to "5"
                )
            ),
            hintStrategy = HintStrategy.VISUAL
        ),
        
        SkillContentConfig(
            skillId = "advanced_table_10",
            name = "Tafel van 10",
            description = "De tafel van 10",
            category = SkillCategory.ADVANCED,
            isPremium = true,
            prerequisites = listOf("advanced_groups", "patterns_count_10", "advanced_table_5"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            generatorRules = GeneratorRules(
                minValue = 1,
                maxValue = 10,
                allowZero = false,
                allowNegative = false,
                specificRules = mapOf(
                    "multiplier" to "10"
                )
            ),
            hintStrategy = HintStrategy.VISUAL
        )
    )
    
    /**
     * Haal configuratie op voor een skill
     */
    fun getConfig(skillId: String): SkillContentConfig? {
        return configs.find { it.skillId == skillId }
    }
    
    /**
     * Haal alle configuraties op
     */
    fun getAllConfigs(): List<SkillContentConfig> = configs
    
    /**
     * Haal gratis configuraties op
     */
    fun getFreeConfigs(): List<SkillContentConfig> = configs.filter { !it.isPremium }
    
    /**
     * Haal premium configuraties op
     */
    fun getPremiumConfigs(): List<SkillContentConfig> = configs.filter { it.isPremium }
    
    /**
     * Check of een skill unlocked kan worden op basis van prerequisites
     */
    fun canUnlockSkill(skillId: String, masteredSkills: Set<String>): Boolean {
        val config = getConfig(skillId) ?: return false
        return config.prerequisites.all { it in masteredSkills }
    }
    
    /**
     * Haal de leerlijn op (skills in logische volgorde)
     */
    fun getLearningPath(): List<List<String>> {
        return listOf(
            // Level 1: Foundation
            listOf("foundation_number_images_5"),
            // Level 2: Basic splits and add/sub to 10
            listOf("foundation_splits_10", "arithmetic_add_10"),
            // Level 3: Subtraction and doubles/halves
            listOf("arithmetic_sub_10", "patterns_doubles", "patterns_halves"),
            // Level 4: Splits to 20, add/sub to 20
            listOf("foundation_splits_20", "arithmetic_add_20", "arithmetic_sub_20"),
            // Level 5: Bridge over 10
            listOf("arithmetic_bridge_add", "arithmetic_bridge_sub"),
            // Level 6: Skip counting
            listOf("patterns_count_2", "patterns_count_5", "patterns_count_10"),
            // Level 7: Compare and place value
            listOf("advanced_compare_100", "advanced_place_value"),
            // Level 8: Groups and tables
            listOf("advanced_groups", "advanced_table_2", "advanced_table_5", "advanced_table_10")
        )
    }
}