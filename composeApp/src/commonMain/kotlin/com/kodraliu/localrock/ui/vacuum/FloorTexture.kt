package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.kodraliu.localrock.shared.vacuum.map.FloorMaterial

/** How a room's floor is drawn on the map, from the material the robot reports for it. */
internal enum class FloorPattern { NONE, WOOD_ALONG_X, WOOD_ALONG_Y, TILE }

private const val PLANK_CELLS = 3
private const val TILE_CELLS = 8

/**
 * Wood grain is 0 (planks left to right) or 90 (up and down) degrees; unreported is drawn as 0.
 */
internal fun floorPattern(materialId: Int?, direction: Int?): FloorPattern = when (materialId?.let { FloorMaterial.fromId(it) }) {
    FloorMaterial.WOOD -> if (direction == 90) FloorPattern.WOOD_ALONG_Y else FloorPattern.WOOD_ALONG_X
    FloorMaterial.TILE -> FloorPattern.TILE
    else -> FloorPattern.NONE
}

/**
 * Shade of the cell at ([x], [y]) against its room's tint, -1 to 1: darkest at plank and tile seams, with a small shift per board.
 */
internal fun textureShade(pattern: FloorPattern, x: Int, y: Int): Float = FLOOR_TEXTURE_STRENGTH * rawTextureShade(pattern, x, y)

private fun rawTextureShade(pattern: FloorPattern, x: Int, y: Int): Float = when (pattern) {
    FloorPattern.NONE -> 0f
    FloorPattern.WOOD_ALONG_Y -> plankShade(x)
    FloorPattern.WOOD_ALONG_X -> plankShade(y)
    FloorPattern.TILE -> {
        if (x % TILE_CELLS == 0 || y % TILE_CELLS == 0) {
            -0.16f
        } else {
            (((x / TILE_CELLS) + (y / TILE_CELLS)) % 2).let { if (it == 0) 0.03f else -0.03f }
        }
    }
}

/** [across] is the cell index across the grain: a plank is [PLANK_CELLS] cells wide. */
private fun plankShade(across: Int): Float {
    val plank = across / PLANK_CELLS
    if (across % PLANK_CELLS == 0) return -0.18f
    // Cheap integer hash so neighbouring planks differ without any state.
    val h = (plank * 73856093) xor (plank * 19349663 ushr 7)
    return ((h and 0xff) / 255f - 0.5f) * 0.14f
}

/** Diagonal hatching for cells the robot has seen carpet on. */
internal fun carpetShade(x: Int, y: Int): Float = CARPET_HATCH_STRENGTH * if ((x + y) % 4 < 2) -0.14f else 0.04f

internal fun shaded(base: Color, shade: Float): Color =
    if (shade < 0f) lerp(base, Color.Black, -shade) else lerp(base, Color.White, shade)
