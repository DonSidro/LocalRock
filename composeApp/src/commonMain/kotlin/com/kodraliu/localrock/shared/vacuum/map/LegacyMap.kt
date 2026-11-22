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
    var patrolPoints: List<PatrolPoint> = emptyList()
    var patrolTaskId: Long? = null
    var zoneBlock: Triple<Int, Int, Int>? = null
    var eraserZones: List<MapZone> = emptyList()

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
            BLOCK_PATROL -> {
                patrolTaskId = u32(bytes, off + 8).toLong() and 0xFFFFFFFFL
                patrolPoints = parsePatrolPoints(bytes, off + blockHeaderLen, u32(bytes, off + 12), blockDataLen)
            }
            BLOCK_ZONE_LINES -> zoneBlock = Triple(off, blockHeaderLen, blockDataLen)
            BLOCK_ERASER_ZONES -> eraserZones = parseEraserZones(bytes, off, blockHeaderLen, blockDataLen)
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

    val zoneLines = zoneBlock?.let { (o, h, d) -> parseZoneLines(bytes, o, h, d, imageLeft, imageTop) } ?: emptyList()

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

    val roomAt = IntArray(imageWidth * imageHeight) { i ->
        val v = pixels[i].toInt() and 0xff
        if ((v and 0x07) == 7) (v ushr 3) and 0x1f else 0
    }
    val labelCells = roomLabelCells(roomAt, imageWidth, imageHeight)
    val rooms = labelCells.keys.sorted().map { roomId ->
        val (lx, ly) = labelCells.getValue(roomId)
        // The middle of the label's pixel, so a tap on the label's centre finds this room.
        val cx = ((lx + 0.5f) / imageWidth).coerceIn(0f, 1f)
        val cy = ((imageHeight - 1 - ly + 0.5f) / imageHeight).coerceIn(0f, 1f)
        // How far the room reaches sideways around its label, over a few rows so the gaps inside a room
        // (furniture, unscanned patches) do not make it look narrower than it is.
        var left = lx
        var right = lx
        for (row in (ly - LABEL_SPAN_ROWS)..(ly + LABEL_SPAN_ROWS)) {
            if (row !in 0 until imageHeight) continue
            for (x in 0 until imageWidth) {
                if (roomAt[row * imageWidth + x] == roomId) {
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
        }
        // The same reach up and down around the label, for a map turned a quarter turn.
        var top = ly
        var bottom = ly
        for (col in (lx - LABEL_SPAN_ROWS)..(lx + LABEL_SPAN_ROWS)) {
            if (col !in 0 until imageWidth) continue
            for (y in 0 until imageHeight) {
                if (roomAt[y * imageWidth + col] == roomId) {
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
        patrolPoints = patrolPoints,
        patrolTaskId = patrolTaskId,
        pathFlags = pathFlags.takeIf { it.size == path.size } ?: emptyList(),
        carpetMask = carpetMask?.takeIf { it.size == grid.size },
        zoneLines = zoneLines,
        eraserZones = eraserZones,
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

/** Block 47: [entryCount] records of a u32 id and four corners of two `u16` values each, in robot millimetres. */
private fun parseEraserZones(bytes: ByteArray, off: Int, headerLen: Int, dataLen: Int): List<MapZone> {
    val count = minOf(entryCount(bytes, off, headerLen, dataLen), dataLen / 20)
    val body = off + headerLen
    return List(count) { i ->
        val p = body + i * 20 + 4
        MapZone(
            x0 = u16(bytes, p), y0 = u16(bytes, p + 2),
            x1 = u16(bytes, p + 4), y1 = u16(bytes, p + 6),
            x2 = u16(bytes, p + 8), y2 = u16(bytes, p + 10),
            x3 = u16(bytes, p + 12), y3 = u16(bytes, p + 14),
            kind = ZoneKind.ERASER,
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

/**
 * A PATROL (37) block of a Cruise record: the header carries the task id and the point count, each
 * record is u16 x, u16 y, u8 result, u8 photo count, then 32-byte NUL-padded photo ids (8 slots) and a
 * 16-byte tail. Checked against a real Curv 2 Flow record map (3 points of 278 bytes).
 */
private fun parsePatrolPoints(bytes: ByteArray, body: Int, count: Int, dataLen: Int): List<PatrolPoint> {
    if (count <= 0 || dataLen / count < PATROL_FIXED_BYTES) return emptyList()
    val recordLen = dataLen / count
    return List(count) { i ->
        val p = body + i * recordLen
        val slots = minOf(bytes[p + 5].toInt() and 0xff, (recordLen - PATROL_FIXED_BYTES) / PATROL_ID_BYTES)
        PatrolPoint(
            x = u16(bytes, p), y = u16(bytes, p + 2), result = bytes[p + 4].toInt() and 0xff,
            photoIds = List(slots) { j ->
                val start = p + PATROL_FIXED_BYTES + j * PATROL_ID_BYTES
                bytes.decodeToString(start, start + PATROL_ID_BYTES).trimEnd(Char(0))
            },
        )
    }
}

private const val BLOCK_PATROL = 37
private const val PATROL_FIXED_BYTES = 6
private const val PATROL_ID_BYTES = 32

/**
 * Block 55: u32 zone count at `off+8`, then per zone u16 segment, u16 point count, u16 point size and points of
 * u16 x, u16 y, i16 type (mm); a zone that overruns the payload ends the block.
 */
private fun parseZoneLines(bytes: ByteArray, off: Int, headerLen: Int, dataLen: Int, left: Int, top: Int): List<FloorPlanZone> {
    if (headerLen < 12) return emptyList()
    val declared = u32(bytes, off + 8)
    val zones = ArrayList<FloorPlanZone>()
    var p = off + headerLen
    val end = p + dataLen
    while (zones.size < declared && p + ZONE_HEADER_BYTES <= end) {
        val segment = u16(bytes, p)
        val count = u16(bytes, p + 2)
        val size = u16(bytes, p + 4)
        p += ZONE_HEADER_BYTES
        if (size < ZONE_POINT_MIN_BYTES || p + count.toLong() * size > end) break
        if (count >= 3) {
            zones += FloorPlanZone(
                segment,
                List(count) { i ->
                    val q = p + i * size
                    FloorPlanPoint(
                        x = u16(bytes, q) / MM_PER_CELL - left,
                        y = u16(bytes, q + 2) / MM_PER_CELL - top,
                        type = u16(bytes, q + 4).toShort().toInt(),
                    )
                },
            )
        }
        p += count * size
    }
    return zones
}

private const val BLOCK_ZONE_LINES = 55
private const val BLOCK_ERASER_ZONES = 47
private const val ZONE_HEADER_BYTES = 6
private const val ZONE_POINT_MIN_BYTES = 6
private const val MM_PER_CELL = 50

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

/**
 * Where each room's name goes, as an (x, y) pixel of [roomAt] (a room id per pixel, 0 for none,
 * row 0 at the bottom like the image): the floor pixel nearest the room's middle among those at
 * least half as far from any wall, gap, other room or the image edge as the room's most open
 * pixel. The middle alone can fall outside an L- or U-shaped room, on a gap or in the room next
 * door; the most open pixel alone drifts away from furniture gaps toward one side of the room.
 */
internal fun roomLabelCells(roomAt: IntArray, width: Int, height: Int): Map<Int, Pair<Int, Int>> {
    // City-block distance from each floor pixel to the nearest pixel that isn't the same room's
    // floor (the image edge counts as one), in a forward and a backward pass.
    val far = IntArray(roomAt.size)
    fun sameRoom(i: Int, x: Int, y: Int) =
        x in 0 until width && y in 0 until height && roomAt[y * width + x] == roomAt[i]
    for (y in 0 until height) {
        for (x in 0 until width) {
            val i = y * width + x
            if (roomAt[i] == 0) continue
            val left = if (sameRoom(i, x - 1, y)) far[i - 1] else 0
            val below = if (sameRoom(i, x, y - 1)) far[i - width] else 0
            far[i] = minOf(left, below) + 1
        }
    }
    for (y in height - 1 downTo 0) {
        for (x in width - 1 downTo 0) {
            val i = y * width + x
            if (roomAt[i] == 0) continue
            val right = if (sameRoom(i, x + 1, y)) far[i + 1] else 0
            val above = if (sameRoom(i, x, y + 1)) far[i + width] else 0
            far[i] = minOf(far[i], minOf(right, above) + 1)
        }
    }

    val sumX = HashMap<Int, Long>()
    val sumY = HashMap<Int, Long>()
    val count = HashMap<Int, Int>()
    for (i in roomAt.indices) {
        val room = roomAt[i]
        if (room == 0) continue
        sumX[room] = (sumX[room] ?: 0L) + i % width
        sumY[room] = (sumY[room] ?: 0L) + i / width
        count[room] = (count[room] ?: 0) + 1
    }

    val mostOpen = HashMap<Int, Int>()
    for (i in roomAt.indices) {
        val room = roomAt[i]
        if (room != 0 && far[i] > (mostOpen[room] ?: 0)) mostOpen[room] = far[i]
    }
    val best = HashMap<Int, Int>()
    val bestDistance = HashMap<Int, Float>()
    for (i in roomAt.indices) {
        val room = roomAt[i]
        if (room == 0 || far[i] < (mostOpen.getValue(room) + 1) / 2) continue
        val n = count.getValue(room).toFloat()
        val dx = i % width - sumX.getValue(room) / n
        val dy = i / width - sumY.getValue(room) / n
        val distance = dx * dx + dy * dy
        if (distance < (bestDistance[room] ?: Float.MAX_VALUE)) {
            best[room] = i
            bestDistance[room] = distance
        }
    }
    return best.mapValues { (_, i) -> (i % width) to (i / width) }
}
