package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** CleanRecord.clean_type values for the camera runs listed on the History Cruise tab. */
const val CLEAN_TYPE_VIDEO_PATROL = 5
const val CLEAN_TYPE_PET_PATROL = 6
const val CLEAN_TYPE_OBJECT_PATROL = 10

val CleanRecord.isCruise: Boolean
    get() = cleanType == CLEAN_TYPE_VIDEO_PATROL || cleanType == CLEAN_TYPE_PET_PATROL || cleanType == CLEAN_TYPE_OBJECT_PATROL

/** The official app's Cruise tab titles; object patrol has no captured title, so it reads "Cruise". */
val CleanRecord.cruiseTitle: String
    get() = if (cleanType == CLEAN_TYPE_PET_PATROL) "Search for the Pet" else "Cruise"

fun List<CleanRecord>.cruiseRecords(): List<CleanRecord> = filter { it.isCruise }.sortedByDescending { it.beginEpoch }

fun List<CleanRecord>.cleaningRecords(): List<CleanRecord> = filterNot { it.isCruise }

fun parseCleanRecord(result: JsonElement, recordId: Long): CleanRecord? {
    val element = if (result is JsonArray) result.firstOrNull() ?: return null else result
    return when (element) {
        is JsonArray -> {
            if (element.size < 6) return null
            CleanRecord(
                beginEpoch = (element[0] as? JsonPrimitive)?.longOrNull ?: recordId,
                endEpoch = (element[1] as? JsonPrimitive)?.longOrNull ?: 0L,
                durationSeconds = (element[2] as? JsonPrimitive)?.longOrNull ?: 0L,
                areaMm2 = (element[3] as? JsonPrimitive)?.longOrNull ?: 0L,
                errorCode = (element[4] as? JsonPrimitive)?.intOrNull ?: 0,
                complete = (element[5] as? JsonPrimitive)?.intOrNull ?: 0,
            )
        }
        is JsonObject -> CleanRecord(
            beginEpoch = element["begin"]?.jsonPrimitive?.longOrNull ?: recordId,
            endEpoch = element["end"]?.jsonPrimitive?.longOrNull ?: 0L,
            durationSeconds = element["duration"]?.jsonPrimitive?.longOrNull ?: 0L,
            areaMm2 = element["area"]?.jsonPrimitive?.longOrNull ?: 0L,
            errorCode = element["error"]?.jsonPrimitive?.intOrNull ?: 0,
            complete = element["complete"]?.jsonPrimitive?.intOrNull ?: 0,
            startType = element["start_type"]?.jsonPrimitive?.intOrNull ?: 0,
            cleanType = element["clean_type"]?.jsonPrimitive?.intOrNull ?: 0,
            finishReason = element["finish_reason"]?.jsonPrimitive?.intOrNull ?: 0,
            dustCollected = element["dust_collection_status"]?.jsonPrimitive?.intOrNull == 1,
            washCount = element["wash_count"]?.jsonPrimitive?.intOrNull ?: 0,
            cleanedAreaMm2 = element["cleaned_area"]?.jsonPrimitive?.longOrNull,
        )
        else -> null
    }
}
