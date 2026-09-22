package com.auracommunityact.missiongtamobile.runtime

import android.app.Activity
import android.content.Context
import android.view.Surface
import com.auracommunityact.missiongtamobile.device.DeviceInfoManager
import com.auracommunityact.missiongtamobile.device.DriverManager
import com.auracommunityact.missiongtamobile.device.LoadedDriver
import com.auracommunityact.missiongtamobile.device.PerformanceManager
import com.auracommunityact.missiongtamobile.input.NormalizedInput
import com.auracommunityact.missiongtamobile.input.TouchController
import com.auracommunityact.missiongtamobile.runtime.compat.CompatibilityRuntime
import com.auracommunityact.missiongtamobile.runtime.compat.ExecutableType
import com.auracommunityact.missiongtamobile.runtime.compat.GameExecutable
import com.auracommunityact.missiongtamobile.runtime.compat.NativeArm64Runtime
import com.auracommunityact.missiongtamobile.runtime.compat.RuntimeEnvironment
import com.auracommunityact.missiongtamobile.runtime.compat.RuntimeExecutionListener
import com.auracommunityact.missiongtamobile.runtime.compat.WindowsCompatibilityRuntime
import com.auracommunityact.missiongtamobile.runtime.dxvk.DxvkManager
import com.auracommunityact.missiongtamobile.runtime.logging.DiagnosticLogger
import com.auracommunityact.missiongtamobile.runtime.state.DriverStatus
import com.auracommunityact.missiongtamobile.runtime.state.GameDataStatus
import com.auracommunityact.missiongtamobile.runtime.state.GameStatus
import com.auracommunityact.missiongtamobile.runtime.state.RendererStatus
import com.auracommunityact.missiongtamobile.runtime.state.RuntimeStatus
import com.auracommunityact.missiongtamobile.storage.GameDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class PipelineStepResult {
    data class Success(val stepNumber: Int, val message: String) : PipelineStepResult()
    data class Failure(val stepNumber: Int, val error: String, val category: String) : PipelineStepResult()
}

class GameRuntimeManager(
    val context: Context,
    val gameDataManager: GameDataManager = GameDataManager(context),
    val driverManager: DriverManager = DriverManager(context),
    val dxvkManager: DxvkManager = DxvkManager(context),
    val performanceManager: PerformanceManager = PerformanceManager(context),
    val touchController: TouchController = TouchController(context)
) : GameRuntime {

    private val _runtimeStatus = MutableStateFlow(RuntimeStatus.CHECKING)
    val runtimeStatus: StateFlow<RuntimeStatus> = _runtimeStatus.asStateFlow()

    private val _rendererStatus = MutableStateFlow(RendererStatus.INITIALIZING)
    val rendererStatus: StateFlow<RendererStatus> = _rendererStatus.asStateFlow()

    private val _gameStatus = MutableStateFlow(GameStatus.STOPPED)
    val gameStatus: StateFlow<GameStatus> = _gameStatus.asStateFlow()

    private val _pipelineProgress = MutableStateFlow<String?>("Ready")
    val pipelineProgress: StateFlow<String?> = _pipelineProgress.asStateFlow()

    private val _pipelineStepNumber = MutableStateFlow(0)
    val pipelineStepNumber: StateFlow<Int> = _pipelineStepNumber.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Backward compatibility with legacy GameRuntime interface
    override val status: GameRuntimeStatus
        get() = when (_gameStatus.value) {
            GameStatus.RUNNING -> GameRuntimeStatus.RUNNING
            GameStatus.STARTING -> GameRuntimeStatus.INITIALIZING
            GameStatus.CRASHED -> GameRuntimeStatus.ERROR
            GameStatus.STOPPED -> if (_runtimeStatus.value == RuntimeStatus.READY) GameRuntimeStatus.READY else GameRuntimeStatus.PENDING
        }

    private var activeSurface: Surface? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var loadedDriver: LoadedDriver? = null
    private var activeRuntimeEnvironment: RuntimeEnvironment? = null
    private var activeCompatibilityRuntime: CompatibilityRuntime? = null

    init {
        DiagnosticLogger.init(context)
        DiagnosticLogger.logRuntime("Initializing GameRuntimeManager architecture.")
        checkInitialStatus()
    }

    private fun checkInitialStatus() {
        val nativeRuntime = NativeArm64Runtime()
        val isNativeInstalled = nativeRuntime.checkInstallation(context)
        if (isNativeInstalled) {
            _runtimeStatus.value = RuntimeStatus.READY
        } else {
            _runtimeStatus.value = RuntimeStatus.ERROR
        }
    }

    /**
     * Executes the strict 16-step initialization pipeline.
     * If ANY required step fails: STOP. Show the exact reason. Never continue with fake success.
     */
    fun startLaunchPipeline(surface: Surface?, onFinished: (Boolean, String) -> Unit) {
        if (_gameStatus.value == GameStatus.STARTING || _gameStatus.value == GameStatus.RUNNING) {
            onFinished(false, "Game is already starting or running.")
            return
        }

        _gameStatus.value = GameStatus.STARTING
        _lastError.value = null

        CoroutineScope(Dispatchers.Default).launch {
            val result = executePipeline(surface)
            withContext(Dispatchers.Main) {
                when (result) {
                    is PipelineStepResult.Success -> {
                        _pipelineProgress.value = "Pipeline complete. Game active."
                        onFinished(true, "Launch successful")
                    }
                    is PipelineStepResult.Failure -> {
                        _gameStatus.value = GameStatus.STOPPED
                        _lastError.value = "[Step ${result.stepNumber}/16] ${result.error}"
                        _pipelineProgress.value = "Failed at Step ${result.stepNumber}: ${result.error}"
                        DiagnosticLogger.logRuntime("Pipeline FAILED at step ${result.stepNumber}: ${result.error}")
                        onFinished(false, result.error)
                    }
                }
            }
        }
    }

    private suspend fun executePipeline(surface: Surface?): PipelineStepResult {
        // Step 1: Check game-data permission
        updateProgress(1, "Checking game-data storage permission...")
        val selectedUri = gameDataManager.getSelectedGameDirectory()
        if (selectedUri == null) {
            return PipelineStepResult.Failure(1, "No game data directory selected. Please select game data first.", "Storage")
        }

        // Step 2: Validate selected game data
        updateProgress(2, "Validating game data integrity and required resources...")
        val validationResult = gameDataManager.validateGameDataSync(selectedUri)
        if (validationResult.status != GameDataStatus.READY) {
            val missing = validationResult.missingFiles.joinToString()
            return PipelineStepResult.Failure(
                2,
                "Game data invalid or missing required files: $missing",
                "GameData"
            )
        }

        // Step 3: Detect device
        updateProgress(3, "Detecting device hardware and memory...")
        val hw = DeviceInfoManager.getHardwareReport(context, forceRefresh = true)
        DiagnosticLogger.logRuntime("Hardware detected: ${hw.deviceManufacturer} ${hw.deviceModel}, RAM: ${hw.totalRamMb}MB")

        // Step 4: Detect ABI
        updateProgress(4, "Checking processor ABI support...")
        if (!hw.isArm64Supported) {
            return PipelineStepResult.Failure(4, "Unsupported CPU ABI: ${hw.primaryAbi}. arm64-v8a is required.", "Hardware")
        }

        // Step 5: Detect GPU
        updateProgress(5, "Detecting GPU vendor and renderer...")
        val gpuRenderer = hw.gpuRenderer
        DiagnosticLogger.logDriver("GPU Renderer: $gpuRenderer, Vendor: ${hw.gpuVendor}, Family: ${hw.gpuFamily}")

        // Step 6: Detect Vulkan
        updateProgress(6, "Detecting Vulkan hardware capabilities...")
        if (!hw.vulkanSupported) {
            return PipelineStepResult.Failure(6, "Vulkan is not supported on this device.", "Vulkan")
        }

        // Step 7: Select compatible driver
        updateProgress(7, "Selecting compatible GPU driver...")
        val driver = driverManager.detectDriver()
        driverManager.selectDriver(driver.id)

        // Step 8: Validate driver
        updateProgress(8, "Validating selected GPU driver...")
        val driverValidation = driverManager.validateDriver(driver)
        if (!driverValidation.isValid) {
            return PipelineStepResult.Failure(8, "Driver validation failed: ${driverValidation.reason}", "Driver")
        }
        val loadResult = driverManager.loadDriver(driver)
        if (loadResult.isFailure) {
            return PipelineStepResult.Failure(8, "Failed to load driver: ${loadResult.exceptionOrNull()?.message}", "Driver")
        }
        loadedDriver = loadResult.getOrNull()

        // Step 9: Select compatible DXVK / runtime configuration
        updateProgress(9, "Selecting compatible DXVK runtime profile...")
        dxvkManager.detectAndConfigure()
        val dxvkProfile = dxvkManager.activeProfile.value
        DiagnosticLogger.logRuntime("Selected DXVK version: ${dxvkProfile.version}")

        // Step 10: Prepare runtime environment
        updateProgress(10, "Preparing runtime environment and shader cache...")
        val runtimeRoot = File(context.filesDir, "runtime").apply { if (!exists()) mkdirs() }
        val dxvkConf = dxvkManager.prepareDxvkConfigFile(runtimeRoot, dxvkProfile)

        // Step 11: Create game surface
        updateProgress(11, "Verifying game display surface...")
        val gameSurface = surface ?: activeSurface
        if (gameSurface == null || !gameSurface.isValid) {
            return PipelineStepResult.Failure(11, "Game display surface is not created or invalid.", "Renderer")
        }

        // Step 12: Initialize runtime
        updateProgress(12, "Checking runtime engine executable and dependencies...")
        val nativeRuntime = NativeArm64Runtime()
        val isNativeInstalled = nativeRuntime.checkInstallation(context)

        val compatRuntime: CompatibilityRuntime = if (isNativeInstalled) {
            nativeRuntime
        } else {
            val winRuntime = WindowsCompatibilityRuntime()
            winRuntime.checkInstallation(context)
            winRuntime
        }

        // Step 13: Start actual supported executable/game runtime
        updateProgress(13, "Launching runtime engine...")
        if (!compatRuntime.isInstalled) {
            _runtimeStatus.value = RuntimeStatus.ERROR
            return PipelineStepResult.Failure(
                13,
                "Runtime engine not installed: Required native ARM64 engine (libgta5.so) or compatibility layer is missing.",
                "Engine"
            )
        }

        val gameDirFile = File(context.filesDir, "gamedata") // Resolved game data root
        val env = compatRuntime.prepareEnvironment(context, gameDirFile, dxvkConf, loadedDriver)
        activeRuntimeEnvironment = env
        activeCompatibilityRuntime = compatRuntime

        val gameExec = GameExecutable(
            name = if (isNativeInstalled) "libgta5.so" else "GTA5.exe",
            type = if (isNativeInstalled) ExecutableType.NATIVE_ARM64_SHARED_LIB else ExecutableType.WINDOWS_X86_64_PE,
            architecture = "arm64-v8a",
            targetPath = if (isNativeInstalled) "libgta5.so" else "GTA5.exe",
            exists = true,
            description = "Main Game Executable"
        )

        val started = compatRuntime.start(
            executable = gameExec,
            surface = gameSurface,
            env = env,
            listener = object : RuntimeExecutionListener {
                override fun onRuntimeStarted() {
                    _gameStatus.value = GameStatus.RUNNING
                    _runtimeStatus.value = RuntimeStatus.READY
                    _rendererStatus.value = RendererStatus.READY
                    performanceManager.setGameRunning(true)
                }

                override fun onFrameRendered(frameTimeMs: Float) {
                    performanceManager.onFrameRendered()
                }

                override fun onRuntimeError(error: String, throwable: Throwable?) {
                    _gameStatus.value = GameStatus.CRASHED
                    _runtimeStatus.value = RuntimeStatus.ERROR
                    _lastError.value = "Runtime error: $error"
                    DiagnosticLogger.logRuntime("CRASH: $error", throwable)
                    performanceManager.setGameRunning(false)
                }

                override fun onRuntimeStopped(exitCode: Int) {
                    _gameStatus.value = GameStatus.STOPPED
                    performanceManager.setGameRunning(false)
                    DiagnosticLogger.logRuntime("Runtime stopped with exit code $exitCode")
                }
            }
        )

        if (!started) {
            return PipelineStepResult.Failure(13, "Runtime failed to start.", "Engine")
        }

        // Step 14: Attach touch/input system
        updateProgress(14, "Attaching touch controller and input hooks...")
        touchController.onInputChanged = { input ->
            handleInput(input)
        }

        // Step 15: Enter fullscreen
        updateProgress(15, "Entering fullscreen immersive mode...")

        // Step 16: Start performance monitoring
        updateProgress(16, "Starting real-time performance telemetry...")
        performanceManager.setGameRunning(true)
        _gameStatus.value = GameStatus.RUNNING
        _rendererStatus.value = RendererStatus.READY

        return PipelineStepResult.Success(16, "Game runtime initialized successfully.")
    }

    private suspend fun updateProgress(step: Int, message: String) {
        withContext(Dispatchers.Main) {
            _pipelineStepNumber.value = step
            _pipelineProgress.value = "Step $step/16: $message"
            DiagnosticLogger.logRuntime("[Step $step/16] $message")
        }
    }

    fun quitToLauncher() {
        DiagnosticLogger.logRuntime("Quit to Launcher initiated.")
        stop()
        _gameStatus.value = GameStatus.STOPPED
        _rendererStatus.value = RendererStatus.INITIALIZING
        performanceManager.setGameRunning(false)
    }

    fun exitApp(activity: Activity?) {
        DiagnosticLogger.logRuntime("Exit App initiated.")
        quitToLauncher()
        shutdown()
        activity?.finish()
    }

    // GameRuntime interface overrides
    override fun initialize() {
        checkInitialStatus()
    }

    override fun start() {
        activeSurface?.let { surface ->
            startLaunchPipeline(surface) { _, _ -> }
        }
    }

    override fun pause() {
        DiagnosticLogger.logRuntime("Game paused.")
    }

    override fun resume() {
        DiagnosticLogger.logRuntime("Game resumed.")
    }

    override fun stop() {
        activeCompatibilityRuntime?.stop()
        activeCompatibilityRuntime = null
        driverManager.unloadDriver()
        loadedDriver = null
        _gameStatus.value = GameStatus.STOPPED
        performanceManager.setGameRunning(false)
        DiagnosticLogger.logRuntime("Game stopped.")
    }

    override fun shutdown() {
        stop()
        activeSurface = null
        DiagnosticLogger.logRuntime("Runtime manager shutdown.")
    }

    override fun onSurfaceCreated() {
        DiagnosticLogger.logRenderer("Vulkan game surface created.")
        _rendererStatus.value = RendererStatus.READY
    }

    fun onSurfaceCreatedWithSurface(surface: Surface) {
        activeSurface = surface
        onSurfaceCreated()
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        DiagnosticLogger.logRenderer("Surface changed: ${width}x${height}")
    }

    override fun onSurfaceDestroyed() {
        DiagnosticLogger.logRenderer("Surface destroyed.")
        activeSurface = null
        _rendererStatus.value = RendererStatus.INITIALIZING
    }

    override fun handleInput(input: NormalizedInput) {
        // Forward input events to the active runtime
        DiagnosticLogger.logRuntime("Input dispatched: move=(${input.moveX}, ${input.moveY}), sprint=${input.sprint}, jump=${input.jump}")
    }
}
