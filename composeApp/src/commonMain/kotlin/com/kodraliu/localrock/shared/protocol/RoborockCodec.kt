package com.kodraliu.localrock.shared.protocol

import com.kodraliu.localrock.shared.crypto.Aes128

class RoborockProtocolException(message: String) : RuntimeException(message)


class RoborockCodec(
    private val localKey: String,
    private val prefixed: Boolean,
) {
    private var buffer = ByteArray(0)

    fun encode(message: RoborockMessage): ByteArray {
        require(message.version == "1.0") { "Only version 1.0 is implemented (got ${message.version})" }
        val token = deriveToken(localKey, message.timestamp)
        val encryptedPayload = if (message.payload.isEmpty()) {
            ByteArray(0)
        } else {
            Aes128.encryptEcb(message.payload, token)
        }

        val headerLen = 3 + 4 + 4 + 4 + 2 + 2
        val bodyLen = headerLen + encryptedPayload.size
        val totalLen = bodyLen + 4 // body + CRC
        val frame = ByteArray(if (prefixed) 4 + totalLen else totalLen)
        var off = 0
        if (prefixed) {
            // python-roborock's PrefixedStruct writes (body + CRC) — i.e., everything that follows the prefix.
            putIntBe(frame, off, totalLen); off += 4
        }
        val bodyStart = off
        message.version.encodeToByteArray().copyInto(frame, off); off += 3
        putIntBe(frame, off, message.seq); off += 4
        putIntBe(frame, off, message.random); off += 4
        putIntBe(frame, off, message.timestamp); off += 4
        putShortBe(frame, off, message.protocol); off += 2
        putShortBe(frame, off, encryptedPayload.size); off += 2
        encryptedPayload.copyInto(frame, off); off += encryptedPayload.size
        val crc = Crc32.compute(frame, bodyStart, bodyLen)
        putIntBe(frame, off, crc)
        return frame
    }


    fun decode(bytesIn: ByteArray): List<RoborockMessage> {
        buffer = if (buffer.isEmpty()) bytesIn.copyOf() else buffer + bytesIn
        val out = mutableListOf<RoborockMessage>()
        while (true) {
            val parsed = tryParseOne(buffer) ?: break
            buffer = parsed.remaining
            out += parsed.message
        }
        return out
    }

    private data class ParseResult(val message: RoborockMessage, val remaining: ByteArray)

    private fun tryParseOne(data: ByteArray): ParseResult? {
        var pos = 0
        if (prefixed) {
            if (data.size < 4) return null
            val payloadAndCrcLen = readIntBe(data, 0) // body + CRC, per python-roborock
            if (payloadAndCrcLen < 23 || payloadAndCrcLen > 16 * 1024 * 1024) {
                throw RoborockProtocolException("Implausible message length: $payloadAndCrcLen")
            }
            if (data.size < 4 + payloadAndCrcLen) return null
            pos = 4
            val bodyLen = payloadAndCrcLen - 4
            val msg = parseMessage(data, pos, bodyLen)
            val end = 4 + payloadAndCrcLen
            val remaining = if (data.size == end) ByteArray(0) else data.copyOfRange(end, data.size)
            return ParseResult(msg, remaining)
        }

        val versionStart = findVersionHeader(data, 0) ?: return null
        if (data.size - versionStart < 19) return null
        val payloadLen = readShortBe(data, versionStart + 17)
        val bodyLen = 19 + payloadLen
        if (data.size - versionStart < bodyLen + 4) return null
        val msg = parseMessage(data, versionStart, bodyLen)
        val end = versionStart + bodyLen + 4
        val remaining = if (data.size == end) ByteArray(0) else data.copyOfRange(end, data.size)
        return ParseResult(msg, remaining)
    }

    private fun parseMessage(data: ByteArray, off: Int, bodyLen: Int): RoborockMessage {
        val version = data.decodeToString(off, off + 3)
        require(version == "1.0") { "Only version 1.0 is supported (got '$version')" }
        val seq = readIntBe(data, off + 3)
        val random = readIntBe(data, off + 7)
        val timestamp = readIntBe(data, off + 11)
        val protocol = readShortBe(data, off + 15)
        val payloadLen = readShortBe(data, off + 17)
        if (payloadLen != bodyLen - 19) {
            throw RoborockProtocolException("Inconsistent payload length")
        }
        val payloadCt = data.copyOfRange(off + 19, off + 19 + payloadLen)
        val expectedCrc = readIntBe(data, off + bodyLen)
        val actualCrc = Crc32.compute(data, off, bodyLen)
        if (expectedCrc != actualCrc) {
            throw RoborockProtocolException("CRC32 mismatch")
        }
        val payload = if (payloadCt.isEmpty()) {
            ByteArray(0)
        } else {
            val token = deriveToken(localKey, timestamp)
            Aes128.decryptEcb(payloadCt, token)
        }
        return RoborockMessage(
            protocol = protocol,
            payload = payload,
            seq = seq,
            random = random,
            timestamp = timestamp,
            version = version,
        )
    }

    private fun findVersionHeader(data: ByteArray, from: Int): Int? {
        var i = from
        while (i <= data.size - 3) {
            val v0 = data[i].toInt() and 0xff
            val v1 = data[i + 1].toInt() and 0xff
            val v2 = data[i + 2].toInt() and 0xff
            // "1.0" = 0x31,0x2e,0x30 ; "A01" = 0x41,0x30,0x31 ; "B01" = 0x42,0x30,0x31 ; "L01" = 0x4c,0x30,0x31
            if (v0 == 0x31 && v1 == 0x2e && v2 == 0x30) return i
            if ((v0 == 0x41 || v0 == 0x42 || v0 == 0x4c) && v1 == 0x30 && v2 == 0x31) return i
            i++
        }
        return null
    }

    private fun putIntBe(out: ByteArray, off: Int, v: Int) {
        out[off] = (v ushr 24).toByte()
        out[off + 1] = (v ushr 16).toByte()
        out[off + 2] = (v ushr 8).toByte()
        out[off + 3] = v.toByte()
    }

    private fun putShortBe(out: ByteArray, off: Int, v: Int) {
        out[off] = (v ushr 8).toByte()
        out[off + 1] = v.toByte()
    }

    private fun readIntBe(data: ByteArray, off: Int): Int =
        ((data[off].toInt() and 0xff) shl 24) or
            ((data[off + 1].toInt() and 0xff) shl 16) or
            ((data[off + 2].toInt() and 0xff) shl 8) or
            (data[off + 3].toInt() and 0xff)

    private fun readShortBe(data: ByteArray, off: Int): Int =
        ((data[off].toInt() and 0xff) shl 8) or (data[off + 1].toInt() and 0xff)
}
