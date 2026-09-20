package com.auracommunityact.missiongtamobile.runtime

import com.auracommunityact.missiongtamobile.input.NormalizedInput
import android.util.Log

class PlaceholderGameRuntime(private val onStatusChange: (GameRuntimeStatus) -> Unit) : GameRuntime {
    
    override var status: GameRuntimeStatus = GameRuntimeStatus.PENDING
        private set(value) {
            field = value
            onStatusChange(value)
        }

    var lastInput: NormalizedInput = NormalizedInput()
        private set

    fun updateStatus(newStatus: GameRuntimeStatus) {
        status = newStatus
    }

    override fun initialize() {
        Log.i("RUNTIME", "Placeholder initialization. Runtime Integration Pending.")
    }

    override fun start() {
        Log.i("RUNTIME", "Placeholder start called.")
        status = GameRuntimeStatus.RUNNING
    }

    override fun pause() {
        Log.i("RUNTIME", "Placeholder pause called.")
        status = GameRuntimeStatus.PAUSED
    }

    override fun resume() {
        Log.i("RUNTIME", "Placeholder resume called.")
        status = GameRuntimeStatus.RUNNING
    }

    override fun stop() {
        Log.i("RUNTIME", "Placeholder stop called.")
        status = GameRuntimeStatus.READY
    }

    override fun shutdown() {
        Log.i("RUNTIME", "Placeholder shutdown called.")
        status = GameRuntimeStatus.PENDING
    }

    override fun onSurfaceCreated() {
        Log.i("RENDERER", "Placeholder surface created.")
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        Log.i("RENDERER", "Placeholder surface changed: $width x $height")
    }

    override fun onSurfaceDestroyed() {
        Log.i("RENDERER", "Placeholder surface destroyed.")
    }

    override fun handleInput(input: NormalizedInput) {
        lastInput = input
        try {
            Log.d("INPUT", "Received input: move=(${input.moveX}, ${input.moveY})")
        } catch (_: Throwable) {}
    }
}
