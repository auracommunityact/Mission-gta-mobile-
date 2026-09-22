package com.auracommunityact.missiongtamobile.runtime.dxvk

import android.content.Context
import com.auracommunityact.missiongtamobile.device.DeviceInfoManager
import com.auracommunityact.missiongtamobile.device.GpuFamily
import com.auracommunityact.missiongtamobile.runtime.logging.DiagnosticLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter

enum class DxvkStatus(val displayName: String) {
    CHECKING("CHECKING"),
    AVAILABLE("AVAILABLE"),
    NOT_INSTALLED("NOT INSTALLED"),
    ERROR("ERROR")
}

data class DxvkProfile(
    val version: String,
    val asyncShaders: Boolean = true,
    val maxFramerate: Int = 60,
    val enableGpl: Boolean = true,
    val stateCache: Boolean = true,
    val hudOptions: String = "fps,frametimes,gpurenderer",
    val customConfig: Map<String, String> = emptyMap()
)

data class DxvkValidationResult(
    val status: DxvkStatus,
    val version: String,
    val isReady: Boolean,
    val requiredFilesPresent: List<String>,
    val missingFiles: List<String>,
    val reason: String
)

class DxvkManager(private val context: Context) {
    private val _status = MutableStateFlow(DxvkStatus.CHECKING)
    val status: StateFlow<DxvkStatus> = _status.asStateFlow()

    private val _activeVersion = MutableStateFlow("2.4")
    val activeVersion: StateFlow<String> = _activeVersion.asStateFlow()

    private val _activeProfile = MutableStateFlow(DxvkProfile("2.4"))
    val activeProfile: StateFlow<DxvkProfile> = _activeProfile.asStateFlow()

    private val dxvkRuntimeDir = File(context.filesDir, "runtime/dxvk").apply {
        if (!exists()) mkdirs()
    }

    private val supportedVersions = listOf("2.4", "2.3.1", "1.10.3-async")

    init {
        detectAndConfigure()
    }

    fun detectAndConfigure() {
        _status.value = DxvkStatus.CHECKING
        val hw = DeviceInfoManager.getHardwareReport(context)

        // Select compatible profile based on GPU and Vulkan level
        val defaultVersion = when {
            hw.gpuFamily == GpuFamily.ADRENO && hw.vulkanVersion != null -> "2.4"
            hw.gpuFamily == GpuFamily.MALI -> "1.10.3-async"
            else -> "2.3.1"
        }
        _activeVersion.value = defaultVersion
        _activeProfile.value = DxvkProfile(
            version = defaultVersion,
            asyncShaders = hw.gpuFamily == GpuFamily.MALI || hw.gpuFamily == GpuFamily.ADRENO,
            maxFramerate = 60,
            enableGpl = hw.vulkanLevel != null && hw.vulkanLevel >= 1
        )

        val validation = validateDxvk()
        _status.value = validation.status
        DiagnosticLogger.logRuntime("DXVK configured: $defaultVersion (${validation.reason})")
    }

    fun getSupportedVersions(): List<String> = supportedVersions

    fun selectVersion(version: String): DxvkValidationResult {
        if (!supportedVersions.contains(version)) {
            val err = "Unsupported DXVK version: $version"
            _status.value = DxvkStatus.ERROR
            return DxvkValidationResult(DxvkStatus.ERROR, version, false, emptyList(), listOf(version), err)
        }
        _activeVersion.value = version
        _activeProfile.value = _activeProfile.value.copy(version = version)
        val validation = validateDxvk()
        _status.value = validation.status
        return validation
    }

    fun updateProfile(profile: DxvkProfile) {
        _activeProfile.value = profile
        _activeVersion.value = profile.version
        val validation = validateDxvk()
        _status.value = validation.status
    }

    fun validateDxvk(): DxvkValidationResult {
        val ver = _activeVersion.value
        val verDir = File(dxvkRuntimeDir, ver)
        val d3d11 = File(verDir, "d3d11.dll")
        val dxgi = File(verDir, "dxgi.dll")

        val present = mutableListOf<String>()
        val missing = mutableListOf<String>()

        if (d3d11.exists()) present.add("d3d11.dll") else missing.add("d3d11.dll")
        if (dxgi.exists()) present.add("dxgi.dll") else missing.add("dxgi.dll")

        // In an APK without pre-bundled DLLs, if the directory is missing files, report NOT_INSTALLED
        return if (missing.isEmpty()) {
            DxvkValidationResult(
                status = DxvkStatus.AVAILABLE,
                version = ver,
                isReady = true,
                requiredFilesPresent = present,
                missingFiles = emptyList(),
                reason = "DXVK $ver translation layer libraries present and verified."
            )
        } else {
            DxvkValidationResult(
                status = DxvkStatus.NOT_INSTALLED,
                version = ver,
                isReady = false,
                requiredFilesPresent = present,
                missingFiles = missing,
                reason = "DXVK $ver components not installed in runtime directory (${missing.joinToString()})."
            )
        }
    }

    fun prepareDxvkConfigFile(targetDir: File, profile: DxvkProfile = _activeProfile.value): File {
        if (!targetDir.exists()) targetDir.mkdirs()
        val confFile = File(targetDir, "dxvk.conf")

        FileWriter(confFile, false).use { writer ->
            writer.write("# Auto-generated DXVK Configuration for Mission GTA Mobile\n")
            writer.write("dxvk.enableAsync = ${if (profile.asyncShaders) "True" else "False"}\n")
            writer.write("dxvk.numCompilerThreads = ${Runtime.getRuntime().availableProcessors()}\n")
            writer.write("dxvk.maxFrameRate = ${profile.maxFramerate}\n")
            writer.write("dxvk.hud = ${profile.hudOptions}\n")
            writer.write("d3d11.maxTessFactor = 8\n")
            writer.write("dxgi.syncInterval = 1\n")
            profile.customConfig.forEach { (k, v) ->
                writer.write("$k = $v\n")
            }
        }
        DiagnosticLogger.logRuntime("Generated DXVK configuration at: ${confFile.absolutePath}")
        return confFile
    }
}
