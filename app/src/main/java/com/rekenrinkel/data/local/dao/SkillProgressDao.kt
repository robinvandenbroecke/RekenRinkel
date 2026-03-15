package com.rekenrinkel.data.local.dao

import androidx.room.*
import com.rekenrinkel.data.local.entity.SkillProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillProgressDao {
    @Query("SELECT * FROM skill_progress WHERE skillId = :skillId")
    fun getProgress(skillId: String): Flow<SkillProgressEntity?>
    
    @Query("SELECT * FROM skill_progress")
    fun getAllProgress(): Flow<List<SkillProgressEntity>>
    
    @Query("SELECT * FROM skill_progress")
    suspend fun getAllProgressSync(): List<SkillProgressEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: SkillProgressEntity)
    
    @Update
    suspend fun updateProgress(progress: SkillProgressEntity)
    
    @Query("SELECT * FROM skill_progress WHERE masteryScore < 75 ORDER BY masteryScore ASC")
    suspend fun getWeakSkills(): List<SkillProgressEntity>
    
    @Query("SELECT * FROM skill_progress WHERE masteryScore >= 75 ORDER BY masteryScore DESC")
    suspend fun getStrongSkills(): List<SkillProgressEntity>
}