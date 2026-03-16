package com.rekenrinkel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rekenrinkel.data.datastore.SettingsDataStore
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
    private val sessionEngine: SessionEngine,
    private val settingsDataStore: SettingsDataStore
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
        
        viewModelScope.launch {
            settingsDataStore.soundEnabled.collect { enabled ->
                _uiState.update { it.copy(soundEnabled = enabled) }
            }
        }
        
        viewModelScope.launch {
            settingsDataStore.premiumUnlocked.collect { unlocked ->
                _uiState.update { it.copy(isPremiumUnlocked = unlocked) }
            }
        }

        viewModelScope.launch {
            settingsDataStore.einkModeEnabled.collect { enabled ->
                _uiState.update { it.copy(einkModeEnabled = enabled) }
            }
        }
    }
    
    fun startSession() {
        viewModelScope.launch {
            val isPremium = uiState.value.isPremiumUnlocked
            val exercises = sessionEngine.buildSession(isPremium)
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
            settingsDataStore.setProfileName(name)
        }
    }
    
    fun updateProfileTheme(theme: Theme) {
        viewModelScope.launch {
            val current = uiState.value.profile ?: return@launch
            val updated = current.copy(theme = theme)
            profileRepository.updateProfile(updated)
            settingsDataStore.setTheme(theme)
        }
    }
    
    /**
     * Toggle sound on/off
     */
    fun toggleSound(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSoundEnabled(enabled)
        }
    }
    
    /**
     * Toggle premium unlocked status (for testing/placeholder)
     */
    fun togglePremiumUnlocked(unlocked: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setPremiumUnlocked(unlocked)
        }
    }

    /**
     * Toggle E-Ink mode
     */
    fun toggleEinkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setEinkModeEnabled(enabled)
        }
    }

    /**
     * Reset all progress (skills, sessions, exercises)
     */
    fun resetProgress() {
        viewModelScope.launch {
            progressRepository.clearAll()
        }
    }

    /**
     * Reset profile and all data
     */
    fun resetProfile() {
        viewModelScope.launch {
            progressRepository.clearAll()
            profileRepository.clearAll()
            settingsDataStore.clearAll()
        }
    }

    fun completeOnboarding(name: String, age: Int, theme: Theme) {
        viewModelScope.launch {
            profileRepository.createProfile(name, age, theme)
            settingsDataStore.setProfileName(name)
            settingsDataStore.setTheme(theme)
            settingsDataStore.setOnboardingCompleted(true)
        }
    }
}

data class MainUiState(
    val profile: Profile? = null,
    val progress: List<SkillProgress> = emptyList(),
    val soundEnabled: Boolean = true,
    val isPremiumUnlocked: Boolean = false,
    val einkModeEnabled: Boolean = false,
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