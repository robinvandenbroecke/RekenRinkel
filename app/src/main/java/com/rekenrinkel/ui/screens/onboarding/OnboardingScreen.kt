package com.rekenrinkel.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rekenrinkel.domain.model.Theme

@Composable
fun OnboardingScreen(
    onComplete: (name: String, theme: Theme) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var selectedTheme by remember { mutableStateOf(Theme.DINOSAURS) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (currentStep) {
            0 -> WelcomeStep(
                onNext = { currentStep = 1 }
            )
            1 -> NameStep(
                name = name,
                onNameChange = { name = it },
                onNext = { currentStep = 2 }
            )
            2 -> ThemeStep(
                selectedTheme = selectedTheme,
                onThemeSelect = { selectedTheme = it },
                onComplete = { onComplete(name, selectedTheme) }
            )
        }
        
        // Progress dots
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) { index ->
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = if (index == currentStep) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                ) {}
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    onNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎯",
            style = MaterialTheme.typography.displayLarge
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Welkom bij RekenRinkel!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Leer rekenen op een leuke manier met korte oefensessies.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Start",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "👋",
            style = MaterialTheme.typography.displayLarge
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Hoe heet je?",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Jouw naam") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onNext,
            enabled = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Verder",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun ThemeStep(
    selectedTheme: Theme,
    onThemeSelect: (Theme) -> Unit,
    onComplete: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎨",
            style = MaterialTheme.typography.displayLarge
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Kies een thema",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ThemeCard(
                emoji = "🦕",
                name = "Dino's",
                isSelected = selectedTheme == Theme.DINOSAURS,
                onClick = { onThemeSelect(Theme.DINOSAURS) }
            )
            ThemeCard(
                emoji = "🚗",
                name = "Auto's",
                isSelected = selectedTheme == Theme.CARS,
                onClick = { onThemeSelect(Theme.CARS) }
            )
            ThemeCard(
                emoji = "🚀",
                name = "Ruimte",
                isSelected = selectedTheme == Theme.SPACE,
                onClick = { onThemeSelect(Theme.SPACE) }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Klaar!",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun ThemeCard(
    emoji: String,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(100.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surface,
        border = if (isSelected) 
            androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        else 
            null
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.displayMedium
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}