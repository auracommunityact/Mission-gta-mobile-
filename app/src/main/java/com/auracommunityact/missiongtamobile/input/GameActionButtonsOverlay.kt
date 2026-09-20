package com.auracommunityact.missiongtamobile.input

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * On-screen action buttons overlay (Sprint, Jump, Interact) positioned on the bottom-right
 * complementing the D-Pad on the bottom-left.
 */
@Composable
fun GameActionButtonsOverlay(
    modifier: Modifier = Modifier,
    isSprinting: Boolean,
    onSprintToggle: (Boolean) -> Unit,
    onJump: () -> Unit,
    onInteract: () -> Unit
) {
    val view = LocalView.current

    Box(
        modifier = modifier
            .size(170.dp)
            .testTag("action_buttons_overlay")
    ) {
        // Sprint Button (Top / Diamond north)
        ActionButton(
            label = "SPRINT",
            icon = if (isSprinting) Icons.AutoMirrored.Filled.DirectionsRun else Icons.AutoMirrored.Filled.DirectionsWalk,
            isActive = isSprinting,
            activeColor = Color(0xFF38BDF8),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .testTag("btn_sprint"),
            onClick = {
                try {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } catch (_: Exception) {}
                onSprintToggle(!isSprinting)
            }
        )

        // Jump Button (Right / Diamond east)
        ActionButton(
            label = "JUMP",
            icon = Icons.Default.KeyboardDoubleArrowUp,
            isActive = false,
            activeColor = Color(0xFF60A5FA),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .testTag("btn_jump"),
            onClick = {
                try {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                } catch (_: Exception) {}
                onJump()
            }
        )

        // Interact / Action Button (Center / Diamond south)
        ActionButton(
            label = "ACTION",
            icon = Icons.Default.PanTool,
            isActive = false,
            activeColor = Color(0xFFFBBF24),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .testTag("btn_action"),
            onClick = {
                try {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                } catch (_: Exception) {}
                onInteract()
            }
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = if (isActive) {
                        listOf(activeColor.copy(alpha = 0.5f), Color(0xCC0F172A))
                    } else {
                        listOf(Color(0x991E293B), Color(0xDD0F172A))
                    }
                )
            )
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) activeColor else Color(0x4494A3B8),
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) activeColor else Color.White,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            color = if (isActive) activeColor else Color(0xFF94A3B8),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
