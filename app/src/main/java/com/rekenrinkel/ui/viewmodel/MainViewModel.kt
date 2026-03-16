package com.rekenrinkel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.repository.ProfileRepository
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.ExerciseEngine
import com.rekenrinkel.domain.engine.LessonEngine
import com.rekenrinkel.domain.engine.SessionEngine
import com.rekenrinkel.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
    private val exerciseEngine: ExerciseEngine,
    private val lessonEngine: LessonEngine,
    private val sessionEngine: SessionEngine,  // Helper voor legacy/noodgevallen
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
    
    /**
     * PATCH 2: LessonEngine als primaire leermotor
     * Bouwt didactische les met warm-up, focus, review, challenge
     */
    fun startSession() {
        viewModelScope.launch {
            val profile = uiState.value.profile
            val isPremium = uiState.value.isPremiumUnlocked
            
            // Gebruik LessonEngine voor didactische lesopbouw
            val lessonPlan = profile?.let {
                lessonEngine.buildLesson(
                    userProfile = com.rekenrinkel.domain.model.UserProfile(
                        id = it.id,
                        name = it.name,
                        age = it.age,
                        theme = it.theme
                    ),
                    isPremiumUnlocked = isPremium
                )
            }
            
            // Converteer LessonPlan naar exercises voor UI
            val exercises = lessonPlan?.exercises ?: sessionEngine.buildSession(isPremium)
            
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
            // Niet direct naar home - placement komt eerst
        }
    }
    
    /**
     * PATCH 1: Voltooi placement en bepaal startniveau
     */
    fun completePlacement() {
        viewModelScope.launch {
            val profile = uiState.value.profile ?: return@launch
            
            // Simuleer placement analyse (in echte implementatie: gebruik PlacementEngine resultaten)
            val updated = profile.copy(
                placementCompleted = true,
                startingBand = determineStartingBand(profile.age)
            )
            profileRepository.updateProfile(updated)
        }
    }
    
    private fun determineStartingBand(age: Int): com.rekenrinkel.domain.model.StartingBand {
        return when (age) {
            in 5..6 -> com.rekenrinkel.domain.model.StartingBand.FOUNDATION
            in 7..8 -> com.rekenrinkel.domain.model.StartingBand.EARLY_ARITHMETIC
            else -> com.rekenrinkel.domain.model.StartingBand.EXTENDED
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