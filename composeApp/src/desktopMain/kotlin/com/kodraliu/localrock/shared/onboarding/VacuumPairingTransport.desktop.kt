package com.kodraliu.localrock.shared.onboarding

/**
 * Pairing a new vacuum means joining its Wi-Fi access point from the app, which the desktop build
 * does not do. Pair from the phone app (or the server's onboarding tool); the desktop app then sees
 * the vacuum through the server like any other.
 */
actual class VacuumPairingTransport : AutoCloseable {
    actual suspend fun joinVacuumWifi(ssidPrefix: String, timeoutMs: Long): Unit = unsupported()
    actual suspend fun joinedSsid(): String? = null
    actual suspend fun sendUdp(host: String, port: Int, data: ByteArray): Unit = unsupported()
    actual suspend fun receiveUdp(timeoutMs: Long): ByteArray? = unsupported()
    actual override fun close() = Unit

    private fun unsupported(): Nothing =
        throw VacuumPairingException("Adding a vacuum is not available in the desktop app; use the phone app")
}

actual fun createVacuumPairingTransport(): VacuumPairingTransport = VacuumPairingTransport()

actual val vacuumPairingSupported: Boolean = false
