package com.auracommunityact.missiongtamobile.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class DeviceCapabilities(
    val androidVersion: String,
    val apiLevel: Int,
    val manufacturer: String,
    val model: String,
    val cpuAbi: String,
    val supportedAbis: List<String>,
    val cpuCoreCount: Int,
    val totalRamMb: Long,
    val availableRamMb: Long
)

object DeviceCapabilityProvider {
    fun getCapabilities(context: Context): DeviceCapabilities {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        return DeviceCapabilities(
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown",
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            totalRamMb = memoryInfo.totalMem / (1024 * 1024),
            availableRamMb = memoryInfo.availMem / (1024 * 1024)
        )
    }
}
