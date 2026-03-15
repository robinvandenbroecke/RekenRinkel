package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.content.ContentRepository
import com.rekenrinkel.domain.model.*
import com.rekenrinkel.data.repository.ProgressRepository

/**
 * Engine voor sessiebeheer en adaptieve oefeningselectie.
 * Gebruikt ContentRepository voor skill configuratie en prerequisites.
 */
class SessionEngine(
    private val exerciseEngine: ExerciseEngine,
    private val progressRepository: ProgressRepository
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
        val allProgress = progressRepository.getAllProgress()
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
            val challengeDifficulty = ((progress?.currentDifficulty ?: 1) + 1)
                .coerceIn(skill.minDifficulty, skill.maxDifficulty)
            exercises.add(exerciseEngine.generateExercise(skill.id, challengeDifficulty))
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
            val recencyPenalty = if (isRecent(progress.lastPracticed)) 0 else 10
            val errorScore = progress.wrongAnswers * 2
            candidates.add(ReviewCandidate(
                skill = skill,
                progress = progress,
                priorityScore = 80 + recencyPenalty + errorScore - progress.masteryScore
            ))
        }
        
        // Practicing skills - sorteer op zwakte en fouten
        practicing.filter { it.id != focusSkill.id }.forEach { skill ->
            val progress = progressMap[skill.id]!!
            val recencyPenalty = if (isRecent(progress.lastPracticed)) 0 else 15
            val errorScore = progress.wrongAnswers
            candidates.add(ReviewCandidate(
                skill = skill,
                progress = progress,
                priorityScore = 50 + recencyPenalty + errorScore - (progress.masteryScore / 2)
            ))
        }
        
        // Solid skills alleen als ze niet recent zijn geoefend
        solid.filter { it.id != focusSkill.id }.forEach { skill ->
            val progress = progressMap[skill.id]!!
            if (!isRecent(progress.lastPracticed)) {
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
            if (progress != null && progress.currentDifficulty < skill.maxDifficulty) {
                challenges.add(skill)
            }
        }
        
        // 2. Mastered skills die uitgebreid kunnen worden
        mastered.forEach { skill ->
            val progress = progressMap[skill.id]
            if (progress != null && progress.currentDifficulty < skill.maxDifficulty) {
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
     * Bepaal moeilijkheid op basis van voortgang
     */
    private fun determineDifficulty(skill: Skill, progress: SkillProgress?, isFocus: Boolean): Int {
        return if (isFocus) {
            // Focus skills: gebruik current difficulty of start bij min
            progress?.currentDifficulty?.coerceIn(skill.minDifficulty, skill.maxDifficulty)
                ?: skill.minDifficulty
        } else {
            // Review skills: iets makkelijker voor zwakke skills
            when {
                progress == null -> skill.minDifficulty
                progress.masteryScore < MASTERY_THRESHOLD_WEAK -> skill.minDifficulty
                progress.masteryScore < MASTERY_THRESHOLD_SOLID -> 
                    (progress.currentDifficulty).coerceIn(skill.minDifficulty, skill.maxDifficulty)
                else -> progress.currentDifficulty.coerceIn(skill.minDifficulty, skill.maxDifficulty)
            }
        }
    }
    
    /**
     * Smart shuffle: voorkom teveel van zelfde skill achter elkaar
     */
    private fun smartShuffle(exercises: List<Exercise>): List<Exercise> {
        if (exercises.size <= 3) return exercises.shuffled()
        
        val result = mutableListOf<Exercise>()
        val remaining = exercises.toMutableList()
        var lastSkillId: String? = null
        
        while (remaining.isNotEmpty()) {
            // Zoek een oefening die niet dezelfde skill is als de vorige
            val candidate = remaining.find { it.skillId != lastSkillId } 
                ?: remaining.first() // Fallback als alleen zelfde skill overblijft
            
            result.add(candidate)
            remaining.remove(candidate)
            lastSkillId = candidate.skillId
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
        
        val allProgress = progressRepository.getAllProgress()
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