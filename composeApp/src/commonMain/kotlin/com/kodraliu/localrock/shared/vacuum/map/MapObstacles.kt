package com.kodraliu.localrock.shared.vacuum.map

/**
 * An obstacle the robot recognised, from legacy map blocks 13-16 (robot millimetres).
 *
 * [photoId] names the photo `get_photo` returns for it; null when the robot kept none.
 * [confidence] is 0-1, null when the record has no confidence fields.
 */
data class MapObstacle(
    val x: Int,
    val y: Int,
    val type: Int?,
    val confidence: Float?,
    val photoId: String?,
    /** From the "ignored" blocks (14 and 16): obstacles the user told the robot to stop avoiding. */
    val ignored: Boolean,
) {
    /** A readable name for [type], or null for a type no parser has a name for. */
    val typeName: String? get() = type?.let { OBSTACLE_TYPE_NAMES[it] }
}

/**
 * Parses one obstacle block. Records are evenly sized and the size differs by model: 28 bytes on an
 * S8 Pro Ultra, 36 on a Saros 20 Neo (#36). x and y are u16 at 0 and 2, the type u16 at 4, the
 * confidence u16/u16 at 6 and 8, and records of 28 bytes or more hold the photo id as up to 16 ASCII
 * characters from byte 12, zero padded.
 */
internal fun parseObstacles(bytes: ByteArray, body: Int, count: Int, dataLen: Int, ignored: Boolean): List<MapObstacle> {
    if (count <= 0 || dataLen <= 0) return emptyList()
    val size = dataLen / count
    if (size < 4) return emptyList()
    return List(count) { i ->
        val p = body + i * size
        // vacuum-map-parser-roborock reads the percentage as u1 * 10 / u2 (8505 and 1001 make 85 %).
        val confidence = if (size >= 10) {
            val scale = u16le(bytes, p + 8)
            if (scale == 0) 0f else u16le(bytes, p + 6) / (scale * 10f)
        } else null
        MapObstacle(
            x = u16le(bytes, p),
            y = u16le(bytes, p + 2),
            type = if (size >= 6) u16le(bytes, p + 4) else null,
            confidence = confidence,
            photoId = if (size >= 28) photoIdAt(bytes, p + 12) else null,
            ignored = ignored,
        )
    }
}

private fun photoIdAt(bytes: ByteArray, start: Int): String? {
    var end = start
    while (end < start + 16 && bytes[end] != 0.toByte()) end++
    if (end == start) return null
    val text = bytes.decodeToString(start, end)
    return text.takeIf { id -> id.all { it in '!'..'~' } }
}

private fun u16le(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8)

/**
 * The robot sends only a number; these names are the ones vacuum-map-parser-roborock
 * (KNOWN_OBSTACLE_TYPES) uses. Types missing here have no known name.
 */
private val OBSTACLE_TYPE_NAMES = mapOf(
    0 to "Cable",
    1 to "Pet waste",
    2 to "Shoes",
    3 to "Poop",
    4 to "Pedestal",
    5 to "Extension cord",
    9 to "Weighing scale",
    10 to "Clothes",
    25 to "Dustpan",
    26 to "Furniture with a crossbar",
    27 to "Furniture with a crossbar",
    34 to "Clothes",
    48 to "Cable",
    49 to "Pet",
    50 to "Pet",
    51 to "Fabric or paper ball",
)
