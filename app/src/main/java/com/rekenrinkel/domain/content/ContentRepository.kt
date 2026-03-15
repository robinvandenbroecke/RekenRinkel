package com.rekenrinkel.domain.content

import com.rekenrinkel.domain.model.ExerciseType
import com.rekenrinkel.domain.model.SkillCategory

/**
 * Typed didactische regels voor oefeninggeneratie.
 * Vervangt losse Map<String, String> configuratie.
 */
sealed class DidacticRule {
    // Basis regels
    data class ValueRange(val min: Int, val max: Int) : DidacticRule()
    data class AllowZero(val allowed: Boolean) : DidacticRule()
    data class AllowNegative(val allowed: Boolean) : DidacticRule()
    
    // Rekenregels
    data class MaxResult(val max: Int) : DidacticRule()
    data class MinResult(val min: Int) : DidacticRule()
    data class RequireBridgeThroughTen(val required: Boolean) : DidacticRule()
    data class RequireNoBridge(val required: Boolean) : DidacticRule()
    data class BothOperandsUnder(val limit: Int) : DidacticRule()
    data class ResultUnder(val limit: Int) : DidacticRule()
    
    // Patroonregels
    data class RequireEvenInput(val required: Boolean) : DidacticRule()
    data class ExactStep(val step: Int) : DidacticRule()
    data class SequenceLength(val length: Int) : DidacticRule()
    
    // Vergelijkingregels
    data class AllowEqualComparison(val allowed: Boolean) : DidacticRule()
    data class MinDifference(val difference: Int) : DidacticRule()
    
    // Groepjes/vermenigvuldiging
    data class MaxGroups(val max: Int) : DidacticRule()
    data class MaxPerGroup(val max: Int) : DidacticRule()
    data class ExactMultiplier(val multiplier: Int) : DidacticRule()
    
    // Splitsingen
    data class MinPartValue(val min: Int) : DidacticRule()
    data class MaxPartValue(val max: Int) : DidacticRule()
    
    // Visuele prompts
    data class RequiresVisualPrompt(val required: Boolean) : DidacticRule()
}

/**
 * Didactische regels container met typed toegang
 */
data class DidacticRules(
    val rules: Set<DidacticRule> = emptySet()
) {
    inline fun <reified T : DidacticRule> find(): T? {
        return rules.filterIsInstance<T>().firstOrNull()
    }
    
    fun has(rule: DidacticRule): Boolean = rule in rules
    
    val valueRange: DidacticRule.ValueRange?
        get() = find()
    
    val maxResult: Int?
        get() = find<DidacticRule.MaxResult>()?.max
    
    val minResult: Int?
        get() = find<DidacticRule.MinResult>()?.min
    
    val requireBridge: Boolean
        get() = find<DidacticRule.RequireBridgeThroughTen>()?.required ?: false
    
    val step: Int?
        get() = find<DidacticRule.ExactStep>()?.step
    
    val multiplier: Int?
        get() = find<DidacticRule.ExactMultiplier>()?.multiplier
    
    val requireEven: Boolean
        get() = find<DidacticRule.RequireEvenInput>()?.required ?: false
    
    companion object {
        fun build(block: Builder.() -> Unit): DidacticRules {
            return Builder().apply(block).build()
        }
    }
    
    class Builder {
        private val rules = mutableSetOf<DidacticRule>()
        
        fun range(min: Int, max: Int) = apply {
            rules.add(DidacticRule.ValueRange(min, max))
        }
        
        fun allowZero() = apply {
            rules.add(DidacticRule.AllowZero(true))
        }
        
        fun allowNegative() = apply {
            rules.add(DidacticRule.AllowNegative(true))
        }
        
        fun maxResult(max: Int) = apply {
            rules.add(DidacticRule.MaxResult(max))
        }
        
        fun minResult(min: Int) = apply {
            rules.add(DidacticRule.MinResult(min))
        }
        
        fun requireBridgeThroughTen() = apply {
            rules.add(DidacticRule.RequireBridgeThroughTen(true))
        }
        
        fun requireNoBridge() = apply {
            rules.add(DidacticRule.RequireNoBridge(true))
        }
        
        fun bothOperandsUnder(limit: Int) = apply {
            rules.add(DidacticRule.BothOperandsUnder(limit))
        }
        
        fun resultUnder(limit: Int) = apply {
            rules.add(DidacticRule.ResultUnder(limit))
        }
        
        fun requireEven() = apply {
            rules.add(DidacticRule.RequireEvenInput(true))
        }
        
        fun exactStep(step: Int) = apply {
            rules.add(DidacticRule.ExactStep(step))
        }
        
        fun sequenceLength(length: Int) = apply {
            rules.add(DidacticRule.SequenceLength(length))
        }
        
        fun allowEqual() = apply {
            rules.add(DidacticRule.AllowEqualComparison(true))
        }
        
        fun minDifference(diff: Int) = apply {
            rules.add(DidacticRule.MinDifference(diff))
        }
        
        fun maxGroups(max: Int) = apply {
            rules.add(DidacticRule.MaxGroups(max))
        }
        
        fun maxPerGroup(max: Int) = apply {
            rules.add(DidacticRule.MaxPerGroup(max))
        }
        
        fun exactMultiplier(multiplier: Int) = apply {
            rules.add(DidacticRule.ExactMultiplier(multiplier))
        }
        
        fun minPart(min: Int) = apply {
            rules.add(DidacticRule.MinPartValue(min))
        }
        
        fun maxPart(max: Int) = apply {
            rules.add(DidacticRule.MaxPartValue(max))
        }
        
        fun requireVisualPrompt() = apply {
            rules.add(DidacticRule.RequiresVisualPrompt(true))
        }
        
        fun build(): DidacticRules = DidacticRules(rules)
    }
}

/**
 * Didactische configuratie voor een skill.
 * Gebruikt typed DidacticRules in plaats van Map<String, String>.
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
    val rules: DidacticRules,
    val hintStrategy: HintStrategy = HintStrategy.NONE
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
 * Content repository met alle skill configuraties (met typed rules)
 */
object ContentRepository {
    
    private val configs = listOf(
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
            rules = DidacticRules.build {
                range(1, 5)
                requireVisualPrompt()
            },
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
            rules = DidacticRules.build {
                range(3, 10)
                minPart(1)
                maxPart(9)
            },
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
            rules = DidacticRules.build {
                range(11, 20)
                minPart(1)
                maxPart(19)
            },
            hintStrategy = HintStrategy.STEP_BY_STEP
        ),
        
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
            rules = DidacticRules.build {
                range(1, 9)
                maxResult(10)
                minResult(2)
                requireNoBridge()
            },
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
            rules = DidacticRules.build {
                range(1, 10)
                minResult(1)
                requireNoBridge()
            },
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
            rules = DidacticRules.build {
                range(1, 19)
                maxResult(20)
                minResult(11)
                requireNoBridge()
            },
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
            rules = DidacticRules.build {
                range(11, 20)
                minResult(1)
                requireNoBridge()
            },
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
            rules = DidacticRules.build {
                range(2, 9)
                maxResult(18)
                minResult(11)
                requireBridgeThroughTen()
                bothOperandsUnder(10)
            },
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
            rules = DidacticRules.build {
                range(11, 18)
                minResult(1)
                resultUnder(10)
                requireBridgeThroughTen()
            },
            hintStrategy = HintStrategy.STEP_BY_STEP
        ),
        
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
            rules = DidacticRules.build {
                range(1, 10)
            },
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
            rules = DidacticRules.build {
                range(2, 20)
                requireEven()
            },
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
            rules = DidacticRules.build {
                range(2, 20)
                exactStep(2)
                sequenceLength(4)
            },
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
            rules = DidacticRules.build {
                range(5, 50)
                exactStep(5)
                sequenceLength(4)
            },
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
            rules = DidacticRules.build {
                range(10, 100)
                exactStep(10)
                sequenceLength(4)
            },
            hintStrategy = HintStrategy.COUNTING_AID
        ),
        
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
            rules = DidacticRules.build {
                range(1, 100)
                minDifference(5)
                allowEqual()
            },
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
            rules = DidacticRules.build {
                range(10, 99)
                allowZero()
            },
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
            rules = DidacticRules.build {
                range(2, 5)
                maxGroups(5)
                maxPerGroup(5)
            },
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
            rules = DidacticRules.build {
                range(1, 10)
                exactMultiplier(2)
            },
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
            rules = DidacticRules.build {
                range(1, 10)
                exactMultiplier(5)
            },
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
            rules = DidacticRules.build {
                range(1, 10)
                exactMultiplier(10)
            },
            hintStrategy = HintStrategy.VISUAL
        )
    )
    
    fun getConfig(skillId: String): SkillContentConfig? {
        return configs.find { it.skillId == skillId }
    }
    
    fun getAllConfigs(): List<SkillContentConfig> = configs
    
    fun getFreeConfigs(): List<SkillContentConfig> = configs.filter { !it.isPremium }
    
    fun getPremiumConfigs(): List<SkillContentConfig> = configs.filter { it.isPremium }
    
    fun canUnlockSkill(skillId: String, masteredSkills: Set<String>): Boolean {
        val config = getConfig(skillId) ?: return false
        return config.prerequisites.all { it in masteredSkills }
    }
    
    fun getLearningPath(): List<List<String>> {
        return listOf(
            listOf("foundation_number_images_5"),
            listOf("foundation_splits_10", "arithmetic_add_10"),
            listOf("arithmetic_sub_10", "patterns_doubles", "patterns_halves"),
            listOf("foundation_splits_20", "arithmetic_add_20", "arithmetic_sub_20"),
            listOf("arithmetic_bridge_add", "arithmetic_bridge_sub"),
            listOf("patterns_count_2", "patterns_count_5", "patterns_count_10"),
            listOf("advanced_compare_100", "advanced_place_value"),
            listOf("advanced_groups", "advanced_table_2", "advanced_table_5", "advanced_table_10")
        )
    }
}