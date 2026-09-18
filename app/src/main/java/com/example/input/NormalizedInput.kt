package com.example.input

data class NormalizedInput(
    val moveX: Float = 0f,
    val moveY: Float = 0f,
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    val action1: Boolean = false,
    val action2: Boolean = false,
    val action3: Boolean = false,
    val pause: Boolean = false
)
