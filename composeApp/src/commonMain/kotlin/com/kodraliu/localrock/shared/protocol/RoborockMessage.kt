package com.kodraliu.localrock.shared.protocol


data class RoborockMessage(
    val protocol: Int,
    val payload: ByteArray,
    val seq: Int,
    val random: Int,
    val timestamp: Int,
    val version: String = "1.0",
) {
    override fun equals(other: Any?): Boolean =
        other is RoborockMessage &&
            other.protocol == protocol &&
            other.payload.contentEquals(payload) &&
            other.seq == seq &&
            other.random == random &&
            other.timestamp == timestamp &&
            other.version == version

    override fun hashCode(): Int {
        var r = protocol
        r = 31 * r + payload.contentHashCode()
        r = 31 * r + seq
        r = 31 * r + random
        r = 31 * r + timestamp
        r = 31 * r + version.hashCode()
        return r
    }
}

object RoborockProtocol {
    const val HELLO_REQUEST = 0
    const val HELLO_RESPONSE = 1
    const val PING_REQUEST = 2
    const val PING_RESPONSE = 3
    const val GENERAL_REQUEST = 4
    const val GENERAL_RESPONSE = 5
    const val RPC_REQUEST = 101
    const val RPC_RESPONSE = 102
    const val MAP_RESPONSE = 301
}
