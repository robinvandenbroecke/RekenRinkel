package com.rekenrinkel.data.local.dao

import androidx.room.*
import com.rekenrinkel.data.local.entity.ExerciseResultEntity

@Dao
interface ExerciseResultDao {
    @Query("SELECT * FROM exercise_results WHERE sessionId = :sessionId")
    suspend fun getResultsForSession(sessionId: String): List<ExerciseResultEntity>
    
    @Query("SELECT * FROM exercise_results WHERE skillId = :skillId ORDER BY timestamp DESC LIMIT 10")
    suspend fun getRecentResultsForSkill(skillId: String): List<ExerciseResultEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ExerciseResultEntity)
    
    @Query("SELECT * FROM exercise_results WHERE isCorrect = 0 ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentErrors(): List<ExerciseResultEntity>
}