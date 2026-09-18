# Mission GTA Mobile

## Project Purpose
This is an experimental Android game-porting project. The purpose of this first build is to create a clean Android landscape game shell and runtime integration foundation. It provides the necessary Android UI, lifecycle handling, performance monitoring, and input normalization so that a legally usable game runtime (such as a C++ port) can be integrated later.

**Note:** This initial build does NOT contain the actual game, any proprietary GTA V assets, or fake/simulated gameplay.

## Current Status
- **Android Shell:** Ready
- **Landscape Mode:** Ready
- **Game Surface:** Ready
- **Input System:** Foundation Ready
- **Performance Monitor:** Foundation Ready
- **Native Runtime:** Not Installed (Pending Integration)
- **Game Assets:** Not Installed

## Architecture
The application is designed to be highly modular.
- `com.example.runtime`: Contains the interfaces (`GameRuntime`) and state management (`GameRuntimeProvider`) for connecting the Android shell to a native game engine.
- `com.example.renderer`: Contains `GameSurface` (wrapping `SurfaceView`) for OpenGL/Vulkan rendering.
- `com.example.input`: Provides normalized input models to send touch/controller data to the engine.
- `com.example.diagnostics`: Handles system capability detection (RAM, ABI, screen resolution).
- `com.example.ui`: Implements the landscape-first immersive Compose UI.

## Build Instructions
1. Open this project in Android Studio (Ladybug or newer recommended).
2. Sync Project with Gradle Files.
3. Select `app` run configuration.
4. Build and deploy to an Android device or emulator.
5. Minimum Android Version: 7.0 (API 24)

## Supported Android Versions
Target SDK: 36 (Android 15)
Min SDK: 24 (Android 7.0)
Requires landscape orientation support.

## Development Workflow
1. Use Android native APIs for lifecycle and UI.
2. Ensure immersive mode is active for all screens.
3. Do not place heavy logic on the main thread.
4. Prepare for JNI/NDK integration in the next phase.

## Runtime Integration Notes
When integrating the real game runtime:
1. Provide an implementation of `GameRuntime` that calls your JNI methods.
2. Pass the implementation into `GameRuntimeProvider`.
3. Use the `SurfaceHolder` callbacks in `GameSurface` to initialize your EGL/Vulkan context.
4. Route Android input events through `NormalizedInput` into your engine's input queue.

## Performance Testing Instructions
1. Navigate to Settings and enable the Developer Performance Overlay.
2. In the `GameScreen`, verify FPS and frame times once the engine is connected.
3. Check `Diagnostics` to ensure the device has sufficient available RAM.

## Known Limitations
- The first build contains a `PlaceholderGameRuntime` which intentionally displays "Runtime unavailable".
- Controller support mapping is currently stubbed and awaits the actual input mapping logic from the native engine.
- Settings are primarily placeholders to demonstrate the intended UI structure.
