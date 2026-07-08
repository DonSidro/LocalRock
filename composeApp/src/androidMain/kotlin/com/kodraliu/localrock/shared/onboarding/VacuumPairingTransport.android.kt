package com.kodraliu.localrock.shared.onboarding

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.PatternMatcher
import com.kodraliu.localrock.shared.protocol.appContextOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("MissingPermission")
actual class VacuumPairingTransport internal constructor(private val context: Context) : AutoCloseable {

    private val cm: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkRef = AtomicReference<Network?>(null)
    private val callbackRef = AtomicReference<ConnectivityManager.NetworkCallback?>(null)
    private val socketRef = AtomicReference<DatagramSocket?>(null)

    actual suspend fun joinVacuumWifi(ssidPrefix: String, timeoutMs: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw VacuumPairingException("Vacuum onboarding requires Android 10 (API 29) or newer")
        }
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsidPattern(PatternMatcher(ssidPrefix, PatternMatcher.PATTERN_PREFIX))
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val deferred = CompletableDeferred<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                deferred.complete(network)
            }
            override fun onUnavailable() {
                deferred.completeExceptionally(
                    VacuumPairingException("Wi-Fi join failed: no matching network or user denied"),
                )
            }
            override fun onLost(network: Network) {

            }
        }
        callbackRef.set(callback)
        try {
            cm.requestNetwork(request, callback)
        } catch (e: Throwable) {
            callbackRef.set(null)
            throw VacuumPairingException("requestNetwork rejected (missing permission?): ${e.message}", e)
        }
        val net = withTimeoutOrNull(timeoutMs) { deferred.await() }
            ?: throw VacuumPairingException("Wi-Fi join timed out after ${timeoutMs}ms")
        networkRef.set(net)


        val socket = DatagramSocket(InetSocketAddress(0))
        try {
            net.bindSocket(socket)
        } catch (e: Throwable) {
            socket.close()
            throw VacuumPairingException("bindSocket failed: ${e.message}", e)
        }
        socketRef.set(socket)
    }

    actual suspend fun sendUdp(host: String, port: Int, data: ByteArray) {
        val socket = socketRef.get() ?: throw VacuumPairingException("Not joined")
        withContext(Dispatchers.IO) {
            val addr = InetAddress.getByName(host)
            socket.send(DatagramPacket(data, data.size, addr, port))
        }
    }

    actual suspend fun receiveUdp(timeoutMs: Long): ByteArray? {
        val socket = socketRef.get() ?: throw VacuumPairingException("Not joined")
        return withContext(Dispatchers.IO) {
            val buf = ByteArray(4096)
            val pkt = DatagramPacket(buf, buf.size)
            socket.soTimeout = timeoutMs.toInt().coerceAtLeast(1)
            try {
                socket.receive(pkt)
                pkt.data.copyOfRange(pkt.offset, pkt.offset + pkt.length)
            } catch (e: SocketTimeoutException) {
                null
            } catch (e: IOException) {
                throw VacuumPairingException("UDP receive failed: ${e.message}", e)
            }
        }
    }

    actual override fun close() {
        socketRef.getAndSet(null)?.close()
        callbackRef.getAndSet(null)?.let {
            try { cm.unregisterNetworkCallback(it) } catch (_: Throwable) { /* ignore */ }
        }
        networkRef.set(null)
    }
}

actual fun createVacuumPairingTransport(): VacuumPairingTransport {
    val ctx = appContextOrNull()
        ?: throw VacuumPairingException("Android context not initialized; call initVacLocalAndroidContext first")
    return VacuumPairingTransport(ctx)
}
