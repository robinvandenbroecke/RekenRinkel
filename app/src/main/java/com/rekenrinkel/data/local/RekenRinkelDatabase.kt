package com.rekenrinkel.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rekenrinkel.data.local.dao.*
import com.rekenrinkel.data.local.entity.*

@Database(
    entities = [
        ProfileEntity::class,
        SkillProgressEntity::class,
        SessionResultEntity::class,
        ExerciseResultEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class RekenRinkelDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun skillProgressDao(): SkillProgressDao
    abstract fun sessionResultDao(): SessionResultDao
    abstract fun exerciseResultDao(): ExerciseResultDao
    
    companion object {
        @Volatile
        private var INSTANCE: RekenRinkelDatabase? = null
        
        fun getDatabase(context: Context): RekenRinkelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RekenRinkelDatabase::class.java,
                    "rekenrinkel_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}