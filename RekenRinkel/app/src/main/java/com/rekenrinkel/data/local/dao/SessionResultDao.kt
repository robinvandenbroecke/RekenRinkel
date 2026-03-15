package com.rekenrinkel.data.local.dao

import androidx.room.*
import com.rekenrinkel.data.local.entity.SessionResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionResultDao {
    @Query("SELECT * FROM session_results ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionResultEntity>>
    
    @Query("SELECT * FROM session_results ORDER BY startTime DESC LIMIT 10")
    suspend fun getRecentSessions(): List<SessionResultEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionResultEntity)
    
    @Query("SELECT COUNT(*) FROM session_results")
    suspend fun getTotalSessions(): Int
    
    @Query("SELECT SUM(xpEarned) FROM session_results")
    suspend fun getTotalXpEarned(): Int?
}