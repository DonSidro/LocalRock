package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.RoborockMessage
import com.kodraliu.localrock.shared.protocol.RoborockProtocol
import com.kodraliu.localrock.shared.protocol.RoborockProtocolException
import com.kodraliu.localrock.shared.protocol.gunzip
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Obstacle photos (#36), following python-roborock's ObstaclePhotoTrait and BlobRpcChannel:
 * get_photo carries the key from get_random_pkey, the map's security endpoint and nonce, and the
 * photo id; the robot answers ["ok"] and then sends the photo as a protocol-301 frame of its own.
 */

/** The 285x285 crop around the obstacle. 0 is the full 640x480 frame, which arrives split over frames. */
internal const val PHOTO_TYPE_CROP = 1

private val BLOB_MAGIC = "ROBOROCK".encodeToByteArray()
private const val PHOTO_DATA_BLOCK = 3
private const val PHOTO_BLOCK_HEADER_MIN = 8

/** A decoded "ROBOROCK" blob frame: the get_photo request it answers and its unzipped payload. */
internal class BlobResponse(val requestId: Int, val data: ByteArray)

internal fun getPhotoParams(publicKey: JsonElement, security: SecurityData, photoId: String, type: Int = PHOTO_TYPE_CROP) =
    JsonObject(
        mapOf(
            "security" to JsonObject(mapOf("pub_key" to publicKey, "cipher_suite" to JsonPrimitive(0))),
            "endpoint" to JsonPrimitive(security.endpoint),
            "nonce" to JsonPrimitive(security.nonceHex),
            "data_filter" to JsonObject(mapOf("img_id" to JsonPrimitive(photoId), "type" to JsonPrimitive(type))),
        ),
    )

/**
 * A protocol-301 frame that starts with "ROBOROCK": the request id is a u32 at 8, the header size a
 * u16 at 16 and the gzip length a u32 at 20, all little endian. Null for any other frame, such as a
 * map, which arrives on the same protocol.
 */
internal fun decodeBlobResponse(message: RoborockMessage): BlobResponse? {
    val p = message.payload
    if (message.protocol != RoborockProtocol.MAP_RESPONSE || p.size < 24) return null
    if (!p.copyOfRange(0, BLOB_MAGIC.size).contentEquals(BLOB_MAGIC)) return null
    val requestId = u32le(p, 8)
    val headerSize = u16le(p, 16)
    val end = headerSize.toLong() + (u32le(p, 20).toLong() and 0xFFFFFFFFL)
    if (headerSize < 24 || end > p.size) throw RoborockProtocolException("Invalid photo frame")
    return BlobResponse(requestId, gunzip(p.copyOfRange(headerSize, end.toInt())))
}

/**
 * The image in an unzipped get_photo payload: a run of blocks of u16 type, u16 header size and u32
 * payload size, where block 3 holds the JPEG.
 */
internal fun imageFromPhotoData(data: ByteArray): ByteArray {
    var off = 0
    while (off + PHOTO_BLOCK_HEADER_MIN <= data.size) {
        val type = u16le(data, off)
        val headerSize = u16le(data, off + 2)
        val next = off.toLong() + headerSize + (u32le(data, off + 4).toLong() and 0xFFFFFFFFL)
        if (headerSize < PHOTO_BLOCK_HEADER_MIN || next > data.size) throw RoborockProtocolException("Invalid photo payload")
        if (type == PHOTO_DATA_BLOCK) {
            val image = data.copyOfRange(off + headerSize, next.toInt())
            if (!image.isJpeg() && !image.isPng()) throw RoborockProtocolException("Photo payload is not an image")
            return image
        }
        off = next.toInt()
    }
    throw RoborockProtocolException("Photo payload has no image")
}

private fun ByteArray.isJpeg() = size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()

private fun ByteArray.isPng() = size >= 8 && copyOfRange(0, 8).contentEquals(
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
)

private fun u16le(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8)

private fun u32le(b: ByteArray, off: Int): Int =
    u16le(b, off) or (u16le(b, off + 2) shl 16)
