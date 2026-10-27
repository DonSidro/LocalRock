package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.roomIdAtCell
import kotlin.math.floor

/** Palette slot of the colour a room gets when the app cannot give it one of its four. */
internal const val NO_COLOUR_SLOT = 4

/**
 * Palette slot per room id: 0 blue, 1 salmon, 2 teal, 3 yellow, [NO_COLOUR_SLOT] grey.
 *
 * A legacy map is coloured the way the Roborock app does it (its `getNeighboursInfo` and
 * `getColorData`): rooms are visited by how many rooms touch them, most first, and each takes the
 * lowest of four colours that no already coloured neighbour has, the biggest room taking the first.
 * Colours left unused are then shared out so all four appear. Walls carry their room's id in the
 * map, so two rooms touch where their floor or wall cells do.
 */
internal fun roomColorSlots(map: ParsedMap, slots: Int): Map<Int, Int> {
    val raw = map.originalGrid ?: return lookAcrossColorSlots(map, slots)
    return appRoomColours(raw, map.width, map.height).mapValues { (_, colour) -> if (colour in 1..4) colour - 1 else NO_COLOUR_SLOT }
}

private const val ROOM_IDS = 32
private const val COLOURS = 4

internal fun appRoomColours(raw: ByteArray, width: Int, height: Int): Map<Int, Int> {
    val segment = IntArray(raw.size) { i ->
        val v = raw[i].toInt() and 0xff
        if (v and 0x07 != 0) v ushr 3 else 0
    }
    val exists = BooleanArray(ROOM_IDS)
    val pixels = IntArray(ROOM_IDS)
    val touches = Array(ROOM_IDS) { BooleanArray(ROOM_IDS) }
    for (y in 0 until height) {
        for (x in 0 until width) {
            val i = y * width + x
            val id = segment[i]
            if (id == 0) continue
            exists[id] = true
            pixels[id]++
            if (y > 0) {
                val up = segment[i - width]
                if (up != 0 && up != id) { touches[id][up] = true; touches[up][id] = true }
            }
            if (x > 0) {
                val left = segment[i - 1]
                if (left != 0 && left != id) { touches[id][left] = true; touches[left][id] = true }
            }
        }
    }
    return colourRooms(exists, pixels, touches)
}

/** The colouring step on its own: which rooms exist, how many pixels each has, and which touch which. */
internal fun colourRooms(exists: BooleanArray, pixels: IntArray, touches: Array<BooleanArray>): Map<Int, Int> {
    val degree = IntArray(ROOM_IDS) { id -> (1 until ROOM_IDS).count { it != id && touches[id][it] } }
    val order = (1 until ROOM_IDS).sortedByDescending { degree[it] }
    var biggest = 0
    for (id in 0 until ROOM_IDS) if (pixels[id] > pixels[biggest]) biggest = id

    val colour = IntArray(ROOM_IDS)
    val buckets = List(COLOURS) { mutableListOf<Int>() }
    if (biggest != 0) {
        colour[biggest] = 1
        buckets[0].add(biggest)
    }
    for (room in order) {
        if (room == biggest || !exists[room]) continue
        val used = BooleanArray(COLOURS + 1)
        for (other in order) if (touches[other][room] && colour[other] != 0) used[colour[other]] = true
        var c = 1
        while (c <= COLOURS && used[c]) c++
        if (c <= COLOURS) {
            colour[room] = c
            buckets[c - 1].add(room)
        }
    }
    // A colour nobody got takes half of the rooms of an earlier one, so all four colours show.
    for (i in 0 until COLOURS) {
        if (buckets[i].isNotEmpty()) continue
        val source = floor((i + 1) / 2.0).toInt() - 1
        if (source < 0 || buckets[source].size <= 1) continue
        val half = buckets[source].size / 2.0
        var k = buckets[source].size - 1
        var moved = 0
        while (k >= half) {
            buckets[i].add(buckets[source][k])
            moved++
            k--
        }
        repeat(moved) { buckets[source].removeAt(buckets[source].lastIndex) }
    }
    var biggestBucket = 0
    var emptyBucket = -1
    var most = 0
    for (i in 0 until COLOURS) {
        if (buckets[i].size > most) { most = buckets[i].size; biggestBucket = i }
        if (buckets[i].isEmpty()) emptyBucket = i
    }
    if (buckets[biggestBucket].size >= 2 && emptyBucket != -1 && buckets[biggestBucket].size > buckets[emptyBucket].size) {
        while (buckets[biggestBucket].size > buckets[emptyBucket].size) {
            buckets[emptyBucket].add(buckets[biggestBucket].last())
            buckets[biggestBucket].removeAt(buckets[biggestBucket].lastIndex)
        }
    }
    val result = HashMap<Int, Int>()
    for (id in 1 until ROOM_IDS) if (exists[id]) result[id] = 0
    for ((i, bucket) in buckets.withIndex()) for (room in bucket) result[room] = i + 1
    return result
}

private const val LOOK_ACROSS_CELLS = 6

/** For maps with no raw segment bytes: rooms a short way apart count as touching, then a greedy colouring. */
private fun lookAcrossColorSlots(map: ParsedMap, slots: Int): Map<Int, Int> {
    val neighbours = HashMap<Int, MutableSet<Int>>()
    val w = map.width
    for (idx in map.grid.indices) {
        val a = map.roomIdAtCell(idx) ?: continue
        neighbours.getOrPut(a) { mutableSetOf() }
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
