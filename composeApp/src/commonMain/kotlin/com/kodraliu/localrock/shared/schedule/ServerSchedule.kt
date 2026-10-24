package com.kodraliu.localrock.shared.schedule

import kotlinx.serialization.json.*

/** Keep the original payload: firmware-specific fields must survive edits. */
data class ServerSchedule(val raw: JsonObject) {
    val id: String get() = requireNotNull((raw["id"] as? JsonPrimitive)?.contentOrNull)
    val cron: String get() = (raw["cron"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val timezone: String get() = (raw["timeZoneId"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val enabled: Boolean get() = (raw["enabled"] as? JsonPrimitive)?.booleanOrNull ?: false
    val repeated: Boolean get() = (raw["repeated"] as? JsonPrimitive)?.booleanOrNull ?: true
    val nextFire: String? get() = (raw["nextFireTime"] as? JsonPrimitive)?.contentOrNull
    val param: JsonObject? get() = when (val value = raw["param"]) {
        is JsonObject -> value
        is JsonPrimitive -> runCatching { Json.parseToJsonElement(value.content) as? JsonObject }.getOrNull()
        else -> null
    }
    val settings: JsonObject? get() = (param?.get("params") as? JsonArray)?.singleOrNull() as? JsonObject
    val nativeName: String? get() = (settings?.get("name") as? JsonPrimitive)?.contentOrNull
    val mapIndex: String? get() = (settings?.get("map_index") as? JsonPrimitive)?.contentOrNull
    val weekly: WeeklyTime? get() = if (repeated) WeeklyTime.parse(cron) else null
    val editable: Boolean get() = weekly != null && timezone.isNotBlank() && settings != null &&
        (param?.get("method") as? JsonPrimitive)?.content == "server_scheduled_start"

    fun settingsText(key: String): String = (settings?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

    /** Only changed settings are supplied; omitted model fields and native timer identity survive. */
    fun update(time: WeeklyTime, zone: String, enabled: Boolean, changes: Map<String, JsonElement> = emptyMap()): JsonObject {
        require(editable) { "This schedule format is read-only" }
        require(zone.isNotBlank()) { "Enter an IANA timezone" }
        require(changes.keys.all { it in EDITABLE_SETTINGS }) { "Unsupported setting" }
        return buildJsonObject {
            put("cron", time.cron())
            put("timeZoneId", zone.trim())
            put("enabled", enabled)
            if (changes.isNotEmpty()) {
                put("param", JsonObject(param!!.toMutableMap().apply {
                    put("params", JsonArray(listOf(JsonObject(settings!!.toMutableMap().apply { putAll(changes) }))))
                }))
            }
        }
    }

    // nextFireTime changes with time and is not part of a user's editable snapshot.
    fun snapshot(): JsonObject = JsonObject(raw.filterKeys { it != "nextFireTime" })

    companion object {
        val EDITABLE_SETTINGS = setOf("segments", "repeat", "fan_power", "water_box_mode", "mop_mode")
        fun create(time: WeeklyTime, zone: String, settings: Map<String, JsonElement>): JsonObject {
            require(zone.isNotBlank()) { "Enter an IANA timezone" }
            require(settings.keys.all { it in EDITABLE_SETTINGS })
            return buildJsonObject {
                put("cron", time.cron()); put("timeZoneId", zone.trim())
                // New jobs are explicitly enabled after review, not while editing.
                put("enabled", false); put("repeated", true); put("type", "clean")
                put("param", buildJsonObject {
                    put("id", 1); put("method", "server_scheduled_start")
                    put("params", JsonArray(listOf(buildJsonObject {
                        put("repeat", 1); put("segments", "0")
                        settings.forEach { (key, value) -> put(key, value) }
                    })))
                })
            }
        }
    }
}

data class WeeklyTime(val hour: Int, val minute: Int, val days: Set<Int>) {
    fun cron(): String {
        require(hour in 0..23 && minute in 0..59 && days.isNotEmpty() && days.all { it in 0..6 }) {
            "Choose a valid time and at least one weekday"
        }
        return "$minute $hour ? * ${days.sorted().joinToString(",")}"
    }
    companion object {
        /** Only losslessly editable weekly patterns; never turn dated/complex cron into daily. */
        fun parse(cron: String): WeeklyTime? {
            val parts = cron.trim().split(Regex("\\s+"))
            if (parts.size != 5 || parts[2] !in listOf("*", "?") || parts[3] != "*") return null
            val minute = parts[0].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            val hour = parts[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
            val days = mutableSetOf<Int>()
            if (parts[4] in listOf("*", "?")) days.addAll(0..6) else {
                for (item in parts[4].split(',')) {
                    val range = item.split('-')
                    if (range.size !in 1..2) return null
                    val start = range[0].toIntOrNull()?.takeIf { it in 0..7 } ?: return null
                    val end = range.last().toIntOrNull()?.takeIf { it in start..7 } ?: return null
                    (start..end).forEach { days.add(it % 7) }
                }
            }
            return WeeklyTime(hour, minute, days).takeIf { days.isNotEmpty() }
        }
    }
}

/** Native timer state is independent of the server job's enabled flag. */
fun nativeTimerState(result: JsonElement?, name: String): String {
    val entries = result as? JsonArray ?: return "Unknown"
    val matches = entries.filterIsInstance<JsonArray>().filter {
        (it.getOrNull(0) as? JsonPrimitive)?.contentOrNull == name
    }
    if (matches.isEmpty()) return "Missing"
    if (matches.size != 1) return "Ambiguous"
    return when ((matches.single().getOrNull(1) as? JsonPrimitive)?.contentOrNull) {
        "on" -> "On"
        "off" -> "Off"
        else -> "Unknown"
    }
}
