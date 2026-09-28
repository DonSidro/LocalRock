package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Roborock's camera "PIN" on newer models is drawn as a pattern on a 3x3 grid rather than typed.
 * The wire format is still a digit string: the dots are numbered row-major starting at 1
 * (`123` / `456` / `789`), and the robot is sent md5(digits) — see [CameraLiveSession].
 */
private const val GRID = 3
private const val MIN_DOTS = 4

@Composable
fun PatternPinInput(
    onPattern: (String) -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color = Color.White.copy(alpha = 0.35f),
    activeColor: Color = Color.White,
) {
    val selected = remember { mutableStateListOf<Int>() }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    val currentOnPattern by rememberUpdatedState(onPattern)

    Box(modifier.fillMaxWidth().widthIn(max = 280.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    val cellW = size.width / GRID.toFloat()
                    val cellH = size.height / GRID.toFloat()
                    val hitRadius = min(cellW, cellH) * 0.34f
                    val centers = List(GRID * GRID) { i ->
                        Offset((i % GRID + 0.5f) * cellW, (i / GRID + 0.5f) * cellH)
                    }

                    fun select(position: Offset) {
                        val hit = centers.indexOfFirst { (it - position).getDistance() <= hitRadius }
                        if (hit < 0 || hit in selected) return
                        // Dragging straight across a dot picks it up too, as on an Android lock screen.
                        val previous = selected.lastOrNull()
                        if (previous != null) {
                            val rowSum = previous / GRID + hit / GRID
                            val colSum = previous % GRID + hit % GRID
                            if (rowSum % 2 == 0 && colSum % 2 == 0) {
                                val between = (rowSum / 2) * GRID + colSum / 2
                                if (between != previous && between !in selected) selected += between
                            }
                        }
                        selected += hit
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        selected.clear()
                        select(down.position)
                        cursor = down.position
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            select(change.position)
                            cursor = change.position
                            change.consume()
                        }

                        cursor = null
                        if (selected.size >= MIN_DOTS) {
                            currentOnPattern(selected.joinToString("") { (it + 1).toString() })
                        }
                        selected.clear()
                    }
                },
        ) {
            val cellW = size.width / GRID
            val cellH = size.height / GRID
            val centers = List(GRID * GRID) { i ->
                Offset((i % GRID + 0.5f) * cellW, (i / GRID + 0.5f) * cellH)
            }
            val radius = min(cellW, cellH) * 0.12f

            selected.zipWithNext { a, b ->
                drawLine(activeColor, centers[a], centers[b], strokeWidth = radius * 0.5f)
            }
            cursor?.let { end ->
                selected.lastOrNull()?.let { last ->
                    drawLine(activeColor, centers[last], end, strokeWidth = radius * 0.5f)
                }
            }

            centers.forEachIndexed { i, center ->
                val active = i in selected
                drawCircle(if (active) activeColor else dotColor, radius, center)
                if (active) {
                    drawCircle(activeColor, radius * 2.2f, center, style = Stroke(width = radius * 0.35f))
                }
            }
        }
    }
}
