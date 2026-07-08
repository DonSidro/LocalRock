package com.kodraliu.localrock.shared.crypto

internal object Aes128 {
    private const val BLOCK = 16
    private const val NK = 4
    private const val NR = 10
    private const val NB = 4

    fun encryptEcb(plaintext: ByteArray, key: ByteArray): ByteArray {
        require(key.size == BLOCK) { "AES-128 key must be 16 bytes" }
        if (plaintext.isEmpty()) return ByteArray(0)
        val padded = pkcs7Pad(plaintext)
        val out = ByteArray(padded.size)
        val rk = keyExpansion(key)
        var off = 0
        while (off < padded.size) {
            encryptBlock(padded, off, out, off, rk)
            off += BLOCK
        }
        return out
    }

    fun decryptEcb(ciphertext: ByteArray, key: ByteArray): ByteArray {
        require(key.size == BLOCK) { "AES-128 key must be 16 bytes" }
        if (ciphertext.isEmpty()) return ByteArray(0)
        require(ciphertext.size % BLOCK == 0) { "AES-ECB ciphertext must be a multiple of 16 bytes" }
        val out = ByteArray(ciphertext.size)
        val rk = keyExpansion(key)
        var off = 0
        while (off < ciphertext.size) {
            decryptBlock(ciphertext, off, out, off, rk)
            off += BLOCK
        }
        return pkcs7Unpad(out)
    }

    fun encryptCbc(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(key.size == BLOCK) { "AES-128 key must be 16 bytes" }
        require(iv.size == BLOCK) { "AES IV must be 16 bytes" }
        val padded = pkcs7Pad(plaintext)
        val out = ByteArray(padded.size)
        val rk = keyExpansion(key)
        val prev = iv.copyOf()
        var off = 0
        while (off < padded.size) {
            for (i in 0 until BLOCK) prev[i] = (padded[off + i].toInt() xor prev[i].toInt()).toByte()
            encryptBlock(prev, 0, out, off, rk)
            for (i in 0 until BLOCK) prev[i] = out[off + i]
            off += BLOCK
        }
        return out
    }

    fun decryptCbc(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(key.size == BLOCK) { "AES-128 key must be 16 bytes" }
        require(iv.size == BLOCK) { "AES IV must be 16 bytes" }
        require(ciphertext.size % BLOCK == 0) { "AES-CBC ciphertext must be a multiple of 16 bytes" }
        val out = ByteArray(ciphertext.size)
        val rk = keyExpansion(key)
        var prev = iv
        var off = 0
        while (off < ciphertext.size) {
            decryptBlock(ciphertext, off, out, off, rk)
            for (i in 0 until BLOCK) out[off + i] = (out[off + i].toInt() xor prev[i].toInt()).toByte()
            prev = ciphertext.copyOfRange(off, off + BLOCK)
            off += BLOCK
        }
        return pkcs7Unpad(out)
    }

    private fun pkcs7Pad(input: ByteArray): ByteArray {
        val padLen = BLOCK - (input.size % BLOCK)
        val out = ByteArray(input.size + padLen)
        input.copyInto(out)
        for (i in input.size until out.size) out[i] = padLen.toByte()
        return out
    }

    private fun pkcs7Unpad(input: ByteArray): ByteArray {
        require(input.isNotEmpty()) { "PKCS7 unpad: empty input" }
        val padLen = input.last().toInt() and 0xff
        require(padLen in 1..BLOCK && padLen <= input.size) { "PKCS7 unpad: bad pad length $padLen" }
        for (i in input.size - padLen until input.size) {
            require((input[i].toInt() and 0xff) == padLen) { "PKCS7 unpad: corrupt padding" }
        }
        return input.copyOfRange(0, input.size - padLen)
    }

    private fun encryptBlock(input: ByteArray, inOff: Int, output: ByteArray, outOff: Int, rk: IntArray) {
        val state = IntArray(16)
        for (i in 0 until 16) state[i] = input[inOff + i].toInt() and 0xff
        addRoundKey(state, rk, 0)
        for (round in 1 until NR) {
            subBytes(state)
            shiftRows(state)
            mixColumns(state)
            addRoundKey(state, rk, round)
        }
        subBytes(state)
        shiftRows(state)
        addRoundKey(state, rk, NR)
        for (i in 0 until 16) output[outOff + i] = state[i].toByte()
    }

    private fun decryptBlock(input: ByteArray, inOff: Int, output: ByteArray, outOff: Int, rk: IntArray) {
        val state = IntArray(16)
        for (i in 0 until 16) state[i] = input[inOff + i].toInt() and 0xff
        addRoundKey(state, rk, NR)
        for (round in NR - 1 downTo 1) {
            invShiftRows(state)
            invSubBytes(state)
            addRoundKey(state, rk, round)
            invMixColumns(state)
        }
        invShiftRows(state)
        invSubBytes(state)
        addRoundKey(state, rk, 0)
        for (i in 0 until 16) output[outOff + i] = state[i].toByte()
    }

    private fun keyExpansion(key: ByteArray): IntArray {
        val w = IntArray(NB * (NR + 1) * 4)
        for (i in 0 until NK * 4) w[i] = key[i].toInt() and 0xff
        var i = NK
        while (i < NB * (NR + 1)) {
            var t0 = w[(i - 1) * 4]
            var t1 = w[(i - 1) * 4 + 1]
            var t2 = w[(i - 1) * 4 + 2]
            var t3 = w[(i - 1) * 4 + 3]
            if (i % NK == 0) {
                val r = t0
                t0 = (SBOX[t1] and 0xff) xor (RCON[i / NK - 1] and 0xff)
                t1 = SBOX[t2] and 0xff
                t2 = SBOX[t3] and 0xff
                t3 = SBOX[r] and 0xff
            }
            w[i * 4] = w[(i - NK) * 4] xor t0
            w[i * 4 + 1] = w[(i - NK) * 4 + 1] xor t1
            w[i * 4 + 2] = w[(i - NK) * 4 + 2] xor t2
            w[i * 4 + 3] = w[(i - NK) * 4 + 3] xor t3
            i++
        }
        return w
    }

    private fun addRoundKey(state: IntArray, rk: IntArray, round: Int) {
        val base = round * NB * 4
        for (i in 0 until 16) state[i] = state[i] xor rk[base + i]
    }

    private fun subBytes(state: IntArray) {
        for (i in 0 until 16) state[i] = SBOX[state[i]] and 0xff
    }

    private fun invSubBytes(state: IntArray) {
        for (i in 0 until 16) state[i] = INV_SBOX[state[i]] and 0xff
    }

    private fun shiftRows(s: IntArray) {
        // state laid out column-major: s[col*4 + row]. Rotate row r left by r.
        val t1 = s[1]; s[1] = s[5]; s[5] = s[9]; s[9] = s[13]; s[13] = t1
        val t2a = s[2]; val t2b = s[6]; s[2] = s[10]; s[6] = s[14]; s[10] = t2a; s[14] = t2b
        val t3 = s[15]; s[15] = s[11]; s[11] = s[7]; s[7] = s[3]; s[3] = t3
    }

    private fun invShiftRows(s: IntArray) {
        val t1 = s[13]; s[13] = s[9]; s[9] = s[5]; s[5] = s[1]; s[1] = t1
        val t2a = s[2]; val t2b = s[6]; s[2] = s[10]; s[6] = s[14]; s[10] = t2a; s[14] = t2b
        val t3 = s[3]; s[3] = s[7]; s[7] = s[11]; s[11] = s[15]; s[15] = t3
    }

    private fun mixColumns(s: IntArray) {
        for (c in 0 until 4) {
            val a0 = s[c * 4]; val a1 = s[c * 4 + 1]; val a2 = s[c * 4 + 2]; val a3 = s[c * 4 + 3]
            s[c * 4]     = (xtime(a0) xor (xtime(a1) xor a1) xor a2 xor a3) and 0xff
            s[c * 4 + 1] = (a0 xor xtime(a1) xor (xtime(a2) xor a2) xor a3) and 0xff
            s[c * 4 + 2] = (a0 xor a1 xor xtime(a2) xor (xtime(a3) xor a3)) and 0xff
            s[c * 4 + 3] = ((xtime(a0) xor a0) xor a1 xor a2 xor xtime(a3)) and 0xff
        }
    }

    private fun invMixColumns(s: IntArray) {
        for (c in 0 until 4) {
            val a0 = s[c * 4]; val a1 = s[c * 4 + 1]; val a2 = s[c * 4 + 2]; val a3 = s[c * 4 + 3]
            s[c * 4]     = (gmul(a0, 0x0e) xor gmul(a1, 0x0b) xor gmul(a2, 0x0d) xor gmul(a3, 0x09)) and 0xff
            s[c * 4 + 1] = (gmul(a0, 0x09) xor gmul(a1, 0x0e) xor gmul(a2, 0x0b) xor gmul(a3, 0x0d)) and 0xff
            s[c * 4 + 2] = (gmul(a0, 0x0d) xor gmul(a1, 0x09) xor gmul(a2, 0x0e) xor gmul(a3, 0x0b)) and 0xff
            s[c * 4 + 3] = (gmul(a0, 0x0b) xor gmul(a1, 0x0d) xor gmul(a2, 0x09) xor gmul(a3, 0x0e)) and 0xff
        }
    }

    private fun xtime(b: Int): Int = ((b shl 1) xor (if (b and 0x80 != 0) 0x1b else 0)) and 0xff

    private fun gmul(a: Int, b: Int): Int {
        var x = a and 0xff
        var y = b and 0xff
        var r = 0
        repeat(8) {
            if (y and 1 != 0) r = r xor x
            val hi = x and 0x80
            x = (x shl 1) and 0xff
            if (hi != 0) x = x xor 0x1b
            y = y shr 1
        }
        return r and 0xff
    }

    private val RCON = intArrayOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36)

    private val SBOX = intArrayOf(
        0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
        0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
        0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
        0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
        0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
        0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
        0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
        0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
        0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
        0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
        0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
        0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
        0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
        0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
        0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
        0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16,
    )

    private val INV_SBOX = intArrayOf(
        0x52, 0x09, 0x6a, 0xd5, 0x30, 0x36, 0xa5, 0x38, 0xbf, 0x40, 0xa3, 0x9e, 0x81, 0xf3, 0xd7, 0xfb,
        0x7c, 0xe3, 0x39, 0x82, 0x9b, 0x2f, 0xff, 0x87, 0x34, 0x8e, 0x43, 0x44, 0xc4, 0xde, 0xe9, 0xcb,
        0x54, 0x7b, 0x94, 0x32, 0xa6, 0xc2, 0x23, 0x3d, 0xee, 0x4c, 0x95, 0x0b, 0x42, 0xfa, 0xc3, 0x4e,
        0x08, 0x2e, 0xa1, 0x66, 0x28, 0xd9, 0x24, 0xb2, 0x76, 0x5b, 0xa2, 0x49, 0x6d, 0x8b, 0xd1, 0x25,
        0x72, 0xf8, 0xf6, 0x64, 0x86, 0x68, 0x98, 0x16, 0xd4, 0xa4, 0x5c, 0xcc, 0x5d, 0x65, 0xb6, 0x92,
        0x6c, 0x70, 0x48, 0x50, 0xfd, 0xed, 0xb9, 0xda, 0x5e, 0x15, 0x46, 0x57, 0xa7, 0x8d, 0x9d, 0x84,
        0x90, 0xd8, 0xab, 0x00, 0x8c, 0xbc, 0xd3, 0x0a, 0xf7, 0xe4, 0x58, 0x05, 0xb8, 0xb3, 0x45, 0x06,
        0xd0, 0x2c, 0x1e, 0x8f, 0xca, 0x3f, 0x0f, 0x02, 0xc1, 0xaf, 0xbd, 0x03, 0x01, 0x13, 0x8a, 0x6b,
        0x3a, 0x91, 0x11, 0x41, 0x4f, 0x67, 0xdc, 0xea, 0x97, 0xf2, 0xcf, 0xce, 0xf0, 0xb4, 0xe6, 0x73,
        0x96, 0xac, 0x74, 0x22, 0xe7, 0xad, 0x35, 0x85, 0xe2, 0xf9, 0x37, 0xe8, 0x1c, 0x75, 0xdf, 0x6e,
        0x47, 0xf1, 0x1a, 0x71, 0x1d, 0x29, 0xc5, 0x89, 0x6f, 0xb7, 0x62, 0x0e, 0xaa, 0x18, 0xbe, 0x1b,
        0xfc, 0x56, 0x3e, 0x4b, 0xc6, 0xd2, 0x79, 0x20, 0x9a, 0xdb, 0xc0, 0xfe, 0x78, 0xcd, 0x5a, 0xf4,
        0x1f, 0xdd, 0xa8, 0x33, 0x88, 0x07, 0xc7, 0x31, 0xb1, 0x12, 0x10, 0x59, 0x27, 0x80, 0xec, 0x5f,
        0x60, 0x51, 0x7f, 0xa9, 0x19, 0xb5, 0x4a, 0x0d, 0x2d, 0xe5, 0x7a, 0x9f, 0x93, 0xc9, 0x9c, 0xef,
        0xa0, 0xe0, 0x3b, 0x4d, 0xae, 0x2a, 0xf5, 0xb0, 0xc8, 0xeb, 0xbb, 0x3c, 0x83, 0x53, 0x99, 0x61,
        0x17, 0x2b, 0x04, 0x7e, 0xba, 0x77, 0xd6, 0x26, 0xe1, 0x69, 0x14, 0x63, 0x55, 0x21, 0x0c, 0x7d,
    )
}
