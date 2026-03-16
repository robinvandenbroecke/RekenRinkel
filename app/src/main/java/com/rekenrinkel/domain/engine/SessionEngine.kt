package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.content.ContentRepository
import com.rekenrinkel.domain.model.*
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.ProgressRepositoryInterface
import kotlinx.coroutines.flow.first

/**
 * Engine voor sessiebeheer en adaptieve oefeningselectie.
 * Gebruikt ContentRepository voor skill configuratie en prerequisites.
 */
class SessionEngine(
    private val exerciseEngine: ExerciseEngine,
    private val progressRepository: ProgressRepositoryInterface
) {
    
    companion object {
        const val SESSION_SIZE = 8
        const val FOCUS_PERCENTAGE = 50
        const val REVIEW_PERCENTAGE = 30
        const val CHALLENGE_PERCENTAGE = 20
        
        // Mastery drempels
        const val MASTERY_THRESHOLD_EMERGING = 25
        const val MASTERY_THRESHOLD_WEAK = 50
        const val MASTERY_THRESHOLD_PRACTICING = 50
        const val MASTERY_THRESHOLD_SOLID = 75
        const val MASTERY_THRESHOLD_MASTERED = 90
        
        // Tijdsdrempels voor recentheid (ms)
        const val RECENT_CUTOFF_MS = 7L * 24 * 60 * 60 * 1000 // 7 dagen
    }
    
    /**
     * Enum voor expliciete skill status categorieën
     */
    enum class SkillStatus {
        NOT_LEARNED,    // Geen progress record
        EMERGING,       // 1-24% mastery
        PRACTICING,     // 25-74% mastery
        SOLID,          // 75-89% mastery
        MASTERED        // 90-100% mastery
    }
    
    /**
     * Data class voor gesorteerde review candidate
     */
    data class ReviewCandidate(
        val skill: Skill,
        val progress: SkillProgress,
        val priorityScore: Int
    )
    
    /**
     * Bouw een sessie met adaptieve mix van oefeningen.
     * Houdt rekening met prerequisites, mastery levels, en premium status.
     */
    suspend fun buildSession(isPremiumUnlocked: Boolean = false): List<Exercise> {
        val exercises = mutableListOf<Exercise>()
        
        // Haal voortgang op
        val allProgress = progressRepository.getAllProgress().first()
        val progressMap = allProgress.associateBy { it.skillId }
        
        // Bepaal welke skills beschikbaar zijn (prerequisites behaald)
        val availableSkills = getAvailableSkills(progressMap)
        
        // Filter op premium status
        val accessibleConfigs = if (isPremiumUnlocked) {
            ContentRepository.getAllConfigs()
        } else {
            ContentRepository.getFreeConfigs()
        }.filter { config ->
            availableSkills.any { it.id == config.skillId }
        }
        
        val accessibleSkills = accessibleConfigs.map { it.toSkill() }
        
        // Categoriseer skills op basis van status
        val skillStatuses = categorizeSkills(accessibleSkills, progressMap)
        
        val notLearned = skillStatuses[SkillStatus.NOT_LEARNED] ?: emptyList()
        val emerging = skillStatuses[SkillStatus.EMERGING] ?: emptyList()
        val practicing = skillStatuses[SkillStatus.PRACTICING] ?: emptyList()
        val solid = skillStatuses[SkillStatus.SOLID] ?: emptyList()
        val mastered = skillStatuses[SkillStatus.MASTERED] ?: emptyList()
        
        // Bepaal focus skill met verbeterde logica
        val focusSkill = determineFocusSkillExplicit(
            notLearned, emerging, practicing, accessibleSkills
        )
        
        // 50% Focus oefeningen
        val focusCount = (SESSION_SIZE * FOCUS_PERCENTAGE / 100).coerceAtLeast(2)
        val focusProgress = progressMap[focusSkill.id]
        val focusDifficulty = determineDifficulty(focusSkill, focusProgress, true)
        
        repeat(focusCount) {
            exercises.add(exerciseEngine.generateExercise(focusSkill.id, focusDifficulty))
        }
        
        // 30% Review - gesorteerd op prioriteit
        val reviewCount = (SESSION_SIZE * REVIEW_PERCENTAGE / 100).coerceAtLeast(2)
        val reviewCandidates = determineReviewCandidates(
            notLearned, emerging, practicing, solid, progressMap, focusSkill
        )
        
        reviewCandidates.take(reviewCount).forEach { candidate ->
            val difficulty = determineDifficulty(candidate.skill, candidate.progress, false)
            exercises.add(exerciseEngine.generateExercise(candidate.skill.id, difficulty))
        }
        
        // Vul review aan indien nodig met veilige fallback
        if (exercises.size < focusCount + reviewCount) {
            val needed = focusCount + reviewCount - exercises.size
            val fallbackCandidates = accessibleSkills
                .filter { it.id != focusSkill.id }
                .filter { skill -> exercises.none { it.skillId == skill.id } }
                .shuffled()
                .take(needed)
            
            fallbackCandidates.forEach { skill ->
                val progress = progressMap[skill.id]
                val difficulty = determineDifficulty(skill, progress, false)
                exercises.add(exerciseEngine.generateExercise(skill.id, difficulty))
            }
        }
        
        // 20% Challenge
        val challengeCount = SESSION_SIZE - exercises.size
        val challengeCandidates = determineChallengeSkillsExplicit(
            solid, mastered, accessibleConfigs, progressMap, isPremiumUnlocked
        )

        challengeCandidates.take(challengeCount).forEach { skill ->
            val progress = progressMap[skill.id]
            val challengeDifficulty = ((progress?.currentDifficultyTier ?: 1) + 1)
                .coerceIn(skill.minDifficulty, skill.maxDifficulty)
            exercises.add(exerciseEngine.generateExercise(skill.id, challengeDifficulty))
        }

        // Backfill: ensure we always have exactly SESSION_SIZE exercises
        while (exercises.size < SESSION_SIZE) {
            val needed = SESSION_SIZE - exercises.size

            // Try to fill from accessible skills, allowing repeats if necessary
            val backfillSkills = accessibleSkills
                .filter { skill -> exercises.count { it.skillId == skill.id } < 2 } // Max 2 per skill
                .shuffled()
                .take(needed)

            if (backfillSkills.isEmpty()) {
                // Last resort: allow any accessible skill even if already used
                accessibleSkills.shuffled().take(needed).forEach { skill ->
                    val progress = progressMap[skill.id]
                    val difficulty = determineDifficulty(skill, progress, false)
                    exercises.add(exerciseEngine.generateExercise(skill.id, difficulty))
                }
            } else {
                backfillSkills.forEach { skill ->
                    val progress = progressMap[skill.id]
                    val difficulty = determineDifficulty(skill, progress, false)
                    exercises.add(exerciseEngine.generateExercise(skill.id, difficulty))
                }
            }

            // Safety check to prevent infinite loop
            if (exercises.size < SESSION_SIZE && accessibleSkills.isEmpty()) {
                throw IllegalStateException("Cannot build session: no accessible skills available")
            }
        }

        // Shuffle met constraint: niet teveel van zelfde skill achter elkaar
        return smartShuffle(exercises)
    }
    
    /**
     * Categoriseer skills in expliciete status categorieën
     */
    private fun categorizeSkills(
        skills: List<Skill>,
        progressMap: Map<String, SkillProgress>
    ): Map<SkillStatus, List<Skill>> {
        return skills.groupBy { skill ->
            val progress = progressMap[skill.id]
            when {
                progress == null -> SkillStatus.NOT_LEARNED
                progress.masteryScore < MASTERY_THRESHOLD_EMERGING -> SkillStatus.EMERGING
                progress.masteryScore < MASTERY_THRESHOLD_SOLID -> SkillStatus.PRACTICING
                progress.masteryScore < MASTERY_THRESHOLD_MASTERED -> SkillStatus.SOLID
                else -> SkillStatus.MASTERED
            }
        }
    }
    
    /**
     * Bepaal focus skill met expliciete status prioriteit
     */
    private fun determineFocusSkillExplicit(
        notLearned: List<Skill>,
        emerging: List<Skill>,
        practicing: List<Skill>,
        allAccessible: List<Skill>
    ): Skill {
        // Prioriteit 1: Niet geleerde skills in volgorde van leerpad
        if (notLearned.isNotEmpty()) {
            val learningPath = ContentRepository.getLearningPath().flatten()
            return notLearned.minByOrNull { learningPath.indexOf(it.id) } 
                ?: notLearned.first()
        }
        
        // Prioriteit 2: Emerging skills (net begonnen, nog zwak)
        if (emerging.isNotEmpty()) {
            return emerging.first()
        }
        
        // Prioriteit 3: Practicing skills met meeste fouten
        if (practicing.isNotEmpty()) {
            return practicing.first() // Geordend op foutaantal in determineReviewCandidates
        }
        
        // Fallback: eerste beschikbare
        return allAccessible.first()
    }
    
    /**
     * Bepaal review candidates met prioriteitsscore
     */
    private fun determineReviewCandidates(
        notLearned: List<Skill>,
        emerging: List<Skill>,
        practicing: List<Skill>,
        solid: List<Skill>,
        progressMap: Map<String, SkillProgress>,
        focusSkill: Skill
    ): List<ReviewCandidate> {
        val candidates = mutableListOf<ReviewCandidate>()
        
        // Niet geleerde skills (hoge prioriteit)
        notLearned.filter { it.id != focusSkill.id }.forEach { skill ->
            candidates.add(ReviewCandidate(
                skill = skill,
                progress = SkillProgress(skillId = skill.id),
                priorityScore = 100 // Hoogste prioriteit
            ))
        }
        
        // Emerging skills
        emerging.filter { it.id != focusSkill.id }.forEach { skill ->
            val progress = progressMap[skill.id]!!
            // Prioriteit op basis van: laag mastery, veel fouten, niet recent
            val recencyPenalty = if (isRecent(progress.lastPracticedAt)) 0 else 10
            val errorScore = progress.incorrectCount * 2
            candidates.add(ReviewCandidate(
                skill = skill,
                progress = progress,
                priorityScore = 80 + recencyPenalty + errorScore - progress.masteryScore
            ))
        }

        // Practicing skills - sorteer op zwakte en fouten
        practicing.filter { it.id != focusSkill.id }.forEach { skill ->
            val progress = progressMap[skill.id]!!
            val recencyPenalty = if (isRecent(progress.lastPracticedAt)) 0 else 15
            val errorScore = progress.incorrectCount
            candidates.add(ReviewCandidate(
                skill = skill,
                progress = progress,
                priorityScore = 50 + recencyPenalty + errorScore - (progress.masteryScore / 2)
            ))
        }

        // Solid skills alleen als ze niet recent zijn geoefend
        solid.filter { it.id != focusSkill.id }.forEach { skill ->
            val progress = progressMap[skill.id]!!
            if (!isRecent(progress.lastPracticedAt)) {
                candidates.add(ReviewCandidate(
                    skill = skill,
                    progress = progress,
                    priorityScore = 20
                ))
            }
        }
        
        // Sorteer op prioriteit (hoogste eerst)
        return candidates.sortedByDescending { it.priorityScore }
    }
    
    /**
     * Bepaal challenge skills met expliciete criteria
     */
    private fun determineChallengeSkillsExplicit(
        solid: List<Skill>,
        mastered: List<Skill>,
        accessibleConfigs: List<com.rekenrinkel.domain.content.SkillContentConfig>,
        progressMap: Map<String, SkillProgress>,
        isPremiumUnlocked: Boolean
    ): List<Skill> {
        val challenges = mutableListOf<Skill>()
        
        // 1. Solid skills met ruimte voor hogere difficulty
        solid.forEach { skill ->
            val progress = progressMap[skill.id]
            if (progress != null && progress.currentDifficultyTier < skill.maxDifficulty) {
                challenges.add(skill)
            }
        }

        // 2. Mastered skills die uitgebreid kunnen worden
        mastered.forEach { skill ->
            val progress = progressMap[skill.id]
            if (progress != null && progress.currentDifficultyTier < skill.maxDifficulty) {
                challenges.add(skill)
            }
        }
        
        // 3. Skills die net unlocked kunnen worden (alleen als niet al te veel in session)
        val masteredSkillIds = progressMap
            .filter { it.value.masteryScore >= MASTERY_THRESHOLD_WEAK }
            .keys
        
        val nextUnlockable = ContentRepository.getAllConfigs()
            .filter { config ->
                // Alleen als premium unlocked of skill is free
                (isPremiumUnlocked || !config.isPremium) &&
                // Moet prerequisites hebben
                config.prerequisites.isNotEmpty() &&
                // Alle prerequisites moeten gemastered zijn
                config.prerequisites.all { it in masteredSkillIds } &&
                // Nog niet in accessible (dus nog niet unlocked)
                accessibleConfigs.none { it.skillId == config.skillId }
            }
            .map { it.toSkill() }
        
        challenges.addAll(nextUnlockable)
        
        return challenges.ifEmpty { solid + mastered }
    }
    
    /**
     * Check of een timestamp recent is (< 7 dagen)
     */
    private fun isRecent(timestamp: Long?): Boolean {
        if (timestamp == null) return false
        return System.currentTimeMillis() - timestamp < RECENT_CUTOFF_MS
    }
    
    /**
     * Bepaal moeilijkheid op basis van voortgang en CPA-fase
     */
    private fun determineDifficulty(skill: Skill, progress: SkillProgress?, isFocus: Boolean): Int {
        val baseDifficulty = if (isFocus) {
            progress?.currentDifficultyTier?.coerceIn(skill.minDifficulty, skill.maxDifficulty)
                ?: skill.minDifficulty
        } else {
            when {
                progress == null -> skill.minDifficulty
                progress.masteryScore < MASTERY_THRESHOLD_WEAK -> skill.minDifficulty
                progress.masteryScore < MASTERY_THRESHOLD_SOLID ->
                    (progress.currentDifficultyTier).coerceIn(skill.minDifficulty, skill.maxDifficulty)
                else -> progress.currentDifficultyTier.coerceIn(skill.minDifficulty, skill.maxDifficulty)
            }
        }
        
        // Aanpassing op basis van CPA-fase: concrete fase = makkelijker
        val config = ContentRepository.getConfig(skill.id)
        return when (config?.cpaPhase) {
            com.rekenrinkel.domain.content.CpaPhase.CONCRETE -> baseDifficulty.coerceAtMost(2)
            com.rekenrinkel.domain.content.CpaPhase.PICTORIAL -> baseDifficulty.coerceAtMost(3)
            else -> baseDifficulty
        }
    }
    
    /**
     * Bepaal representatie op basis van skill en voortgang
     * Respecteert CPA-fase: jongere kinderen/kinderen met fouten krijgen meer concrete representaties
     */
    fun determineRepresentation(skillId: String, progress: SkillProgress?): String {
        val config = ContentRepository.getConfig(skillId) ?: return "SYMBOLS"
        val representations = config.preferredRepresentations
        
        if (representations.isEmpty()) return "SYMBOLS"
        
        // Als er fouten zijn in specifieke error types, kies concrete representatie
        val hasErrors = progress?.errorTypeSummary?.values?.sum() ?: 0 > 0
        val hasRecentErrors = progress?.streakIncorrect ?: 0 >= 2
        
        return when {
            // Recent fouten: meest concrete representatie
            hasRecentErrors -> representations.firstOrNull()?.name ?: "DOTS"
            // Error summary aanwezig: een stap terug in abstractie
            hasErrors && representations.size > 1 -> representations[representations.size / 2].name
            // Anders: representatie passend bij mastery niveau
            progress?.masteryScore ?: 0 < 50 -> representations.firstOrNull()?.name ?: "DOTS"
            progress?.masteryScore ?: 0 < 75 -> representations.getOrNull(1)?.name ?: representations.last().name
            else -> representations.lastOrNull()?.name ?: "SYMBOLS"
        }
    }
    
    /**
     * Check of remediëring nodig is op basis van fouttypes
     */
    fun needsRemediation(skillId: String, progress: SkillProgress?): String? {
        if (progress == null) return null
        
        val config = ContentRepository.getConfig(skillId) ?: return null
        
        // Check of er herhaalde fouten zijn van een specifiek type
        val dominantError = progress.errorTypeSummary.maxByOrNull { it.value }
        if (dominantError != null && dominantError.value >= 3) {
            // Als er een remediation skill is voor dit error type, gebruik die
            if (config.remediationSkill != null) {
                return config.remediationSkill
            }
        }
        
        // Check streak
        if (progress.streakIncorrect >= 3) {
            // Suggesteer remediation skill of een makkelijkere versie
            return config.remediationSkill
        }
        
        return null
    }
    
    /**
     * Smart shuffle: voorkom teveel van zelfde skill, antwoord of vraag achter elkaar
     */
    private fun smartShuffle(exercises: List<Exercise>): List<Exercise> {
        if (exercises.size <= 3) return exercises.shuffled()

        val result = mutableListOf<Exercise>()
        val remaining = exercises.toMutableList()
        var lastExercise: Exercise? = null

        while (remaining.isNotEmpty()) {
            // Zoek een oefening die verschilt van de vorige op meerdere dimensies
            val candidate = remaining.find { candidate ->
                val last = lastExercise
                if (last == null) return@find true

                // Must differ on at least one key dimension
                candidate.skillId != last.skillId &&
                candidate.correctAnswer != last.correctAnswer &&
                candidate.question != last.question
            } ?: remaining.find { candidate ->
                // Fallback: at least different skill or answer
                val last = lastExercise
                if (last == null) return@find true
                candidate.skillId != last.skillId || candidate.correctAnswer != last.correctAnswer
            } ?: remaining.first() // Ultimate fallback

            result.add(candidate)
            remaining.remove(candidate)
            lastExercise = candidate
        }

        return result
    }
    
    /**
     * Bereken XP voor een sessie met tijdsbonus
     */
    fun calculateXp(results: List<ExerciseResult>): Int {
        var xp = 0
        results.forEach { result ->
            if (result.isCorrect) {
                xp += 10 // Base XP
                
                // Snelheidsbonus
                when {
                    result.responseTimeMs < 1500 -> xp += 10 // Zeer snel
                    result.responseTimeMs < 3000 -> xp += 5  // Snel
                    result.responseTimeMs < 5000 -> xp += 2  // Normaal
                }
            }
        }
        return xp
    }
    
    /**
     * Bereken sterren (0-3) voor een sessie
     */
    fun calculateStars(accuracy: Float): Int {
        return when {
            accuracy >= 0.9f -> 3
            accuracy >= 0.75f -> 2
            accuracy >= 0.5f -> 1
            else -> 0
        }
    }
    
    /**
     * Bepaal welke skills getoond kunnen worden (prerequisites behaald)
     */
    private fun getAvailableSkills(progressMap: Map<String, SkillProgress>): List<Skill> {
        val masteredSkills = progressMap.filter { it.value.masteryScore >= MASTERY_THRESHOLD_WEAK }.keys
        
        return ContentRepository.getAllConfigs().map { it.toSkill() }.filter { skill ->
            skill.prerequisites.isEmpty() || 
            skill.prerequisites.all { it in masteredSkills }
        }
    }
    
    /**
     * Check of een skill unlocked is
     */
    suspend fun isSkillUnlocked(skillId: String): Boolean {
        val config = ContentRepository.getConfig(skillId) ?: return false
        if (config.prerequisites.isEmpty()) return true
        
        val allProgress = progressRepository.getAllProgress().first()
        val masteredSkills = allProgress
            .filter { it.masteryScore >= MASTERY_THRESHOLD_WEAK }
            .map { it.skillId }
            .toSet()
        
        return ContentRepository.canUnlockSkill(skillId, masteredSkills)
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
    
    /**
     * Bereken gemiddelde sessie score
     */
    fun calculateSessionScore(results: List<ExerciseResult>): Float {
        if (results.isEmpty()) return 0f
        val correct = results.count { it.isCorrect }
        return correct.toFloat() / results.size
    }
}