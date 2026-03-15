package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.model.*
import com.rekenrinkel.data.repository.ProgressRepository

/**
 * Engine voor sessiebeheer en adaptieve oefeningselectie
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
    }
    
    /**
     * Bouw een sessie met adaptieve mix van oefeningen
     */
    suspend fun buildSession(): List<Exercise> {
        val exercises = mutableListOf<Exercise>()
        
        val allProgress = progressRepository.getAllProgress()
        val weakSkills = progressRepository.getWeakSkills()
        val strongSkills = progressRepository.getStrongSkills()
        
        // Bepaal focus skill (huidige of zwakke)
        val focusSkill = determineFocusSkill(weakSkills)
        
        // 50% Focus oefeningen
        val focusCount = (SESSION_SIZE * FOCUS_PERCENTAGE / 100).coerceAtLeast(2)
        repeat(focusCount) {
            val progress = progressRepository.getOrCreateProgress(focusSkill.id)
            exercises.add(exerciseEngine.generateExercise(focusSkill.id, progress.currentDifficulty))
        }
        
        // 30% Herhaling zwakke vaardigheden
        val reviewCount = (SESSION_SIZE * REVIEW_PERCENTAGE / 100).coerceAtLeast(2)
        val reviewSkills = weakSkills.filter { it.skillId != focusSkill.id }.take(reviewCount)
        reviewSkills.forEach { progress ->
            exercises.add(exerciseEngine.generateExercise(progress.skillId, progress.currentDifficulty))
        }
        
        // Vul aan als we niet genoeg zwakke skills hebben
        while (exercises.size < focusCount + reviewCount) {
            val randomSkill = getRandomFreeSkill()
            val progress = progressRepository.getOrCreateProgress(randomSkill.id)
            exercises.add(exerciseEngine.generateExercise(randomSkill.id, progress.currentDifficulty))
        }
        
        // 20% Uitdaging of nieuwe vaardigheid
        val challengeCount = SESSION_SIZE - exercises.size
        repeat(challengeCount) {
            val skill = getNextUnlockableSkill(strongSkills) ?: getRandomSkill()
            val progress = progressRepository.getOrCreateProgress(skill.id)
            exercises.add(exerciseEngine.generateExercise(skill.id, (progress.currentDifficulty + 1).coerceAtMost(5)))
        }
        
        return exercises.shuffled()
    }
    
    /**
     * Bereken XP voor een sessie
     */
    fun calculateXp(results: List<ExerciseResult>): Int {
        var xp = 0
        results.forEach { result ->
            if (result.isCorrect) {
                xp += 10 // Base XP
                if (result.responseTimeMs < 2000) xp += 5 // Speed bonus
                if (result.responseTimeMs < 1000) xp += 5 // Fast bonus
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
            accuracy >= 0.7f -> 2
            accuracy >= 0.5f -> 1
            else -> 0
        }
    }
    
    /**
     * Bepaal welke skills getoond kunnen worden (prerequisites behaald)
     */
    suspend fun getAvailableSkills(): List<Skill> {
        val allProgress = progressRepository.getAllProgress()
        val progressMap = allProgress.associateBy { it.skillId }
        
        return SkillDefinitions.ALL_SKILLS.filter { skill ->
            skill.prerequisites.isEmpty() || skill.prerequisites.all { prereq ->
                progressMap[prereq]?.masteryScore?.let { it >= 50 } == true
            }
        }
    }
    
    /**
     * Check of een skill unlocked is
     */
    suspend fun isSkillUnlocked(skillId: String): Boolean {
        val skill = SkillDefinitions.getById(skillId) ?: return false
        if (skill.prerequisites.isEmpty()) return true
        
        val allProgress = progressRepository.getAllProgress()
        val progressMap = allProgress.associateBy { it.skillId }
        
        return skill.prerequisites.all { prereq ->
            progressMap[prereq]?.masteryScore?.let { it >= 50 } == true
        }
    }
    
    private suspend fun determineFocusSkill(weakSkills: List<SkillProgress>): Skill {
        // Prioriteit: zwakke skills met minste mastery
        return if (weakSkills.isNotEmpty()) {
            val weakest = weakSkills.minByOrNull { it.masteryScore }
            SkillDefinitions.getById(weakest?.skillId ?: "arithmetic_add_10")!!
        } else {
            getRandomFreeSkill()
        }
    }
    
    private fun getRandomFreeSkill(): Skill {
        val freeSkills = SkillDefinitions.getFreeSkills()
        return freeSkills.random()
    }
    
    private fun getRandomSkill(): Skill {
        return SkillDefinitions.ALL_SKILLS.random()
    }
    
    private suspend fun getNextUnlockableSkill(strongSkills: List<SkillProgress>): Skill? {
        val lockedSkills = SkillDefinitions.ALL_SKILLS.filter { skill ->
            skill !in SkillDefinitions.getFreeSkills() &&
            !isSkillUnlocked(skill.id)
        }
        
        return lockedSkills.firstOrNull { skill ->
            skill.prerequisites.all { prereq ->
                strongSkills.any { it.skillId == prereq && it.masteryScore >= 50 }
            }
        }
    }
}