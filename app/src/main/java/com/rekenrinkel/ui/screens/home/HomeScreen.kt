package com.rekenrinkel.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rekenrinkel.domain.model.Profile
import com.rekenrinkel.domain.model.Theme
import com.rekenrinkel.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profile: Profile?,
    progress: List<com.rekenrinkel.domain.model.SkillProgress> = emptyList(),
    onStartSession: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenParentDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RekenRinkel") },
                actions = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profiel")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Instellingen")
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profiel kaart
            if (profile != null) {
                ProfileCard(profile = profile)
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // PATCH 3 & 5: Leerdoelen sectie met echte skill data
            val focusSkillId = profile?.placementAnalysisResult?.startSkills?.firstOrNull() 
                ?: "foundation_subitize_5"
            val focusSkillProgress = progress.find { it.skillId == focusSkillId }
            
            // Gebruik echte data indien beschikbaar, anders defaults
            val actualMasteryScore = focusSkillProgress?.masteryScore ?: 0
            val actualCpaPhase = focusSkillProgress?.currentCpaPhase?.name?.lowercase() 
                ?: profile?.placementAnalysisResult?.startCpaPhase?.name?.lowercase() 
                ?: "concrete"
            val masteryProgress = actualMasteryScore / 100f
            val nextSkillId = getNextSkill(focusSkillId)
            val nextSkillProgress = progress.find { it.skillId == nextSkillId }
            val isNextUnlocked = nextSkillProgress?.isUnlocked == true
            
            LearningGoalsCard(
                focusSkill = formatSkillName(focusSkillId),
                cpaPhase = actualCpaPhase,
                masteryProgress = masteryProgress.coerceIn(0f, 1f),
                masteryScore = actualMasteryScore,
                nextSkill = formatSkillName(nextSkillId),
                isNextUnlocked = isNextUnlocked,
                isFocusMastered = focusSkillProgress?.isMastered() == true
            )

            Spacer(modifier = Modifier.weight(1f))

            // Hoofdactie
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                    .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Klaar om te oefenen?",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Doe vandaag je dagelijkse sessie!",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onStartSession,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Start met oefenen",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Parent dashboard toegang
            TextButton(
                onClick = onOpenParentDashboard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ouderdashboard")
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: Profile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (profile.theme) {
                        Theme.DINOSAURS -> "🦕"
                        Theme.CARS -> "🚗"
                        Theme.SPACE -> "🚀"
                    },
                    style = MaterialTheme.typography.displayMedium
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name.ifEmpty { "Rekenheld" },
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Level ${profile.currentLevel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // XP bar
                LinearProgressIndicator(
                    progress = { profile.xpProgress() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = AppColors.Accent
                )
                Text(
                    text = "${profile.totalXp % 100} / 100 XP",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            // Streak
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔥", style = MaterialTheme.typography.displaySmall)
                Text(
                    "${profile.currentStreak}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/**
 * PATCH 3 & 5: Leerdoelen kaart - toont huidige focus en voortgang met echte data
 */
@Composable
private fun LearningGoalsCard(
    focusSkill: String,
    cpaPhase: String,
    masteryProgress: Float,
    masteryScore: Int,
    nextSkill: String,
    isNextUnlocked: Boolean,
    isFocusMastered: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "📚 Vandaag",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = focusSkill,
                style = MaterialTheme.typography.headlineSmall
            )

            // CPA fase indicator
            val cpaIcon = when (cpaPhase) {
                "concrete" -> "🧱"
                "pictorial" -> "🖼️"
                "abstract" -> "🔢"
                "mixed_transfer" -> "🔄"
                else -> "📚"
            }
            Text(
                text = "$cpaIcon Fase: $cpaPhase",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Mastery progress met sterren
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { masteryProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Sterren 0-3 gebaseerd op mastery
                val stars = when {
                    masteryScore >= 90 -> "⭐⭐⭐"
                    masteryScore >= 70 -> "⭐⭐"
                    masteryScore >= 50 -> "⭐"
                    else -> ""
                }
                if (stars.isNotEmpty()) {
                    Text(stars, style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(
                text = if (isFocusMastered) "✅ Mastered!" else "$masteryScore% naar mastery",
                style = MaterialTheme.typography.labelSmall,
                color = if (isFocusMastered) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Volgende skill met lock status
            val lockIcon = if (isNextUnlocked) "➡️" else "🔒"
            Text(
                text = "$lockIcon Volgende: $nextSkill",
                style = MaterialTheme.typography.bodySmall,
                color = if (isNextUnlocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

/**
 * PATCH 5: Helper functie om skill ID naar leesbare naam te converteren
 */
private fun formatSkillName(skillId: String): String {
    return when (skillId) {
        "foundation_subitize_5" -> "Herkennen tot 5"
        "foundation_counting" -> "Tellen"
        "foundation_number_bonds_5" -> "Splitsen tot 5"
        "foundation_number_bonds_10" -> "Splitsen tot 10"
        "foundation_more_less" -> "Meer/minder"
        "arithmetic_add_10_concrete" -> "Optellen tot 10 (concreet)"
        "arithmetic_add_10_pictorial" -> "Optellen tot 10 (picturaal)"
        "arithmetic_add_10_abstract" -> "Optellen tot 10 (abstract)"
        "arithmetic_sub_10_concrete" -> "Aftrekken tot 10 (concreet)"
        "arithmetic_sub_10_pictorial" -> "Aftrekken tot 10 (picturaal)"
        "arithmetic_sub_10_abstract" -> "Aftrekken tot 10 (abstract)"
        "arithmetic_bridge_add" -> "Brug over 10"
        "patterns_doubles" -> "Dubbelen"
        "patterns_count_2" -> "Tellen per 2"
        "advanced_groups" -> "Groepjes"
        "advanced_table_2" -> "Tafel van 2"
        "advanced_table_5" -> "Tafel van 5"
        else -> skillId.replace("_", " ").replaceFirstChar { it.uppercase() }
    }
}

/**
 * PATCH 5: Helper functie om volgende skill te bepalen
 */
private fun getNextSkill(currentSkill: String): String {
    return when (currentSkill) {
        "foundation_subitize_5" -> "foundation_counting"
        "foundation_counting" -> "foundation_number_bonds_5"
        "foundation_number_bonds_5" -> "foundation_number_bonds_10"
        "foundation_number_bonds_10" -> "arithmetic_add_10_concrete"
        "arithmetic_add_10_concrete" -> "arithmetic_add_10_pictorial"
        "arithmetic_add_10_pictorial" -> "arithmetic_add_10_abstract"
        "arithmetic_add_10_abstract" -> "arithmetic_sub_10_concrete"
        "arithmetic_sub_10_concrete" -> "arithmetic_bridge_add"
        "arithmetic_bridge_add" -> "patterns_count_2"
        "patterns_count_2" -> "advanced_groups"
        "advanced_groups" -> "advanced_table_2"
        else -> "Volgende skill"
    }
}