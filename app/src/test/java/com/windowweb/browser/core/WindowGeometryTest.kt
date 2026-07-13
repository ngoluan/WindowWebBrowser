package com.windowweb.browser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowGeometryTest {
    @Test
    fun resizeRightClampsToWorkspace() {
        val start = NormalizedWindowBounds(0.2f, 0.2f, 0.6f, 0.5f)
        val resized = WindowGeometry.resize(start, ResizeEdge.RIGHT, 0.5f, 0f)

        assertEquals(1f, resized.x + resized.width, 0.0001f)
    }

    @Test
    fun resizeLeftHonoursMinimumWidth() {
        val start = NormalizedWindowBounds(0.2f, 0.2f, 0.4f, 0.5f)
        val resized = WindowGeometry.resize(start, ResizeEdge.LEFT, 0.5f, 0f)

        assertTrue(resized.width >= WindowGeometry.MIN_WIDTH)
    }

    @Test
    fun tiledReturnsRequestedCountInsideWorkspace() {
        val result = WindowGeometry.tiled(5)

        assertEquals(5, result.size)
        assertTrue(result.all { it.x >= 0f && it.y >= 0f && it.x + it.width <= 1.0001f && it.y + it.height <= 1.0001f })
    }
}
