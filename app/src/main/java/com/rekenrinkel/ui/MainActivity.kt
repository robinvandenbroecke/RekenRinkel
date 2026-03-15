package com.rekenrinkel.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.domain.model.SessionResult
import com.rekenrinkel.domain.model.Theme
import com.rekenrinkel.ui.screens.exercise.ExerciseScreen
import com.rekenrinkel.ui.screens.home.HomeScreen
import com.rekenrinkel.ui.screens.onboarding.OnboardingScreen
import com.rekenrinkel.ui.screens.parent.ParentDashboardScreen
import com.rekenrinkel.ui.screens.premium.PremiumScreen
import com.rekenrinkel.ui.screens.profile.ProfileScreen
import com.rekenrinkel.ui.screens.session.SessionResultScreen
import com.rekenrinkel.ui.screens.settings.SettingsScreen
import com.rekenrinkel.ui.theme.RekenRinkelTheme
import com.rekenrinkel.ui.viewmodel.LessonNavigationEvent
import com.rekenrinkel.ui.viewmodel.LessonViewModel
import com.rekenrinkel.ui.viewmodel.LessonViewModelFactory
import com.rekenrinkel.ui.viewmodel.MainViewModel
import com.rekenrinkel.ui.viewmodel.MainViewModelFactory
import com.rekenrinkel.ui.viewmodel.NavigationEvent as MainNavEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            RekenRinkelApp()
        }
    }
}

@Composable
fun RekenRinkelApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // DataStore for settings
    val settingsDataStore = remember { SettingsDataStore(context) }

    // Check if onboarding is completed
    val onboardingCompleted by settingsDataStore.onboardingCompleted.collectAsState(initial = false)
    val einkModeEnabled by settingsDataStore.einkModeEnabled.collectAsState(initial = false)
    val appTheme by settingsDataStore.theme.collectAsState(initial = Theme.DINOSAURS)
    val startDestination = if (onboardingCompleted) "home" else "onboarding"

    RekenRinkelTheme(
        appTheme = appTheme,
        einkMode = einkModeEnabled
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable("home") {
                    val viewModel: MainViewModel = viewModel(
                        factory = MainViewModelFactory(context)
                    )
                    
                    val uiState by viewModel.uiState.collectAsState()
                    
                    // Handle navigation events
                    LaunchedEffect(Unit) {
                        viewModel.navigation.collectLatest { event ->
                            when (event) {
                                is MainNavEvent.StartSession -> {
                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                        "exercises", 
                                        ArrayList(event.exercises)
                                    )
                                    navController.navigate("exercise")
                                }
                                is MainNavEvent.OpenProfile -> navController.navigate("profile")
                                is MainNavEvent.OpenSettings -> navController.navigate("settings")
                                is MainNavEvent.OpenParentDashboard -> navController.navigate("parent")
                                is MainNavEvent.OpenPremium -> navController.navigate("premium")
                            }
                        }
                    }
                    
                    HomeScreen(
                        profile = uiState.profile,
                        onStartSession = { viewModel.startSession() },
                        onOpenProfile = { viewModel.openProfile() },
                        onOpenSettings = { viewModel.openSettings() },
                        onOpenParentDashboard = { viewModel.openParentDashboard() }
                    )
                }
                
                composable("profile") {
                    val viewModel: MainViewModel = viewModel(
                        factory = MainViewModelFactory(context)
                    )
                    val uiState by viewModel.uiState.collectAsState()
                    
                    ProfileScreen(
                        profile = uiState.profile,
                        onBack = { navController.popBackStack() },
                        onNameChange = { viewModel.updateProfileName(it) },
                        onThemeChange = { viewModel.updateProfileTheme(it) }
                    )
                }
                
                composable("settings") {
                    val viewModel: MainViewModel = viewModel(
                        factory = MainViewModelFactory(context)
                    )
                    val uiState by viewModel.uiState.collectAsState()

                    SettingsScreen(
                        soundEnabled = uiState.soundEnabled,
                        onSoundToggle = { viewModel.toggleSound(it) },
                        isPremiumUnlocked = uiState.isPremiumUnlocked,
                        onPremiumToggle = { viewModel.togglePremiumUnlocked(it) },
                        einkModeEnabled = uiState.einkModeEnabled,
                        onEinkModeToggle = { viewModel.toggleEinkMode(it) },
                        onResetProgress = { viewModel.resetProgress() },
                        onResetProfile = { viewModel.resetProfile() },
                        onOpenPremium = { navController.navigate("premium") },
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("parent") {
                    val viewModel: MainViewModel = viewModel(
                        factory = MainViewModelFactory(context)
                    )
                    val uiState by viewModel.uiState.collectAsState()
                    
                    ParentDashboardScreen(
                        progressList = uiState.progress,
                        isPremiumUnlocked = uiState.isPremiumUnlocked,
                        currentStreak = uiState.profile?.currentStreak ?: 0,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("premium") {
                    PremiumScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("onboarding") {
                    val viewModel: MainViewModel = viewModel(
                        factory = MainViewModelFactory(context)
                    )
                    
                    OnboardingScreen(
                        onComplete = { name, theme ->
                            viewModel.completeOnboarding(name, theme)
                            navController.navigate("home") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    )
                }
                
                composable("exercise") {
                    val viewModel: LessonViewModel = viewModel(
                        factory = LessonViewModelFactory(context)
                    )

                    val uiState by viewModel.uiState.collectAsState()

                    // Start lesson if not active
                    LaunchedEffect(Unit) {
                        if (!uiState.isActive && !uiState.isLoading) {
                            viewModel.startLesson()
                        }
                    }

                    // Handle navigation events
                    LaunchedEffect(Unit) {
                        viewModel.navigation.collectLatest { event ->
                            when (event) {
                                is LessonNavigationEvent.LessonComplete -> {
                                    navController.navigate("result")
                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                        "result",
                                        event.result
                                    )
                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                        "badges",
                                        ArrayList(event.badges)
                                    )
                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                        "xpTotal",
                                        event.xpTotal
                                    )
                                }
                                is LessonNavigationEvent.BackToHome -> {
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            }
                        }
                    }

                    // Show loading while building lesson
                    if (uiState.isLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Les aan het voorbereiden...",
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                        return@composable
                    }

                    val currentExercise = uiState.currentExercise

                    if (currentExercise != null) {
                        ExerciseScreen(
                            exercise = currentExercise,
                            currentIndex = uiState.currentIndex + 1,
                            totalExercises = uiState.totalExercises,
                            showFeedback = uiState.showFeedback,
                            isLastAnswerCorrect = uiState.lastAnswerCorrect,
                            onAnswer = { answer ->
                                viewModel.submitAnswer(answer)
                            },
                            onSkip = { viewModel.skipExercise() },
                            onFeedbackComplete = { viewModel.nextExercise() },
                            onExerciseShown = { viewModel.startExerciseTimer() }
                        )
                    }
                }
                
                composable("result") {
                    val result = navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.get<SessionResult>("result")

                    when {
                        result != null -> {
                            SessionResultScreen(
                                result = result,
                                onHome = {
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                },
                                onRetry = {
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }
                        else -> {
                            // Fallback: show error message and navigate home
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Resultaat laden...",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                            }
                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(500)
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}