package com.auracommunityact.missiongtamobile.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracommunityact.missiongtamobile.renderer.GameSurface
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeProvider
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeStatus
import com.auracommunityact.missiongtamobile.storage.GameSettingsManager

@Composable
fun GameScreen(
    runtimeProvider: GameRuntimeProvider,
    onNavigateBack: () -> Unit,
    settingsManager: GameSettingsManager = GameSettingsManager.getInstance(LocalContext.current)
) {
    val context = LocalContext.current
    val status by runtimeProvider.status.collectAsState()
    val forceLandscape by settingsManager.forceLandscape.collectAsState()
    val landscapeMode by settingsManager.landscapeMode.collectAsState()

    // Enforce landscape orientation during gameplay when setting is enabled
    DisposableEffect(forceLandscape, landscapeMode) {
        val activity = context as? Activity
        settingsManager.applyGameOrientation(activity)

        onDispose {
            settingsManager.restoreDefaultOrientation(activity)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (status == GameRuntimeStatus.PENDING || status == GameRuntimeStatus.ERROR) {
            // Show fallback when runtime is not available
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "RUNTIME UNAVAILABLE",
                    color = Color.Red,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "The game runtime is currently not installed or could not be initialized.",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) {
                    Text("RETURN TO HOME", color = Color.White)
                }
            }
        } else {
            // Show the actual game surface
            GameSurface(
                runtime = runtimeProvider.getRuntime(),
                modifier = Modifier.fillMaxSize()
            )
            
            // Temporary Pause/Back Button overlay for testing the shell
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Exit Game",
                    tint = Color.White
                )
            }
            
            // Performance Overlay Foundation (Developer Mode)
            PerformanceOverlay(
                orientationStatus = if (forceLandscape) landscapeMode.displayName else "Free Rotation",
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            )
        }
    }
}

@Composable
fun PerformanceOverlay(
    orientationStatus: String = "Sensor Landscape",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0x88000000))
            .padding(8.dp)
    ) {
        Text("PERFORMANCE", color = Color.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        PerfRow("FPS", "60")
        PerfRow("Frame Time", "16.6 ms")
        PerfRow("RAM", "N/A")
        PerfRow("GPU", "N/A")
        PerfRow("CPU", "N/A")
        PerfRow("Thermal", "Normal")
        PerfRow("Runtime", "Pending")
        PerfRow("Orientation", orientationStatus)
    }
}

@Composable
private fun PerfRow(label: String, value: String) {
    Row(
        modifier = Modifier.width(120.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.LightGray, fontSize = 10.sp)
        Text(text = value, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}
