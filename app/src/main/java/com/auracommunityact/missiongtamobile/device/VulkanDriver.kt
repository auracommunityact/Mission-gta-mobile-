package com.auracommunityact.missiongtamobile.device

import android.content.Context
import com.auracommunityact.missiongtamobile.runtime.state.DriverStatus
import java.io.File

/**
 * High-level driver classification.
 */
enum class DriverType {
    SYSTEM_VULKAN,
    TURNIP_ADRENO,
    CUSTOM_IMPORTED
}

/**
 * Universal validation result for any driver attempt.
 */
data class DriverValidationResult(
    val isValid: Boolean,
    val status: DriverStatus,
    val reason: String,
    val warnings: List<String> = emptyList()
)

/**
 * Representation of an actively bound and loaded driver in the game runtime process.
 */
data class LoadedDriver(
    val driverPackage: DriverPackage,
    val icdPath: String?,
    val environmentVariables: Map<String, String>
)

/**
 * Legacy/compatibility DTO representing driver metadata.
 */
data class DriverPackage(
    val id: String,
    val name: String,
    val version: String,
    val vendor: String,
    val type: DriverType,
    val targetGpuFamily: GpuFamily,
    val libraryName: String? = null,
    val icdConfigFileName: String? = null,
    val isPackaged: Boolean,
    val isAvailable: Boolean,
    val description: String
)

/**
 * Core abstraction representing any Vulkan driver available to the runtime.
 * Allows decoupling OS-provided system drivers from installable/packaged drivers.
 */
sealed interface VulkanDriver {
    val id: String
    val name: String
    val version: String
    val vendor: String
    val type: DriverType
    val targetGpuFamily: GpuFamily
    val description: String

    /**
     * True if this is an Android OS-provided native Vulkan driver.
     */
    val isSystemDriver: Boolean

    /**
     * True if this driver is distributed as an installable/managed file package.
     */
    val isPackaged: Boolean

    /**
     * Returns the absolute path to the Vulkan ICD configuration JSON file,
     * or null if this is a system driver utilizing standard Android loader paths.
     */
    fun getIcdPath(context: Context): String?

    /**
     * Returns process environment variables (such as VK_ICD_FILENAMES, TU_DEBUG)
     * required to redirect the Vulkan runtime loader to this driver.
     */
    fun getEnvironmentVariables(context: Context): Map<String, String>

    /**
     * Converts to backward-compatible DriverPackage structure.
     */
    fun toDriverPackage(): DriverPackage
}

/**
 * System Vulkan Driver provided natively by the device OEM / Android OS.
 * Bound to the Android platform libvulkan.so and vendor hardware ICD.
 */
data class SystemVulkanDriver(
    override val id: String = "driver_system_vulkan",
    override val name: String = "System Vulkan Driver",
    override val version: String,
    override val vendor: String,
    override val targetGpuFamily: GpuFamily,
    val isVulkanSupportedOnDevice: Boolean,
    override val description: String = "Native Android platform Vulkan ICD provided by device OEM."
) : VulkanDriver {
    override val type: DriverType = DriverType.SYSTEM_VULKAN
    override val isSystemDriver: Boolean = true
    override val isPackaged: Boolean = false

    override fun getIcdPath(context: Context): String? = null

    override fun getEnvironmentVariables(context: Context): Map<String, String> = emptyMap()

    override fun toDriverPackage(): DriverPackage {
        return DriverPackage(
            id = id,
            name = name,
            version = version,
            vendor = vendor,
            type = type,
            targetGpuFamily = targetGpuFamily,
            libraryName = null,
            icdConfigFileName = null,
            isPackaged = false,
            isAvailable = isVulkanSupportedOnDevice,
            description = description
        )
    }
}

/**
 * Base class for all non-system, file-packaged drivers managed on internal storage.
 * Separates file and archive management from OS-level drivers.
 */
sealed class PackagedVulkanDriver : VulkanDriver {
    override val isSystemDriver: Boolean = false
    override val isPackaged: Boolean = true

    abstract val packageDir: File
    abstract val libraryFileName: String
    abstract val icdFileName: String

    fun getDriverLibraryFile(): File = File(packageDir, libraryFileName)
    fun getIcdConfigFile(): File = File(packageDir, icdFileName)

    open fun isInstalled(): Boolean = getIcdConfigFile().exists()

    override fun getIcdPath(context: Context): String? {
        val icd = getIcdConfigFile()
        return if (icd.exists()) icd.absolutePath else null
    }
}

/**
 * Turnip Open-Source Vulkan Driver Package.
 * Built from Mesa Freedreno, specifically optimized for Qualcomm Adreno GPUs with KGSL / MSM backends.
 */
data class TurnipDriverPackage(
    override val id: String = "driver_turnip_adreno",
    override val name: String = "Turnip Open-Source Vulkan Driver",
    override val version: String = "Mesa 24.x-turnip",
    override val vendor: String = "Mesa / Freedreno",
    override val description: String = "Optimized open-source Mesa Turnip Vulkan driver for Qualcomm Adreno GPUs.",
    override val packageDir: File,
    override val libraryFileName: String = "libvulkan_freedreno.so",
    override val icdFileName: String = "freedreno_icd.arm64-v8a.json",
    val mesaVersion: String = "24.x",
    val tuDebugFlags: String = "noconform",
    val isAdrenoHardware: Boolean = false,
    val isArm64Supported: Boolean = true
) : PackagedVulkanDriver() {
    override val type: DriverType = DriverType.TURNIP_ADRENO
    override val targetGpuFamily: GpuFamily = GpuFamily.ADRENO

    override fun getEnvironmentVariables(context: Context): Map<String, String> {
        val icdPath = getIcdConfigFile().absolutePath
        return mapOf(
            "VK_ICD_FILENAMES" to icdPath,
            "TU_DEBUG" to tuDebugFlags,
            "MESA_VK_WSI_PRESENT_MODE" to "mailbox"
        )
    }

    override fun toDriverPackage(): DriverPackage {
        return DriverPackage(
            id = id,
            name = name,
            version = version,
            vendor = vendor,
            type = type,
            targetGpuFamily = targetGpuFamily,
            libraryName = libraryFileName,
            icdConfigFileName = icdFileName,
            isPackaged = true,
            isAvailable = isAdrenoHardware && isArm64Supported,
            description = description
        )
    }
}

/**
 * Custom user-imported driver package (e.g. from ZIP archive or downloaded driver package).
 */
data class CustomDriverPackage(
    override val id: String,
    override val name: String,
    override val version: String = "Custom",
    override val vendor: String = "User Package",
    override val description: String,
    override val packageDir: File,
    override val libraryFileName: String = "vulkan.so",
    override val icdFileName: String = "icd.json",
    override val targetGpuFamily: GpuFamily = GpuFamily.UNKNOWN,
    val author: String? = null,
    val customEnvVars: Map<String, String> = emptyMap()
) : PackagedVulkanDriver() {
    override val type: DriverType = DriverType.CUSTOM_IMPORTED

    override fun getEnvironmentVariables(context: Context): Map<String, String> {
        val vars = mutableMapOf<String, String>()
        val icd = getIcdConfigFile()
        if (icd.exists()) {
            vars["VK_ICD_FILENAMES"] = icd.absolutePath
        }
        vars.putAll(customEnvVars)
        return vars
    }

    override fun toDriverPackage(): DriverPackage {
        return DriverPackage(
            id = id,
            name = name,
            version = version,
            vendor = vendor,
            type = type,
            targetGpuFamily = targetGpuFamily,
            libraryName = libraryFileName,
            icdConfigFileName = icdFileName,
            isPackaged = true,
            isAvailable = packageDir.exists(),
            description = description
        )
    }
}
