package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureSetFromInitStatusTest {
    private fun set(json: String) = featureSetFromInitStatus(Json.parseToJsonElement(json))

    // A Qrevo Pro's own flags: the string the server keeps as newFeatureSet.
    private val qrevo = "42BA8F5C7EDAFFFE"

    @Test
    fun the_flag_string_is_read_from_an_object_or_a_list_of_one() {
        assertEquals(qrevo, set("""{"new_feature_info_str":"$qrevo"}"""))
        assertEquals(qrevo, set("""[{"new_feature_info_str":"$qrevo","new_feature_info":1}]"""))
    }

    @Test
    fun a_missing_empty_or_non_hex_string_is_null() {
        assertNull(set("""{"other":1}"""))
        assertNull(set("""{"new_feature_info_str":""}"""))
        assertNull(set("""{"new_feature_info_str":"not hex"}"""))
        assertNull(set("[]"))
        assertNull(set("null"))
    }

    @Test
    fun the_robots_own_flags_switch_smartplan_on_where_the_server_record_has_none() {
        val fromRobot = set("""{"new_feature_info_str":"$qrevo"}""")
        assertFalse(isSmartPlanSupported(null))
        assertTrue(isSmartPlanSupported(fromRobot) == newFeatureBit(qrevo, 55))
    }
}
