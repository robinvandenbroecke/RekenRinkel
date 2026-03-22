package com.rekenrinkel.ui.screens.exercise

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rekenrinkel.domain.engine.InputMode
import com.rekenrinkel.domain.engine.TtsManager
import com.rekenrinkel.domain.engine.determineInputMode
import com.rekenrinkel.domain.model.Exercise
import com.rekenrinkel.domain.model.ExerciseType
import com.rekenrinkel.domain.model.VisualType
import com.rekenrinkel.ui.components.*
import com.rekenrinkel.ui.viewmodel.LessonStepState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(
    exercise: Exercise,
    currentIndex: Int,
    totalExercises: Int,
    showFeedback: Boolean,
    isLastAnswerCorrect: Boolean?,
    error: String? = null,
    stepState: String? = null,
    profileAge: Int = 6,
    errorHint: String? = null,
    showHintPersistent: Boolean = false,
    showRevealAnswer: Boolean = false,
    wrongAttempts: Int = 0,
    einkModeEnabled: Boolean = false,
    ttsManager: TtsManager? = null,
    onAnswer: (String) -> Unit,
    onSkip: () -> Unit,
    onRevealAnswer: () -> Unit = {},
    onContinueAfterError: () -> Unit = {},
    onExerciseShown: () -> Unit = {},
    onContinueWorkedExample: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var typedAnswer by remember { mutableStateOf("") }
    var selectedOption by remember { mutableStateOf<String?>(null) }

    // Animation states (skip if e-ink mode)
    var triggerCorrectBounce by remember { mutableStateOf(false) }
    var triggerWrongFlash by remember { mutableStateOf(false) }

    val correctScale by animateFloatAsState(
        targetValue = if (triggerCorrectBounce && !einkModeEnabled) 1.5f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        finishedListener = { if (triggerCorrectBounce) triggerCorrectBounce = false },
        label = "correctBounce"
    )

    val wrongCardColor by animateColorAsState(
        targetValue = if (triggerWrongFlash && !einkModeEnabled) Color(0xFFFFCDD2) else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        finishedListener = { if (triggerWrongFlash) triggerWrongFlash = false },
        label = "wrongFlash"
    )

    // Trigger animations on feedback state
    LaunchedEffect(stepState, isLastAnswerCorrect) {
        if (!einkModeEnabled) {
            when {
                stepState == "FEEDBACK_CORRECT" && isLastAnswerCorrect == true -> triggerCorrectBounce = true
                stepState == "FEEDBACK_WRONG" && isLastAnswerCorrect == false -> triggerWrongFlash = true
            }
        }
    }

    // Reset typed answer on retry (stepState back to SHOWING after wrong)
    LaunchedEffect(exercise.id, stepState) {
        if (stepState == "SHOWING") {
            typedAnswer = ""
            selectedOption = null
        }
    }
    
    // Reset state when exercise changes
    LaunchedEffect(exercise.id) {
        typedAnswer = ""
        selectedOption = null
        onExerciseShown()
        // Auto-speak for young kids (age ≤ 6)
        if (profileAge <= 6) {
            ttsManager?.speak(exercise.question)
        }
    }

    val inputMode = remember(exercise.id, profileAge) {
        determineInputMode(profileAge, exercise.type, exercise.difficulty)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Oefening $currentIndex van $totalExercises") },
                navigationIcon = {
                    IconButton(onClick = onSkip) {
                        Icon(Icons.Default.Close, contentDescription = "Sluiten")
                    }
                },
                actions = {
                    if (ttsManager != null) {
                        IconButton(onClick = { ttsManager.speak(exercise.question) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Voorlezen")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Top section: scrollable question area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    ProgressBar(
                        current = currentIndex,
                        total = totalExercises,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(correctScale),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (wrongCardColor != Color.Transparent) wrongCardColor
                                else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = exercise.question,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            VisualContent(exercise = exercise)
                        }
                    }

                    // Hint — show persistently after wrong attempt, or toggle manually
                    if (exercise.hint != null &&
                        exercise.type != ExerciseType.WORKED_EXAMPLE &&
                        exercise.type != ExerciseType.GUIDED_PRACTICE
                    ) {
                        var showHint by remember { mutableStateOf(false) }
                        val hintVisible = showHint || showHintPersistent
                        
                        if (hintVisible) {
                            // Show error-specific hint if available, otherwise generic hint
                            val hintText = if (showHintPersistent && errorHint != null) {
                                "$errorHint\n\n💡 ${exercise.hint}"
                            } else {
                                exercise.hint
                            }
                            Text(
                                text = hintText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (!hintVisible) {
                                TextButton(onClick = { showHint = true }) {
                                    Text("💡 Hint")
                                }
                            }
                            
                            // Reveal answer button after 3 wrong attempts
                            if (showRevealAnswer) {
                                TextButton(onClick = onRevealAnswer) {
                                    Text("👀 Toon antwoord", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        
                        // Wrong attempts counter
                        if (wrongAttempts > 0) {
                            Text(
                                text = "Poging ${wrongAttempts + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom section: answer input — fixed, never scrolls
                val enabled = !showFeedback && stepState != "ERROR" && stepState != "FEEDBACK_CORRECT" && stepState != "FEEDBACK_WRONG"
                AnswerContent(
                    exercise = exercise,
                    inputMode = inputMode,
                    typedAnswer = typedAnswer,
                    selectedOption = selectedOption,
                    onTypedAnswerChange = { newValue ->
                        typedAnswer = newValue
                        // Auto-submit when typed length matches correct answer length
                        val correctLen = exercise.correctAnswer.length
                        if (newValue.length == correctLen && newValue.isNotEmpty()) {
                            onAnswer(newValue)
                        }
                    },
                    onOptionSelected = { option ->
                        selectedOption = option
                        onAnswer(option)
                    },
                    onConfirmTyped = {
                        if (typedAnswer.isNotEmpty()) onAnswer(typedAnswer)
                    },
                    onContinueWorkedExample = onContinueWorkedExample,
                    enabled = enabled
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Inline feedback (no overlay, no blocking)
            if (showFeedback && isLastAnswerCorrect != null) {
                // TTS feedback
                LaunchedEffect(isLastAnswerCorrect) {
                    if (isLastAnswerCorrect) {
                        ttsManager?.speak("Goed zo!")
                    } else {
                        val feedbackText = errorHint ?: "Bijna, het was ${exercise.correctAnswer}"
                        ttsManager?.speak(feedbackText)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLastAnswerCorrect) {
                        Text(
                            text = "✅",
                            style = MaterialTheme.typography.displayLarge
                        )
                    } else {
                        Card(
                            modifier = Modifier.padding(32.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "❌",
                                    style = MaterialTheme.typography.displayMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (errorHint != null) {
                                    Text(
                                        text = errorHint,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Text(
                                    text = "Antwoord: ${exercise.correctAnswer}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Error overlay
            if (error != null || stepState == "ERROR") {
                ErrorOverlay(
                    error = error ?: "Er ging iets mis",
                    onContinue = onContinueAfterError
                )
            }
        }
    }
}

@Composable
private fun ErrorOverlay(
    error: String,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⚠️", style = MaterialTheme.typography.displayMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Er ging iets mis bij deze oefening.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verder →", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun VisualContent(exercise: Exercise) {
    val visualData = exercise.visualData
    when {
        visualData?.count != null -> {
            when {
                visualData.type == VisualType.BLOCKS -> VisualBlocks(count = visualData.count)
                else -> VisualDots(count = visualData.count)
            }
        }
        visualData?.groups != null -> {
            VisualGroups(groups = visualData.groups)
        }
        visualData?.firstNumber != null && visualData.secondNumber != null -> {
            // Comparison: show numbers as text, not dots
            if (visualData.type == VisualType.COMPARISON) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${visualData.firstNumber}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "?",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${visualData.secondNumber}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            } else if (visualData.firstNumber <= 10 && visualData.secondNumber <= 10) {
                // Small numbers: show as dot groups
                VisualGroups(groups = listOf(visualData.firstNumber, visualData.secondNumber))
            } else {
                // Large numbers: just show as text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("${visualData.firstNumber}", style = MaterialTheme.typography.displayMedium)
                    Text("en", style = MaterialTheme.typography.bodyLarge)
                    Text("${visualData.secondNumber}", style = MaterialTheme.typography.displayMedium)
                }
            }
        }
        exercise.type == ExerciseType.VISUAL_QUANTITY -> {
            // For VISUAL_QUANTITY, always show something
            // If visualData exists but count is null, show fallback with count 3
            val fallbackCount = 3
            VisualDots(count = fallbackCount)
        }
        exercise.type == ExerciseType.VISUAL_GROUPS -> {
            MissingVisualFallback()
        }
    }
}

@Composable
private fun AnswerContent(
    exercise: Exercise,
    inputMode: InputMode,
    typedAnswer: String,
    selectedOption: String?,
    onTypedAnswerChange: (String) -> Unit,
    onOptionSelected: (String) -> Unit,
    onConfirmTyped: () -> Unit,
    onContinueWorkedExample: () -> Unit = {},
    enabled: Boolean
) {
    when (inputMode) {
        InputMode.CONTINUE_BUTTON -> {
            // Multi-step worked example
            val steps = exercise.workedSteps
            if (steps.isNotEmpty()) {
                var currentStep by remember(exercise.id) { mutableIntStateOf(0) }
                val isLastStep = currentStep >= steps.size - 1

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📖 Uitgewerkt voorbeeld",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Show steps up to current
                    steps.take(currentStep + 1).forEachIndexed { index, step ->
                        Text(
                            text = step,
                            style = if (index == currentStep)
                                MaterialTheme.typography.titleLarge
                            else
                                MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = if (index == currentStep)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step indicator
                    Text(
                        text = "Stap ${currentStep + 1} van ${steps.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (isLastStep) {
                                onContinueWorkedExample()
                            } else {
                                currentStep++
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled
                    ) {
                        Text(if (isLastStep) "Ik snap het ✓" else "Volgende stap →")
                    }
                }
            } else {
                // Fallback: single hint
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
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled
                    ) {
                        Text("Begrepen! Verder →")
                    }
                }
            }
        }

        InputMode.COMPARISON -> {
            // Three buttons: <, =, >
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("<", "=", ">").forEach { symbol ->
                    AnswerButton(
                        text = symbol,
                        onClick = { onOptionSelected(symbol) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .height(48.dp),
                        isSelected = selectedOption == symbol,
                        enabled = enabled
                    )
                }
            }
        }

        InputMode.MULTIPLE_CHOICE -> {
            // Build options: use exercise.options if available, else distractors + correct
            val options = exercise.options
                ?: if (exercise.distractors.isNotEmpty()) {
                    (exercise.distractors.take(3) + exercise.correctAnswer).shuffled()
                } else {
                    listOf(exercise.correctAnswer)
                }

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
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .height(48.dp),
                                isSelected = selectedOption == option,
                                enabled = enabled
                            )
                        }
                        // Pad with spacer if odd
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f).padding(4.dp))
                        }
                    }
                }
            }
        }

        InputMode.NUMBER_PAD -> {
            // Guided practice header if applicable
            if (exercise.type == ExerciseType.GUIDED_PRACTICE) {
                Text(
                    text = "💡 ${exercise.hint ?: "Probeer het met deze tip"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

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
                    if (enabled) onConfirmTyped()
                },
                currentValue = typedAnswer
            )
        }
    }
}
