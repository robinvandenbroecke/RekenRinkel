package com.rekenrinkel.data.local.dao

import androidx.room.*
import com.rekenrinkel.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles LIMIT 1")
    fun getProfile(): Flow<ProfileEntity?>
    
    @Query("SELECT * FROM profiles LIMIT 1")
    suspend fun getProfileSync(): ProfileEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)
    
    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles")
    suspend fun clearAll()
}