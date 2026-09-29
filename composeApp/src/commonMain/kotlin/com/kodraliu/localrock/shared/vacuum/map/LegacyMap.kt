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
            v == 0 -> 0
            (v and 0x07) == 1 -> 127.toByte()
            else -> 128.toByte()
        }
    }

    val roomSumX = mutableMapOf<Int, Long>()
    val roomSumY = mutableMapOf<Int, Long>()
    val roomCount = mutableMapOf<Int, Int>()
    for (y in 0 until imageHeight) {
        for (x in 0 until imageWidth) {
            val v = pixels[y * imageWidth + x].toInt() and 0xff
            if (v != 0 && (v and 0x07) != 1) {
                val roomId = (v ushr 3) and 0x1f
                if (roomId > 0) {
                    roomSumX[roomId] = (roomSumX[roomId] ?: 0L) + x
                    roomSumY[roomId] = (roomSumY[roomId] ?: 0L) + y
                    roomCount[roomId] = (roomCount[roomId] ?: 0) + 1
                }
            }
        }
    }
    val rooms = roomCount.entries.sortedBy { it.key }.map { (roomId, count) ->
        val cx = (roomSumX[roomId]!! / count.toFloat() / imageWidth).coerceIn(0f, 1f)
        val cy = ((imageHeight - 1 - roomSumY[roomId]!! / count.toFloat()) / imageHeight).coerceIn(0f, 1f)
        ParsedMapRoom(id = roomId, name = "Room $roomId", labelNormX = cx, labelNormY = cy)
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
        originalGrid = pixels,
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

private const val BLOCK_CHARGER = 1
private const val BLOCK_IMAGE = 2
private const val BLOCK_PATH = 3
private const val BLOCK_ROBOT_POSITION = 8
private const val BLOCK_FORBIDDEN_ZONES = 9        // persistent no-go zones
private const val BLOCK_VIRTUAL_WALLS = 10
private const val BLOCK_FORBIDDEN_MOP_ZONES = 12   // persistent no-mop zones
private const val BLOCK_SEGMENT_MATERIALS = 24

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
