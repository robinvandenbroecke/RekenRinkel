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

/**
 * Factory voor LessonViewModel met alle dependencies
 */
class LessonViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LessonViewModel::class.java)) {
            val database = RekenRinkelDatabase.getDatabase(context)
            val settingsDataStore = SettingsDataStore(context)

            val progressRepository = ProgressRepository(database.skillProgressDao())
            val profileRepository = ProfileRepository(database.profileDao(), settingsDataStore)
            val exerciseEngine = ExerciseEngine()
            val exerciseValidator = ExerciseValidator()

            @Suppress("UNCHECKED_CAST")
            return LessonViewModel(
                progressRepository = progressRepository,
                profileRepository = profileRepository,
                settingsDataStore = settingsDataStore,
                exerciseEngine = exerciseEngine,
                exerciseValidator = exerciseValidator
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}