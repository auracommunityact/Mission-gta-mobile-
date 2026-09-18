package com.example.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class SystemDiagnostics(
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String,
    val cpuAbi: String,
    val availableRamMb: Long,
    val totalRamMb: Long,
    val screenResolution: String,
    val screenDensity: Float
)

object DiagnosticsCollector {
    fun collect(context: Context): SystemDiagnostics {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "Unknown"

        return SystemDiagnostics(
            appVersion = versionName,
            androidVersion = Build.VERSION.RELEASE,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            cpuAbi = Build.SUPPORTED_ABIS.joinToString(", "),
            availableRamMb = memoryInfo.availMem / (1024 * 1024),
            totalRamMb = memoryInfo.totalMem / (1024 * 1024),
            screenResolution = "${width}x${height}",
            screenDensity = displayMetrics.density
        )
    }
}
