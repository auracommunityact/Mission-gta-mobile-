package com.auracommunityact.missiongtamobile.storage

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Supported landscape orientation modes for the gameplay environment.
 */
enum class LandscapeOrientationMode(
    val id: String,
    val displayName: String,
    val description: String,
    val activityInfoOrientation: Int
) {
    SENSOR_LANDSCAPE(
        id = "sensor_landscape",
        displayName = "Sensor Landscape",
        description = "Allows standard and reverse landscape (rotates 180° when flipping device)",
        activityInfoOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    ),
    FIXED_LANDSCAPE(
        id = "fixed_landscape",
        displayName = "Fixed Landscape",
        description = "Locks strictly to standard landscape orientation",
        activityInfoOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    ),
    REVERSE_LANDSCAPE(
        id = "reverse_landscape",
        displayName = "Reverse Landscape",
        description = "Inverted landscape (convenient for right-side charging/headphone cables)",
        activityInfoOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    );

    companion object {
        fun fromId(id: String?): LandscapeOrientationMode {
            return entries.firstOrNull { it.id == id } ?: SENSOR_LANDSCAPE
        }
    }
}

/**
 * Manages game settings including gameplay orientation locking and preferences.
 */
class GameSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _forceLandscape = MutableStateFlow(
        prefs.getBoolean(KEY_FORCE_LANDSCAPE, true)
    )
    val forceLandscape: StateFlow<Boolean> = _forceLandscape.asStateFlow()

    private val _landscapeMode = MutableStateFlow(
        LandscapeOrientationMode.fromId(prefs.getString(KEY_LANDSCAPE_MODE, LandscapeOrientationMode.SENSOR_LANDSCAPE.id))
    )
    val landscapeMode: StateFlow<LandscapeOrientationMode> = _landscapeMode.asStateFlow()

    /**
     * Enables or disables forcing landscape orientation in the game environment.
     */
    fun setForceLandscape(enabled: Boolean) {
        _forceLandscape.value = enabled
        prefs.edit().putBoolean(KEY_FORCE_LANDSCAPE, enabled).apply()
    }

    /**
     * Sets the specific landscape mode (Sensor Landscape, Fixed, or Reverse).
     */
    fun setLandscapeMode(mode: LandscapeOrientationMode) {
        _landscapeMode.value = mode
        prefs.edit().putString(KEY_LANDSCAPE_MODE, mode.id).apply()
    }

    /**
     * Computes the effective ActivityInfo screen orientation based on current settings.
     */
    fun getEffectiveGameOrientation(): Int {
        return if (_forceLandscape.value) {
            _landscapeMode.value.activityInfoOrientation
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    /**
     * Applies the configured orientation to the provided activity if active.
     */
    fun applyGameOrientation(activity: Activity?) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        activity.requestedOrientation = getEffectiveGameOrientation()
    }

    /**
     * Restores default app orientation when leaving gameplay.
     */
    fun restoreDefaultOrientation(activity: Activity?) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    companion object {
        const val PREFS_NAME = "mission_gta_game_settings"
        const val KEY_FORCE_LANDSCAPE = "key_force_landscape"
        const val KEY_LANDSCAPE_MODE = "key_landscape_mode"

        @Volatile
        private var instance: GameSettingsManager? = null

        fun getInstance(context: Context): GameSettingsManager {
            return instance ?: synchronized(this) {
                instance ?: GameSettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
