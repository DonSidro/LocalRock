package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.vacuum.VacuumSession
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*

private open class MotionFailure(message: String) : RuntimeException(message)
private class MotionRefused(message: String) : MotionFailure(message)

// Starting a session and reading status can be slow. Motion and stop acknowledgements
// keep their short deadline; the robot's movement watchdog is also unchanged.
internal fun cameraControlTimeoutMs(method: String): Long = when (method) {
    "get_status", "app_rc_start" -> 5_000L
    else -> 1_500L
}

private val idleStates = setOf(2, 3, 8, 24, 100)
private val manualStates = setOf(4, 7)

/** One foreground viewer owns the manual session and serializes robot controls. */
class CameraMotionController internal constructor(
    private val rpc: CameraRpc,
    private val ready: () -> Boolean,
    private val scope: CoroutineScope,
) {
    constructor(session: VacuumSession, ready: () -> Boolean) : this(
        CameraRpc { method, params -> session.sendCommandRaw(method, params, timeoutMs = cameraControlTimeoutMs(method)) },
        ready, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    enum class Direction(val velocity: Double, val omega: Double) {
        FORWARD(.2, 0.0), BACKWARD(-.2, 0.0), LEFT(0.0, 1.05), RIGHT(0.0, -1.05),
    }
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress
    private val _manualMode = MutableStateFlow(false)
    val manualMode: StateFlow<Boolean> = _manualMode
    private val _viewerStartedActivity = MutableStateFlow(false)
    val viewerStartedActivity: StateFlow<Boolean> = _viewerStartedActivity
    private var job: Job? = null
    private var holding = false
    private var haltJob: Job? = null
    private var sequence = 0
    private var navigationRequested = false
    private var disposed = false

    fun hold(direction: Direction) {
        if (disposed || job != null || haltJob != null) return
        holding = true
        launchOperation {
            var entered = false
            var failed = false
            try {
                requireReady()
                val status = prepareDrive()
                currentCoroutineContext().ensureActive()
                requireReady()
                if (!_manualMode.value || status.state() !in manualStates) {
                    _progress.value = "Starting manual control…"
                    // A late acknowledgement must still be cleaned up after finger release.
                    withContext(NonCancellable) {
                        _manualMode.value = true
                        sequence = 0
                        try { ack("app_rc_start") }
                        catch (e: MotionRefused) { _manualMode.value = false; throw e }
                    }
                }
                currentCoroutineContext().ensureActive()
                _progress.value = "Waiting for manual control…"
                waitForDriveState("Waiting for manual control", manualStates, idleStates)
                entered = true
                _progress.value = null
                while (currentCoroutineContext().isActive) {
                    requireReady()
                    _viewerStartedActivity.value = true
                    ack("app_rc_move", movement(direction.velocity, direction.omega, ++sequence))
                    delay(200)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                reportFailure(e)
                throw e
            } finally {
                holding = false
                if (_manualMode.value) withContext(NonCancellable) {
                    val stopped = runCatching { ack("app_rc_stop") }
                        .onFailure(::reportFailure).isSuccess
                    // Normal release retains the OEM manual session and its sequence numbers.
                    if (!entered || failed || !stopped || !ready()) endManualMode()
                }
            }
        }
    }

    /** Pointer-up stops motion; leaving the viewer separately ends its manual session. */
    fun release() { if (holding) job?.cancel() }

    /** A held direction may take over only the Go initiated by this viewer. */
    private suspend fun prepareDrive(): JsonObject {
        _progress.value = "Checking robot…"
        var status = readStatus()
        requireNoTask(status)
        if (status.state() == VacuumStateCodes.GOING_TO_TARGET && navigationRequested) {
            requireReady()
            _progress.value = "Stopping Pin-and-Go…"
            ack("stop_goto_target")
            _progress.value = "Waiting for the robot to stop…"
            status = waitForDriveState("Waiting for Pin-and-Go to stop",
                idleStates + (if (_manualMode.value) manualStates else emptySet()),
                setOf(VacuumStateCodes.GOING_TO_TARGET))
        }
        requireIdleStatus(status)
        navigationRequested = false
        return status
    }

    private suspend fun waitForDriveState(label: String, accepted: Set<Int>, pending: Set<Int>): JsonObject =
        deadline(label, 5_000) {
            var status = readStatus()
            while (true) {
                requireReady()
                requireNoTask(status)
                if (status.state() in accepted) break
                if (status.state() !in pending) throw stateFailure(status)
                delay(100)
                status = readStatus()
            }
            status
        }

    fun cleaning(action: CameraCleaningAction) = launchOperation {
        if (_manualMode.value) {
            if (!endManualMode()) return@launchOperation
            deadline("Waiting for manual control to end", 5_000) {
                while (readStatus().state() in setOf(4, 7)) delay(100)
            }
        }
        val status = readStatus()
        val method = cameraCleaningCommand(action, status.state(),
            status["in_cleaning"]?.jsonPrimitive?.intOrNull,
            status["error_code"]?.jsonPrimitive?.intOrNull)
            ?: error("The robot's cleaning state changed")
        if (action == CameraCleaningAction.START) _viewerStartedActivity.value = true
        ack(method)
    }

    /** Pin-and-Go can undock the robot before camera frames are available. */
    fun goTo(x: Int, y: Int, floor: Int) = launchOperation {
        require(x in 0..51_200 && y in 0..51_200)
        requireIdle(floor)
        navigationRequested = true
        _viewerStartedActivity.value = true
        try { ack("app_goto_target", JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y)))) }
        catch (e: MotionRefused) { navigationRequested = false; throw e }
    }

    fun dock(onDocked: () -> Unit = {}) = endViewing(returnToDock = true, onFinished = onDocked)

    /** Finish robot commands before the caller releases the camera preview. */
    fun endViewing(returnToDock: Boolean = false, onFinished: () -> Unit = {}) = transition {
        if (returnToDock) {
            val status = readStatus()
            check(status["error_code"]?.jsonPrimitive?.intOrNull == 0)
            check(status.state() in setOf(2, 3, 4, 5, 6, 7, 8, 10, 11, 15, 16, 17, 18, 24, 100))
            if (status.state() !in setOf(6, 8, 15, 100)) ack("app_charge")
            navigationRequested = false
        } else if (!stopNavigation()) return@transition
        if (!endManualMode()) return@transition
        _viewerStartedActivity.value = false
        onFinished()
    }

    /** Explicit Stop may halt docking; background cleanup lets a return to dock continue. */
    fun stop(includeDock: Boolean = true) = transition {
        stopNavigation(includeDock)
        endManualMode()
    }

    /** A later foreground visit observes any continuing clean as an existing task. */
    fun background() {
        _viewerStartedActivity.value = false
        stop(includeDock = false)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        val previous = job
        val pendingHalt = haltJob
        previous?.cancel()
        scope.launch {
            previous?.join()
            pendingHalt?.join()
            try { stopNavigation(); endManualMode() } finally { scope.cancel() }
        }
    }

    private fun transition(action: suspend () -> Unit) {
        if (disposed || haltJob != null) return
        val previous = job
        previous?.cancel()
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try { previous?.join(); action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { reportFailure(e) }
            finally { haltJob = null; _busy.value = false; _progress.value = null }
        }
        haltJob = next
        _error.value = null
        _busy.value = true
        next.start()
    }

    private fun launchOperation(action: suspend () -> Unit) {
        if (disposed || job != null || haltJob != null) return
        _busy.value = true
        _error.value = null
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { reportFailure(e) }
            finally { holding = false; _busy.value = haltJob != null; job = null; _progress.value = null }
        }
        job = next
        next.invokeOnCompletion {
            if (job === next) { holding = false; job = null; _busy.value = haltJob != null }
        }
        next.start()
    }

    private suspend fun endManualMode(): Boolean {
        if (!_manualMode.value) return true
        return try {
            ack("app_rc_end")
            _manualMode.value = false
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            reportFailure(e)
            false
        }
    }

    private suspend fun stopNavigation(includeDock: Boolean = false): Boolean {
        if (!navigationRequested && !includeDock) return true
        return try {
            val status = readStatus()
            // A completed Go must never cause a later cleaning task to be stopped.
            val state = status.state()
            if (status["in_cleaning"]?.jsonPrimitive?.intOrNull == 0) {
                if (navigationRequested && state == 16) ack("stop_goto_target")
                else if (includeDock && state in setOf(6, 15)) ack("app_stop")
            }
            navigationRequested = false
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { reportFailure(e); false }
    }

    private fun requireReady() {
        if (!ready()) throw MotionFailure("Video disconnected. Reconnect before driving.")
    }
    private fun JsonObject.number(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.state() = number("state")
    private fun requireNoTask(status: JsonObject) {
        val error = status.number("error_code")
            ?: throw MotionFailure("Robot error status is unavailable (get_status). Try again once status updates.")
        if (error != 0) throw MotionFailure("Robot reports error $error. Resolve it before driving.")
        val cleaning = status.number("in_cleaning")
            ?: throw MotionFailure("Robot task status is unavailable (get_status). Try again once status updates.")
        if (cleaning != 0) throw MotionFailure("A cleaning task is still active (${stateLabel(status)}). Finish it before driving.")
    }

    private suspend fun readStatus(): JsonObject {
        val response = command("get_status")
        val result = response.result
        return (if (result is JsonArray) result.singleOrNull() else result) as? JsonObject
            ?: throw MotionFailure("Robot status is unavailable (get_status). Try again once status updates.")
    }

    private suspend fun requireIdle(floor: Int? = null): JsonObject {
        val status = readStatus()
        requireNoTask(status)
        requireIdleStatus(status)
        if (floor != null && status.number("map_status")?.shr(2) != floor)
            throw MotionFailure("The robot's floor changed. Open its current map and choose the target again.")
        return status
    }

    private fun requireIdleStatus(status: JsonObject) {
        if (status.state() !in idleStates && !(_manualMode.value && status.state() in manualStates))
            throw stateFailure(status)
    }

    private fun stateLabel(status: JsonObject): String = status.state()?.let { VacuumStateCodes.describe(it) } ?: "unknown state"

    private fun stateFailure(status: JsonObject) = MotionFailure(when (status.state()) {
        in manualStates -> "Another manual-control session is active. Close it and wait for the robot to become idle."
        VacuumStateCodes.GOING_TO_TARGET -> "The robot is going to a target outside this viewer's control. Let it finish before driving."
        else -> "The robot is ${stateLabel(status).lowercase()}. Wait for it to become idle before driving."
    })

    private fun commandLabel(method: String): String = when (method) {
        "get_status" -> "Reading robot status"
        "app_rc_start" -> "Starting manual control"
        "app_rc_move" -> "Driving"
        "app_rc_stop" -> "Stopping driving"
        "app_rc_end" -> "Ending manual control"
        "stop_goto_target" -> "Stopping Pin-and-Go"
        "app_goto_target" -> "Starting Pin-and-Go"
        "app_charge" -> "Returning to dock"
        else -> "Robot control"
    } + " ($method)"

    private suspend fun <T> deadline(label: String, timeout: Long, block: suspend () -> T): T = try {
        withTimeout(timeout) { block() }
    } catch (e: TimeoutCancellationException) {
        // Parent cancellation (finger release/backgrounding) must never become a retry.
        currentCoroutineContext().ensureActive()
        throw MotionFailure("$label timed out. Check the robot before trying again.")
    }

    private suspend fun command(method: String, params: JsonElement = JsonArray(emptyList())): V1Response {
        val label = commandLabel(method)
        val response = try {
            deadline(label, cameraControlTimeoutMs(method)) { rpc.call(method, params) }
        } catch (e: CancellationException) { throw e }
        catch (e: MotionFailure) { throw e }
        catch (_: Exception) { throw MotionFailure("$label could not be sent or confirmed. Check the connection and try again.") }
        if (response.error != null) {
            // Do not expose raw replies, parameters or transport exception text.
            val code = (response.error as? JsonObject)?.number("code")
            throw MotionRefused("$label was refused by the robot" + (code?.let { " (code $it)" } ?: "") + ".")
        }
        return response
    }

    private suspend fun ack(method: String, params: JsonElement = JsonArray(emptyList())) {
        val response = command(method, params)
        val result = response.result
        if ((if (result is JsonArray) result.singleOrNull() else result) != JsonPrimitive("ok"))
            throw MotionFailure("${commandLabel(method)} returned an unrecognized reply. Check the robot before trying again.")
    }

    private fun reportFailure(error: Throwable) {
        val message = (error as? MotionFailure)?.message
            ?: "Robot control was not confirmed. Check the robot before trying again."
        val previous = _error.value
        if (previous == null) _error.value = message
        else if (!previous.contains(message)) _error.value = "$previous $message"
    }

    private fun movement(velocity: Double, omega: Double, sequence: Int) = buildJsonArray {
        add(buildJsonObject {
            put("velocity", velocity); put("omega", omega); put("duration", 1500); put("seqnum", sequence)
        })
    }
}
