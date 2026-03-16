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

            // PATCH 5: Leerdoelen sectie
            LearningGoalsCard(
                focusSkill = "splitsingen tot 10",  // TODO: uit repository halen
                cpaPhase = "picturaal",
                masteryProgress = 0.65f,
                nextSkill = "brug over 10"
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
 * PATCH 5: Leerdoelen kaart - toont huidige focus en voortgang
 */
@Composable
private fun LearningGoalsCard(
    focusSkill: String,
    cpaPhase: String,
    masteryProgress: Float,
    nextSkill: String
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

            Text(
                text = "Fase: $cpaPhase",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Mastery progress
            LinearProgressIndicator(
                progress = { masteryProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )

            Text(
                text = "${(masteryProgress * 100).toInt()}% naar mastery",
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "➡️ Volgende: $nextSkill",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}