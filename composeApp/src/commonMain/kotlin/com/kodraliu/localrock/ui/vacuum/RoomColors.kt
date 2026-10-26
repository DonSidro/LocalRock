package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.roomIdAtCell

/** Palette slot per room id, chosen so rooms that share a wall get different colours where possible. */
internal fun roomColorSlots(map: ParsedMap, slots: Int): Map<Int, Int> {
    val neighbours = HashMap<Int, MutableSet<Int>>()
    val w = map.width
    for (idx in map.grid.indices) {
        val a = map.roomIdAtCell(idx) ?: continue
        neighbours.getOrPut(a) { mutableSetOf() }
        val right = if ((idx + 1) % w != 0) map.roomIdAtCell(idx + 1) else null
        val below = map.roomIdAtCell(idx + w)
        for (b in listOf(right, below)) {
            if (b == null || b == a) continue
            neighbours.getOrPut(a) { mutableSetOf() }.add(b)
            neighbours.getOrPut(b) { mutableSetOf() }.add(a)
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
