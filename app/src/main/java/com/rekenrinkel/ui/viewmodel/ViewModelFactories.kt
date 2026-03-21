package com.rekenrinkel.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.local.RekenRinkelDatabase
import com.rekenrinkel.data.repository.ProfileRepository
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.ExerciseEngine
import com.rekenrinkel.domain.engine.ExerciseValidator
import com.rekenrinkel.domain.engine.LessonEngine
import com.rekenrinkel.domain.engine.SessionEngine

/**
 * Factory for creating MainViewModel with all dependencies
 */
class MainViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val database = RekenRinkelDatabase.getDatabase(context)
            val settingsDataStore = SettingsDataStore(context)
            
            val profileRepository = ProfileRepository(
                database.profileDao(),
                settingsDataStore
            )
            
            val progressRepository = ProgressRepository(
                database.skillProgressDao()
            )
            
            val exerciseEngine = ExerciseEngine()
            val lessonEngine = LessonEngine(exerciseEngine, progressRepository)
            val sessionEngine = SessionEngine(
                exerciseEngine,
                progressRepository
            )
            
            return MainViewModel(
                profileRepository,
                progressRepository,
                exerciseEngine,
                lessonEngine,
                sessionEngine,
                settingsDataStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

