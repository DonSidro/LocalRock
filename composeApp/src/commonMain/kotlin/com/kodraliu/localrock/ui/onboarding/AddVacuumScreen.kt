package com.kodraliu.localrock.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.shared.onboarding.DEFAULT_IANA_TZ
import com.kodraliu.localrock.shared.onboarding.OnboardingDevice
import com.kodraliu.localrock.shared.onboarding.OnboardingInput
import com.kodraliu.localrock.shared.onboarding.VacuumOnboarder
import com.kodraliu.localrock.shared.onboarding.countryDomainFromIana
import com.kodraliu.localrock.shared.onboarding.posixTzFromIana
import com.kodraliu.localrock.shared.onboarding.supportedIanaTimezones
import com.kodraliu.localrock.ui.LocalAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private class AddVacuumModel(
    private val container: AppContainer,
    private val scope: CoroutineScope,
) {
    val onboarder = VacuumOnboarder()
    val step: StateFlow<VacuumOnboarder.Step> = onboarder.step
    private val adminApi = container.onboardingAdminApi

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _loadingDevices = MutableStateFlow(false)
    val loadingDevices: StateFlow<Boolean> = _loadingDevices.asStateFlow()

    private val _devices = MutableStateFlow<List<OnboardingDevice>>(emptyList())
    val devices: StateFlow<List<OnboardingDevice>> = _devices.asStateFlow()

    private val _selectedDuid = MutableStateFlow<String?>(null)
    val selectedDuid: StateFlow<String?> = _selectedDuid.asStateFlow()

    private val _serverStatus = MutableStateFlow<String?>(null)
    val serverStatus: StateFlow<String?> = _serverStatus.asStateFlow()

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun selectDuid(duid: String) { _selectedDuid.value = duid }

    fun loadDevices(adminPassword: String) {
        if (_loadingDevices.value || _running.value) return
        scope.launch {
            _loadingDevices.value = true
            _error.value = null
            try {
                container.appSettings.setAdminPassword(adminPassword)
                adminApi.login(adminPassword)
                val list = adminApi.listDevices()
                _devices.value = list
                if (_selectedDuid.value == null || _selectedDuid.value !in list.map { it.duid }) {
                    _selectedDuid.value = list.firstOrNull()?.duid
                }
                if (list.isEmpty()) {
                    _error.value = "No vacuums are available for onboarding yet. " +
                        "Finish the cloud import / fetch-data step on the server first."
                }
            } catch (e: Throwable) {
                _error.value = describe(e)
            } finally {
                _loadingDevices.value = false
            }
        }
    }

    fun start(adminPassword: String, ssid: String, password: String, timezone: String) {
        if (_running.value) return
        val duid = _selectedDuid.value
        scope.launch {
            _running.value = true
            _error.value = null
            _result.value = null
            _serverStatus.value = null
            try {
                val baseUrl = container.appSettings.serverBaseUrl.value
                    ?: error("Server URL not set")
                if (duid == null) error("Load and select a vacuum to onboard first")

                container.appSettings.setAdminPassword(adminPassword)
                // 1. Register a server-side onboarding session so the server recovers the
                //    vacuum's public key while we provision it.
                adminApi.login(adminPassword)
                val session = adminApi.startSession(duid)
                val sessionId = session.sessionId
                    ?: error("Server did not return a session id")

                // 2. Push Wi-Fi credentials + server URL to the vacuum over the local UDP handshake.
                onboarder.run(
                    OnboardingInput(
                        serverBaseUrl = baseUrl,
                        timezone = timezone,
                        homeSsid = ssid,
                        homePassword = password,
                    )
                )

                // 3. Poll the session until the vacuum reports in.
                _serverStatus.value = "Wi-Fi sent. Waiting for the vacuum to reach the server…"
                val connected = pollSession(sessionId)
                _result.value = if (connected) {
                    "Vacuum connected to the server. It should now appear in your device list."
                } else {
                    val guidance = session.guidance?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                    "Wi-Fi was sent, but the vacuum hasn't reported to the server within the wait window. " +
                        "Keep it powered near the dock — it can take a few minutes, then re-check the device list.$guidance"
                }
            } catch (e: Throwable) {
                _error.value = describe(e)
            } finally {
                _running.value = false
            }
        }
    }

    private suspend fun pollSession(sessionId: String): Boolean {
        repeat(POLL_ATTEMPTS) {
            val snap = runCatching { adminApi.getSession(sessionId) }.getOrNull()
            if (snap != null) {
                _serverStatus.value = buildString {
                    append("samples=${snap.querySamples}")
                    append(" • public key ${if (snap.hasPublicKey) "recovered" else "pending"}")
                    snap.publicKeyState?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
                    append(" • ${if (snap.connected) "connected" else "waiting"}")
                }
                if (snap.connected) return true
            }
            delay(POLL_INTERVAL_MS)
        }
        return false
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val POLL_ATTEMPTS = 60 // ~5 minutes
    }
}

private fun describe(e: Throwable): String = "${e::class.simpleName}: ${e.message ?: "Unknown error"}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVacuumScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val model = remember { AddVacuumModel(container, scope) }

    val step by model.step.collectAsState()
    val running by model.running.collectAsState()
    val loadingDevices by model.loadingDevices.collectAsState()
    val devices by model.devices.collectAsState()
    val selectedDuid by model.selectedDuid.collectAsState()
    val serverStatus by model.serverStatus.collectAsState()
    val result by model.result.collectAsState()
    val error by model.error.collectAsState()

    var adminPassword by remember { mutableStateOf(container.appSettings.adminPassword.value.orEmpty()) }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var timezone by remember { mutableStateOf(DEFAULT_IANA_TZ) }

    val canStartPairing = !running && !loadingDevices &&
        selectedDuid != null && ssid.isNotBlank() && password.isNotBlank() && timezone.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Vacuum") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Put the robot in pairing mode, then connect it to your home Wi-Fi.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ServerVacuumCard(
                adminPassword = adminPassword,
                onAdminPasswordChange = { adminPassword = it },
                devices = devices,
                selectedDuid = selectedDuid,
                loadingDevices = loadingDevices,
                enabled = !running,
                onLoadDevices = { model.loadDevices(adminPassword.trim()) },
                onSelectDevice = { model.selectDuid(it) },
            )

            NetworkDetailsCard(
                ssid = ssid,
                password = password,
                timezone = timezone,
                running = running,
                canStartPairing = canStartPairing,
                onSsidChange = { ssid = it },
                onPasswordChange = { password = it },
                onTimezoneChange = { timezone = it },
                onStartPairing = {
                    model.start(
                        adminPassword = adminPassword.trim(),
                        ssid = ssid.trim(),
                        password = password,
                        timezone = timezone.trim(),
                    )
                },
            )

            SetupProgressCard(step = step, running = running, serverStatus = serverStatus)

            error?.let { message ->
                SetupMessageCard(
                    title = "Pairing failed",
                    message = message,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            result?.let { message ->
                SetupMessageCard(
                    title = "Robot connected",
                    message = message,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            SetupLogsCard(step = step, running = running, error = error, result = result)

            Text(
                "After the robot joins your network, it will appear in the device list. " +
                    "This can take around 30 seconds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkDetailsCard(
    ssid: String,
    password: String,
    timezone: String,
    running: Boolean,
    canStartPairing: Boolean,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTimezoneChange: (String) -> Unit,
    onStartPairing: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Network details", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            OutlinedTextField(
                value = ssid,
                onValueChange = onSsidChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
                singleLine = true,
                label = { Text("Home Wi-Fi network") },
                placeholder = { Text("HomeNetwork_5G") },
                supportingText = { Text("Use the network your robot should join.") },
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
                singleLine = true,
                label = { Text("Wi-Fi password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    FilledTonalIconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
            )

            TimezonePicker(selected = timezone, onSelect = onTimezoneChange, enabled = !running)

            Button(
                onClick = onStartPairing,
                enabled = canStartPairing,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = if (running) "Pairing robot…" else "Start pairing",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimezonePicker(selected: String, onSelect: (String) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember { supportedIanaTimezones() }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = !expanded },
        ) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                singleLine = true,
                label = { Text("Timezone") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { tz ->
                    DropdownMenuItem(
                        text = { Text(tz) },
                        onClick = { onSelect(tz); expanded = false },
                    )
                }
            }
        }
        Text(
            "Country: ${countryDomainFromIana(selected)} • POSIX: ${posixTzFromIana(selected)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SetupProgressCard(step: VacuumOnboarder.Step, running: Boolean, serverStatus: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pairing status", style = MaterialTheme.typography.titleMedium)
            Text(
                text = stepLabel(step),
                style = MaterialTheme.typography.bodyLarge,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            serverStatus?.let {
                Text(
                    text = "Server: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerVacuumCard(
    adminPassword: String,
    onAdminPasswordChange: (String) -> Unit,
    devices: List<OnboardingDevice>,
    selectedDuid: String?,
    loadingDevices: Boolean,
    enabled: Boolean,
    onLoadDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Server & vacuum", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "Sign in to your server's admin panel and pick the vacuum to onboard. " +
                    "It must already be imported on the server (cloud fetch-data step).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = adminPassword,
                onValueChange = onAdminPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && !loadingDevices,
                singleLine = true,
                label = { Text("Admin password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    FilledTonalIconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
            )

            Button(
                onClick = onLoadDevices,
                enabled = enabled && !loadingDevices && adminPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                if (loadingDevices) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (loadingDevices) "Loading vacuums…" else "Load vacuums")
            }

            if (devices.isNotEmpty()) {
                val selected = devices.firstOrNull { it.duid == selectedDuid }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (enabled) expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = selected?.displayName ?: "Select a vacuum",
                        onValueChange = {},
                        readOnly = true,
                        enabled = enabled,
                        singleLine = true,
                        label = { Text("Vacuum to onboard") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        devices.forEach { device ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(device.displayName)
                                        val hint = buildString {
                                            append(if (device.connected) "connected" else "not connected")
                                            if (device.onboarding?.hasPublicKey == true) append(" • key ready")
                                            if (device.onboarding?.unsupported == true) append(" • unsupported")
                                        }
                                        Text(
                                            hint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = { onSelectDevice(device.duid); expanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupMessageCard(title: String, message: String, containerColor: Color, contentColor: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SetupLogsCard(step: VacuumOnboarder.Step, running: Boolean, error: String?, result: String?) {
    val logs = buildList {
        add("[setup] Ready to begin robot provisioning.")
        if (running) add("[setup] ${stepLabel(step)}")
        when {
            error != null -> add("[error] $error")
            result != null -> add("[success] $result")
            !running -> add("[setup] Enter Wi-Fi details and start pairing.")
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null)
                Text("Setup logs", style = MaterialTheme.typography.titleMedium)
            }
            Surface(
                modifier = Modifier.fillMaxWidth().height(172.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        logs.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = logColor(line),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun logColor(line: String): Color = when {
    line.startsWith("[error]") -> MaterialTheme.colorScheme.error
    line.startsWith("[success]") -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun stepLabel(step: VacuumOnboarder.Step): String = when (step) {
    VacuumOnboarder.Step.Idle -> "Waiting to start"
    VacuumOnboarder.Step.JoiningWifi -> "Connecting to the robot Wi-Fi…"
    VacuumOnboarder.Step.SendingHello -> "Starting pairing session…"
    VacuumOnboarder.Step.AwaitingHello -> "Waiting for the robot to respond…"
    VacuumOnboarder.Step.SendingWifiConfig -> "Sending Wi-Fi credentials…"
    VacuumOnboarder.Step.AwaitingAck -> "Waiting for the robot to join your network…"
    VacuumOnboarder.Step.Done -> "Pairing completed"
}
