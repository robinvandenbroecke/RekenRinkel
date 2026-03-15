package com.rekenrinkel.data.repository

import com.rekenrinkel.data.local.dao.SkillProgressDao
import com.rekenrinkel.data.local.entity.SkillProgressEntity
import com.rekenrinkel.domain.model.SkillProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class ProgressRepository(private val skillProgressDao: SkillProgressDao) {
    
    fun getProgress(skillId: String): Flow<SkillProgress?> {
        return skillProgressDao.getProgress(skillId).map { entity ->
            entity?.let {
                SkillProgress(
                    skillId = it.skillId,
                    masteryScore = it.masteryScore,
                    correctAnswers = it.correctAnswers,
                    wrongAnswers = it.wrongAnswers,
                    averageResponseTimeMs = it.averageResponseTimeMs,
                    lastPracticed = it.lastPracticed,
                    currentDifficulty = it.currentDifficulty
                )
            }
        }
    }
    
    fun getAllProgress(): Flow<List<SkillProgress>> {
        return skillProgressDao.getAllProgress().map { entities ->
            entities.map {
                SkillProgress(
                    skillId = it.skillId,
                    masteryScore = it.masteryScore,
                    correctAnswers = it.correctAnswers,
                    wrongAnswers = it.wrongAnswers,
                    averageResponseTimeMs = it.averageResponseTimeMs,
                    lastPracticed = it.lastPracticed,
                    currentDifficulty = it.currentDifficulty
                )
            }
        }
    }
    
    suspend fun getOrCreateProgress(skillId: String): SkillProgress {
        val existing = skillProgressDao.getProgress(skillId).firstOrNull()
        if (existing != null) {
            return SkillProgress(
                skillId = existing.skillId,
                masteryScore = existing.masteryScore,
                correctAnswers = existing.correctAnswers,
                wrongAnswers = existing.wrongAnswers,
                averageResponseTimeMs = existing.averageResponseTimeMs,
                lastPracticed = existing.lastPracticed,
                currentDifficulty = existing.currentDifficulty
            )
        }
        
        val new = SkillProgress(skillId = skillId)
        skillProgressDao.insertProgress(new.toEntity())
        return new
    }
    
    suspend fun updateProgress(progress: SkillProgress) {
        skillProgressDao.updateProgress(progress.toEntity())
    }
    
    suspend fun recordResult(skillId: String, isCorrect: Boolean, responseTimeMs: Long) {
        val current = getOrCreateProgress(skillId)
        
        val newCorrect = current.correctAnswers + if (isCorrect) 1 else 0
        val newWrong = current.wrongAnswers + if (isCorrect) 0 else 1
        val totalAttempts = newCorrect + newWrong
        
        // Calculate new mastery score
        val successRate = if (totalAttempts > 0) newCorrect.toFloat() / totalAttempts else 0f
        val speedBonus = if (responseTimeMs < 3000) 10 else 0
        val newMastery = minOf(100, (successRate * 100).toInt() + speedBonus)
        
        // Calculate new average response time
        val newAvgTime = if (current.averageResponseTimeMs == 0L) {
            responseTimeMs
        } else {
            (current.averageResponseTimeMs * (totalAttempts - 1) + responseTimeMs) / totalAttempts
        }
        
        // Adjust difficulty
        val newDifficulty = when {
            isCorrect && responseTimeMs < 3000 && current.currentDifficulty < 5 -> current.currentDifficulty + 1
            !isCorrect && current.currentDifficulty > 1 -> current.currentDifficulty - 1
            else -> current.currentDifficulty
        }
        
        val updated = current.copy(
            masteryScore = newMastery,
            correctAnswers = newCorrect,
            wrongAnswers = newWrong,
            averageResponseTimeMs = newAvgTime,
            lastPracticed = System.currentTimeMillis(),
            currentDifficulty = newDifficulty
        )
        
        skillProgressDao.updateProgress(updated.toEntity())
    }
    
    suspend fun getWeakSkills(): List<SkillProgress> {
        return skillProgressDao.getWeakSkills().map {
            SkillProgress(
                skillId = it.skillId,
                masteryScore = it.masteryScore,
                correctAnswers = it.correctAnswers,
                wrongAnswers = it.wrongAnswers,
                averageResponseTimeMs = it.averageResponseTimeMs,
                lastPracticed = it.lastPracticed,
                currentDifficulty = it.currentDifficulty
            )
        }
    }
    
    suspend fun getStrongSkills(): List<SkillProgress> {
        return skillProgressDao.getStrongSkills().map {
            SkillProgress(
                skillId = it.skillId,
                masteryScore = it.masteryScore,
                correctAnswers = it.correctAnswers,
                wrongAnswers = it.wrongAnswers,
                averageResponseTimeMs = it.averageResponseTimeMs,
                lastPracticed = it.lastPracticed,
                currentDifficulty = it.currentDifficulty
            )
        }
    }
    
    private fun SkillProgress.toEntity() = SkillProgressEntity(
        skillId = skillId,
        masteryScore = masteryScore,
        correctAnswers = correctAnswers,
        wrongAnswers = wrongAnswers,
        averageResponseTimeMs = averageResponseTimeMs,
        lastPracticed = lastPracticed,
        currentDifficulty = currentDifficulty
    )
}