package com.auracommunityact.missiongtamobile.device

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.auracommunityact.missiongtamobile.runtime.state.DriverStatus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class DriverManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clean up test directories
        val driversDir = File(context.filesDir, "drivers")
        if (driversDir.exists()) {
            driversDir.deleteRecursively()
        }
    }

    @After
    fun tearDown() {
        DeviceInfoManager.setHardwareReportForTesting(null)
    }

    private fun createMockHardwareReport(
        gpuFamily: GpuFamily,
        gpuRenderer: String,
        vulkanSupported: Boolean = true,
        isArm64: Boolean = true
    ): DeviceHardwareReport {
        return DeviceHardwareReport(
            androidVersion = "14",
            apiLevel = 34,
            primaryAbi = if (isArm64) "arm64-v8a" else "armeabi-v7a",
            supportedAbis = if (isArm64) listOf("arm64-v8a", "armeabi-v7a") else listOf("armeabi-v7a"),
            isArm64Supported = isArm64,
            socManufacturer = if (gpuFamily == GpuFamily.ADRENO) "Qualcomm" else "MediaTek",
            socModel = if (gpuFamily == GpuFamily.ADRENO) "Snapdragon 8 Gen 2" else "Dimensity 9000",
            deviceManufacturer = "TestManufacturer",
            deviceModel = "TestPhone",
            cpuCoreCount = 8,
            totalRamMb = 12288L,
            availableRamMb = 8192L,
            isLowRamDevice = false,
            gpuVendor = if (gpuFamily == GpuFamily.ADRENO) "Qualcomm" else "ARM",
            gpuRenderer = gpuRenderer,
            gpuFamily = gpuFamily,
            openGlVersion = "OpenGL ES 3.2",
            maxTextureSize = 16384,
            vulkanSupported = vulkanSupported,
            vulkanVersion = if (vulkanSupported) "1.3.260" else null,
            vulkanLevel = if (vulkanSupported) 1 else null,
            supportedTextureFormats = listOf("ASTC", "ETC1", "BC/DXT")
        )
    }

    @Test
    fun testAdrenoDevice_detectsAndSelectsTurnipDriver() {
        val hw = createMockHardwareReport(GpuFamily.ADRENO, "Adreno (TM) 740", vulkanSupported = true, isArm64 = true)
        DeviceInfoManager.setHardwareReportForTesting(hw)

        val driverManager = DriverManager(context)
        val selected = driverManager.selectedVulkanDriver.value

        assertNotNull("Selected driver should not be null", selected)
        assertTrue("Expected TurnipDriverPackage on Qualcomm Adreno", selected is TurnipDriverPackage)
        assertEquals(DriverType.TURNIP_ADRENO, selected?.type)
        assertEquals(GpuFamily.ADRENO, selected?.targetGpuFamily)
        assertEquals(DriverStatus.READY, driverManager.status.value)
    }

    @Test
    fun testMaliDevice_detectsAndSelectsSystemVulkanDriver() {
        val hw = createMockHardwareReport(GpuFamily.MALI, "Mali-G710 MC10", vulkanSupported = true, isArm64 = true)
        DeviceInfoManager.setHardwareReportForTesting(hw)

        val driverManager = DriverManager(context)
        val selected = driverManager.selectedVulkanDriver.value

        assertNotNull("Selected driver should not be null", selected)
        assertTrue("Expected SystemVulkanDriver on Mali GPU", selected is SystemVulkanDriver)
        assertEquals(DriverType.SYSTEM_VULKAN, selected?.type)
        assertEquals(DriverStatus.READY, driverManager.status.value)
    }

    @Test
    fun testUnsupportedVulkan_setsStatusToUnsupported() {
        val hw = createMockHardwareReport(GpuFamily.ADRENO, "Adreno (TM) 506", vulkanSupported = false, isArm64 = true)
        DeviceInfoManager.setHardwareReportForTesting(hw)

        val driverManager = DriverManager(context)
        val selected = driverManager.detectOptimalVulkanDriver()

        assertEquals(DriverStatus.UNSUPPORTED, driverManager.status.value)
        val validation = driverManager.validateDriver(selected)
        assertFalse(validation.isValid)
        assertEquals(DriverStatus.UNSUPPORTED, validation.status)
        assertTrue(validation.reason.contains("Vulkan", ignoreCase = true))
    }

    @Test
    fun testTurnipValidationOnMaliGpu_failsWithHelpfulReason() {
        val hw = createMockHardwareReport(GpuFamily.MALI, "Mali-G78 MP14", vulkanSupported = true, isArm64 = true)
        DeviceInfoManager.setHardwareReportForTesting(hw)

        val driverManager = DriverManager(context)
        val turnip = driverManager.getTurnipPackages().first()

        val result = driverManager.validateDriver(turnip)
        assertFalse("Turnip must not validate on Mali GPU", result.isValid)
        assertEquals(DriverStatus.UNSUPPORTED, result.status)
        assertTrue("Reason should mention Adreno requirement: ${result.reason}", result.reason.contains("Adreno"))
    }

    @Test
    fun testTurnipValidationOn32Bit_failsWithArchitectureReason() {
        val hw = createMockHardwareReport(GpuFamily.ADRENO, "Adreno (TM) 618", vulkanSupported = true, isArm64 = false)
        DeviceInfoManager.setHardwareReportForTesting(hw)

        val driverManager = DriverManager(context)
        val turnip = driverManager.getTurnipPackages().first()

        val result = driverManager.validateDriver(turnip)
        assertFalse("Turnip must not validate on 32-bit arch", result.isValid)
        assertEquals(DriverStatus.UNSUPPORTED, result.status)
        assertTrue("Reason should mention 64-bit requirement", result.reason.contains("64-bit") || result.reason.contains("arm64"))
    }

    @Test
    fun testLoadTurnipDriver_configuresEnvironmentVariables() {
        val hw = createMockHardwareReport(GpuFamily.ADRENO, "Adreno (TM) 730", vulkanSupported = true, isArm64 = true)
        DeviceInfoManager.setHardwareReportForTesting(hw)

        val driverManager = DriverManager(context)
        val turnip = driverManager.getTurnipPackages().first()

        val loadResult = driverManager.loadDriver(turnip)
        assertTrue("Loading compatible Turnip driver should succeed", loadResult.isSuccess)

        val loaded = loadResult.getOrThrow()
        assertNotNull("LoadedDriver should not be null", loaded)
        assertNotNull("Turnip ICD path should be defined", loaded.icdPath)
        assertTrue("Turnip ICD file must exist on storage", File(loaded.icdPath!!).exists())
        assertEquals(loaded.icdPath, loaded.environmentVariables["VK_ICD_FILENAMES"])
        assertEquals("noconform", loaded.environmentVariables["TU_DEBUG"])
        assertEquals("mailbox", loaded.environmentVariables["MESA_VK_WSI_PRESENT_MODE"])

        assertEquals(DriverStatus.READY, driverManager.status.value)
        assertEquals(loaded, driverManager.loadedDriver.value)

        // Test unload
        driverManager.unloadDriver()
        assertNull(driverManager.loadedDriver.value)
        assertEquals(DriverStatus.DETECTING, driverManager.status.value)
    }

    @Test
    fun testLoadSystemDriver_hasEmptyEnvironmentVariables() {
        val hw = createMockHardwareReport(GpuFamily.MALI, "Mali-G77", vulkanSupported = true, isArm64 = true)
        DeviceInfoManager.setHardwareReportForTesting(hw)

        val driverManager = DriverManager(context)
        val systemDriver = driverManager.getSystemDriver()

        val loadResult = driverManager.loadDriver(systemDriver)
        assertTrue(loadResult.isSuccess)

        val loaded = loadResult.getOrThrow()
        assertNull("System driver does not redirect ICD path", loaded.icdPath)
        assertTrue("System driver should not require custom environment overrides", loaded.environmentVariables.isEmpty())
    }

    @Test
    fun testDriverPackageManager_installAndUninstallCustomPackage() {
        val hw = createMockHardwareReport(GpuFamily.ADRENO, "Adreno (TM) 740", vulkanSupported = true, isArm64 = true)
        DeviceInfoManager.setHardwareReportForTesting(hw)

        val driverManager = DriverManager(context)
        val packageManager = driverManager.packageManager

        // Create a mock ZIP archive in memory
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("libvulkan_custom.so"))
            zos.write("fake-so-binary-content".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("meta.txt"))
            zos.write("Test Turnip Custom\nCommunity\n24.3.0\nCustom experimental build".toByteArray())
            zos.closeEntry()
        }

        val zipBytes = baos.toByteArray()
        val installResult = packageManager.installPackageFromStream(
            inputStream = ByteArrayInputStream(zipBytes),
            packageFolderName = "turnip_custom_test",
            displayName = "Test Turnip Custom"
        )

        assertTrue("Package installation from stream should succeed", installResult.isSuccess)
        val installedPkg = installResult.getOrThrow()
        assertEquals("Test Turnip Custom", installedPkg.name)
        assertEquals("Community", installedPkg.vendor)
        assertEquals("custom_turnip_custom_test", installedPkg.id)
        assertTrue("Installed package directory must exist", installedPkg.packageDir.exists())

        // Verify it appears in packageManager and driverManager
        driverManager.scanDrivers()
        val customPackages = driverManager.getCustomPackages()
        assertTrue("Custom package should be discovered", customPackages.any { it.id == installedPkg.id })

        // Uninstall
        val uninstallResult = driverManager.uninstallCustomPackage(installedPkg.id)
        assertTrue("Uninstalling custom package should succeed", uninstallResult)
        assertFalse("Uninstalled custom package folder should no longer exist", installedPkg.packageDir.exists())
    }

    @Test
    fun testUninstallBuiltinTurnipOrSystem_isRejected() {
        val hw = createMockHardwareReport(GpuFamily.ADRENO, "Adreno (TM) 740", vulkanSupported = true, isArm64 = true)
        DeviceInfoManager.setHardwareReportForTesting(hw)

        val driverManager = DriverManager(context)
        assertFalse("System driver cannot be uninstalled", driverManager.uninstallCustomPackage("driver_system_vulkan"))
        assertFalse("Built-in Turnip driver cannot be uninstalled", driverManager.uninstallCustomPackage("driver_turnip_adreno"))
    }
}
