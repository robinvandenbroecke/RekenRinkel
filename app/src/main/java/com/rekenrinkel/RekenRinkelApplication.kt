package com.rekenrinkel

import android.app.Application
import androidx.room.Room
import com.rekenrinkel.data.local.RekenRinkelDatabase

class RekenRinkelApplication : Application() {
    
    lateinit var database: RekenRinkelDatabase
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        database = Room.databaseBuilder(
            applicationContext,
            RekenRinkelDatabase::class.java,
            "rekenrinkel_database"
        ).build()
    }
}