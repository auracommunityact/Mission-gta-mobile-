package com.auracommunityact.missiongtamobile.input

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Represents the 8 possible movement directions plus neutral state on the D-Pad.
 */
enum class DPadDirection(
    val label: String,
    val moveX: Float,
    val moveY: Float,
    val angleDegrees: Float?
) {
    NONE("Neutral", 0f, 0f, null),
    UP("Up", 0f, -1f, 270f),
    UP_RIGHT("Up-Right", 0.7071f, -0.7071f, 315f),
    RIGHT("Right", 1f, 0f, 0f),
    DOWN_RIGHT("Down-Right", 0.7071f, 0.7071f, 45f),
    DOWN("Down", 0f, 1f, 90f),
    DOWN_LEFT("Down-Left", -0.7071f, 0.7071f, 135f),
    LEFT("Left", -1f, 0f, 180f),
    UP_LEFT("Up-Left", -0.7071f, -0.7071f, 225f);

    val isMoving: Boolean
        get() = this != NONE

    fun toNormalizedInput(
        action1: Boolean = false,
        action2: Boolean = false,
        action3: Boolean = false,
        pause: Boolean = false
    ): NormalizedInput {
        return NormalizedInput(
            moveX = moveX,
            moveY = moveY,
            action1 = action1,
            action2 = action2,
            action3 = action3,
            pause = pause
        )
    }

    companion object {
        private const val SQRT2_INV = 0.70710678f

        /**
         * Resolves a directional vector (dx, dy) relative to D-Pad center into a [DPadDirection].
         * @param dx horizontal displacement from center
         * @param dy vertical displacement from center (positive downwards in screen coords)
         * @param deadzoneRadius minimum displacement required before triggering movement
         */
        fun fromDisplacement(dx: Float, dy: Float, deadzoneRadius: Float = 16f): DPadDirection {
            val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (dist < deadzoneRadius) return NONE

            // atan2 returns angle in radians [-PI, PI] where 0 is (1, 0)
            var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (angleDeg < 0) {
                angleDeg += 360f
            }

            // 8 sectors of 45 degrees centered on the primary axes
            return when {
                angleDeg in 337.5f..360f || angleDeg in 0.0f..22.5f -> RIGHT
                angleDeg in 22.5f..67.5f -> DOWN_RIGHT
                angleDeg in 67.5f..112.5f -> DOWN
                angleDeg in 112.5f..157.5f -> DOWN_LEFT
                angleDeg in 157.5f..202.5f -> LEFT
                angleDeg in 202.5f..247.5f -> UP_LEFT
                angleDeg in 247.5f..292.5f -> UP
                angleDeg in 292.5f..337.5f -> UP_RIGHT
                else -> NONE
            }
        }
    }
}
