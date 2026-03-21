package com.rekenrinkel.data.repository

import com.rekenrinkel.data.local.dao.SkillProgressDao
import com.rekenrinkel.data.local.entity.SkillProgressEntity
import com.rekenrinkel.domain.content.CpaPhase
import com.rekenrinkel.domain.model.SkillProgress
import com.rekenrinkel.domain.engine.ProgressRepositoryInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * Repository voor skill progress met enhanced model mapping.
 * Database entity gebruikt nog oude veldnamen, model gebruikt nieuwe.
 */
open class ProgressRepository(private val skillProgressDao: SkillProgressDao) : ProgressRepositoryInterface {

    override fun getProgress(skillId: String): Flow<SkillProgress?> {
        return skillProgressDao.getProgress(skillId).map { entity ->
            entity?.let {
                SkillProgress(
                    skillId = it.skillId,
                    masteryScore = it.masteryScore,
                    currentDifficultyTier = it.currentDifficulty,
                    currentCpaPhase = it.currentCpaPhase.toCpaPhase(),
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

    override fun getAllProgress(): Flow<List<SkillProgress>> {
        return skillProgressDao.getAllProgress().map { entities ->
            entities.map {
                SkillProgress(
                    skillId = it.skillId,
                    masteryScore = it.masteryScore,
                    currentDifficultyTier = it.currentDifficulty,
                    currentCpaPhase = it.currentCpaPhase.toCpaPhase(),
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
                currentCpaPhase = existing.currentCpaPhase.toCpaPhase(),
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
                currentCpaPhase = it.currentCpaPhase.toCpaPhase(),
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
                currentCpaPhase = it.currentCpaPhase.toCpaPhase(),
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

    // PATCH 4: Update CPA fase voor een skill
    suspend fun updateCpaPhase(skillId: String, newPhase: CpaPhase) {
        val current = getOrCreateProgress(skillId)
        val updated = current.copy(currentCpaPhase = newPhase)
        skillProgressDao.updateProgress(updated.toEntity())
    }

    // PATCH 4: Bepaal volgende CPA fase gebaseerd op mastery
    fun determineNextCpaPhase(currentPhase: CpaPhase, masteryScore: Int, attempts: Int): CpaPhase {
        return when (currentPhase) {
            CpaPhase.CONCRETE -> 
                if (masteryScore >= 60 && attempts >= 5) CpaPhase.PICTORIAL else CpaPhase.CONCRETE
            CpaPhase.PICTORIAL -> 
                if (masteryScore >= 75 && attempts >= 8) CpaPhase.ABSTRACT else CpaPhase.PICTORIAL
            CpaPhase.ABSTRACT -> 
                if (masteryScore >= 85) CpaPhase.MIXED_TRANSFER else CpaPhase.ABSTRACT
            CpaPhase.MIXED_TRANSFER -> CpaPhase.MIXED_TRANSFER
        }
    }

    private fun SkillProgress.toEntity() = SkillProgressEntity(
        skillId = skillId,
        masteryScore = masteryScore,
        correctAnswers = correctCount,
        wrongAnswers = incorrectCount,
        averageResponseTimeMs = averageResponseTimeMs,
        lastPracticed = lastPracticedAt,
        currentDifficulty = currentDifficultyTier,
        currentCpaPhase = currentCpaPhase.name
    )

    private fun String.toCpaPhase(): CpaPhase = try {
        CpaPhase.valueOf(this)
    } catch (e: IllegalArgumentException) {
        CpaPhase.CONCRETE
    }

    /**
     * Count distinct skills practiced today (sessions completed today).
     */
    suspend fun getSessionsCompletedToday(): Int {
        val allProgress = skillProgressDao.getAllProgressSync()
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        return allProgress.count { entity ->
            (entity.lastPracticed ?: 0L) >= todayStart
        }
    }

    suspend fun clearAll() {
        skillProgressDao.clearAll()
    }
}