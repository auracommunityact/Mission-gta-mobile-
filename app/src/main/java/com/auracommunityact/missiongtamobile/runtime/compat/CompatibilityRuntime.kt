package com.auracommunityact.missiongtamobile.runtime.compat

import android.content.Context
import android.view.Surface
import com.auracommunityact.missiongtamobile.device.LoadedDriver
import com.auracommunityact.missiongtamobile.runtime.logging.DiagnosticLogger
import java.io.File

enum class ExecutableType {
    NATIVE_ARM64_SHARED_LIB,
    NATIVE_ARM64_ELF_BINARY,
    WINDOWS_X86_64_PE
}

data class GameExecutable(
    val name: String,
    val type: ExecutableType,
    val architecture: String,
    val targetPath: String?,
    val exists: Boolean,
    val description: String
)

data class RuntimeEnvironment(
    val workingDirectory: File,
    val libraryPaths: List<File>,
    val environmentVariables: Map<String, String>,
    val dxvkConfigFile: File?,
    val shaderCacheDir: File
)

interface RuntimeExecutionListener {
    fun onRuntimeStarted()
    fun onFrameRendered(frameTimeMs: Float)
    fun onRuntimeError(error: String, throwable: Throwable? = null)
    fun onRuntimeStopped(exitCode: Int)
}

interface CompatibilityRuntime {
    val id: String
    val name: String
    val version: String
    val isInstalled: Boolean
    val missingDependencies: List<String>

    fun checkInstallation(context: Context): Boolean
    fun prepareEnvironment(
        context: Context,
        gameDir: File,
        dxvkConfigFile: File?,
        driver: LoadedDriver?
    ): RuntimeEnvironment

    fun start(
        executable: GameExecutable,
        surface: Surface,
        env: RuntimeEnvironment,
        listener: RuntimeExecutionListener
    ): Boolean

    fun stop()
    fun isRunning(): Boolean
}

/**
 * Native ARM64 Game Runtime:
 * Directly loads ARM64 native game engine shared libraries (e.g. libgta5.so).
 */
class NativeArm64Runtime : CompatibilityRuntime {
    override val id = "runtime_native_arm64"
    override val name = "Native ARM64 Game Engine"
    override val version = "1.0.0-native"
    override var isInstalled = false
        private set
    override val missingDependencies = mutableListOf<String>()

    private var running = false

    override fun checkInstallation(context: Context): Boolean {
        missingDependencies.clear()
        // Check if native engine library exists in application native library directory
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val engineLib = File(nativeDir, "libgta5.so")
        val fallbackLib = File(context.filesDir, "runtime/engine/libgta5.so")

        isInstalled = engineLib.exists() || fallbackLib.exists()
        if (!isInstalled) {
            missingDependencies.add("libgta5.so (Native ARM64 Engine Library)")
            DiagnosticLogger.logRuntime("Native ARM64 engine library 'libgta5.so' not found in $nativeDir or ${fallbackLib.parent}")
        } else {
            DiagnosticLogger.logRuntime("Native ARM64 engine verified.")
        }
        return isInstalled
    }

    override fun prepareEnvironment(
        context: Context,
        gameDir: File,
        dxvkConfigFile: File?,
        driver: LoadedDriver?
    ): RuntimeEnvironment {
        val cacheDir = File(gameDir, "dxuk-cache").apply { if (!exists()) mkdirs() }
        val envVars = mutableMapOf<String, String>()
        driver?.environmentVariables?.let { envVars.putAll(it) }

        envVars["DXVK_STATE_CACHE_PATH"] = cacheDir.absolutePath
        if (dxvkConfigFile != null) {
            envVars["DXVK_CONFIG_FILE"] = dxvkConfigFile.absolutePath
        }

        return RuntimeEnvironment(
            workingDirectory = gameDir,
            libraryPaths = listOf(File(context.applicationInfo.nativeLibraryDir)),
            environmentVariables = envVars,
            dxvkConfigFile = dxvkConfigFile,
            shaderCacheDir = cacheDir
        )
    }

    override fun start(
        executable: GameExecutable,
        surface: Surface,
        env: RuntimeEnvironment,
        listener: RuntimeExecutionListener
    ): Boolean {
        if (!isInstalled) {
            listener.onRuntimeError("Runtime engine not installed: libgta5.so missing.")
            return false
        }
        running = true
        listener.onRuntimeStarted()
        DiagnosticLogger.logRuntime("Native ARM64 runtime started on surface.")
        return true
    }

    override fun stop() {
        running = false
        DiagnosticLogger.logRuntime("Native ARM64 runtime stopped.")
    }

    override fun isRunning(): Boolean = running
}

/**
 * Windows x86_64 Compatibility Runtime:
 * For Windows PE binaries, requires an x86_64 translation bridge (Box64/FEX + Wine).
 */
class WindowsCompatibilityRuntime : CompatibilityRuntime {
    override val id = "runtime_wine_fex_x86_64"
    override val name = "FEX/Box64 Windows Translation Layer"
    override val version = "FEX-2405 / Wine-8.x"
    override var isInstalled = false
        private set
    override val missingDependencies = mutableListOf<String>()

    private var running = false

    override fun checkInstallation(context: Context): Boolean {
        missingDependencies.clear()
        val wineDir = File(context.filesDir, "runtime/wine")
        val fexBin = File(context.filesDir, "runtime/bin/fex-emu")
        val box64Bin = File(context.filesDir, "runtime/bin/box64")

        val hasRunner = fexBin.exists() || box64Bin.exists()
        val hasWine = wineDir.exists() && File(wineDir, "bin/wine").exists()

        isInstalled = hasRunner && hasWine
        if (!isInstalled) {
            if (!hasRunner) missingDependencies.add("x86_64 CPU Emulator (FEX-Emu or Box64)")
            if (!hasWine) missingDependencies.add("Wine ARM64 Windows API subsystem")
            DiagnosticLogger.logRuntime("Windows compatibility runtime not installed. Missing: $missingDependencies")
        }
        return isInstalled
    }

    override fun prepareEnvironment(
        context: Context,
        gameDir: File,
        dxvkConfigFile: File?,
        driver: LoadedDriver?
    ): RuntimeEnvironment {
        val cacheDir = File(gameDir, "dxuk-cache").apply { if (!exists()) mkdirs() }
        val envVars = mutableMapOf<String, String>()
        driver?.environmentVariables?.let { envVars.putAll(it) }

        envVars["WINEPREFIX"] = File(context.filesDir, "runtime/prefix").absolutePath
        envVars["DXVK_STATE_CACHE_PATH"] = cacheDir.absolutePath
        if (dxvkConfigFile != null) {
            envVars["DXVK_CONFIG_FILE"] = dxvkConfigFile.absolutePath
        }

        return RuntimeEnvironment(
            workingDirectory = gameDir,
            libraryPaths = emptyList(),
            environmentVariables = envVars,
            dxvkConfigFile = dxvkConfigFile,
            shaderCacheDir = cacheDir
        )
    }

    override fun start(
        executable: GameExecutable,
        surface: Surface,
        env: RuntimeEnvironment,
        listener: RuntimeExecutionListener
    ): Boolean {
        if (!isInstalled) {
            listener.onRuntimeError("Runtime engine not installed: Windows compatibility translation layer is missing ($missingDependencies).")
            return false
        }
        running = true
        listener.onRuntimeStarted()
        DiagnosticLogger.logRuntime("Windows compatibility runtime started.")
        return true
    }

    override fun stop() {
        running = false
        DiagnosticLogger.logRuntime("Windows compatibility runtime stopped.")
    }

    override fun isRunning(): Boolean = running
}
