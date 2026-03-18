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
     * PATCH 3: LessonEngine als EXCLUSIEVE leermotor
     * Geen fallback naar SessionEngine - LessonEngine bepaalt alles
     */
    fun startSession() {
        viewModelScope.launch {
            val profile = uiState.value.profile
            val isPremium = uiState.value.isPremiumUnlocked
            
            if (profile == null) {
                // Geen profiel - kan geen sessie starten
                return@launch
            }
            
            // Gebruik ALLEEN LessonEngine - geen fallback
            val lessonPlan = lessonEngine.buildLesson(
                userProfile = com.rekenrinkel.domain.model.UserProfile(
                    id = profile.id,
                    name = profile.name,
                    age = profile.age,
                    theme = profile.theme
                ),
                isPremiumUnlocked = isPremium
            )
            
            // SessionEngine is nu alleen een helper - niet meer parallel
            _navigation.emit(NavigationEvent.StartSession(lessonPlan.exercises))
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

    /**
     * PATCH 2: Leeftijd bepaalt startband en initiële skillset
     * Geen verplichte placement meer - direct naar home
     */
    fun completeOnboarding(name: String, age: Int, theme: Theme) {
        viewModelScope.launch {
            // Bepaal startband op basis van leeftijd
            val startingBand = determineStartingBand(age)
            val startSkills = determineStartSkillsForAge(age)
            val startCpaPhase = determineStartCpaPhaseForAge(age)
            
            // Maak profiel met leeftijdsbepaalde startinstellingen
            profileRepository.createProfileWithStartConfig(
                name = name,
                age = age,
                theme = theme,
                startingBand = startingBand,
                startSkills = startSkills,
                startCpaPhase = startCpaPhase
            )
            
            settingsDataStore.setProfileName(name)
            settingsDataStore.setTheme(theme)
            settingsDataStore.setOnboardingCompleted(true)
            // PATCH 1: Direct naar home, geen placement verplicht
        }
    }
    
    /**
     * PATCH 2: Bepaal start skills op basis van leeftijd
     * Afgestemd op NL/BE curriculum
     */
    private fun determineStartSkillsForAge(age: Int): List<String> {
        return when (age) {
            5, 6 -> listOf(
                "foundation_subitize_5",
                "foundation_counting", 
                "foundation_more_less",
                "foundation_number_bonds_5",
                "foundation_number_bonds_10"
            )
            7, 8 -> listOf(
                "foundation_number_bonds_20",
                "arithmetic_add_10_concrete",
                "arithmetic_sub_10_concrete",
                "patterns_doubles",
                "patterns_halves",
                "patterns_count_2"
            )
            9, 10, 11 -> listOf(
                "arithmetic_bridge_add",
                "arithmetic_bridge_sub",
                "patterns_count_5",
                "patterns_count_10",
                "advanced_groups",
                "advanced_table_2",
                "advanced_place_value"
            )
            else -> listOf("foundation_subitize_5", "foundation_counting")
        }
    }
    
    /**
     * PATCH 2: Bepaal start CPA fase op basis van leeftijd
     */
    private fun determineStartCpaPhaseForAge(age: Int): com.rekenrinkel.domain.content.CpaPhase {
        return when (age) {
            5, 6 -> com.rekenrinkel.domain.content.CpaPhase.CONCRETE
            7, 8 -> com.rekenrinkel.domain.content.CpaPhase.PICTORIAL
            else -> com.rekenrinkel.domain.content.CpaPhase.ABSTRACT
        }
    }
    
    /**
     * PATCH 2: Voltooi placement met echte analyse
     */
    fun completePlacement(analysis: com.rekenrinkel.domain.engine.PlacementEngine.PlacementAnalysis) {
        viewModelScope.launch {
            val profile = uiState.value.profile ?: return@launch
            
            val updated = profile.copy(
                placementCompleted = true,
                startingBand = analysis.recommendedBand,
                placementAnalysisResult = com.rekenrinkel.domain.model.PlacementAnalysisResult(
                    recommendedBand = analysis.recommendedBand,
                    startSkills = analysis.startSkills,
                    startCpaPhase = analysis.startCpaPhase,
                    difficultyOffset = analysis.difficultyOffset
                )
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
    data class StartSession(val exercises: List<Exercise>) : NavigationEvent()
}