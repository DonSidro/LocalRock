package com.kodraliu.localrock.shared.vacuum.map

/** Header revision of a legacy map. Used to avoid highlighting an unchanged previous-run map. */
data class LegacyMapRevision(val mapIndex: Long, val sequence: Long)

/**
 * Legacy block 11: u32 count followed by u8 segment IDs. Valetudo's RoborockMapParser uses this
 * list to mark active segments independently of robot position. Keep the entire reported set.
 * https://github.com/Hypfer/Valetudo/blob/master/backend/lib/robots/roborock/RoborockMapParser.js
 * Other strides, incomplete lists and unknown IDs must not turn into a guessed selection.
 */
internal fun parseRunSegments(bytes: ByteArray, off: Int, headerLen: Int, dataLen: Int): Set<Int>? {
    if (headerLen != 12 || dataLen < 0 || off < 0 || off > bytes.size - headerLen ||
        dataLen > bytes.size - off - headerLen) return null
    val count = (0..3).fold(0L) { n, i -> n or ((bytes[off + 8 + i].toLong() and 255L) shl (8 * i)) }
    if (count != dataLen.toLong()) return null
    val ids = (0 until dataLen).map { bytes[off + headerLen + it].toInt() and 255 }
    if (ids.any { it !in 1..31 } || ids.distinct().size != ids.size) return null
    return ids.toSet()
}
