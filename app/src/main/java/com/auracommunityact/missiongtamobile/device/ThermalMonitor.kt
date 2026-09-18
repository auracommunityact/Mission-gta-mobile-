package com.auracommunityact.missiongtamobile.device

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

enum class ThermalStatus {
    NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN, UNAVAILABLE
}

data class ThermalState(
    val status: ThermalStatus,
    val headroom: Float?
)

class ThermalMonitor(private val context: Context) {
    fun monitor(): Flow<ThermalState> = flow {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            while (true) {
                if (powerManager != null) {
                    val statusInt = powerManager.currentThermalStatus
                    val status = when (statusInt) {
                        PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
                        PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
                        PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
                        PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
                        PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
                        PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
                        PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
                        else -> ThermalStatus.UNAVAILABLE
                    }
                    var headroom: Float? = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val hr = powerManager.getThermalHeadroom(0)
                            if (!hr.isNaN()) {
                                headroom = hr
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                    emit(ThermalState(status, headroom))
                } else {
                    emit(ThermalState(ThermalStatus.UNAVAILABLE, null))
                }
                delay(5000)
            }
        } else {
            emit(ThermalState(ThermalStatus.UNAVAILABLE, null))
        }
    }
}
