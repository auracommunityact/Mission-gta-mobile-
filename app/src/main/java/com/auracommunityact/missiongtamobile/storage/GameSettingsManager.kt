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

    private val _dpadOpacity = MutableStateFlow(
        prefs.getFloat(KEY_DPAD_OPACITY, 0.85f)
    )
    val dpadOpacity: StateFlow<Float> = _dpadOpacity.asStateFlow()

    private val _dpadSizeDp = MutableStateFlow(
        prefs.getInt(KEY_DPAD_SIZE_DP, 156)
    )
    val dpadSizeDp: StateFlow<Int> = _dpadSizeDp.asStateFlow()

    private val _dpadHaptics = MutableStateFlow(
        prefs.getBoolean(KEY_DPAD_HAPTICS, true)
    )
    val dpadHaptics: StateFlow<Boolean> = _dpadHaptics.asStateFlow()

    /**
     * Updates D-Pad opacity (between 0.3f and 1.0f).
     */
    fun setDpadOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.3f, 1.0f)
        _dpadOpacity.value = clamped
        prefs.edit().putFloat(KEY_DPAD_OPACITY, clamped).apply()
    }

    /**
     * Updates D-Pad dimension in dp (between 120 and 200).
     */
    fun setDpadSizeDp(sizeDp: Int) {
        val clamped = sizeDp.coerceIn(120, 200)
        _dpadSizeDp.value = clamped
        prefs.edit().putInt(KEY_DPAD_SIZE_DP, clamped).apply()
    }

    /**
     * Toggles haptic feedback for D-Pad touch inputs.
     */
    fun setDpadHaptics(enabled: Boolean) {
        _dpadHaptics.value = enabled
        prefs.edit().putBoolean(KEY_DPAD_HAPTICS, enabled).apply()
    }

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
        const val KEY_DPAD_OPACITY = "key_dpad_opacity"
        const val KEY_DPAD_SIZE_DP = "key_dpad_size_dp"
        const val KEY_DPAD_HAPTICS = "key_dpad_haptics"

        @Volatile
        private var instance: GameSettingsManager? = null

        fun getInstance(context: Context): GameSettingsManager {
            return instance ?: synchronized(this) {
                instance ?: GameSettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
