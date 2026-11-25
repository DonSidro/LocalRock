package com.kodraliu.localrock.shared.vacuum.camera

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Validate a completed entry before opening the viewer, including while docked. */
internal class CameraCredentialEntry(
    private val verify: suspend (String) -> Unit,
    private val scope: CoroutineScope,
) {
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private var job: Job? = null
    private var generation = 0

    fun submit(credential: String, onAccepted: () -> Unit) {
        if (credential.isBlank() || job != null) return
        val request = ++generation
        _checking.value = true
        _error.value = null
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try {
                verify(credential)
                currentCoroutineContext().ensureActive()
                if (generation == request) onAccepted()
            } catch (_: TimeoutCancellationException) {
                if (generation == request) _error.value = "Checking the camera credential timed out. Try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: CameraFailure) {
                if (generation == request) _error.value = e.message
            } catch (_: Exception) {
                if (generation == request) _error.value = "Could not check the camera credential. Check the connection and try again."
            }
        }
        job = next
        next.invokeOnCompletion {
            if (job === next) { job = null; _checking.value = false }
        }
        next.start()
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        _checking.value = false
        _error.value = null
    }
}
