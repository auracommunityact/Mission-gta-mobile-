package com.auracommunityact.missiongtamobile.ui.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracommunityact.missiongtamobile.input.NormalizedInput
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * World dimensions for the game environment sandbox.
 */
const val WORLD_WIDTH = 2800f
const val WORLD_HEIGHT = 2800f

/**
 * Represents the player character state within the game environment.
 */
class CharacterState(
    initialX: Float = 1400f,
    initialY: Float = 1400f
) {
    var posX by mutableFloatStateOf(initialX)
    var posY by mutableFloatStateOf(initialY)
    var headingDegrees by mutableFloatStateOf(0f)
    var speedKmh by mutableFloatStateOf(0f)
    var isSprinting by mutableStateOf(false)
    var isMoving by mutableStateOf(false)

    fun updateMovement(input: NormalizedInput, deltaSeconds: Float) {
        val mx = input.moveX
        val my = input.moveY
        val inputMagnitude = sqrt((mx * mx + my * my).toDouble()).toFloat()

        if (inputMagnitude > 0.05f) {
            isMoving = true
            // Calculate target heading in screen coordinates (0 is right, 90 down, 180 left, 270 up)
            val angle = Math.toDegrees(atan2(my.toDouble(), mx.toDouble())).toFloat()
            headingDegrees = angle

            val baseSpeed = if (isSprinting || input.action1) 480f else 240f
            val moveStep = baseSpeed * deltaSeconds

            posX = (posX + mx * moveStep).coerceIn(100f, WORLD_WIDTH - 100f)
            posY = (posY + my * moveStep).coerceIn(100f, WORLD_HEIGHT - 100f)
            speedKmh = if (isSprinting || input.action1) 28f else 14f
        } else {
            isMoving = false
            speedKmh = 0f
        }
    }
}

/**
 * Interactive game environment viewport that renders the city terrain, roads, buildings,
 * and player character responding to on-screen D-Pad movement.
 */
@Composable
fun GameEnvironmentView(
    characterState: CharacterState,
    currentInput: NormalizedInput,
    modifier: Modifier = Modifier
) {
    // Frame ticker for smooth physics and camera updates
    var lastTimeNanos by remember { mutableLongStateOf(0L) }
    val infiniteAnim = rememberInfiniteTransition(label = "gameWorldPulse")
    val walkCyclePhase by infiniteAnim.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "walkCycle"
    )

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (lastTimeNanos != 0L) {
                    val deltaSec = ((frameTimeNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    characterState.updateMovement(currentInput, deltaSec)
                }
                lastTimeNanos = frameTimeNanos
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        Canvas(modifier = Modifier.fillMaxSize().testTag("game_viewport_canvas")) {
            val viewWidth = size.width
            val viewHeight = size.height

            // Camera offset to keep player centered
            val cameraX = characterState.posX - viewWidth / 2f
            val cameraY = characterState.posY - viewHeight / 2f

            // 1. Draw World Background & City Grid
            drawCityWorld(cameraX, cameraY, viewWidth, viewHeight)

            // 2. Draw Player Character
            drawPlayerCharacter(
                screenCenterX = viewWidth / 2f,
                screenCenterY = viewHeight / 2f,
                headingDeg = characterState.headingDegrees,
                isMoving = characterState.isMoving,
                walkPhase = walkCyclePhase
            )
        }

        // Mini-Map Radar (Top-Left HUD)
        MiniMapRadar(
            playerX = characterState.posX,
            playerY = characterState.posY,
            headingDeg = characterState.headingDegrees,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 72.dp, top = 16.dp)
        )

        // Player Status HUD (Health & Armor)
        PlayerStatusHUD(
            speedKmh = characterState.speedKmh,
            playerX = characterState.posX,
            playerY = characterState.posY,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )
    }
}

/**
 * Draws the city world environment including asphalt avenues, lane markings, sidewalks, and city blocks.
 */
private fun DrawScope.drawCityWorld(
    cameraX: Float,
    cameraY: Float,
    viewWidth: Float,
    viewHeight: Float
) {
    // Base grass / asphalt terrain
    drawRect(Color(0xFF1E293B))

    val blockSize = 400f
    val roadWidth = 120f
    val totalStep = blockSize + roadWidth

    val startCol = ((cameraX - totalStep) / totalStep).toInt().coerceAtLeast(0)
    val endCol = ((cameraX + viewWidth + totalStep) / totalStep).toInt()
    val startRow = ((cameraY - totalStep) / totalStep).toInt().coerceAtLeast(0)
    val endRow = ((cameraY + viewHeight + totalStep) / totalStep).toInt()

    // Draw City Blocks and Buildings
    for (col in startCol..endCol) {
        for (row in startRow..endRow) {
            val blockLeft = col * totalStep - cameraX
            val blockTop = row * totalStep - cameraY

            // Sidewalk perimeter
            drawRoundRect(
                color = Color(0xFF334155),
                topLeft = Offset(blockLeft, blockTop),
                size = Size(blockSize, blockSize),
                cornerRadius = CornerRadius(16f, 16f)
            )

            // Building structure
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B)),
                    start = Offset(blockLeft, blockTop),
                    end = Offset(blockLeft + blockSize, blockTop + blockSize)
                ),
                topLeft = Offset(blockLeft + 16f, blockTop + 16f),
                size = Size(blockSize - 32f, blockSize - 32f),
                cornerRadius = CornerRadius(12f, 12f)
            )

            // Rooftop architectural details
            drawRect(
                color = Color(0xFF2563EB).copy(alpha = 0.25f),
                topLeft = Offset(blockLeft + 40f, blockTop + 40f),
                size = Size(blockSize - 80f, blockSize - 80f)
            )
        }
    }

    // Draw Roads (horizontal and vertical strips)
    val roadColor = Color(0xFF090D16)
    val markingColor = Color(0xFFFBBF24).copy(alpha = 0.7f)

    for (row in startRow..endRow + 1) {
        val roadTop = row * totalStep - roadWidth - cameraY
        drawRect(
            color = roadColor,
            topLeft = Offset(0f, roadTop),
            size = Size(viewWidth, roadWidth)
        )
        // Center dashed yellow lane markings
        var dashX = 0f
        while (dashX < viewWidth) {
            drawRect(
                color = markingColor,
                topLeft = Offset(dashX, roadTop + roadWidth / 2f - 2f),
                size = Size(24f, 4f)
            )
            dashX += 48f
        }
    }

    for (col in startCol..endCol + 1) {
        val roadLeft = col * totalStep - roadWidth - cameraX
        drawRect(
            color = roadColor,
            topLeft = Offset(roadLeft, 0f),
            size = Size(roadWidth, viewHeight)
        )
        // Center dashed yellow lane markings
        var dashY = 0f
        while (dashY < viewHeight) {
            drawRect(
                color = markingColor,
                topLeft = Offset(roadLeft + roadWidth / 2f - 2f, dashY),
                size = Size(4f, 24f)
            )
            dashY += 48f
        }
    }
}

/**
 * Draws the player character sprite with animated legs, torso, and directional facing indicator.
 */
private fun DrawScope.drawPlayerCharacter(
    screenCenterX: Float,
    screenCenterY: Float,
    headingDeg: Float,
    isMoving: Boolean,
    walkPhase: Float
) {
    val center = Offset(screenCenterX, screenCenterY)

    // Directional flashlight / forward perception cone
    rotate(headingDeg, pivot = center) {
        val fovPath = Path().apply {
            moveTo(center.x, center.y)
            lineTo(center.x + 180f, center.y - 60f)
            lineTo(center.x + 200f, center.y)
            lineTo(center.x + 180f, center.y + 60f)
            close()
        }
        drawPath(
            path = fovPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0x3338BDF8), Color(0x0038BDF8)),
                center = center,
                radius = 200f
            )
        )
    }

    // Soft drop shadow
    drawCircle(
        color = Color(0x77000000),
        radius = 22f,
        center = Offset(center.x + 3f, center.y + 6f)
    )

    // Animated walking feet when moving
    if (isMoving) {
        val legSwing = (sin(Math.toRadians(walkPhase.toDouble())) * 12f).toFloat()
        rotate(headingDeg, pivot = center) {
            // Left foot
            drawRoundRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(center.x - 14f + legSwing, center.y - 12f),
                size = Size(10f, 6f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            // Right foot
            drawRoundRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(center.x - 14f - legSwing, center.y + 6f),
                size = Size(10f, 6f),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }

    // Main character body with rotation
    rotate(headingDeg, pivot = center) {
        // Shoulders / Torso (Navy Blue Jacket)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF1E3A8A), Color(0xFF2563EB))
            ),
            topLeft = Offset(center.x - 12f, center.y - 14f),
            size = Size(24f, 28f),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // Head (top-down view)
        drawCircle(
            color = Color(0xFFFDE68A),
            radius = 9f,
            center = center
        )

        // Cap / Hair visor facing forward
        drawArc(
            color = Color(0xFF0F172A),
            startAngle = -80f,
            sweepAngle = 160f,
            useCenter = true,
            topLeft = Offset(center.x - 8f, center.y - 8f),
            size = Size(16f, 16f)
        )

        // Forward directional pointer chevron
        val pointerPath = Path().apply {
            moveTo(center.x + 18f, center.y)
            lineTo(center.x + 28f, center.y - 7f)
            lineTo(center.x + 25f, center.y)
            lineTo(center.x + 28f, center.y + 7f)
            close()
        }
        drawPath(pointerPath, color = Color(0xFF38BDF8))
    }

    // Outer aura ring
    drawCircle(
        color = Color(0x4438BDF8),
        radius = 24f,
        center = center,
        style = Stroke(width = 2f)
    )
}

/**
 * Top-left Mini-Map Radar showing world orientation and player position.
 */
@Composable
private fun MiniMapRadar(
    playerX: Float,
    playerY: Float,
    headingDeg: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(90.dp)
            .clip(CircleShape)
            .background(Color(0xCC0F172A))
            .border(2.dp, Color(0xFF38BDF8), CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radarRadius = size.minDimension / 2f

            // Grid concentric range rings
            drawCircle(Color(0x3338BDF8), radius = radarRadius * 0.5f, style = Stroke(1f))
            drawCircle(Color(0x3338BDF8), radius = radarRadius * 0.85f, style = Stroke(1f))

            // Crosshairs
            drawLine(Color(0x3338BDF8), Offset(center.x, 0f), Offset(center.x, size.height), strokeWidth = 1f)
            drawLine(Color(0x3338BDF8), Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 1f)

            // Mission blip in world
            val blipWorldX = 1600f
            val blipWorldY = 1200f
            val relX = (blipWorldX - playerX) * 0.08f
            val relY = (blipWorldY - playerY) * 0.08f
            val blipOffset = Offset(
                (center.x + relX).coerceIn(4f, size.width - 4f),
                (center.y + relY).coerceIn(4f, size.height - 4f)
            )
            drawCircle(Color(0xFFFBBF24), radius = 3.5f, center = blipOffset)

            // Player arrow at center
            rotate(headingDeg, pivot = center) {
                val playerMarker = Path().apply {
                    moveTo(center.x + 6f, center.y)
                    lineTo(center.x - 5f, center.y - 4f)
                    lineTo(center.x - 3f, center.y)
                    lineTo(center.x - 5f, center.y + 4f)
                    close()
                }
                drawPath(playerMarker, color = Color.White)
            }
        }

        // Compass "N"
        Text(
            text = "N",
            color = Color(0xFF38BDF8),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Top-center HUD showing player health, armor, location coordinates, and velocity.
 */
@Composable
private fun PlayerStatusHUD(
    speedKmh: Float,
    playerX: Float,
    playerY: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC0F172A))
            .border(1.dp, Color(0x4438BDF8), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Health Bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HP", color = Color(0xFF4ADE80), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.92f)
                            .background(Color(0xFF22C55E))
                    )
                }
            }

            // Armor Bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ARM", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.75f)
                            .background(Color(0xFF0284C7))
                    )
                }
            }

            // Speedometer
            Text(
                text = "${speedKmh.toInt()} KM/H",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // World Coordinates
        Text(
            text = "LOS SANTOS • X:${playerX.toInt()} Y:${playerY.toInt()}",
            color = Color(0xFF94A3B8),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
