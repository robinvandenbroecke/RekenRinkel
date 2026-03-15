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
        const val MASTERY_THRESHOLD_WEAK = 50
        const val MASTERY_THRESHOLD_SOLID = 75
        const val MASTERY_THRESHOLD_MASTERED = 90
    }
    
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
        val accessibleSkills = if (isPremiumUnlocked) {
            availableSkills
        } else {
            availableSkills.filter { !it.isPremium }
        }
        
        // Categoriseer skills op basis van mastery
        val weakSkills = accessibleSkills.filter { skill ->
            val progress = progressMap[skill.id]
            progress == null || progress.masteryScore < MASTERY_THRESHOLD_WEAK
        }
        
        val practicingSkills = accessibleSkills.filter { skill ->
            val progress = progressMap[skill.id]
            progress != null && 
            progress.masteryScore >= MASTERY_THRESHOLD_WEAK && 
            progress.masteryScore < MASTERY_THRESHOLD_SOLID
        }
        
        val strongSkills = accessibleSkills.filter { skill ->
            val progress = progressMap[skill.id]
            progress != null && progress.masteryScore >= MASTERY_THRESHOLD_SOLID
        }
        
        // Bepaal focus skill (zwakste of eerste niet-geleerde)
        val focusSkill = determineFocusSkill(weakSkills, practicingSkills, accessibleSkills)
        
        // 50% Focus oefeningen
        val focusCount = (SESSION_SIZE * FOCUS_PERCENTAGE / 100).coerceAtLeast(2)
        repeat(focusCount) {
            val progress = progressMap[focusSkill.id]
                ?: SkillProgress(skillId = focusSkill.id)
            exercises.add(exerciseEngine.generateExercise(
                focusSkill.id, 
                progress.currentDifficulty.coerceIn(focusSkill.minDifficulty, focusSkill.maxDifficulty)
            ))
        }
        
        // 30% Herhaling zwakke/practicing vaardigheden
        val reviewCount = (SESSION_SIZE * REVIEW_PERCENTAGE / 100).coerceAtLeast(2)
        val reviewCandidates = (weakSkills + practicingSkills)
            .filter { it.id != focusSkill.id }
            .takeIf { it.isNotEmpty() }
            ?: accessibleSkills.filter { it.id != focusSkill.id }
        
        reviewCandidates.take(reviewCount).forEach { skill ->
            val progress = progressMap[skill.id]
                ?: SkillProgress(skillId = skill.id)
            exercises.add(exerciseEngine.generateExercise(
                skill.id,
                progress.currentDifficulty.coerceIn(skill.minDifficulty, skill.maxDifficulty)
            ))
        }
        
        // Vul review aan indien nodig
        while (exercises.size < focusCount + reviewCount && exercises.size < accessibleSkills.size * 2) {
            val randomSkill = accessibleSkills.random()
            if (exercises.none { it.skillId == randomSkill.id }) {
                val progress = progressMap[randomSkill.id]
                    ?: SkillProgress(skillId = randomSkill.id)
                exercises.add(exerciseEngine.generateExercise(
                    randomSkill.id,
                    progress.currentDifficulty.coerceIn(randomSkill.minDifficulty, randomSkill.maxDifficulty)
                ))
            }
        }
        
        // 20% Uitdaging of nieuwe vaardigheid
        val challengeCount = (SESSION_SIZE * CHALLENGE_PERCENTAGE / 100).coerceAtLeast(1)
        val challengeCandidates = determineChallengeSkills(strongSkills, accessibleSkills, progressMap, isPremiumUnlocked)
        
        challengeCandidates.take(challengeCount).forEach { skill ->
            val progress = progressMap[skill.id]
            val challengeDifficulty = ((progress?.currentDifficulty ?: 1) + 1)
                .coerceIn(skill.minDifficulty, skill.maxDifficulty)
            exercises.add(exerciseEngine.generateExercise(skill.id, challengeDifficulty))
        }
        
        // Shuffle voor variatie, maar behoud geen duplicate skill IDs te vaak
        return exercises.shuffled().take(SESSION_SIZE)
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
     * Bepaal focus skill op basis van zwakte en leerpad
     */
    private fun determineFocusSkill(
        weakSkills: List<Skill>,
        practicingSkills: List<Skill>,
        allAccessible: List<Skill>
    ): Skill {
        // Prioriteit 1: Nog niet gestarte skills (nieuw in leerpad)
        val notStarted = allAccessible.filter { skill ->
            weakSkills.none { it.id == skill.id } && 
            practicingSkills.none { it.id == skill.id }
        }
        
        if (notStarted.isNotEmpty()) {
            // Kies de eerste in het leerpad
            val learningPath = ContentRepository.getLearningPath().flatten()
            return notStarted.minByOrNull { learningPath.indexOf(it.id) } 
                ?: notStarted.first()
        }
        
        // Prioriteit 2: Zwakste skill
        if (weakSkills.isNotEmpty()) {
            return weakSkills.first()
        }
        
        // Prioriteit 3: Practicing skills
        if (practicingSkills.isNotEmpty()) {
            return practicingSkills.random()
        }
        
        // Fallback: eerste beschikbare
        return allAccessible.first()
    }
    
    /**
     * Bepaal challenge skills op basis van voortgang
     */
    private fun determineChallengeSkills(
        strongSkills: List<Skill>,
        accessibleSkills: List<Skill>,
        progressMap: Map<String, SkillProgress>,
        isPremiumUnlocked: Boolean
    ): List<Skill> {
        val challenges = mutableListOf<Skill>()
        
        // 1. Sterke skills met ruimte voor hogere difficulty
        strongSkills.forEach { skill ->
            val progress = progressMap[skill.id]
            if (progress != null && progress.currentDifficulty < skill.maxDifficulty) {
                challenges.add(skill)
            }
        }
        
        // 2. Skills die net unlocked kunnen worden
        val masteredSkills = progressMap.filter { it.value.masteryScore >= MASTERY_THRESHOLD_WEAK }.keys
        val nextUnlockable = ContentRepository.getAllConfigs()
            .filter { !isPremiumUnlocked || !it.isPremium }
            .filter { it.prerequisites.isNotEmpty() }
            .filter { it.prerequisites.all { prereq -> prereq in masteredSkills } }
            .map { it.toSkill() }
            .filter { skill -> accessibleSkills.none { it.id == skill.id } }
        
        challenges.addAll(nextUnlockable)
        
        return challenges.ifEmpty { accessibleSkills }
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