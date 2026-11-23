package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class CruisePinsTest {
    @Test
    fun the_pin_head_sits_one_stem_above_its_tip() {
        assertEquals(Offset(50f, 83f), cruisePinHead(Offset(50f, 100f), 17f))
    }
}
