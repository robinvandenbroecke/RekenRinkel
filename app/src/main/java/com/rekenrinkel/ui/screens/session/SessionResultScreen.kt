package com.rekenrinkel.ui.screens.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rekenrinkel.domain.model.SessionResult
import com.rekenrinkel.ui.components.StarDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionResultScreen(
    result: SessionResult,
    skillProgress: List<com.rekenrinkel.domain.model.SkillProgress> = emptyList(),
    onHome: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accuracy = result.accuracy()
    val correctCount = result.correctCount()
    val totalCount = result.totalCount()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sessie voltooid!") }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Success icon
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stars
            StarDisplay(
                stars = result.stars,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title
            Text(
                text = when (result.stars) {
                    3 -> "Geweldig werk! 🌟"
                    2 -> "Goed bezig! 👍"
                    1 -> "Je bent op weg! 💪"
                    else -> "Blijf oefenen! 🎯"
                },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Stats cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(
                    value = "$correctCount/$totalCount",
                    label = "Goed",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                StatCard(
                    value = "${(accuracy * 100).toInt()}%",
                    label = "Score",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                StatCard(
                    value = "+${result.xpEarned}",
                    label = "XP",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Response time
            if (result.averageResponseTimeMs > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gemiddelde tijd per vraag",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${(result.averageResponseTimeMs / 1000.0).format(1)}s",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // What went well
            if (correctCount > 0) {
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
                            text = "Wat ging goed:",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Je hebt $correctCount van de $totalCount opgaven correct beantwoord!",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // What to practice
            if (correctCount < totalCount) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Waar je aan kunt werken:",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Blijf oefenen om nog beter te worden!",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // PATCH 6: Skill mastery informatie
            if (skillProgress.isNotEmpty()) {
                val practicedSkillIds = result.exercises.map { it.skillId }.distinct()
                val practicedSkills = skillProgress.filter { it.skillId in practicedSkillIds }
                
                if (practicedSkills.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "📚 Skill voortgang",
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            practicedSkills.take(3).forEach { skill ->
                                val wasMastered = skill.isMastered()
                                val masteryPercent = skill.masteryScore
                                val stars = when {
                                    masteryPercent >= 90 -> "⭐⭐⭐"
                                    masteryPercent >= 70 -> "⭐⭐"
                                    masteryPercent >= 50 -> "⭐"
                                    else -> ""
                                }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = formatSkillName(skill.skillId),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        LinearProgressIndicator(
                                            progress = { masteryPercent / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (wasMastered) "✅ $stars" else "$masteryPercent% $stars",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            
                            // Toon "Les Master" badge als accuracy >= 80%
                            if (accuracy >= 0.8f) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "🏆 Lesmeester! (80%+ score)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Actions
            Button(
                onClick = onHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Terug naar home",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Nog een keer oefenen")
            }
        }
    }
}

/**
 * Formatteer een double naar x decimalen
 */
private fun Double.format(decimals: Int): String {
    return String.format("%.${decimals}f", this)
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * PATCH 6: Helper om skill ID naar leesbare naam te converteren
 */
private fun formatSkillName(skillId: String): String {
    return when (skillId) {
        "foundation_subitize_5" -> "Herkennen tot 5"
        "foundation_counting" -> "Tellen"
        "foundation_number_bonds_5" -> "Splitsen tot 5"
        "foundation_number_bonds_10" -> "Splitsen tot 10"
        "foundation_compare" -> "Vergelijken"
        "foundation_more_less" -> "Meer/minder"
        "foundation_patterns" -> "Patronen"
        "foundation_sequence" -> "Getalreeksen"
        "arithmetic_add" -> "Optellen"
        "arithmetic_sub" -> "Aftrekken"
        "arithmetic_add_20" -> "Optellen tot 20"
        "arithmetic_sub_20" -> "Aftrekken tot 20"
        "arithmetic_bridge" -> "Brug over 10"
        "patterns_doubles" -> "Dubbelen"
        "patterns_halves" -> "Helften"
        "patterns_count_2" -> "Tellen per 2"
        "patterns_count_5" -> "Tellen per 5"
        "patterns_count_10" -> "Tellen per 10"
        "multiplication_groups" -> "Groepjes"
        "table_2" -> "Tafel van 2"
        "table_5" -> "Tafel van 5"
        "table_10" -> "Tafel van 10"
        "place_value_tens" -> "Tientallen"
        "comparison_symbol" -> "Vergelijken"
        else -> skillId.replace("_", " ").replaceFirstChar { it.uppercase() }
    }
}