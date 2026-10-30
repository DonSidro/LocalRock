package com.kodraliu.localrock.shared.vacuum.map

/**
 * A legacy block-25 record. Keep every byte: byte 21 is the persistent furniture ID verified
 * against S7 MaxV edit commands and map readback. Bytes 16-17 and 22 remain uninterpreted.
 * The 23-byte layout and display flag were checked on an S7 MaxV; other layouts are not guessed.
 */
data class MapFurniture internal constructor(val rawBytes: List<Int>) {
    init { require(rawBytes.size == FURNITURE_RECORD_SIZE) }

    val cornersMm: List<ParsedMapPoint> = List(4) { i ->
        ParsedMapPoint(rawBytes.u16(i * 4), rawBytes.u16(i * 4 + 2))
    }
    val type: Int get() = rawBytes[18]
    val subtype: Int get() = rawBytes[19]
    val displayFlag: Int get() = rawBytes[20]
    val id: Int get() = rawBytes[21]

    /** Only flag 1 was verified as displayed. Hidden and unfamiliar flags remain in the model. */
    val isVisible: Boolean get() = displayFlag == 1 && hasUsableFootprint

    /** Reject collapsed or crossed quads for drawing, without dropping their raw records. */
    val hasUsableFootprint: Boolean get() {
        val turns = cornersMm.indices.map { i ->
            val a = cornersMm[i]
            val b = cornersMm[(i + 1) % 4]
            val c = cornersMm[(i + 2) % 4]
            (b.x - a.x).toLong() * (c.y - b.y) - (b.y - a.y).toLong() * (c.x - b.x)
        }
        return turns.all { it > 0L } || turns.all { it < 0L }
    }
}

/** Retain the complete block, including unfamiliar layouts and records that are not displayed. */
data class MapFurnitureBlock(val headerBytes: List<Int>, val bodyBytes: List<Int>) {
    val hasKnownLayout: Boolean get() {
        if (headerBytes.size != 12) return false
        val count = (0..3).fold(0L) { n, i -> n or (headerBytes[8 + i].toLong() shl (8 * i)) }
        return count * FURNITURE_RECORD_SIZE == bodyBytes.size.toLong()
    }
    val records: List<MapFurniture> by lazy {
        // A different header or stride may be a different format. Do not misalign its records.
        if (!hasKnownLayout) return@lazy emptyList()
        bodyBytes.chunked(FURNITURE_RECORD_SIZE).map { MapFurniture(it) }
    }
}

internal fun parseFurnitureBlock(bytes: ByteArray, off: Int, headerLen: Int, dataLen: Int): MapFurnitureBlock {
    require(off >= 0 && off <= bytes.size && headerLen >= 8 && headerLen <= bytes.size - off)
    require(dataLen >= 0 && dataLen <= bytes.size - off - headerLen)
    return MapFurnitureBlock(
        headerBytes = bytes.copyOfRange(off, off + headerLen).map { it.toInt() and 0xff },
        bodyBytes = bytes.copyOfRange(off + headerLen, off + headerLen + dataLen).map { it.toInt() and 0xff },
    )
}

private const val FURNITURE_RECORD_SIZE = 23
private fun List<Int>.u16(off: Int): Int = this[off] or (this[off + 1] shl 8)
