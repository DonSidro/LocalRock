package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockCapabilitiesTest {
    @Test
    fun intervals_follow_the_exact_legacy_models() {
        val legacy = listOf("a26", "a27", "a29", "a30", "a46", "a47", "a62", "a64", "a65", "a66", "a68", "a69", "a70", "a76")
        for (model in legacy) {
            assertEquals((10..50 step 5).toList(), DockCapabilities("roborock.vacuum.$model", null, null).washIntervals)
        }
        for (model in listOf("roborock.vacuum.a187", "a299", "unknown", null)) {
            assertEquals(listOf(10, 15, 20, 25), DockCapabilities(model, null, null).washIntervals)
        }
    }

    @Test
    fun smart_frequency_requires_its_own_flag_and_modes_preserve_their_codes() {
        val ordinary = DockCapabilities("a187", null, flags(34)).washFrequencyOptions
        assertEquals(listOf(10, 15, 20, 25), ordinary.filter { it.mode == 0 }.map { it.intervalMinutes })
        assertEquals(WashFrequencyOption("By room", 1), ordinary.last())
        assertFalse(ordinary.any { it.mode == 2 })
        assertEquals(WashFrequencyOption("Smart", 2), DockCapabilities("a187", null, flags(109)).washFrequencyOptions.last())
    }

    @Test
    fun wash_modes_respect_dock_flags_and_observed_plumbing_status() {
        fun modes(dock: Int?, dss: Int? = null, vararg bits: Int) =
            DockCapabilities("a187", null, flags(*bits)).washModes(dock, dss).map { it.first }

        assertEquals(listOf(0, 1, 2), modes(17))
        for (dock in listOf(41, 42, 43, 46)) assertEquals(listOf(0), modes(dock))
        for (dock in listOf(null, 4, 12, 25, 36, 999)) assertEquals(listOf(0), modes(dock, 2, 15, 34, 107, 144))
        assertEquals(listOf(10, 0, 1, 2), modes(17, null, 34))
        assertEquals(listOf(10, 0), modes(41, null, 150))
        assertEquals(listOf(0, 1), modes(41, null, 144))
        assertEquals(listOf(0, 1, 2), modes(17, null, 15))
        assertEquals(listOf(0, 1, 2), modes(17, 1, 15))
        assertEquals(listOf(0, 1, 2, 8), modes(17, 2, 15))
        assertEquals(listOf(10, 0, 1, 2), modes(17, 2, 15, 34))
        assertEquals(listOf(0, 1, 2), modes(17, null, 107))
        assertEquals(listOf(10, 0, 8), modes(41, null, 15, 107, 150))
    }

    @Test
    fun wash_readback_aliases_and_labels_preserve_unknown_values() {
        val ordinary = DockCapabilities("a187", null, null)
        for (dock in listOf(41, 42, 43, 46)) {
            assertEquals(8, ordinary.washModeCode(2, dock))
            assertEquals(10, ordinary.washModeCode(1, dock))
        }
        assertEquals(1, DockCapabilities("a187", null, flags(144)).washModeCode(1, 41))
        assertEquals(2, ordinary.washModeCode(2, 17))
        assertEquals("Super Deep", ordinary.washModeLabel(2, 41))
        assertEquals("Soak", DockCapabilities("a187", null, flags(107)).washModeLabel(8, 17))
        assertEquals("Unknown (99)", ordinary.washModeLabel(99, 17))
        assertEquals("Unavailable", ordinary.washModeLabel(null, 17))
    }

    @Test
    fun dust_modes_use_decimal_feature_set_and_keep_strong_display_only() {
        val supported = DockCapabilities("a187", "33554432", null)
        assertEquals(listOf(0, 1, 2, 4), supported.autoEmptyModes.map { it.first })
        for (featureSet in listOf(null, "invalid", "67108864")) {
            assertTrue(DockCapabilities("a187", featureSet, flags(25, 53)).autoEmptyModes.isEmpty())
        }
        assertEquals("Strong", supported.autoEmptyModeLabel(3))
        assertEquals("Unknown (99)", supported.autoEmptyModeLabel(99))
        assertEquals("Unavailable", supported.autoEmptyModeLabel(null))
    }

    private fun flags(vararg bits: Int): String {
        val digits = CharArray(38) { '0' }
        for (bit in bits) {
            val index = digits.lastIndex - bit / 4
            digits[index] = (digits[index].digitToInt(16) or (1 shl (bit % 4))).digitToChar(16)
        }
        return digits.concatToString()
    }
}
