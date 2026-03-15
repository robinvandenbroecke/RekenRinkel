package com.rekenrinkel.domain.model

import java.util.UUID

/**
 * Thema's voor de app
 */
enum class Theme {
    DINOSAURS,
    CARS,
    SPACE
}

/**
 * Gebruikersprofiel
 */
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val theme: Theme = Theme.DINOSAURS,
    val currentLevel: Int = 1,
    val totalXp: Int = 0,
    val currentStreak: Int = 0,
    val lastSessionDate: Long? = null,
    val longestStreak: Int = 0
) {
    fun xpForNextLevel(): Int {
        return currentLevel * 100
    }
    
    fun xpProgress(): Float {
        val xpInCurrentLevel = totalXp % 100
        return xpInCurrentLevel / 100f
    }
}

/**
 * Mastery levels voor vaardigheden
 */
enum class MasteryLevel {
    NOT_LEARNED,    // 0-24
    EMERGING,       // 25-49
    PRACTICING,     // 50-74
    SOLID,          // 75-89
    MASTERED        // 90-100
}

fun Int.toMasteryLevel(): MasteryLevel {
    return when (this) {
        in 0..24 -> MasteryLevel.NOT_LEARNED
        in 25..49 -> MasteryLevel.EMERGING
        in 50..74 -> MasteryLevel.PRACTICING
        in 75..89 -> MasteryLevel.SOLID
        else -> MasteryLevel.MASTERED
    }
}

/**
 * Skill definitie
 */
data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val category: SkillCategory,
    val minDifficulty: Int = 1,
    val maxDifficulty: Int = 5,
    val isPremium: Boolean = false,
    val prerequisites: List<String> = emptyList()
)

enum class SkillCategory {
    FOUNDATION,
    ARITHMETIC,
    PATTERNS,
    ADVANCED
}

/**
 * Skill progress voor een gebruiker
 */
data class SkillProgress(
    val skillId: String,
    val masteryScore: Int = 0, // 0-100
    val correctAnswers: Int = 0,
    val wrongAnswers: Int = 0,
    val averageResponseTimeMs: Long = 0,
    val lastPracticed: Long? = null,
    val currentDifficulty: Int = 1
) {
    fun totalAttempts(): Int = correctAnswers + wrongAnswers
    fun successRate(): Float = if (totalAttempts() > 0) correctAnswers.toFloat() / totalAttempts() else 0f
    fun masteryLevel(): MasteryLevel = masteryScore.toMasteryLevel()
}

/**
 * Oefentypes
 */
enum class ExerciseType {
    MULTIPLE_CHOICE,
    TYPED_NUMERIC,
    MISSING_NUMBER,
    VISUAL_QUANTITY,
    VISUAL_GROUPS,
    SIMPLE_SEQUENCE,
    COMPARE_NUMBERS,
    NUMBER_LINE_CLICK
}

/**
 * Een oefening
 */
data class Exercise(
    val id: String = UUID.randomUUID().toString(),
    val skillId: String,
    val type: ExerciseType,
    val difficulty: Int,
    val question: String,
    val visualData: VisualData? = null,
    val options: List<String>? = null,
    val correctAnswer: String,
    val distractors: List<String> = emptyList(),
    val hint: String? = null
)

/**
 * Visuele data voor oefeningen
 */
data class VisualData(
    val type: VisualType,
    val count: Int? = null,
    val groups: List<Int>? = null,
    val firstNumber: Int? = null,
    val secondNumber: Int? = null
)

enum class VisualType {
    DOTS,
    BLOCKS,
    GROUPS,
    NUMBER_LINE,
    COMPARISON
}

/**
 * Resultaat van een oefening
 */
data class ExerciseResult(
    val exerciseId: String,
    val skillId: String,
    val isCorrect: Boolean,
    val responseTimeMs: Long,
    val givenAnswer: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Sessie resultaat
 */
data class SessionResult(
    val sessionId: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val exercises: List<ExerciseResult> = emptyList(),
    val xpEarned: Int = 0,
    val stars: Int = 0
) {
    fun correctCount(): Int = exercises.count { it.isCorrect }
    fun totalCount(): Int = exercises.size
    fun accuracy(): Float = if (totalCount() > 0) correctCount().toFloat() / totalCount() else 0f
}

/**
 * Content availability
 */
enum class ContentAvailability {
    FREE,
    PREMIUM
}

/**
 * Alle skills voor V1
 */
object SkillDefinitions {
    val ALL_SKILLS = listOf(
        // FOUNDATION - FREE
        Skill("foundation_number_images_5", "Getalbeelden tot 5", "Visuele getalbeelden tot 5", SkillCategory.FOUNDATION, 1, 3, false),
        Skill("foundation_splits_10", "Splitsingen tot 10", "Getallen splitsen tot 10", SkillCategory.FOUNDATION, 1, 4, false),
        Skill("foundation_splits_20", "Splitsingen tot 20", "Getallen splitsen tot 20", SkillCategory.FOUNDATION, 2, 5, true),
        
        // ARITHMETIC - FREE + PREMIUM
        Skill("arithmetic_add_10", "Optellen tot 10", "Optellen tot 10", SkillCategory.ARITHMETIC, 1, 3, false),
        Skill("arithmetic_sub_10", "Aftrekken tot 10", "Aftrekken tot 10", SkillCategory.ARITHMETIC, 1, 3, false),
        Skill("arithmetic_add_20", "Optellen tot 20", "Optellen tot 20", SkillCategory.ARITHMETIC, 2, 4, true),
        Skill("arithmetic_sub_20", "Aftrekken tot 20", "Aftrekken tot 20", SkillCategory.ARITHMETIC, 2, 4, true),
        Skill("arithmetic_bridge_add", "Brug over 10 (optellen)", "Brug over 10 bij optellen", SkillCategory.ARITHMETIC, 3, 5, true),
        Skill("arithmetic_bridge_sub", "Brug over 10 (aftrekken)", "Brug over 10 bij aftrekken", SkillCategory.ARITHMETIC, 3, 5, true),
        
        // PATTERNS - FREE + PREMIUM
        Skill("patterns_doubles", "Dubbelen tot 20", "Dubbelen tot 20", SkillCategory.PATTERNS, 1, 3, false),
        Skill("patterns_halves", "Helften tot 20", "Helften van even getallen tot 20", SkillCategory.PATTERNS, 1, 3, false),
        Skill("patterns_count_2", "Tellen per 2", "Tellen met sprongen van 2", SkillCategory.PATTERNS, 2, 4, true),
        Skill("patterns_count_5", "Tellen per 5", "Tellen met sprongen van 5", SkillCategory.PATTERNS, 2, 4, true),
        Skill("patterns_count_10", "Tellen per 10", "Tellen met sprongen van 10", SkillCategory.PATTERNS, 2, 4, true),
        
        // ADVANCED - PREMIUM
        Skill("advanced_compare_100", "Vergelijken tot 100", "Getallen vergelijken tot 100", SkillCategory.ADVANCED, 3, 5, true),
        Skill("advanced_place_value", "Tientallen en eenheden", "Tientallen en eenheden herkennen", SkillCategory.ADVANCED, 3, 5, true),
        Skill("advanced_groups", "Vermenigvuldigen als groepjes", "Groepjes tellen", SkillCategory.ADVANCED, 3, 5, true),
        Skill("advanced_table_2", "Tafel van 2", "Tafel van 2", SkillCategory.ADVANCED, 3, 5, true),
        Skill("advanced_table_5", "Tafel van 5", "Tafel van 5", SkillCategory.ADVANCED, 3, 5, true),
        Skill("advanced_table_10", "Tafel van 10", "Tafel van 10", SkillCategory.ADVANCED, 3, 5, true)
    )
    
    fun getById(id: String): Skill? = ALL_SKILLS.find { it.id == id }
    fun getFreeSkills(): List<Skill> = ALL_SKILLS.filter { !it.isPremium }
    fun getPremiumSkills(): List<Skill> = ALL_SKILLS.filter { it.isPremium }
}