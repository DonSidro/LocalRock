package com.kodraliu.localrock.shared

import com.russhwolf.settings.Settings
import com.kodraliu.localrock.shared.auth.AuthRepository
import com.kodraliu.localrock.shared.auth.LoginApi
import com.kodraliu.localrock.shared.device.DeviceApi
import com.kodraliu.localrock.shared.device.DeviceRepository
import com.kodraliu.localrock.shared.http.HttpClientConfig
import com.kodraliu.localrock.shared.http.buildAdminHttpClient
import com.kodraliu.localrock.shared.http.buildVacLocalHttpClient
import com.kodraliu.localrock.shared.http.currentEpochSeconds
import com.kodraliu.localrock.shared.onboarding.OnboardingAdminApi
import com.kodraliu.localrock.shared.model.Device
import com.kodraliu.localrock.shared.mqtt.MqttClient
import com.kodraliu.localrock.shared.mqtt.MqttNativeTransport
import com.kodraliu.localrock.shared.mqtt.defaultMqttTransport
import com.kodraliu.localrock.shared.mqtt.deriveMqttCreds
import com.kodraliu.localrock.shared.mqtt.mqttTopicsFor
import com.kodraliu.localrock.shared.messages.MessageCenter
import com.kodraliu.localrock.shared.messages.MessageSeverity
import com.kodraliu.localrock.shared.settings.AppSettings
import com.kodraliu.localrock.shared.vacuum.VacuumRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppContainer(
    settings: Settings,
    mqttTransport: MqttNativeTransport? = defaultMqttTransport(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val appSettings: AppSettings = AppSettings(settings)

    val messages: MessageCenter = MessageCenter()

    val httpClient: HttpClient = buildVacLocalHttpClient(
        HttpClientConfig(
            baseUrlProvider = { appSettings.serverBaseUrl.value },
            credsProvider = { authRepository.hawkCreds() },
        )
    )

    val adminHttpClient: HttpClient = buildAdminHttpClient(
        HttpClientConfig(
            baseUrlProvider = { appSettings.serverBaseUrl.value },
            credsProvider = { null },
        )
    )

    private val loginApi = LoginApi(httpClient)
    private val deviceApi = DeviceApi(httpClient)
    val onboardingAdminApi: OnboardingAdminApi = OnboardingAdminApi(adminHttpClient)

    val authRepository: AuthRepository = AuthRepository(appSettings, loginApi)
    val deviceRepository: DeviceRepository =
        DeviceRepository(deviceApi, authRepository, isDemo = { appSettings.demoMode.value })

    private val isDemo: Boolean get() = appSettings.demoMode.value

    val mqttClient: MqttClient = MqttClient(
        mqttTransport ?: error("No MQTT transport available — iOS must inject one via MainViewController(mqttTransport:)")
    )
    private val mqttMutex = Mutex()
    private var mqttConnected = false

    /** How long an open-but-silent link may sit before a foreground reconnect is worth it. */
    private val FOREGROUND_STALE_MS = 90_000L

    suspend fun ensureMqttConnected() {
        if (isDemo) return
        if (mqttConnected) return
        mqttMutex.withLock {
            if (mqttConnected) return
            val ud = authRepository.userData.value
                ?: error("ensureMqttConnected called before login")
            val creds = deriveMqttCreds(ud.rriot)

            val clientId = "vaclocal-${ud.rruid}-${appSettings.installId}"
            println("[VacLocal] MQTT connecting host=${creds.host}:${creds.port} tls=${creds.tls}")
            mqttClient.credentialRefresher = ::refreshMqttCredentials
            mqttClient.connect(creds, clientId = clientId)
            println("[VacLocal] MQTT connected")
            mqttConnected = true
            observeMqttConnection()
        }
    }

    /**
     * Repeated reconnect failures usually mean the server has expired our session rather than that
     * the network is down, so sign in again and hand back credentials derived from the new session.
     */
    private suspend fun refreshMqttCredentials(attempt: Int, error: Throwable) =
        when {
            isDemo -> null
            !authRepository.canReLogin -> {
                messages.showBanner(
                    key = MessageCenter.BANNER_MQTT,
                    text = "Session expired — sign out and sign in again to reconnect.",
                    severity = MessageSeverity.ERROR,
                    dismissible = false,
                )
                null
            }
            else -> {
                println("[VacLocal] MQTT failed $attempt times (${error.message}) — re-authenticating")
                // Live VacuumRepositories resolve their topics from userData on every poll, so a
                // renewed session is picked up without rebuilding them.
                authRepository.reLogin()?.let { fresh -> deriveMqttCreds(fresh.rriot) }
            }
        }

    private var observingMqtt = false


    private fun observeMqttConnection() {
        if (observingMqtt) return
        observingMqtt = true
        scope.launch {
            var wasOffline = false
            mqttClient.connected.collect { up ->
                if (up) {
                    messages.dismissBanner(MessageCenter.BANNER_MQTT)
                    if (wasOffline) messages.success("Back online")
                    wasOffline = false
                } else {
                    messages.showBanner(
                        key = MessageCenter.BANNER_MQTT,
                        text = "Offline — reconnecting…",
                        severity = MessageSeverity.WARNING,
                        dismissible = false,
                    )
                    wasOffline = true
                }
            }
        }
    }


    /**
     * Coming back to the foreground used to tear down the MQTT session unconditionally, costing a
     * reconnect and a data gap every time the app was switched away from. Only do it when the link
     * looks genuinely stuck: reported down, or open but silent for a long time.
     */
    suspend fun onEnterForeground() {
        if (!mqttConnected) return
        if (!mqttClient.connected.value) return // the supervisor is already reconnecting
        val silentMs = mqttClient.millisSinceLastMessage()
        if (silentMs < FOREGROUND_STALE_MS) return
        println("[VacLocal] foreground: link silent ${silentMs}ms — reconnecting")
        mqttClient.forceReconnect()
    }

    fun vacuumRepositoryFor(device: Device): VacuumRepository {
        val ud = authRepository.userData.value
            ?: error("vacuumRepositoryFor called before login")
        // Resolved per call rather than captured: a silent re-login mints a new session, and the
        // topics are derived from it.
        val topicsProvider = {
            val current = authRepository.userData.value ?: ud
            mqttTopicsFor(current.rriot.u, deriveMqttCreds(current.rriot).username, device.duid)
        }
        val topics = topicsProvider()
        println("[VacLocal] repo duid=${device.duid} sub=${topics.subscribeTopic} pub=${topics.publishTopic}")
        val localKey = device.localKey
            ?: error("Device ${device.duid} has no localKey — cannot control vacuum")
        return VacuumRepository(
            duid = device.duid,
            localKey = localKey,
            rriotKeyProvider = { (authRepository.userData.value ?: ud).rriot.k },
            mqttClient = mqttClient,
            topicsProvider = topicsProvider,
            nowEpochSeconds = { currentEpochSeconds().toInt() },
            demo = isDemo,
        ).apply {
            setHomeRooms(deviceRepository.home.value?.rooms ?: emptyList())
        }
    }
}
