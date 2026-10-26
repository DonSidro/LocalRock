package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.roomIdAtCell

private const val LOOK_ACROSS_CELLS = 6

/** Palette slot per room id, chosen so rooms that share a wall get different colours where possible. */
internal fun roomColorSlots(map: ParsedMap, slots: Int): Map<Int, Int> {
    val neighbours = HashMap<Int, MutableSet<Int>>()
    val w = map.width
    for (idx in map.grid.indices) {
        val a = map.roomIdAtCell(idx) ?: continue
        neighbours.getOrPut(a) { mutableSetOf() }
        // Rooms are split by a wall a cell or two thick, so look across it to the next room cell.
        for (stride in intArrayOf(1, w)) {
            for (step in 1..LOOK_ACROSS_CELLS) {
                val at = idx + step * stride
                if (stride == 1 && at / w != idx / w) break
                val b = map.roomIdAtCell(at) ?: continue
                if (b != a) {
                    neighbours.getOrPut(a) { mutableSetOf() }.add(b)
                    neighbours.getOrPut(b) { mutableSetOf() }.add(a)
                }
                break
            }
        }
    }
    val slotOf = HashMap<Int, Int>()
    for (room in neighbours.keys.sortedWith(compareByDescending<Int> { neighbours[it]!!.size }.thenBy { it })) {
        val taken = neighbours[room]!!.mapNotNull { slotOf[it] }
        val free = (0 until slots).firstOrNull { it !in taken }
        slotOf[room] = free ?: (0 until slots).minBy { s -> taken.count { it == s } }
    }
    return slotOf
}
