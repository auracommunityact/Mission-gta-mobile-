package com.auracommunityact.missiongtamobile.storage

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameSettingsManagerTest {

    private lateinit var context: Context
    private lateinit var settingsManager: GameSettingsManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs before each test
        context.getSharedPreferences(GameSettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        settingsManager = GameSettingsManager(context)
    }

    @Test
    fun testDefaultSettings_forceLandscapeIsEnabledByDefault() {
        assertTrue(
            "Force landscape should be enabled by default for gameplay consistency",
            settingsManager.forceLandscape.value
        )
        assertEquals(
            LandscapeOrientationMode.SENSOR_LANDSCAPE,
            settingsManager.landscapeMode.value
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            settingsManager.getEffectiveGameOrientation()
        )
    }

    @Test
    fun testDisableForceLandscape_returnsUnspecifiedOrientation() {
        settingsManager.setForceLandscape(false)

        assertFalse(settingsManager.forceLandscape.value)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            settingsManager.getEffectiveGameOrientation()
        )
    }

    @Test
    fun testLandscapeModes_provideCorrectActivityOrientations() {
        settingsManager.setForceLandscape(true)

        settingsManager.setLandscapeMode(LandscapeOrientationMode.FIXED_LANDSCAPE)
        assertEquals(LandscapeOrientationMode.FIXED_LANDSCAPE, settingsManager.landscapeMode.value)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            settingsManager.getEffectiveGameOrientation()
        )

        settingsManager.setLandscapeMode(LandscapeOrientationMode.REVERSE_LANDSCAPE)
        assertEquals(LandscapeOrientationMode.REVERSE_LANDSCAPE, settingsManager.landscapeMode.value)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            settingsManager.getEffectiveGameOrientation()
        )

        settingsManager.setLandscapeMode(LandscapeOrientationMode.SENSOR_LANDSCAPE)
        assertEquals(LandscapeOrientationMode.SENSOR_LANDSCAPE, settingsManager.landscapeMode.value)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            settingsManager.getEffectiveGameOrientation()
        )
    }

    @Test
    fun testSettingsPersistenceAcrossInstances() {
        settingsManager.setForceLandscape(false)
        settingsManager.setLandscapeMode(LandscapeOrientationMode.FIXED_LANDSCAPE)

        val newInstance = GameSettingsManager(context)
        assertFalse(newInstance.forceLandscape.value)
        assertEquals(LandscapeOrientationMode.FIXED_LANDSCAPE, newInstance.landscapeMode.value)
    }
}
