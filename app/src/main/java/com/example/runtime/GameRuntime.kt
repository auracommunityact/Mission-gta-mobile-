package com.example.runtime

import com.example.input.NormalizedInput

interface GameRuntime {
    val status: GameRuntimeStatus

    fun initialize()
    fun start()
    fun pause()
    fun resume()
    fun stop()
    fun shutdown()
    
    fun onSurfaceCreated()
    fun onSurfaceChanged(width: Int, height: Int)
    fun onSurfaceDestroyed()
    
    fun handleInput(input: NormalizedInput)
}
