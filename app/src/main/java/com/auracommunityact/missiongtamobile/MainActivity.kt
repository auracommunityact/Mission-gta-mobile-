package com.auracommunityact.missiongtamobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeProvider
import com.auracommunityact.missiongtamobile.storage.GameResourceManager
import com.auracommunityact.missiongtamobile.ui.screens.DiagnosticsScreen
import com.auracommunityact.missiongtamobile.ui.screens.GameHomeScreen
import com.auracommunityact.missiongtamobile.ui.screens.GameScreen
import com.auracommunityact.missiongtamobile.ui.screens.Screen
import com.auracommunityact.missiongtamobile.ui.screens.SettingsScreen
import com.auracommunityact.missiongtamobile.ui.screens.StartupScreen
import com.auracommunityact.missiongtamobile.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    
    private val runtimeProvider = GameRuntimeProvider()
    private lateinit var resourceManager: GameResourceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        resourceManager = GameResourceManager(applicationContext)

        // Immersive mode
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Startup.route
                    ) {
                        composable(Screen.Startup.route) {
                            StartupScreen(
                                runtimeProvider = runtimeProvider,
                                resourceManager = resourceManager,
                                onStartupComplete = {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Startup.route) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(Screen.Home.route) {
                            GameHomeScreen(
                                runtimeProvider = runtimeProvider,
                                resourceManager = resourceManager,
                                onStartGame = { navController.navigate(Screen.Game.route) },
                                onDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
                                onSettings = { navController.navigate(Screen.Settings.route) },
                                onExit = { finish() }
                            )
                        }
                        composable(Screen.Game.route) {
                            GameScreen(
                                runtimeProvider = runtimeProvider,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.Diagnostics.route) {
                            DiagnosticsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                resourceManager = resourceManager,
                                runtimeProvider = runtimeProvider
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                resourceManager = resourceManager
                            )
                        }
                    }
                }
            }
        }
    }
}
