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
        // PATCH 6: Kortere, curriculumgestuurde lessen (5-10 min)
        const val WARM_UP_COUNT = 1
        const val FOCUS_COUNT = 4      // Kernfocus
        const val REVIEW_COUNT = 2     // Spaced review
        const val CHALLENGE_COUNT = 1  // Challenge/transfer
        const val TOTAL_LESSON_SIZE = 8  // 6-12 items range

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
        
        // PATCH 4: Adaptiviteit parameters - leeftijd start, prestaties sturen
        const val FAST_RESPONSE_THRESHOLD_MS = 3000  // < 3s = snel
        const val SLOW_RESPONSE_THRESHOLD_MS = 8000  // > 8s = traag
        const val MIN_ATTEMPTS_FOR_CPA_ADVANCE = 5   // Minstens 5 pogingen nodig
    }

    /**
     * Bepaal warm-up, focus en review counts op basis van leeftijd.
     * Returns Triple(warmUp, focus, review).
     */
    private fun getSessionCounts(age: Int): Triple<Int, Int, Int> {
        return when {
            age <= 6 -> Triple(2, 3, 1)   // 6 total
            age <= 8 -> Triple(2, 5, 2)   // 9 total
            else -> Triple(3, 6, 3)       // 12 total
        }
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
     * Sessielengte is leeftijdsafhankelijk (research-based):
     * - Age 5-6: 6 exercises (2 warm-up, 3 focus, 1 review)
     * - Age 7-8: 9 exercises (2 warm-up, 5 focus, 2 review)
     * - Age 9-11: 12 exercises (3 warm-up, 6 focus, 3 review)
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

        // Leeftijdsafhankelijke sessielengte
        val (warmUpCount, focusCount, reviewCount) = getSessionCounts(userProfile.age)

        // 1. Warm-up: makkelijke review (bekende skills, difficulty 1)
        val warmUpExercises = buildWarmUp(
            accessibleSkills, progressMap, focusSkill, warmUpCount
        )

        // 2. Focus: kernskill
        val focusExercises = buildFocusBlock(
            focusSkill, progressMap, focusCount
        )

        // 3. Review: spaced review van zwakke/oude skills
        val reviewExercises = buildReviewBlock(
            accessibleSkills, progressMap, focusSkill, reviewCount
        )

        // 4. Challenge: geen apart challenge blok meer, zit in focus/review
        val challengeExercises = emptyList<Exercise>()

        // Combineer alle phases
        // PATCH 1 & 2: Block-aware shuffle - behoud didactische volgorde
        // Shuffle per block, maar behoud volgorde binnen blocks
        val shuffledWarmUp = warmUpExercises.shuffled()
        val shuffledReview = reviewExercises.shuffled()
        val shuffledChallenge = challengeExercises.shuffled()
        
        // Focus block: worked example blijft eerst, rest wordt gehusseld
        val workedExample = focusExercises.firstOrNull { it.type == ExerciseType.WORKED_EXAMPLE }
        val otherFocus = focusExercises.filterNot { it.type == ExerciseType.WORKED_EXAMPLE }.shuffled()
        val shuffledFocus = if (workedExample != null) {
            listOf(workedExample) + otherFocus
        } else {
            focusExercises.shuffled()
        }
        
        val allExercises = shuffledWarmUp + shuffledFocus + shuffledReview + shuffledChallenge

        return LessonPlan(
            exercises = allExercises,
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
    /**
     * PATCH 4 & 5: Verwerk resultaat met adaptieve CPA en difficulty sturing
     * Prestaties overrulen leeftijd - snelle correcte antwoorden = sneller door
     */
    suspend fun processExerciseResult(
        result: DetailedExerciseResult,
        currentProgress: SkillProgress
    ): ExerciseOutcome {
        val isCorrect = result.isCorrect
        val responseTimeMs = result.responseTimeMs
        val isFast = responseTimeMs < FAST_RESPONSE_THRESHOLD_MS
        val isSlow = responseTimeMs > SLOW_RESPONSE_THRESHOLD_MS

        // Update streaks
        val newStreakCorrect = if (isCorrect) currentProgress.streakCorrect + 1 else 0
        val newStreakIncorrect = if (!isCorrect) currentProgress.streakIncorrect + 1 else 0

        // PATCH 4: Adaptieve difficulty adjustment
        // Snelle correcte antwoorden = sneller difficulty omhoog
        // Trage fouten = sneller difficulty omlaag
        val difficultyAdjustment = when {
            newStreakCorrect >= STREAK_FOR_DIFFICULTY_UP && isFast -> 1
            newStreakCorrect >= STREAK_FOR_DIFFICULTY_UP -> 1
            newStreakIncorrect >= STREAK_FOR_DIFFICULTY_DOWN && isSlow -> -1
            newStreakIncorrect >= STREAK_FOR_DIFFICULTY_DOWN -> -1
            else -> 0
        }

        val newDifficultyTier = (currentProgress.currentDifficultyTier + difficultyAdjustment)
            .coerceIn(1, 5)

        // PATCH 4: Adaptieve mastery update
        // Snel correct = +6, langzaam correct = +4, fout = -3
        val masteryDelta = when {
            isCorrect && isFast -> 6
            isCorrect -> 4
            else -> -3
        }
        val newMasteryScore = (currentProgress.masteryScore + masteryDelta)
            .coerceIn(0, 100)

        // PATCH 5: CPA-fase update op basis van prestaties
        val newCpaPhase = calculateNewCpaPhase(
            currentProgress.currentCpaPhase,
            newMasteryScore,
            currentProgress.totalAttempts() + 1,
            newStreakCorrect,
            newStreakIncorrect
        )

        // Bereken XP
        val baseXp = if (isCorrect) XP_PER_CORRECT else 0
        val speedBonus = if (isCorrect && isFast) XP_PER_CORRECT_FAST - XP_PER_CORRECT else 0
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
            currentCpaPhase = newCpaPhase,
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
            cpaPhaseChanged = newCpaPhase != currentProgress.currentCpaPhase,
            newCpaPhase = newCpaPhase,
            isMastered = isNowMastered,
            isNewlyMastered = isNowMastered && currentProgress.masteredAt == null
        )
    }

    /**
     * PATCH 5: Bereken nieuwe CPA-fase op basis van prestaties
     * Snelle voortgang mogelijk bij goede prestaties
     * Terugval naar sterkere representatie bij fouten
     */
    private fun calculateNewCpaPhase(
        currentPhase: com.rekenrinkel.domain.content.CpaPhase,
        masteryScore: Int,
        totalAttempts: Int,
        streakCorrect: Int,
        streakIncorrect: Int
    ): com.rekenrinkel.domain.content.CpaPhase {
        // Bij herhaalde fouten: terug naar vorige fase
        if (streakIncorrect >= 3 && currentPhase != com.rekenrinkel.domain.content.CpaPhase.CONCRETE) {
            return when (currentPhase) {
                com.rekenrinkel.domain.content.CpaPhase.PICTORIAL -> com.rekenrinkel.domain.content.CpaPhase.CONCRETE
                com.rekenrinkel.domain.content.CpaPhase.ABSTRACT -> com.rekenrinkel.domain.content.CpaPhase.PICTORIAL
                com.rekenrinkel.domain.content.CpaPhase.MIXED_TRANSFER -> com.rekenrinkel.domain.content.CpaPhase.ABSTRACT
                else -> currentPhase
            }
        }

        // Bij goede prestaties: door naar volgende fase
        return when (currentPhase) {
            com.rekenrinkel.domain.content.CpaPhase.CONCRETE -> {
                if (masteryScore >= 60 && totalAttempts >= MIN_ATTEMPTS_FOR_CPA_ADVANCE && streakCorrect >= 2) {
                    com.rekenrinkel.domain.content.CpaPhase.PICTORIAL
                } else currentPhase
            }
            com.rekenrinkel.domain.content.CpaPhase.PICTORIAL -> {
                if (masteryScore >= 75 && totalAttempts >= 8 && streakCorrect >= 3) {
                    com.rekenrinkel.domain.content.CpaPhase.ABSTRACT
                } else currentPhase
            }
            com.rekenrinkel.domain.content.CpaPhase.ABSTRACT -> {
                if (masteryScore >= 90 && streakCorrect >= 5) {
                    com.rekenrinkel.domain.content.CpaPhase.MIXED_TRANSFER
                } else currentPhase
            }
            com.rekenrinkel.domain.content.CpaPhase.MIXED_TRANSFER -> currentPhase
        }
    }

    /**
     * PATCH 8: Check of er nieuwe badges unlocked zijn - leerpad-gericht
     * Beloningen voor: skill mastered, CPA fase, cluster, review, streak, unlocks
     */
    fun checkBadges(
        outcome: ExerciseOutcome,
        currentRewards: Rewards,
        skillName: String,
        allProgress: List<SkillProgress> = emptyList()  // Voor cluster badges
    ): List<Badge> {
        val newBadges = mutableListOf<Badge>()
        val progress = outcome.updatedProgress

        // 1. Skill Mastered badge
        if (outcome.isNewlyMastered) {
            newBadges.add(Badge(
                id = "mastered_${progress.skillId}",
                name = "$skillName Meester",
                description = "Je hebt $skillName volledig beheerst! (90%+ mastery)",
                icon = "🏆"
            ))
        }

        // 2. PATCH 8: CPA Fase voltooid badge
        if (outcome.cpaPhaseChanged && outcome.newCpaPhase != null) {
            val cpaBadgeId = "cpa_${progress.skillId}_${outcome.newCpaPhase.name.lowercase()}"
            if (currentRewards.badges.none { it.id == cpaBadgeId }) {
                val phaseName = when (outcome.newCpaPhase) {
                    com.rekenrinkel.domain.content.CpaPhase.PICTORIAL -> "Picturaal"
                    com.rekenrinkel.domain.content.CpaPhase.ABSTRACT -> "Abstract"
                    com.rekenrinkel.domain.content.CpaPhase.MIXED_TRANSFER -> "Transfer"
                    else -> ""
                }
                if (phaseName.isNotEmpty()) {
                    newBadges.add(Badge(
                        id = cpaBadgeId,
                        name = "$skillName $phaseName",
                        description = "Je kunt $skillName nu op $phaseName niveau!",
                        icon = "📈"
                    ))
                }
            }
        }

        // 3. PATCH 8: Mastery sterren badges (50%, 70%, 90%)
        val masteryStars = progress.masteryStars()
        if (masteryStars >= 1 && currentRewards.badges.none { it.id == "${progress.skillId}_star1" }) {
            newBadges.add(Badge(
                id = "${progress.skillId}_star1",
                name = "$skillName Beginner",
                description = "Eerste ster behaald! (50% mastery)",
                icon = "⭐"
            ))
        }
        if (masteryStars >= 2 && currentRewards.badges.none { it.id == "${progress.skillId}_star2" }) {
            newBadges.add(Badge(
                id = "${progress.skillId}_star2",
                name = "$skillName Gevorderd",
                description = "Tweede ster behaald! (70% mastery)",
                icon = "⭐⭐"
            ))
        }

        // 4. PATCH 8: Cluster badge als alle skills in een cluster mastered
        val clusterBadges = checkClusterBadges(allProgress, currentRewards)
        newBadges.addAll(clusterBadges)

        // 5. Review consistentie badge
        val reviewSkills = allProgress.filter { p ->
            val daysSince = p.lastPracticedAt?.let {
                (System.currentTimeMillis() - it) / (24 * 60 * 60 * 1000)
            } ?: 30
            p.masteryScore >= 90 && daysSince <= 7  // Recent geoefende mastered skills
        }
        if (reviewSkills.size >= 3 && currentRewards.badges.none { it.id == "review_master" }) {
            newBadges.add(Badge(
                id = "review_master",
                name = "Review Meester",
                description = "Je blijft geoefende skills scherp!",
                icon = "🔄"
            ))
        }

        // 6. Streak badge (behouden)
        if (progress.streakCorrect >= 10 &&
            currentRewards.badges.none { it.id == "streak_10" }) {
            newBadges.add(Badge(
                id = "streak_10",
                name = "Hot Streak",
                description = "10 juiste antwoorden op een rij!",
                icon = "🔥"
            ))
        }

        return newBadges
    }

    /**
     * PATCH 8: Check cluster-level badges
     */
    private fun checkClusterBadges(
        allProgress: List<SkillProgress>,
        currentRewards: Rewards
    ): List<Badge> {
        val newBadges = mutableListOf<Badge>()

        // Foundation cluster: alle foundation skills mastered
        val foundationIds = listOf(
            "foundation_subitize_5", "foundation_counting",
            "foundation_number_bonds_5", "foundation_number_bonds_10"
        )
        val foundationMastered = foundationIds.count { id ->
            allProgress.find { it.skillId == id }?.masteryScore?.let { it >= 90 } == true
        }
        if (foundationMastered >= 3 && currentRewards.badges.none { it.id == "cluster_foundation" }) {
            newBadges.add(Badge(
                id = "cluster_foundation",
                name = "Foundation Expert",
                description = "Je beheerst de basisvaardigheden!",
                icon = "🎯"
            ))
        }

        // Arithmetic cluster
        val arithmeticIds = listOf(
            "arithmetic_add_10", "arithmetic_sub_10",
            "arithmetic_add_20", "arithmetic_sub_20"
        )
        val arithmeticMastered = arithmeticIds.count { id ->
            allProgress.find { it.skillId == id }?.masteryScore?.let { it >= 90 } == true
        }
        if (arithmeticMastered >= 3 && currentRewards.badges.none { it.id == "cluster_arithmetic" }) {
            newBadges.add(Badge(
                id = "cluster_arithmetic",
                name = "Rekenwonder",
                description = "Je kunt al goed optellen en aftrekken!",
                icon = "➕"
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

    /**
     * PATCH 4: Bepaal focus skill op basis van prestaties, niet leeftijd
     * Leeftijd bepaalt alleen welke skills beschikbaar zijn (prerequisites)
     * Prestaties sturen welke skill nu geoefend wordt
     */
    private fun determineFocusSkill(
        availableSkills: List<Skill>,
        progressMap: Map<String, SkillProgress>,
        userProfile: UserProfile
    ): Skill {
        // Score elke skill op basis van leerbehoefte (adaptief)
        val skillScores = availableSkills.map { skill ->
            val progress = progressMap[skill.id]
            val score = calculateLearningPriority(skill, progress, availableSkills, progressMap)
            skill to score
        }

        // Kies skill met hoogste leerbehoefte (laagste score = hogere prioriteit)
        return skillScores.minByOrNull { it.second }?.first
            ?: availableSkills.firstOrNull()
            ?: getDefaultSkill()
    }

    /**
     * PATCH 4: Bereken leerprioriteit voor een skill
     * Lager getal = hogere prioriteit om te oefenen
     */
    private fun calculateLearningPriority(
        skill: Skill,
        progress: SkillProgress?,
        allSkills: List<Skill>,
        progressMap: Map<String, SkillProgress>
    ): Double {
        if (progress == null) {
            // Nieuwe skill: prioriteit afhankelijk van prerequisites
            val prereqsMet = skill.prerequisites.count { prereqId ->
                progressMap[prereqId]?.masteryScore?.let { it >= 70 } == true
            }
            val prereqFactor = if (skill.prerequisites.isEmpty()) 0.0
                else 100.0 * (1 - prereqsMet.toDouble() / skill.prerequisites.size)
            return 50.0 + prereqFactor  // Medium prioriteit voor nieuwe skills
        }

        val mastery = progress.masteryScore
        val daysSincePractice = progress.lastPracticedAt?.let {
            (System.currentTimeMillis() - it) / (24 * 60 * 60 * 1000)
        } ?: 30

        // Factoren voor prioriteit
        val masteryFactor = when {
            mastery < 25 -> 0.0    // Emerging: hoogste prioriteit
            mastery < 50 -> 25.0   // Developing: hoge prioriteit
            mastery < 75 -> 50.0   // Practicing: medium prioriteit
            mastery < 90 -> 75.0   // Solid: lage prioriteit (review)
            else -> 100.0          // Mastered: laagste prioriteit
        }

        // Spaced review factor: skills die lang geleden geoefend zijn
        val reviewFactor = when {
            daysSincePractice > 14 -> -20.0  // Review nodig na 2 weken
            daysSincePractice > 7 -> -10.0   // Licht review nodig
            else -> 0.0
        }

        // Foutpatroon factor: skills met recente fouten
        val errorFactor = if (progress.streakIncorrect >= 2) -15.0 else 0.0
        
        // PATCH 5: Voorkom dat "Tellen" te vaak dominant is
        val countingPenalty = when {
            // Counting skills die al veel zijn geoefend krijgen penalty
            skill.id == "foundation_counting" && (progress.correctCount + progress.incorrectCount) > 20 -> 25.0
            skill.id == "foundation_counting" && (progress.correctCount + progress.incorrectCount) > 10 -> 15.0
            else -> 0.0
        }

        return masteryFactor + reviewFactor + errorFactor + countingPenalty
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
        
        // PATCH 4: Echte end-to-end CPA flow voor foundation/optellen cluster
        val config = ContentRepository.getConfig(focusSkill.id)
        val skillCpaPhase = config?.cpaPhase ?: com.rekenrinkel.domain.content.CpaPhase.CONCRETE
        val currentCpaPhase = progress?.currentCpaPhase ?: com.rekenrinkel.domain.content.CpaPhase.CONCRETE
        
        // Bepaal toegestane CPA-fase op basis van mastery
        val allowedCpaPhase = determineAllowedCpaPhase(currentCpaPhase, progress)
        
        // Gebruik de strengere van skill-fase en toegestane fase
        val effectiveCpaPhase = minOf(skillCpaPhase, allowedCpaPhase)
        
        // Specifieke flow voor foundation/optellen cluster
        val isFoundationArithmetic = focusSkill.id in setOf(
            "foundation_number_bonds_5",
            "foundation_number_bonds_10", 
            "arithmetic_add_10_concrete",
            "arithmetic_add_10_pictorial",
            "arithmetic_add_10_abstract",
            "arithmetic_sub_10_concrete",
            "arithmetic_sub_10_pictorial",
            "arithmetic_sub_10_abstract"
        )

        // DIDACTISCHE STRUCTUUR: worked example -> guided -> independent
        // Specifiek per CPA-fase en cluster
        return when {
            // Speciale behandeling voor foundation/optellen cluster
            isFoundationArithmetic && effectiveCpaPhase == com.rekenrinkel.domain.content.CpaPhase.CONCRETE -> {
                // CONCRETE: 1 worked example + 3 guided
                listOf(
                    exerciseEngine.generateWorkedExample(focusSkill.id, difficulty),
                    exerciseEngine.generateGuidedExercise(focusSkill.id, difficulty),
                    exerciseEngine.generateGuidedExercise(focusSkill.id, difficulty),
                    exerciseEngine.generateGuidedExercise(focusSkill.id, difficulty)
                )
            }
            isFoundationArithmetic && effectiveCpaPhase == com.rekenrinkel.domain.content.CpaPhase.PICTORIAL -> {
                // PICTORIAL: 1 worked + 1 guided + 2 independent
                listOf(
                    exerciseEngine.generateWorkedExample(focusSkill.id, difficulty),
                    exerciseEngine.generateGuidedExercise(focusSkill.id, difficulty),
                    exerciseEngine.generateExercise(focusSkill.id, difficulty),
                    exerciseEngine.generateExercise(focusSkill.id, difficulty)
                )
            }
            isFoundationArithmetic && effectiveCpaPhase == com.rekenrinkel.domain.content.CpaPhase.ABSTRACT -> {
                // ABSTRACT: 1 guided + 3 independent (als er al attempts zijn)
                if (progress != null && progress.totalAttempts() >= 5) {
                    listOf(
                        exerciseEngine.generateExercise(focusSkill.id, difficulty),
                        exerciseEngine.generateExercise(focusSkill.id, difficulty),
                        exerciseEngine.generateExercise(focusSkill.id, difficulty),
                        exerciseEngine.generateExercise(focusSkill.id, difficulty)
                    )
                } else {
                    listOf(
                        exerciseEngine.generateGuidedExercise(focusSkill.id, difficulty),
                        exerciseEngine.generateExercise(focusSkill.id, difficulty),
                        exerciseEngine.generateExercise(focusSkill.id, difficulty),
                        exerciseEngine.generateExercise(focusSkill.id, difficulty)
                    )
                }
            }
            // Default voor andere skills
            effectiveCpaPhase == com.rekenrinkel.domain.content.CpaPhase.CONCRETE -> {
                listOf(
                    exerciseEngine.generateWorkedExample(focusSkill.id, difficulty)
                ) + (1 until count).map {
                    exerciseEngine.generateGuidedExercise(focusSkill.id, difficulty)
                }
            }
            effectiveCpaPhase == com.rekenrinkel.domain.content.CpaPhase.PICTORIAL -> {
                listOf(
                    exerciseEngine.generateGuidedExercise(focusSkill.id, difficulty)
                ) + (1 until count).map {
                    exerciseEngine.generateExercise(focusSkill.id, difficulty)
                }
            }
            else -> {
                // ABSTRACT/MIXED
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

    // PATCH 2: Block-aware shuffle vervangt globale smartShuffleWithVariation
    // Didactische volgorde > globale randomisatie

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
 * PATCH 4 & 5: Uitkomst van een enkele oefening
 * Inclusief CPA-fase wijzigingen voor adaptieve sturing
 */
data class ExerciseOutcome(
    val updatedProgress: SkillProgress,
    val xpEarned: Int,
    val difficultyChanged: Boolean,
    val newDifficultyTier: Int,
    val cpaPhaseChanged: Boolean = false,  // PATCH 5
    val newCpaPhase: com.rekenrinkel.domain.content.CpaPhase? = null,  // PATCH 5
    val isMastered: Boolean,
    val isNewlyMastered: Boolean
)