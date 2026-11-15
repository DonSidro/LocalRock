package com.kodraliu.localrock.shared.validation

import com.kodraliu.localrock.shared.mqtt.*
import com.kodraliu.localrock.shared.protocol.*
import com.kodraliu.localrock.shared.schedule.*
import com.kodraliu.localrock.shared.vacuum.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import kotlin.test.*

/** Runs the unmodified repository/session/codec through an in-memory MQTT transport. */
class NativeRestoreFlowTest {
    private class Mirror(var jobs: List<ServerSchedule>) : ScheduleMirror {
        var reads = 0
        var writes = 0
        override suspend fun list(duid: String): List<ServerSchedule> { reads++; return jobs }
        override suspend fun create(duid: String, job: JsonObject): String { writes++; error("Unexpected create") }
        override suspend fun update(duid: String, jobId: String, job: JsonObject) {
            writes++
            jobs = jobs.map { if (it.id == jobId) ServerSchedule(JsonObject(it.raw + job)) else it }
        }
        override suspend fun delete(duid: String, jobId: String) { writes++; error("Unexpected delete") }
    }

    private class Robot : MqttNativeTransport {
        val codec = RoborockCodec("0123456789abcdef", prefixed = false)
        val calls = mutableListOf<Pair<String, JsonElement>>()
        var native: Boolean? = null
        var nativeId = "1680623937705"
        var cron: String? = null
        var settings: JsonObject? = null
        var overrideFirstRead: JsonElement? = null
        var reads = 0
        var beforeRead: (() -> Unit)? = null
        var refuseSet = false
        var refuseDisable = false
        var failRead = false
        var loseSetReply = false
        var dropSetReply = false
        var loseDisableReply = false
        var ignoreDisable = false
        var afterSet: (() -> Unit)? = null
        var afterDisable: (() -> Unit)? = null
        var subscribed: (() -> Unit)? = null
        override fun open(host: String, port: Int, tls: Boolean, username: String, password: String,
            clientId: String, keepAliveSeconds: Int, onConnected: () -> Unit,
            onFailed: (String) -> Unit, onMessage: (String, ByteArray) -> Unit,
            onDisconnected: () -> Unit): MqttNativeConnection {
            onConnected()
            return object : MqttNativeConnection {
                override val isConnected = true
                override fun subscribe(topic: String, qos: Int) { subscribed?.invoke() }
                override fun unsubscribe(topic: String) {}
                override fun close() {}
                override fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean) {
                    val frame = codec.decode(payload).single()
                    val outer = Json.parseToJsonElement(frame.payload.decodeToString()).jsonObject
                    val request = Json.parseToJsonElement(outer["dps"]!!.jsonObject["101"]!!.jsonPrimitive.content).jsonObject
                    val method = request["method"]!!.jsonPrimitive.content
                    val params = request["params"]!!
                    calls += method to params
                    var err: JsonObject? = null
                    val result = when (method) {
                        "get_server_timer" -> {
                            reads++
                            beforeRead?.invoke()
                            if (failRead) { err = buildJsonObject { put("code", -1); put("message", "read unavailable") }; JsonNull }
                            else if (reads == 1 && overrideFirstRead != null) overrideFirstRead!!
                            else native?.let { enabled ->
                                buildJsonArray {
                                    add(buildJsonArray { add(nativeId); add(if (enabled) "on" else "off"); add(1) })
                                }
                            } ?: JsonArray(emptyList())
                        }
                        "set_server_timer" -> {
                            if (refuseSet) err = buildJsonObject { put("code", -10005); put("message", "Param error") }
                            else {
                                val row = params.jsonObject["data"]!!.jsonArray.single().jsonArray
                                nativeId = row[0].jsonPrimitive.content
                                cron = row[1].jsonArray[0].jsonPrimitive.content
                                settings = row[1].jsonArray[1].jsonArray[1].jsonObject
                                native = true
                                afterSet?.invoke()
                                if (loseSetReply) throw RuntimeException("Write applied; connection lost before response")
                                if (dropSetReply) return
                            }
                            buildJsonArray { add("ok") }
                        }
                        "upd_server_timer" -> {
                            val row = params.jsonArray.single().jsonArray
                            check(row[0].jsonPrimitive.content == nativeId)
                            if (refuseDisable) err = buildJsonObject { put("code", -10005); put("message", "Param error") }
                            else {
                                if (!ignoreDisable) native = row[1].jsonPrimitive.content == "on"
                                afterDisable?.invoke()
                                if (loseDisableReply) throw RuntimeException("Toggle applied; connection lost before response")
                            }
                            buildJsonArray { add("ok") }
                        }
                        else -> error("Unexpected robot command: $method")
                    }
                    val answer = buildJsonObject {
                        put("id", request["id"]!!)
                        if (err == null) put("result", result) else put("error", err!!)
                    }
                    val response = buildJsonObject {
                        put("dps", buildJsonObject { put("102", answer.toString()) }); put("t", 1_790_000_000)
                    }
                    val bytes = codec.encode(RoborockMessage(RoborockProtocol.RPC_RESPONSE,
                        response.toString().encodeToByteArray(), frame.seq, frame.random, frame.timestamp))
                    onMessage("test/response", bytes)
                }
            }
        }
        fun sets() = calls.count { it.first == "set_server_timer" }
        fun updates() = calls.count { it.first == "upd_server_timer" }
    }

    private fun job(enabled: Boolean = true, segments: JsonElement = JsonPrimitive("0"),
        cron: String = "10 9 ? * 0", nativeName: String? = "1680623937705", unknown: Boolean = false, repeated: Boolean = true): ServerSchedule =
        ServerSchedule(buildJsonObject {
            put("id", 918265); put("cron", cron); put("timeZoneId", "America/Los_Angeles")
            put("enabled", enabled); put("repeated", repeated)
            put("param", buildJsonObject {
                put("id", 1); put("method", "server_scheduled_start")
                put("params", buildJsonArray { add(buildJsonObject {
                    nativeName?.let { put("name", it) }
                    put("segments", segments); put("fan_power", 102); put("water_box_mode", 202)
                    put("mop_mode", 300); put("repeat", 2); put("clean_mop", 1); put("map_index", 1)
                    if (unknown) put("future_model_setting", buildJsonObject { put("keep", true) })
                }) })
            })
        })

    private class Fixture(initial: ServerSchedule) {
        val robot = Robot()
        val mirror = Mirror(listOf(initial))
        val mqtt = MqttClient(robot)
        val repo = VacuumRepository("test-robot", "0123456789abcdef", { "test-key" }, mqtt,
            { MqttTopics("test/request", "test/response") }, { 1_790_000_000 }, scheduleMirror = mirror)
        lateinit var loaded: CleanTimer
        suspend fun start() {
            mqtt.start(MqttCreds("unused.invalid", 8883, true, "test", "test"), "restore-test")
            withTimeout(5_000) { mqtt.connected.first { it } }
            repo.session.start()
            // Session collector runs on Dispatchers.Default. A read handshake ensures it is ready.
            delay(100)
            repo.fetchTimers()
            loaded = repo.timers.value.single()
        }
        suspend fun stop() { repo.session.close(); mqtt.disconnect() }
        suspend fun restore() = repo.restoreNativeTimer(loaded)
        suspend fun failure(): Throwable {
            val result = runCatching { restore() }
            return result.exceptionOrNull() ?: throw AssertionError("Expected restore to refuse this operation")
        }
    }
    private fun exercise(initial: ServerSchedule = job(), block: suspend Fixture.() -> Unit) = runBlocking {
        val f = Fixture(initial)
        try { f.start(); withTimeout(20_000) { f.block() } } finally { f.stop() }
    }

    @Test fun enabled_whole_home_restores_verified_native_id_and_settings() = exercise {
        assertEquals("On", restore())
        assertEquals("1680623937705", robot.nativeId)
        assertEquals("10 9 * * 0", robot.cron)
        assertEquals(JsonPrimitive(1), robot.settings!!["map_index"])
        assertEquals(JsonPrimitive(2), robot.settings!!["repeat"])
        assertEquals(1, robot.sets()); assertEquals(0, robot.updates()); assertEquals(2, robot.reads)
        assertEquals(0, mirror.writes)
    }
    @Test fun disabled_whole_home_restores_off_and_verifies() = exercise(job(enabled = false)) {
        assertEquals("Off", restore()); assertEquals(false, robot.native)
        assertEquals(1, robot.sets()); assertEquals(1, robot.updates()); assertEquals(2, robot.reads)
    }
    @Test fun dated_once_cron_is_preserved() = exercise(job(cron = "30 9 14 11 ?", repeated = false)) {
        restore(); assertEquals("30 9 14 11 *", robot.cron)
    }
    @Test fun existing_timer_is_not_overwritten() = exercise {
        robot.native = true; failure(); assertEquals(0, robot.sets())
    }
    @Test fun room_schedule_is_refused_before_robot_write() = exercise(job(segments = JsonPrimitive("20,17"))) {
        failure(); assertTrue(robot.calls.isEmpty())
    }
    @Test fun server_only_job_is_refused_before_robot_write() = exercise(job(nativeName = null)) {
        failure(); assertTrue(robot.calls.isEmpty())
    }
    @Test fun failed_native_read_never_creates() = exercise {
        robot.failRead = true; failure(); assertEquals(0, robot.sets())
    }
    @Test fun ambiguous_matching_native_timers_never_create() = exercise {
        robot.overrideFirstRead = Json.parseToJsonElement("[[\"1680623937705\",\"on\",1],[\"1680623937705\",\"off\",1]]")
        failure(); assertEquals(0, robot.sets())
    }
    @Test fun refused_create_never_falls_back_or_retries() = exercise {
        robot.refuseSet = true; failure(); assertEquals(1, robot.sets())
        assertFalse(robot.calls.any { it.first == "app_start" || it.first == "set_timer" })
    }
    @Test fun disable_ack_without_effect_is_not_success() = exercise(job(enabled = false)) {
        robot.ignoreDisable = true; failure(); assertEquals(true, robot.native); assertEquals(2, robot.reads)
    }
    @Test fun deleted_server_job_must_not_be_restored_from_stale_card() = exercise {
        mirror.jobs = emptyList()
        runCatching { restore() }
        assertEquals(0, robot.sets(), "A deleted server job must not recreate a robot timer")
    }
    @Test fun changed_server_job_must_not_be_restored_from_stale_card() = exercise {
        mirror.jobs = listOf(job(enabled = false, cron = "15 10 ? * 1"))
        runCatching { restore() }
        assertEquals(0, robot.sets(), "An externally edited schedule must be reloaded before restore")
    }
    @Test fun shared_native_reference_must_block_restore() = exercise {
        mirror.jobs = listOf(job(), ServerSchedule(JsonObject(job().raw + ("id" to JsonPrimitive(918266)))))
        runCatching { restore() }
        assertEquals(0, robot.sets(), "Two server jobs with the same native timer must not be guessed at")
    }
    @Test fun malformed_success_array_must_not_mean_missing() = exercise {
        robot.overrideFirstRead = Json.parseToJsonElement("[\"ok\"]")
        runCatching { restore() }
        assertEquals(0, robot.sets(), "A malformed list is unknown, not evidence that no timer exists")
    }
    @Test fun unsupported_room_array_must_not_restore_as_whole_home() = exercise(job(segments = Json.parseToJsonElement("[20,17]"))) {
        runCatching { restore() }
        assertEquals(0, robot.sets(), "An unsupported segments shape must not be replaced with whole-home defaults")
    }
    @Test fun unknown_settings_must_be_preserved_or_restore_refused() = exercise(job(unknown = true)) {
        runCatching { restore() }
        assertTrue(robot.sets() == 0 || robot.settings?.containsKey("future_model_setting") == true,
            "Restore must not silently drop unsupported model-specific settings")
    }
    @Test fun lost_create_reply_must_be_read_back_without_repeating_write() = exercise {
        robot.loseSetReply = true
        runCatching { restore() }
        assertEquals(1, robot.sets())
        assertTrue(robot.reads >= 2, "A possibly successful write needs a read-back")
    }
    @Test fun lost_disable_reply_must_be_read_back() = exercise(job(enabled = false)) {
        robot.loseDisableReply = true
        runCatching { restore() }
        assertEquals(false, robot.native)
        assertTrue(robot.reads >= 2, "The applied off state should be verified after a lost reply")
    }
    @Test fun real_create_response_timeout_must_be_read_back() = exercise {
        robot.dropSetReply = true
        // The read-back confirms the timer, so restore may report it rather than fail.
        assertEquals("On", restore())
        assertEquals(1, robot.sets())
        assertTrue(robot.reads >= 2, "Timeout after applied creation must trigger a read-back")
    }
    @Test fun failed_disable_must_explicitly_report_partial_restore() = exercise(job(enabled = false)) {
        robot.refuseDisable = true
        val error = failure()
        assertEquals(true, robot.native)
        assertTrue(error.message.orEmpty().contains("restor", ignoreCase = true) &&
            (error.message.orEmpty().contains("on", ignoreCase = true) || error.message.orEmpty().contains("unconfirmed", ignoreCase = true)),
            "Error must explain that creation succeeded and the disabled state was not established; got: ${error.message}")
    }
    @Test fun failed_disable_retry_needs_a_recovery_path() = exercise(job(enabled = false)) {
        robot.refuseDisable = true; failure(); robot.refuseDisable = false
        runCatching { restore() }
        assertEquals(false, robot.native, "Retry must repair a verified partial restore or direct the user to recovery")
    }
    @Test fun reconcile_between_create_and_disable_must_not_leave_server_enabled() = exercise(job(enabled = false)) {
        // Models #179's independent native-state polling observing the default-on interval.
        robot.afterSet = { mirror.jobs = listOf(job(enabled = true)) }
        restore()
        assertEquals(false, robot.native)
        assertFalse(mirror.jobs.single().enabled, "Success must verify the saved job is still disabled after native reconciliation")
    }
}
