package com.auracommunityact.missiongtamobile.input

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class TouchControlConfig(
    val opacity: Float = 0.65f,
    val buttonSizeDp: Int = 54,
    val joystickSizeDp: Int = 140,
    val enableHaptics: Boolean = true
)

class TouchController(
    private val context: Context,
    var onInputChanged: ((NormalizedInput) -> Unit)? = null
) {
    private val _currentInput = MutableStateFlow(NormalizedInput())
    val currentInput: StateFlow<NormalizedInput> = _currentInput.asStateFlow()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun updateJoystick(deltaX: Float, deltaY: Float, maxRadius: Float) {
        val dist = sqrt(deltaX * deltaX + deltaY * deltaY)
        if (dist <= 0f) {
            updateInput { it.copy(moveX = 0f, moveY = 0f) }
            return
        }
        val clampedDist = min(dist, maxRadius)
        val normalizedMagnitude = clampedDist / maxRadius
        val angle = atan2(deltaY, deltaX)
        val nx = (cos(angle) * normalizedMagnitude).coerceIn(-1f, 1f)
        val ny = (sin(angle) * normalizedMagnitude).coerceIn(-1f, 1f)

        updateInput { it.copy(moveX = nx, moveY = -ny) } // Invert Y for standard 3D forward
    }

    fun resetJoystick() {
        updateInput { it.copy(moveX = 0f, moveY = 0f) }
    }

    fun updateCameraDrag(deltaX: Float, deltaY: Float) {
        updateInput { it.copy(lookX = deltaX, lookY = deltaY) }
    }

    fun resetCamera() {
        updateInput { it.copy(lookX = 0f, lookY = 0f) }
    }

    fun setAction(actionName: String, pressed: Boolean, haptics: Boolean = true) {
        if (pressed && haptics) {
            triggerHaptic()
        }
        updateInput { current ->
            when (actionName) {
                "sprint" -> current.copy(sprint = pressed)
                "jump" -> current.copy(jump = pressed)
                "attack" -> current.copy(attack = pressed)
                "aim" -> current.copy(aim = pressed)
                "enterVehicle" -> current.copy(enterVehicle = pressed)
                "interaction" -> current.copy(interaction = pressed)
                "weaponWheel" -> current.copy(weaponWheel = pressed)
                "nextWeapon" -> current.copy(nextWeapon = pressed)
                "pause" -> current.copy(pause = pressed)
                else -> current
            }
        }
    }

    private fun updateInput(transform: (NormalizedInput) -> NormalizedInput) {
        val updated = transform(_currentInput.value)
        _currentInput.value = updated
        onInputChanged?.invoke(updated)
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(18)
            }
        } catch (_: Exception) {}
    }
}

@Composable
fun TouchControlsOverlay(
    controller: TouchController,
    config: TouchControlConfig = TouchControlConfig(),
    onPauseClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(config.opacity)
    ) {
        // TOP RIGHT: Pause and Quick Weapon
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TouchActionButton(
                icon = Icons.Default.SwapHoriz,
                label = "WEAPON",
                sizeDp = 48,
                onClick = { controller.setAction("nextWeapon", true, config.enableHaptics) },
                onRelease = { controller.setAction("nextWeapon", false, false) }
            )
            TouchActionButton(
                icon = Icons.Default.Pause,
                label = "PAUSE",
                sizeDp = 48,
                onClick = {
                    controller.setAction("pause", true, config.enableHaptics)
                    onPauseClicked()
                },
                onRelease = { controller.setAction("pause", false, false) }
            )
        }

        // BOTTOM LEFT: Virtual Movement Joystick
        VirtualJoystickView(
            sizeDp = config.joystickSizeDp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 28.dp, bottom = 28.dp),
            onJoystickMoved = { dx, dy, maxR ->
                controller.updateJoystick(dx, dy, maxR)
            },
            onJoystickReleased = {
                controller.resetJoystick()
            }
        )

        // CENTER-RIGHT: Camera Touch Drag Area
        Box(
            modifier = Modifier
                .fillMaxHeight(0.65f)
                .fillMaxWidth(0.45f)
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            controller.updateCameraDrag(dragAmount.x, dragAmount.y)
                        },
                        onDragEnd = { controller.resetCamera() },
                        onDragCancel = { controller.resetCamera() }
                    )
                }
        )

        // BOTTOM RIGHT: GTA Action Button Cluster
        ActionButtonsCluster(
            buttonSizeDp = config.buttonSizeDp,
            haptics = config.enableHaptics,
            controller = controller,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun VirtualJoystickView(
    sizeDp: Int,
    onJoystickMoved: (Float, Float, Float) -> Unit,
    onJoystickReleased: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadiusPx = (sizeDp * 1.5f)

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color(0x55000000))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val delta = offset - center
                        thumbOffset = delta
                        onJoystickMoved(delta.x, delta.y, maxRadiusPx)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = thumbOffset + dragAmount
                        val dist = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)
                        val clamped = if (dist > maxRadiusPx) {
                            val angle = atan2(newOffset.y, newOffset.x)
                            Offset(cos(angle) * maxRadiusPx, sin(angle) * maxRadiusPx)
                        } else newOffset
                        thumbOffset = clamped
                        onJoystickMoved(clamped.x, clamped.y, maxRadiusPx)
                    },
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        onJoystickReleased()
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        onJoystickReleased()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            // Outer ring
            drawCircle(
                color = Color(0x88FFFFFF),
                radius = size.width / 2.2f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
            // Thumb
            val thumbPos = center + thumbOffset
            drawCircle(
                color = Color(0xCCFFFFFF),
                radius = size.width / 5f,
                center = thumbPos
            )
        }
    }
}

@Composable
private fun ActionButtonsCluster(
    buttonSizeDp: Int,
    haptics: Boolean,
    controller: TouchController,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Vehicle / Enter and Aim
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TouchActionButton(
                icon = Icons.Default.DirectionsCar,
                label = "ENTER",
                sizeDp = buttonSizeDp,
                onClick = { controller.setAction("enterVehicle", true, haptics) },
                onRelease = { controller.setAction("enterVehicle", false, false) }
            )
            TouchActionButton(
                icon = Icons.Default.CenterFocusStrong,
                label = "AIM",
                sizeDp = buttonSizeDp,
                onClick = { controller.setAction("aim", true, haptics) },
                onRelease = { controller.setAction("aim", false, false) }
            )
        }

        // Row 2: Attack and Interact
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TouchActionButton(
                icon = Icons.Default.FlashOn,
                label = "ATTACK",
                sizeDp = buttonSizeDp,
                accentColor = Color(0xFFE53935),
                onClick = { controller.setAction("attack", true, haptics) },
                onRelease = { controller.setAction("attack", false, false) }
            )
            TouchActionButton(
                icon = Icons.Default.TouchApp,
                label = "USE",
                sizeDp = buttonSizeDp,
                onClick = { controller.setAction("interaction", true, haptics) },
                onRelease = { controller.setAction("interaction", false, false) }
            )
        }

        // Row 3: Sprint and Jump
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TouchActionButton(
                icon = Icons.Default.DirectionsRun,
                label = "SPRINT",
                sizeDp = buttonSizeDp,
                onClick = { controller.setAction("sprint", true, haptics) },
                onRelease = { controller.setAction("sprint", false, false) }
            )
            TouchActionButton(
                icon = Icons.Default.ArrowUpward,
                label = "JUMP",
                sizeDp = buttonSizeDp,
                accentColor = Color(0xFF1E88E5),
                onClick = { controller.setAction("jump", true, haptics) },
                onRelease = { controller.setAction("jump", false, false) }
            )
        }
    }
}

@Composable
private fun TouchActionButton(
    icon: ImageVector,
    label: String,
    sizeDp: Int,
    accentColor: Color = Color(0xAA333333),
    onClick: () -> Unit,
    onRelease: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(if (isPressed) Color.White else accentColor)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isPressed = true
                        onClick()
                    },
                    onDragEnd = {
                        isPressed = false
                        onRelease()
                    },
                    onDragCancel = {
                        isPressed = false
                        onRelease()
                    },
                    onDrag = { change, _ -> change.consume() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isPressed) Color.Black else Color.White,
                modifier = Modifier.size((sizeDp * 0.45f).dp)
            )
            Text(
                text = label,
                color = if (isPressed) Color.Black else Color.LightGray,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
