package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap

/**
 * The outline of every floor or wall cell, in drawn (Y-flipped) cell coordinates, as closed loops
 * with the stair steps rounded off. Gaps inside the plan come out as loops of their own.
 */
internal fun mapOutlineLoops(map: ParsedMap, smoothing: Int = 2): List<List<Offset>> {
    val w = map.width
    val h = map.height
    fun filled(x: Int, yDrawn: Int): Boolean {
        if (x !in 0 until w || yDrawn !in 0 until h) return false
        val v = map.grid[(h - 1 - yDrawn) * w + x].toInt() and 0xff
        return v == 127 || v == 128
    }
    // Directed unit edges with the filled cell on the right, keyed by where they start.
    val next = HashMap<Long, MutableList<Long>>()
    fun key(x: Int, y: Int) = x.toLong() * (h + 1) + y
    fun edge(x0: Int, y0: Int, x1: Int, y1: Int) {
        next.getOrPut(key(x0, y0)) { mutableListOf() }.add(key(x1, y1))
    }
    for (y in 0 until h) for (x in 0 until w) {
        if (!filled(x, y)) continue
        if (!filled(x, y - 1)) edge(x, y, x + 1, y)
        if (!filled(x + 1, y)) edge(x + 1, y, x + 1, y + 1)
        if (!filled(x, y + 1)) edge(x + 1, y + 1, x, y + 1)
        if (!filled(x - 1, y)) edge(x, y + 1, x, y)
    }
    val loops = ArrayList<List<Offset>>()
    while (next.isNotEmpty()) {
        val start = next.keys.first()
        val points = ArrayList<Long>()
        var at = start
        do {
            points += at
            val out = next[at] ?: break
            val to = out.removeAt(out.lastIndex)
            if (out.isEmpty()) next.remove(at)
            at = to
        } while (at != start)
        if (points.size >= 4) loops += smooth(dropCollinear(points, h), smoothing)
    }
    return loops
}

private fun dropCollinear(points: List<Long>, h: Int): List<Offset> {
    val pts = points.map { Offset((it / (h + 1)).toFloat(), (it % (h + 1)).toFloat()) }
    val kept = ArrayList<Offset>()
    for (i in pts.indices) {
        val a = pts[(i + pts.size - 1) % pts.size]
        val b = pts[i]
        val c = pts[(i + 1) % pts.size]
        if ((b.x - a.x) * (c.y - b.y) != (b.y - a.y) * (c.x - b.x)) kept += b
    }
    return kept
}

/**
 * Corner cutting: each pass replaces a corner with two points part of the way along its sides, up
 * to halfway, which turns a staircase of one-cell steps into a diagonal. A cut is never more than
 * [MAX_CUT_CELLS] from the corner, so a long straight wall keeps its square ends.
 */
private fun smooth(points: List<Offset>, passes: Int): List<Offset> {
    var cur = points
    repeat(passes) {
        val out = ArrayList<Offset>(cur.size * 2)
        for (i in cur.indices) {
            val a = cur[i]
            val b = cur[(i + 1) % cur.size]
            val len = kotlin.math.hypot(b.x - a.x, b.y - a.y)
            if (len == 0f) continue
            val cut = minOf(len * 0.5f, MAX_CUT_CELLS) / len
            out += Offset(a.x + (b.x - a.x) * cut, a.y + (b.y - a.y) * cut)
            out += Offset(b.x - (b.x - a.x) * cut, b.y - (b.y - a.y) * cut)
        }
        cur = out
    }
    return cur
}

private const val MAX_CUT_CELLS = 0.8f

internal fun mapOutlinePath(map: ParsedMap): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    for (loop in mapOutlineLoops(map)) {
        moveTo(loop[0].x, loop[0].y)
        for (i in 1 until loop.size) lineTo(loop[i].x, loop[i].y)
        close()
    }
}
