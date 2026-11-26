package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureCodecsTest {
    private val photos = BetaFeatureDefs.first { it.id == "obstacle_photos" }
    private val avoidance = BetaFeatureDefs.first { it.id == "obstacle_avoidance" }

    @Test
    fun a_switch_that_needs_another_bit_says_why_it_is_blocked() {
        assertEquals("Enable Reactive Obstacle Avoidance", featureBlockedReason(photos, "[0]"))
        assertNull(featureBlockedReason(photos, "[1]"))
    }

    @Test
    fun no_reason_without_a_readable_answer_or_a_camera_status_codec() {
        assertNull(featureBlockedReason(photos, null))
        assertNull(featureBlockedReason(photos, "not json"))
        assertNull(featureBlockedReason(BetaFeatureDefs.first { it.id == "carpet_boost" }, "[0]"))
    }

    @Test
    fun turning_avoidance_off_clears_the_bits_that_depend_on_it() {
        val c = avoidance.codec as FeatureCodec.CameraStatusBit
        val word = (1 shl 0) or (1 shl 6) or (1 shl 10) or (1 shl 14) or (1 shl 20)
        assertEquals(1 shl 20, cameraWordFor(c, word, on = false))
        assertEquals(word, cameraWordFor(c, word, on = true))
    }

    @Test
    fun vac_then_mop_needs_bit_93_and_bit_114_too_on_an_fcc_robot() {
        fun flags(vararg bits: Int): String {
            val digits = CharArray(38) { '0' }
            for (bit in bits) {
                val i = digits.lastIndex - bit / 4
                digits[i] = (digits[i].digitToInt(16) or (1 shl (bit % 4))).digitToChar(16)
            }
            return String(digits)
        }
        assertFalse(cleanThenMopSupported(flags(), fcc = false))
        assertTrue(cleanThenMopSupported(flags(93), fcc = false))
        assertFalse(cleanThenMopSupported(flags(93), fcc = true))
        assertFalse(cleanThenMopSupported(flags(93), fcc = null))
        assertTrue(cleanThenMopSupported(flags(93, 114), fcc = true))
    }
}
