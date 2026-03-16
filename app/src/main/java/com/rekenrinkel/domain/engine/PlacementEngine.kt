package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.content.ContentRepository
import com.rekenrinkel.domain.model.PlacementResult
import com.rekenrinkel.domain.model.StartingBand

/**
 * Placement engine voor diagnostische startbepaling.
 * 6-10 items om het werkelijke niveau te schatten.
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
            PlacementItem("foundation_subitize_5", 1, "Hoeveel stippen zie je?", "3"),
            PlacementItem("foundation_counting", 1, "Tel de blokjes", "5"),
            PlacementItem("foundation_more_less", 2, "Welke groep heeft meer?", "links"),
            PlacementItem("foundation_number_bonds_5", 2, "5 is 2 en...", "3"),
            PlacementItem("foundation_number_bonds_10", 3, "10 is 7 en...", "3"),
            PlacementItem("foundation_subitize_5", 2, "Hoeveel direct zien?", "4")
        )
    }

    private fun generateEarlyArithmeticItems(): List<PlacementItem> {
        return listOf(
            PlacementItem("foundation_number_bonds_10", 2, "10 is 8 en...", "2"),
            PlacementItem("arithmetic_add_10", 2, "3 + 4 =", "7"),
            PlacementItem("arithmetic_sub_10", 2, "8 - 3 =", "5"),
            PlacementItem("arithmetic_add_20", 3, "12 + 5 =", "17"),
            PlacementItem("arithmetic_bridge_add", 4, "8 + 5 =", "13"),
            PlacementItem("patterns_doubles", 2, "Dubbel van 6 is", "12"),
            PlacementItem("foundation_number_bonds_20", 3, "15 is 10 en...", "5"),
            PlacementItem("arithmetic_sub_20", 3, "17 - 4 =", "13")
        )
    }

    private fun generateExtendedItems(): List<PlacementItem> {
        return listOf(
            PlacementItem("patterns_count_2", 2, "Tellen per 2: 2, 4, 6, ...", "8"),
            PlacementItem("advanced_groups", 3, "3 groepjes van 4 is", "12"),
            PlacementItem("advanced_table_2", 3, "2 x 6 =", "12"),
            PlacementItem("advanced_table_5", 3, "5 x 4 =", "20"),
            PlacementItem("advanced_compare_100", 3, "Welk is groter: 45 of 54?", "54"),
            PlacementItem("advanced_place_value", 4, "Hoeveel tientallen in 67?", "6"),
            PlacementItem("patterns_count_10", 2, "Tellen per 10: 10, 20, 30, ...", "40"),
            PlacementItem("advanced_table_10", 3, "10 x 7 =", "70"),
            PlacementItem("advanced_groups", 4, "Hoeveel in 4 rijen van 6?", "24"),
            PlacementItem("patterns_halves", 3, "Helft van 18 is", "9")
        )
    }

    /**
     * Analyseer placement resultaten en bepaal startcluster
     */
    fun analyzePlacement(
        band: StartingBand,
        results: List<PlacementResult>
    ): PlacementAnalysis {
        val correctCount = results.count { it.isCorrect }
        val accuracy = correctCount.toFloat() / results.size
        val avgResponseTime = results.map { it.responseTimeMs }.average()

        return when (band) {
            StartingBand.FOUNDATION -> analyzeFoundation(accuracy, avgResponseTime)
            StartingBand.EARLY_ARITHMETIC -> analyzeEarlyArithmetic(accuracy, avgResponseTime)
            StartingBand.EXTENDED -> analyzeExtended(accuracy, avgResponseTime)
        }
    }

    private fun analyzeFoundation(accuracy: Float, avgResponseTime: Double): PlacementAnalysis {
        return when {
            accuracy >= 0.8 && avgResponseTime < 5000 -> PlacementAnalysis(
                recommendedBand = StartingBand.EARLY_ARITHMETIC,
                startSkills = listOf("foundation_number_bonds_10", "arithmetic_add_10"),
                startCpaPhase = com.rekenrinkel.domain.content.CpaPhase.PICTORIAL,
                difficultyOffset = 1
            )
            accuracy >= 0.5 -> PlacementAnalysis(
                recommendedBand = StartingBand.FOUNDATION,
                startSkills = listOf("foundation_number_bonds_5", "foundation_more_less"),
                startCpaPhase = com.rekenrinkel.domain.content.CpaPhase.CONCRETE,
                difficultyOffset = 1
            )
            else -> PlacementAnalysis(
                recommendedBand = StartingBand.FOUNDATION,
                startSkills = listOf("foundation_subitize_5", "foundation_counting"),
                startCpaPhase = com.rekenrinkel.domain.content.CpaPhase.CONCRETE,
                difficultyOffset = 0
            )
        }
    }

    private fun analyzeEarlyArithmetic(accuracy: Float, avgResponseTime: Double): PlacementAnalysis {
        return when {
            accuracy >= 0.75 && avgResponseTime < 6000 -> PlacementAnalysis(
                recommendedBand = StartingBand.EXTENDED,
                startSkills = listOf("patterns_count_2", "advanced_groups"),
                startCpaPhase = com.rekenrinkel.domain.content.CpaPhase.CONCRETE,
                difficultyOffset = 1
            )
            accuracy >= 0.5 -> PlacementAnalysis(
                recommendedBand = StartingBand.EARLY_ARITHMETIC,
                startSkills = listOf("arithmetic_add_10", "arithmetic_sub_10"),
                startCpaPhase = com.rekenrinkel.domain.content.CpaPhase.MIXED_TRANSFER,
                difficultyOffset = 1
            )
            else -> PlacementAnalysis(
                recommendedBand = StartingBand.FOUNDATION,
                startSkills = listOf("foundation_number_bonds_10", "arithmetic_add_10"),
                startCpaPhase = com.rekenrinkel.domain.content.CpaPhase.CONCRETE,
                difficultyOffset = 0
            )
        }
    }

    private fun analyzeExtended(accuracy: Float, avgResponseTime: Double): PlacementAnalysis {
        return when {
            accuracy >= 0.7 && avgResponseTime < 7000 -> PlacementAnalysis(
                recommendedBand = StartingBand.EXTENDED,
                startSkills = listOf("advanced_table_2", "advanced_table_5", "advanced_table_10"),
                startCpaPhase = com.rekenrinkel.domain.content.CpaPhase.ABSTRACT,
                difficultyOffset = 2
            )
            accuracy >= 0.5 -> PlacementAnalysis(
                recommendedBand = StartingBand.EXTENDED,
                startSkills = listOf("advanced_groups", "advanced_table_2"),
                startCpaPhase = com.rekenrinkel.domain.content.CpaPhase.CONCRETE,
                difficultyOffset = 1
            )
            else -> PlacementAnalysis(
                recommendedBand = StartingBand.EARLY_ARITHMETIC,
                startSkills = listOf("arithmetic_bridge_add", "patterns_count_2"),
                startCpaPhase = com.rekenrinkel.domain.content.CpaPhase.PICTORIAL,
                difficultyOffset = 0
            )
        }
    }

    data class PlacementItem(
        val skillId: String,
        val difficulty: Int,
        val question: String,
        val correctAnswer: String
    )

    data class PlacementAnalysis(
        val recommendedBand: StartingBand,
        val startSkills: List<String>,
        val startCpaPhase: com.rekenrinkel.domain.content.CpaPhase,
        val difficultyOffset: Int
    )
}
