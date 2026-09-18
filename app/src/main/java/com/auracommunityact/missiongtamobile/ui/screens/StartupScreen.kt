package com.auracommunityact.missiongtamobile.ui.screens

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.auracommunityact.missiongtamobile.R
import com.auracommunityact.missiongtamobile.device.DeviceCapabilityProvider
import com.auracommunityact.missiongtamobile.device.DeviceInfoProvider
import com.auracommunityact.missiongtamobile.device.DeviceProfileSelector
import com.auracommunityact.missiongtamobile.device.GraphicsCapabilityProvider
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeProvider
import com.auracommunityact.missiongtamobile.storage.GameResourceManager
import com.auracommunityact.missiongtamobile.storage.ResourceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

private data class AmbientParticle(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val alpha: Float,
    val phase: Float
)

private val STATIC_PARTICLES = listOf(
    AmbientParticle(0.12f, 0.25f, 2.2f, 0.04f, 0.35f, 0.4f),
    AmbientParticle(0.28f, 0.65f, 3.2f, 0.03f, 0.25f, 1.2f),
    AmbientParticle(0.42f, 0.15f, 1.8f, 0.05f, 0.40f, 2.1f),
    AmbientParticle(0.55f, 0.85f, 2.8f, 0.035f, 0.30f, 0.8f),
    AmbientParticle(0.68f, 0.40f, 2.0f, 0.045f, 0.20f, 3.0f),
    AmbientParticle(0.82f, 0.70f, 3.0f, 0.03f, 0.35f, 1.8f),
    AmbientParticle(0.91f, 0.30f, 1.5f, 0.05f, 0.25f, 2.5f),
    AmbientParticle(0.18f, 0.80f, 2.5f, 0.04f, 0.20f, 0.1f),
    AmbientParticle(0.35f, 0.45f, 3.0f, 0.035f, 0.30f, 1.5f),
    AmbientParticle(0.48f, 0.90f, 1.6f, 0.045f, 0.25f, 2.8f),
    AmbientParticle(0.62f, 0.20f, 2.4f, 0.03f, 0.35f, 0.6f),
    AmbientParticle(0.75f, 0.55f, 3.4f, 0.025f, 0.20f, 3.4f),
    AmbientParticle(0.88f, 0.10f, 1.8f, 0.05f, 0.30f, 1.1f),
    AmbientParticle(0.22f, 0.35f, 2.6f, 0.04f, 0.25f, 2.0f),
    AmbientParticle(0.60f, 0.65f, 2.2f, 0.035f, 0.30f, 0.9f),
    AmbientParticle(0.79f, 0.92f, 2.8f, 0.03f, 0.25f, 2.3f)
)

@Composable
fun StartupScreen(
    runtimeProvider: GameRuntimeProvider,
    resourceManager: GameResourceManager,
    onStartupComplete: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Ensure edge-to-edge immersive full screen while startup screen is active
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose { }
    }

    // Sequence phases:
    // 1: Pure dark background with subtle illumination start
    // 2: Aura Community Act logo fades & scales in
    // 3: "MISSION GTA MOBILE" title & subtitle fade in
    // 4: Loading indicator appears & real initialization progresses
    // 5: Complete, smooth exit
    var currentPhase by remember { mutableIntStateOf(1) }
    var targetProgress by remember { mutableFloatStateOf(0f) }
    var loadingStatusText by remember { mutableStateOf("INITIALIZING...") }
    var isFailed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isExiting by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Quick initial memory check via DeviceInfoProvider
    val initialDeviceInfo = remember {
        try {
            DeviceInfoProvider.getDeviceInfo(context)
        } catch (e: Exception) {
            null
        }
    }
    var isLowMemoryMode by remember {
        mutableStateOf(initialDeviceInfo?.isLowMemory ?: false)
    }
    var detectedRamMb by remember {
        mutableLongStateOf(initialDeviceInfo?.totalRamMb ?: 0L)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cinematicAmbient")

    // Ambient lighting breathing motion
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val glowOffsetAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowAngle"
    )

    // Slow ambient camera breathing for cinematic parallax
    val ambientZoom by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientZoom"
    )

    // Slow drifting particles
    val particleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )

    // Smooth transition states
    val screenAlpha by animateFloatAsState(
        targetValue = if (!isExiting) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "screenAlpha"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (currentPhase >= 2 && !isExiting) 1f else 0f,
        animationSpec = tween(700, easing = EaseOutCubic),
        label = "logoAlpha"
    )

    val logoScale by animateFloatAsState(
        targetValue = if (currentPhase >= 2) 1.0f else 0.86f,
        animationSpec = tween(900, easing = EaseOutCubic),
        label = "logoScale"
    )

    val titleAlpha by animateFloatAsState(
        targetValue = if (currentPhase >= 3 && !isExiting) 1f else 0f,
        animationSpec = tween(600, easing = EaseOutCubic),
        label = "titleAlpha"
    )

    val subtitleAlpha by animateFloatAsState(
        targetValue = if (currentPhase >= 3 && !isExiting) 1f else 0f,
        animationSpec = tween(650, delayMillis = 150, easing = EaseOutCubic),
        label = "subtitleAlpha"
    )

    val loadingAlpha by animateFloatAsState(
        targetValue = if (currentPhase >= 4 && !isExiting) 1f else 0f,
        animationSpec = tween(500, easing = EaseOutCubic),
        label = "loadingAlpha"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "animatedProgress"
    )

    // Real system initialization sequence connected to progress
    LaunchedEffect(retryTrigger) {
        isFailed = false
        errorMessage = ""
        isExiting = false
        currentPhase = 1
        targetProgress = 0.05f

        // Phase 1: Subtle illumination begins
        delay(200)

        // Phase 2: Logo fades & scales in
        currentPhase = 2
        delay(400)

        // Phase 3: Title & Subtitle fade in
        currentPhase = 3
        delay(350)

        // Phase 4: Loading bar appears and initialization starts
        currentPhase = 4

        try {
            // Quick check via DeviceInfoProvider at the start
            loadingStatusText = "CHECKING SYSTEM HARDWARE..."
            val devInfo = withContext(Dispatchers.Default) {
                DeviceInfoProvider.getDeviceInfo(context)
            }
            detectedRamMb = devInfo.totalRamMb
            isLowMemoryMode = devInfo.isLowMemory
            delay(100)

            // Stage 1: Hardware Architecture Detection
            loadingStatusText = "PROBING HARDWARE ARCHITECTURE..."
            targetProgress = 0.22f
            val device = withContext(Dispatchers.Default) {
                DeviceCapabilityProvider.getCapabilities(context)
            }
            delay(150)

            // Stage 2: Graphics Stack & Vulkan
            loadingStatusText = "QUERYING GRAPHICS STACK & VULKAN..."
            targetProgress = 0.46f
            val graphics = withContext(Dispatchers.Default) {
                GraphicsCapabilityProvider.getCapabilities(context)
            }
            delay(150)

            // Stage 3: Compatibility Profile Evaluation
            loadingStatusText = "EVALUATING COMPATIBILITY PROFILE..."
            targetProgress = 0.68f
            withContext(Dispatchers.Default) {
                DeviceProfileSelector.select(device, graphics)
            }
            delay(150)

            // Stage 4: Storage & Resource Verification
            loadingStatusText = "VERIFYING GAME STORAGE & ASSETS..."
            targetProgress = 0.84f
            // If resource manager is currently in scanning state, allow it to complete
            var scanWaitCycles = 0
            while (resourceManager.status.value == ResourceStatus.SCANNING && scanWaitCycles < 20) {
                delay(100)
                scanWaitCycles++
            }
            delay(100)

            // Stage 5: Runtime Subsystem Check
            loadingStatusText = "PREPARING RUNTIME SUBSYSTEM..."
            targetProgress = 0.95f
            val runtime = runtimeProvider.getRuntime()
            runtime?.initialize()
            delay(150)

            // Stage 6: Ready
            loadingStatusText = "SYSTEM READY"
            targetProgress = 1.0f

            // Wait for smooth progress animation to complete visually
            while (animatedProgress < 0.98f) {
                delay(30)
            }
            delay(250)

            // Phase 5: Smooth exit transition
            isExiting = true
            delay(380)
            onStartupComplete()

        } catch (e: Exception) {
            isFailed = true
            errorMessage = e.localizedMessage ?: "Unexpected initialization failure"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha)
            .background(Color(0xFF030712)),
        contentAlignment = Alignment.Center
    ) {
        // Subtle animated cinematic navy background gradient & volumetric lighting
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width * 0.5f + (cos(glowOffsetAngle) * width * 0.06f)
            val centerY = height * 0.44f + (sin(glowOffsetAngle) * height * 0.04f)
            val radius = size.maxDimension * 0.62f * glowPulse

            // Base deep dark canvas
            drawRect(color = Color(0xFF030712))

            // Cinematic Navy Blue glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x381E4E8C), // Navy center glow
                        Color(0x180E2954), // Mid-space navy
                        Color(0x00030712)  // Transparent edge
                    ),
                    center = Offset(centerX, centerY),
                    radius = radius
                ),
                center = Offset(centerX, centerY),
                radius = radius
            )
        }

        // Lightweight ambient particle motes
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val time = particleTime
            val particleAlphaMultiplier = if (currentPhase >= 2) 1.0f else 0.2f

            for (p in STATIC_PARTICLES) {
                val yNorm = (p.y - (time * p.speed * 4f)) % 1f
                val currentY = (if (yNorm < 0f) yNorm + 1f else yNorm) * canvasHeight
                val currentX = (p.x + sin((time * 6.2831855f) + p.phase) * 0.02f).coerceIn(0f, 1f) * canvasWidth

                drawCircle(
                    color = Color(0xFF64B5F6).copy(
                        alpha = p.alpha * screenAlpha * particleAlphaMultiplier
                    ),
                    radius = p.size * density,
                    center = Offset(currentX, currentY)
                )
            }
        }

        // Main Center Branding & Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .scale(logoScale * ambientZoom)
        ) {
            // Aura Community Act Logo with smooth fade & scale
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(108.dp)
                    .alpha(logoAlpha)
            ) {
                // Subtle glowing halo behind the logo
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x553B82F6),
                                Color(0x181E40AF),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.minDimension / 2f
                        )
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.aura_community_act_logo),
                    contentDescription = "Aura Community Act Logo",
                    modifier = Modifier.size(86.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary title: MISSION GTA MOBILE
            Text(
                text = stringResource(id = R.string.startup_title),
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(titleAlpha)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Secondary subtitle: An Aura Community Act Project
            Text(
                text = stringResource(id = R.string.startup_subtitle),
                color = Color(0xFF93C5FD),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(subtitleAlpha)
            )
        }

        // Bottom Loading & Informational Area
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .fillMaxWidth()
        ) {
            if (!isFailed) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .widthIn(max = 440.dp)
                        .alpha(loadingAlpha)
                ) {
                    // Discrete Low Memory Mode warning if device RAM is below 4GB
                    if (isLowMemoryMode) {
                        Row(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x22F59E0B))
                                .border(0.7.dp, Color(0x66F59E0B), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Canvas(modifier = Modifier.size(6.dp)) {
                                drawCircle(Color(0xFFFBBF24))
                            }
                            Text(
                                text = "Low Memory Mode",
                                color = Color(0xFFFDE68A),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                            if (detectedRamMb > 0) {
                                Text(
                                    text = "• ${detectedRamMb}MB RAM",
                                    color = Color(0xFFF59E0B),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Status text and percentage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = loadingStatusText,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        val percent = (animatedProgress * 100).toInt().coerceIn(0, 100)
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Sleek console-style loading progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF0F1B33))
                            .border(0.5.dp, Color(0xFF1E3A6E), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF1D4ED8),
                                            Color(0xFF3B82F6),
                                            Color(0xFF93C5FD)
                                        )
                                    )
                                )
                        )
                    }
                }
            } else {
                // Clean error state preventing user from getting stuck
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "STARTUP WARNING: $errorMessage",
                        color = Color(0xFFF87171),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                                .clickable { retryTrigger++ }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "RETRY",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E3A8A))
                                .clickable { onStartupComplete() }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "CONTINUE TO MENU",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Project Description
            Text(
                text = stringResource(id = R.string.startup_footer),
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(loadingAlpha)
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Disclaimer text
            Text(
                text = stringResource(id = R.string.disclaimer_text),
                color = Color(0xFF475569),
                fontSize = 9.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .alpha(loadingAlpha)
            )
        }
    }
}
