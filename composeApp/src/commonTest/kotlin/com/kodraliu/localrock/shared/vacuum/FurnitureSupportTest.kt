package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FurnitureSupportTest {
    private fun support(json: String) = furnitureSupportFromInitStatus(Json.parseToJsonElement(json))

    @Test fun reads_furniture_from_the_decimal_feature_integer_in_both_reply_shapes() {
        assertEquals(FurnitureSupport.SUPPORTED, support("[{\"new_feature_info\":68719476736}]"))
        assertEquals(FurnitureSupport.SUPPORTED, support("{\"new_feature_info\":\"68719476736\"}"))
        assertEquals(FurnitureSupport.SUPPORTED, support("[{\"new_feature_info\":18446744073709551615}]"))
        assertEquals(FurnitureSupport.UNSUPPORTED, support("[{\"new_feature_info\":0}]"))
        assertEquals(FurnitureSupport.UNSUPPORTED, support("[{\"new_feature_info\":34359738368}]"))
        assertEquals(FurnitureSupport.UNSUPPORTED, support("[{\"new_feature_info\":137438953472}]"))
    }

    @Test fun the_hex_string_and_server_metadata_cannot_override_the_robot_capability() {
        assertEquals(FurnitureSupport.UNSUPPORTED,
            support("[{\"new_feature_info\":0,\"new_feature_info_str\":\"FFFFFFFFFFFFFFFF\"}]"))
        assertEquals(FurnitureSupport.UNKNOWN,
            support("[{\"featureSet\":\"68719476736\",\"newFeatureSet\":\"FFFFFFFFFFFFFFFF\"}]"))
        assertEquals(FurnitureSupport.UNKNOWN,
            support("[{\"new_feature_info_str\":\"1000000000\"}]"))
    }

    @Test fun missing_malformed_and_ambiguous_replies_remain_unknown() {
        assertEquals(FurnitureSupport.UNKNOWN, furnitureSupportFromInitStatus(null))
        for (json in listOf("null", "[]", "{}", "[{}]", "[{},{}]", "true", "68719476736",
            "[{\"new_feature_info\":68719476736},{\"new_feature_info\":0}]")) {
            assertEquals(FurnitureSupport.UNKNOWN, support(json), json)
        }
        for (value in listOf("null", "true", "false", "[]", "{}", "-1", "68719476736.0", "6.8719476736e10",
            "18446744073709551616", "\"\"", "\" 68719476736 \"", "\"0x1000000000\"", "\"+68719476736\"")) {
            assertEquals(FurnitureSupport.UNKNOWN, support("[{\"new_feature_info\":$value}]"), value)
        }
    }
}
