package com.auracommunityact.missiongtamobile.input

import com.auracommunityact.missiongtamobile.runtime.PlaceholderGameRuntime
import com.auracommunityact.missiongtamobile.ui.game.CharacterState
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class DPadInputTest {

    @Test
    fun testDeadzoneProducesNeutralDirection() {
        val dir = DPadDirection.fromDisplacement(5f, 5f, deadzoneRadius = 15f)
        assertEquals(DPadDirection.NONE, dir)
        assertFalse(dir.isMoving)
        assertEquals(0f, dir.moveX, 0.001f)
        assertEquals(0f, dir.moveY, 0.001f)
    }

    @Test
    fun testCardinalDirections() {
        // Screen coordinates: Y is positive downwards
        val up = DPadDirection.fromDisplacement(0f, -50f, deadzoneRadius = 15f)
        assertEquals(DPadDirection.UP, up)
        assertEquals(0f, up.moveX, 0.001f)
        assertEquals(-1f, up.moveY, 0.001f)

        val down = DPadDirection.fromDisplacement(0f, 50f, deadzoneRadius = 15f)
        assertEquals(DPadDirection.DOWN, down)
        assertEquals(0f, down.moveX, 0.001f)
        assertEquals(1f, down.moveY, 0.001f)

        val left = DPadDirection.fromDisplacement(-50f, 0f, deadzoneRadius = 15f)
        assertEquals(DPadDirection.LEFT, left)
        assertEquals(-1f, left.moveX, 0.001f)
        assertEquals(0f, left.moveY, 0.001f)

        val right = DPadDirection.fromDisplacement(50f, 0f, deadzoneRadius = 15f)
        assertEquals(DPadDirection.RIGHT, right)
        assertEquals(1f, right.moveX, 0.001f)
        assertEquals(0f, right.moveY, 0.001f)
    }

    @Test
    fun testDiagonalDirections() {
        val upRight = DPadDirection.fromDisplacement(40f, -40f, deadzoneRadius = 15f)
        assertEquals(DPadDirection.UP_RIGHT, upRight)
        assertTrue(upRight.moveX > 0)
        assertTrue(upRight.moveY < 0)

        val downRight = DPadDirection.fromDisplacement(40f, 40f, deadzoneRadius = 15f)
        assertEquals(DPadDirection.DOWN_RIGHT, downRight)
        assertTrue(downRight.moveX > 0)
        assertTrue(downRight.moveY > 0)

        val downLeft = DPadDirection.fromDisplacement(-40f, 40f, deadzoneRadius = 15f)
        assertEquals(DPadDirection.DOWN_LEFT, downLeft)
        assertTrue(downLeft.moveX < 0)
        assertTrue(downLeft.moveY > 0)

        val upLeft = DPadDirection.fromDisplacement(-40f, -40f, deadzoneRadius = 15f)
        assertEquals(DPadDirection.UP_LEFT, upLeft)
        assertTrue(upLeft.moveX < 0)
        assertTrue(upLeft.moveY < 0)
    }

    @Test
    fun testDiagonalVectorMagnitudeIsNormalized() {
        for (dir in listOf(DPadDirection.UP_RIGHT, DPadDirection.DOWN_RIGHT, DPadDirection.DOWN_LEFT, DPadDirection.UP_LEFT)) {
            val mag = sqrt((dir.moveX * dir.moveX + dir.moveY * dir.moveY).toDouble()).toFloat()
            assertTrue("Expected magnitude ~1.0, got $mag", abs(mag - 1.0f) < 0.01f)
        }
    }

    @Test
    fun testToNormalizedInput() {
        val input = DPadDirection.UP.toNormalizedInput(action1 = true, action2 = false)
        assertEquals(0f, input.moveX, 0.001f)
        assertEquals(-1f, input.moveY, 0.001f)
        assertTrue(input.action1)
        assertFalse(input.action2)
    }

    @Test
    fun testCharacterStateMovement() {
        val charState = CharacterState(initialX = 1000f, initialY = 1000f)
        val initialX = charState.posX
        val initialY = charState.posY

        // Move Right for 0.5s
        charState.updateMovement(NormalizedInput(moveX = 1f, moveY = 0f), deltaSeconds = 0.5f)
        assertTrue("Character should have moved right", charState.posX > initialX)
        assertEquals(initialY, charState.posY, 0.001f)
        assertTrue(charState.isMoving)
        assertEquals(0f, charState.headingDegrees, 0.001f)

        // Move Up for 0.5s
        val currentX = charState.posX
        charState.updateMovement(NormalizedInput(moveX = 0f, moveY = -1f), deltaSeconds = 0.5f)
        assertEquals(currentX, charState.posX, 0.001f)
        assertTrue("Character should have moved up", charState.posY < initialY)
        assertEquals(-90f, charState.headingDegrees, 0.001f)
    }

    @Test
    fun testPlaceholderGameRuntimeReceivesInput() {
        val runtime = PlaceholderGameRuntime {}
        val testInput = NormalizedInput(moveX = 0.7071f, moveY = -0.7071f, action1 = true)

        runtime.handleInput(testInput)

        assertEquals(0.7071f, runtime.lastInput.moveX, 0.0001f)
        assertEquals(-0.7071f, runtime.lastInput.moveY, 0.0001f)
        assertTrue(runtime.lastInput.action1)
    }
}
