package com.kodraliu.localrock.shared

import com.russhwolf.settings.Settings
import com.kodraliu.localrock.shared.auth.AuthRepository
import com.kodraliu.localrock.shared.auth.LoginApi
import com.kodraliu.localrock.shared.device.DeviceApi
import com.kodraliu.localrock.shared.device.DeviceRepository
import com.kodraliu.localrock.shared.device.ScheduleApi
import com.kodraliu.localrock.shared.http.HttpClientConfig
import com.kodraliu.localrock.shared.http.buildAdminHttpClient
import com.kodraliu.localrock.shared.http.buildVacLocalHttpClient
import com.kodraliu.localrock.shared.http.currentEpochSeconds
import com.kodraliu.localrock.shared.onboarding.OnboardingAdminApi
import com.kodraliu.localrock.shared.model.Device
import com.kodraliu.localrock.shared.mqtt.MqttClient
import com.kodraliu.localrock.shared.mqtt.MqttClientException
import com.kodraliu.localrock.shared.mqtt.MqttNativeTransport
import com.kodraliu.localrock.shared.mqtt.MqttTopics
import com.kodraliu.localrock.shared.mqtt.defaultMqttTransport
import com.kodraliu.localrock.shared.mqtt.deriveMqttCreds
import com.kodraliu.localrock.shared.mqtt.mqttTopicsFor
import com.kodraliu.localrock.shared.messages.MessageCenter
import com.kodraliu.localrock.shared.messages.MessageSeverity
import com.kodraliu.localrock.shared.notify.RobotEvent
import com.kodraliu.localrock.shared.notify.RobotMonitor
import com.kodraliu.localrock.shared.notify.RobotNotifier
import com.kodraliu.localrock.shared.notify.RobotProgress
import com.kodraliu.localrock.shared.notify.platformRobotNotifier
import com.kodraliu.localrock.shared.notify.toNotification
import com.kodraliu.localrock.shared.settings.AppSettings
import com.kodraliu.localrock.shared.vacuum.VacuumRepository
import com.kodraliu.localrock.shared.vacuum.VacuumSession
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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
    private val scheduleApi = ScheduleApi(httpClient)
    val onboardingAdminApi: OnboardingAdminApi = OnboardingAdminApi(adminHttpClient)

    val authRepository: AuthRepository = AuthRepository(appSettings, loginApi)
    val deviceRepository: DeviceRepository = DeviceRepository(
        deviceApi,
        authRepository,
        isDemo = { appSettings.demoMode.value },
        probe = ::probeDevice,
    )

    private val isDemo: Boolean get() = appSettings.demoMode.value

    val mqttClient: MqttClient = MqttClient(
        mqttTransport ?: error("No MQTT transport available — iOS must inject one via MainViewController(mqttTransport:)")
    )
    private val mqttMutex = Mutex()

    /** True while the MQTT supervisor runs for the signed-in session. */
    @Volatile
    private var mqttStarted = false

    private val notifier: RobotNotifier = platformRobotNotifier()

    /** True while the app's own UI is on screen, where an in-app message beats a system notification. */
    @Volatile
    private var inForeground = false

    fun setForeground(value: Boolean) {
        inForeground = value
    }

    private val robotMonitor = RobotMonitor(
        sessionFor = { device -> sessionFor(device, VacuumSession.MONITOR_REQUEST_IDS) },
        ensureConnected = ::ensureMqttConnected,
        onEvent = ::onRobotEvent,
        onProgress = ::onRobotProgress,
        wantsRoom = ::progressWanted,
        rriotKey = { authRepository.userData.value?.rriot?.k ?: error("not logged in") },
        homeRoomName = { roomId -> deviceRepository.home.value?.rooms?.find { it.id == roomId }?.name },
    )

    /** Whether the platform should keep the process alive to go on watching once the app is closed. */
    val backgroundWatchWanted: StateFlow<Boolean> = combine(
        authRepository.userData,
        appSettings.demoMode,
        appSettings.notifyEnabled,
        appSettings.notifyBackground,
    ) { user, demo, enabled, background -> user != null && !demo && enabled && background }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            authRepository.userData.value != null && !appSettings.demoMode.value &&
                appSettings.notifyEnabled.value && appSettings.notifyBackground.value,
        )

    private val _pendingOpenDuid = MutableStateFlow<String?>(null)

    /** A robot the user asked to see by tapping its notification, until the UI has gone there. */
    val pendingOpenDuid: StateFlow<String?> = _pendingOpenDuid

    fun requestOpenDevice(duid: String) {
        _pendingOpenDuid.value = duid
    }

    fun consumePendingOpen() {
        _pendingOpenDuid.value = null
    }

    init {
        scope.launch {
            combine(
                authRepository.userData,
                appSettings.demoMode,
                appSettings.notifyEnabled,
                deviceRepository.devices,
            ) { user, demo, enabled, devices ->
                if (user != null && !demo && enabled) devices.filter { it.localKey != null } else emptyList()
            }.collect { robotMonitor.watch(it) }
        }
    }

    /**
     * Progress is only worth showing where it can be kept up to date, which is while the
     * background watch holds the process: a standing notification that stops moving is worse
     * than none.
     */
    private fun progressWanted(): Boolean = appSettings.notifyProgress.value && backgroundWatchWanted.value

    private val progressMutex = Mutex()
    private val shownProgress = mutableMapOf<String, RobotProgress>()

    private fun onRobotProgress(device: Device, progress: RobotProgress?) {
        scope.launch {
            progressMutex.withLock {
                val wanted = progress?.takeIf { progressWanted() }
                if (wanted == shownProgress[device.duid]) return@withLock
                if (wanted == null) {
                    shownProgress.remove(device.duid)
                    notifier.clearProgress(device.duid)
                } else {
                    shownProgress[device.duid] = wanted
                    notifier.showProgress(wanted)
                }
            }
        }
    }

    private fun onRobotEvent(device: Device, event: RobotEvent) {
        val wanted = when (event) {
            is RobotEvent.Error -> appSettings.notifyErrors.value
            is RobotEvent.CleanFinished -> appSettings.notifyFinished.value
            is RobotEvent.DockAlert -> appSettings.notifyDock.value
        }
        if (!wanted) return
        val notification = event.toNotification(device.duid, device.name)
        if (inForeground) {
            messages.post(
                "${notification.title}: ${notification.body}",
                if (notification.alert) MessageSeverity.WARNING else MessageSeverity.SUCCESS,
            )
        } else {
            notifier.post(notification)
        }
    }

    /** How long an open-but-silent link may sit before a foreground reconnect is worth it. */
    private val FOREGROUND_STALE_MS = 90_000L

    /** Start the MQTT supervisor for the signed-in session, without waiting for the broker. */
    private suspend fun startMqtt() {
        if (isDemo || mqttStarted) return
        mqttMutex.withLock {
            if (mqttStarted) return
            val ud = authRepository.userData.value ?: return
            val creds = deriveMqttCreds(ud.rriot)

            val clientId = "vaclocal-${ud.rruid}-${appSettings.installId}"
            println("[VacLocal] MQTT connecting host=${creds.host}:${creds.port} tls=${creds.tls}")
            mqttClient.credentialRefresher = ::refreshMqttCredentials
            mqttClient.start(creds, clientId = clientId)
            mqttStarted = true
            observeMqttConnection()
        }
    }

    /** Signing out ends the link: it carries the credentials of the session that just ended. */
    private suspend fun stopMqtt() {
        mqttMutex.withLock {
            if (!mqttStarted) return
            mqttStarted = false
            mqttClient.disconnect()
            messages.dismissBanner(MessageCenter.BANNER_MQTT)
        }
    }

    /**
     * Make sure the MQTT supervisor runs and give the link a short while to come up. An
     * unreachable broker is not an error here: the supervisor keeps trying, the offline banner
     * tells the user, and one-shot reads can wait with [awaitMqttConnected].
     */
    suspend fun ensureMqttConnected() {
        if (isDemo) return
        if (authRepository.userData.value == null) error("ensureMqttConnected called before login")
        startMqtt()
        withTimeoutOrNull(MQTT_CONNECT_WAIT_MS) { mqttClient.connected.first { it } }
    }

    /** Suspends until the MQTT link is up; returns at once in demo mode. */
    suspend fun awaitMqttConnected() {
        if (isDemo) return
        mqttClient.connected.first { it }
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
            var bannerShown = false
            mqttClient.connected.collectLatest { up ->
                if (up) {
                    messages.dismissBanner(MessageCenter.BANNER_MQTT)
                    if (bannerShown) messages.success("Back online")
                    bannerShown = false
                } else {
                    // The link is down for a moment on every app start. collectLatest cancels
                    // this wait when it comes up in time, so only a real outage shows the banner.
                    delay(OFFLINE_BANNER_DELAY_MS)
                    if (!mqttStarted) return@collectLatest
                    messages.showBanner(
                        key = MessageCenter.BANNER_MQTT,
                        text = "Offline — reconnecting…",
                        severity = MessageSeverity.WARNING,
                        dismissible = false,
                    )
                    bannerShown = true
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
        if (!mqttStarted) return
        if (!mqttClient.connected.value) {
            // The supervisor is already reconnecting, but may be minutes into its backoff from
            // when the screen was off and the network asleep. Now is a good moment to try.
            mqttClient.retryNow()
            return
        }
        val silentMs = mqttClient.millisSinceLastMessage()
        if (silentMs < FOREGROUND_STALE_MS) return
        println("[VacLocal] foreground: link silent ${silentMs}ms — reconnecting")
        mqttClient.forceReconnect()
    }

    /**
     * Topics for one robot, resolved per call rather than captured: a silent re-login mints a new
     * session, and the topics are derived from it.
     */
    private fun topicsProviderFor(duid: String): () -> MqttTopics {
        val ud = authRepository.userData.value ?: error("topicsProviderFor called before login")
        return {
            val current = authRepository.userData.value ?: ud
            mqttTopicsFor(current.rriot.u, deriveMqttCreds(current.rriot).username, duid)
        }
    }

    /** A session of its own on a robot, next to whatever its screens have open. */
    private fun sessionFor(device: Device, requestIds: IntRange): VacuumSession = VacuumSession(
        localKey = device.localKey ?: error("Device ${device.duid} has no localKey"),
        mqtt = mqttClient,
        topicsProvider = topicsProviderFor(device.duid),
        nowEpochSeconds = { currentEpochSeconds().toInt() },
        requestIds = requestIds,
    )

    /**
     * True if the robot answers a status request, false if it stays silent. Throws when the
     * question could not be put at all, which callers must not read as "offline".
     */
    private suspend fun probeDevice(device: Device): Boolean {
        // The loop below does its own, shorter wait for the link.
        startMqtt()
        val started = TimeSource.Monotonic.markNow()
        val session = sessionFor(device, VacuumSession.PROBE_REQUEST_IDS)
        session.start()
        try {
            // Only silence counts against the robot, and it is asked twice: the first request can
            // go out before the broker has acknowledged the subscription, and its reply is then
            // lost. A link that drops while asking is the app's problem, not the robot's.
            var silences = 0
            var linkFailures = 0
            var linkChecked = false
            while (silences < PROBE_ATTEMPTS) {
                try {
                    withTimeoutOrNull(PROBE_LINK_WAIT_MS) { mqttClient.connected.first { it } }
                        ?: throw MqttClientException("MQTT link is down")
                    session.sendCommand("get_status", timeoutMs = PROBE_TIMEOUT_MS)
                    return true
                } catch (e: TimeoutCancellationException) {
                    silences++
                    println("[VacLocal] probe ${device.duid}: no reply within ${PROBE_TIMEOUT_MS}ms")
                } catch (e: MqttClientException) {
                    if (++linkFailures >= PROBE_LINK_RETRIES) throw e
                    delay(PROBE_LINK_RETRY_DELAY_MS)
                }
                // A link that has stopped delivering looks exactly like a silent robot, and has
                // been seen to stay "connected" while doing it. If nothing at all arrived while
                // asking, renew the link once and ask one more time before blaming the robot.
                if (silences == PROBE_ATTEMPTS && !linkChecked && linkSilentSince(started)) {
                    linkChecked = true
                    renewLinkForProbe(started)
                    silences--
                }
            }
            return false
        } finally {
            withContext(NonCancellable) { session.close() }
        }
    }

    private fun linkSilentSince(mark: TimeMark): Boolean =
        mqttClient.millisSinceLastMessage() >= mark.elapsedNow().inWholeMilliseconds

    private val probeRenewMutex = Mutex()
    private var lastProbeRenewal: TimeMark? = null

    /**
     * Reconnect once on behalf of every probe that started before [started]: robots are probed in
     * parallel, and each of them finding the link silent must not mean a reconnect each.
     */
    private suspend fun renewLinkForProbe(started: TimeMark) {
        probeRenewMutex.withLock {
            val last = lastProbeRenewal
            val renewedSinceStart = last != null && last.elapsedNow() < started.elapsedNow()
            if (renewedSinceStart || !linkSilentSince(started)) return
            println("[VacLocal] probe: nothing received since it began, renewing the MQTT link")
            mqttClient.forceReconnect()
            lastProbeRenewal = TimeSource.Monotonic.markNow()
            // The supervisor notices the teardown a moment later; wait for it so the next
            // request goes out on the new link rather than failing against the old one.
            withTimeoutOrNull(PROBE_LINK_RETRY_DELAY_MS) { mqttClient.connected.first { !it } }
        }
    }

    fun vacuumRepositoryFor(device: Device): VacuumRepository {
        val ud = authRepository.userData.value
            ?: error("vacuumRepositoryFor called before login")
        val topicsProvider = topicsProviderFor(device.duid)
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
            scheduleMirror = scheduleApi,
        ).apply {
            setHomeRooms(deviceRepository.home.value?.rooms ?: emptyList())
        }
    }

    // Last in the class body: the coroutine runs on another thread and must not find properties
    // that are still being initialised.
    init {
        // Connect as soon as there is a session, stored from the last run or from a fresh login,
        // instead of when the vacuum screen opens: the credentials need no network, so the link
        // comes up while the splash and the device list load.
        scope.launch {
            authRepository.userData.collect { user ->
                runCatching { if (user == null) stopMqtt() else startMqtt() }
                    .onFailure { println("[VacLocal] MQTT start/stop failed: ${it.message}") }
            }
        }
    }

    private companion object {
        /** How long opening the vacuum screen waits for the link before carrying on without it. */
        const val MQTT_CONNECT_WAIT_MS = 10_000L
        const val OFFLINE_BANNER_DELAY_MS = 5_000L
        const val PROBE_ATTEMPTS = 2
        const val PROBE_TIMEOUT_MS = 4_000L
        const val PROBE_LINK_WAIT_MS = 5_000L
        const val PROBE_LINK_RETRIES = 3
        const val PROBE_LINK_RETRY_DELAY_MS = 500L
    }
}
