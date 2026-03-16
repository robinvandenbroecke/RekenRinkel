package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.content.CpaPhase
import com.rekenrinkel.domain.model.PlacementResult
import com.rekenrinkel.domain.model.StartingBand

/**
 * Placement engine voor diagnostische startbepaling.
 * 6-8 items met didactisch correcte antwoordopties.
 */
class PlacementEngine {

    /**
     * Genereer placement items op basis van leeftijd/startband
     */
    fun generatePlacementItems(band: StartingBand): List<PlacementItem> {
        return when (band) {
            StartingBand.FOUNDATION -> generateFoundationItems()
            StartingBand.EARLY_ARITHMETIC -> generateEarlyArithmeticItems()
            StartingBand.EXTENDED -> generateExtendedItems()
        }
    }

    private fun generateFoundationItems(): List<PlacementItem> {
        return listOf(
            PlacementItem(
                skillId = "foundation_subitize",
                skillArea = SkillArea.NUMBER_SENSE,
                difficulty = 1,
                question = "Hoeveel stippen zie je?",
                correctAnswer = "4",
                options = listOf("3", "4", "5", "6"),
                representationType = RepresentationType.DOTS
            ),
            PlacementItem(
                skillId = "foundation_counting",
                skillArea = SkillArea.NUMBER_SENSE,
                difficulty = 1,
                question = "Tel de blokjes. Hoeveel zijn er?",
                correctAnswer = "7",
                options = listOf("6", "7", "8", "9"),
                representationType = RepresentationType.BLOCKS
            ),
            PlacementItem(
                skillId = "foundation_compare",
                skillArea = SkillArea.COMPARISON,
                difficulty = 2,
                question = "Welke groep heeft MEER?",
                correctAnswer = "Links",
                options = listOf("Links", "Rechts", "Evenveel"),
                representationType = RepresentationType.GROUPS
            ),
            PlacementItem(
                skillId = "foundation_number_bonds",
                skillArea = SkillArea.NUMBER_SENSE,
                difficulty = 2,
                question = "5 is 2 en...",
                correctAnswer = "3",
                options = listOf("2", "3", "4", "5"),
                representationType = RepresentationType.BOND_MODEL
            ),
            PlacementItem(
                skillId = "foundation_sequence",
                skillArea = SkillArea.NUMBER_SENSE,
                difficulty = 2,
                question = "Welk getal ontbreekt? 3, 4, ..., 6",
                correctAnswer = "5",
                options = listOf("4", "5", "7"),
                representationType = RepresentationType.NUMBER_LINE
            ),
            PlacementItem(
                skillId = "foundation_more_less",
                skillArea = SkillArea.COMPARISON,
                difficulty = 3,
                question = "Is 8 groter of kleiner dan 6?",
                correctAnswer = "Groter",
                options = listOf("Groter", "Kleiner", "Evenveel"),
                representationType = RepresentationType.NUMBER_LINE
            )
        )
    }

    private fun generateEarlyArithmeticItems(): List<PlacementItem> {
        return listOf(
            PlacementItem(
                skillId = "arithmetic_add",
                skillArea = SkillArea.ARITHMETIC,
                difficulty = 2,
                question = "3 + 4 =",
                correctAnswer = "7",
                options = listOf("6", "7", "8", "9"),
                representationType = RepresentationType.BLOCKS
            ),
            PlacementItem(
                skillId = "arithmetic_sub",
                skillArea = SkillArea.ARITHMETIC,
                difficulty = 2,
                question = "9 - 4 =",
                correctAnswer = "5",
                options = listOf("4", "5", "6", "7"),
                representationType = RepresentationType.BLOCKS
            ),
            PlacementItem(
                skillId = "number_bonds_10",
                skillArea = SkillArea.NUMBER_SENSE,
                difficulty = 2,
                question = "10 is 8 en...",
                correctAnswer = "2",
                options = listOf("1", "2", "3", "4"),
                representationType = RepresentationType.BOND_MODEL
            ),
            PlacementItem(
                skillId = "arithmetic_bridge",
                skillArea = SkillArea.ARITHMETIC,
                difficulty = 3,
                question = "8 + 5 =",
                correctAnswer = "13",
                options = listOf("11", "12", "13", "14"),
                representationType = RepresentationType.BOND_MODEL
            ),
            PlacementItem(
                skillId = "arithmetic_add_20",
                skillArea = SkillArea.ARITHMETIC,
                difficulty = 3,
                question = "12 + 6 =",
                correctAnswer = "18",
                options = listOf("16", "17", "18", "19"),
                representationType = RepresentationType.NUMBER_LINE
            ),
            PlacementItem(
                skillId = "patterns_doubles",
                skillArea = SkillArea.ARITHMETIC,
                difficulty = 2,
                question = "Dubbel van 7 is...",
                correctAnswer = "14",
                options = listOf("12", "13", "14", "15"),
                representationType = RepresentationType.ARRAY
            ),
            PlacementItem(
                skillId = "comparison_symbol",
                skillArea = SkillArea.COMPARISON,
                difficulty = 2,
                question = "Vul in: 15 ... 12",
                correctAnswer = ">",
                options = listOf("<", ">", "="),
                representationType = RepresentationType.NUMBER_LINE
            ),
            PlacementItem(
                skillId = "number_bonds_20",
                skillArea = SkillArea.NUMBER_SENSE,
                difficulty = 3,
                question = "17 is 10 en...",
                correctAnswer = "7",
                options = listOf("6", "7", "8", "9"),
                representationType = RepresentationType.BOND_MODEL
            )
        )
    }

    private fun generateExtendedItems(): List<PlacementItem> {
        return listOf(
            PlacementItem(
                skillId = "multiplication_groups",
                skillArea = SkillArea.MULTIPLICATION,
                difficulty = 3,
                question = "3 groepjes van 4 is...",
                correctAnswer = "12",
                options = listOf("7", "11", "12", "13"),
                representationType = RepresentationType.GROUPS
            ),
            PlacementItem(
                skillId = "table_2",
                skillArea = SkillArea.MULTIPLICATION,
                difficulty = 3,
                question = "2 x 7 =",
                correctAnswer = "14",
                options = listOf("12", "13", "14", "16"),
                representationType = RepresentationType.ARRAY
            ),
            PlacementItem(
                skillId = "table_5",
                skillArea = SkillArea.MULTIPLICATION,
                difficulty = 3,
                question = "5 x 6 =",
                correctAnswer = "30",
                options = listOf("25", "28", "30", "35"),
                representationType = RepresentationType.ARRAY
            ),
            PlacementItem(
                skillId = "place_value_tens",
                skillArea = SkillArea.PLACE_VALUE,
                difficulty = 3,
                question = "Hoeveel tientallen in 56?",
                correctAnswer = "5",
                options = listOf("5", "6", "50", "56"),
                representationType = RepresentationType.BLOCKS
            ),
            PlacementItem(
                skillId = "comparison_100",
                skillArea = SkillArea.COMPARISON,
                difficulty = 3,
                question = "Welk is groter: 72 of 68?",
                correctAnswer = "72",
                options = listOf("68", "72", "Evenveel"),
                representationType = RepresentationType.NUMBER_LINE
            ),
            PlacementItem(
                skillId = "counting_patterns",
                skillArea = SkillArea.NUMBER_SENSE,
                difficulty = 2,
                question = "Tellen per 10: 20, 30, 40, ...",
                correctAnswer = "50",
                options = listOf("45", "50", "60"),
                representationType = RepresentationType.NUMBER_LINE
            ),
            PlacementItem(
                skillId = "table_10",
                skillArea = SkillArea.MULTIPLICATION,
                difficulty = 3,
                question = "10 x 8 =",
                correctAnswer = "80",
                options = listOf("18", "80", "88", "108"),
                representationType = RepresentationType.ARRAY
            ),
            PlacementItem(
                skillId = "halves",
                skillArea = SkillArea.MULTIPLICATION,
                difficulty = 3,
                question = "Helft van 16 is...",
                correctAnswer = "8",
                options = listOf("6", "7", "8", "9"),
                representationType = RepresentationType.GROUPS
            )
        )
    }

    /**
     * Analyseer placement resultaten per vaardigheidscluster
     */
    fun analyzePlacement(
        band: StartingBand,
        results: List<PlacementResult>
    ): PlacementAnalysis {
        // Groepeer resultaten per skill area
        val areaResults = results.groupBy { itemForResult(it)?.skillArea ?: SkillArea.NUMBER_SENSE }
        
        // Bereken accuracy per area
        val areaAccuracy = areaResults.mapValues { (_, items) ->
            items.count { it.isCorrect }.toFloat() / items.size
        }
        
        val overallAccuracy = results.count { it.isCorrect }.toFloat() / results.size
        val avgResponseTime = results.map { it.responseTimeMs }.average()
        
        // Bepaal zwakke en sterke areas
        val weakAreas = areaAccuracy.filter { it.value < 0.5 }.keys.toList()
        val strongAreas = areaAccuracy.filter { it.value >= 0.7 }.keys.toList()

        return when (band) {
            StartingBand.FOUNDATION -> analyzeFoundation(overallAccuracy, avgResponseTime, areaAccuracy, weakAreas, strongAreas)
            StartingBand.EARLY_ARITHMETIC -> analyzeEarlyArithmetic(overallAccuracy, avgResponseTime, areaAccuracy, weakAreas, strongAreas)
            StartingBand.EXTENDED -> analyzeExtended(overallAccuracy, avgResponseTime, areaAccuracy, weakAreas, strongAreas)
        }
    }

    private fun analyzeFoundation(
        accuracy: Float, 
        avgResponseTime: Double,
        areaAccuracy: Map<SkillArea, Float>,
        weakAreas: List<SkillArea>,
        strongAreas: List<SkillArea>
    ): PlacementAnalysis {
        return when {
            accuracy >= 0.8 && avgResponseTime < 5000 -> PlacementAnalysis(
                recommendedBand = StartingBand.EARLY_ARITHMETIC,
                startSkills = listOf("arithmetic_add", "number_bonds_10"),
                startCpaPhase = CpaPhase.PICTORIAL,
                difficultyOffset = 1,
                weakAreas = emptyList(),
                strongAreas = listOf(SkillArea.NUMBER_SENSE, SkillArea.COMPARISON)
            )
            accuracy >= 0.5 -> PlacementAnalysis(
                recommendedBand = StartingBand.FOUNDATION,
                startSkills = listOf("foundation_number_bonds", "foundation_counting"),
                startCpaPhase = CpaPhase.CONCRETE,
                difficultyOffset = 1,
                weakAreas = weakAreas,
                strongAreas = strongAreas
            )
            else -> PlacementAnalysis(
                recommendedBand = StartingBand.FOUNDATION,
                startSkills = listOf("foundation_subitize", "foundation_counting"),
                startCpaPhase = CpaPhase.CONCRETE,
                difficultyOffset = 0,
                weakAreas = weakAreas,
                strongAreas = emptyList()
            )
        }
    }

    private fun analyzeEarlyArithmetic(
        accuracy: Float, 
        avgResponseTime: Double,
        areaAccuracy: Map<SkillArea, Float>,
        weakAreas: List<SkillArea>,
        strongAreas: List<SkillArea>
    ): PlacementAnalysis {
        return when {
            accuracy >= 0.75 && avgResponseTime < 6000 -> PlacementAnalysis(
                recommendedBand = StartingBand.EXTENDED,
                startSkills = listOf("multiplication_groups", "table_2"),
                startCpaPhase = CpaPhase.CONCRETE,
                difficultyOffset = 1,
                weakAreas = emptyList(),
                strongAreas = listOf(SkillArea.ARITHMETIC)
            )
            accuracy >= 0.5 -> PlacementAnalysis(
                recommendedBand = StartingBand.EARLY_ARITHMETIC,
                startSkills = listOf("arithmetic_add", "arithmetic_sub", "number_bonds_10"),
                startCpaPhase = CpaPhase.MIXED_TRANSFER,
                difficultyOffset = 1,
                weakAreas = weakAreas,
                strongAreas = strongAreas
            )
            else -> PlacementAnalysis(
                recommendedBand = StartingBand.FOUNDATION,
                startSkills = listOf("number_bonds_10", "arithmetic_add"),
                startCpaPhase = CpaPhase.CONCRETE,
                difficultyOffset = 0,
                weakAreas = weakAreas,
                strongAreas = emptyList()
            )
        }
    }

    private fun analyzeExtended(
        accuracy: Float, 
        avgResponseTime: Double,
        areaAccuracy: Map<SkillArea, Float>,
        weakAreas: List<SkillArea>,
        strongAreas: List<SkillArea>
    ): PlacementAnalysis {
        return when {
            accuracy >= 0.7 && avgResponseTime < 7000 -> PlacementAnalysis(
                recommendedBand = StartingBand.EXTENDED,
                startSkills = listOf("table_2", "table_5", "table_10", "place_value_tens"),
                startCpaPhase = CpaPhase.ABSTRACT,
                difficultyOffset = 2,
                weakAreas = emptyList(),
                strongAreas = listOf(SkillArea.MULTIPLICATION, SkillArea.PLACE_VALUE)
            )
            accuracy >= 0.5 -> PlacementAnalysis(
                recommendedBand = StartingBand.EXTENDED,
                startSkills = listOf("multiplication_groups", "table_2", "place_value_tens"),
                startCpaPhase = CpaPhase.CONCRETE,
                difficultyOffset = 1,
                weakAreas = weakAreas,
                strongAreas = strongAreas
            )
            else -> PlacementAnalysis(
                recommendedBand = StartingBand.EARLY_ARITHMETIC,
                startSkills = listOf("arithmetic_bridge", "multiplication_groups"),
                startCpaPhase = CpaPhase.PICTORIAL,
                difficultyOffset = 0,
                weakAreas = weakAreas,
                strongAreas = emptyList()
            )
        }
    }

    /**
     * Helper om het originele PlacementItem te vinden voor een resultaat
     */
    private fun itemForResult(result: PlacementResult): PlacementItem? {
        // Deze methode zou normaal gesproken de originele items opslaan
        // Voor nu returnen we null en gebruiken we default gedrag
        return null
    }

    // Enums voor skill areas
    enum class SkillArea {
        NUMBER_SENSE,
        ARITHMETIC,
        COMPARISON,
        MULTIPLICATION,
        PLACE_VALUE
    }

    // Enum voor representatie types
    enum class RepresentationType {
        DOTS,
        BLOCKS,
        NUMBER_LINE,
        BOND_MODEL,
        GROUPS,
        ARRAY,
        SYMBOLS
    }

    data class PlacementItem(
        val skillId: String,
        val skillArea: SkillArea,
        val difficulty: Int,
        val question: String,
        val correctAnswer: String,
        val options: List<String>,
        val representationType: RepresentationType
    )

    data class PlacementAnalysis(
        val recommendedBand: StartingBand,
        val startSkills: List<String>,
        val startCpaPhase: CpaPhase,
        val difficultyOffset: Int,
        val weakAreas: List<SkillArea> = emptyList(),
        val strongAreas: List<SkillArea> = emptyList(),
        val focusSkills: List<String> = startSkills  // Alias voor consistentie
    )
}
