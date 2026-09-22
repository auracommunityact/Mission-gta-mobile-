package com.auracommunityact.missiongtamobile.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.auracommunityact.missiongtamobile.device.PerformanceManager
import com.auracommunityact.missiongtamobile.device.RealtimePerformanceData
import com.auracommunityact.missiongtamobile.input.TouchControlConfig
import com.auracommunityact.missiongtamobile.input.TouchController
import com.auracommunityact.missiongtamobile.input.TouchControlsOverlay
import com.auracommunityact.missiongtamobile.renderer.GameSurface
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeManager
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeProvider
import com.auracommunityact.missiongtamobile.runtime.state.GameStatus
import com.auracommunityact.missiongtamobile.runtime.state.RendererStatus
import com.auracommunityact.missiongtamobile.runtime.state.RuntimeStatus
import com.auracommunityact.missiongtamobile.storage.GameSettingsManager

@Composable
fun GameScreen(
    runtimeProvider: GameRuntimeProvider,
    onNavigateBack: () -> Unit,
    settingsManager: GameSettingsManager = GameSettingsManager.getInstance(LocalContext.current)
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val runtimeManager = runtimeProvider.runtimeManager

    val defaultPerfData = remember {
        RealtimePerformanceData(
            fps = null,
            frameTimeMs = null,
            totalRamMb = 0,
            availableRamMb = 0,
            ramUsagePercent = 0,
            thermalStatus = "N/A",
            thermalHeadroom = null,
            isGameRunning = false
        )
    }

    val gameStatus by (runtimeManager?.gameStatus ?: remember { kotlinx.coroutines.flow.MutableStateFlow(GameStatus.STOPPED) }).collectAsState()
    val runtimeStatus by (runtimeManager?.runtimeStatus ?: remember { kotlinx.coroutines.flow.MutableStateFlow(RuntimeStatus.CHECKING) }).collectAsState()
    val rendererStatus by (runtimeManager?.rendererStatus ?: remember { kotlinx.coroutines.flow.MutableStateFlow(RendererStatus.INITIALIZING) }).collectAsState()
    val pipelineProgress by (runtimeManager?.pipelineProgress ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsState()
    val pipelineStepNumber by (runtimeManager?.pipelineStepNumber ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsState()
    val lastError by (runtimeManager?.lastError ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsState()

    val forceLandscape by settingsManager.forceLandscape.collectAsState()
    val landscapeMode by settingsManager.landscapeMode.collectAsState()
    val dpadOpacity by settingsManager.dpadOpacity.collectAsState()
    val dpadSizeDp by settingsManager.dpadSizeDp.collectAsState()
    val dpadHaptics by settingsManager.dpadHaptics.collectAsState()

    val performanceManager = runtimeManager?.performanceManager
    val perfData by (performanceManager?.performanceData ?: remember { kotlinx.coroutines.flow.MutableStateFlow(defaultPerfData) }).collectAsState()

    val touchController = runtimeManager?.touchController ?: remember { TouchController(context) }

    var showPauseMenu by remember { mutableStateOf(false) }

    // Enforce landscape orientation during gameplay
    DisposableEffect(forceLandscape, landscapeMode) {
        settingsManager.applyGameOrientation(activity)
        onDispose {
            settingsManager.restoreDefaultOrientation(activity)
        }
    }

    // Trigger 16-step startup pipeline on launch
    LaunchedEffect(Unit) {
        if (runtimeManager != null && gameStatus == GameStatus.STOPPED) {
            runtimeManager.startLaunchPipeline(null) { success, _ ->
                if (!success) {
                    // Failure caught and reflected in state
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("game_environment_root")
    ) {
        // LAYER 0: Fullscreen Dedicated Game Surface (SurfaceView)
        GameSurface(
            runtime = runtimeManager ?: runtimeProvider.getRuntime(),
            modifier = Modifier.fillMaxSize()
        )

        // LAYER 1: Runtime Initialization / Error State HUD (when not actively running)
        if (gameStatus != GameStatus.RUNNING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (gameStatus == GameStatus.STARTING) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "INITIALIZING RUNTIME PIPELINE",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = pipelineProgress ?: "Executing step $pipelineStepNumber/16...",
                            color = Color(0xFF38BDF8),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (gameStatus == GameStatus.CRASHED) "GAME PROCESS CRASHED" else "RUNTIME INITIALIZATION STOPPED",
                            color = Color(0xFFEF4444),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = lastError ?: "Runtime engine not installed or prerequisite step failed.",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = {
                                    runtimeManager?.quitToLauncher()
                                    onNavigateBack()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                            ) {
                                Text("QUIT TO LAUNCHER", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    runtimeManager?.startLaunchPipeline(null) { _, _ -> }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Text("RETRY LAUNCH", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // LAYER 2: Touch Controls Overlay (Movement Joystick, Camera, Action Buttons)
        if (gameStatus == GameStatus.RUNNING) {
            TouchControlsOverlay(
                controller = touchController,
                config = TouchControlConfig(
                    opacity = dpadOpacity,
                    buttonSizeDp = dpadSizeDp,
                    enableHaptics = dpadHaptics
                ),
                onPauseClicked = { showPauseMenu = true },
                modifier = Modifier.fillMaxSize()
            )
        }

        // LAYER 3: Top Navigation Bar and Real Performance Telemetry HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Top-Start: Pause / Menu Button and Status Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showPauseMenu = true },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xBB0F172A))
                        .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(8.dp))
                        .testTag("btn_pause_game")
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Game Menu",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x990F172A))
                        .border(0.8.dp, Color(0x4438BDF8), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "VULKAN RENDERER: ${rendererStatus.name} | RUNTIME: ${runtimeStatus.name}",
                        color = if (gameStatus == GameStatus.RUNNING) Color(0xFF4ADE80) else Color(0xFFF59E0B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Top-End: Real-time Telemetry HUD (no fake values; displays N/A when unavailable)
            RealtimeTelemetryOverlay(
                data = perfData,
                orientationStatus = if (forceLandscape) landscapeMode.displayName else "Free Rotation"
            )
        }

        // Pause Menu Dialog (Section 16: Quit to Launcher vs Exit App)
        if (showPauseMenu) {
            PauseMenuDialog(
                onResume = { showPauseMenu = false },
                onQuitToLauncher = {
                    showPauseMenu = false
                    runtimeManager?.quitToLauncher()
                    onNavigateBack()
                },
                onExitApp = {
                    showPauseMenu = false
                    runtimeManager?.exitApp(activity)
                }
            )
        }
    }
}

@Composable
fun RealtimeTelemetryOverlay(
    data: RealtimePerformanceData,
    orientationStatus: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC0F172A))
            .border(0.8.dp, Color(0x3338BDF8), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "REALTIME TELEMETRY",
            color = Color(0xFF38BDF8),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        TelemetryRow("FPS", data.fps?.let { "%.1f".format(it) } ?: "N/A")
        TelemetryRow("Frame Time", data.frameTimeMs?.let { "%.1f ms".format(it) } ?: "N/A")
        TelemetryRow("RAM Avail", "${data.availableRamMb} MB (${data.ramUsagePercent}% used)")
        TelemetryRow("Thermal", data.thermalStatus)
        TelemetryRow("Orientation", orientationStatus)
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.width(160.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 9.sp)
        Text(
            text = value,
            color = Color.White,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PauseMenuDialog(
    onResume: () -> Unit,
    onQuitToLauncher: () -> Unit,
    onExitApp: () -> Unit
) {
    Dialog(onDismissRequest = onResume) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GAME PAUSED",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            PauseMenuOption(
                text = "RESUME",
                icon = Icons.Default.PlayArrow,
                onClick = onResume,
                color = Color(0xFF0284C7)
            )

            Spacer(modifier = Modifier.height(12.dp))

            PauseMenuOption(
                text = "QUIT TO LAUNCHER",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onQuitToLauncher,
                color = Color(0xFF334155)
            )

            Spacer(modifier = Modifier.height(12.dp))

            PauseMenuOption(
                text = "EXIT APP",
                icon = Icons.Default.Close,
                onClick = onExitApp,
                color = Color(0xFF991B1B)
            )
        }
    }
}

@Composable
private fun PauseMenuOption(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    color: Color
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = text, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
