package com.auracommunityact.missiongtamobile.runtime

import com.auracommunityact.missiongtamobile.input.NormalizedInput
import android.util.Log

class PlaceholderGameRuntime(private val onStatusChange: (GameRuntimeStatus) -> Unit) : GameRuntime {
    
    override var status: GameRuntimeStatus = GameRuntimeStatus.PENDING
        private set(value) {
            field = value
            onStatusChange(value)
        }

    override fun initialize() {
        Log.i("RUNTIME", "Placeholder initialization. Runtime Integration Pending.")
        status = GameRuntimeStatus.PENDING
    }

    override fun start() {
        Log.i("RUNTIME", "Placeholder start called.")
    }

    override fun pause() {
        Log.i("RUNTIME", "Placeholder pause called.")
    }

    override fun resume() {
        Log.i("RUNTIME", "Placeholder resume called.")
    }

    override fun stop() {
        Log.i("RUNTIME", "Placeholder stop called.")
    }

    override fun shutdown() {
        Log.i("RUNTIME", "Placeholder shutdown called.")
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
        // No-op for placeholder
    }
}
