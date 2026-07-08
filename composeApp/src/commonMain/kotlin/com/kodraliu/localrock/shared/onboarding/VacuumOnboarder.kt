package com.kodraliu.localrock.shared.onboarding

import com.kodraliu.localrock.shared.protocol.secureRandomBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull


class VacuumOnboarder(
    private val transportFactory: () -> VacuumPairingTransport = ::createVacuumPairingTransport,
    private val keyPair: RsaKeyPair = generateRsa1024KeyPair(),
) {
    enum class Step { Idle, JoiningWifi, SendingHello, AwaitingHello, SendingWifiConfig, AwaitingAck, Done }

    private val _step = MutableStateFlow(Step.Idle)
    val step: StateFlow<Step> = _step

    suspend fun run(input: OnboardingInput): OnboardingResult {
        val transport = transportFactory()
        try {
            _step.value = Step.JoiningWifi
            transport.joinVacuumWifi()
            _step.value = Step.SendingHello
            val hello = buildHelloPacket(publicKeyPem = keyPair.publicKeyPem)
            transport.sendUdp(VACUUM_PAIRING_HOST, VACUUM_PAIRING_PORT, hello)
            _step.value = Step.AwaitingHello
            val resp = withTimeoutOrNull(HELLO_TIMEOUT_MS) {
                transport.receiveUdp(HELLO_TIMEOUT_MS)
            } ?: throw VacuumPairingException("Vacuum did not reply to hello within ${HELLO_TIMEOUT_MS}ms")
            val sessionKey = parseHelloResponse(resp, keyPair)
            val body = WifiConfigBody(
                ssid = input.homeSsid,
                password = input.homePassword,
                serverStack = sanitizeStackServer(input.serverBaseUrl),
                timezone = input.timezone,
                posixTz = posixTzFromIana(input.timezone),
                countryDomain = countryDomainFromIana(input.timezone),
                tokenS = "S_TOKEN_" + bytesToHex(secureRandomBytes(16)),
                tokenT = "T_TOKEN_" + bytesToHex(secureRandomBytes(16)),
            )
            _step.value = Step.SendingWifiConfig
            transport.sendUdp(VACUUM_PAIRING_HOST, VACUUM_PAIRING_PORT, buildWifiConfigPacket(sessionKey, body))
            _step.value = Step.AwaitingAck
            transport.receiveUdp(WIFI_ACK_TIMEOUT_MS)
            _step.value = Step.Done
            return OnboardingResult(sessionKey = sessionKey, tokenS = body.tokenS, tokenT = body.tokenT)
        } finally {
            transport.close()

        }
    }

    private fun bytesToHex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (byte in b) {
            val v = byte.toInt() and 0xff
            sb.append(HEX[v ushr 4]); sb.append(HEX[v and 0x0f])
        }
        return sb.toString()
    }

    private companion object {
        const val HELLO_TIMEOUT_MS: Long = 5_000L
        const val WIFI_ACK_TIMEOUT_MS: Long = 5_000L
        val HEX = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    }
}

data class OnboardingInput(
    val homeSsid: String,
    val homePassword: String,
    val serverBaseUrl: String,
    val timezone: String = DEFAULT_IANA_TZ,
)

data class OnboardingResult(
    val sessionKey: String,
    val tokenS: String,
    val tokenT: String,
)
