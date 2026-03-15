package com.rekenrinkel.ui.screens.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rekenrinkel.domain.model.MasteryLevel
import com.rekenrinkel.domain.model.SkillDefinitions
import com.rekenrinkel.domain.model.SkillProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    progressList: List<SkillProgress>,
    totalSessions: Int,
    averageSessionTime: String,
    currentStreak: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weakSkills = progressList.filter { it.masteryScore < 50 }
    val strongSkills = progressList.filter { it.masteryScore >= 75 }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ouderdashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Terug")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary stats
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryCard(
                        value = totalSessions.toString(),
                        label = "Sessies",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SummaryCard(
                        value = "$currentStreak",
                        label = "Dagen streak",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Weak skills section
            if (weakSkills.isNotEmpty()) {
                item {
                    Text(
                        text = "Aandacht nodig",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                
                items(weakSkills) { progress ->
                    SkillProgressCard(progress = progress)
                }
            }
            
            // Strong skills section
            if (strongSkills.isNotEmpty()) {
                item {
                    Text(
                        text = "Goed beheerst",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                
                items(strongSkills) { progress ->
                    SkillProgressCard(progress = progress)
                }
            }
            
            // All skills
            item {
                Text(
                    text = "Alle vaardigheden",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            
            items(progressList) { progress ->
                SkillProgressCard(progress = progress)
            }
            
            // Suggested focus
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "💡 Suggestie",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (weakSkills.isNotEmpty()) {
                                "Focus op: ${weakSkills.firstOrNull()?.let { 
                                    SkillDefinitions.getById(it.skillId)?.name 
                                } ?: "basisvaardigheden"}"
                            } else {
                                "Alles gaat goed! Probeer nieuwe uitdagingen."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SkillProgressCard(progress: SkillProgress) {
    val skill = SkillDefinitions.getById(progress.skillId)
    val masteryColor = when (progress.masteryLevel()) {
        MasteryLevel.NOT_LEARNED -> Color.Gray
        MasteryLevel.EMERGING -> Color(0xFFFFA726)
        MasteryLevel.PRACTICING -> Color(0xFF42A5F5)
        MasteryLevel.SOLID -> Color(0xFF66BB6A)
        MasteryLevel.MASTERED -> Color(0xFF2E7D32)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = skill?.name ?: progress.skillId,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${progress.masteryScore}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = masteryColor
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { progress.masteryScore / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = masteryColor
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row {
                Text(
                    text = "✓ ${progress.correctAnswers}  ✗ ${progress.wrongAnswers}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}