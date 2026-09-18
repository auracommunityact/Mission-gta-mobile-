package com.auracommunityact.missiongtamobile.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val totalRamMb: Long,
    val screenWidth: Int,
    val screenHeight: Int,
    val screenDensity: Float
)

object DeviceInfoProvider {
    fun getDeviceInfo(context: Context): DeviceInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val displayMetrics = context.resources.displayMetrics

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            totalRamMb = memoryInfo.totalMem / (1024 * 1024),
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels,
            screenDensity = displayMetrics.density
        )
    }
}
