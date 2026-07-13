package com.windowweb.browser.core

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Window positions and dimensions are stored as normalized workspace coordinates.
 * Centralizing the geometry keeps drag-resize, snapping and arrange operations consistent.
 */
data class NormalizedWindowBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

object WindowGeometry {
    const val MIN_WIDTH = 0.22f
    const val MIN_HEIGHT = 0.18f

    fun clamp(
        bounds: NormalizedWindowBounds,
        minWidth: Float = MIN_WIDTH,
        minHeight: Float = MIN_HEIGHT
    ): NormalizedWindowBounds {
        val width = bounds.width.coerceIn(minWidth, 1f)
        val height = bounds.height.coerceIn(minHeight, 1f)
        val x = bounds.x.coerceIn(0f, 1f - width)
        val y = bounds.y.coerceIn(0f, 1f - height)
        return NormalizedWindowBounds(x, y, width, height)
    }

    fun move(bounds: NormalizedWindowBounds, x: Float, y: Float): NormalizedWindowBounds =
        clamp(bounds.copy(x = x, y = y))

    fun resize(
        start: NormalizedWindowBounds,
        edge: ResizeEdge,
        deltaX: Float,
        deltaY: Float,
        minWidth: Float = MIN_WIDTH,
        minHeight: Float = MIN_HEIGHT
    ): NormalizedWindowBounds {
        var left = start.x
        var top = start.y
        var right = start.x + start.width
        var bottom = start.y + start.height

        when (edge) {
            ResizeEdge.LEFT,
            ResizeEdge.TOP_LEFT,
            ResizeEdge.BOTTOM_LEFT -> left += deltaX

            ResizeEdge.RIGHT,
            ResizeEdge.TOP_RIGHT,
            ResizeEdge.BOTTOM_RIGHT -> right += deltaX

            else -> Unit
        }

        when (edge) {
            ResizeEdge.TOP,
            ResizeEdge.TOP_LEFT,
            ResizeEdge.TOP_RIGHT -> top += deltaY

            ResizeEdge.BOTTOM,
            ResizeEdge.BOTTOM_LEFT,
            ResizeEdge.BOTTOM_RIGHT -> bottom += deltaY

            else -> Unit
        }

        left = left.coerceIn(0f, right - minWidth)
        right = right.coerceIn(left + minWidth, 1f)
        top = top.coerceIn(0f, bottom - minHeight)
        bottom = bottom.coerceIn(top + minHeight, 1f)

        return NormalizedWindowBounds(
            x = left,
            y = top,
            width = right - left,
            height = bottom - top
        )
    }

    fun snap(position: SnapPosition): NormalizedWindowBounds = when (position) {
        SnapPosition.LEFT -> NormalizedWindowBounds(0f, 0f, 0.5f, 1f)
        SnapPosition.RIGHT -> NormalizedWindowBounds(0.5f, 0f, 0.5f, 1f)
        SnapPosition.TOP -> NormalizedWindowBounds(0f, 0f, 1f, 0.5f)
        SnapPosition.BOTTOM -> NormalizedWindowBounds(0f, 0.5f, 1f, 0.5f)
        SnapPosition.TOP_LEFT -> NormalizedWindowBounds(0f, 0f, 0.5f, 0.5f)
        SnapPosition.TOP_RIGHT -> NormalizedWindowBounds(0.5f, 0f, 0.5f, 0.5f)
        SnapPosition.BOTTOM_LEFT -> NormalizedWindowBounds(0f, 0.5f, 0.5f, 0.5f)
        SnapPosition.BOTTOM_RIGHT -> NormalizedWindowBounds(0.5f, 0.5f, 0.5f, 0.5f)
        SnapPosition.LEFT_THIRD -> NormalizedWindowBounds(0f, 0f, 1f / 3f, 1f)
        SnapPosition.CENTER_THIRD -> NormalizedWindowBounds(1f / 3f, 0f, 1f / 3f, 1f)
        SnapPosition.RIGHT_THIRD -> NormalizedWindowBounds(2f / 3f, 0f, 1f / 3f, 1f)
        SnapPosition.PICTURE_IN_PICTURE -> NormalizedWindowBounds(0.58f, 0.56f, 0.38f, 0.34f)
        SnapPosition.MAXIMIZE -> NormalizedWindowBounds(0f, 0f, 1f, 1f)
        SnapPosition.FLOATING -> NormalizedWindowBounds(0.08f, 0.08f, 0.84f, 0.76f)
    }

    fun tiled(count: Int): List<NormalizedWindowBounds> {
        if (count <= 0) return emptyList()
        val columns = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(count.toDouble() / columns).toInt().coerceAtLeast(1)
        val width = 1f / columns
        val height = 1f / rows

        return List(count) { index ->
            val column = index % columns
            val row = index / columns
            NormalizedWindowBounds(
                x = column * width,
                y = row * height,
                width = width,
                height = height
            )
        }
    }

    fun cascaded(count: Int): List<NormalizedWindowBounds> {
        if (count <= 0) return emptyList()
        return List(count) { index ->
            val offset = (index % 7) * 0.035f
            clamp(
                NormalizedWindowBounds(
                    x = 0.04f + offset,
                    y = 0.04f + offset,
                    width = 0.78f,
                    height = 0.72f
                )
            )
        }
    }

    fun stacked(count: Int): List<NormalizedWindowBounds> {
        if (count <= 0) return emptyList()
        return List(count) { index ->
            val offset = (index % 8) * 0.018f
            clamp(
                NormalizedWindowBounds(
                    x = 0.08f + offset,
                    y = 0.08f + offset,
                    width = 0.82f,
                    height = 0.78f
                )
            )
        }
    }
}
