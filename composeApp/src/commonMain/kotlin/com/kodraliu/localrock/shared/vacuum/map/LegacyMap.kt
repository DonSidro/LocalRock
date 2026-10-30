package com.kodraliu.localrock.shared.vacuum.map


fun parseLegacyMap(bytes: ByteArray): ParsedMap {
    if (bytes.size < 20) throw MapParseException("Legacy map too short (${bytes.size} bytes)")
    if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1) {
        throw MapParseException("Legacy map missing 'rr' magic")
    }
    val headerLen = u16(bytes, 2)
    if (headerLen != 20) throw MapParseException("Unexpected file header length: $headerLen")

    var imageWidth = 0
    var imageHeight = 0
    var imagePixels: ByteArray? = null
    var imageLeft = 0
    var imageTop = 0
    var charger: ParsedMapPoint? = null
    var robot: ParsedMapPoint? = null
    var path: List<ParsedMapPoint> = emptyList()
    var noGoZones: List<MapZone> = emptyList()
    var noMopZones: List<MapZone> = emptyList()
    var virtualWalls: List<VirtualWall> = emptyList()
    var carpetAreas: List<MapZone> = emptyList()
    var flaggedCarpets: List<MapZone>? = null
    var thresholds: List<MapZone> = emptyList()
    var segmentMaterials: Map<Int, Int> = emptyMap()
    var segmentMaterialDirections: Map<Int, Int> = emptyMap()
    val unmodeledRestrictionBlocks = mutableSetOf<Int>()
    var pathFlags: List<Int> = emptyList()
    var carpetMask: ByteArray? = null
    val obstacles = mutableListOf<MapObstacle>()

    var off = headerLen
    while (off + 8 <= bytes.size) {
        val type = u16(bytes, off)
        val blockHeaderLen = u16(bytes, off + 2)
        val blockDataLen = u32(bytes, off + 4)
        if (blockHeaderLen < 8 || blockHeaderLen > bytes.size - off) {
            throw MapParseException("Invalid block at off=$off: header_len=$blockHeaderLen")
        }
        if (blockDataLen < 0 || off + blockHeaderLen + blockDataLen > bytes.size) {
            throw MapParseException("Block body out of range at off=$off")
        }
        when (type) {
            BLOCK_DIGEST -> break
            BLOCK_IMAGE -> {
                if (blockHeaderLen >= 28) {
                    val ih = off + 8
                    imageTop = u32(bytes, ih + 4)
                    imageLeft = u32(bytes, ih + 8)
                    imageHeight = u32(bytes, ih + 12)
                    imageWidth = u32(bytes, ih + 16)
                }
                val pixelStart = off + blockHeaderLen
                val pixelCount = imageWidth * imageHeight
                if (pixelCount > 0 && pixelStart + pixelCount <= bytes.size) {
                    imagePixels = bytes.copyOfRange(pixelStart, pixelStart + pixelCount)
                }
            }
            BLOCK_MOP_PATH -> {
                val body = off + blockHeaderLen
                pathFlags = List(blockDataLen) { bytes[body + it].toInt() and 0xff }
            }
            BLOCK_CARPET_MAP -> {
                val body = off + blockHeaderLen
                carpetMask = bytes.copyOfRange(body, body + blockDataLen)
            }
            BLOCK_CHARGER -> {
                val body = off + blockHeaderLen
                if (blockDataLen >= 12) {
                    charger = ParsedMapPoint(
                        x = u32(bytes, body),
                        y = u32(bytes, body + 4),
                        angle = u32(bytes, body + 8),
                    )
                }
            }
            BLOCK_ROBOT_POSITION -> {
                val body = off + blockHeaderLen
                if (blockDataLen >= 12) {
                    robot = ParsedMapPoint(
                        x = u32(bytes, body),
                        y = u32(bytes, body + 4),
                        angle = u32(bytes, body + 8),
                    )
                }
            }
            BLOCK_PATH -> {

                val body = off + blockHeaderLen
                val pointCount = blockDataLen / 4
                if (pointCount > 0 && body + pointCount * 4 <= bytes.size) {
                    val pts = ArrayList<ParsedMapPoint>(pointCount)
                    var p = body
                    repeat(pointCount) {
                        pts += ParsedMapPoint(x = u16(bytes, p), y = u16(bytes, p + 2))
                        p += 4
                    }
                    path = pts
                }
            }
            BLOCK_FORBIDDEN_ZONES ->
                noGoZones = parseQuadZones(bytes, off, blockHeaderLen, blockDataLen, ZoneKind.NO_GO)
            BLOCK_FORBIDDEN_MOP_ZONES ->
                noMopZones = parseQuadZones(bytes, off, blockHeaderLen, blockDataLen, ZoneKind.NO_MOP)
            BLOCK_VIRTUAL_WALLS -> virtualWalls = parseWalls(bytes, off, blockHeaderLen, blockDataLen)
            BLOCK_CARPET_AREAS ->
                carpetAreas = parseQuadZones(bytes, off, blockHeaderLen, blockDataLen, ZoneKind.CARPET)
            BLOCK_CARPETS_WITH_FLAGS ->
                flaggedCarpets = parseFlaggedCarpets(bytes, off, blockHeaderLen, blockDataLen)
            BLOCK_THRESHOLDS ->
                thresholds = parseQuadZones(bytes, off, blockHeaderLen, blockDataLen, ZoneKind.THRESHOLD)
            BLOCK_SEGMENT_MATERIALS -> {
                // One byte per segment id, indexed from 0 (on the S8 Pro Ultra, segment 21 read 4
                // and 24 read 3 right after the official app set them to tile and wood).
                val body = off + blockHeaderLen
                segmentMaterials = buildMap {
                    for (i in 0 until blockDataLen) put(i, bytes[body + i].toInt() and 0xff)
                }
            }
            BLOCK_SEGMENT_MATERIAL_DIRECTIONS -> {
                // Triplets of u8 segment id + u16 direction in degrees.
                val body = off + blockHeaderLen
                segmentMaterialDirections = buildMap {
                    var p = 0
                    while (p + 3 <= blockDataLen) {
                        put(bytes[body + p].toInt() and 0xff, u16(bytes, body + p + 1))
                        p += 3
                    }
                }
            }
            in UNMODELED_RESTRICTION_BLOCKS ->
                if (entryCount(bytes, off, blockHeaderLen, blockDataLen) > 0) unmodeledRestrictionBlocks += type
        }
        if (type in OBSTACLE_BLOCKS) {
            obstacles += parseObstacles(
                bytes, off + blockHeaderLen, entryCount(bytes, off, blockHeaderLen, blockDataLen), blockDataLen,
                ignored = type == BLOCK_IGNORED_OBSTACLES || type == BLOCK_IGNORED_OBSTACLES_WITH_PHOTO,
            )
        }
        off += blockHeaderLen + blockDataLen
    }

    // Block 39 repeats block 22's corners and adds each carpet's flags. Use it only when the two
    // agree on the count, so a map where they differ can never lose carpets.
    val carpetsFromFlags = flaggedCarpets?.takeIf { it.size == carpetAreas.size }
    if (carpetsFromFlags != null) carpetAreas = carpetsFromFlags

    val pixels = imagePixels ?: throw MapParseException("Legacy map has no IMAGE block")
    val grid = ByteArray(pixels.size)
    for (i in pixels.indices) {
        val v = pixels[i].toInt() and 0xff
        grid[i] = when {
            (v and 0x07) == 1 -> 127.toByte()
            (v and 0x07) == 7 -> 128.toByte()
            else -> 0
        }
    }

    val roomSumX = mutableMapOf<Int, Long>()
    val roomSumY = mutableMapOf<Int, Long>()
    val roomCount = mutableMapOf<Int, Int>()
    for (y in 0 until imageHeight) {
        for (x in 0 until imageWidth) {
            val v = pixels[y * imageWidth + x].toInt() and 0xff
            if ((v and 0x07) == 7) {
                val roomId = (v ushr 3) and 0x1f
                if (roomId > 0) {
                    roomSumX[roomId] = (roomSumX[roomId] ?: 0L) + x
                    roomSumY[roomId] = (roomSumY[roomId] ?: 0L) + y
                    roomCount[roomId] = (roomCount[roomId] ?: 0) + 1
                }
            }
        }
    }
    // The label goes at the room's most open spot: the floor cell farthest from any wall, gap or other
    // room, so a name sits on clear floor and not on furniture gaps or in a doorway.
    val roomAt = IntArray(imageWidth * imageHeight)
    for (i in roomAt.indices) {
        val v = pixels[i].toInt() and 0xff
        roomAt[i] = if ((v and 0x07) == 7) (v ushr 3) and 0x1f else 0
    }
    val open = IntArray(roomAt.size) { Int.MAX_VALUE / 2 }
    for (y in 0 until imageHeight) {
        for (x in 0 until imageWidth) {
            val i = y * imageWidth + x
            if (roomAt[i] == 0) {
                open[i] = 0
            } else if (x == 0 || y == 0 || x == imageWidth - 1 || y == imageHeight - 1 ||
                roomAt[i - 1] != roomAt[i] || roomAt[i + 1] != roomAt[i] ||
                roomAt[i - imageWidth] != roomAt[i] || roomAt[i + imageWidth] != roomAt[i]
            ) {
                open[i] = 1
            }
        }
    }
    for (y in 0 until imageHeight) {
        for (x in 0 until imageWidth) {
            val i = y * imageWidth + x
            if (roomAt[i] == 0) continue
            if (x > 0 && roomAt[i - 1] == roomAt[i]) open[i] = minOf(open[i], open[i - 1] + 1)
            if (y > 0 && roomAt[i - imageWidth] == roomAt[i]) open[i] = minOf(open[i], open[i - imageWidth] + 1)
        }
    }
    for (y in imageHeight - 1 downTo 0) {
        for (x in imageWidth - 1 downTo 0) {
            val i = y * imageWidth + x
            if (roomAt[i] == 0) continue
            if (x < imageWidth - 1 && roomAt[i + 1] == roomAt[i]) open[i] = minOf(open[i], open[i + 1] + 1)
            if (y < imageHeight - 1 && roomAt[i + imageWidth] == roomAt[i]) open[i] = minOf(open[i], open[i + imageWidth] + 1)
        }
    }
    // Open enough is good enough: among the cells at least 70% as open as the room's best one, the
    // name goes on the one nearest the room's middle, so it is neither on a gap nor out at an edge.
    val mostOpen = mutableMapOf<Int, Int>()
    for (i in roomAt.indices) {
        val id = roomAt[i]
        if (id != 0 && open[i] > (mostOpen[id] ?: 0)) mostOpen[id] = open[i]
    }
    val labelX = mutableMapOf<Int, Int>()
    val labelY = mutableMapOf<Int, Int>()
    val labelDistance = mutableMapOf<Int, Float>()
    for (y in 0 until imageHeight) {
        for (x in 0 until imageWidth) {
            val i = y * imageWidth + x
            val roomId = roomAt[i]
            if (roomId == 0) continue
            val count = roomCount[roomId] ?: continue
            if (open[i] * 10 < (mostOpen[roomId] ?: 0) * 7) continue
            val dx = x - roomSumX[roomId]!! / count.toFloat()
            val dy = y - roomSumY[roomId]!! / count.toFloat()
            val distance = dx * dx + dy * dy
            if (distance < (labelDistance[roomId] ?: Float.MAX_VALUE)) {
                labelDistance[roomId] = distance
                labelX[roomId] = x
                labelY[roomId] = y
            }
        }
    }
    val rooms = roomCount.keys.sorted().map { roomId ->
        val cx = ((labelX[roomId]!! + 0.5f) / imageWidth).coerceIn(0f, 1f)
        val cy = ((imageHeight - 1 - labelY[roomId]!! + 0.5f) / imageHeight).coerceIn(0f, 1f)
        // How far the room reaches sideways around its label, over a few rows so the gaps inside a room
        // (furniture, unscanned patches) do not make it look narrower than it is.
        var left = labelX[roomId]!!
        var right = left
        for (row in (labelY[roomId]!! - LABEL_SPAN_ROWS)..(labelY[roomId]!! + LABEL_SPAN_ROWS)) {
            if (row !in 0 until imageHeight) continue
            for (x in 0 until imageWidth) {
                val v = pixels[row * imageWidth + x].toInt() and 0xff
                if ((v and 0x07) == 7 && ((v ushr 3) and 0x1f) == roomId) {
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
        }
        // The same reach up and down around the label, for a map turned a quarter turn.
        var top = labelY[roomId]!!
        var bottom = top
        for (col in (labelX[roomId]!! - LABEL_SPAN_ROWS)..(labelX[roomId]!! + LABEL_SPAN_ROWS)) {
            if (col !in 0 until imageWidth) continue
            for (y in 0 until imageHeight) {
                val v = pixels[y * imageWidth + col].toInt() and 0xff
                if ((v and 0x07) == 7 && ((v ushr 3) and 0x1f) == roomId) {
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        ParsedMapRoom(
            id = roomId, name = "Room $roomId", labelNormX = cx, labelNormY = cy,
            labelSpanCells = right - left + 1, labelSpanCellsY = bottom - top + 1,
        )
    }

    return ParsedMap(
        width = imageWidth,
        height = imageHeight,
        grid = grid,
        rooms = rooms,
        resolution = LEGACY_PIXEL_RESOLUTION_M,
        pixelOffsetLeft = imageLeft,
        pixelOffsetTop = imageTop,
        chargerMm = charger,
        robotMm = robot,
        pathMm = path,
        noGoZones = noGoZones,
        noMopZones = noMopZones,
        virtualWalls = virtualWalls,
        segmentMaterials = segmentMaterials,
        segmentMaterialDirections = segmentMaterialDirections,
        unmodeledRestrictionBlocks = unmodeledRestrictionBlocks.toSet(),
        format = MapFormat.LEGACY,
        carpetAreas = carpetAreas,
        carpetFlagsPresent = carpetsFromFlags != null,
        thresholds = thresholds,
        obstacles = obstacles,
        originalGrid = pixels,
        pathFlags = pathFlags.takeIf { it.size == path.size } ?: emptyList(),
        carpetMask = carpetMask?.takeIf { it.size == grid.size },
    )
}

/**
 * Number of entries a restriction block declares. Those blocks use a 12-byte header whose last
 * field (u16 at `off+8`) is the entry count; python-roborock's map parser reads it
 * there. A block with a shorter header has no count field, so any payload counts as present.
 */
private fun entryCount(bytes: ByteArray, off: Int, headerLen: Int, dataLen: Int): Int =
    if (headerLen >= 12) u16(bytes, off + 8) else if (dataLen > 0) 1 else 0

/**
 * Parse a FORBIDDEN_ZONES (9) or FORBIDDEN_MOP_ZONES (12) block: [entryCount] quadrilaterals of
 * eight `u16` values (four corners, robot millimetres) starting right after the block header.
 * Corners are kept in the robot's order so an unedited zone is written back unchanged.
 */
private fun parseQuadZones(bytes: ByteArray, off: Int, headerLen: Int, dataLen: Int, kind: ZoneKind): List<MapZone> {
    val count = minOf(entryCount(bytes, off, headerLen, dataLen), dataLen / 16)
    val body = off + headerLen
    return List(count) { i ->
        val p = body + i * 16
        MapZone(
            x0 = u16(bytes, p), y0 = u16(bytes, p + 2),
            x1 = u16(bytes, p + 4), y1 = u16(bytes, p + 6),
            x2 = u16(bytes, p + 8), y2 = u16(bytes, p + 10),
            x3 = u16(bytes, p + 12), y3 = u16(bytes, p + 14),
            kind = kind,
        )
    }
}

/**
 * Parse the carpet block with flags (39): [entryCount] quadrilaterals like block 22, followed by
 * one `u32` flags value per carpet in the same order.
 */
private fun parseFlaggedCarpets(bytes: ByteArray, off: Int, headerLen: Int, dataLen: Int): List<MapZone>? {
    val count = entryCount(bytes, off, headerLen, dataLen)
    if (count * 20 > dataLen) return null
    val quads = parseQuadZones(bytes, off, headerLen, dataLen, ZoneKind.CARPET)
    val flagsStart = off + headerLen + count * 16
    return quads.mapIndexed { i, z -> z.copy(flags = u32(bytes, flagsStart + i * 4).toLong() and 0xFFFFFFFFL) }
}

/** Parse a VIRTUAL_WALLS (10) block: [entryCount] lines of four `u16` values (robot mm). */
private fun parseWalls(bytes: ByteArray, off: Int, headerLen: Int, dataLen: Int): List<VirtualWall> {
    val count = minOf(entryCount(bytes, off, headerLen, dataLen), dataLen / 8)
    val body = off + headerLen
    return List(count) { i ->
        val p = body + i * 8
        VirtualWall(
            x0 = u16(bytes, p), y0 = u16(bytes, p + 2),
            x1 = u16(bytes, p + 4), y1 = u16(bytes, p + 6),
        )
    }
}

private const val MAGIC_0 = 0x72.toByte()
private const val MAGIC_1 = 0x72.toByte()

/** Rows either side of a room's label that its width is read over. */
private const val LABEL_SPAN_ROWS = 8

private const val BLOCK_CHARGER = 1
private const val BLOCK_IMAGE = 2
private const val BLOCK_PATH = 3
private const val BLOCK_CARPET_MAP = 17    // one byte per image cell, 1 on carpet
private const val BLOCK_MOP_PATH = 18      // one flag byte per path point
private const val BLOCK_ROBOT_POSITION = 8
private const val BLOCK_FORBIDDEN_ZONES = 9        // persistent no-go zones
private const val BLOCK_VIRTUAL_WALLS = 10
private const val BLOCK_FORBIDDEN_MOP_ZONES = 12   // persistent no-mop zones
private const val BLOCK_SEGMENT_MATERIALS = 24

// Obstacle blocks, ids from vacuum-map-parser-roborock: 13 obstacles, 14 ignored obstacles, 15 and 16
// the same with a photo id per record.
private const val BLOCK_OBSTACLES = 13
private const val BLOCK_IGNORED_OBSTACLES = 14
private const val BLOCK_OBSTACLES_WITH_PHOTO = 15
private const val BLOCK_IGNORED_OBSTACLES_WITH_PHOTO = 16
private val OBSTACLE_BLOCKS = setOf(
    BLOCK_OBSTACLES, BLOCK_IGNORED_OBSTACLES, BLOCK_OBSTACLES_WITH_PHOTO, BLOCK_IGNORED_OBSTACLES_WITH_PHOTO,
)

// Identified on an S8 Pro Ultra by adding one of each with the official app and finding the exact
// captured corners in these blocks (2026-09-28).
private const val BLOCK_CARPET_AREAS = 22   // set_carpet_area
private const val BLOCK_THRESHOLDS = 28     // app_set_smart_door_sill
private const val BLOCK_SEGMENT_MATERIAL_DIRECTIONS = 32

// Found 2026-09-29 by saving a round and a rectangular carpet with the official app: block 22 got
// the corners, block 39 the same corners plus one u32 of flags per carpet.
private const val BLOCK_CARPETS_WITH_FLAGS = 39

/**
 * Restriction blocks that are not parsed: 19 no-carpet areas, 23 no-vacuum areas, 30 cliff areas,
 * 31 smart door-sill areas (block ids known from community Roborock map parsers). They are
 * only detected, never rewritten.
 */
internal val UNMODELED_RESTRICTION_BLOCKS = setOf(19, 23, 30, 31)
private const val BLOCK_DIGEST = 1024

private const val LEGACY_PIXEL_RESOLUTION_M = 0.05f

private fun u16(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8)

private fun u32(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xff) or
        ((b[off + 1].toInt() and 0xff) shl 8) or
        ((b[off + 2].toInt() and 0xff) shl 16) or
        ((b[off + 3].toInt() and 0xff) shl 24)
