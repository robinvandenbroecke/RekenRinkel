package com.rekenrinkel.data.repository

import com.rekenrinkel.data.local.dao.SkillProgressDao
import com.rekenrinkel.data.local.entity.SkillProgressEntity
import com.rekenrinkel.domain.model.SkillProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * Repository voor skill progress met enhanced model mapping.
 * Database entity gebruikt nog oude veldnamen, model gebruikt nieuwe.
 */
open class ProgressRepository(private val skillProgressDao: SkillProgressDao) {

    fun getProgress(skillId: String): Flow<SkillProgress?> {
        return skillProgressDao.getProgress(skillId).map { entity ->
            entity?.let {
                SkillProgress(
                    skillId = it.skillId,
                    masteryScore = it.masteryScore,
                    currentDifficultyTier = it.currentDifficulty,
                    correctCount = it.correctAnswers,
                    incorrectCount = it.wrongAnswers,
                    averageResponseTimeMs = it.averageResponseTimeMs,
                    lastPracticedAt = it.lastPracticed,
                    streakCorrect = 0,
                    streakIncorrect = 0,
                    errorTypeSummary = emptyMap(),
                    isUnlocked = it.masteryScore > 0,
                    masteredAt = if (it.masteryScore >= 90) System.currentTimeMillis() else null
                )
            }
        }
    }

    open fun getAllProgress(): Flow<List<SkillProgress>> {
        return skillProgressDao.getAllProgress().map { entities ->
            entities.map {
                SkillProgress(
                    skillId = it.skillId,
                    masteryScore = it.masteryScore,
                    currentDifficultyTier = it.currentDifficulty,
                    correctCount = it.correctAnswers,
                    incorrectCount = it.wrongAnswers,
                    averageResponseTimeMs = it.averageResponseTimeMs,
                    lastPracticedAt = it.lastPracticed,
                    streakCorrect = 0,
                    streakIncorrect = 0,
                    errorTypeSummary = emptyMap(),
                    isUnlocked = it.masteryScore > 0,
                    masteredAt = if (it.masteryScore >= 90) System.currentTimeMillis() else null
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
                currentDifficultyTier = existing.currentDifficulty,
                correctCount = existing.correctAnswers,
                incorrectCount = existing.wrongAnswers,
                averageResponseTimeMs = existing.averageResponseTimeMs,
                lastPracticedAt = existing.lastPracticed,
                streakCorrect = 0,
                streakIncorrect = 0,
                errorTypeSummary = emptyMap(),
                isUnlocked = existing.masteryScore > 0,
                masteredAt = if (existing.masteryScore >= 90) System.currentTimeMillis() else null
            )
        }

        val new = SkillProgress(skillId = skillId)
        skillProgressDao.insertProgress(new.toEntity())
        return new
    }

    suspend fun updateProgress(progress: SkillProgress) {
        skillProgressDao.updateProgress(progress.toEntity())
    }

    suspend fun updateProgress(progress: com.rekenrinkel.domain.model.SkillProgress) {
        skillProgressDao.updateProgress(progress.toEntity())
    }

    suspend fun recordResult(skillId: String, isCorrect: Boolean, responseTimeMs: Long) {
        val current = getOrCreateProgress(skillId)

        val newCorrect = current.correctCount + if (isCorrect) 1 else 0
        val newWrong = current.incorrectCount + if (isCorrect) 0 else 1
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
            isCorrect && responseTimeMs < 3000 && current.currentDifficultyTier < 5 -> current.currentDifficultyTier + 1
            !isCorrect && current.currentDifficultyTier > 1 -> current.currentDifficultyTier - 1
            else -> current.currentDifficultyTier
        }

        val updated = current.copy(
            masteryScore = newMastery,
            correctCount = newCorrect,
            incorrectCount = newWrong,
            averageResponseTimeMs = newAvgTime,
            lastPracticedAt = System.currentTimeMillis(),
            currentDifficultyTier = newDifficulty
        )

        skillProgressDao.updateProgress(updated.toEntity())
    }

    suspend fun getWeakSkills(): List<SkillProgress> {
        return skillProgressDao.getWeakSkills().map {
            SkillProgress(
                skillId = it.skillId,
                masteryScore = it.masteryScore,
                currentDifficultyTier = it.currentDifficulty,
                correctCount = it.correctAnswers,
                incorrectCount = it.wrongAnswers,
                averageResponseTimeMs = it.averageResponseTimeMs,
                lastPracticedAt = it.lastPracticed,
                streakCorrect = 0,
                streakIncorrect = 0,
                errorTypeSummary = emptyMap(),
                isUnlocked = it.masteryScore > 0,
                masteredAt = if (it.masteryScore >= 90) System.currentTimeMillis() else null
            )
        }
    }

    suspend fun getStrongSkills(): List<SkillProgress> {
        return skillProgressDao.getStrongSkills().map {
            SkillProgress(
                skillId = it.skillId,
                masteryScore = it.masteryScore,
                currentDifficultyTier = it.currentDifficulty,
                correctCount = it.correctAnswers,
                incorrectCount = it.wrongAnswers,
                averageResponseTimeMs = it.averageResponseTimeMs,
                lastPracticedAt = it.lastPracticed,
                streakCorrect = 0,
                streakIncorrect = 0,
                errorTypeSummary = emptyMap(),
                isUnlocked = it.masteryScore > 0,
                masteredAt = if (it.masteryScore >= 90) System.currentTimeMillis() else null
            )
        }
    }

    private fun SkillProgress.toEntity() = SkillProgressEntity(
        skillId = skillId,
        masteryScore = masteryScore,
        correctAnswers = correctCount,
        wrongAnswers = incorrectCount,
        averageResponseTimeMs = averageResponseTimeMs,
        lastPracticed = lastPracticedAt,
        currentDifficulty = currentDifficultyTier
    )

    suspend fun clearAll() {
        skillProgressDao.clearAll()
    }
}