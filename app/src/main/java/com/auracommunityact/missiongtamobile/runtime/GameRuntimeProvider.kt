package com.auracommunityact.missiongtamobile.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GameRuntimeProvider {
    private val _status = MutableStateFlow(GameRuntimeStatus.PENDING)
    val status: StateFlow<GameRuntimeStatus> = _status.asStateFlow()

    private var activeRuntime: GameRuntime? = null

    init {
        // First build placeholder
        activeRuntime = PlaceholderGameRuntime { newStatus ->
            _status.value = newStatus
        }
    }

    fun getRuntime(): GameRuntime? = activeRuntime

    fun updateStatus(newStatus: GameRuntimeStatus) {
        _status.value = newStatus
        (activeRuntime as? PlaceholderGameRuntime)?.updateStatus(newStatus)
    }
}
