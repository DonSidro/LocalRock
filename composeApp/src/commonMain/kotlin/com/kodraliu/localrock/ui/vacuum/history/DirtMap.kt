package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.ui.strings.SecondaryStrings

// Dirt Map of a finished run, as the official CleanRecordDetailPage draws it from the carpet block (17).
internal const val DIRT_OUTSIDE: Byte = 0
internal const val DIRT_WALL: Byte = 1
internal const val DIRT_FLOOR: Byte = 2
internal const val DIRT_LIGHT: Byte = 3 // bit 2 ("dirtyClean"), drawn in dirtyMopBg
internal const val DIRT_DEEP: Byte = 4 // bit 1 ("dirty"), drawn in dirtyBg

/** Dirt class of one block-17 byte; cells marked 1 or 2 in bits 3-4 are skipped, as the plugin does. */
internal fun dirtLevelOf(value: Int): Byte {
    val marked = (value ushr 3) and 3
    if (marked == 1 || marked == 2) return DIRT_OUTSIDE
    return when {
        value and 2 != 0 -> DIRT_DEEP
        value and 4 != 0 -> DIRT_LIGHT
        else -> DIRT_OUTSIDE
    }
}

/** Grid-sized cell classes (row 0 is the bottom, like [ParsedMap.grid]) and the crop that holds the plan. */
internal class DirtLayer(
    val width: Int,
    val height: Int,
    val cells: ByteArray,
    val left: Int,
    val bottom: Int,
    val right: Int,
    val top: Int,
) {
    val cropWidth get() = right - left + 1
    val cropHeight get() = top - bottom + 1
}

/** The run's dirt layer, or null when the map has no block 17 or no cell carries a dirt bit. */
internal fun ParsedMap.dirtLayer(): DirtLayer? {
    val mask = carpetMask ?: return null
    val n = width * height
    if (n == 0 || mask.size != n || grid.size != n) return null
    val cells = ByteArray(n)
    var dirt = false
    var minX = width
    var maxX = -1
    var minY = height
    var maxY = -1
    for (i in 0 until n) {
        val level = dirtLevelOf(mask[i].toInt() and 0xff)
        val cls = when {
            level != DIRT_OUTSIDE -> level.also { dirt = true }
            grid[i] == 127.toByte() -> DIRT_WALL
            grid[i] == 128.toByte() -> DIRT_FLOOR
            else -> DIRT_OUTSIDE
        }
        cells[i] = cls
        if (cls != DIRT_OUTSIDE) {
            val x = i % width
            val y = i / width
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    if (!dirt) return null
    return DirtLayer(width, height, cells, minX, minY, maxX, maxY)
}

/** The official toggle shows only with the DirtyReplenishClean bit and a dirt image that is not empty. */
internal fun showsDirtMapToggle(supported: Boolean, layer: DirtLayer?): Boolean = supported && layer != null

/** Colours from the plugin theme (dirtyBg, dirtyMopBg); floor and wall greys measured on the dark screenshot. */
internal data class DirtPalette(val floor: Color, val wall: Color, val light: Color, val deep: Color)

internal fun dirtPalette(dark: Boolean): DirtPalette = if (dark) {
    DirtPalette(floor = Color(0xFF1A1A1A), wall = Color(0xFF3C3C3C), light = Color(0xFF4E6886), deep = Color(0xFF193764))
} else {
    // Light floor and wall are the plugin's PNG palette entries 1 and 42 (not seen on a phone).
    DirtPalette(floor = Color(0xFFFFFFFF), wall = Color(0xFF6B7174), light = Color(0xFFCDE3FD), deep = Color(0xFF638DCC))
}

private fun renderDirt(layer: DirtLayer, palette: DirtPalette): ImageBitmap {
    val bitmap = ImageBitmap(layer.cropWidth, layer.cropHeight)
    val canvas = Canvas(bitmap)
    val paints = arrayOf(null, palette.wall, palette.floor, palette.light, palette.deep).map { c -> c?.let { Paint().apply { color = it } } }
    for (y in layer.bottom..layer.top) {
        val drawY = (layer.top - y).toFloat()
        var x = layer.left
        while (x <= layer.right) {
            val cls = layer.cells[y * layer.width + x]
            var end = x + 1
            while (end <= layer.right && layer.cells[y * layer.width + end] == cls) end++
            paints[cls.toInt()]?.let { canvas.drawRect(Rect((x - layer.left).toFloat(), drawY, (end - layer.left).toFloat(), drawY + 1f), it) }
            x = end
        }
    }
    return bitmap
}

/** The flat dirt image, framed, turned and zoomed exactly like the route view of [map] and clipped to its plan bounds. */
@Composable
internal fun DirtMapImage(
    layer: DirtLayer,
    map: ParsedMap,
    viewport: LiveMapViewport,
    rotationDegrees: Int,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bitmap = remember(layer, dark) { renderDirt(layer, dirtPalette(dark)) }
    val bounds = remember(map) { map.contentBoundsNorm() }
    Canvas(modifier.clipToBounds()) {
        // One layer pixel per cell; VacuumMapImage's supersampling does not change the framing.
        val bw = map.width.toFloat()
        val bh = map.height.toFloat()
        val yaw = rotationDegrees.toFloat()
        val fit = fitScale(size.width, size.height, bounds.width * bw, bounds.height * bh, yaw, squash = 1f, freeTurning = false)
        val view = framedView(
            canvasW = size.width, canvasH = size.height,
            centerX = (bounds.left + bounds.right) / 2f * bw, centerY = (bounds.top + bounds.bottom) / 2f * bh,
            fit = fit, zoom = viewport.zoom, pan = viewport.pan, yawDegrees = yaw, squash = 1f,
        )
        withTransform({ transform(view.layerMatrix(0f)) }) {
            clipRect(bounds.left * bw, bounds.top * bh, bounds.right * bw, bounds.bottom * bh) {
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(layer.left, layer.height - 1 - layer.top),
                    dstSize = IntSize(layer.cropWidth, layer.cropHeight),
                    filterQuality = FilterQuality.None,
                )
            }
        }
    }
}

/** Pill under the map, bottom right: Dirt Level legend (dirt mode only) and the Cleaning Route / Dirt Map switch. */
@Composable
internal fun DirtMapSwitch(dirtMode: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val palette = dirtPalette(MaterialTheme.colorScheme.surface.luminance() < 0.5f)
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dirtMode) {
                Row(
                    Modifier.padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(SecondaryStrings.DIRT_LEVEL, style = MaterialTheme.typography.labelMedium)
                    LegendChip(palette.light)
                    LegendChip(palette.deep)
                }
            }
            IconButton(onClick = onToggle) {
                Icon(Icons.Default.Layers, if (dirtMode) SecondaryStrings.CLEANING_ROUTE else SecondaryStrings.DIRT_MAP)
            }
        }
    }
}

@Composable
private fun LegendChip(color: Color) {
    Box(
        Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
    )
}
