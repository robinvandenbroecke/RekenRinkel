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
import com.rekenrinkel.domain.content.ContentRepository
import com.rekenrinkel.domain.model.MasteryLevel
import com.rekenrinkel.domain.model.SkillProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    progressList: List<SkillProgress>,
    isPremiumUnlocked: Boolean,
    currentStreak: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Filter op basis van premium status
    val allConfigs = ContentRepository.getAllConfigs()
    val accessibleConfigs = if (isPremiumUnlocked) {
        allConfigs
    } else {
        allConfigs.filter { !it.isPremium }
    }
    
    val accessibleSkillIds = accessibleConfigs.map { it.skillId }.toSet()
    
    // Categoriseer voortgang
    val accessibleProgress = progressList.filter { it.skillId in accessibleSkillIds }
    val weakSkills = accessibleProgress.filter { it.masteryScore < 50 }
    val practicingSkills = accessibleProgress.filter { 
        it.masteryScore >= 50 && it.masteryScore < 75 
    }
    val strongSkills = accessibleProgress.filter { it.masteryScore >= 75 }
    
    // Ontbrekende skills (nog niet gestart)
    val startedSkillIds = accessibleProgress.map { it.skillId }.toSet()
    val notStartedConfigs = accessibleConfigs.filter { it.skillId !in startedSkillIds }
    
    // Bereken statistieken
    val totalSessions = accessibleProgress.sumOf { it.correctAnswers + it.wrongAnswers }
    val avgResponseTime = if (accessibleProgress.isNotEmpty()) {
        accessibleProgress.map { it.averageResponseTimeMs }.filter { it > 0 }.average()
    } else 0.0
    
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
                        label = "Opgaven gemaakt",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SummaryCard(
                        value = "$currentStreak",
                        label = "Dagen streak",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SummaryCard(
                        value = "${accessibleProgress.size}/${accessibleConfigs.size}",
                        label = "Skills gestart",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Average response time
            if (avgResponseTime > 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Gemiddelde responstijd",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "${(avgResponseTime / 1000).toInt()}s",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
            
            // Nog niet gestart
            if (notStartedConfigs.isNotEmpty()) {
                item {
                    Text(
                        text = "Nog te ontdekken (${notStartedConfigs.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                
                items(notStartedConfigs.take(3)) { config ->
                    SkillConfigCard(
                        config = config,
                        isStarted = false
                    )
                }
            }
            
            // Weak skills
            if (weakSkills.isNotEmpty()) {
                item {
                    Text(
                        text = "Aandacht nodig (${weakSkills.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE57373)
                    )
                }
                
                items(weakSkills.sortedBy { it.masteryScore }) { progress ->
                    SkillProgressCard(progress = progress)
                }
            }
            
            // Practicing skills
            if (practicingSkills.isNotEmpty()) {
                item {
                    Text(
                        text = "Aan het oefenen (${practicingSkills.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFFFA726)
                    )
                }
                
                items(practicingSkills.sortedByDescending { it.masteryScore }) { progress ->
                    SkillProgressCard(progress = progress)
                }
            }
            
            // Strong skills
            if (strongSkills.isNotEmpty()) {
                item {
                    Text(
                        text = "Goed beheerst (${strongSkills.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF66BB6A)
                    )
                }
                
                items(strongSkills.sortedByDescending { it.masteryScore }) { progress ->
                    SkillProgressCard(progress = progress)
                }
            }
            
            // Suggested focus
            item {
                val suggestionText = when {
                    weakSkills.isNotEmpty() -> {
                        val weakest = weakSkills.minByOrNull { it.masteryScore }
                        val skillName = ContentRepository.getConfig(weakest?.skillId ?: "")?.name
                        "Focus op: $skillName"
                    }
                    notStartedConfigs.isNotEmpty() -> {
                        val nextSkill = notStartedConfigs.first()
                        "Volgende stap: ${nextSkill.name}"
                    }
                    else -> "Alles gaat goed! Blijf oefenen om sterker te worden."
                }
                
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
                            text = suggestionText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // Premium info
            if (!isPremiumUnlocked) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "⭐ Premium",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ontgrendel ${ContentRepository.getPremiumConfigs().size} extra vaardigheden in de instellingen",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SkillProgressCard(progress: SkillProgress) {
    val config = ContentRepository.getConfig(progress.skillId)
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config?.name ?: progress.skillId,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = progress.masteryLevel().name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = masteryColor
                    )
                }
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
                color = masteryColor,
                trackColor = masteryColor.copy(alpha = 0.2f)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "✓ ${progress.correctAnswers} goed  ✗ ${progress.wrongAnswers} fout",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (progress.averageResponseTimeMs > 0) {
                    Text(
                        text = "${(progress.averageResponseTimeMs / 1000)}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillConfigCard(
    config: com.rekenrinkel.domain.content.SkillContentConfig,
    isStarted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (config.isPremium) 
                MaterialTheme.colorScheme.secondaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = config.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (config.isPremium) {
                Text(
                    text = "⭐",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}