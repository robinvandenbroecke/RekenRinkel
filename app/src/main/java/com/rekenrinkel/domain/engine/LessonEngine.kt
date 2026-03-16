package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.content.ContentRepository
import com.rekenrinkel.domain.model.*
import com.rekenrinkel.data.repository.ProgressRepository
import kotlinx.coroutines.flow.first

/**
 * Verbeterde SessionEngine met lesson phases en mastery-based adaptiviteit.
 * 
 * Lesson structuur:
 * 1. Warm-up: 2 makkelijke review items
 * 2. Focus: 4-6 items van kernskill (50%)
 * 3. Challenge: 1-2 moeilijkere items (20%)
 * 4. Exit check: bepaalt mastery update
 * 
 * Mix: 50% focus, 30% review, 20% challenge
 */
class LessonEngine(
    private val exerciseEngine: ExerciseEngine,
    private val progressRepository: ProgressRepository
) {
    companion object {
        const val WARM_UP_COUNT = 2
        const val FOCUS_COUNT = 4      // 50% van 8
        const val REVIEW_COUNT = 2     // ~25% van 8  
        const val CHALLENGE_COUNT = 2  // ~25% van 8
        const val TOTAL_LESSON_SIZE = 10

        // Mastery drempels
        const val MASTERY_THRESHOLD_EMERGING = 25
        const val MASTERY_THRESHOLD_PRACTICING = 50
        const val MASTERY_THRESHOLD_SOLID = 75
        const val MASTERY_THRESHOLD_MASTERED = 90

        // Streaks voor difficulty adjustment
        const val STREAK_FOR_DIFFICULTY_UP = 3
        const val STREAK_FOR_DIFFICULTY_DOWN = 2

        // XP waarden
        const val XP_PER_CORRECT = 10
        const val XP_PER_CORRECT_FAST = 15      // < 3 seconden
        const val XP_STREAK_BONUS = 5
        const val XP_MASTERED_BONUS = 25
    }

    /**
     * PATCH 4: Remediëringsinformatie per fouttype
     */
    data class RemediationInfo(
        val errorType: com.rekenrinkel.domain.content.ErrorType,
        val fallbackRepresentation: com.rekenrinkel.domain.content.RepresentationType,
        val remediationSkill: String,
        val hint: String
    )

    /**
     * PATCH 4: Bepaal remediëring op basis van fouttype
     */
    fun getRemediationForError(errorType: com.rekenrinkel.domain.content.ErrorType): RemediationInfo {
        return when (errorType) {
            com.rekenrinkel.domain.content.ErrorType.COUNTING_ERROR -> RemediationInfo(
                errorType = errorType,
                fallbackRepresentation = com.rekenrinkel.domain.content.RepresentationType.DOTS,
                remediationSkill = "foundation_subitize_5",
                hint = "Tel langzaam en wijs naar elk object"
            )
            com.rekenrinkel.domain.content.ErrorType.BOND_ERROR -> RemediationInfo(
                errorType = errorType,
                fallbackRepresentation = com.rekenrinkel.domain.content.RepresentationType.BOND_MODEL,
                remediationSkill = "foundation_number_bonds_10",
                hint = "Welke twee getallen maken samen 10?"
            )
            com.rekenrinkel.domain.content.ErrorType.COMPARE_ERROR -> RemediationInfo(
                errorType = errorType,
                fallbackRepresentation = com.rekenrinkel.domain.content.RepresentationType.NUMBER_LINE,
                remediationSkill = "foundation_more_less",
                hint = "Welk getal staat verder op de getallenlijn?"
            )
            com.rekenrinkel.domain.content.ErrorType.BRIDGE_10_ERROR -> RemediationInfo(
                errorType = errorType,
                fallbackRepresentation = com.rekenrinkel.domain.content.RepresentationType.BOND_MODEL,
                remediationSkill = "foundation_number_bonds_10",
                hint = "Splits om over 10 heen te komen: 8 + 5 = 8 + 2 + 3 = 10 + 3"
            )
            com.rekenrinkel.domain.content.ErrorType.GROUPING_ERROR -> RemediationInfo(
                errorType = errorType,
                fallbackRepresentation = com.rekenrinkel.domain.content.RepresentationType.ARRAY,
                remediationSkill = "advanced_groups",
                hint = "Maak gelijke groepjes of teken een rooster"
            )
            com.rekenrinkel.domain.content.ErrorType.PLACE_VALUE_ERROR -> RemediationInfo(
                errorType = errorType,
                fallbackRepresentation = com.rekenrinkel.domain.content.RepresentationType.BLOCKS,
                remediationSkill = "advanced_place_value",
                hint = "Hoeveel tientallen en hoeveel eenheden?"
            )
            com.rekenrinkel.domain.content.ErrorType.SEQUENCE_ERROR -> RemediationInfo(
                errorType = errorType,
                fallbackRepresentation = com.rekenrinkel.domain.content.RepresentationType.NUMBER_LINE,
                remediationSkill = "patterns_count_2",
                hint = "Gebruik de getallenlijn om de sprong te zien"
            )
            else -> RemediationInfo(
                errorType = errorType,
                fallbackRepresentation = com.rekenrinkel.domain.content.RepresentationType.BLOCKS,
                remediationSkill = "foundation_counting",
                hint = "Probeer het nog eens stap voor stap"
            )
        }
    }

    /**
     * PATCH 4: Genereer remediëringsoefening na fout
     */
    fun generateRemediationExercise(
        originalSkillId: String,
        errorType: com.rekenrinkel.domain.content.ErrorType,
        difficulty: Int
    ): Exercise {
        val remediation = getRemediationForError(errorType)
        
        // Genereer worked example voor remediëring
        return exerciseEngine.generateWorkedExample(
            skillId = originalSkillId,
            difficulty = maxOf(1, difficulty - 1)  // Iets makkelijker
        ).copy(
            hint = remediation.hint,
            isRemediation = true
        )
    }

    /**
     * Bouw een complete les met alle phases
     */
    suspend fun buildLesson(
        userProfile: UserProfile,
        isPremiumUnlocked: Boolean = false
    ): LessonPlan {
        val allProgress = progressRepository.getAllProgress().first()
        val progressMap = allProgress.associateBy { it.skillId }

        // Bepaal beschikbare skills op basis van leeftijd en voortgang
        val availableSkills = getAvailableSkills(progressMap, userProfile.age)
        
        val accessibleConfigs = if (isPremiumUnlocked) {
            ContentRepository.getAllConfigs()
        } else {
            ContentRepository.getFreeConfigs()
        }.filter { config ->
            availableSkills.any { it.id == config.skillId }
        }

        val accessibleSkills = accessibleConfigs.map { it.toSkill() }

        // Bepaal focus skill (waar de les om draait)
        val focusSkill = determineFocusSkill(accessibleSkills, progressMap, userProfile)

        // 1. Warm-up: makkelijke review (bekende skills, difficulty 1)
        val warmUpExercises = buildWarmUp(
            accessibleSkills, progressMap, focusSkill, WARM_UP_COUNT
        )

        // 2. Focus: 50% van kernskill
        val focusExercises = buildFocusBlock(
            focusSkill, progressMap, FOCUS_COUNT
        )

        // 3. Review: 30% spaced review van zwakke/oude skills
        val reviewExercises = buildReviewBlock(
            accessibleSkills, progressMap, focusSkill, REVIEW_COUNT
        )

        // 4. Challenge: 20% moeilijker of nieuw
        val challengeExercises = buildChallengeBlock(
            accessibleSkills, progressMap, focusSkill, CHALLENGE_COUNT
        )

        // Combineer alle phases
        val allExercises = warmUpExercises + focusExercises + reviewExercises + challengeExercises

        // Smart shuffle met variatie garantie
        val shuffledExercises = smartShuffleWithVariation(allExercises)

        return LessonPlan(
            exercises = shuffledExercises,
            focusSkillId = focusSkill.id,
            warmUpCount = warmUpExercises.size,
            focusCount = focusExercises.size,
            reviewCount = reviewExercises.size,
            challengeCount = challengeExercises.size
        )
    }

    /**
     * Verwerk resultaat van een oefening en update mastery
     */
    suspend fun processExerciseResult(
        result: DetailedExerciseResult,
        currentProgress: SkillProgress
    ): ExerciseOutcome {
        val isCorrect = result.isCorrect
        val responseTimeMs = result.responseTimeMs

        // Update streaks
        val newStreakCorrect = if (isCorrect) currentProgress.streakCorrect + 1 else 0
        val newStreakIncorrect = if (!isCorrect) currentProgress.streakIncorrect + 1 else 0

        // Bereken difficulty adjustment
        val difficultyAdjustment = when {
            newStreakCorrect >= STREAK_FOR_DIFFICULTY_UP -> 1
            newStreakIncorrect >= STREAK_FOR_DIFFICULTY_DOWN -> -1
            else -> 0
        }

        val newDifficultyTier = (currentProgress.currentDifficultyTier + difficultyAdjustment)
            .coerceIn(1, 5)

        // Update mastery score (0-100)
        val masteryDelta = if (isCorrect) 5 else -3
        val newMasteryScore = (currentProgress.masteryScore + masteryDelta)
            .coerceIn(0, 100)

        // Bereken XP
        val baseXp = if (isCorrect) XP_PER_CORRECT else 0
        val speedBonus = if (isCorrect && responseTimeMs < 3000) XP_PER_CORRECT_FAST - XP_PER_CORRECT else 0
        val streakBonus = if (newStreakCorrect >= 3) XP_STREAK_BONUS else 0
        val masteredBonus = if (newMasteryScore >= MASTERY_THRESHOLD_MASTERED && currentProgress.masteryScore < MASTERY_THRESHOLD_MASTERED) {
            XP_MASTERED_BONUS
        } else 0

        val totalXp = baseXp + speedBonus + streakBonus + masteredBonus

        // Update error type summary
        val newErrorSummary = if (!isCorrect && result.errorType != null) {
            currentProgress.errorTypeSummary.toMutableMap().apply {
                put(result.errorType, getOrDefault(result.errorType, 0) + 1)
            }
        } else currentProgress.errorTypeSummary

        // Check voor mastered
        val isNowMastered = newMasteryScore >= MASTERY_THRESHOLD_MASTERED
        val masteredAt = if (isNowMastered && currentProgress.masteredAt == null) {
            System.currentTimeMillis()
        } else currentProgress.masteredAt

        val updatedProgress = currentProgress.copy(
            masteryScore = newMasteryScore,
            currentDifficultyTier = newDifficultyTier,
            correctCount = currentProgress.correctCount + if (isCorrect) 1 else 0,
            incorrectCount = currentProgress.incorrectCount + if (!isCorrect) 1 else 0,
            streakCorrect = newStreakCorrect,
            streakIncorrect = newStreakIncorrect,
            lastPracticedAt = System.currentTimeMillis(),
            lastRepresentationUsed = result.representationUsed,
            errorTypeSummary = newErrorSummary,
            masteredAt = masteredAt
        )

        return ExerciseOutcome(
            updatedProgress = updatedProgress,
            xpEarned = totalXp,
            difficultyChanged = difficultyAdjustment != 0,
            newDifficultyTier = newDifficultyTier,
            isMastered = isNowMastered,
            isNewlyMastered = isNowMastered && currentProgress.masteredAt == null
        )
    }

    /**
     * Check of er nieuwe badges unlocked zijn
     */
    fun checkBadges(
        outcome: ExerciseOutcome,
        currentRewards: Rewards,
        skillName: String
    ): List<Badge> {
        val newBadges = mutableListOf<Badge>()

        // Skill mastered badge
        if (outcome.isNewlyMastered) {
            newBadges.add(Badge(
                id = "mastered_${outcome.updatedProgress.skillId}",
                name = "$skillName Meester",
                description = "Je hebt $skillName volledig beheerst!",
                icon = "🏆"
            ))
        }

        // Streak badge
        if (outcome.updatedProgress.streakCorrect >= 10 && 
            currentRewards.badges.none { it.id == "streak_10" }) {
            newBadges.add(Badge(
                id = "streak_10",
                name = "Hot Streak",
                description = "10 juiste antwoorden op een rij!",
                icon = "🔥"
            ))
        }

        // Speed badge
        if (outcome.xpEarned >= XP_PER_CORRECT_FAST &&
            currentRewards.badges.none { it.id == "speed_demon" }) {
            newBadges.add(Badge(
                id = "speed_demon",
                name = "Snelheidsgans",
                description = "Snel antwoord gegeven!",
                icon = "⚡"
            ))
        }

        return newBadges
    }

    // ============ PRIVATE HELPERS ============

    private suspend fun getAvailableSkills(
        progressMap: Map<String, SkillProgress>,
        userAge: Int
    ): List<Skill> {
        // Start met leeftijd-gebaseerde skills
        val ageGroup = AgeGroup.fromAge(userAge)
        val startSkills = AgeGroup.getStartSkills(ageGroup)

        // Unlock skills gebaseerd op prerequisites
        val unlockedSkills = mutableSetOf<String>()
        unlockedSkills.addAll(startSkills)

        // Voeg skills toe waarvan prerequisites behaald zijn
        val allConfigs = ContentRepository.getAllConfigs()
        var changed = true
        while (changed) {
            changed = false
            allConfigs.forEach { config ->
                if (config.skillId !in unlockedSkills) {
                    val prerequisitesMet = config.prerequisites.all { prereq ->
                        prereq in unlockedSkills || progressMap[prereq]?.masteryScore?.let { it >= 50 } == true
                    }
                    if (prerequisitesMet) {
                        unlockedSkills.add(config.skillId)
                        changed = true
                    }
                }
            }
        }

        return unlockedSkills.mapNotNull { ContentRepository.getConfig(it)?.toSkill() }
    }

    private fun determineFocusSkill(
        availableSkills: List<Skill>,
        progressMap: Map<String, SkillProgress>,
        userProfile: UserProfile
    ): Skill {
        // Prioriteit: emerging/practicing skills, dan not_learned, dan solid voor review
        val candidates = availableSkills.filter { skill ->
            val progress = progressMap[skill.id]
            progress?.masteryScore?.let { it in 10..85 } == true || progress == null
        }

        return candidates.minByOrNull { skill ->
            val progress = progressMap[skill.id]
            // Prioriteit: lager mastery, langer geleden geoefend
            val masteryScore = progress?.masteryScore ?: 0
            val daysSincePractice = progress?.lastPracticedAt?.let {
                (System.currentTimeMillis() - it) / (24 * 60 * 60 * 1000)
            } ?: 30
            masteryScore - (daysSincePractice * 2)
        } ?: availableSkills.firstOrNull() ?: getDefaultSkill()
    }

    private fun buildWarmUp(
        accessibleSkills: List<Skill>,
        progressMap: Map<String, SkillProgress>,
        focusSkill: Skill,
        count: Int
    ): List<Exercise> {
        // Makkelijke, bekende skills (mastered of solid)
        val masteredSkills = accessibleSkills.filter { skill ->
            val progress = progressMap[skill.id]
            progress?.masteryScore?.let { it >= MASTERY_THRESHOLD_SOLID } == true &&
            skill.id != focusSkill.id
        }.shuffled()

        return (0 until count).map { index ->
            val skill = masteredSkills.getOrNull(index) ?: focusSkill
            // Warm-up altijd difficulty 1 (makkelijk)
            exerciseEngine.generateExercise(skill.id, 1)
        }
    }

    private fun buildFocusBlock(
        focusSkill: Skill,
        progressMap: Map<String, SkillProgress>,
        count: Int
    ): List<Exercise> {
        val progress = progressMap[focusSkill.id]
        val difficulty = progress?.currentDifficultyTier ?: 1
        
        // PATCH 3: CPA-overgangen afdwingen
        val config = ContentRepository.getConfig(focusSkill.id)
        val skillCpaPhase = config?.cpaPhase ?: com.rekenrinkel.domain.content.CpaPhase.CONCRETE
        val currentCpaPhase = progress?.currentCpaPhase ?: com.rekenrinkel.domain.content.CpaPhase.CONCRETE
        
        // Bepaal toegestane CPA-fase op basis van mastery
        val allowedCpaPhase = determineAllowedCpaPhase(currentCpaPhase, progress)
        
        // Gebruik de strengere van skill-fase en toegestane fase
        val effectiveCpaPhase = minOf(skillCpaPhase, allowedCpaPhase)

        // DIDACTISCHE STRUCTUUR: worked example -> guided -> independent
        // Aangepast voor CPA-fase
        return when (effectiveCpaPhase) {
            com.rekenrinkel.domain.content.CpaPhase.CONCRETE -> {
                // Alleen concrete oefeningen, veel scaffolding
                listOf(
                    exerciseEngine.generateWorkedExample(focusSkill.id, difficulty)
                ) + (1 until count).map {
                    exerciseEngine.generateGuidedExercise(focusSkill.id, difficulty)
                }
            }
            com.rekenrinkel.domain.content.CpaPhase.PICTORIAL -> {
                // Picturale oefeningen, mix van guided en independent
                listOf(
                    exerciseEngine.generateGuidedExercise(focusSkill.id, difficulty)
                ) + (1 until count).map {
                    exerciseEngine.generateExercise(focusSkill.id, difficulty)
                }
            }
            com.rekenrinkel.domain.content.CpaPhase.ABSTRACT,
            com.rekenrinkel.domain.content.CpaPhase.MIXED_TRANSFER -> {
                // Abstract, voornamelijk independent
                if (progress == null || progress.totalAttempts() < 3) {
                    listOf(
                        exerciseEngine.generateGuidedExercise(focusSkill.id, difficulty)
                    ) + (1 until count).map {
                        exerciseEngine.generateExercise(focusSkill.id, difficulty)
                    }
                } else {
                    (0 until count).map {
                        exerciseEngine.generateExercise(focusSkill.id, difficulty)
                    }
                }
            }
        }
    }
    
    /**
     * PATCH 3: Bepaal welke CPA-fase toegestaan is op basis van mastery
     * CONCRETE nodig voor PICTORIAL, PICTORIAL nodig voor ABSTRACT
     */
    private fun determineAllowedCpaPhase(
        currentPhase: com.rekenrinkel.domain.content.CpaPhase,
        progress: SkillProgress?
    ): com.rekenrinkel.domain.content.CpaPhase {
        if (progress == null) return com.rekenrinkel.domain.content.CpaPhase.CONCRETE
        
        val mastery = progress.masteryScore
        val attempts = progress.totalAttempts()
        
        return when (currentPhase) {
            com.rekenrinkel.domain.content.CpaPhase.CONCRETE -> {
                // Naar PICTORIAL als voldoende mastery in CONCRETE
                if (mastery >= 60 && attempts >= 5) {
                    com.rekenrinkel.domain.content.CpaPhase.PICTORIAL
                } else {
                    com.rekenrinkel.domain.content.CpaPhase.CONCRETE
                }
            }
            com.rekenrinkel.domain.content.CpaPhase.PICTORIAL -> {
                // Naar ABSTRACT als voldoende mastery in PICTORIAL
                if (mastery >= 75 && attempts >= 8) {
                    com.rekenrinkel.domain.content.CpaPhase.ABSTRACT
                } else {
                    com.rekenrinkel.domain.content.CpaPhase.PICTORIAL
                }
            }
            com.rekenrinkel.domain.content.CpaPhase.ABSTRACT,
            com.rekenrinkel.domain.content.CpaPhase.MIXED_TRANSFER -> {
                // MIXED_TRANSFER na voldoende abstracte beheersing
                if (mastery >= 85) {
                    com.rekenrinkel.domain.content.CpaPhase.MIXED_TRANSFER
                } else {
                    com.rekenrinkel.domain.content.CpaPhase.ABSTRACT
                }
            }
        }
    }

    private fun buildReviewBlock(
        accessibleSkills: List<Skill>,
        progressMap: Map<String, SkillProgress>,
        focusSkill: Skill,
        count: Int
    ): List<Exercise> {
        // Review: zwakke skills (emerging, practicing) en spaced review (mastered maar lang geleden)
        val reviewCandidates = accessibleSkills.filter { skill ->
            skill.id != focusSkill.id
        }.sortedByDescending { skill ->
            val progress = progressMap[skill.id]
            when {
                progress == null -> 0
                progress.masteryScore in 25..74 -> 100 // Practicing = hoge prioriteit
                progress.masteryScore < 25 -> 80        // Emerging
                progress.masteryScore >= 90 -> {        // Mastered maar lang geleden?
                    val daysSince = (System.currentTimeMillis() - (progress.lastPracticedAt ?: 0)) / (24 * 60 * 60 * 1000)
                    if (daysSince > 7) 60 else 20
                }
                else -> 40
            }
        }.take(count * 2) // Meer kandidaten voor variatie

        return (0 until count).map { index ->
            val skill = reviewCandidates.getOrNull(index) ?: focusSkill
            val progress = progressMap[skill.id]
            val difficulty = (progress?.currentDifficultyTier ?: 1).coerceAtMost(3) // Review niet te moeilijk
            exerciseEngine.generateExercise(skill.id, difficulty)
        }
    }

    private fun buildChallengeBlock(
        accessibleSkills: List<Skill>,
        progressMap: Map<String, SkillProgress>,
        focusSkill: Skill,
        count: Int
    ): List<Exercise> {
        // Challenge: iets moeilijker dan huidig niveau, of net unlocked skill
        val challengeCandidates = accessibleSkills.filter { skill ->
            val progress = progressMap[skill.id]
            progress?.masteryScore?.let { it in 50..85 } == true || // Solid maar niet mastered
            (progress == null && skill.prerequisites.isNotEmpty()) // Net unlocked
        }.shuffled()

        return (0 until count).map { index ->
            val skill = challengeCandidates.getOrNull(index) ?: focusSkill
            val progress = progressMap[skill.id]
            // Challenge = +1 difficulty
            val difficulty = ((progress?.currentDifficultyTier ?: 1) + 1).coerceIn(1, 5)
            exerciseEngine.generateExercise(skill.id, difficulty)
        }
    }

    private fun smartShuffleWithVariation(exercises: List<Exercise>): List<Exercise> {
        if (exercises.size <= 3) return exercises.shuffled()

        val result = mutableListOf<Exercise>()
        val remaining = exercises.toMutableList()

        while (remaining.isNotEmpty()) {
            // Zoek een oefening die verschilt van laatste 2
            val lastTwo = result.takeLast(2)
            val candidate = remaining.find { exercise ->
                lastTwo.none { last ->
                    last.skillId == exercise.skillId &&
                    last.correctAnswer == exercise.correctAnswer
                }
            } ?: remaining.first()

            result.add(candidate)
            remaining.remove(candidate)
        }

        return result
    }

    private fun getDefaultSkill(): Skill {
        return ContentRepository.getConfig("foundation_subitize_5")?.toSkill()
            ?: throw IllegalStateException("No default skill available")
    }

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

/**
 * Complete les planning
 */
data class LessonPlan(
    val exercises: List<Exercise>,
    val focusSkillId: String,
    val warmUpCount: Int,
    val focusCount: Int,
    val reviewCount: Int,
    val challengeCount: Int
)

/**
 * Uitkomst van een enkele oefening
 */
data class ExerciseOutcome(
    val updatedProgress: SkillProgress,
    val xpEarned: Int,
    val difficultyChanged: Boolean,
    val newDifficultyTier: Int,
    val isMastered: Boolean,
    val isNewlyMastered: Boolean
)