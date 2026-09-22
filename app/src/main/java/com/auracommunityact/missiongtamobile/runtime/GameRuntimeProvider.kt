package com.auracommunityact.missiongtamobile.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GameRuntimeProvider {
    private val _status = MutableStateFlow(GameRuntimeStatus.PENDING)
    val status: StateFlow<GameRuntimeStatus> = _status.asStateFlow()

    var runtimeManager: GameRuntimeManager? = null
        private set

    private var activeRuntime: GameRuntime? = null

    init {
        // Fallback default runtime
        activeRuntime = PlaceholderGameRuntime { newStatus ->
            _status.value = newStatus
        }
    }

    fun setRuntimeManager(manager: GameRuntimeManager) {
        runtimeManager = manager
        activeRuntime = manager
        _status.value = manager.status
    }

    fun getRuntime(): GameRuntime? = activeRuntime

    fun updateStatus(newStatus: GameRuntimeStatus) {
        _status.value = newStatus
        (activeRuntime as? PlaceholderGameRuntime)?.updateStatus(newStatus)
    }
}
