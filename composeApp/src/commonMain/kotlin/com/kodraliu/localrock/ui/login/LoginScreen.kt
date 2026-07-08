package com.kodraliu.localrock.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun LoginScreen(onSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val model = remember { LoginModel(container.authRepository, scope) }

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
        Text("Sign in", style = MaterialTheme.typography.titleLarge)
        Text(
            "Enter the email and login code you set up on your LocalRock server. " +
                "These are your server's credentials — not a new Roborock account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = email,
            onValueChange = model::setEmail,
            label = { Text("Email") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = code,
            onValueChange = model::setCode,
            label = { Text("Login code") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = model::submit,
            enabled = !busy && email.isNotBlank() && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.height(20.dp))
            else Text("Sign in")
        }
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSettings, modifier = Modifier.align(Alignment.Start)) {
            Text("Server settings")
        }
        TextButton(
            onClick = model::enterDemo,
            enabled = !busy,
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text("Explore the demo (no server needed)")
        }
    }
}
