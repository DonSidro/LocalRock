package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.RoborockProtocolException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Cruise photos, as the official app asks for them (captured 2026-11-13): get_patrol_photo
 * {task_id, x, y, type, endpoint, security}, where x and y are the cruise point in robot mm, type 1
 * asks for the point's thumbnails and type 0 with a photo_id for one original. The robot answers
 * ["ok"] and then sends the photo data as a "ROBOROCK" protocol-301 frame carrying the request id.
 */

/** `type` of a thumbnail request; the original of one photo is type 0 with its photo id. */
internal const val PATROL_TYPE_THUMBNAILS = 1
internal const val PATROL_TYPE_ORIGINAL = 0

/** The official app pads a photo id with NULs to this many characters. */
private const val PHOTO_ID_FIELD = 32

/** A cruise point's thumbnail: its PNG bytes and the placement fields the robot sends with it. */
class PatrolThumbnail(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val angle: Int,
    val png: ByteArray,
)

internal fun getPatrolPhotoParams(
    publicKey: JsonElement,
    security: SecurityData,
    taskId: Long,
    x: Int,
    y: Int,
    photoId: String? = null,
    type: Int = PATROL_TYPE_THUMBNAILS,
    cipherSuite: Int = CIPHER_SUITE_PLAIN,
    endpoint: String = security.endpoint,
): JsonObject = JsonObject(
    buildMap {
        if (taskId != 0L) put("task_id", JsonPrimitive(taskId))
        if (x != 0) put("x", JsonPrimitive(x))
        if (y != 0) put("y", JsonPrimitive(y))
        if (!photoId.isNullOrEmpty()) put("photo_id", JsonPrimitive(photoId.padEnd(PHOTO_ID_FIELD, Char(0))))
        put("type", JsonPrimitive(type))
        put("endpoint", JsonPrimitive(endpoint))
        put("security", JsonObject(mapOf("pub_key" to publicKey, "cipher_suite" to JsonPrimitive(cipherSuite))))
    },
)

/**
 * The thumbnails in an unzipped get_patrol_photo payload, following the official app's parser: after a
 * 12-byte lead, records of u16 header size, u16 x, y, width, height and angle, a u32 image length at 12,
 * and the PNG at the header size. All little endian.
 */
fun parsePatrolThumbnails(data: ByteArray): List<PatrolThumbnail> {
    val out = mutableListOf<PatrolThumbnail>()
    var off = LEAD_BYTES
    while (off < data.size - (RECORD_MIN - 1)) {
        val headerSize = u16(data, off)
        val length = u32(data, off + 12)
        val start = off.toLong() + headerSize
        val end = start + length
        if (headerSize < RECORD_MIN || length < 0 || end > data.size) throw RoborockProtocolException("Invalid patrol photo record")
        out += PatrolThumbnail(
            x = u16(data, off + 2), y = u16(data, off + 4), width = u16(data, off + 6),
            height = u16(data, off + 8), angle = u16(data, off + 10),
            png = data.copyOfRange(start.toInt(), end.toInt()),
        )
        off = end.toInt()
    }
    return out
}

private const val LEAD_BYTES = 12
private const val RECORD_MIN = 16

private fun u16(b: ByteArray, off: Int): Int = (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8)

private fun u32(b: ByteArray, off: Int): Int = u16(b, off) or (u16(b, off + 2) shl 16)
