package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.model.Device
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NeverHeardTest {
    private fun device(duid: String, key: String? = "k") = Device(duid = duid, name = duid, localKey = key)

    @Test
    fun unpairedSkipsOnlineAndKeepsOfflineForProbedRobots() {
        val devices = listOf(device("a"), device("b"), device("c"))
        val reach = mapOf("a" to Reachability.ONLINE, "b" to Reachability.OFFLINE, "c" to Reachability.CHECKING)
        assertEquals(setOf("b"), unpairedDuids(setOf("a", "b", "c"), reach, devices))
    }

    @Test
    fun aProbedRobotWithNoVerdictIsNotUnpairedYet() {
        assertEquals(emptySet(), unpairedDuids(setOf("a"), emptyMap(), listOf(device("a"))))
    }

    @Test
    fun aRobotWeCannotProbeIsUnpairedUnlessReportedOnline() {
        val devices = listOf(device("a", key = null), device("b", key = null))
        val reach = mapOf("b" to Reachability.ONLINE)
        assertEquals(setOf("a"), unpairedDuids(setOf("a", "b"), reach, devices))
    }

    @Test
    fun emptyNeverHeardGivesEmpty() {
        assertEquals(emptySet(), unpairedDuids(emptySet(), emptyMap(), emptyList()))
    }

    @Test
    fun lookupFindsTheUnknownOnesAndAThrownErrorCountsAsPaired() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val tracker = NeverHeard(scope) { duid -> if (duid == "boom") error("no route") else duid != "new" }
        tracker.refresh(listOf(device("new"), device("old"), device("boom")))
        scope.advanceUntilIdle()
        assertEquals(setOf("new"), tracker.duids.value)
    }

    @Test
    fun emptyDeviceListClearsTheSet() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val tracker = NeverHeard(scope) { false }
        tracker.refresh(listOf(device("a")))
        scope.advanceUntilIdle()
        assertEquals(setOf("a"), tracker.duids.value)
        tracker.refresh(emptyList())
        scope.advanceUntilIdle()
        assertEquals(emptySet(), tracker.duids.value)
    }

    @Test
    fun aSecondRefreshCancelsTheFirstSoItCannotOverwrite() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val tracker = NeverHeard(scope) {
            if (++calls == 1) { gate.await(); false } else true
        }
        tracker.refresh(listOf(device("a")))
        scope.advanceUntilIdle()
        tracker.refresh(listOf(device("b")))
        scope.advanceUntilIdle()
        gate.complete(Unit)
        scope.advanceUntilIdle()
        assertEquals(emptySet(), tracker.duids.value)
    }
}
