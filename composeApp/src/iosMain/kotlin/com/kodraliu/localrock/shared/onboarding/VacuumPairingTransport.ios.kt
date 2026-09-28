package com.kodraliu.localrock.shared.onboarding

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS Wi-Fi pairing lives on the Swift side: joining the vacuum's access point needs
 * `NEHotspotConfiguration` (NetworkExtension) and the UDP handshake needs `NWConnection`
 * (Network.framework), neither of which is reachable from Kotlin/Native. This mirrors the MQTT and
 * WebRTC approach: the Swift app implements the callback-based, non-suspend [VacuumPairingNative]
 * and injects a factory via `MainViewController`.
 *
 * See `iosApp/iosApp/VacuumPairing.swift`.
 */

/** One pairing session, implemented in Swift. Non-suspend so Swift can implement it. */
interface VacuumPairingNative {
    fun joinWifi(
        ssidPrefix: String,
        timeoutMs: Long,
        onJoined: () -> Unit,
        onError: (String) -> Unit,
    )

    fun send(
        host: String,
        port: Int,
        data: ByteArray,
        onSent: () -> Unit,
        onError: (String) -> Unit,
    )

    /** Calls back with null when nothing arrived within [timeoutMs]. */
    fun receive(
        timeoutMs: Long,
        onReceived: (ByteArray?) -> Unit,
        onError: (String) -> Unit,
    )

    /** Tears down the socket and puts the phone back on its normal Wi-Fi network. */
    fun close()
}

interface VacuumPairingNativeFactory {
    fun create(): VacuumPairingNative
}

/** Holds the Swift-injected factory; set once from `MainViewController(pairingFactory:)`. */
object IosPairing {
    var factory: VacuumPairingNativeFactory? = null
}

actual val vacuumPairingSupported: Boolean
    get() = IosPairing.factory != null

actual class VacuumPairingTransport internal constructor(
    private val native: VacuumPairingNative,
) : AutoCloseable {

    actual suspend fun joinVacuumWifi(ssidPrefix: String, timeoutMs: Long): Unit =
        suspendCancellableCoroutine { cont ->
            native.joinWifi(
                ssidPrefix = ssidPrefix,
                timeoutMs = timeoutMs,
                onJoined = { cont.resume(Unit) },
                onError = { cont.resumeWithException(VacuumPairingException(it)) },
            )
        }

    actual suspend fun sendUdp(host: String, port: Int, data: ByteArray): Unit =
        suspendCancellableCoroutine { cont ->
            native.send(
                host = host,
                port = port,
                data = data,
                onSent = { cont.resume(Unit) },
                onError = { cont.resumeWithException(VacuumPairingException(it)) },
            )
        }

    actual suspend fun receiveUdp(timeoutMs: Long): ByteArray? =
        suspendCancellableCoroutine { cont ->
            native.receive(
                timeoutMs = timeoutMs,
                onReceived = { bytes -> cont.resume(bytes) },
                onError = { cont.resumeWithException(VacuumPairingException(it)) },
            )
        }

    actual override fun close() {
        native.close()
    }
}

actual fun createVacuumPairingTransport(): VacuumPairingTransport {
    val factory = IosPairing.factory
        ?: throw VacuumPairingException(
            "Wi-Fi pairing is unavailable — no pairing factory was injected into MainViewController"
        )
    return VacuumPairingTransport(factory.create())
}
