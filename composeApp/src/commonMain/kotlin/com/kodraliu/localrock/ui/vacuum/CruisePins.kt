package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap

/** Navy of the official app's numbered cruise pins. */
internal val CRUISE_PIN_COLOR = Color(0xFF1B3B6F)

/** Pin head radius and the height from the tip to the head's centre, in dp. */
internal const val CRUISE_PIN_RADIUS_DP = 11f
internal const val CRUISE_PIN_STEM_DP = 17f

/** Where the pin head's centre sits for a tip at [tip], in px, given the stem length in px. */
internal fun cruisePinHead(tip: Offset, stemPx: Float): Offset = Offset(tip.x, tip.y - stemPx)

/**
 * One cruise point as the official app draws it: a navy teardrop whose tip touches [tip] (screen px),
 * with [number] in white. Without a [measurer] the head shows a white dot instead of the number.
 */
fun DrawScope.drawCruisePin(tip: Offset, number: Int, measurer: TextMeasurer?) {
    val r = CRUISE_PIN_RADIUS_DP.dp.toPx()
    val head = cruisePinHead(tip, CRUISE_PIN_STEM_DP.dp.toPx())
    val edge = 1.5.dp.toPx()
    // A white edge first, then the navy pin: a round head and a point down to the tip.
    drawPin(head, tip, r + edge, Color.White)
    drawPin(head, Offset(tip.x, tip.y - edge), r, CRUISE_PIN_COLOR)
    if (measurer != null) {
        val label = measurer.measure("$number", TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        drawText(label, topLeft = Offset(head.x - label.size.width / 2f, head.y - label.size.height / 2f))
    } else {
        drawCircle(Color.White, radius = r * 0.32f, center = head)
    }
}

private fun DrawScope.drawPin(head: Offset, tip: Offset, r: Float, color: Color) {
    drawCircle(color, radius = r, center = head)
    // The point leaves the head at 45 degrees either side of straight down.
    val side = r * 0.7071f
    val point = Path().apply {
        moveTo(head.x - side, head.y + side)
        lineTo(tip.x, tip.y)
        lineTo(head.x + side, head.y + side)
        close()
    }
    drawPath(point, color)
}

/** Each cruise point of [map] in the order the robot visited them, as a pin on screen through [toScreen]. */
internal fun DrawScope.drawCruisePins(map: ParsedMap, measurer: TextMeasurer?, toScreen: (cell: Offset) -> Offset) {
    map.patrolPoints.forEachIndexed { i, p ->
        val cell = map.mmToCell(p.x, p.y)
        if (cell.x !in 0f..map.width.toFloat() || cell.y !in 0f..map.height.toFloat()) return@forEachIndexed
        drawCruisePin(toScreen(cell), i + 1, measurer)
    }
}
