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
    /** The official name for [type]; "Obstacle" for one the official app has no name for. */
    val typeName: String get() = obstacleType(type).name
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

/** The icon an obstacle type gets on the map (official obstacle_new_p* artwork, redrawn). */
enum class ObstacleKind {
    WIRE, PET_WASTE, FOOTWEAR, PEDESTAL, POWER_STRIP, SCALE, FABRIC, OBSTACLE, DUSTPAN, FURNITURE,
    CORDS, PET, FABRIC_BALL, MIRROR, GREENERY, HANGER, PLASTIC_BAG, CARRYOUT_BAG, CARD, UNKNOWN,
}

/** One known type: official name (map_object_name_*), description (map_object_desc_*) and icon. */
data class ObstacleType(val name: String, val description: String, val kind: ObstacleKind)

private const val GENERAL_DESC = "Intelligently recognizes and avoids obstacles that may block its way."
private val PEDESTAL = ObstacleType("Pedestal", "Pedestal avoided preventing entrapment.", ObstacleKind.PEDESTAL)
private val FABRIC = ObstacleType("Fabric", "Fabric recognized, preventing tangling and entrapment.", ObstacleKind.FABRIC)
private val GENERAL = ObstacleType("Obstacle", GENERAL_DESC, ObstacleKind.OBSTACLE)
private val CROSSBAR = ObstacleType("Easily trapped furniture", "Furniture with a crossbar, i.e., cantilever chair, avoided preventing entrapment.", ObstacleKind.FURNITURE)
private val PET = ObstacleType("Pet", "Pets recognized, preventing collision and disturbance.", ObstacleKind.PET)
private val FABRIC_BALL = ObstacleType("Fabric/paper balls", "Fabric/paper balls recognized, preventing tangling and entrapment.", ObstacleKind.FABRIC_BALL)

/** The official type table (p223 names L296040, icons L459020); other types read as a plain obstacle. */
private val OBSTACLE_TYPES = mapOf(
    0 to ObstacleType("Wire", "Cord/Wire avoided preventing tangling. Long, thin cords may still become tangled.", ObstacleKind.WIRE),
    1 to ObstacleType("Pet Waste", "Pet waste avoided preventing mess.", ObstacleKind.PET_WASTE),
    2 to ObstacleType("Footwear", "Shoes/slippers avoided preventing entrapment.", ObstacleKind.FOOTWEAR),
    3 to PEDESTAL,
    4 to PEDESTAL,
    5 to ObstacleType("Power Strip", "Power strip avoided preventing entrapment.", ObstacleKind.POWER_STRIP),
    9 to ObstacleType("Scale", "Scale avoided preventing entrapment.", ObstacleKind.SCALE),
    10 to FABRIC,
    18 to GENERAL,
    25 to ObstacleType("Dustpan", "Dustpan or similar objects avoided preventing entrapment.", ObstacleKind.DUSTPAN),
    26 to CROSSBAR,
    27 to CROSSBAR,
    34 to FABRIC,
    42 to GENERAL,
    48 to ObstacleType("Cords/wires", "Cords/wires avoided, preventing tangling. Long, thin cords may still be tangled.", ObstacleKind.CORDS),
    49 to PET,
    50 to PET,
    51 to FABRIC_BALL,
    54 to ObstacleType("Stand Mirror", "The robot recognizes stand mirrors, turns to avoid collisions in advance.", ObstacleKind.MIRROR),
    65 to ObstacleType("Greenery", "The robot detected greenery and turned to avoid hitting it.", ObstacleKind.GREENERY),
    66 to FABRIC_BALL,
    67 to ObstacleType("Hanger", "The robot detected a hanger and turned to avoid getting stuck.", ObstacleKind.HANGER),
    69 to ObstacleType("Plastic bag", "The robot detected a plastic bag and turned to avoid entanglement.", ObstacleKind.PLASTIC_BAG),
    70 to ObstacleType("Carryout bag", "The robot detected a carryout bag and turned to avoid entanglement.", ObstacleKind.CARRYOUT_BAG),
    81 to ObstacleType("Card", "Card avoided to prevent card being sucked in and causing a jam.", ObstacleKind.CARD),
)

/** The type's official entry, or a plain obstacle with the "unknown" icon. */
fun obstacleType(type: Int?): ObstacleType =
    type?.let { OBSTACLE_TYPES[it] } ?: ObstacleType("Obstacle", GENERAL_DESC, ObstacleKind.UNKNOWN)

/** Whole percent as the official sheet shows it ("Confidence:89%"): truncated, not rounded. */
fun MapObstacle.confidencePercent(): Int? = confidence?.takeIf { it > 0f }?.let { (it * 100f + 1e-4f).toInt() }
