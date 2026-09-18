package com.auracommunityact.missiongtamobile.device

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.util.Log

data class GraphicsCapabilities(
    val glVendor: String?,
    val glRenderer: String?,
    val glVersion: String?,
    val vulkanAvailable: Boolean,
    val vulkanVersion: String?
)

object GraphicsCapabilityProvider {
    fun getCapabilities(context: Context): GraphicsCapabilities {
        var vendor: String? = null
        var renderer: String? = null
        var version: String? = null

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
            Log.e("GraphicsCapability", "Failed to get EGL info", e)
        }

        val pm = context.packageManager
        val hasVulkan = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        var vkVersion: String? = null
        if (hasVulkan) {
            val features = pm.systemAvailableFeatures
            for (feature in features) {
                if (feature.name == android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION) {
                    val ver = feature.version
                    val major = (ver shr 22) and 0x3FF
                    val minor = (ver shr 12) and 0x3FF
                    val patch = ver and 0xFFF
                    vkVersion = "$major.$minor.$patch"
                    break
                }
            }
        }

        return GraphicsCapabilities(vendor, renderer, version, hasVulkan, vkVersion)
    }
}
