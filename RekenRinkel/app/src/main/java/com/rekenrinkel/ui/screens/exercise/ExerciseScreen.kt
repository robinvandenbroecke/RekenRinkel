package com.rekenrinkel.ui.screens.exercise

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rekenrinkel.domain.model.Exercise
import com.rekenrinkel.domain.model.ExerciseType
import com.rekenrinkel.domain.model.VisualType
import com.rekenrinkel.ui.components.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(
    exercise: Exercise,
    currentIndex: Int,
    totalExercises: Int,
    onAnswer: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var typedAnswer by remember { mutableStateOf("") }
    var selectedOption by remember { mutableStateOf<String?>(null) }
    
    // Reset state when exercise changes
    LaunchedEffect(exercise.id) {
        showFeedback = false
        isCorrect = false
        typedAnswer = ""
        selectedOption = null
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Oefening $currentIndex van $totalExercises") },
                navigationIcon = {
                    IconButton(onClick = onSkip) {
                        Icon(Icons.Default.Close, contentDescription = "Sluiten")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Progress bar
            ProgressBar(
                current = currentIndex,
                total = totalExercises,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Question card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Question text
                    Text(
                        text = exercise.question,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Visual data
                    VisualContent(exercise = exercise)
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Answer input
                    AnswerContent(
                        exercise = exercise,
                        typedAnswer = typedAnswer,
                        selectedOption = selectedOption,
                        onTypedAnswerChange = { typedAnswer = it },
                        onOptionSelected = { option ->
                            selectedOption = option
                            handleAnswer(option, exercise, onAnswer) { correct ->
                                isCorrect = correct
                                showFeedback = true
                            }
                        },
                        onConfirmTyped = {
                            handleAnswer(typedAnswer, exercise, onAnswer) { correct ->
                                isCorrect = correct
                                showFeedback = true
                            }
                        },
                        enabled = !showFeedback
                    )
                }
            }
            
            // Hint button
            if (exercise.hint != null) {
                TextButton(
                    onClick = { /* Show hint */ },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("💡 Hint")
                }
            }
        }
        
        // Feedback overlay
        if (showFeedback) {
            FeedbackOverlay(
                isCorrect = isCorrect,
                onDismiss = {
                    showFeedback = false
                    // Auto-advance after delay
                }
            )
            
            LaunchedEffect(showFeedback) {
                delay(1500)
                showFeedback = false
            }
        }
    }
}

@Composable
private fun VisualContent(exercise: Exercise) {
    when (exercise.type) {
        ExerciseType.VISUAL_QUANTITY -> {
            exercise.visualData?.count?.let { count ->
                VisualDots(count = count)
            }
        }
        ExerciseType.VISUAL_GROUPS -> {
            when {
                exercise.visualData?.groups != null -> {
                    VisualGroups(groups = exercise.visualData.groups)
                }
                exercise.visualData?.firstNumber != null && 
                exercise.visualData?.secondNumber != null -> {
                    VisualGroups(
                        groups = listOf(
                            exercise.visualData.firstNumber,
                            exercise.visualData.secondNumber
                        )
                    )
                }
            }
        }
        else -> {
            // No visual for other types
        }
    }
}

@Composable
private fun AnswerContent(
    exercise: Exercise,
    typedAnswer: String,
    selectedOption: String?,
    onTypedAnswerChange: (String) -> Unit,
    onOptionSelected: (String) -> Unit,
    onConfirmTyped: () -> Unit,
    enabled: Boolean
) {
    when (exercise.type) {
        ExerciseType.MULTIPLE_CHOICE,
        ExerciseType.COMPARE_NUMBERS -> {
            // Multiple choice buttons
            exercise.options?.let { options ->
                Column {
                    options.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { option ->
                                AnswerButton(
                                    text = option,
                                    onClick = { onOptionSelected(option) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(4.dp),
                                    isSelected = selectedOption == option,
                                    enabled = enabled
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        
        ExerciseType.TYPED_NUMERIC,
        ExerciseType.MISSING_NUMBER,
        ExerciseType.SIMPLE_SEQUENCE -> {
            // Number pad
            NumberPad(
                onNumberClick = { num ->
                    if (enabled && typedAnswer.length < 3) {
                        onTypedAnswerChange(typedAnswer + num)
                    }
                },
                onBackspace = {
                    if (enabled && typedAnswer.isNotEmpty()) {
                        onTypedAnswerChange(typedAnswer.dropLast(1))
                    }
                },
                onConfirm = {
                    if (enabled && typedAnswer.isNotEmpty()) {
                        onConfirmTyped()
                    }
                },
                currentValue = typedAnswer
            )
        }
        
        ExerciseType.VISUAL_QUANTITY,
        ExerciseType.VISUAL_GROUPS -> {
            // Multiple choice or number pad depending on distractors
            if (!exercise.distractors.isNullOrEmpty()) {
                val allOptions = (exercise.distractors + exercise.correctAnswer).shuffled()
                Column {
                    allOptions.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { option ->
                                AnswerButton(
                                    text = option,
                                    onClick = { onOptionSelected(option) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(4.dp),
                                    isSelected = selectedOption == option,
                                    enabled = enabled
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } else {
                NumberPad(
                    onNumberClick = { num ->
                        if (enabled && typedAnswer.length < 3) {
                            onTypedAnswerChange(typedAnswer + num)
                        }
                    },
                    onBackspace = {
                        if (enabled && typedAnswer.isNotEmpty()) {
                            onTypedAnswerChange(typedAnswer.dropLast(1))
                        }
                    },
                    onConfirm = {
                        if (enabled && typedAnswer.isNotEmpty()) {
                            onConfirmTyped()
                        }
                    },
                    currentValue = typedAnswer
                )
            }
        }
        
        ExerciseType.NUMBER_LINE_CLICK -> {
            // Simplified - just use number pad for now
            NumberPad(
                onNumberClick = { num ->
                    if (enabled && typedAnswer.length < 3) {
                        onTypedAnswerChange(typedAnswer + num)
                    }
                },
                onBackspace = {
                    if (enabled && typedAnswer.isNotEmpty()) {
                        onTypedAnswerChange(typedAnswer.dropLast(1))
                    }
                },
                onConfirm = {
                    if (enabled && typedAnswer.isNotEmpty()) {
                        onConfirmTyped()
                    }
                },
                currentValue = typedAnswer
            )
        }
    }
}

private fun handleAnswer(
    answer: String,
    exercise: Exercise,
    onAnswer: (String) -> Unit,
    onShowFeedback: (Boolean) -> Unit
) {
    val isCorrect = validateAnswer(answer, exercise)
    onShowFeedback(isCorrect)
    onAnswer(answer)
}

private fun validateAnswer(answer: String, exercise: Exercise): Boolean {
    return answer.trim().equals(exercise.correctAnswer, ignoreCase = true) ||
           answer.trim().toIntOrNull()?.toString() == exercise.correctAnswer
}