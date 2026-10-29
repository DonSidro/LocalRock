package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CameraMotionControllerTest {
    private class Rpc : CameraRpc {
        val calls = mutableListOf<Pair<String,JsonElement>>()
        var state = 3
        var cleaning = 0
        var startDelay = 0L
        var enterManualMode = true
        var statusDelay = 0L
        var refuse: String? = null
        var robotError = 0
        var stopTargetState = 3
        var transportFailure: String? = null
        var unexpectedReply: String? = null
        val replyDelays = mutableMapOf<String, Long>()
        override suspend fun call(method: String, params: JsonElement): V1Response {
            calls += method to params
            delay(replyDelays[method] ?: 0)
            if (method == transportFailure) error("private transport details")
            if (method == refuse) return V1Response(null,null,buildJsonObject {
                put("code",-1);put("message","private robot response")
            })
            if (method == "get_status") {
                delay(statusDelay)
                return V1Response(null,buildJsonArray { add(buildJsonObject {
                    put("state",state);put("in_cleaning",cleaning);put("error_code",robotError);put("map_status",0)
                }) },null)
            }
            if (method == "app_rc_start") { delay(startDelay); if (enterManualMode) state=7 }
            if (method == "app_rc_end") state=3
            if (method == "app_goto_target") state=16
            if (method == "app_stop") state=3
            if (method == "stop_goto_target") state=stopTargetState
            if (method == "app_start") { state=5; cleaning=1 }
            if (method == "app_charge") { state=6; cleaning=0 }
            return V1Response(null,buildJsonArray {add(if(method==unexpectedReply) "private unexpected reply" else "ok")},null)
        }
        fun methods() = calls.map {it.first}
    }
    private fun TestScope.controller(rpc: Rpc, ready: () -> Boolean = {true}) =
        CameraMotionController(rpc,ready,CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler)))

    @Test fun dockedPlayStartsCleaningBeforeVideoConnects() = runTest {
        val rpc=Rpc().apply {state=8};val c=controller(rpc){false}
        c.cleaning(CameraCleaningAction.START);advanceUntilIdle()
        assertEquals(listOf("get_status","app_start"),rpc.methods())
        c.dispose();advanceUntilIdle()
        assertFalse("app_stop" in rpc.methods())
    }
    @Test fun cleaningCommandsPreserveTheCurrentTask() = runTest {
        for ((scope,method) in mapOf(1 to "app_start",2 to "resume_zoned_clean",3 to "resume_segment_clean")) {
            val rpc=Rpc().apply {state=10;cleaning=scope};val c=controller(rpc)
            c.cleaning(CameraCleaningAction.RESUME);advanceUntilIdle()
            assertEquals(listOf("get_status",method),rpc.methods())
            c.dispose();advanceUntilIdle()
        }
    }
    @Test fun runningTaskCanBePaused() = runTest {
        val rpc=Rpc().apply {state=5;cleaning=1};val c=controller(rpc)
        c.cleaning(CameraCleaningAction.PAUSE);advanceUntilIdle()
        assertEquals(listOf("get_status","app_pause"),rpc.methods())
        c.dispose();advanceUntilIdle()
    }
    @Test fun stalePlayDoesNotPauseARunThatStartedElsewhere() = runTest {
        val rpc=Rpc().apply {state=5;cleaning=1};val c=controller(rpc)
        c.cleaning(CameraCleaningAction.START);advanceUntilIdle()
        assertEquals(listOf("get_status"),rpc.methods());assertNotNull(c.error.value)
        c.dispose();advanceUntilIdle()
    }
    @Test fun repeatedPlayAndDriveShareOneCommandQueue() = runTest {
        val rpc=Rpc().apply {state=8;statusDelay=500};val c=controller(rpc)
        c.cleaning(CameraCleaningAction.START)
        c.cleaning(CameraCleaningAction.START)
        c.hold(CameraMotionController.Direction.FORWARD)
        advanceUntilIdle()
        assertEquals(listOf("get_status","app_start"),rpc.methods())
        c.dispose();advanceUntilIdle()
    }
    @Test fun refusedCleaningCommandIsReportedWithoutRetries() = runTest {
        val rpc=Rpc().apply {refuse="app_start"};val c=controller(rpc)
        c.cleaning(CameraCleaningAction.START);advanceUntilIdle()
        assertEquals(listOf("get_status","app_start"),rpc.methods());assertNotNull(c.error.value)
        c.dispose();advanceUntilIdle()
    }

    @Test fun release_uses_oem_stop_and_keeps_manual_session() = runTest {
        val rpc=Rpc();val c=controller(rpc)
        c.hold(CameraMotionController.Direction.FORWARD);runCurrent();advanceTimeBy(450);runCurrent()
        c.release();advanceUntilIdle()
        assertEquals("app_rc_stop",rpc.methods().last())
        assertFalse("app_rc_end" in rpc.methods())
        val last=rpc.calls.last {it.first=="app_rc_move"}.second.jsonArray.single().jsonObject
        assertEquals(.2,last["velocity"]!!.jsonPrimitive.double)
        assertEquals(1500,last["duration"]!!.jsonPrimitive.int)
        val sequences=rpc.calls.filter { it.first=="app_rc_move" }.map { it.second.jsonArray.single().jsonObject["seqnum"]!!.jsonPrimitive.int }
        assertEquals((1..sequences.size).toList(),sequences)
        val count=rpc.calls.size;advanceTimeBy(5000);runCurrent();assertEquals(count,rpc.calls.size)
        c.dispose();advanceUntilIdle()
    }
    @Test fun release_during_status_read_never_starts_driving() = runTest {
        val rpc=Rpc().apply {statusDelay=1000};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.FORWARD);runCurrent();c.release();advanceUntilIdle()
        assertEquals(listOf("get_status"),rpc.methods());c.dispose();advanceUntilIdle()
    }
    @Test fun release_before_dispatch_never_starts_driving() = runTest {
        val rpc=Rpc();val c=controller(rpc)
        c.hold(CameraMotionController.Direction.LEFT);c.release();advanceUntilIdle()
        assertTrue(rpc.calls.isEmpty());assertFalse(c.busy.value);c.dispose();advanceUntilIdle()
    }
    @Test fun late_start_ack_is_cleaned_without_motion() = runTest {
        val rpc=Rpc().apply {startDelay=800};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.FORWARD);runCurrent();c.release();advanceUntilIdle()
        assertEquals(listOf("get_status","app_rc_start","app_rc_stop","app_rc_end"),rpc.methods())
        c.dispose();advanceUntilIdle()
    }
    @Test fun connection_loss_stops_drive_and_reports_failure() = runTest {
        val rpc=Rpc();var connected=true;val c=controller(rpc){connected}
        c.hold(CameraMotionController.Direction.RIGHT);runCurrent();connected=false;advanceUntilIdle()
        assertEquals("app_rc_end",rpc.methods().last());assertNotNull(c.error.value)
        c.dispose();advanceUntilIdle()
    }
    @Test fun busy_robot_and_missing_video_cannot_start_motion() = runTest {
        for (connected in listOf(false,true)) {
            val rpc=Rpc().apply {state=5;cleaning=1};val c=controller(rpc){connected}
            c.hold(CameraMotionController.Direction.BACKWARD);advanceUntilIdle()
            assertFalse("app_rc_start" in rpc.methods());assertNotNull(c.error.value)
            c.dispose();advanceUntilIdle()
        }
    }
    @Test fun missing_manual_mode_never_sends_nonzero_motion() = runTest {
        val rpc=Rpc().apply {enterManualMode=false};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.FORWARD);advanceUntilIdle()
        val moves=rpc.calls.filter {it.first=="app_rc_move"}
        assertTrue(moves.all {it.second.jsonArray.single().jsonObject["velocity"]!!.jsonPrimitive.double==0.0})
        assertEquals("app_rc_end",rpc.methods().last());assertNotNull(c.error.value)
        c.dispose();advanceUntilIdle()
    }
    @Test fun refused_start_does_not_send_move_or_end() = runTest {
        val rpc=Rpc().apply {refuse="app_rc_start"};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.FORWARD);advanceUntilIdle()
        assertEquals(listOf("get_status","app_rc_start"),rpc.methods());assertNotNull(c.error.value)
        c.dispose();advanceUntilIdle()
    }
    @Test fun refused_move_stops_and_does_not_repeat() = runTest {
        val rpc=Rpc().apply {refuse="app_rc_move"};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.FORWARD);advanceUntilIdle()
        assertEquals("app_rc_end",rpc.methods().last());assertNotNull(c.error.value)
        c.dispose();advanceUntilIdle()
    }
    @Test fun explicit_retry_after_refused_start_can_drive_and_release() = runTest {
        val rpc=Rpc().apply {refuse="app_rc_start"};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.FORWARD);advanceUntilIdle()
        assertNotNull(c.error.value);assertFalse(c.busy.value);assertFalse(c.manualMode.value)
        assertFalse("app_rc_move" in rpc.methods())
        rpc.refuse=null
        c.hold(CameraMotionController.Direction.FORWARD);runCurrent();advanceTimeBy(250);runCurrent()
        c.release();advanceUntilIdle()
        assertNull(c.error.value);assertFalse(c.busy.value)
        assertEquals(2,rpc.methods().count {it=="app_rc_start"})
        assertTrue("app_rc_move" in rpc.methods());assertEquals("app_rc_stop",rpc.methods().last())
        c.dispose();advanceUntilIdle()
    }
    @Test fun explicit_retry_after_manual_entry_timeout_uses_a_fresh_session() = runTest {
        val rpc=Rpc().apply {enterManualMode=false};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.LEFT);advanceUntilIdle()
        assertNotNull(c.error.value);assertFalse(c.busy.value);assertFalse(c.manualMode.value)
        assertEquals("app_rc_end",rpc.methods().last());assertFalse("app_rc_move" in rpc.methods())
        rpc.enterManualMode=true
        c.hold(CameraMotionController.Direction.RIGHT);runCurrent();advanceTimeBy(250);runCurrent()
        c.release();advanceUntilIdle()
        assertNull(c.error.value);assertFalse(c.busy.value)
        val firstMove=rpc.calls.first {it.first=="app_rc_move"}.second.jsonArray.single().jsonObject
        assertEquals(1,firstMove["seqnum"]!!.jsonPrimitive.int)
        assertEquals("app_rc_stop",rpc.methods().last())
        c.dispose();advanceUntilIdle()
    }

    @Test fun slow_status_and_start_acknowledgements_can_enter_manual_mode() = runTest {
        val rpc=Rpc().apply {statusDelay=2_000;startDelay=2_000};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.FORWARD);runCurrent()
        advanceTimeBy(5_900);runCurrent();assertFalse("app_rc_move" in rpc.methods())
        advanceTimeBy(200);runCurrent();assertTrue("app_rc_move" in rpc.methods())
        c.release();advanceUntilIdle()
        assertNull(c.error.value);assertNull(c.progress.value);assertFalse(c.busy.value)
        c.dispose();advanceUntilIdle()
    }

    @Test fun delayed_manual_status_is_waited_for_without_resending_start() = runTest {
        val rpc=Rpc().apply {enterManualMode=false};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.FORWARD);runCurrent()
        advanceTimeBy(2_100);runCurrent();assertFalse("app_rc_move" in rpc.methods())
        assertEquals("Waiting for manual control…",c.progress.value)
        rpc.state=7;advanceTimeBy(200);runCurrent();assertTrue("app_rc_move" in rpc.methods())
        c.release();advanceUntilIdle()
        assertNull(c.error.value);assertEquals(1,rpc.methods().count {it=="app_rc_start"})
        c.dispose();advanceUntilIdle()
    }

    @Test fun drive_stops_owned_goto_and_waits_for_idle_before_starting() = runTest {
        val rpc=Rpc().apply {stopTargetState=16};val c=controller(rpc)
        c.goTo(24500,26000,0);advanceUntilIdle()
        c.hold(CameraMotionController.Direction.LEFT);runCurrent()
        advanceTimeBy(2_100);runCurrent()
        assertEquals(1,rpc.methods().count {it=="stop_goto_target"})
        assertFalse("app_rc_start" in rpc.methods());assertFalse("app_rc_move" in rpc.methods())
        assertEquals("Waiting for the robot to stop…",c.progress.value)
        rpc.state=3;advanceTimeBy(200);runCurrent();c.release();advanceUntilIdle()
        assertTrue("app_rc_move" in rpc.methods());assertNull(c.error.value)
        assertTrue(rpc.methods().indexOf("stop_goto_target") < rpc.methods().indexOf("app_rc_start"))
        c.dispose();advanceUntilIdle()
        assertEquals(1,rpc.methods().count {it=="stop_goto_target"})
    }

    @Test fun release_during_goto_handoff_never_starts_manual_motion() = runTest {
        val rpc=Rpc().apply {stopTargetState=16};val c=controller(rpc)
        c.goTo(24500,26000,0);advanceUntilIdle()
        c.hold(CameraMotionController.Direction.LEFT);runCurrent();advanceTimeBy(300)
        c.release();advanceUntilIdle();rpc.state=3;advanceTimeBy(6_000);runCurrent()
        assertFalse("app_rc_start" in rpc.methods());assertFalse("app_rc_move" in rpc.methods())
        assertNull(c.error.value);assertNull(c.progress.value);assertFalse(c.busy.value)
        c.dispose();advanceUntilIdle()
    }

    @Test fun timed_out_goto_handoff_reports_the_stage_without_starting_motion() = runTest {
        val rpc=Rpc().apply {stopTargetState=16};val c=controller(rpc)
        c.goTo(24500,26000,0);advanceUntilIdle()
        c.hold(CameraMotionController.Direction.LEFT);advanceUntilIdle()
        assertContains(c.error.value!!,"Waiting for Pin-and-Go to stop timed out")
        assertFalse("app_rc_start" in rpc.methods());assertFalse("app_rc_move" in rpc.methods())
        assertFalse(c.busy.value)
        c.dispose();advanceUntilIdle()
    }

    @Test fun drive_does_not_take_over_an_unowned_goto_or_a_later_cleaning_task() = runTest {
        val rpc=Rpc().apply {state=16};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.LEFT);advanceUntilIdle()
        assertEquals(listOf("get_status"),rpc.methods())
        assertContains(c.error.value!!,"outside this viewer's control")
        rpc.state=3;c.goTo(24500,26000,0);advanceUntilIdle()
        rpc.state=5;rpc.cleaning=1
        c.hold(CameraMotionController.Direction.LEFT);advanceUntilIdle()
        assertContains(c.error.value!!,"cleaning task is still active")
        assertFalse("stop_goto_target" in rpc.methods());assertFalse("app_rc_start" in rpc.methods())
        c.dispose();advanceUntilIdle();assertFalse("app_stop" in rpc.methods())
    }

    @Test fun refused_handoff_keeps_driving_stopped_and_reports_a_sanitized_code() = runTest {
        val rpc=Rpc();val c=controller(rpc)
        c.goTo(24500,26000,0);advanceUntilIdle();rpc.refuse="stop_goto_target"
        c.hold(CameraMotionController.Direction.LEFT);advanceUntilIdle()
        assertContains(c.error.value!!,"Stopping Pin-and-Go (stop_goto_target)")
        assertContains(c.error.value!!,"code -1");assertFalse(c.error.value!!.contains("private"))
        assertFalse("app_rc_start" in rpc.methods());assertEquals(1,rpc.methods().count {it=="stop_goto_target"})
        c.dispose();advanceUntilIdle()
    }

    @Test fun release_during_slow_start_ack_cleans_up_without_late_motion() = runTest {
        val rpc=Rpc().apply {startDelay=2_500};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.LEFT);runCurrent();advanceTimeBy(200);c.release()
        advanceUntilIdle()
        assertEquals(listOf("get_status","app_rc_start","app_rc_stop","app_rc_end"),rpc.methods())
        assertNull(c.error.value);assertFalse(c.manualMode.value);assertFalse(c.busy.value)
        c.dispose();advanceUntilIdle()
    }

    @Test fun start_timeout_reports_start_and_releases_the_possible_session() = runTest {
        val rpc=Rpc().apply {startDelay=6_000};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.LEFT);advanceUntilIdle()
        assertContains(c.error.value!!,"Starting manual control (app_rc_start) timed out")
        assertEquals("app_rc_end",rpc.methods().last());assertFalse("app_rc_move" in rpc.methods())
        assertFalse(c.manualMode.value);assertFalse(c.busy.value)
        c.dispose();advanceUntilIdle()
    }

    @Test fun move_timeout_keeps_short_deadline_and_ends_session_before_explicit_retry() = runTest {
        val rpc=Rpc().apply {replyDelays["app_rc_move"]=2_000};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.LEFT);runCurrent()
        advanceTimeBy(1_501);runCurrent()
        assertContains(c.error.value!!,"Driving (app_rc_move) timed out")
        assertEquals(listOf("app_rc_stop","app_rc_end"),rpc.methods().takeLast(2))
        assertFalse(c.manualMode.value);assertFalse(c.busy.value)
        assertEquals(1,rpc.methods().count {it=="app_rc_move"})
        rpc.replyDelays.clear();c.hold(CameraMotionController.Direction.RIGHT);runCurrent();c.release();advanceUntilIdle()
        assertEquals(2,rpc.methods().count {it=="app_rc_start"});assertNull(c.error.value)
        val last=rpc.calls.last {it.first=="app_rc_move"}.second.jsonArray.single().jsonObject
        assertEquals(1,last["seqnum"]!!.jsonPrimitive.int)
        c.dispose();advanceUntilIdle()
    }

    @Test fun stop_timeout_keeps_short_deadline_and_surfaces_cleanup_failure() = runTest {
        val rpc=Rpc().apply {replyDelays["app_rc_stop"]=2_000};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.LEFT);runCurrent();c.release()
        advanceTimeBy(1_501);runCurrent()
        assertContains(c.error.value!!,"Stopping driving (app_rc_stop) timed out")
        assertEquals("app_rc_end",rpc.methods().last());assertFalse(c.busy.value)
        c.dispose();advanceUntilIdle()
    }

    @Test fun unexpected_start_ack_is_not_treated_as_a_definite_refusal() = runTest {
        val rpc=Rpc().apply {unexpectedReply="app_rc_start"};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.LEFT);advanceUntilIdle()
        assertContains(c.error.value!!,"app_rc_start");assertContains(c.error.value!!,"unrecognized reply")
        assertFalse(c.error.value!!.contains("private"));assertFalse("app_rc_move" in rpc.methods())
        assertEquals(listOf("app_rc_stop","app_rc_end"),rpc.methods().takeLast(2))
        c.dispose();advanceUntilIdle()
    }

    @Test fun transport_failure_and_robot_errors_are_actionable_without_private_details() = runTest {
        val rpc=Rpc().apply {transportFailure="get_status"};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.LEFT);advanceUntilIdle()
        assertContains(c.error.value!!,"get_status");assertFalse(c.error.value!!.contains("private"))
        rpc.transportFailure=null;rpc.robotError=3
        c.hold(CameraMotionController.Direction.LEFT);advanceUntilIdle()
        assertContains(c.error.value!!,"Robot reports error 3");assertFalse("app_rc_start" in rpc.methods())
        c.dispose();advanceUntilIdle()
    }

    @Test fun movement_error_and_failed_stop_are_both_preserved() = runTest {
        val rpc=Rpc().apply {replyDelays["app_rc_move"]=2_000;refuse="app_rc_stop"};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.LEFT);advanceUntilIdle()
        assertContains(c.error.value!!,"Driving (app_rc_move) timed out")
        assertContains(c.error.value!!,"Stopping driving (app_rc_stop) was refused")
        assertContains(c.error.value!!,"code -1");assertFalse(c.error.value!!.contains("private"))
        assertEquals("app_rc_end",rpc.methods().last());assertFalse(c.busy.value)
        c.dispose();advanceUntilIdle()
    }

    @Test fun losing_video_during_slow_status_does_not_start_manual_control() = runTest {
        val rpc=Rpc().apply {statusDelay=2_000};var ready=true;val c=controller(rpc){ready}
        c.hold(CameraMotionController.Direction.LEFT);runCurrent();ready=false;advanceUntilIdle()
        assertEquals(listOf("get_status"),rpc.methods());assertContains(c.error.value!!,"Video disconnected")
        assertFalse(c.busy.value);c.dispose();advanceUntilIdle()
    }
    @Test fun goto_uses_bare_coordinates_and_stop_releases_owned_navigation() = runTest {
        val rpc=Rpc();val c=controller(rpc)
        c.goTo(24500,26000,0);advanceUntilIdle()
        assertEquals("[24500,26000]",rpc.calls.last().second.toString())
        c.stop();advanceUntilIdle();assertEquals("stop_goto_target",rpc.methods().last())
        c.dispose();advanceUntilIdle()
    }
    @Test fun changed_floor_prevents_goto() = runTest {
        val rpc=Rpc();val c=controller(rpc)
        c.goTo(24500,26000,1);advanceUntilIdle()
        assertFalse("app_goto_target" in rpc.methods());c.dispose();advanceUntilIdle()
    }
    @Test fun completed_goto_does_not_stop_a_later_cleaning_task() = runTest {
        val rpc=Rpc();val c=controller(rpc)
        c.goTo(24500,26000,0);advanceUntilIdle();rpc.state=5;rpc.cleaning=1
        c.stop();advanceUntilIdle();assertFalse("app_stop" in rpc.methods())
        c.dispose();advanceUntilIdle()
    }
    @Test fun explicit_stop_can_stop_returning_to_dock() = runTest {
        val rpc=Rpc().apply {state=6};val c=controller(rpc)
        c.stop();advanceUntilIdle();assertEquals("app_stop",rpc.methods().last())
        c.dispose();advanceUntilIdle()
    }
    @Test fun background_cleanup_does_not_interrupt_returning_to_dock() = runTest {
        val rpc=Rpc().apply {state=6};val c=controller(rpc)
        c.stop(includeDock=false);advanceUntilIdle();assertTrue(rpc.calls.isEmpty())
        c.dispose();advanceUntilIdle();assertTrue(rpc.calls.isEmpty())
    }
    @Test fun dispose_releases_held_drive() = runTest {
        val rpc=Rpc();val c=controller(rpc)
        c.hold(CameraMotionController.Direction.LEFT);runCurrent();c.dispose();advanceUntilIdle()
        assertEquals("app_rc_end",rpc.methods().last())
        val count=rpc.calls.size;c.hold(CameraMotionController.Direction.RIGHT);runCurrent();assertEquals(count,rpc.calls.size)
    }
    @Test fun repeated_holds_share_manual_session_and_increasing_sequence() = runTest {
        val rpc=Rpc();val c=controller(rpc)
        for (direction in CameraMotionController.Direction.entries) {
            c.hold(direction);runCurrent();advanceTimeBy(250);runCurrent();c.release();advanceUntilIdle()
        }
        assertEquals(1,rpc.methods().count {it=="app_rc_start"})
        assertEquals(4,rpc.methods().count {it=="app_rc_stop"})
        val moves=rpc.calls.filter {it.first=="app_rc_move"}.map {it.second.jsonArray.single().jsonObject}
        assertEquals((1..moves.size).toList(),moves.map {it["seqnum"]!!.jsonPrimitive.int})
        assertTrue(moves.any {it["omega"]!!.jsonPrimitive.double==1.05})
        assertTrue(moves.any {it["omega"]!!.jsonPrimitive.double== -1.05})
        c.endViewing();advanceUntilIdle();assertEquals("app_rc_end",rpc.methods().last())
        c.dispose();advanceUntilIdle()
    }
    @Test fun pin_and_go_can_undock_before_video_connects() = runTest {
        val rpc=Rpc().apply {state=8};val c=controller(rpc){false}
        c.goTo(24500,26000,0);advanceUntilIdle()
        assertEquals(listOf("get_status","app_goto_target"),rpc.methods())
        assertTrue(c.viewerStartedActivity.value)
        c.dispose();advanceUntilIdle()
    }
    @Test fun manual_mode_owned_by_another_viewer_is_not_used() = runTest {
        val rpc=Rpc().apply {state=7};val c=controller(rpc)
        c.hold(CameraMotionController.Direction.LEFT);advanceUntilIdle()
        assertEquals(listOf("get_status"),rpc.methods())
        c.dispose();advanceUntilIdle();assertFalse("app_rc_end" in rpc.methods())
    }
    @Test fun go_after_driving_keeps_the_manual_session_until_exit() = runTest {
        val rpc=Rpc();val c=controller(rpc)
        c.hold(CameraMotionController.Direction.FORWARD);runCurrent();c.release();advanceUntilIdle()
        c.goTo(24500,26000,0);advanceUntilIdle()
        assertEquals("app_goto_target",rpc.methods().last());assertFalse("app_rc_end" in rpc.methods())
        c.dispose();advanceUntilIdle()
    }
    @Test fun exit_and_dock_matches_charge_then_rc_end_then_camera_cleanup() = runTest {
        val rpc=Rpc();val c=controller(rpc)
        c.hold(CameraMotionController.Direction.FORWARD);runCurrent();c.release();advanceUntilIdle()
        var cameraStopped=false
        c.dock {cameraStopped=true;rpc.calls += "stop_camera_preview" to JsonObject(emptyMap())}
        advanceUntilIdle()
        assertTrue(cameraStopped)
        assertEquals(listOf("app_charge","app_rc_end","stop_camera_preview"),rpc.methods().takeLast(3))
        assertFalse(c.viewerStartedActivity.value)
        c.dispose();advanceUntilIdle();assertFalse("app_stop" in rpc.methods())
    }
    @Test fun leaving_video_preserves_an_existing_cleaning_run() = runTest {
        val rpc=Rpc().apply {state=5;cleaning=1};val c=controller(rpc)
        var ended=false;c.endViewing {ended=true};advanceUntilIdle()
        assertTrue(ended);assertFalse(c.viewerStartedActivity.value);assertTrue(rpc.calls.isEmpty())
        c.dispose();advanceUntilIdle();assertTrue(rpc.calls.isEmpty())
    }
    @Test fun video_started_cleaning_can_continue_or_explicitly_return_home() = runTest {
        for (returnHome in listOf(false,true)) {
            val rpc=Rpc().apply {state=8};val c=controller(rpc)
            c.cleaning(CameraCleaningAction.START);advanceUntilIdle()
            assertTrue(c.viewerStartedActivity.value)
            c.endViewing(returnToDock=returnHome);advanceUntilIdle()
            assertEquals(returnHome,"app_charge" in rpc.methods())
            assertFalse("app_pause" in rpc.methods());assertFalse("app_stop" in rpc.methods())
            c.dispose();advanceUntilIdle()
        }
    }
    @Test fun resume_does_not_claim_ownership_of_existing_cleaning() = runTest {
        val rpc=Rpc().apply {state=10;cleaning=2};val c=controller(rpc)
        c.cleaning(CameraCleaningAction.RESUME);advanceUntilIdle()
        assertFalse(c.viewerStartedActivity.value)
        c.dispose();advanceUntilIdle()
    }
    @Test fun dock_refusal_keeps_viewer_open_and_reports_failure() = runTest {
        val rpc=Rpc().apply {refuse="app_charge"};val c=controller(rpc)
        var ended=false;c.dock {ended=true};advanceUntilIdle()
        assertFalse(ended);assertNotNull(c.error.value)
        c.dispose();advanceUntilIdle()
    }
    @Test fun cleaning_after_driving_releases_manual_mode_first() = runTest {
        val rpc=Rpc();val c=controller(rpc)
        c.hold(CameraMotionController.Direction.LEFT);runCurrent();c.release();advanceUntilIdle()
        c.cleaning(CameraCleaningAction.START);advanceUntilIdle()
        assertTrue(rpc.methods().indexOf("app_rc_end") < rpc.methods().indexOf("app_start"))
        assertEquals("app_start",rpc.methods().last());assertNull(c.error.value)
        c.dispose();advanceUntilIdle()
    }

    @Test fun background_preserves_cleaning_and_resets_viewer_activity() = runTest {
        val rpc=Rpc().apply {state=8};val c=controller(rpc)
        c.cleaning(CameraCleaningAction.START);advanceUntilIdle();assertTrue(c.viewerStartedActivity.value)
        c.background();advanceUntilIdle()
        assertFalse(c.viewerStartedActivity.value);assertEquals(5,rpc.state)
        assertFalse("app_pause" in rpc.methods());assertFalse("app_charge" in rpc.methods());assertFalse("app_stop" in rpc.methods())
        c.dispose();advanceUntilIdle()
    }

}
