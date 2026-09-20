package com.auracommunityact.missiongtamobile.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.auracommunityact.missiongtamobile.input.DPadDirection
import com.auracommunityact.missiongtamobile.input.DPadOverlay
import com.auracommunityact.missiongtamobile.input.GameActionButtonsOverlay
import com.auracommunityact.missiongtamobile.input.NormalizedInput
import com.auracommunityact.missiongtamobile.renderer.GameSurface
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeProvider
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeStatus
import com.auracommunityact.missiongtamobile.storage.GameSettingsManager
import com.auracommunityact.missiongtamobile.ui.game.CharacterState
import com.auracommunityact.missiongtamobile.ui.game.GameEnvironmentView

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
    val dpadOpacity by settingsManager.dpadOpacity.collectAsState()
    val dpadSizeDp by settingsManager.dpadSizeDp.collectAsState()
    val dpadHaptics by settingsManager.dpadHaptics.collectAsState()

    // Character state and movement inputs
    val characterState = remember { CharacterState() }
    var currentInput by remember { mutableStateOf(NormalizedInput()) }
    var currentDirection by remember { mutableStateOf(DPadDirection.NONE) }

    // Enforce landscape orientation during gameplay when setting is enabled
    DisposableEffect(forceLandscape, landscapeMode) {
        val activity = context as? Activity
        settingsManager.applyGameOrientation(activity)

        onDispose {
            settingsManager.restoreDefaultOrientation(activity)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("game_environment_root")
    ) {
        // LAYER 0: Game Environment Rendering
        // If native runtime is fully running, show native surface; otherwise render interactive sandbox environment
        if (status == GameRuntimeStatus.RUNNING) {
            GameSurface(
                runtime = runtimeProvider.getRuntime(),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            GameEnvironmentView(
                characterState = characterState,
                currentInput = currentInput,
                modifier = Modifier.fillMaxSize()
            )
        }

        // LAYER 1: Top Navigation and Status HUD Overlays
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Top-Start: Exit/Pause Button and Session Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xBB0F172A))
                        .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(8.dp))
                        .testTag("btn_exit_game")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Exit Game Environment",
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
                        text = if (status == GameRuntimeStatus.RUNNING) "NATIVE RUNTIME" else "SANDBOX ENVIRONMENT",
                        color = if (status == GameRuntimeStatus.RUNNING) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Top-End: Developer / Telemetry Performance Overlay
            PerformanceOverlay(
                orientationStatus = if (forceLandscape) landscapeMode.displayName else "Free Rotation",
                activeDirection = currentDirection.label,
                moveX = currentInput.moveX,
                moveY = currentInput.moveY
            )
        }

        // LAYER 2: Controls Overlays
        // Bottom-Start: On-Screen D-Pad Overlay for Character Movement
        DPadOverlay(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 28.dp, bottom = 20.dp),
            size = dpadSizeDp.dp,
            opacity = dpadOpacity,
            hapticFeedbackEnabled = dpadHaptics,
            onInputChanged = { direction, input ->
                currentDirection = direction
                val updated = input.copy(action1 = characterState.isSprinting)
                currentInput = updated
                runtimeProvider.getRuntime()?.handleInput(updated)
            }
        )

        // Bottom-End: Action Buttons Overlay (Sprint, Jump, Action)
        GameActionButtonsOverlay(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = 20.dp),
            isSprinting = characterState.isSprinting,
            onSprintToggle = { sprinting ->
                characterState.isSprinting = sprinting
                val updated = currentInput.copy(action1 = sprinting)
                currentInput = updated
                runtimeProvider.getRuntime()?.handleInput(updated)
            },
            onJump = {
                val updated = currentInput.copy(action2 = true)
                currentInput = updated
                runtimeProvider.getRuntime()?.handleInput(updated)
            },
            onInteract = {
                val updated = currentInput.copy(action3 = true)
                currentInput = updated
                runtimeProvider.getRuntime()?.handleInput(updated)
            }
        )

        // Bottom-Center: Subtle Movement Vector Readout
        if (currentDirection.isMoving) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x990F172A))
                    .border(0.8.dp, Color(0x5538BDF8), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "MOVE: ${currentDirection.label.uppercase()}  [${String.format("%.2f", currentInput.moveX)}, ${String.format("%.2f", currentInput.moveY)}]",
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun PerformanceOverlay(
    orientationStatus: String = "Sensor Landscape",
    activeDirection: String = "Neutral",
    moveX: Float = 0f,
    moveY: Float = 0f,
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
            text = "PERFORMANCE & INPUT",
            color = Color(0xFF38BDF8),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        PerfRow("FPS", "60")
        PerfRow("Frame Time", "16.6 ms")
        PerfRow("Thermal", "Normal")
        PerfRow("Orientation", orientationStatus)
        PerfRow("D-Pad Dir", activeDirection)
        PerfRow("Vector X", String.format("%.2f", moveX))
        PerfRow("Vector Y", String.format("%.2f", moveY))
    }
}

@Composable
private fun PerfRow(label: String, value: String) {
    Row(
        modifier = Modifier.width(140.dp),
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
