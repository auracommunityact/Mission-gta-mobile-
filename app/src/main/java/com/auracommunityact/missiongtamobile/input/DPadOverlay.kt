package com.auracommunityact.missiongtamobile.input

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An on-screen D-Pad overlay for character movement in the game environment.
 * Supports both continuous touch drag gestures across 8 directions and discrete
 * directional button presses with haptic feedback and glowing visual states.
 *
 * @param modifier Custom modifier for positioning and sizing the overlay.
 * @param size Outer dimension of the D-Pad.
 * @param opacity Base alpha transparency when idle.
 * @param hapticFeedbackEnabled Whether to trigger tactile haptics on direction changes.
 * @param onInputChanged Callback invoked whenever the user's directional input changes.
 */
@Composable
fun DPadOverlay(
    modifier: Modifier = Modifier,
    size: Dp = 156.dp,
    opacity: Float = 0.85f,
    hapticFeedbackEnabled: Boolean = true,
    onInputChanged: (DPadDirection, NormalizedInput) -> Unit
) {
    val view = LocalView.current
    var activeDirection by remember { mutableStateOf(DPadDirection.NONE) }
    var isTouching by remember { mutableStateOf(false) }

    // Notify caller when active direction updates
    fun updateDirection(newDirection: DPadDirection) {
        if (activeDirection != newDirection) {
            activeDirection = newDirection
            if (hapticFeedbackEnabled && newDirection.isMoving) {
                try {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                } catch (_: Exception) {}
            }
            onInputChanged(newDirection, newDirection.toNormalizedInput())
        }
    }

    // Dynamic animations for thumb rest displacement
    val animatedShiftX by animateFloatAsState(
        targetValue = activeDirection.moveX * 18f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "thumbShiftX"
    )
    val animatedShiftY by animateFloatAsState(
        targetValue = activeDirection.moveY * 18f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "thumbShiftY"
    )

    val currentAlpha by animateFloatAsState(
        targetValue = if (isTouching || activeDirection.isMoving) 1.0f else opacity,
        label = "dpadAlpha"
    )

    val scaleFactor by animateFloatAsState(
        targetValue = if (isTouching || activeDirection.isMoving) 1.03f else 1.0f,
        label = "dpadScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scaleFactor)
            .alpha(currentAlpha)
            .testTag("dpad_overlay")
            .semantics { contentDescription = "Character Movement D-Pad Overlay" }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isTouching = true
                        val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        updateDirection(DPadDirection.fromDisplacement(dx, dy, deadzoneRadius = 14f))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                        val dx = change.position.x - center.x
                        val dy = change.position.y - center.y
                        updateDirection(DPadDirection.fromDisplacement(dx, dy, deadzoneRadius = 14f))
                    },
                    onDragEnd = {
                        isTouching = false
                        updateDirection(DPadDirection.NONE)
                    },
                    onDragCancel = {
                        isTouching = false
                        updateDirection(DPadDirection.NONE)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer glow & radial backdrop
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 2f

            // Ambient navy-blue radial gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x991E293B),
                        Color(0xBB0F172A),
                        Color(0xDD090D16)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Neon cyan border highlight
            drawCircle(
                color = if (activeDirection.isMoving) Color(0x8838BDF8) else Color(0x3338BDF8),
                radius = radius - 1.5f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )

            // Diagonal quadrant indicator ticks
            val tickRadius = radius * 0.76f
            val angles = listOf(45.0, 135.0, 225.0, 315.0)
            for (ang in angles) {
                val rad = Math.toRadians(ang)
                val tickCenter = Offset(
                    center.x + (tickRadius * kotlin.math.cos(rad)).toFloat(),
                    center.y + (tickRadius * kotlin.math.sin(rad)).toFloat()
                )
                drawCircle(
                    color = Color(0x4464748B),
                    radius = 2.5f,
                    center = tickCenter
                )
            }
        }

        // Cross Pad Wings (Up, Down, Left, Right)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            // UP BUTTON
            DPadButton(
                direction = DPadDirection.UP,
                isActive = activeDirection == DPadDirection.UP ||
                        activeDirection == DPadDirection.UP_LEFT ||
                        activeDirection == DPadDirection.UP_RIGHT,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag("dpad_up"),
                onClick = {
                    updateDirection(if (activeDirection == DPadDirection.UP) DPadDirection.NONE else DPadDirection.UP)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move Up",
                    tint = if (activeDirection == DPadDirection.UP ||
                        activeDirection == DPadDirection.UP_LEFT ||
                        activeDirection == DPadDirection.UP_RIGHT
                    ) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                    modifier = Modifier.size(32.dp)
                )
            }

            // DOWN BUTTON
            DPadButton(
                direction = DPadDirection.DOWN,
                isActive = activeDirection == DPadDirection.DOWN ||
                        activeDirection == DPadDirection.DOWN_LEFT ||
                        activeDirection == DPadDirection.DOWN_RIGHT,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .testTag("dpad_down"),
                onClick = {
                    updateDirection(if (activeDirection == DPadDirection.DOWN) DPadDirection.NONE else DPadDirection.DOWN)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move Down",
                    tint = if (activeDirection == DPadDirection.DOWN ||
                        activeDirection == DPadDirection.DOWN_LEFT ||
                        activeDirection == DPadDirection.DOWN_RIGHT
                    ) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                    modifier = Modifier.size(32.dp)
                )
            }

            // LEFT BUTTON
            DPadButton(
                direction = DPadDirection.LEFT,
                isActive = activeDirection == DPadDirection.LEFT ||
                        activeDirection == DPadDirection.UP_LEFT ||
                        activeDirection == DPadDirection.DOWN_LEFT,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .testTag("dpad_left"),
                onClick = {
                    updateDirection(if (activeDirection == DPadDirection.LEFT) DPadDirection.NONE else DPadDirection.LEFT)
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Move Left",
                    tint = if (activeDirection == DPadDirection.LEFT ||
                        activeDirection == DPadDirection.UP_LEFT ||
                        activeDirection == DPadDirection.DOWN_LEFT
                    ) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                    modifier = Modifier.size(32.dp)
                )
            }

            // RIGHT BUTTON
            DPadButton(
                direction = DPadDirection.RIGHT,
                isActive = activeDirection == DPadDirection.RIGHT ||
                        activeDirection == DPadDirection.UP_RIGHT ||
                        activeDirection == DPadDirection.DOWN_RIGHT,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .testTag("dpad_right"),
                onClick = {
                    updateDirection(if (activeDirection == DPadDirection.RIGHT) DPadDirection.NONE else DPadDirection.RIGHT)
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Move Right",
                    tint = if (activeDirection == DPadDirection.RIGHT ||
                        activeDirection == DPadDirection.UP_RIGHT ||
                        activeDirection == DPadDirection.DOWN_RIGHT
                    ) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Center Pivot Hub / Floating Thumb Indicator
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = animatedShiftX
                    translationY = animatedShiftY
                }
                .size(size * 0.34f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (activeDirection.isMoving) {
                            listOf(Color(0xFF2563EB), Color(0xFF0F172A))
                        } else {
                            listOf(Color(0xFF334155), Color(0xFF0F172A))
                        }
                    )
                )
                .border(
                    width = 1.5.dp,
                    color = if (activeDirection.isMoving) Color(0xFF38BDF8) else Color(0x6694A3B8),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    updateDirection(DPadDirection.NONE)
                }
                .testTag("dpad_center"),
            contentAlignment = Alignment.Center
        ) {
            // Inner metallic accent dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (activeDirection.isMoving) Color(0xFF38BDF8) else Color(0x8894A3B8)
                    )
            )
        }
    }
}

@Composable
private fun DPadButton(
    direction: DPadDirection,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isActive) {
                    Brush.radialGradient(
                        colors = listOf(Color(0x662563EB), Color(0x330F172A))
                    )
                } else {
                    Brush.radialGradient(
                        colors = listOf(Color(0x22334155), Color(0x110F172A))
                    )
                }
            )
            .border(
                width = if (isActive) 1.5.dp else 0.8.dp,
                color = if (isActive) Color(0xFF38BDF8) else Color(0x2294A3B8),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
