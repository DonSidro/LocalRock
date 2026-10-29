package com.kodraliu.localrock.shared.vacuum.camera

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CameraCredentialEntryTest {
    @Test fun accepted_entry_opens_once_after_verification_and_ignores_duplicate_submission() = runTest {
        var checks=0;var opened=0
        val entry=CameraCredentialEntry({checks++;delay(100)},this)
        entry.submit("1234") {opened++}
        entry.submit("1234") {opened++}
        runCurrent()
        assertTrue(entry.checking.value);assertEquals(0,opened)
        advanceUntilIdle()
        assertEquals(1,checks);assertEquals(1,opened);assertFalse(entry.checking.value)
        assertNull(entry.error.value)
    }
    @Test fun rejection_keeps_entry_open_and_allows_an_explicit_retry() = runTest {
        var rejected=true;var opened=0
        val entry=CameraCredentialEntry({if(rejected) throw CameraFailure("Credential rejected")},this)
        entry.submit("1234") {opened++};advanceUntilIdle()
        assertEquals(0,opened);assertEquals("Credential rejected",entry.error.value)
        assertFalse(entry.checking.value)
        rejected=false
        entry.submit("1478") {opened++};advanceUntilIdle()
        assertEquals(1,opened);assertNull(entry.error.value)
    }
    @Test fun incomplete_pattern_never_sends_a_check() = runTest {
        var checks=0;var opened=0
        val entry=CameraCredentialEntry({checks++},this)
        entry.submit("") {opened++};advanceUntilIdle()
        assertEquals(0,checks);assertEquals(0,opened);assertFalse(entry.checking.value)
    }
    @Test fun cancelled_late_reply_cannot_open_viewer_or_clear_a_new_request() = runTest {
        var opened=0
        val entry=CameraCredentialEntry({credential ->
            withContext(NonCancellable) {delay(if(credential=="1234") 100 else 500)}
        },this)
        entry.submit("1234") {opened++};runCurrent();entry.cancel()
        entry.submit("1478") {opened++};runCurrent()
        advanceTimeBy(101);runCurrent()
        assertEquals(0,opened);assertTrue(entry.checking.value)
        advanceUntilIdle()
        assertEquals(1,opened);assertFalse(entry.checking.value);assertNull(entry.error.value)
    }
    @Test fun unexpected_transport_failure_does_not_expose_payload() = runTest {
        var opened=false
        val entry=CameraCredentialEntry({error("private-transport-payload")},this)
        entry.submit("1234") {opened=true};advanceUntilIdle()
        assertFalse(opened);assertFalse(entry.checking.value)
        assertFalse(entry.error.value!!.contains("private-transport-payload"))
        assertContains(entry.error.value!!,"Check the connection")
    }
    @Test fun timeout_does_not_open_viewer_and_reports_retry() = runTest {
        var opened=false
        val entry=CameraCredentialEntry({withTimeout(100) {delay(500)}},this)
        entry.submit("1234") {opened=true};advanceUntilIdle()
        assertFalse(opened);assertFalse(entry.checking.value)
        assertContains(entry.error.value!!,"timed out")
    }
}
