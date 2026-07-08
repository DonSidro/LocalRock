package com.kodraliu.localrock.shared.onboarding

import com.kodraliu.localrock.shared.protocol.Crc32


data class CfgWifiFrame(val commandId: Int, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is CfgWifiFrame && other.commandId == commandId && other.payload.contentEquals(payload)
    override fun hashCode(): Int = 31 * commandId + payload.contentHashCode()
}

class CfgWifiFrameException(message: String) : RuntimeException(message)

object CfgWifi {

    const val CMD_HELLO = 16
    const val CMD_WIFI_CONFIG = 1

    private val MAGIC: ByteArray = byteArrayOf(0x31, 0x2e, 0x30)
    private val VERSION: ByteArray = byteArrayOf(0x00, 0x00, 0x00, 0x01)
    private const val HEADER_SIZE = 11
    private const val TRAILER_SIZE = 4

    fun build(commandId: Int, payload: ByteArray): ByteArray {
        require(commandId in 0..0xffff) { "command id out of range: $commandId" }
        require(payload.size in 0..0xffff) { "payload too large: ${payload.size}" }
        val out = ByteArray(HEADER_SIZE + payload.size + TRAILER_SIZE)
        MAGIC.copyInto(out, 0)
        VERSION.copyInto(out, 3)
        out[7] = ((commandId ushr 8) and 0xff).toByte()
        out[8] = (commandId and 0xff).toByte()
        out[9] = ((payload.size ushr 8) and 0xff).toByte()
        out[10] = (payload.size and 0xff).toByte()
        payload.copyInto(out, HEADER_SIZE)
        val crc = Crc32.compute(out, 0, HEADER_SIZE + payload.size)
        out[out.size - 4] = ((crc ushr 24) and 0xff).toByte()
        out[out.size - 3] = ((crc ushr 16) and 0xff).toByte()
        out[out.size - 2] = ((crc ushr 8) and 0xff).toByte()
        out[out.size - 1] = (crc and 0xff).toByte()
        return out
    }

    fun parse(packet: ByteArray): CfgWifiFrame {
        if (packet.size < HEADER_SIZE + TRAILER_SIZE) {
            throw CfgWifiFrameException("frame too short: ${packet.size}")
        }
        for (i in MAGIC.indices) if (packet[i] != MAGIC[i]) {
            throw CfgWifiFrameException("bad magic at byte $i")
        }
        for (i in VERSION.indices) if (packet[3 + i] != VERSION[i]) {
            throw CfgWifiFrameException("unexpected version bytes")
        }
        val cmd = ((packet[7].toInt() and 0xff) shl 8) or (packet[8].toInt() and 0xff)
        val payloadLen = ((packet[9].toInt() and 0xff) shl 8) or (packet[10].toInt() and 0xff)
        if (HEADER_SIZE + payloadLen + TRAILER_SIZE != packet.size) {
            throw CfgWifiFrameException("payload length mismatch: header says $payloadLen, packet=${packet.size}")
        }
        val expectedCrc = Crc32.compute(packet, 0, HEADER_SIZE + payloadLen)
        val gotCrc = ((packet[packet.size - 4].toInt() and 0xff) shl 24) or
            ((packet[packet.size - 3].toInt() and 0xff) shl 16) or
            ((packet[packet.size - 2].toInt() and 0xff) shl 8) or
            (packet[packet.size - 1].toInt() and 0xff)
        if (expectedCrc != gotCrc) throw CfgWifiFrameException("CRC32 mismatch")
        val payload = packet.copyOfRange(HEADER_SIZE, HEADER_SIZE + payloadLen)
        return CfgWifiFrame(commandId = cmd, payload = payload)
    }
}
