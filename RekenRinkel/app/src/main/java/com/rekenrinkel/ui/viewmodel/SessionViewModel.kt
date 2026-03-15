package com.rekenrinkel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.ExerciseEngine
import com.rekenrinkel.domain.engine.ExerciseValidator
import com.rekenrinkel.domain.engine.SessionEngine
import com.rekenrinkel.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class SessionViewModel(
    private val progressRepository: ProgressRepository,
    private val exerciseEngine: ExerciseEngine,
    private val sessionEngine: SessionEngine,
    private val exerciseValidator: ExerciseValidator
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    
    private val _navigation = MutableSharedFlow<NavigationEvent>()
    val navigation: SharedFlow<NavigationEvent> = _navigation.asSharedFlow()
    
    fun startSession(exercises: List<Exercise>) {
        _uiState.update {
            it.copy(
                exercises = exercises,
                currentIndex = 0,
                results = emptyList(),
                isActive = true
            )
        }
    }
    
    fun submitAnswer(answer: String) {
        val state = _uiState.value
        val currentExercise = state.currentExercise ?: return
        
        viewModelScope.launch {
            val isCorrect = exerciseValidator.validate(currentExercise, answer)
            val result = ExerciseResult(
                exerciseId = currentExercise.id,
                skillId = currentExercise.skillId,
                isCorrect = isCorrect,
                responseTimeMs = 0, // TODO: Track actual response time
                givenAnswer = answer
            )
            
            // Update progress
            progressRepository.recordResult(
                currentExercise.skillId,
                isCorrect,
                0L // TODO: Actual response time
            )
            
            // Add to results
            val newResults = state.results + result
            _uiState.update {
                it.copy(results = newResults)
            }
            
            // Show feedback
            _uiState.update {
                it.copy(
                    showFeedback = true,
                    lastAnswerCorrect = isCorrect
                )
            }
        }
    }
    
    fun nextExercise() {
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1
        
        if (nextIndex >= state.exercises.size) {
            completeSession()
        } else {
            _uiState.update {
                it.copy(
                    currentIndex = nextIndex,
                    showFeedback = false,
                    lastAnswerCorrect = null
                )
            }
        }
    }
    
    private fun completeSession() {
        viewModelScope.launch {
            val state = _uiState.value
            val xp = sessionEngine.calculateXp(state.results)
            val stars = sessionEngine.calculateStars(state.results.accuracy())
            
            val sessionResult = SessionResult(
                exercises = state.results,
                xpEarned = xp,
                stars = stars
            )
            
            _navigation.emit(NavigationEvent.SessionComplete(sessionResult))
        }
    }
    
    fun skipSession() {
        viewModelScope.launch {
            _navigation.emit(NavigationEvent.BackToHome)
        }
    }
}

data class SessionUiState(
    val exercises: List<Exercise> = emptyList(),
    val currentIndex: Int = 0,
    val results: List<ExerciseResult> = emptyList(),
    val isActive: Boolean = false,
    val showFeedback: Boolean = false,
    val lastAnswerCorrect: Boolean? = null
) {
    val currentExercise: Exercise? = exercises.getOrNull(currentIndex)
    val totalExercises: Int = exercises.size
    val progress: Float = if (totalExercises > 0) currentIndex.toFloat() / totalExercises else 0f
}

sealed class NavigationEvent {
    data class SessionComplete(val result: SessionResult) : NavigationEvent()
    data object BackToHome : NavigationEvent()
}

private fun List<ExerciseResult>.accuracy(): Float {
    if (isEmpty()) return 0f
    val correct = count { it.isCorrect }
    return correct.toFloat() / size
}