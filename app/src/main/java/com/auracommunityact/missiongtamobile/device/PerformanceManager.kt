package com.auracommunityact.missiongtamobile.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.auracommunityact.missiongtamobile.runtime.state.GameStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RealtimePerformanceData(
    val fps: Float?, // null when not running (displays as N/A)
    val frameTimeMs: Float?, // null when not running
    val totalRamMb: Long,
    val availableRamMb: Long,
    val ramUsagePercent: Int,
    val thermalStatus: String,
    val thermalHeadroom: Float?,
    val isGameRunning: Boolean
)

enum class GraphicsPreset {
    LOW,
    MEDIUM,
    HIGH,
    CUSTOM
}

data class GraphicsSettings(
    val preset: GraphicsPreset = GraphicsPreset.MEDIUM,
    val resolutionScale: Float = 1.0f,
    val textureQuality: String = "Medium",
    val shadowQuality: String = "Low",
    val effectsQuality: String = "Medium",
    val frameLimit: Int = 60,
    val vSync: Boolean = true
)

class PerformanceManager(private val context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val _performanceData = MutableStateFlow(
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
    )
    val performanceData: StateFlow<RealtimePerformanceData> = _performanceData.asStateFlow()

    private val _graphicsSettings = MutableStateFlow(GraphicsSettings())
    val graphicsSettings: StateFlow<GraphicsSettings> = _graphicsSettings.asStateFlow()

    private val frameTimestamps = mutableListOf<Long>()
    private var lastFrameTimestampNs: Long = 0L
    private var isRunning = false
    private var monitoringScope: CoroutineScope? = null

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        monitoringScope?.launch { return@launch }
        val scope = CoroutineScope(Dispatchers.Default)
        monitoringScope = scope

        scope.launch {
            while (isActive) {
                updateSystemMetrics()
                delay(1000)
            }
        }
    }

    private fun updateSystemMetrics() {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = totalMb - availMb
        val usagePercent = if (totalMb > 0) ((usedMb * 100) / totalMb).toInt() else 0

        var thermal = "N/A"
        var headroom: Float? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            thermal = when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "NORMAL"
                PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                else -> "N/A"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val hr = powerManager.getThermalHeadroom(0)
                    if (!hr.isNaN()) headroom = hr
                } catch (_: Exception) {}
            }
        }

        val (calculatedFps, calculatedFrameTime) = calculateFps()

        _performanceData.value = RealtimePerformanceData(
            fps = if (isRunning) calculatedFps else null,
            frameTimeMs = if (isRunning) calculatedFrameTime else null,
            totalRamMb = totalMb,
            availableRamMb = availMb,
            ramUsagePercent = usagePercent,
            thermalStatus = thermal,
            thermalHeadroom = headroom,
            isGameRunning = isRunning
        )
    }

    fun setGameRunning(running: Boolean) {
        isRunning = running
        if (!running) {
            synchronized(frameTimestamps) {
                frameTimestamps.clear()
            }
            lastFrameTimestampNs = 0L
        }
        updateSystemMetrics()
    }

    fun onFrameRendered() {
        val now = System.nanoTime()
        synchronized(frameTimestamps) {
            frameTimestamps.add(now)
            val cutoff = now - 1_000_000_000L // 1 second window
            while (frameTimestamps.isNotEmpty() && frameTimestamps.first() < cutoff) {
                frameTimestamps.removeAt(0)
            }
        }
        lastFrameTimestampNs = now
    }

    private fun calculateFps(): Pair<Float?, Float?> {
        synchronized(frameTimestamps) {
            if (frameTimestamps.size < 2) {
                return Pair(null, null)
            }
            val count = frameTimestamps.size
            val durationNs = frameTimestamps.last() - frameTimestamps.first()
            if (durationNs <= 0) return Pair(null, null)

            val fps = (count.toFloat() * 1_000_000_000f) / durationNs.toFloat()
            val frameTimeMs = 1000f / fps
            return Pair(fps, frameTimeMs)
        }
    }

    fun applyPreset(preset: GraphicsPreset) {
        val newSettings = when (preset) {
            GraphicsPreset.LOW -> GraphicsSettings(
                preset = GraphicsPreset.LOW,
                resolutionScale = 0.75f,
                textureQuality = "Low",
                shadowQuality = "Off",
                effectsQuality = "Low",
                frameLimit = 30,
                vSync = true
            )
            GraphicsPreset.MEDIUM -> GraphicsSettings(
                preset = GraphicsPreset.MEDIUM,
                resolutionScale = 1.0f,
                textureQuality = "Medium",
                shadowQuality = "Low",
                effectsQuality = "Medium",
                frameLimit = 60,
                vSync = true
            )
            GraphicsPreset.HIGH -> GraphicsSettings(
                preset = GraphicsPreset.HIGH,
                resolutionScale = 1.0f,
                textureQuality = "High",
                shadowQuality = "High",
                effectsQuality = "High",
                frameLimit = 60,
                vSync = false
            )
            GraphicsPreset.CUSTOM -> _graphicsSettings.value.copy(preset = GraphicsPreset.CUSTOM)
        }
        _graphicsSettings.value = newSettings
    }

    fun updateSettings(settings: GraphicsSettings) {
        _graphicsSettings.value = settings
    }
}
