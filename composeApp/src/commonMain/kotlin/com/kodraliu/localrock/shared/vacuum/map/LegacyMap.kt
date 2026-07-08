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
        }
        off += blockHeaderLen + blockDataLen
    }

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
        originalGrid = pixels,
    )
}

private const val MAGIC_0 = 0x72.toByte()
private const val MAGIC_1 = 0x72.toByte()

private const val BLOCK_CHARGER = 1
private const val BLOCK_IMAGE = 2
private const val BLOCK_PATH = 3
private const val BLOCK_ROBOT_POSITION = 8
private const val BLOCK_DIGEST = 1024

private const val LEGACY_PIXEL_RESOLUTION_M = 0.05f

private fun u16(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8)

private fun u32(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xff) or
        ((b[off + 1].toInt() and 0xff) shl 8) or
        ((b[off + 2].toInt() and 0xff) shl 16) or
        ((b[off + 3].toInt() and 0xff) shl 24)
