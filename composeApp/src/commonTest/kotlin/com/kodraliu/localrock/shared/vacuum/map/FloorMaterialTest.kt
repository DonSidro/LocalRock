package com.kodraliu.localrock.shared.vacuum.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FloorMaterialTest {

    @Test
    fun known_ids() {
        assertEquals(FloorMaterial.DEFAULT, FloorMaterial.fromId(0))
        // A Qrevo 3 Pro reports 2 for its fully carpeted rooms (#26).
        assertEquals(FloorMaterial.CARPET, FloorMaterial.fromId(2))
        assertEquals(FloorMaterial.WOOD, FloorMaterial.fromId(3))
        assertEquals(FloorMaterial.TILE, FloorMaterial.fromId(4))
    }

    @Test
    fun other_ids_stay_unknown() {
        assertNull(FloorMaterial.fromId(1))
        assertNull(FloorMaterial.fromId(5))
    }
}
