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
    showFeedback: Boolean,
    isLastAnswerCorrect: Boolean?,
    error: String? = null,  // PATCH 7: Error tonen
    stepState: String? = null, // PATCH 1: ERROR state zichtbaar
    onAnswer: (String) -> Unit,
    onSkip: () -> Unit,
    onContinueAfterError: () -> Unit = {},  // PATCH 7: Verder na fout
    // PATCH 3: onFeedbackComplete verwijderd - ViewModel regelt advance intern
    onExerciseShown: () -> Unit = {},
    onContinueWorkedExample: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var typedAnswer by remember { mutableStateOf("") }
    var selectedOption by remember { mutableStateOf<String?>(null) }

    // PATCH 2: Reset state when exercise changes and start timer
    LaunchedEffect(exercise.id) {
        typedAnswer = ""
        selectedOption = null
        onExerciseShown() // Start de timer voor response time tracking
    }
    
    // PATCH 2: GEEN LaunchedEffect voor auto-advance meer
    // De ViewModel regelt nu volledig wanneer naar volgende oefening gegaan wordt
    
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
                            handleAnswer(option, exercise, onAnswer)
                        },
                        onConfirmTyped = {
                            handleAnswer(typedAnswer, exercise, onAnswer)
                        },
                        onContinueWorkedExample = onContinueWorkedExample,
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
        if (showFeedback && isLastAnswerCorrect != null) {
            FeedbackOverlay(
                isCorrect = isLastAnswerCorrect,
                onDismiss = { /* Handled by LaunchedEffect above */ }
            )
        }
        
        // PATCH 1 & 7: Error overlay - fouten zijn zichtbaar
        if (error != null || stepState == "ERROR") {
            ErrorOverlay(
                error = error ?: "Er ging iets mis bij het verwerken van de oefening",
                onContinue = onContinueAfterError
            )
        }
    }
}

/**
 * PATCH 6: Error overlay voor zichtbare foutmeldingen
 * Maakt fouten duidelijk zichtbaar voor de gebruiker - niet ambigu
 */
@Composable
private fun ErrorOverlay(
    error: String,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Duidelijke fout-indicator
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Hoofdmessage - simpel en duidelijk
                Text(
                    text = "Er ging iets mis bij deze oefening.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Submessage - geruststelling
                Text(
                    text = "We proberen veilig verder te gaan.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Duidelijke actie-knop
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verder →", style = MaterialTheme.typography.titleMedium)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Extra hint
                Text(
                    text = "Tik op 'Verder' om door te gaan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                )
                
                // Debug info alleen in debug builds (zeer compact)
                if (error.isNotEmpty() && error.length < 100) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "(${error.take(50)}${if (error.length > 50) "..." else ""})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun VisualContent(exercise: Exercise) {
    when (exercise.type) {
        ExerciseType.VISUAL_QUANTITY -> {
            val visualData = exercise.visualData
            val count = visualData?.count
            when {
                count == null -> MissingVisualFallback()
                visualData.type == VisualType.BLOCKS -> VisualBlocks(count = count)
                else -> VisualDots(count = count)
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
                else -> MissingVisualFallback()
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
    onContinueWorkedExample: () -> Unit = {},
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

        ExerciseType.WORKED_EXAMPLE -> {
            // Toon uitleg + voorbeeld + knop "verder" - geen validatie nodig
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📖 Uitgewerkt voorbeeld",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = exercise.hint ?: "Bekijk dit voorbeeld aandachtig",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onContinueWorkedExample,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Begrepen! Verder →")
                }
            }
        }

        ExerciseType.GUIDED_PRACTICE -> {
            // Toon vraag met hint/ondersteuning
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💡 Begeleide oefening",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = exercise.hint ?: "Probeer het met deze tip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Gebruik number pad zoals bij TYPED_NUMERIC
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
}

private fun handleAnswer(
    answer: String,
    exercise: Exercise,
    onAnswer: (String) -> Unit
) {
    onAnswer(answer)
}

private fun validateAnswer(answer: String, exercise: Exercise): Boolean {
    return answer.trim().equals(exercise.correctAnswer, ignoreCase = true) ||
           answer.trim().toIntOrNull()?.toString() == exercise.correctAnswer
}