package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockSettingsPageTest {
    private fun flags(vararg bits: Int): String {
        val digits = CharArray(38) { '0' }
        for (bit in bits) {
            val index = digits.lastIndex - bit / 4
            digits[index] = (digits[index].digitToInt(16) or (1 shl (bit % 4))).digitToChar(16)
        }
        return String(digits)
    }

    @Test
    fun washing_mode_value_adds_the_temperature_only_when_reported() {
        val caps = DockCapabilities("roborock.vacuum.a245", null, flags(34))
        assertEquals("Light | High Temperature", caps.washingModeValue(0, 29, 2))
        // A high-temperature dock reads 1 as High Temperature, as the plugin does.
        assertEquals("Light | High Temperature", caps.washingModeValue(0, 29, 1))
        assertEquals("Balanced | Hot Water", caps.washingModeValue(1, 9, 2))
        assertEquals("Light", caps.washingModeValue(0, 29, null))
        assertEquals("Unavailable", caps.washingModeValue(null, 29, 2))
    }

    @Test
    fun ai_wash_robots_name_the_smart_mode_smart_mop_washing() {
        assertEquals("Smart Mop Washing", DockCapabilities("a245", null, flags(34, 144)).washingModeValue(10, 29, 2))
        assertEquals("Smart | High Temperature", DockCapabilities("a245", null, flags(34)).washingModeValue(10, 29, 2))
    }

    @Test
    fun mop_wash_frequency_uses_the_official_value_names() {
        assertEquals("Smart Mop Wash", washFrequencyLabel(SmartWashSettings(2, 1200)))
        assertEquals("By Room", washFrequencyLabel(SmartWashSettings(1, 1200)))
        assertEquals("20 min", washFrequencyLabel(SmartWashSettings(0, 1200)))
    }

    @Test
    fun drain_row_follows_feature_bit_27_and_stays_when_the_set_is_unknown() {
        assertTrue(DockCapabilities("a245", null, flags(27)).drainOnboardTankSupported)
        assertFalse(DockCapabilities("a245", null, flags(34)).drainOnboardTankSupported)
        assertTrue(DockCapabilities("a245", null, null).drainOnboardTankSupported)
    }

    @Test
    fun tray_self_clean_needs_a_listed_dock_without_the_plumbing_module() {
        // The Curv 2 Flow reports dock_type 29 and dss 168.
        assertTrue(DockOptions.traySelfCleanSupported(29, 168))
        assertFalse(DockOptions.traySelfCleanSupported(29, 168 or 2))
        assertFalse(DockOptions.traySelfCleanSupported(9, 169))
        assertFalse(DockOptions.traySelfCleanSupported(null, null))
    }

    @Test
    fun cleaning_fluid_is_reported_by_dss_bits_10_and_11() {
        assertFalse(DockOptions.cleanFluidReported(168))
        assertTrue(DockOptions.cleanFluidReported(168 or (1 shl 10)))
        assertFalse(DockOptions.cleanFluidReported(null))
    }

    @Test
    fun auto_empty_frequency_is_bit_53_with_off_balanced_high_and_replaces_the_switch() {
        val def = BetaFeatureDefs.first { it.id == "dust_collection_interval" }
        assertEquals(53, def.bit)
        assertEquals("set_dust_collection_switch_status", def.setMethod)
        assertEquals(listOf(0 to "Off", 1 to "Balanced", 2 to "High"), def.choices!!.map { it.value to it.label })
        assertEquals("dust_collection_switch", def.replaces)
        assertTrue(def in supportedBetaFeatures(flags(53)))
        assertFalse(supportedBetaFeatures(flags(34)).any { it.id == def.id })
        val states = listOf(BetaFeatureState(BetaFeatureDefs.first { it.id == "dust_collection_switch" }, 1, "status"), BetaFeatureState(def, 2, "status"))
        assertEquals(listOf("dust_collection_interval"), states.withoutReplaced().map { it.def.id })
    }

    @Test
    fun auto_empty_frequency_writes_the_bare_status_object_first() {
        val def = BetaFeatureDefs.first { it.id == "dust_collection_interval" }
        assertEquals("""{"status":2}""", featureSetCandidates(def, "status", 2).first().toString())
    }
}
