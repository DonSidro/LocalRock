package com.kodraliu.localrock.shared.onboarding


expect class VacuumPairingTransport : AutoCloseable {

    suspend fun joinVacuumWifi(ssidPrefix: String = "roborock-vacuum-", timeoutMs: Long = 30_000L)

    suspend fun sendUdp(host: String, port: Int, data: ByteArray)

    suspend fun receiveUdp(timeoutMs: Long): ByteArray?

    override fun close()
}

class VacuumPairingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

expect fun createVacuumPairingTransport(): VacuumPairingTransport

/** False when the platform cannot pair over Wi-Fi, so the UI can hide the flow instead of failing. */
expect val vacuumPairingSupported: Boolean

const val VACUUM_PAIRING_HOST: String = "192.168.8.1"
const val VACUUM_PAIRING_PORT: Int = 55559
