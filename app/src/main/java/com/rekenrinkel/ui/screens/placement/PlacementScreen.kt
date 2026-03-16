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
            // PATCH 5: Pass placement items voor cluster-analyse
            val placementResults = results.mapIndexed { index, (isCorrect, responseTime) ->
                com.rekenrinkel.domain.model.PlacementResult(
                    skillId = placementItems.getOrNull(index)?.skillId ?: "",
                    isCorrect = isCorrect,
                    responseTimeMs = responseTime,
                    givenAnswer = if (isCorrect) "correct" else "incorrect",
                    correctAnswer = "correct"
                )
            }
            
            val analysis = placementEngine.analyzePlacement(
                profile.startingBand,
                placementResults,
                placementItems  // Pass items voor cluster context
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
                text = "Focus: ${analysis.startSkills.take(2).joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            // PATCH 5: Toon cluster scores
            if (analysis.clusterScores.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Vaardigheidsclusters:",
                    style = MaterialTheme.typography.titleSmall
                )
                
                analysis.clusterScores.forEach { (area, score) ->
                    val emoji = when (score.recommendation) {
                        PlacementEngine.ClusterRecommendation.STRONG -> "✅"
                        PlacementEngine.ClusterRecommendation.DEVELOPING -> "📈"
                        PlacementEngine.ClusterRecommendation.NEEDS_WORK -> "🎯"
                    }
                    val cpaPhase = analysis.cpaPreferencePerCluster[area]?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Mixed"
                    
                    Text(
                        text = "$emoji ${area.name}: ${(score.accuracy * 100).toInt()}% ($cpaPhase)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            if (analysis.weakAreas.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aandacht nodig: ${analysis.weakAreas.take(2).joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
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
            
            // Antwoord knoppen - gebruik didactisch correcte opties uit placement item
            val answers = currentItem.options.shuffled()
            val columns = if (answers.size <= 3) 1 else if (answers.size <= 6) 2 else 3
            
            // Gebruik dynamische layout gebaseerd op aantal opties
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                answers.chunked(columns).forEach { rowAnswers ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowAnswers.forEach { answer ->
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
                                    .padding(8.dp)
                                    .height(56.dp)
                                    .weight(1f)
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
    }
}