package com.rekenrinkel.domain.content

import com.rekenrinkel.domain.model.ExerciseType
import com.rekenrinkel.domain.model.SkillCategory

/**
 * CPA (Concrete-Pictorial-Abstract) fasen
 * Singapore Math didactiek
 */
enum class CpaPhase {
    CONCRETE,      // Fysieke objecten, manipulatieve materialen
    PICTORIAL,     // Visuele representaties, diagrammen
    ABSTRACT,      // Symbolische notatie, cijfers
    MIXED_TRANSFER // Gemengd, contextoefeningen
}

/**
 * Fouttypen voor gestuurde remediëring
 */
enum class ErrorType {
    COUNTING_ERROR,      // Fout in tellen
    SUBITIZING_ERROR,    // Fout in direct herkennen
    BOND_ERROR,          // Fout in number bonds
    PLACE_VALUE_ERROR,   // Fout in plaatswaarde
    COMPARE_ERROR,       // Fout in vergelijken
    BRIDGE_10_ERROR,     // Fout in brug over 10
    GROUPING_ERROR,      // Fout in groepjes
    SEQUENCE_ERROR,      // Fout in patronen/reeksen
    MISSING_PART_ERROR,  // Fout in missing number
    CALCULATION_ERROR    // Algemene rekenfout
}

/**
 * Representatie types voor verschillende denkniveaus
 */
enum class RepresentationType {
    DOTS,           // Stippen (meest concreet)
    BLOCKS,         // Blokjes
    NUMBER_LINE,    // Getallenlijn
    BOND_MODEL,     // Part-part-whole model
    GROUPS,         // Groepjes
    ARRAY,          // Rooster/array
    SYMBOLS,        // Symbolen/cijfers (meest abstract)
    CONTEXT         // Context/woordproblem
}

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
/**
 * Strategie voor het tonen van hints
 * MOET voor SkillContentConfig staan vanwege default parameter
 */
enum class HintStrategy {
    NONE,
    VISUAL,
    STEP_BY_STEP,
    COUNTING_AID,
    BOND_MODEL,
    NUMBER_LINE
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
    val cpaPhase: CpaPhase = CpaPhase.CONCRETE,
    val isPremium: Boolean = false,
    val prerequisites: List<String> = emptyList(),
    val minDifficulty: Int = 1,
    val maxDifficulty: Int = 5,
    val allowedExerciseTypes: List<ExerciseType>,
    val preferredRepresentations: List<RepresentationType> = emptyList(),
    val rules: DidacticRules,
    val hintStrategy: HintStrategy = HintStrategy.NONE,
    val commonErrorTypes: List<ErrorType> = emptyList(),
    val remediationSkill: String? = null
)

/**
 * PATCH 3: Curriculum mapping Nederland & Vlaanderen
 * 
 * === NEDERLAND - Kerndoelen Rekenen-Wiskunde PO ===
 * 
 * Getallenkennis (Number Sense):
 * - Subitizing tot 5: hoofdstroom 1F (direct herkennen)
 * - Tellen en kardinaliteit: hoofdstroom 1F
 * - Number bonds 5/10/20: hoofdstroom 1F/1S (splitsen)
 * - Plaatswaarde tientallen/eenheden: hoofdstroom 1S
 * 
 * Bewerkingen (Operations):
 * - Optellen/aftrekken tot 10: hoofdstroom 1F
 * - Optellen/aftrekken tot 20: hoofdstroom 1F (met brug over 10)
 * - Automatiseren tot 20: hoofdstroom 1S
 * - Tafels 2,5,10: hoofdstroom 1S
 * 
 * Vergelijken & Relaties:
 * - Meer/minder/evenveel: hoofdstroom 1F
 * - Getallenlijn vergelijken: hoofdstroom 1S
 * 
 * Metend rekenen & Meetkunde:
 * - Patroonherkenning (doubles/halves): hoofdstroom 1F
 * - Tellen per 2,5,10: hoofdstroom 1S
 * 
 * === VLAANDEREN - Minimumdoelen Wiskunde LO ===
 * 
 * Getalbegrip:
 * - Structuur van natuurlijke getallen tot 20: ontwikkelingsdoel
 * - Splitsingen tot 10/20: ontwikkelingsdoel
 * - Getalbeelden: ontwikkelingsdoel
 * 
 * Bewerkingen:
 * - Optellen/aftrekken tot 20: ontwikkelingsdoel
 * - Automatiseren: ontwikkelingsdoel
 * - Vermenigvuldigen als herhaald optellen: ontwikkelingsdoel
 * 
 * Toepassingen:
 * - Contextuele problemen tot 20: ontwikkelingsdoel
 * - Redeneren over patronen: ontwikkelingsdoel
 * 
 * === LEEFTIJDSINDELING ===
 * 5-6 jaar: FOUNDATION (NL groep 3 / Vlaanderen 1e leerjaar)
 * 6-8 jaar: EARLY_ARITHMETIC (NL groep 4 / Vlaanderen 2e leerjaar)  
 * 8-11 jaar: EXTENDED (NL groep 5-6 / Vlaanderen 3e-4e leerjaar)
 */

/**
 * Content repository met alle skill configuraties (met typed rules)
 */
object ContentRepository {

    // Leeftijd 5-6: Subitizing, telling, cardinaliteit, meer/minder
    private val age5_6Configs = listOf(
        // === SUBITIZING & COUNTING (Leeftijd 5-6) ===
        SkillContentConfig(
            skillId = "foundation_subitize_5",
            name = "Herkennen tot 5",
            description = "Direct herkennen van hoeveelheden zonder te tellen (subitizing)",
            category = SkillCategory.FOUNDATION,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = false,
            prerequisites = emptyList(),
            minDifficulty = 1,
            maxDifficulty = 3,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_QUANTITY),
            preferredRepresentations = listOf(RepresentationType.DOTS, RepresentationType.BLOCKS),
            rules = DidacticRules.build {
                range(1, 5)
                requireVisualPrompt()
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.SUBITIZING_ERROR, ErrorType.COUNTING_ERROR)
        ),

        SkillContentConfig(
            skillId = "foundation_counting",
            name = "Tellen",
            description = "Tellen en kardinaliteit begrijpen",
            category = SkillCategory.FOUNDATION,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = false,
            prerequisites = listOf("foundation_subitize_5"),
            minDifficulty = 1,
            maxDifficulty = 3,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_QUANTITY, ExerciseType.SIMPLE_SEQUENCE),
            preferredRepresentations = listOf(RepresentationType.DOTS, RepresentationType.BLOCKS),
            rules = DidacticRules.build {
                range(1, 10)
                requireVisualPrompt()
            },
            hintStrategy = HintStrategy.COUNTING_AID,
            commonErrorTypes = listOf(ErrorType.COUNTING_ERROR)
        ),

        SkillContentConfig(
            skillId = "foundation_more_less",
            name = "Meer, Minder, Evenveel",
            description = "Vergelijken van hoeveelheden",
            category = SkillCategory.FOUNDATION,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = false,
            prerequisites = listOf("foundation_counting"),
            minDifficulty = 1,
            maxDifficulty = 2,
            allowedExerciseTypes = listOf(ExerciseType.COMPARE_NUMBERS, ExerciseType.VISUAL_QUANTITY),
            preferredRepresentations = listOf(RepresentationType.DOTS, RepresentationType.BLOCKS),
            rules = DidacticRules.build {
                range(1, 10)
                requireVisualPrompt()
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.COMPARE_ERROR, ErrorType.COUNTING_ERROR)
        ),

        // === NUMBER BONDS (Leeftijd 5-6) ===
        SkillContentConfig(
            skillId = "foundation_number_bonds_5",
            name = "Number Bonds tot 5",
            description = "Leren welke getallen samen 5 maken",
            category = SkillCategory.FOUNDATION,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = false,
            prerequisites = listOf("foundation_counting"),
            minDifficulty = 1,
            maxDifficulty = 3,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.BOND_MODEL, RepresentationType.BLOCKS),
            rules = DidacticRules.build {
                range(2, 5)
                maxResult(5)
                requireVisualPrompt()
            },
            hintStrategy = HintStrategy.BOND_MODEL,
            commonErrorTypes = listOf(ErrorType.BOND_ERROR)
        ),

        SkillContentConfig(
            skillId = "foundation_number_bonds_10",
            name = "Number Bonds tot 10",
            description = "Leren welke getallen samen 10 maken",
            category = SkillCategory.FOUNDATION,
            cpaPhase = CpaPhase.PICTORIAL,
            isPremium = false,
            prerequisites = listOf("foundation_number_bonds_5"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.BOND_MODEL, RepresentationType.NUMBER_LINE),
            rules = DidacticRules.build {
                range(3, 10)
                maxResult(10)
                requireVisualPrompt()
            },
            hintStrategy = HintStrategy.BOND_MODEL,
            commonErrorTypes = listOf(ErrorType.BOND_ERROR),
            remediationSkill = "foundation_number_bonds_5"
        ),

        // === SHAPES & PATTERNS (Leeftijd 5-6) ===
        SkillContentConfig(
            skillId = "foundation_shapes",
            name = "Vormen Herkennen",
            description = "Basale geometrische vormen",
            category = SkillCategory.FOUNDATION,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = false,
            prerequisites = emptyList(),
            minDifficulty = 1,
            maxDifficulty = 2,
            allowedExerciseTypes = listOf(ExerciseType.MULTIPLE_CHOICE),
            preferredRepresentations = listOf(RepresentationType.DOTS),
            rules = DidacticRules.build {
                range(1, 5)
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.COUNTING_ERROR)
        ),

        SkillContentConfig(
            skillId = "foundation_patterns",
            name = "Patronen Vervolgen",
            description = "Eenvoudige visuele patronen",
            category = SkillCategory.FOUNDATION,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = false,
            prerequisites = listOf("foundation_counting"),
            minDifficulty = 1,
            maxDifficulty = 3,
            allowedExerciseTypes = listOf(ExerciseType.SIMPLE_SEQUENCE),
            preferredRepresentations = listOf(RepresentationType.DOTS),
            rules = DidacticRules.build {
                range(1, 5)
                sequenceLength(3)
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.SEQUENCE_ERROR)
        )
    )

    // Leeftijd 6-8: Rekenen tot 20, brug over 10, doubles/halves
    private val age6_8Configs = listOf(
        // === VERMENIGVULDIGING INTRO (Leeftijd 6-8) ===
        SkillContentConfig(
            skillId = "foundation_groups_intro",
            name = "Groepjes tellen",
            description = "Herkennen en tellen van groepjes (eerste stap naar vermenigvuldigen)",
            category = SkillCategory.FOUNDATION,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = false,
            prerequisites = emptyList(),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS),
            preferredRepresentations = listOf(RepresentationType.GROUPS, RepresentationType.ARRAY),
            rules = DidacticRules.build {
                maxGroups(3)
                maxPerGroup(5)
                range(2, 15)
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.GROUPING_ERROR, ErrorType.COUNTING_ERROR),
            remediationSkill = "foundation_counting"
        ),
        
        // === NUMBER BONDS 20 (Leeftijd 6-8) ===
        SkillContentConfig(
            skillId = "foundation_number_bonds_20",
            name = "Number Bonds tot 20",
            description = "Leren welke getallen samen 20 maken",
            category = SkillCategory.FOUNDATION,
            cpaPhase = CpaPhase.PICTORIAL,
            isPremium = false,
            prerequisites = listOf("foundation_number_bonds_10"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.BOND_MODEL, RepresentationType.NUMBER_LINE),
            rules = DidacticRules.build {
                range(5, 20)
                maxResult(20)
            },
            hintStrategy = HintStrategy.BOND_MODEL,
            commonErrorTypes = listOf(ErrorType.BOND_ERROR, ErrorType.PLACE_VALUE_ERROR),
            remediationSkill = "foundation_number_bonds_10"
        ),

        SkillContentConfig(
            skillId = "foundation_splits_10",
            name = "Splitsingen tot 10",
            description = "Getallen splitsen in twee delen tot 10",
            category = SkillCategory.FOUNDATION,
            cpaPhase = CpaPhase.PICTORIAL,
            isPremium = false,
            prerequisites = listOf("foundation_subitize_5"),
            minDifficulty = 1,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.BOND_MODEL, RepresentationType.BLOCKS),
            rules = DidacticRules.build {
                range(3, 10)
                minPart(1)
                maxPart(9)
            },
            hintStrategy = HintStrategy.BOND_MODEL,
            commonErrorTypes = listOf(ErrorType.BOND_ERROR, ErrorType.MISSING_PART_ERROR),
            remediationSkill = "foundation_number_bonds_5"
        ),

        SkillContentConfig(
            skillId = "foundation_splits_20",
            name = "Splitsingen tot 20",
            description = "Getallen splitsen in twee delen tot 20",
            category = SkillCategory.FOUNDATION,
            cpaPhase = CpaPhase.MIXED_TRANSFER,
            isPremium = true,
            prerequisites = listOf("foundation_splits_10", "arithmetic_add_10"),
            minDifficulty = 2,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.MISSING_NUMBER, ExerciseType.TYPED_NUMERIC),
            preferredRepresentations = listOf(RepresentationType.BOND_MODEL, RepresentationType.SYMBOLS),
            rules = DidacticRules.build {
                range(11, 20)
                minPart(1)
                maxPart(19)
            },
            hintStrategy = HintStrategy.BOND_MODEL,
            commonErrorTypes = listOf(ErrorType.BOND_ERROR, ErrorType.PLACE_VALUE_ERROR),
            remediationSkill = "foundation_splits_10"
        ),

        // === ARITHMETIC TOT 10 - MICROSKILLS MET CPA (Leeftijd 6-7) ===
        // Optellen CONCREET
        // Aggregatie skill voor optellen tot 10 (gebruikt abstracte versie)
        SkillContentConfig(
            skillId = "arithmetic_add_10",
            name = "Optellen tot 10",
            description = "Optellen van getallen met resultaat tot 10",
            category = SkillCategory.ARITHMETIC,
            cpaPhase = CpaPhase.MIXED_TRANSFER,
            isPremium = false,
            prerequisites = listOf("foundation_number_bonds_10"),
            minDifficulty = 1,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.VISUAL_GROUPS),
            preferredRepresentations = listOf(RepresentationType.SYMBOLS, RepresentationType.BOND_MODEL),
            rules = DidacticRules.build {
                range(1, 9)
                maxResult(10)
                minResult(2)
                requireNoBridge()
            },
            hintStrategy = HintStrategy.BOND_MODEL,
            commonErrorTypes = listOf(ErrorType.CALCULATION_ERROR, ErrorType.BOND_ERROR)
        ),

        SkillContentConfig(
            skillId = "arithmetic_add_10_concrete",
            name = "Optellen tot 10 (concreet)",
            description = "Optellen met blokjes en groepjes",
            category = SkillCategory.ARITHMETIC,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = false,
            prerequisites = listOf("foundation_number_bonds_10"),
            minDifficulty = 1,
            maxDifficulty = 2,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS),
            preferredRepresentations = listOf(RepresentationType.BLOCKS, RepresentationType.DOTS),
            rules = DidacticRules.build {
                range(1, 9)
                maxResult(10)
                minResult(2)
                requireNoBridge()
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.COUNTING_ERROR)
        ),

        // Optellen PICTURAAL
        SkillContentConfig(
            skillId = "arithmetic_add_10_pictorial",
            name = "Optellen tot 10 (picturaal)",
            description = "Optellen met bond model en getallenlijn",
            category = SkillCategory.ARITHMETIC,
            cpaPhase = CpaPhase.PICTORIAL,
            isPremium = false,
            prerequisites = listOf("arithmetic_add_10_concrete"),
            minDifficulty = 1,
            maxDifficulty = 3,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.NUMBER_LINE_CLICK),
            preferredRepresentations = listOf(RepresentationType.BOND_MODEL, RepresentationType.NUMBER_LINE),
            rules = DidacticRules.build {
                range(1, 9)
                maxResult(10)
                minResult(2)
                requireNoBridge()
            },
            hintStrategy = HintStrategy.BOND_MODEL,
            commonErrorTypes = listOf(ErrorType.BOND_ERROR),
            remediationSkill = "arithmetic_add_10_concrete"
        ),

        // Optellen ABSTRACT
        SkillContentConfig(
            skillId = "arithmetic_add_10_abstract",
            name = "Optellen tot 10 (abstract)",
            description = "Optellen met cijfers",
            category = SkillCategory.ARITHMETIC,
            cpaPhase = CpaPhase.ABSTRACT,
            isPremium = false,
            prerequisites = listOf("arithmetic_add_10_pictorial"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.SYMBOLS),
            rules = DidacticRules.build {
                range(1, 9)
                maxResult(10)
                minResult(2)
                requireNoBridge()
            },
            hintStrategy = HintStrategy.NONE,
            commonErrorTypes = listOf(ErrorType.CALCULATION_ERROR),
            remediationSkill = "arithmetic_add_10_pictorial"
        ),

        // Aggregatie skill voor aftrekken tot 10
        SkillContentConfig(
            skillId = "arithmetic_sub_10",
            name = "Aftrekken tot 10",
            description = "Aftrekken van getallen tot 10",
            category = SkillCategory.ARITHMETIC,
            cpaPhase = CpaPhase.MIXED_TRANSFER,
            isPremium = false,
            prerequisites = listOf("arithmetic_add_10"),
            minDifficulty = 1,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.VISUAL_GROUPS),
            preferredRepresentations = listOf(RepresentationType.SYMBOLS, RepresentationType.BOND_MODEL),
            rules = DidacticRules.build {
                range(1, 10)
                minResult(0)
                requireNoBridge()
            },
            hintStrategy = HintStrategy.BOND_MODEL,
            commonErrorTypes = listOf(ErrorType.CALCULATION_ERROR, ErrorType.BOND_ERROR)
        ),

        // Aftrekken CONCREET
        SkillContentConfig(
            skillId = "arithmetic_sub_10_concrete",
            name = "Aftrekken tot 10 (concreet)",
            description = "Aftrekken met blokjes en wegnemen",
            category = SkillCategory.ARITHMETIC,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = false,
            prerequisites = listOf("arithmetic_sub_10"),
            minDifficulty = 1,
            maxDifficulty = 2,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS),
            preferredRepresentations = listOf(RepresentationType.BLOCKS),
            rules = DidacticRules.build {
                range(1, 10)
                minResult(1)
                requireNoBridge()
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.COUNTING_ERROR)
        ),

        // Aftrekken PICTURAAL
        SkillContentConfig(
            skillId = "arithmetic_sub_10_pictorial",
            name = "Aftrekken tot 10 (picturaal)",
            description = "Aftrekken met bond model",
            category = SkillCategory.ARITHMETIC,
            cpaPhase = CpaPhase.PICTORIAL,
            isPremium = false,
            prerequisites = listOf("arithmetic_sub_10_concrete"),
            minDifficulty = 1,
            maxDifficulty = 3,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.NUMBER_LINE_CLICK),
            preferredRepresentations = listOf(RepresentationType.BOND_MODEL, RepresentationType.NUMBER_LINE),
            rules = DidacticRules.build {
                range(1, 10)
                minResult(1)
                requireNoBridge()
            },
            hintStrategy = HintStrategy.BOND_MODEL,
            commonErrorTypes = listOf(ErrorType.BOND_ERROR),
            remediationSkill = "arithmetic_sub_10_concrete"
        ),

        // Aftrekken ABSTRACT
        SkillContentConfig(
            skillId = "arithmetic_sub_10_abstract",
            name = "Aftrekken tot 10 (abstract)",
            description = "Aftrekken met cijfers",
            category = SkillCategory.ARITHMETIC,
            cpaPhase = CpaPhase.ABSTRACT,
            isPremium = false,
            prerequisites = listOf("arithmetic_sub_10_pictorial"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.SYMBOLS),
            rules = DidacticRules.build {
                range(1, 10)
                minResult(1)
                requireNoBridge()
            },
            hintStrategy = HintStrategy.NONE,
            commonErrorTypes = listOf(ErrorType.CALCULATION_ERROR),
            remediationSkill = "arithmetic_sub_10_pictorial"
        ),

        // === ARITHMETIC TOT 20 (Leeftijd 7-8) ===
        // Aggregatie skill voor optellen tot 20
        SkillContentConfig(
            skillId = "arithmetic_add_20",
            name = "Optellen tot 20",
            description = "Optellen van getallen met resultaat tot 20",
            category = SkillCategory.ARITHMETIC,
            cpaPhase = CpaPhase.MIXED_TRANSFER,
            isPremium = false,
            prerequisites = listOf("arithmetic_add_10", "foundation_number_bonds_20"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.VISUAL_GROUPS),
            preferredRepresentations = listOf(RepresentationType.BOND_MODEL, RepresentationType.NUMBER_LINE, RepresentationType.SYMBOLS),
            rules = DidacticRules.build {
                range(1, 19)
                maxResult(20)
                minResult(11)
                requireNoBridge()
            },
            hintStrategy = HintStrategy.BOND_MODEL,
            commonErrorTypes = listOf(ErrorType.BOND_ERROR, ErrorType.PLACE_VALUE_ERROR),
            remediationSkill = "foundation_number_bonds_20"
        ),

        SkillContentConfig(
            skillId = "arithmetic_sub_20",
            name = "Aftrekken tot 20",
            description = "Aftrekken van getallen tot 20",
            category = SkillCategory.ARITHMETIC,
            cpaPhase = CpaPhase.MIXED_TRANSFER,
            isPremium = false,
            prerequisites = listOf("arithmetic_sub_10", "arithmetic_add_20"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            preferredRepresentations = listOf(RepresentationType.BOND_MODEL, RepresentationType.NUMBER_LINE, RepresentationType.SYMBOLS),
            rules = DidacticRules.build {
                range(11, 20)
                minResult(1)
                requireNoBridge()
            },
            hintStrategy = HintStrategy.BOND_MODEL,
            commonErrorTypes = listOf(ErrorType.BOND_ERROR, ErrorType.PLACE_VALUE_ERROR),
            remediationSkill = "foundation_number_bonds_20"
        ),

        // === BRIDGE OVER 10 (Leeftijd 7-8) ===
        SkillContentConfig(
            skillId = "arithmetic_bridge_add",
            name = "Brug over 10 (optellen)",
            description = "Optellen met brug over 10",
            category = SkillCategory.ARITHMETIC,
            cpaPhase = CpaPhase.MIXED_TRANSFER,
            isPremium = false,
            prerequisites = listOf("arithmetic_add_20", "foundation_splits_10"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.MISSING_NUMBER, ExerciseType.VISUAL_GROUPS),
            preferredRepresentations = listOf(RepresentationType.BOND_MODEL, RepresentationType.NUMBER_LINE, RepresentationType.SYMBOLS),
            rules = DidacticRules.build {
                range(2, 9)
                maxResult(18)
                minResult(11)
                requireBridgeThroughTen()
                bothOperandsUnder(10)
            },
            hintStrategy = HintStrategy.STEP_BY_STEP,
            commonErrorTypes = listOf(ErrorType.BRIDGE_10_ERROR, ErrorType.BOND_ERROR),
            remediationSkill = "foundation_number_bonds_10"
        ),

        SkillContentConfig(
            skillId = "arithmetic_bridge_sub",
            name = "Brug over 10 (aftrekken)",
            description = "Aftrekken met brug over 10",
            category = SkillCategory.ARITHMETIC,
            cpaPhase = CpaPhase.MIXED_TRANSFER,
            isPremium = false,
            prerequisites = listOf("arithmetic_sub_20", "foundation_splits_10", "arithmetic_bridge_add"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.MISSING_NUMBER, ExerciseType.VISUAL_GROUPS),
            preferredRepresentations = listOf(RepresentationType.BOND_MODEL, RepresentationType.NUMBER_LINE, RepresentationType.SYMBOLS),
            rules = DidacticRules.build {
                range(11, 18)
                minResult(1)
                resultUnder(10)
                requireBridgeThroughTen()
            },
            hintStrategy = HintStrategy.STEP_BY_STEP,
            commonErrorTypes = listOf(ErrorType.BRIDGE_10_ERROR, ErrorType.BOND_ERROR),
            remediationSkill = "arithmetic_bridge_add"
        ),

        // === DOUBLES & HALVES (Leeftijd 6-8) ===
        SkillContentConfig(
            skillId = "patterns_doubles",
            name = "Dubbelen tot 20",
            description = "Dubbelen van getallen tot 20",
            category = SkillCategory.PATTERNS,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = false,
            prerequisites = listOf("arithmetic_add_10"),
            minDifficulty = 1,
            maxDifficulty = 3,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            preferredRepresentations = listOf(RepresentationType.GROUPS, RepresentationType.DOTS),
            rules = DidacticRules.build {
                range(1, 10)
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.CALCULATION_ERROR)
        ),

        SkillContentConfig(
            skillId = "patterns_halves",
            name = "Helften tot 20",
            description = "Helften van even getallen tot 20",
            category = SkillCategory.PATTERNS,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = false,
            prerequisites = listOf("patterns_doubles"),
            minDifficulty = 1,
            maxDifficulty = 3,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            preferredRepresentations = listOf(RepresentationType.GROUPS),
            rules = DidacticRules.build {
                range(2, 20)
                requireEven()
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.CALCULATION_ERROR),
            remediationSkill = "patterns_doubles"
        ),
        
        // === SKIP COUNTING (Leeftijd 7-8) ===
        SkillContentConfig(
            skillId = "patterns_count_2",
            name = "Tellen per 2",
            description = "Tellen met sprongen van 2",
            category = SkillCategory.PATTERNS,
            cpaPhase = CpaPhase.PICTORIAL,
            isPremium = false,
            prerequisites = listOf("patterns_doubles"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.SIMPLE_SEQUENCE, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.NUMBER_LINE),
            rules = DidacticRules.build {
                range(2, 20)
                exactStep(2)
                sequenceLength(4)
            },
            hintStrategy = HintStrategy.NUMBER_LINE,
            commonErrorTypes = listOf(ErrorType.SEQUENCE_ERROR)
        ),

        SkillContentConfig(
            skillId = "patterns_count_5",
            name = "Tellen per 5",
            description = "Tellen met sprongen van 5",
            category = SkillCategory.PATTERNS,
            cpaPhase = CpaPhase.ABSTRACT,
            isPremium = false,
            prerequisites = listOf("patterns_count_2"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.SIMPLE_SEQUENCE, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.NUMBER_LINE),
            rules = DidacticRules.build {
                range(5, 50)
                exactStep(5)
                sequenceLength(4)
            },
            hintStrategy = HintStrategy.NUMBER_LINE,
            commonErrorTypes = listOf(ErrorType.SEQUENCE_ERROR),
            remediationSkill = "patterns_count_2"
        ),

        SkillContentConfig(
            skillId = "patterns_count_10",
            name = "Tellen per 10",
            description = "Tellen met sprongen van 10",
            category = SkillCategory.PATTERNS,
            cpaPhase = CpaPhase.ABSTRACT,
            isPremium = false,
            prerequisites = listOf("patterns_count_5"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.SIMPLE_SEQUENCE, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.NUMBER_LINE),
            rules = DidacticRules.build {
                range(10, 100)
                exactStep(10)
                sequenceLength(4)
            },
            hintStrategy = HintStrategy.NUMBER_LINE,
            commonErrorTypes = listOf(ErrorType.SEQUENCE_ERROR, ErrorType.PLACE_VALUE_ERROR),
            remediationSkill = "patterns_count_5"
        ),

        // === WORD PROBLEMS EENVoudig (Leeftijd 6-8) ===
        SkillContentConfig(
            skillId = "arithmetic_word_simple",
            name = "Eenvoudige Sommen in Context",
            description = "Rekenen in eenvoudige verhaaltjes",
            category = SkillCategory.ARITHMETIC,
            cpaPhase = CpaPhase.MIXED_TRANSFER,
            isPremium = false,
            prerequisites = listOf("arithmetic_add_10", "arithmetic_sub_10"),
            minDifficulty = 2,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.MULTIPLE_CHOICE),
            preferredRepresentations = listOf(RepresentationType.CONTEXT),
            rules = DidacticRules.build {
                range(1, 10)
                maxResult(10)
            },
            hintStrategy = HintStrategy.STEP_BY_STEP,
            commonErrorTypes = listOf(ErrorType.CALCULATION_ERROR)
        )
    )

    // Leeftijd 8-11: Plaatswaarde, vermenigvuldigen, breuken, problem solving
    private val age8_11Configs = listOf(
        // === PLAATSwaARDE & GROTE GETALLEN (Leeftijd 8-9) ===
        SkillContentConfig(
            skillId = "advanced_compare_100",
            name = "Vergelijken tot 100",
            description = "Getallen vergelijken tot 100",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.PICTORIAL,
            isPremium = false,
            prerequisites = listOf("patterns_count_10"),
            minDifficulty = 3,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.COMPARE_NUMBERS, ExerciseType.NUMBER_LINE_CLICK),
            preferredRepresentations = listOf(RepresentationType.NUMBER_LINE, RepresentationType.SYMBOLS),
            rules = DidacticRules.build {
                range(1, 100)
                minDifference(5)
                allowEqual()
            },
            hintStrategy = HintStrategy.NUMBER_LINE,
            commonErrorTypes = listOf(ErrorType.COMPARE_ERROR, ErrorType.PLACE_VALUE_ERROR)
        ),

        SkillContentConfig(
            skillId = "advanced_place_value",
            name = "Tientallen en Eenheden",
            description = "Tientallen en eenheden herkennen en gebruiken",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = false,
            prerequisites = listOf("advanced_compare_100"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.MISSING_NUMBER, ExerciseType.VISUAL_GROUPS),
            preferredRepresentations = listOf(RepresentationType.BLOCKS, RepresentationType.SYMBOLS),
            rules = DidacticRules.build {
                range(10, 99)
                allowZero()
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.PLACE_VALUE_ERROR)
        ),

        SkillContentConfig(
            skillId = "advanced_place_value_extended",
            name = "Plaatswaarde tot 1000",
            description = "Honderdtallen, tientallen, eenheden",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.ABSTRACT,
            isPremium = true,
            prerequisites = listOf("advanced_place_value"),
            minDifficulty = 4,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.MISSING_NUMBER, ExerciseType.TYPED_NUMERIC),
            preferredRepresentations = listOf(RepresentationType.SYMBOLS),
            rules = DidacticRules.build {
                range(100, 999)
            },
            hintStrategy = HintStrategy.STEP_BY_STEP,
            commonErrorTypes = listOf(ErrorType.PLACE_VALUE_ERROR),
            remediationSkill = "advanced_place_value"
        ),

        // === VERMENIGvULDIGEN ALS GROEPJES (Leeftijd 8-9) ===
        SkillContentConfig(
            skillId = "advanced_groups",
            name = "Vermenigvuldigen als Groepjes",
            description = "Groepjes tellen als introductie tot vermenigvuldigen",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = false,
            prerequisites = listOf("patterns_doubles", "patterns_count_2"),
            minDifficulty = 3,
            maxDifficulty = 4,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            preferredRepresentations = listOf(RepresentationType.GROUPS, RepresentationType.ARRAY),
            rules = DidacticRules.build {
                range(2, 5)
                maxGroups(5)
                maxPerGroup(5)
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.GROUPING_ERROR, ErrorType.COUNTING_ERROR)
        ),

        SkillContentConfig(
            skillId = "advanced_arrays",
            name = "Arrays en Roosters",
            description = "Vermenigvuldigen met rijen en kolommen",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.PICTORIAL,
            isPremium = false,
            prerequisites = listOf("advanced_groups"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            preferredRepresentations = listOf(RepresentationType.ARRAY, RepresentationType.GROUPS),
            rules = DidacticRules.build {
                range(2, 10)
                maxGroups(10)
                maxPerGroup(10)
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.GROUPING_ERROR),
            remediationSkill = "advanced_groups"
        ),

        // === TAFELS (Leeftijd 8-11) ===
        SkillContentConfig(
            skillId = "advanced_table_2",
            name = "Tafel van 2",
            description = "De tafel van 2",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.MIXED_TRANSFER,
            isPremium = false,
            prerequisites = listOf("advanced_groups", "patterns_count_2"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.ARRAY, RepresentationType.GROUPS, RepresentationType.SYMBOLS),
            rules = DidacticRules.build {
                range(1, 10)
                exactMultiplier(2)
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.GROUPING_ERROR, ErrorType.SEQUENCE_ERROR),
            remediationSkill = "advanced_groups"
        ),

        SkillContentConfig(
            skillId = "advanced_table_5",
            name = "Tafel van 5",
            description = "De tafel van 5",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.ABSTRACT,
            isPremium = false,
            prerequisites = listOf("advanced_table_2"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.SYMBOLS, RepresentationType.ARRAY),
            rules = DidacticRules.build {
                range(1, 10)
                exactMultiplier(5)
            },
            hintStrategy = HintStrategy.NUMBER_LINE,
            commonErrorTypes = listOf(ErrorType.SEQUENCE_ERROR),
            remediationSkill = "patterns_count_5"
        ),

        SkillContentConfig(
            skillId = "advanced_table_10",
            name = "Tafel van 10",
            description = "De tafel van 10",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.ABSTRACT,
            isPremium = false,
            prerequisites = listOf("advanced_table_5"),
            minDifficulty = 3,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.SYMBOLS, RepresentationType.NUMBER_LINE),
            rules = DidacticRules.build {
                range(1, 10)
                exactMultiplier(10)
            },
            hintStrategy = HintStrategy.NUMBER_LINE,
            commonErrorTypes = listOf(ErrorType.PLACE_VALUE_ERROR, ErrorType.SEQUENCE_ERROR),
            remediationSkill = "patterns_count_10"
        ),

        SkillContentConfig(
            skillId = "advanced_table_3",
            name = "Tafel van 3",
            description = "De tafel van 3",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.ABSTRACT,
            isPremium = true,
            prerequisites = listOf("advanced_table_2"),
            minDifficulty = 4,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.SYMBOLS, RepresentationType.ARRAY),
            rules = DidacticRules.build {
                range(1, 10)
                exactMultiplier(3)
            },
            hintStrategy = HintStrategy.STEP_BY_STEP,
            commonErrorTypes = listOf(ErrorType.CALCULATION_ERROR),
            remediationSkill = "advanced_table_2"
        ),

        SkillContentConfig(
            skillId = "advanced_table_4",
            name = "Tafel van 4",
            description = "De tafel van 4",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.ABSTRACT,
            isPremium = true,
            prerequisites = listOf("advanced_table_3"),
            minDifficulty = 4,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.MISSING_NUMBER),
            preferredRepresentations = listOf(RepresentationType.SYMBOLS, RepresentationType.ARRAY),
            rules = DidacticRules.build {
                range(1, 10)
                exactMultiplier(4)
            },
            hintStrategy = HintStrategy.STEP_BY_STEP,
            commonErrorTypes = listOf(ErrorType.CALCULATION_ERROR),
            remediationSkill = "advanced_table_2"
        ),

        // === DELING (Leeftijd 9-11) ===
        SkillContentConfig(
            skillId = "advanced_division_intro",
            name = "Delen als Groepjes Maken",
            description = "Introductie tot delen",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.CONCRETE,
            isPremium = true,
            prerequisites = listOf("advanced_table_5"),
            minDifficulty = 4,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.TYPED_NUMERIC),
            preferredRepresentations = listOf(RepresentationType.GROUPS, RepresentationType.ARRAY),
            rules = DidacticRules.build {
                range(2, 10)
                maxResult(50)
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.GROUPING_ERROR),
            remediationSkill = "advanced_groups"
        ),

        // === BREUKEN (Leeftijd 9-11) ===
        SkillContentConfig(
            skillId = "advanced_fractions_basics",
            name = "Breuken: Deel van het Geheel",
            description = "Basale breuken begrijpen",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.PICTORIAL,
            isPremium = true,
            prerequisites = listOf("advanced_division_intro"),
            minDifficulty = 4,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.VISUAL_GROUPS, ExerciseType.MULTIPLE_CHOICE),
            preferredRepresentations = listOf(RepresentationType.GROUPS, RepresentationType.DOTS),
            rules = DidacticRules.build {
                range(1, 8)
            },
            hintStrategy = HintStrategy.VISUAL,
            commonErrorTypes = listOf(ErrorType.GROUPING_ERROR, ErrorType.CALCULATION_ERROR),
            remediationSkill = "advanced_division_intro"
        ),

        // === MEERSTAPSPROBLEMEN (Leeftijd 9-11) ===
        SkillContentConfig(
            skillId = "advanced_multistep",
            name = "Meerstapsproblemen",
            description = "Sommen met meerdere stappen",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.MIXED_TRANSFER,
            isPremium = true,
            prerequisites = listOf("advanced_table_5", "advanced_division_intro"),
            minDifficulty = 4,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.TYPED_NUMERIC, ExerciseType.MULTIPLE_CHOICE),
            preferredRepresentations = listOf(RepresentationType.CONTEXT, RepresentationType.SYMBOLS),
            rules = DidacticRules.build {
                range(1, 100)
            },
            hintStrategy = HintStrategy.STEP_BY_STEP,
            commonErrorTypes = listOf(ErrorType.CALCULATION_ERROR)
        ),

        // === REDENEEROPGAVEN (Leeftijd 10-11) ===
        SkillContentConfig(
            skillId = "advanced_reasoning",
            name = "Redeneeropgaven",
            description = "Wiskundig denken en redeneren",
            category = SkillCategory.ADVANCED,
            cpaPhase = CpaPhase.ABSTRACT,
            isPremium = true,
            prerequisites = listOf("advanced_multistep", "advanced_fractions_basics"),
            minDifficulty = 5,
            maxDifficulty = 5,
            allowedExerciseTypes = listOf(ExerciseType.MULTIPLE_CHOICE, ExerciseType.TYPED_NUMERIC),
            preferredRepresentations = listOf(RepresentationType.CONTEXT, RepresentationType.SYMBOLS),
            rules = DidacticRules.build {
                range(1, 100)
            },
            hintStrategy = HintStrategy.STEP_BY_STEP,
            commonErrorTypes = listOf(ErrorType.CALCULATION_ERROR)
        )
    )

    // Combine all configs
    private val configs = age5_6Configs + age6_8Configs + age8_11Configs
    
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
            // Level 0: Echte basis (5-6 jaar)
            listOf("foundation_subitize_5", "foundation_shapes"),
            // Level 1: Tellen
            listOf("foundation_counting"),
            // Level 2: Number bonds 5 + meer/minder + patronen
            listOf("foundation_number_bonds_5", "foundation_more_less", "foundation_patterns"),
            // Level 3: Number bonds 10 + groepjes + splitsen
            listOf("foundation_groups_intro", "foundation_number_bonds_10", "foundation_splits_10"),
            // Level 4: Rekenen tot 10
            listOf("arithmetic_add_10", "arithmetic_sub_10"),
            // Level 5: Patterns (doubles/halves)
            listOf("patterns_doubles", "patterns_halves"),
            // Level 6: Rekenen tot 20 + splitsen 20
            listOf("foundation_number_bonds_20", "foundation_splits_20", "arithmetic_add_20", "arithmetic_sub_20"),
            // Level 7: Brug over 10
            listOf("arithmetic_bridge_add", "arithmetic_bridge_sub"),
            // Level 8: Skip counting
            listOf("patterns_count_2", "patterns_count_5", "patterns_count_10"),
            // Level 9: Plaatswaarde
            listOf("advanced_compare_100", "advanced_place_value"),
            // Level 10: Tafels
            listOf("advanced_groups", "advanced_table_2", "advanced_table_5", "advanced_table_10")
        )
    }

    /**
     * PATCH 3: Haal start skills op voor een specifieke leeftijd
     * Gebaseerd op NL/Vlaamse curriculum verwachtingen
     */
    fun getStartSkillsForAge(age: Int): List<String> {
        return when (age) {
            5, 6 -> listOf(
                // Foundation: Subitizing, telling, bonds 5
                "foundation_subitize_5",
                "foundation_counting",
                "foundation_number_bonds_5",
                "foundation_more_less"
            )
            7, 8 -> listOf(
                // Early Arithmetic: Bonds 10/20, optellen/aftrekken
                "foundation_number_bonds_10",
                "foundation_splits_10",
                "arithmetic_add_10_concrete",
                "arithmetic_sub_10_concrete",
                "patterns_doubles"
            )
            9, 10, 11 -> listOf(
                // Extended: Brug over 10, tafels, plaatswaarde
                "arithmetic_bridge_add",
                "patterns_count_2",
                "patterns_count_5",
                "advanced_groups",
                "advanced_table_2"
            )
            else -> listOf("foundation_subitize_5", "foundation_counting")
        }
    }

    /**
     * PATCH 3: Haal skills op per curriculum domein
     */
    fun getSkillsByDomain(domain: CurriculumDomain): List<String> {
        return when (domain) {
            CurriculumDomain.NUMBER_SENSE -> listOf(
                "foundation_subitize_5", "foundation_counting",
                "foundation_number_bonds_5", "foundation_number_bonds_10", "foundation_number_bonds_20",
                "foundation_splits_10", "foundation_splits_20",
                "advanced_compare_100", "advanced_place_value"
            )
            CurriculumDomain.OPERATIONS -> listOf(
                "arithmetic_add_10_concrete", "arithmetic_add_10_pictorial", "arithmetic_add_10_abstract",
                "arithmetic_sub_10_concrete", "arithmetic_sub_10_pictorial", "arithmetic_sub_10_abstract",
                "arithmetic_add_20", "arithmetic_sub_20",
                "arithmetic_bridge_add", "arithmetic_bridge_sub"
            )
            CurriculumDomain.PATTERNS -> listOf(
                "foundation_patterns", "patterns_doubles", "patterns_halves",
                "patterns_count_2", "patterns_count_5", "patterns_count_10"
            )
            CurriculumDomain.MULTIPLICATION -> listOf(
                "advanced_groups", "advanced_arrays",
                "advanced_table_2", "advanced_table_5", "advanced_table_10"
            )
            CurriculumDomain.FRACTIONS -> listOf(
                "advanced_fractions_basics"
            )
            CurriculumDomain.REASONING -> listOf(
                "advanced_multistep", "advanced_reasoning"
            )
        }
    }

    /**
     * PATCH 3: Curriculum domeinen voor NL/Vlaanderen
     */
    enum class CurriculumDomain {
        NUMBER_SENSE,      // Getalbegrip (subitizing, bonds, plaatswaarde)
        OPERATIONS,        // Bewerkingen (optellen, aftrekken)
        PATTERNS,          // Patronen en relaties
        MULTIPLICATION,    // Vermenigvuldigen (groepjes, tafels)
        FRACTIONS,         // Breuken
        REASONING          // Redeneren en probleemoplossing
    }
}