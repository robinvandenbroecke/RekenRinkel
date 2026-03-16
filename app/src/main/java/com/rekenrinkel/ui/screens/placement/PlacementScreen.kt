package com.rekenrinkel.ui.screens.placement

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rekenrinkel.domain.model.Profile

@Composable
fun PlacementScreen(
    profile: Profile?,
    onPlacementComplete: () -> Unit,
    onCancel: () -> Unit
) {
    var currentQuestion by remember { mutableIntStateOf(0) }
    var isComplete by remember { mutableStateOf(false) }
    
    // Simpele placement flow - 5 vragen
    val questions = remember {
        listOf(
            "Hoeveel stippen zie je? (3)" to "3",
            "Wat is 2 + 3?" to "5",
            "Wat is 10 - 4?" to "6",
            "Welk getal is groter: 7 of 5?" to "7",
            "Hoeveel is 5 + 5?" to "10"
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isComplete) {
            // Completing state
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
            
            Text(
                text = "We bepalen nu je startniveau...",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onPlacementComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Start met leren!")
            }
        } else {
            // Question state
            Text(
                text = "Placement ${currentQuestion + 1}/${questions.size}",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            LinearProgressIndicator(
                progress = { (currentQuestion + 1).toFloat() / questions.size },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = questions[currentQuestion].first,
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Answer buttons (simplified)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10").forEach { answer ->
                    Button(
                        onClick = {
                            if (currentQuestion < questions.size - 1) {
                                currentQuestion++
                            } else {
                                isComplete = true
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text(answer)
                    }
                }
            }
        }
    }
}