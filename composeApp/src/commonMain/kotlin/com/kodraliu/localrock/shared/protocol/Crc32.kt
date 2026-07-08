package com.kodraliu.localrock.shared.protocol

internal object Crc32 {
    private val TABLE: IntArray = IntArray(256).also { t ->
        for (i in 0 until 256) {
            var c = i
            repeat(8) { c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1 }
            t[i] = c
        }
    }

    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var c = -1 // 0xFFFFFFFF
        for (i in offset until offset + length) {
            c = (c ushr 8) xor TABLE[(c xor data[i].toInt()) and 0xff]
        }
        return c.inv()
    }
}
