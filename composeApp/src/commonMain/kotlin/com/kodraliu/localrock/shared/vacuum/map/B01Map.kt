package com.kodraliu.localrock.shared.vacuum.map

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber


@Serializable
internal data class B01RobotMap(
    @ProtoNumber(1) val mapType: Int = 0,
    @ProtoNumber(2) val mapExtInfo: B01MapExtInfo? = null,
    @ProtoNumber(3) val mapHead: B01MapHeadInfo? = null,
    @ProtoNumber(4) val mapData: B01MapDataInfo? = null,
    @ProtoNumber(12) val roomDataInfo: List<B01RoomDataInfo> = emptyList(),
)

@Serializable
internal data class B01MapExtInfo(
    @ProtoNumber(1) val taskBeginDate: Int = 0,
    @ProtoNumber(2) val mapUploadDate: Int = 0,
    @ProtoNumber(3) val mapValid: Int = 0,
    @ProtoNumber(4) val radian: Int = 0,
    @ProtoNumber(5) val force: Int = 0,
    @ProtoNumber(6) val cleanPath: Int = 0,
    @ProtoNumber(7) val boudaryInfo: B01MapBoundaryInfo? = null,
    @ProtoNumber(8) val mapVersion: Int = 0,
    @ProtoNumber(9) val mapValueType: Int = 0,
)

@Serializable
internal data class B01MapBoundaryInfo(
    @ProtoNumber(1) val mapMd5: String = "",
    @ProtoNumber(2) val vMinX: Int = 0,
    @ProtoNumber(3) val vMaxX: Int = 0,
    @ProtoNumber(4) val vMinY: Int = 0,
    @ProtoNumber(5) val vMaxY: Int = 0,
)

@Serializable
internal data class B01MapHeadInfo(
    @ProtoNumber(1) val mapHeadId: Int = 0,
    @ProtoNumber(2) val sizeX: Int = 0,
    @ProtoNumber(3) val sizeY: Int = 0,
    @ProtoNumber(4) val minX: Float = 0f,
    @ProtoNumber(5) val minY: Float = 0f,
    @ProtoNumber(6) val maxX: Float = 0f,
    @ProtoNumber(7) val maxY: Float = 0f,
    @ProtoNumber(8) val resolution: Float = 0f,
)

@Serializable
internal data class B01MapDataInfo(
    @ProtoNumber(1) val mapData: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean =
        other is B01MapDataInfo && mapData.contentEquals(other.mapData)
    override fun hashCode(): Int = mapData.contentHashCode()
}

@Serializable
internal data class B01RoomDataInfo(
    @ProtoNumber(1) val roomId: Int = 0,
    @ProtoNumber(2) val roomName: String = "",
    @ProtoNumber(3) val roomTypeId: Int = 0,
    @ProtoNumber(4) val meterialId: Int = 0,
    @ProtoNumber(5) val cleanState: Int = 0,
    @ProtoNumber(6) val roomClean: Int = 0,
    @ProtoNumber(7) val roomCleanIndex: Int = 0,
    @ProtoNumber(8) val roomNamePos: B01DevicePointInfo? = null,
    @ProtoNumber(10) val colorId: Int = 0,
    @ProtoNumber(11) val floorDirection: Int = 0,
    @ProtoNumber(12) val globalSeq: Int = 0,
)

@Serializable
internal data class B01DevicePointInfo(
    @ProtoNumber(1) val x: Float = 0f,
    @ProtoNumber(2) val y: Float = 0f,
)


data class ParsedMap(
    val width: Int,
    val height: Int,

    val grid: ByteArray,
    val rooms: List<ParsedMapRoom>,
    val resolution: Float,
    val pixelOffsetLeft: Int = 0,
    val pixelOffsetTop: Int = 0,
    val chargerMm: ParsedMapPoint? = null,
    val robotMm: ParsedMapPoint? = null,
    val pathMm: List<ParsedMapPoint> = emptyList(),

    val originalGrid: ByteArray? = null,
) {
    val floorCells: Int by lazy { grid.count { (it.toInt() and 0xff) == 128 } }
    val wallCells: Int by lazy { grid.count { (it.toInt() and 0xff) == 127 } }

    override fun equals(other: Any?): Boolean =
        other is ParsedMap && other.width == width && other.height == height &&
            other.grid.contentEquals(grid) && other.rooms == rooms &&
            other.chargerMm == chargerMm && other.robotMm == robotMm &&
            other.pathMm == pathMm

    override fun hashCode(): Int {
        var r = width
        r = 31 * r + height
        r = 31 * r + grid.contentHashCode()
        r = 31 * r + rooms.hashCode()
        r = 31 * r + (chargerMm?.hashCode() ?: 0)
        r = 31 * r + (robotMm?.hashCode() ?: 0)
        r = 31 * r + pathMm.hashCode()
        return r
    }
}

data class ParsedMapRoom(
    val id: Int,
    val name: String,
    val labelNormX: Float? = null,
    val labelNormY: Float? = null,
)

data class ParsedMapPoint(
    val x: Int,
    val y: Int,
    val angle: Int = 0,
)

class MapParseException(message: String) : RuntimeException(message)


fun ParsedMap.roomAtNorm(normX: Float, normY: Float): ParsedMapRoom? {
    if (rooms.isEmpty()) return null
    val col = (normX * width).toInt().coerceIn(0, width - 1)
    // Undo the renderer Y-flip: normY=0 (canvas top) → grid row (height-1).
    val row = ((1f - normY) * height).toInt().coerceIn(0, height - 1)
    val idx = row * width + col

    if (originalGrid != null) {
        // Legacy: (byte >> 3) & 0x1f encodes room ID per pixel.
        val v = originalGrid[idx].toInt() and 0xff
        if (v == 0 || (v and 0x07) == 1) return null  // outside or wall
        val roomId = (v ushr 3) and 0x1f
        return if (roomId > 0) rooms.find { it.id == roomId } else null
    }

    // B01: 0 = outside, 127 = wall, 128 = unassigned floor, 1-126 = segment ID.
    val v = grid[idx].toInt() and 0xff
    if (v == 0 || v == 127) return null
    if (v != 128) rooms.find { it.id == v }?.let { return it }

    // Unassigned floor or unmatched segment — fall back to nearest labelled room.
    return rooms
        .filter { it.labelNormX != null && it.labelNormY != null }
        .minByOrNull { r ->
            val dx = r.labelNormX!! - normX
            val dy = r.labelNormY!! - normY
            dx * dx + dy * dy
        }
}


@OptIn(ExperimentalSerializationApi::class)
fun parseB01Map(bytes: ByteArray): ParsedMap {
    val proto = try {
        ProtoBuf.decodeFromByteArray(B01RobotMap.serializer(), bytes)
    } catch (e: Throwable) {
        throw MapParseException("Failed to decode B01 SCMap protobuf: ${e.message}")
    }
    val head = proto.mapHead ?: throw MapParseException("B01 map missing mapHead")
    val data = proto.mapData ?: throw MapParseException("B01 map missing mapData")
    val width = head.sizeX
    val height = head.sizeY
    if (width <= 0 || height <= 0) throw MapParseException("B01 map has zero dimensions ($width × $height)")
    val expected = width * height
    if (data.mapData.size < expected) {
        throw MapParseException("B01 map grid is ${data.mapData.size} bytes, expected at least $expected for ${width}×${height}")
    }
    val grid = if (data.mapData.size == expected) data.mapData else data.mapData.copyOfRange(0, expected)

    // Scan raw pixels for segment IDs (values that aren't outside/wall/unassigned-floor).
    // Used as fallback centroid when roomNamePos is absent.
    val pixSumX = mutableMapOf<Int, Long>()
    val pixSumY = mutableMapOf<Int, Long>()
    val pixCount = mutableMapOf<Int, Int>()
    for (y in 0 until height) {
        for (x in 0 until width) {
            val v = grid[y * width + x].toInt() and 0xff
            if (v != 0 && v != 127 && v != 128) {
                pixSumX[v] = (pixSumX[v] ?: 0L) + x
                pixSumY[v] = (pixSumY[v] ?: 0L) + y
                pixCount[v] = (pixCount[v] ?: 0) + 1
            }
        }
    }


    val rangeX = head.maxX - head.minX
    val rangeY = head.maxY - head.minY

    val rooms = proto.roomDataInfo.map { r ->
        val pos = r.roomNamePos
        val (nx, ny) = if (pos != null && rangeX > 0f && rangeY > 0f) {
            val normX = ((pos.x - head.minX) / rangeX).coerceIn(0f, 1f)
            val normY = (1f - (pos.y - head.minY) / rangeY).coerceIn(0f, 1f)
            normX to normY
        } else {
            val key = pixCount.keys.firstOrNull { it == r.colorId && it > 0 }
                ?: pixCount.keys.firstOrNull { it == r.roomId && it > 0 }
            if (key != null) {
                val cnt = pixCount[key]!!
                val cx = (pixSumX[key]!! / cnt.toFloat() / width).coerceIn(0f, 1f)
                val cy = ((height - 1 - pixSumY[key]!! / cnt.toFloat()) / height).coerceIn(0f, 1f)
                cx to cy
            } else {
                null to null
            }
        }
        ParsedMapRoom(
            id = r.roomId,
            name = r.roomName.ifBlank { "Room ${r.roomId}" },
            labelNormX = nx,
            labelNormY = ny,
        )
    }
    return ParsedMap(
        width = width,
        height = height,
        grid = grid,
        rooms = rooms,
        resolution = head.resolution,
    )
}
