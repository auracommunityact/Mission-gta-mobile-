package com.auracommunityact.missiongtamobile.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeManager
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeProvider
import com.auracommunityact.missiongtamobile.runtime.state.DriverStatus
import com.auracommunityact.missiongtamobile.runtime.state.GameDataStatus
import com.auracommunityact.missiongtamobile.runtime.state.GameStatus
import com.auracommunityact.missiongtamobile.runtime.state.RendererStatus
import com.auracommunityact.missiongtamobile.runtime.state.RuntimeStatus
import com.auracommunityact.missiongtamobile.storage.GameResourceManager

@Composable
fun GameHomeScreen(
    runtimeProvider: GameRuntimeProvider,
    resourceManager: GameResourceManager,
    onStartGame: () -> Unit,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val runtimeManager = runtimeProvider.runtimeManager

    val runtimeStatus by (runtimeManager?.runtimeStatus ?: remember { kotlinx.coroutines.flow.MutableStateFlow(RuntimeStatus.CHECKING) }).collectAsState()
    val gameDataStatus by (runtimeManager?.gameDataManager?.status ?: remember { kotlinx.coroutines.flow.MutableStateFlow(GameDataStatus.NOT_SELECTED) }).collectAsState()
    val driverStatus by (runtimeManager?.driverManager?.status ?: remember { kotlinx.coroutines.flow.MutableStateFlow(DriverStatus.DETECTING) }).collectAsState()
    val currentDriver by (runtimeManager?.driverManager?.currentDriver ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val rendererStatus by (runtimeManager?.rendererStatus ?: remember { kotlinx.coroutines.flow.MutableStateFlow(RendererStatus.INITIALIZING) }).collectAsState()
    val gameStatus by (runtimeManager?.gameStatus ?: remember { kotlinx.coroutines.flow.MutableStateFlow(GameStatus.STOPPED) }).collectAsState()
    val missingFiles by (runtimeManager?.gameDataManager?.missingFiles ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
    val lastError by (runtimeManager?.lastError ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()

    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            resourceManager.takePersistableUriPermission(uri)
            runtimeManager?.gameDataManager?.selectGameDirectory(uri)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        // Navigation Menu
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(280.dp)
                .background(Color(0xFF121212))
                .padding(vertical = 32.dp, horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "GTA V",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "MOBILE",
                color = Color.Gray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            MenuButton(
                text = "SELECT GAME DATA",
                onClick = { documentTreeLauncher.launch(null) }
            )

            MenuButton(
                text = "START GAME",
                onClick = {
                    if (gameDataStatus != GameDataStatus.READY) {
                        Toast.makeText(context, "Cannot start: Game data not ready (${gameDataStatus.displayName})", Toast.LENGTH_LONG).show()
                    }
                    onStartGame()
                }
            )

            MenuButton(text = "DIAGNOSTICS", onClick = onDiagnostics)
            MenuButton(text = "SETTINGS", onClick = onSettings)
            MenuButton(text = "EXIT", onClick = onExit)
        }

        // Status Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(Color(0xFF222222))
                    .padding(24.dp)
            ) {
                item {
                    Text(
                        text = "GTA V MOBILE LAUNCHER",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    HorizontalDivider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Real Status Display (No hardcoded fake "READY" or "PASSED")
                    SystemStatusRow("Runtime Engine", runtimeStatus.displayName, runtimeStatus == RuntimeStatus.READY)
                    SystemStatusRow("Game Data", gameDataStatus.displayName, gameDataStatus == GameDataStatus.READY)
                    SystemStatusRow("GPU Driver", "${driverStatus.displayName} (${currentDriver?.name ?: "Detecting"})", driverStatus == DriverStatus.READY)
                    SystemStatusRow("Renderer", rendererStatus.displayName, rendererStatus == RendererStatus.READY)
                    SystemStatusRow("Game Process", gameStatus.displayName, gameStatus == GameStatus.RUNNING)

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (gameDataStatus == GameDataStatus.READY) {
                        Text(
                            text = "Validation: PASSED",
                            color = Color(0xFF81C784),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "All required game directories and root resources are verified.",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else if (gameDataStatus == GameDataStatus.INVALID) {
                        Text(
                            text = "Missing Files/Directories:",
                            color = Color(0xFFE57373),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        missingFiles.take(6).forEach { file ->
                            Text(text = "- $file", color = Color.LightGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        if (missingFiles.size > 6) {
                            Text(text = "...and ${missingFiles.size - 6} more", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else if (gameDataStatus == GameDataStatus.NOT_SELECTED) {
                        Text(
                            text = "No game data selected. Click 'SELECT GAME DATA' to choose your game directory.",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }

                    if (lastError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Diagnostics Alert:",
                            color = Color(0xFFF59E0B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = lastError!!,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemStatusRow(label: String, status: String, isOk: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp)
        Text(
            text = status,
            color = if (isOk) Color(0xFF4CAF50) else Color(0xFFFFB300),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MenuButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Color.DarkGray,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
    }
}
