package com.kodraliu.localrock.shared.testing

internal fun gzipForTest(content: ByteArray): ByteArray {
    // Hand-rolled minimal gzip wrapping a "stored" DEFLATE block, so we don't need the
    // platform compressor to run encode-side.
    val deflate = storedDeflate(content)
    val crc = crc32(content)
    val isize = content.size.toUInt()
    val out = mutableListOf<Byte>()
    out += 0x1f.toByte(); out += 0x8b.toByte()  // magic
    out += 0x08.toByte()                        // method = deflate
    out += 0x00.toByte()                        // flags
    repeat(4) { out += 0x00.toByte() }          // mtime
    out += 0x00.toByte()                        // xfl
    out += 0xff.toByte()                        // os = unknown
    out.addAll(deflate.toList())
    for (i in 0..3) out += ((crc shr (i * 8)) and 0xffu).toInt().toByte()
    for (i in 0..3) out += ((isize shr (i * 8)) and 0xffu).toInt().toByte()
    return out.toByteArray()
}

private fun storedDeflate(content: ByteArray): ByteArray {
    // Single stored (BTYPE=00) block. BFINAL=1.
    val len = content.size
    require(len <= 0xffff) { "stored block too large for this test helper" }
    val out = ByteArray(5 + len)
    out[0] = 0x01 // BFINAL=1, BTYPE=00
    out[1] = (len and 0xff).toByte()
    out[2] = ((len ushr 8) and 0xff).toByte()
    out[3] = (len.inv() and 0xff).toByte()
    out[4] = ((len.inv() ushr 8) and 0xff).toByte()
    content.copyInto(out, 5)
    return out
}

private fun crc32(data: ByteArray): UInt {
    var c = 0xffffffffu
    for (b in data) {
        c = c xor (b.toUInt() and 0xffu)
        repeat(8) {
            c = if ((c and 1u) != 0u) (c shr 1) xor 0xedb88320u else c shr 1
        }
    }
    return c.inv()
}
