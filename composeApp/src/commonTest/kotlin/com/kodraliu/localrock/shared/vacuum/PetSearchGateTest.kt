package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PetSearchGateTest {
    @Test
    fun bit_48_turns_pet_search_on() {
        assertTrue(isPetSearchSupported("1000000000000"))
    }

    @Test
    fun the_pet_snapshot_bit_alone_does_not() {
        assertFalse(isPetSearchSupported("4000000000000"))
    }

    @Test
    fun a_missing_feature_set_is_off() {
        assertFalse(isPetSearchSupported(null))
    }

    @Test
    fun the_patrol_kind_in_common_status_tells_a_pet_search() {
        assertTrue(VacuumStatus(commonStatus = 2 shl 3).isPetPatrol)
        assertFalse(VacuumStatus(commonStatus = 1 shl 3).isPetPatrol)
        assertFalse(VacuumStatus().isPetPatrol)
    }
}
