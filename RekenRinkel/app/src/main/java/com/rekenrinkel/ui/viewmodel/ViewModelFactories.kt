package com.rekenrinkel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rekenrinkel.data.repository.ProfileRepository
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.ExerciseEngine
import com.rekenrinkel.domain.engine.ExerciseValidator
import com.rekenrinkel.domain.engine.SessionEngine

class MainViewModelFactory(
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
    private val exerciseEngine: ExerciseEngine,
    private val sessionEngine: SessionEngine
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(
                profileRepository,
                progressRepository,
                exerciseEngine,
                sessionEngine
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SessionViewModelFactory(
    private val progressRepository: ProgressRepository,
    private val exerciseEngine: ExerciseEngine,
    private val sessionEngine: SessionEngine,
    private val exerciseValidator: ExerciseValidator
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            return SessionViewModel(
                progressRepository,
                exerciseEngine,
                sessionEngine,
                exerciseValidator
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}