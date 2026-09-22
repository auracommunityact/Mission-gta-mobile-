package com.auracommunityact.missiongtamobile.device

import android.content.Context
import android.net.Uri
import com.auracommunityact.missiongtamobile.runtime.logging.DiagnosticLogger
import com.auracommunityact.missiongtamobile.runtime.state.DriverStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Orchestrates GPU Vulkan driver detection, validation, selection, and runtime loading.
 * Provides a clean architectural abstraction separating OS-provided System Vulkan drivers
 * from installable/packaged Vulkan/Turnip drivers.
 */
class DriverManager(
    private val context: Context,
    val packageManager: DriverPackageManager = DriverPackageManager(context)
) {
    private val _status = MutableStateFlow(DriverStatus.DETECTING)
    val status: StateFlow<DriverStatus> = _status.asStateFlow()

    private val _currentDriver = MutableStateFlow<DriverPackage?>(null)
    val currentDriver: StateFlow<DriverPackage?> = _currentDriver.asStateFlow()

    private val _selectedVulkanDriver = MutableStateFlow<VulkanDriver?>(null)
    val selectedVulkanDriver: StateFlow<VulkanDriver?> = _selectedVulkanDriver.asStateFlow()

    private val _loadedDriver = MutableStateFlow<LoadedDriver?>(null)
    val loadedDriver: StateFlow<LoadedDriver?> = _loadedDriver.asStateFlow()

    private val allDrivers = mutableListOf<VulkanDriver>()

    init {
        scanDrivers()
    }

    /**
     * Rescans all available system and packaged Vulkan drivers.
     */
    fun scanDrivers() {
        _status.value = DriverStatus.DETECTING
        allDrivers.clear()

        val hw = DeviceInfoManager.getHardwareReport(context)

        // 1. System Vulkan driver (OS provided baseline)
        val systemDriver = SystemVulkanDriver(
            version = hw.vulkanVersion ?: "Default",
            vendor = hw.gpuVendor ?: "System",
            targetGpuFamily = hw.gpuFamily,
            isVulkanSupportedOnDevice = hw.vulkanSupported
        )
        allDrivers.add(systemDriver)

        // 2. Packaged drivers (Turnip & Custom packages managed via DriverPackageManager)
        val packagedDrivers = packageManager.getInstalledPackagedDrivers(
            isAdreno = hw.gpuFamily == GpuFamily.ADRENO,
            isArm64 = hw.isArm64Supported
        )
        allDrivers.addAll(packagedDrivers)

        // Detect & select optimal driver for device
        val optimal = detectOptimalVulkanDriver()
        selectVulkanDriver(optimal)
    }

    /**
     * Detects the optimal Vulkan driver based on device hardware capabilities.
     * Returns a VulkanDriver instance (SystemVulkanDriver, TurnipDriverPackage, or CustomDriverPackage).
     */
    fun detectOptimalVulkanDriver(): VulkanDriver {
        val hw = DeviceInfoManager.getHardwareReport(context)

        if (!hw.vulkanSupported) {
            _status.value = DriverStatus.UNSUPPORTED
            DiagnosticLogger.logDriver("Vulkan hardware support not detected on this device.")
            return getSystemDriver()
        }

        return when (hw.gpuFamily) {
            GpuFamily.ADRENO -> {
                // For Qualcomm Adreno GPUs, Turnip driver package is preferred when 64-bit ARM is available
                val turnip = allDrivers.filterIsInstance<TurnipDriverPackage>().firstOrNull()
                if (turnip != null && hw.isArm64Supported) {
                    DiagnosticLogger.logDriver("Qualcomm Adreno GPU detected. Selected Turnip Vulkan driver package.")
                    turnip
                } else {
                    DiagnosticLogger.logDriver("Qualcomm Adreno GPU detected, falling back to System Vulkan driver.")
                    getSystemDriver()
                }
            }
            GpuFamily.MALI -> {
                DiagnosticLogger.logDriver("ARM Mali GPU detected (${hw.gpuRenderer ?: "Mali"}). Turnip is only compatible with Adreno. Using System Vulkan driver.")
                getSystemDriver()
            }
            GpuFamily.POWERVR -> {
                DiagnosticLogger.logDriver("PowerVR GPU detected. Using System Vulkan driver.")
                getSystemDriver()
            }
            GpuFamily.XCLIPSE -> {
                DiagnosticLogger.logDriver("Samsung Xclipse GPU detected. Using System Vulkan driver.")
                getSystemDriver()
            }
            else -> {
                DiagnosticLogger.logDriver("GPU family ${hw.gpuFamily} detected (${hw.gpuRenderer ?: "Unknown"}). Using System Vulkan driver.")
                getSystemDriver()
            }
        }
    }

    /**
     * Backward-compatible driver detection returning DriverPackage.
     */
    fun detectDriver(): DriverPackage {
        return detectOptimalVulkanDriver().toDriverPackage()
    }

    /**
     * Returns the System Vulkan driver abstraction.
     */
    fun getSystemDriver(): SystemVulkanDriver {
        return allDrivers.filterIsInstance<SystemVulkanDriver>().firstOrNull()
            ?: SystemVulkanDriver(
                version = "Default",
                vendor = "System",
                targetGpuFamily = GpuFamily.UNKNOWN,
                isVulkanSupportedOnDevice = false
            )
    }

    /**
     * Returns all managed packaged drivers (Turnip + Custom).
     */
    fun getPackagedDrivers(): List<PackagedVulkanDriver> {
        return allDrivers.filterIsInstance<PackagedVulkanDriver>()
    }

    /**
     * Returns all Turnip driver packages.
     */
    fun getTurnipPackages(): List<TurnipDriverPackage> {
        return allDrivers.filterIsInstance<TurnipDriverPackage>()
    }

    /**
     * Returns all custom imported packages.
     */
    fun getCustomPackages(): List<CustomDriverPackage> {
        return allDrivers.filterIsInstance<CustomDriverPackage>()
    }

    /**
     * Returns all Vulkan drivers as VulkanDriver abstractions.
     */
    fun getAllVulkanDrivers(): List<VulkanDriver> = allDrivers.toList()

    /**
     * Returns all drivers as legacy DriverPackage DTOs.
     */
    fun getAvailableDrivers(): List<DriverPackage> {
        return allDrivers.map { it.toDriverPackage() }
    }

    /**
     * Returns all compatible drivers for current hardware.
     */
    fun getCompatibleDrivers(): List<DriverPackage> {
        return allDrivers
            .filter { validateDriver(it).isValid }
            .map { it.toDriverPackage() }
    }

    /**
     * Selects a driver by its unique ID.
     */
    fun selectDriver(driverId: String): DriverPackage? {
        val driver = allDrivers.firstOrNull { it.id == driverId } ?: return null
        selectVulkanDriver(driver)
        return driver.toDriverPackage()
    }

    /**
     * Selects and validates a VulkanDriver instance.
     */
    fun selectVulkanDriver(driver: VulkanDriver): DriverValidationResult {
        val validation = validateDriver(driver)
        _status.value = validation.status
        _selectedVulkanDriver.value = driver
        _currentDriver.value = driver.toDriverPackage()
        DiagnosticLogger.logDriver("Selected driver: ${driver.name} (Status: ${validation.status}, Reason: ${validation.reason})")
        return validation
    }

    /**
     * Validates a VulkanDriver instance against device hardware and environment.
     */
    fun validateDriver(driver: VulkanDriver): DriverValidationResult {
        val hw = DeviceInfoManager.getHardwareReport(context)

        // Rule 1: Device must support Vulkan hardware features
        if (!hw.vulkanSupported) {
            return DriverValidationResult(
                isValid = false,
                status = DriverStatus.UNSUPPORTED,
                reason = "Device does not support Vulkan hardware features."
            )
        }

        // Rule 2: Architecture requirements for packaged drivers
        if (driver.isPackaged && !hw.isArm64Supported) {
            return DriverValidationResult(
                isValid = false,
                status = DriverStatus.UNSUPPORTED,
                reason = "${driver.name} requires a 64-bit ARM (arm64-v8a) device architecture."
            )
        }

        // Rule 3: GPU family compatibility check (e.g. Turnip is Adreno-exclusive)
        when (driver) {
            is TurnipDriverPackage -> {
                if (hw.gpuFamily != GpuFamily.ADRENO) {
                    val currentGpu = hw.gpuRenderer ?: hw.gpuFamily.name
                    return DriverValidationResult(
                        isValid = false,
                        status = DriverStatus.UNSUPPORTED,
                        reason = "Turnip driver is only compatible with Qualcomm Adreno GPUs (current GPU: $currentGpu)."
                    )
                }
            }
            is CustomDriverPackage -> {
                if (driver.targetGpuFamily != GpuFamily.UNKNOWN && driver.targetGpuFamily != hw.gpuFamily) {
                    return DriverValidationResult(
                        isValid = false,
                        status = DriverStatus.UNSUPPORTED,
                        reason = "Custom package targets ${driver.targetGpuFamily} but device has ${hw.gpuFamily}."
                    )
                }
            }
            is SystemVulkanDriver -> {
                // System Vulkan driver is always compatible if Vulkan is supported
            }
        }

        return DriverValidationResult(
            isValid = true,
            status = DriverStatus.READY,
            reason = "Driver is validated and compatible with ${hw.gpuRenderer ?: hw.gpuFamily.name}."
        )
    }

    /**
     * Validates a legacy DriverPackage DTO.
     */
    fun validateDriver(driver: DriverPackage): DriverValidationResult {
        val matched = allDrivers.firstOrNull { it.id == driver.id }
        return if (matched != null) {
            validateDriver(matched)
        } else {
            // Fallback validation based on DTO fields
            val hw = DeviceInfoManager.getHardwareReport(context)
            if (!hw.vulkanSupported) {
                return DriverValidationResult(false, DriverStatus.UNSUPPORTED, "Device does not support Vulkan.")
            }
            if (driver.type == DriverType.TURNIP_ADRENO && hw.gpuFamily != GpuFamily.ADRENO) {
                return DriverValidationResult(
                    false,
                    DriverStatus.UNSUPPORTED,
                    "Turnip driver is only compatible with Qualcomm Adreno GPUs."
                )
            }
            DriverValidationResult(true, DriverStatus.READY, "Driver validated.")
        }
    }

    /**
     * Loads and activates a VulkanDriver instance, configuring process environment variables.
     */
    fun loadDriver(driver: VulkanDriver): Result<LoadedDriver> {
        val validation = validateDriver(driver)
        if (!validation.isValid) {
            val err = "Cannot load incompatible driver ${driver.name}: ${validation.reason}"
            DiagnosticLogger.logDriver(err)
            _status.value = validation.status
            return Result.failure(IllegalStateException(err))
        }

        val envVars = driver.getEnvironmentVariables(context).toMutableMap()
        val icdPath = driver.getIcdPath(context)

        val loaded = LoadedDriver(
            driverPackage = driver.toDriverPackage(),
            icdPath = icdPath,
            environmentVariables = envVars
        )

        _loadedDriver.value = loaded
        _selectedVulkanDriver.value = driver
        _currentDriver.value = driver.toDriverPackage()
        _status.value = DriverStatus.READY

        DiagnosticLogger.logDriver("Loaded driver: ${driver.name} (ICD: ${icdPath ?: "System libvulkan.so"})")
        return Result.success(loaded)
    }

    /**
     * Loads a driver from a legacy DriverPackage DTO.
     */
    fun loadDriver(driver: DriverPackage): Result<LoadedDriver> {
        val matched = allDrivers.firstOrNull { it.id == driver.id }
        return if (matched != null) {
            loadDriver(matched)
        } else {
            // Synthesize from DriverPackage
            val validation = validateDriver(driver)
            if (!validation.isValid) {
                return Result.failure(IllegalStateException(validation.reason))
            }
            val loaded = LoadedDriver(
                driverPackage = driver,
                icdPath = null,
                environmentVariables = emptyMap()
            )
            _loadedDriver.value = loaded
            _currentDriver.value = driver
            _status.value = DriverStatus.READY
            Result.success(loaded)
        }
    }

    /**
     * Unloads the active driver and resets runtime driver environment variables.
     */
    fun unloadDriver() {
        val current = _loadedDriver.value?.driverPackage?.name ?: "None"
        _loadedDriver.value = null
        _status.value = DriverStatus.DETECTING
        DiagnosticLogger.logDriver("Unloaded active driver ($current).")
    }

    /**
     * Imports a user-supplied custom driver package from a ZIP file URI.
     */
    fun importDriverZip(uri: Uri): Result<DriverPackage> {
        return try {
            val result = packageManager.installPackageFromZipUri(uri)
            if (result.isSuccess) {
                val installed = result.getOrThrow()
                scanDrivers()
                selectDriver(installed.id)
                Result.success(installed.toDriverPackage())
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown import error"))
            }
        } catch (e: Exception) {
            DiagnosticLogger.logDriver("Driver import error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Uninstalls a custom driver package.
     */
    fun uninstallCustomPackage(packageId: String): Boolean {
        val result = packageManager.uninstallPackage(packageId)
        if (result) {
            scanDrivers()
        }
        return result
    }
}
