package com.kodraliu.localrock.shared.onboarding

actual class VacuumPairingTransport internal constructor() : AutoCloseable {
    actual suspend fun joinVacuumWifi(ssidPrefix: String, timeoutMs: Long) {
        throw NotImplementedError("VacuumPairingTransport is not yet implemented on iOS")
    }
    actual suspend fun sendUdp(host: String, port: Int, data: ByteArray) {
        throw NotImplementedError("VacuumPairingTransport is not yet implemented on iOS")
    }
    actual suspend fun receiveUdp(timeoutMs: Long): ByteArray? {
        throw NotImplementedError("VacuumPairingTransport is not yet implemented on iOS")
    }
    actual override fun close() { /* nothing to clean up on the stub */ }
}

actual fun createVacuumPairingTransport(): VacuumPairingTransport =
    throw NotImplementedError("Vacuum onboarding is not yet implemented on iOS")
