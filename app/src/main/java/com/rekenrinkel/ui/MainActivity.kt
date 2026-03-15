package com.rekenrinkel.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.local.RekenRinkelDatabase
import com.rekenrinkel.data.repository.ProfileRepository
import com.rekenrinkel.data.repository.ProgressRepository
import com.rekenrinkel.domain.engine.ExerciseEngine
import com.rekenrinkel.domain.engine.ExerciseValidator
import com.rekenrinkel.domain.engine.SessionEngine
import com.rekenrinkel.domain.model.SessionResult
import com.rekenrinkel.ui.screens.exercise.ExerciseScreen
import com.rekenrinkel.ui.screens.home.HomeScreen
import com.rekenrinkel.ui.screens.onboarding.OnboardingScreen
import com.rekenrinkel.ui.screens.parent.ParentDashboardScreen
import com.rekenrinkel.ui.screens.premium.PremiumScreen
import com.rekenrinkel.ui.screens.profile.ProfileScreen
import com.rekenrinkel.ui.screens.session.SessionResultScreen
import com.rekenrinkel.ui.screens.settings.SettingsScreen
import com.rekenrinkel.ui.theme.RekenRinkelTheme
import com.rekenrinkel.ui.viewmodel.MainViewModel
import com.rekenrinkel.ui.viewmodel.MainViewModelFactory
import com.rekenrinkel.ui.viewmodel.NavigationEvent as MainNavEvent
import com.rekenrinkel.ui.viewmodel.SessionViewModel
import com.rekenrinkel.ui.viewmodel.SessionViewModelFactory
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
    
    // Initialize dependencies
    val database = remember { RekenRinkelDatabase.getDatabase(context) }
    val settingsDataStore = remember { SettingsDataStore(context) }
    
    val profileRepository = remember {
        ProfileRepository(database.profileDao(), settingsDataStore)
    }
    
    val progressRepository = remember {
        ProgressRepository(database.skillProgressDao())
    }
    
    val exerciseEngine = remember { ExerciseEngine() }
    val sessionEngine = remember {
        SessionEngine(exerciseEngine, progressRepository)
    }
    val exerciseValidator = remember { ExerciseValidator() }
    
    // Check if onboarding is completed
    val onboardingCompleted by settingsDataStore.onboardingCompleted.collectAsState(initial = false)
    
    val startDestination = if (onboardingCompleted) "home" else "onboarding"
    
    RekenRinkelTheme {
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
                        factory = MainViewModelFactory(
                            profileRepository,
                            progressRepository,
                            exerciseEngine,
                            sessionEngine
                        )
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
                        factory = MainViewModelFactory(
                            profileRepository,
                            progressRepository,
                            exerciseEngine,
                            sessionEngine
                        )
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
                    val soundEnabled by settingsDataStore.soundEnabled.collectAsState(initial = true)
                    
                    SettingsScreen(
                        soundEnabled = soundEnabled,
                        onSoundToggle = { /* TODO */ },
                        onOpenPremium = { navController.navigate("premium") },
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("parent") {
                    val viewModel: MainViewModel = viewModel(
                        factory = MainViewModelFactory(
                            profileRepository,
                            progressRepository,
                            exerciseEngine,
                            sessionEngine
                        )
                    )
                    val uiState by viewModel.uiState.collectAsState()
                    
                    ParentDashboardScreen(
                        progressList = uiState.progress,
                        totalSessions = 0,
                        averageSessionTime = "5 min",
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
                        factory = MainViewModelFactory(
                            profileRepository,
                            progressRepository,
                            exerciseEngine,
                            sessionEngine
                        )
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
                    val viewModel: SessionViewModel = viewModel(
                        factory = SessionViewModelFactory(
                            progressRepository,
                            exerciseEngine,
                            sessionEngine,
                            exerciseValidator
                        )
                    )
                    
                    val uiState by viewModel.uiState.collectAsState()
                    
                    // Get exercises from saved state
                    val exercises = navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.get<ArrayList<com.rekenrinkel.domain.model.Exercise>>("exercises")
                    
                    // Start session if not active and exercises available
                    LaunchedEffect(exercises) {
                        if (!uiState.isActive && exercises != null) {
                            viewModel.startSession(exercises.toList())
                        }
                    }
                    
                    // Handle navigation events
                    LaunchedEffect(Unit) {
                        viewModel.navigation.collectLatest { event ->
                            when (event) {
                                is com.rekenrinkel.ui.viewmodel.NavigationEvent.SessionComplete -> {
                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                        "result",
                                        event.result
                                    )
                                    navController.navigate("result") {
                                        popUpTo("home")
                                    }
                                }
                                is com.rekenrinkel.ui.viewmodel.NavigationEvent.BackToHome -> {
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            }
                        }
                    }
                    
                    val currentExercise = uiState.currentExercise
                    
                    if (currentExercise != null) {
                        ExerciseScreen(
                            exercise = currentExercise,
                            currentIndex = uiState.currentIndex + 1,
                            totalExercises = uiState.totalExercises,
                            onAnswer = { answer -> 
                                viewModel.submitAnswer(answer)
                            },
                            onSkip = { viewModel.skipSession() }
                        )
                    }
                }
                
                composable("result") {
                    val result = navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.get<SessionResult>("result")
                    
                    if (result != null) {
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
                }
            }
        }
    }
}