package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.roomIdAtCell

private const val LOOK_ACROSS_CELLS = 6

private val UNNAMED_ROOM = Regex("Room \\d+")

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
    // A room with no name of its own is yellow in the Roborock app, and no named room is.
    val unnamed = map.rooms.filter { UNNAMED_ROOM.matches(it.name) && it.id in neighbours }.map { it.id }.toSet()
    val reserved = if (unnamed.isNotEmpty()) slots - 1 else -1
    for (room in unnamed) slotOf[room] = reserved
    for (room in neighbours.keys.filter { it !in unnamed }.sortedWith(compareByDescending<Int> { neighbours[it]!!.size }.thenBy { it })) {
        val taken = neighbours[room]!!.mapNotNull { slotOf[it] }
        val free = (0 until slots).firstOrNull { it !in taken && it != reserved }
            ?: (0 until slots).firstOrNull { it !in taken }
        slotOf[room] = free ?: (0 until slots).minBy { s -> taken.count { it == s } }
    }
    return slotOf
}
