package com.rekenrinkel.domain.model

import com.rekenrinkel.domain.content.ContentRepository
import java.io.Serializable
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
    val age: Int = 6,
    val theme: Theme = Theme.DINOSAURS,
    val createdAt: Long = System.currentTimeMillis(),
    // Rewards fields
    val currentLevel: Int = 1,
    val totalXp: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastSessionDate: Long? = null,
    // Placement fields
    val placementCompleted: Boolean = false,
    val startingBand: StartingBand = StartingBand.FOUNDATION
) : Serializable {
    fun xpForNextLevel(): Int = currentLevel * 150
    fun xpProgress(): Float = (totalXp % xpForNextLevel()).toFloat() / xpForNextLevel()
}

/**
 * Startniveau banden gebaseerd op leeftijd en placement
 */
enum class StartingBand {
    FOUNDATION,        // 5-6 jaar: subitizing, telling, number bonds 5
    EARLY_ARITHMETIC,  // 6-8 jaar: optellen/aftrekken tot 20, brug over 10
    EXTENDED           // 8-11 jaar: vermenigvuldigen, breuken, redeneren
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

// SkillProgress is nu gedefinieerd in EnhancedModels.kt met uitgebreide velden:
// - correctCount (was: correctAnswers)
// - incorrectCount (was: wrongAnswers)
// - lastPracticedAt (was: lastPracticed)
// - currentDifficultyTier (was: currentDifficulty)
// + streakCorrect, streakIncorrect, errorTypeSummary, isUnlocked, masteredAt

/**
 * Oefentypes
 */
enum class ExerciseType : Serializable {
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
) : Serializable

/**
 * Visuele data voor oefeningen
 */
data class VisualData(
    val type: VisualType,
    val count: Int? = null,
    val groups: List<Int>? = null,
    val firstNumber: Int? = null,
    val secondNumber: Int? = null
) : Serializable

enum class VisualType : Serializable {
    DOTS,
    BLOCKS,
    GROUPS,
    NUMBER_LINE,
    COMPARISON
}

/**
 * Resultaat van een individuele oefening
 */
data class ExerciseResult(
    val exerciseId: String,
    val skillId: String,
    val isCorrect: Boolean,
    val responseTimeMs: Long,
    val givenAnswer: String
) : Serializable

/**
 * Sessie resultaat
 */
data class SessionResult(
    val sessionId: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val exercises: List<ExerciseResult> = emptyList(),
    val xpEarned: Int = 0,
    val stars: Int = 0,
    val averageResponseTimeMs: Long = 0
) : Serializable {
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
 * Skill definitions - gebruikt ContentRepository als bron
 * Deprecated: Gebruik ContentRepository voor nieuwe code
 */
object SkillDefinitions {
    
    /**
     * Alle skills - gedelegeerd naar ContentRepository
     */
    val ALL_SKILLS: List<Skill>
        get() = ContentRepository.getAllConfigs().map { it.toSkill() }
    
    /**
     * Haal skill op basis van ID
     */
    fun getById(id: String): Skill? {
        return ContentRepository.getConfig(id)?.toSkill()
    }
    
    /**
     * Haal alle gratis skills op
     */
    fun getFreeSkills(): List<Skill> {
        return ContentRepository.getFreeConfigs().map { it.toSkill() }
    }
    
    /**
     * Haal alle premium skills op
     */
    fun getPremiumSkills(): List<Skill> {
        return ContentRepository.getPremiumConfigs().map { it.toSkill() }
    }
    
    /**
     * Converteer ContentConfig naar Skill
     */
    private fun com.rekenrinkel.domain.content.SkillContentConfig.toSkill(): Skill {
        return Skill(
            id = this.skillId,
            name = this.name,
            description = this.description,
            category = this.category,
            minDifficulty = this.minDifficulty,
            maxDifficulty = this.maxDifficulty,
            isPremium = this.isPremium,
            prerequisites = this.prerequisites
        )
    }
}