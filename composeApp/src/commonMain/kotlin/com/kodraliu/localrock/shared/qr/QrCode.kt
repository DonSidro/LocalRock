package com.kodraliu.localrock.shared.qr

/** A finished QR symbol: [size] modules square, true is a dark module. */
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && modules[y * size + x]

    fun toText(): String = buildString {
        for (y in 0 until size) {
            for (x in 0 until size) append(if (get(x, y)) '1' else '0')
            append('\n')
        }
    }
}

enum class QrLevel(val formatBits: Int, val index: Int) { L(1, 0), M(0, 1), Q(3, 2) }

/**
 * A small QR code encoder for short texts (versions 1 to 10), enough for a Matter setup payload.
 * Uses alphanumeric mode when the text allows it and byte mode otherwise.
 */
object QrEncoder {
    private const val ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
    private const val MAX_VERSION = 10

    // Index by level (L, M, Q), then version 1..10 (position 0 unused).
    private val ECC_PER_BLOCK = arrayOf(
        intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18),
        intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26),
        intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24),
    )
    private val BLOCKS = arrayOf(
        intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4),
        intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5),
        intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8),
    )

    fun encode(text: String, level: QrLevel = QrLevel.M): QrMatrix {
        val alnum = text.isNotEmpty() && text.all { it in ALPHANUMERIC }
        val bytes = text.encodeToByteArray()
        var version = 1
        while (true) {
            require(version <= MAX_VERSION) { "Text is too long for this QR encoder" }
            val capacityBits = dataCodewords(version, level) * 8
            val used = 4 + countBits(version, alnum) + if (alnum) dataBitsAlnum(text.length) else bytes.size * 8
            if (used <= capacityBits) break
            version++
        }
        val data = buildData(text, bytes, alnum, version, level)
        val all = addEccAndInterleave(data, version, level)
        return Symbol(version, level).build(all)
    }

    private fun countBits(version: Int, alnum: Boolean) = if (alnum) (if (version <= 9) 9 else 11) else (if (version <= 9) 8 else 16)

    private fun dataBitsAlnum(n: Int) = (n / 2) * 11 + (n % 2) * 6

    private fun rawModules(version: Int): Int {
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val align = version / 7 + 2
            result -= (25 * align - 10) * align - 55
            if (version >= 7) result -= 36
        }
        return result
    }

    private fun dataCodewords(version: Int, level: QrLevel): Int =
        rawModules(version) / 8 - ECC_PER_BLOCK[level.index][version] * BLOCKS[level.index][version]

    private fun buildData(text: String, bytes: ByteArray, alnum: Boolean, version: Int, level: QrLevel): ByteArray {
        val bits = ArrayList<Boolean>()
        fun put(value: Int, count: Int) { for (i in count - 1 downTo 0) bits.add((value ushr i) and 1 == 1) }
        if (alnum) {
            put(0b0010, 4)
            put(text.length, countBits(version, true))
            var i = 0
            while (i + 1 < text.length) {
                put(ALPHANUMERIC.indexOf(text[i]) * 45 + ALPHANUMERIC.indexOf(text[i + 1]), 11)
                i += 2
            }
            if (i < text.length) put(ALPHANUMERIC.indexOf(text[i]), 6)
        } else {
            put(0b0100, 4)
            put(bytes.size, countBits(version, false))
            for (b in bytes) put(b.toInt() and 0xFF, 8)
        }
        val capacity = dataCodewords(version, level) * 8
        put(0, minOf(4, capacity - bits.size))
        while (bits.size % 8 != 0) bits.add(false)
        val out = ByteArray(capacity / 8)
        for (i in bits.indices) if (bits[i]) out[i / 8] = (out[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
        var pad = 0xEC
        for (i in bits.size / 8 until out.size) {
            out[i] = pad.toByte()
            pad = pad xor (0xEC xor 0x11)
        }
        return out
    }

    private fun addEccAndInterleave(data: ByteArray, version: Int, level: QrLevel): ByteArray {
        val blocks = BLOCKS[level.index][version]
        val eccLen = ECC_PER_BLOCK[level.index][version]
        val raw = rawModules(version) / 8
        val shortBlocks = blocks - raw % blocks
        val shortLen = raw / blocks
        val divisor = rsDivisor(eccLen)
        val result = ArrayList<ByteArray>()
        var k = 0
        for (i in 0 until blocks) {
            val datLen = shortLen - eccLen + if (i < shortBlocks) 0 else 1
            val dat = data.copyOfRange(k, k + datLen)
            k += datLen
            val ecc = rsRemainder(dat, divisor)
            val block = ByteArray(shortLen + 1)
            dat.copyInto(block)
            ecc.copyInto(block, shortLen + 1 - eccLen)
            result.add(block)
        }
        val out = ByteArray(raw)
        var o = 0
        for (i in 0..shortLen) {
            for (j in 0 until blocks) {
                if (i != shortLen - eccLen || j >= shortBlocks) out[o++] = result[j][i]
            }
        }
        return out
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun rsDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                result[j] = gfMultiply(result[j], root)
                if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun rsRemainder(data: ByteArray, divisor: IntArray): ByteArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor result[0]
            for (i in 0 until result.size - 1) result[i] = result[i + 1]
            result[result.size - 1] = 0
            for (i in result.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
        }
        return ByteArray(result.size) { result[it].toByte() }
    }

    private class Symbol(val version: Int, val level: QrLevel) {
        val size = version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        fun build(codewords: ByteArray): QrMatrix {
            drawFunctionPatterns()
            drawCodewords(codewords)
            var bestMask = 0
            var bestPenalty = Int.MAX_VALUE
            for (mask in 0 until 8) {
                applyMask(mask)
                drawFormatBits(mask)
                val penalty = penalty()
                if (penalty < bestPenalty) { bestPenalty = penalty; bestMask = mask }
                applyMask(mask)
            }
            applyMask(bestMask)
            drawFormatBits(bestMask)
            val flat = BooleanArray(size * size)
            for (y in 0 until size) for (x in 0 until size) flat[y * size + x] = modules[y][x]
            return QrMatrix(size, flat)
        }

        private fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            isFunction[y][x] = true
        }

        private fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
            val align = alignmentPositions()
            for (i in align.indices) for (j in align.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == align.size - 1) || (i == align.size - 1 && j == 0)) continue
                alignment(align[i], align[j])
            }
            drawFormatBits(0)
            drawVersion()
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val count = version / 7 + 2
            val step = (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2
            val result = IntArray(count)
            result[0] = 6
            var pos = size - 7
            for (i in count - 1 downTo 1) { result[i] = pos; pos -= step }
            return result
        }

        private fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until size && y in 0 until size) set(x, y, dist != 2 && dist != 4)
            }
        }

        private fun alignment(cx: Int, cy: Int) {
            for (dy in -2..2) for (dx in -2..2) set(cx + dx, cy + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
        }

        private fun bit(value: Int, i: Int) = (value ushr i) and 1 != 0

        private fun drawFormatBits(mask: Int) {
            val data = level.formatBits shl 3 or mask
            var rem = data
            for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
            val bits = ((data shl 10) or rem) xor 0x5412
            for (i in 0..5) set(8, i, bit(bits, i))
            set(8, 7, bit(bits, 6))
            set(8, 8, bit(bits, 7))
            set(7, 8, bit(bits, 8))
            for (i in 9 until 15) set(14 - i, 8, bit(bits, i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(bits, i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(bits, i))
            set(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            var rem = version
            for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
            val bits = (version shl 12) or rem
            for (i in 0 until 18) {
                val dark = bit(bits, i)
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, dark)
                set(b, a, dark)
            }
        }

        private fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0 until 2) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!isFunction[y][x] && i < data.size * 8) {
                        modules[y][x] = bit(data[i ushr 3].toInt(), 7 - (i and 7))
                        i++
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert && !isFunction[y][x]) modules[y][x] = !modules[y][x]
            }
        }

        /** Runs of one colour, 2x2 blocks and the dark share; the finder-like rule is left out. */
        private fun penalty(): Int {
            var total = 0
            for (horizontal in listOf(true, false)) {
                for (a in 0 until size) {
                    var run = 1
                    for (b in 1 until size) {
                        val same = if (horizontal) modules[a][b] == modules[a][b - 1] else modules[b][a] == modules[b - 1][a]
                        if (same) {
                            run++
                            if (run == 5) total += 3 else if (run > 5) total++
                        } else run = 1
                    }
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) total += 3
            }
            var dark = 0
            for (row in modules) for (m in row) if (m) dark++
            val k = (kotlin.math.abs(dark * 20 - size * size * 10) + size * size - 1) / (size * size) - 1
            return total + k * 10
        }
    }
}
