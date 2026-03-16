package com.rekenrinkel.domain.model

import java.io.Serializable
import java.util.UUID

/**
 * Leeftijdsgroepen voor startniveau bepaling
 */
enum class AgeGroup(val minAge: Int, val maxAge: Int, val displayName: String) {
    AGE_5_6(5, 6, "5-6 jaar"),
    AGE_6_7(6, 7, "6-7 jaar"),
    AGE_7_8(7, 8, "7-8 jaar"),
    AGE_8_9(8, 9, "8-9 jaar"),
    AGE_9_10(9, 10, "9-10 jaar"),
    AGE_10_11(10, 11, "10-11 jaar");

    companion object {
        fun fromAge(age: Int): AgeGroup {
            return when (age) {
                in 5..6 -> AGE_5_6
                in 7..8 -> AGE_7_8
                in 9..10 -> AGE_9_10
                else -> if (age < 5) AGE_5_6 else AGE_10_11
            }
        }

        fun getStartSkills(ageGroup: AgeGroup): List<String> {
            return when (ageGroup) {
                AGE_5_6 -> listOf(
                    "foundation_subitize_5",
                    "foundation_counting",
                    "foundation_more_less",
                    "foundation_number_bonds_5"
                )
                AGE_6_7 -> listOf(
                    "foundation_number_bonds_10",
                    "arithmetic_add_10",
                    "arithmetic_sub_10",
                    "patterns_doubles"
                )
                AGE_7_8 -> listOf(
                    "foundation_number_bonds_20",
                    "arithmetic_add_20",
                    "arithmetic_sub_20",
                    "arithmetic_bridge_add"
                )
                AGE_8_9 -> listOf(
                    "patterns_count_2",
                    "patterns_count_5",
                    "patterns_count_10",
                    "advanced_compare_100"
                )
                AGE_9_10, AGE_10_11 -> listOf(
                    "advanced_groups",
                    "advanced_table_2",
                    "advanced_table_5",
                    "advanced_table_10",
                    "advanced_fractions_basics"
                )
            }
        }
    }
}

/**
 * Uitgebreide skill progress met mastery tracking
 */
data class SkillProgress(
    val skillId: String,
    val masteryScore: Int = 0, // 0-100
    val currentDifficultyTier: Int = 1, // 1-5
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val streakCorrect: Int = 0,
    val streakIncorrect: Int = 0,
    val averageResponseTimeMs: Long = 0,
    val lastPracticedAt: Long? = null,
    val lastRepresentationUsed: String? = null,
    val errorTypeSummary: Map<String, Int> = emptyMap(), // Type -> count
    val isUnlocked: Boolean = false,
    val masteredAt: Long? = null
) {
    fun totalAttempts(): Int = correctCount + incorrectCount
    fun successRate(): Float = if (totalAttempts() > 0) correctCount.toFloat() / totalAttempts() else 0f
    fun masteryLevel(): MasteryLevel = masteryScore.toMasteryLevel()
    fun isMastered(): Boolean = masteryScore >= 90

    /**
     * Bereken difficulty adjustment op basis van recente prestaties
     * @return +1 (moeilijker), -1 (makkelijker), of 0 (gelijk)
     */
    fun calculateDifficultyAdjustment(): Int {
        return when {
            streakCorrect >= 3 -> 1
            streakIncorrect >= 2 -> -1
            else -> 0
        }
    }
}

/**
 * Rewards systeem
 */
data class Rewards(
    val totalXp: Int = 0,
    val currentLevel: Int = 1,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastSessionDate: Long? = null,
    val badges: List<Badge> = emptyList(),
    val unlockedSkills: List<String> = emptyList(),
    val personalBests: Map<String, Long> = emptyMap() // SkillId -> best response time
) : Serializable {
    fun xpForNextLevel(): Int = currentLevel * 150
    fun xpProgress(): Float = (totalXp % xpForNextLevel()).toFloat() / xpForNextLevel()

    fun updateStreak(): Rewards {
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        val lastDay = lastSessionDate?.let { it / (24 * 60 * 60 * 1000) } ?: 0

        return when {
            today == lastDay -> this // Same day, no change
            today == lastDay + 1 -> { // Consecutive day
                val newStreak = currentStreak + 1
                copy(
                    currentStreak = newStreak,
                    longestStreak = maxOf(longestStreak, newStreak),
                    lastSessionDate = System.currentTimeMillis()
                )
            }
            else -> { // Streak broken
                copy(
                    currentStreak = 1,
                    lastSessionDate = System.currentTimeMillis()
                )
            }
        }
    }

    fun addXp(amount: Int): Rewards {
        val newTotal = totalXp + amount
        val newLevel = (newTotal / 150) + 1
        return copy(
            totalXp = newTotal,
            currentLevel = newLevel
        )
    }

    fun addBadge(badge: Badge): Rewards {
        return if (badges.none { it.id == badge.id }) {
            copy(badges = badges + badge)
        } else this
    }
}

/**
 * Badge definitie
 */
data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val earnedAt: Long = System.currentTimeMillis()
) : Serializable

/**
 * Prestatie types voor badges
 */
enum class BadgeType {
    SKILL_MASTERED,      // Een skill volledig mastered
    SKILL_STREAK,        // X correct op een skill
    SPEED_DEMON,         // Snel antwoord
    PERFECT_SESSION,     // 100% score in sessie
    DAILY_STREAK,        // X dagen achter elkaar
    TOTAL_XP,           // X totale XP
    CHALLENGE_COMPLETED  // Speciale uitdaging
}

/**
 * Uitgebreid profiel met leeftijd en rewards
 */
data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val age: Int = 6,
    val theme: Theme = Theme.DINOSAURS,
    val createdAt: Long = System.currentTimeMillis(),
    val rewards: Rewards = Rewards(),
    val settings: UserSettings = UserSettings()
) : Serializable

/**
 * Gebruikersinstellingen
 */
data class UserSettings(
    val soundEnabled: Boolean = true,
    val einkModeEnabled: Boolean = false,
    val dailyReminderEnabled: Boolean = true,
    val preferredRepresentation: RepresentationType = RepresentationType.AUTO
) : Serializable

enum class RepresentationType {
    AUTO,      // Systeem kiest op basis van moeilijkheid
    DOTS,      // Stippen (meest concreet)
    BLOCKS,    // Blokjes
    NUMBER_LINE, // Getallenlijn
    GROUPS,    // Groepjes
    NUMBER_BONDS, // Number bonds
    ABSTRACT   // Kale getallen
}

/**
 * Les structuur fases
 */
enum class LessonPhase {
    WARM_UP,      // 2 makkelijke review items
    FOCUS,        // 4-6 items van kernskill
    CHALLENGE,    // 1-2 moeilijkere/nieuwe items
    EXIT_CHECK    // Bepaalt mastery update
}

/**
 * Les configuratie
 */
data class LessonConfig(
    val phase: LessonPhase,
    val targetSkillId: String,
    val itemCount: Int,
    val difficultyOverride: Int? = null
)

/**
 * Extended exercise result met meer metadata
 */
data class DetailedExerciseResult(
    val exerciseId: String,
    val skillId: String,
    val isCorrect: Boolean,
    val responseTimeMs: Long,
    val givenAnswer: String,
    val correctAnswer: String,
    val difficultyTier: Int,
    val representationUsed: String,
    val errorType: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

// Backwards compatibility aliases verwijderd - gebruik expliciet UserProfile en DetailedExerciseResult