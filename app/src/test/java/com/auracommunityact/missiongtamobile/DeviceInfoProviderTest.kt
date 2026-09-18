package com.auracommunityact.missiongtamobile

import com.auracommunityact.missiongtamobile.device.DeviceInfo
import com.auracommunityact.missiongtamobile.device.DeviceInfoProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInfoProviderTest {

    @Test
    fun testLowMemoryThreshold_below4GB() {
        val lowRamInfo = DeviceInfo(
            manufacturer = "Test",
            model = "LowRamPhone",
            totalRamMb = 3072L, // 3GB RAM
            screenWidth = 1080,
            screenHeight = 1920,
            screenDensity = 2.0f
        )
        assertTrue("3GB RAM should trigger low memory mode", lowRamInfo.isLowMemory)
    }

    @Test
    fun testLowMemoryThreshold_atOrAbove4GB() {
        val standardRamInfo = DeviceInfo(
            manufacturer = "Test",
            model = "StandardRamPhone",
            totalRamMb = 4096L, // 4GB RAM
            screenWidth = 1080,
            screenHeight = 2400,
            screenDensity = 2.5f
        )
        assertFalse("4GB RAM should not trigger low memory mode", standardRamInfo.isLowMemory)

        val highRamInfo = DeviceInfo(
            manufacturer = "Test",
            model = "HighRamPhone",
            totalRamMb = 8192L, // 8GB RAM
            screenWidth = 1440,
            screenHeight = 3200,
            screenDensity = 3.0f
        )
        assertFalse("8GB RAM should not trigger low memory mode", highRamInfo.isLowMemory)
    }

    @Test
    fun testLowMemoryThresholdConstant() {
        org.junit.Assert.assertEquals(4096L, DeviceInfoProvider.LOW_MEMORY_THRESHOLD_MB)
    }
}
