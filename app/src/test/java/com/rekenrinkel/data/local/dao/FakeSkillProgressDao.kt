package com.rekenrinkel.data.local.dao

import com.rekenrinkel.data.local.entity.SkillProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake DAO for testing
 */
class FakeSkillProgressDao : SkillProgressDao {
    private val data = mutableMapOf<String, SkillProgressEntity>()
    
    override fun getProgress(skillId: String): Flow<SkillProgressEntity?> {
        return flow { emit(data[skillId]) }
    }
    
    override fun getAllProgress(): Flow<List<SkillProgressEntity>> {
        return flow { emit(data.values.toList()) }
    }
    
    override suspend fun getAllProgressSync(): List<SkillProgressEntity> {
        return data.values.toList()
    }
    
    override suspend fun insertProgress(progress: SkillProgressEntity) {
        data[progress.skillId] = progress
    }
    
    override suspend fun updateProgress(progress: SkillProgressEntity) {
        data[progress.skillId] = progress
    }
    
    override suspend fun getWeakSkills(): List<SkillProgressEntity> {
        return data.values.filter { it.masteryScore < 50 }
    }
    
    override suspend fun getStrongSkills(): List<SkillProgressEntity> {
        return data.values.filter { it.masteryScore >= 75 }
    }

    override suspend fun clearAll() {
        data.clear()
    }
}