package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Device page RPCs (track B2). Names and bodies from the plugin's dock bar and mode panel (rpc-catalog.md, cleaning-and-dock-settings.md 2.2).

/** Stops a running dock empty; the dock bar's "Stop Emptying". Not yet seen on a robot. */
suspend fun VacuumSession.appStopCollectDust(): V1Response =
    sendCommand("app_stop_collect_dust").requireOk("app_stop_collect_dust")

/** Continues a global clean after a halfway return, as captured from the official app: `app_start [{"clean_mop":0}]`. */
suspend fun VacuumSession.appStartContinue(): V1Response =
    sendCommand("app_start", listOf(JsonObject(mapOf("clean_mop" to JsonPrimitive(0))))).requireOk("app_start")
