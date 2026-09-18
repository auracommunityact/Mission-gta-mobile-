package com.auracommunityact.missiongtamobile.device

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class PerformanceMetrics(
    val fps: Float?,
    val frameTimeMs: Float?,
    val availableRamMb: Long,
    val isRunning: Boolean
)

class PerformanceMonitor(private val context: Context) {
    fun monitor(): Flow<PerformanceMetrics> = flow {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        while (true) {
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            
            // FPS and frameTime are currently unavailable since native game runtime is not initialized
            emit(PerformanceMetrics(
                fps = null,
                frameTimeMs = null,
                availableRamMb = mi.availMem / (1024 * 1024),
                isRunning = false
            ))
            delay(1000)
        }
    }
}
