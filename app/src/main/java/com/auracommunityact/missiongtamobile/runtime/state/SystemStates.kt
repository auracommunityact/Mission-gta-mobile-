package com.auracommunityact.missiongtamobile.runtime.state

/**
 * Real status states for the runtime engine.
 * Never fabricated; reflects the actual state of the runtime subsystem.
 */
enum class RuntimeStatus(val displayName: String) {
    CHECKING("CHECKING"),
    READY("READY"),
    ERROR("ERROR")
}

/**
 * Real status states for the user-selected game data.
 */
enum class GameDataStatus(val displayName: String) {
    NOT_SELECTED("NOT SELECTED"),
    CHECKING("CHECKING"),
    READY("READY"),
    INVALID("INVALID"),
    ERROR("ERROR")
}

/**
 * Real status states for GPU drivers (Turnip / System).
 */
enum class DriverStatus(val displayName: String) {
    DETECTING("DETECTING"),
    READY("READY"),
    UNSUPPORTED("UNSUPPORTED"),
    ERROR("ERROR")
}

/**
 * Real status states for the Vulkan graphics renderer.
 */
enum class RendererStatus(val displayName: String) {
    INITIALIZING("INITIALIZING"),
    READY("READY"),
    ERROR("ERROR")
}

/**
 * Real status states for the game execution process.
 */
enum class GameStatus(val displayName: String) {
    STOPPED("STOPPED"),
    STARTING("STARTING"),
    RUNNING("RUNNING"),
    CRASHED("CRASHED")
}
