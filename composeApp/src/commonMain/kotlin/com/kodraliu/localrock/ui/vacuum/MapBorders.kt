package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

// Flat map look, tuned in one place. Widths are in grid cells (a cell is 50 mm, roughly 4 to 6 screen px).
/** Mid-grey line around the whole floor plan. */
internal val MAP_OUTLINE_COLOR = Color(0xFF8A8F93)
internal const val MAP_OUTLINE_WIDTH_CELLS = 0.30f

/** Thinner line where two rooms with different ids touch. */
internal val MAP_ROOM_BORDER_COLOR = Color(0xFF7C8185)
internal const val MAP_ROOM_BORDER_WIDTH_CELLS = 0.22f

// Floor-plan look (maps with block 55): strokes by polygon edge type, colours from the official plugin.
internal val FLOOR_PLAN_OUTER_COLOR = Color(0xFF6B7174)
internal const val FLOOR_PLAN_OUTER_WIDTH_CELLS = 0.35f
internal val FLOOR_PLAN_INNER_COLOR = Color(0xFF999999)
internal const val FLOOR_PLAN_INNER_WIDTH_CELLS = 0.25f
internal val FLOOR_PLAN_DOOR_COLOR = Color(0xFFF2F2F7)
internal const val FLOOR_PLAN_DOOR_WIDTH_CELLS = 1.0f

/** Background behind the map in the detail hero. */
internal val MAP_HERO_BACKGROUND = Color(0xFF000000)

/** The route on a map that is not being cleaned: a thin faint line, no wide band. */
internal val MAP_IDLE_ROUTE_COLOR = Color(0x59FFFFFF)
internal const val MAP_IDLE_ROUTE_WIDTH_CELLS = 0.45f

/** One straight border segment in drawn (Y-flipped) cell coordinates; either x0 == x1 or y0 == y1. */
internal data class BorderRun(val x0: Int, val y0: Int, val x1: Int, val y1: Int)

private fun roomOf(grid: ByteArray, rawIds: ByteArray, i: Int): Int {
    val g = grid[i].toInt() and 0xff
    if (g != 127 && g != 128) return 0
    return (rawIds[i].toInt() ushr 3) and 0x1f
}

/**
 * Edges between adjacent filled cells that belong to different rooms, merged into straight runs in
 * drawn coordinates. Cells with no room id and empty space never produce an edge: the plan's
 * outer boundary is drawn by the outline, not here.
 */
internal fun roomBorderEdges(grid: ByteArray, rawIds: ByteArray, width: Int, height: Int): List<BorderRun> {
    val runs = ArrayList<BorderRun>()
    // Vertical edges: between (x - 1, y) and (x, y), merged down a column of rows.
    for (x in 1 until width) {
        var startY = -1
        for (y in 0..height) {
            val differs = y < height && run {
                val a = roomOf(grid, rawIds, y * width + x - 1)
                val b = roomOf(grid, rawIds, y * width + x)
                a != 0 && b != 0 && a != b
            }
            if (differs && startY < 0) startY = y
            if (!differs && startY >= 0) {
                runs += BorderRun(x, height - y, x, height - startY)
                startY = -1
            }
        }
    }
    // Horizontal edges: between (x, y - 1) and (x, y), merged along a row.
    for (y in 1 until height) {
        var startX = -1
        for (x in 0..width) {
            val differs = x < width && run {
                val a = roomOf(grid, rawIds, (y - 1) * width + x)
                val b = roomOf(grid, rawIds, y * width + x)
                a != 0 && b != 0 && a != b
            }
            if (differs && startX < 0) startX = x
            if (!differs && startX >= 0) {
                runs += BorderRun(startX, height - y, x, height - y)
                startX = -1
            }
        }
    }
    return runs
}

internal fun roomBorderPath(runs: List<BorderRun>): Path = Path().apply {
    for (r in runs) {
        moveTo(r.x0.toFloat(), r.y0.toFloat())
        lineTo(r.x1.toFloat(), r.y1.toFloat())
    }
}

/**
 * The room a wall cell is painted as on the flat map: its own room id when the map gives it one,
 * else the room of a floor cell next to it, else null (not painted).
 */
internal fun flatWallRoom(grid: ByteArray, rawIds: ByteArray, width: Int, height: Int, x: Int, y: Int): Int? {
    val own = (rawIds[y * width + x].toInt() ushr 3) and 0x1f
    if (own != 0) return own
    for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
        val nx = x + dx
        val ny = y + dy
        if (nx !in 0 until width || ny !in 0 until height) continue
        val i = ny * width + nx
        if ((grid[i].toInt() and 0xff) == 128) {
            val id = (rawIds[i].toInt() ushr 3) and 0x1f
            if (id != 0) return id
        }
    }
    return null
}
