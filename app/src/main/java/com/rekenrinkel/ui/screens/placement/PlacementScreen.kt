package com.rekenrinkel.ui.screens.placement

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rekenrinkel.domain.engine.PlacementEngine
import com.rekenrinkel.domain.model.Profile

@Composable
fun PlacementScreen(
    profile: Profile?,
    placementEngine: PlacementEngine = remember { PlacementEngine() },
    onPlacementComplete: (PlacementEngine.PlacementAnalysis) -> Unit,
    onCancel: () -> Unit
) {
    if (profile == null) {
        // Loading state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var isComplete by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(mutableListOf<Pair<Boolean, Long>>()) }
    var startTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    // Genereer placement items op basis van startband
    val placementItems = remember(profile.startingBand) {
        placementEngine.generatePlacementItems(profile.startingBand)
    }
    
    val currentItem = placementItems.getOrNull(currentQuestionIndex)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isComplete) {
            // Completing state - toon analyse
            val analysis = placementEngine.analyzePlacement(
                profile.startingBand,
                results.mapIndexed { index, (isCorrect, responseTime) ->
                    com.rekenrinkel.domain.model.PlacementResult(
                        skillId = placementItems.getOrNull(index)?.skillId ?: "",
                        isCorrect = isCorrect,
                        responseTimeMs = responseTime,
                        givenAnswer = if (isCorrect) "correct" else "incorrect",
                        correctAnswer = "correct"
                    )
                }
            )
            
            Text(
                text = "🎯",
                style = MaterialTheme.typography.displayLarge
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Placement voltooid!",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Toon resultaat
            val correctCount = results.count { it.first }
            Text(
                text = "$correctCount van ${placementItems.size} correct",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Startniveau: ${analysis.recommendedBand.name}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Eerste skills: ${analysis.startSkills.take(2).joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { onPlacementComplete(analysis) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Start met leren!")
            }
        } else if (currentItem != null) {
            // Question state
            Text(
                text = "Placement ${currentQuestionIndex + 1}/${placementItems.size}",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            LinearProgressIndicator(
                progress = { (currentQuestionIndex + 1).toFloat() / placementItems.size },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = currentItem.question,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Antwoord knoppen
            val answers = when {
                currentItem.correctAnswer.toIntOrNull() != null -> {
                    // Numeriek antwoord
                    (1..10).map { it.toString() }
                }
                else -> {
                    // Textueel antwoord
                    listOf("links", "rechts", "evenveel", currentItem.correctAnswer)
                }
            }.distinct().shuffled().take(6)
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                maxItemsInEachRow = 3
            ) {
                answers.forEach { answer ->
                    Button(
                        onClick = {
                            val responseTime = System.currentTimeMillis() - startTime
                            val isCorrect = answer == currentItem.correctAnswer
                            results.add(isCorrect to responseTime)
                            
                            if (currentQuestionIndex < placementItems.size - 1) {
                                currentQuestionIndex++
                                startTime = System.currentTimeMillis()
                            } else {
                                isComplete = true
                            }
                        },
                        modifier = Modifier
                            .padding(4.dp)
                            .size(64.dp)
                    ) {
                        Text(
                            text = answer,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

// Helper composable voor FlowRow (vereist material3)
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    maxItemsInEachRow: Int = Int.MAX_VALUE,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        maxItemsInEachRow = maxItemsInEachRow,
        content = { content() }
    )
}