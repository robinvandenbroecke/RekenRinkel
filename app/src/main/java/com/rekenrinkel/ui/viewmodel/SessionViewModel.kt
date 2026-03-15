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
import kotlin.math.max

class SessionViewModel(
    private val progressRepository: ProgressRepository,
    private val exerciseEngine: ExerciseEngine,
    private val sessionEngine: SessionEngine,
    private val exerciseValidator: ExerciseValidator
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    
    private val _navigation = MutableSharedFlow<SessionNavigationEvent>()
    val navigation: SharedFlow<SessionNavigationEvent> = _navigation.asSharedFlow()
    
    // Timer voor response time tracking
    private var exerciseStartTime: Long = 0
    
    /**
     * Start een nieuwe sessie met de gegeven oefeningen
     */
    fun startSession(exercises: List<Exercise>) {
        exerciseStartTime = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                exercises = exercises,
                currentIndex = 0,
                results = emptyList(),
                isActive = true,
                showFeedback = false,
                lastAnswerCorrect = null
            )
        }
    }
    
    /**
     * Start de timer voor een nieuwe oefening
     * Moet worden aangeroepen wanneer de oefening wordt getoond
     */
    fun startExerciseTimer() {
        exerciseStartTime = System.currentTimeMillis()
    }
    
    /**
     * Verwerk een antwoord met response time tracking
     */
    fun submitAnswer(answer: String) {
        val state = _uiState.value
        val currentExercise = state.currentExercise ?: return
        
        // Bereken response time
        val responseTimeMs = System.currentTimeMillis() - exerciseStartTime
        
        viewModelScope.launch {
            val isCorrect = exerciseValidator.validate(currentExercise, answer)
            
            val result = ExerciseResult(
                exerciseId = currentExercise.id,
                skillId = currentExercise.skillId,
                isCorrect = isCorrect,
                responseTimeMs = responseTimeMs,
                givenAnswer = answer
            )
            
            // Update progress met echte response time
            progressRepository.recordResult(
                currentExercise.skillId,
                isCorrect,
                responseTimeMs
            )
            
            // Voeg toe aan resultaten
            val newResults = state.results + result
            _uiState.update {
                it.copy(
                    results = newResults,
                    showFeedback = true,
                    lastAnswerCorrect = isCorrect
                )
            }
        }
    }
    
    /**
     * Sla de huidige oefening over
     */
    fun skipExercise() {
        val state = _uiState.value
        
        // Markeer als fout met een hoge response time (skip)
        val currentExercise = state.currentExercise
        if (currentExercise != null) {
            val skippedResult = ExerciseResult(
                exerciseId = currentExercise.id,
                skillId = currentExercise.skillId,
                isCorrect = false,
                responseTimeMs = 30_000, // 30s penalty voor skip
                givenAnswer = "[skipped]"
            )
            
            viewModelScope.launch {
                progressRepository.recordResult(
                    currentExercise.skillId,
                    isCorrect = false,
                    responseTimeMs = 30_000
                )
                
                val newResults = state.results + skippedResult
                _uiState.update {
                    it.copy(
                        results = newResults,
                        showFeedback = true,
                        lastAnswerCorrect = false
                    )
                }
            }
        }
    }
    
    /**
     * Ga naar de volgende oefening
     */
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
            // Start timer voor nieuwe oefening
            exerciseStartTime = System.currentTimeMillis()
        }
    }
    
    /**
     * Voltooi de sessie
     */
    private fun completeSession() {
        viewModelScope.launch {
            val state = _uiState.value
            
            // Bereken statistieken
            val xp = sessionEngine.calculateXp(state.results)
            val stars = sessionEngine.calculateStars(state.results.accuracy())
            val averageResponseTime = if (state.results.isNotEmpty()) {
                state.results.map { it.responseTimeMs }.average().toLong()
            } else 0
            
            val sessionResult = SessionResult(
                exercises = state.results,
                xpEarned = xp,
                stars = stars,
                averageResponseTimeMs = averageResponseTime
            )
            
            _navigation.emit(SessionNavigationEvent.SessionComplete(sessionResult))
        }
    }
    
    /**
     * Sla de sessie over en ga terug naar home
     */
    fun skipSession() {
        viewModelScope.launch {
            _navigation.emit(SessionNavigationEvent.BackToHome)
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

sealed class SessionNavigationEvent {
    data class SessionComplete(val result: SessionResult) : SessionNavigationEvent()
    data object BackToHome : SessionNavigationEvent()
}

private fun List<ExerciseResult>.accuracy(): Float {
    if (isEmpty()) return 0f
    val correct = count { it.isCorrect }
    return correct.toFloat() / size
}