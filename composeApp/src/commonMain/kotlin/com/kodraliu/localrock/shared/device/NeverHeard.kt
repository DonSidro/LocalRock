package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.model.Device
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Robots the server has never heard from, looked up in parallel; a newer [refresh] cancels the one still running. */
internal class NeverHeard(
    private val scope: CoroutineScope,
    private val isKnown: suspend (duid: String) -> Boolean,
) {
    private val _duids = MutableStateFlow<Set<String>>(emptySet())
    val duids: StateFlow<Set<String>> = _duids.asStateFlow()
    private var job: Job? = null

    fun refresh(devices: List<Device>) {
        job?.cancel()
        job = scope.launch {
            val never = devices.map { device ->
                async {
                    val unknown = try { !isKnown(device.duid) } catch (e: CancellationException) { throw e } catch (e: Throwable) { false }
                    device.duid.takeIf { unknown }
                }
            }.awaitAll().filterNotNull().toSet()
            _duids.value = never
        }
    }
}

/** A never-heard robot we probe (it has a local key) is unpaired only once the probe says offline; others unless reported online. */
internal fun unpairedDuids(never: Set<String>, reachability: Map<String, Reachability>, devices: List<Device>): Set<String> {
    val probed = devices.filter { it.localKey != null }.mapTo(mutableSetOf()) { it.duid }
    return never.filterTo(mutableSetOf()) { duid ->
        if (duid in probed) reachability[duid] == Reachability.OFFLINE else reachability[duid] != Reachability.ONLINE
    }
}
