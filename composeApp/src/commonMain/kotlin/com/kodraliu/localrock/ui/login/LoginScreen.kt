package com.kodraliu.localrock.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.auth.AuthRepository
import com.kodraliu.localrock.shared.demo.isDemoCredentials
import com.kodraliu.localrock.ui.LocalAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope


class LoginModel(
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setEmail(value: String) { _email.value = value }
    fun setCode(value: String) { _code.value = value }

    fun submit() {
        // App-store reviewers can sign in with the demo credentials to explore offline.
        if (isDemoCredentials(_email.value, _code.value)) {
            auth.enterDemo()
            return
        }
        scope.launch {
            _busy.value = true
            _error.value = null
            try {
                auth.login(_email.value.trim(), _code.value.trim())
            } catch (e: Throwable) {
                _error.value = e.message ?: "Sign-in failed"
            } finally {
                _busy.value = false
            }
        }
    }

    fun enterDemo() = auth.enterDemo()
}

@Composable
fun LoginScreen(onServerSetup: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val model = remember { LoginModel(container.authRepository, scope) }
    val serverUrl by container.appSettings.serverBaseUrl.collectAsState()

    val email by model.email.collectAsState()
    val code by model.code.collectAsState()
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Sign in", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Use the email and login code you set up on your LocalRock server. " +
                "These are your server's credentials — not a new Roborock account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Which server you are about to sign in to is the thing people get wrong, so show it
        // and make it changeable from here instead of hiding it behind a generic settings link.
        serverUrl?.takeIf { it.isNotBlank() }?.let { url ->
            Surface(
                onClick = onServerSetup,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Server",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            url,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = model::setEmail,
            label = { Text("Email") },
            singleLine = true,
            enabled = !busy,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = code,
            onValueChange = model::setCode,
            label = { Text("Login code") },
            singleLine = true,
            enabled = !busy,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )

        // The error belongs above the button that caused it, not below the fold.
        error?.let { message ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        Button(
            onClick = model::submit,
            enabled = !busy && email.isNotBlank() && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            else Text("Sign in")
        }

        TextButton(
            onClick = model::enterDemo,
            enabled = !busy,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Explore the demo (no server needed)")
        }
    }
}
