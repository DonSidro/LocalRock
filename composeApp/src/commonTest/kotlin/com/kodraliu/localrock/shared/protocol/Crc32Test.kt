package com.kodraliu.localrock.shared.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class Crc32Test {

    @Test
    fun empty() {
        assertEquals(0, Crc32.compute(ByteArray(0)))
    }

    // Known IEEE 802.3 / zlib CRC32 values (matches Python's binascii.crc32).
    @Test
    fun knownValues() {
        assertEquals(0xCBF43926.toInt(), Crc32.compute("123456789".encodeToByteArray()))
        assertEquals(0xE8B7BE43.toInt(), Crc32.compute("a".encodeToByteArray()))
        assertEquals(0x414FA339.toInt(), Crc32.compute("The quick brown fox jumps over the lazy dog".encodeToByteArray()))
    }

    @Test
    fun offsetAndLength() {
        val data = "garbage_123456789_trailing".encodeToByteArray()
        val start = "garbage_".length
        assertEquals(0xCBF43926.toInt(), Crc32.compute(data, start, 9))
    }
}
