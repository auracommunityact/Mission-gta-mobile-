package com.auracommunityact.missiongtamobile.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.util.Log

enum class GpuFamily {
    ADRENO,
    MALI,
    POWERVR,
    XCLIPSE,
    UNKNOWN
}

data class DeviceHardwareReport(
    val androidVersion: String,
    val apiLevel: Int,
    val primaryAbi: String,
    val supportedAbis: List<String>,
    val isArm64Supported: Boolean,
    val socManufacturer: String?,
    val socModel: String?,
    val deviceManufacturer: String,
    val deviceModel: String,
    val cpuCoreCount: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isLowRamDevice: Boolean,
    val gpuVendor: String?,
    val gpuRenderer: String?,
    val gpuFamily: GpuFamily,
    val openGlVersion: String?,
    val maxTextureSize: Int?,
    val vulkanSupported: Boolean,
    val vulkanVersion: String?,
    val vulkanLevel: Int?,
    val supportedTextureFormats: List<String>
)

object DeviceInfoManager {
    private const val TAG = "DeviceInfoManager"
    private var cachedReport: DeviceHardwareReport? = null

    fun setHardwareReportForTesting(report: DeviceHardwareReport?) {
        cachedReport = report
    }

    fun getHardwareReport(context: Context, forceRefresh: Boolean = false): DeviceHardwareReport {
        if (!forceRefresh && cachedReport != null) {
            return cachedReport!!
        }

        val appContext = context.applicationContext

        // Memory info
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val availableRamMb = memoryInfo.availMem / (1024 * 1024)
        val isLowRam = memoryInfo.lowMemory || activityManager.isLowRamDevice

        // ABIs
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        val isArm64 = supportedAbis.any { it.equals("arm64-v8a", ignoreCase = true) }

        // SoC
        var socManufacturer: String? = null
        var socModel: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            socManufacturer = Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            socModel = Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        }
        if (socManufacturer == null) {
            socManufacturer = when {
                Build.HARDWARE.lowercase().contains("qcom") || Build.HARDWARE.lowercase().contains("qualcomm") -> "Qualcomm"
                Build.HARDWARE.lowercase().contains("mt") || Build.HARDWARE.lowercase().contains("mediatek") -> "MediaTek"
                Build.HARDWARE.lowercase().contains("exynos") || Build.HARDWARE.lowercase().contains("samsung") -> "Samsung"
                Build.HARDWARE.lowercase().contains("kirin") || Build.HARDWARE.lowercase().contains("hi") -> "HiSilicon"
                Build.HARDWARE.isNotBlank() && Build.HARDWARE != Build.UNKNOWN -> Build.HARDWARE
                else -> null
            }
        }

        // OpenGL & GPU info via EGL
        val (gpuVendor, gpuRenderer, glVersion, maxTexSize, extensions) = queryEglCapabilities()

        // GPU Family identification
        val gpuFamily = identifyGpuFamily(gpuRenderer, gpuVendor)

        // Vulkan info
        val pm = appContext.packageManager
        val vulkanSupported = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        var vulkanVersion: String? = null
        var vulkanLevel: Int? = null

        if (vulkanSupported) {
            for (feature in pm.systemAvailableFeatures) {
                if (feature.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION) {
                    val ver = feature.version
                    val major = (ver shr 22) and 0x3FF
                    val minor = (ver shr 12) and 0x3FF
                    val patch = ver and 0xFFF
                    vulkanVersion = "$major.$minor.$patch"
                }
                if (feature.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) {
                    vulkanLevel = feature.version
                }
            }
        }

        // Texture formats
        val supportedFormats = mutableListOf<String>()
        if (extensions.contains("GL_OES_compressed_ETC1_RGB8_texture", ignoreCase = true)) supportedFormats.add("ETC1")
        if (extensions.contains("GL_KHR_texture_compression_astc_ldr", ignoreCase = true)) supportedFormats.add("ASTC")
        if (extensions.contains("GL_EXT_texture_compression_s3tc", ignoreCase = true) ||
            extensions.contains("GL_EXT_texture_compression_dxt1", ignoreCase = true)) supportedFormats.add("BC/DXT")
        if (extensions.contains("GL_EXT_texture_compression_bptc", ignoreCase = true)) supportedFormats.add("BC7/BPTC")

        val report = DeviceHardwareReport(
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            primaryAbi = primaryAbi,
            supportedAbis = supportedAbis,
            isArm64Supported = isArm64,
            socManufacturer = socManufacturer,
            socModel = socModel,
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            isLowRamDevice = isLowRam,
            gpuVendor = gpuVendor,
            gpuRenderer = gpuRenderer,
            gpuFamily = gpuFamily,
            openGlVersion = glVersion,
            maxTextureSize = maxTexSize,
            vulkanSupported = vulkanSupported,
            vulkanVersion = vulkanVersion,
            vulkanLevel = vulkanLevel,
            supportedTextureFormats = supportedFormats
        )

        cachedReport = report
        return report
    }

    private fun identifyGpuFamily(renderer: String?, vendor: String?): GpuFamily {
        val r = renderer?.lowercase() ?: ""
        val v = vendor?.lowercase() ?: ""
        return when {
            r.contains("adreno") || v.contains("qualcomm") -> GpuFamily.ADRENO
            r.contains("mali") || v.contains("arm") -> GpuFamily.MALI
            r.contains("powervr") || v.contains("imagination") || r.contains("rogue") -> GpuFamily.POWERVR
            r.contains("xclipse") || r.contains("samsung") -> GpuFamily.XCLIPSE
            else -> GpuFamily.UNKNOWN
        }
    }

    private data class EglDetails(
        val vendor: String?,
        val renderer: String?,
        val version: String?,
        val maxTexSize: Int?,
        val extensions: String
    )

    private fun queryEglCapabilities(): EglDetails {
        var vendor: String? = null
        var renderer: String? = null
        var version: String? = null
        var maxTexSize: Int? = null
        var extensions = ""

        try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display != EGL14.EGL_NO_DISPLAY) {
                val versionArray = IntArray(2)
                if (EGL14.eglInitialize(display, versionArray, 0, versionArray, 1)) {
                    val configAttribs = intArrayOf(
                        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                        EGL14.EGL_NONE
                    )
                    val configs = arrayOfNulls<EGLConfig>(1)
                    val numConfigs = IntArray(1)
                    if (EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
                        val config = configs[0]
                        val contextAttribs = intArrayOf(
                            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                            EGL14.EGL_NONE
                        )
                        val eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
                        if (eglContext != EGL14.EGL_NO_CONTEXT) {
                            val surfaceAttribs = intArrayOf(
                                EGL14.EGL_WIDTH, 1,
                                EGL14.EGL_HEIGHT, 1,
                                EGL14.EGL_NONE
                            )
                            val eglSurface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
                            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                                if (EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) {
                                    vendor = GLES20.glGetString(GLES20.GL_VENDOR)
                                    renderer = GLES20.glGetString(GLES20.GL_RENDERER)
                                    version = GLES20.glGetString(GLES20.GL_VERSION)
                                    extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""

                                    val maxTex = IntArray(1)
                                    GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTex, 0)
                                    if (maxTex[0] > 0) {
                                        maxTexSize = maxTex[0]
                                    }
                                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                                }
                                EGL14.eglDestroySurface(display, eglSurface)
                            }
                            EGL14.eglDestroyContext(display, eglContext)
                        }
                    }
                    EGL14.eglTerminate(display)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying EGL capabilities", e)
        }

        return EglDetails(vendor, renderer, version, maxTexSize, extensions)
    }
}
