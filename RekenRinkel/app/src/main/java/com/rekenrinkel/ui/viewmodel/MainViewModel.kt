package com.rekenrinkel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rekenrinkel.data.repository.ProfileRepository
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.ExerciseEngine
import com.rekenrinkel.domain.engine.SessionEngine
import com.rekenrinkel.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
    private val exerciseEngine: ExerciseEngine,
    private val sessionEngine: SessionEngine
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    private val _navigation = MutableSharedFlow<NavigationEvent>()
    val navigation: SharedFlow<NavigationEvent> = _navigation.asSharedFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            profileRepository.getProfile().collect { profile ->
                _uiState.update { it.copy(profile = profile) }
            }
        }
        
        viewModelScope.launch {
            progressRepository.getAllProgress().collect { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
        }
    }
    
    fun startSession() {
        viewModelScope.launch {
            val exercises = sessionEngine.buildSession()
            _navigation.emit(NavigationEvent.StartSession(exercises))
        }
    }
    
    fun openProfile() {
        viewModelScope.launch {
            _navigation.emit(NavigationEvent.OpenProfile)
        }
    }
    
    fun openSettings() {
        viewModelScope.launch {
            _navigation.emit(NavigationEvent.OpenSettings)
        }
    }
    
    fun openParentDashboard() {
        viewModelScope.launch {
            _navigation.emit(NavigationEvent.OpenParentDashboard)
        }
    }
    
    fun openPremium() {
        viewModelScope.launch {
            _navigation.emit(NavigationEvent.OpenPremium)
        }
    }
    
    fun updateProfileName(name: String) {
        viewModelScope.launch {
            val current = uiState.value.profile ?: return@launch
            val updated = current.copy(name = name)
            profileRepository.updateProfile(updated)
        }
    }
    
    fun updateProfileTheme(theme: Theme) {
        viewModelScope.launch {
            val current = uiState.value.profile ?: return@launch
            val updated = current.copy(theme = theme)
            profileRepository.updateProfile(updated)
        }
    }
    
    fun toggleSound(enabled: Boolean) {
        // TODO: Update in DataStore
    }
    
    fun completeOnboarding(name: String, theme: Theme) {
        viewModelScope.launch {
            profileRepository.createProfile(name, theme)
        }
    }
}

data class MainUiState(
    val profile: Profile? = null,
    val progress: List<SkillProgress> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class NavigationEvent {
    data object OpenProfile : NavigationEvent()
    data object OpenSettings : NavigationEvent()
    data object OpenParentDashboard : NavigationEvent()
    data object OpenPremium : NavigationEvent()
    data class StartSession(val exercises: List<Exercise>) : NavigationEvent()
}