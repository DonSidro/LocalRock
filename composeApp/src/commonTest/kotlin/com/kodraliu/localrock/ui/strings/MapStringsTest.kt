package com.kodraliu.localrock.ui.strings

import kotlin.test.Test
import kotlin.test.assertEquals

class MapStringsTest {

    @Test
    fun rotate_label_carries_a_real_degree_sign() {
        // Written as an escape so a mis-encoded source file cannot hide the check.
        assertEquals("90\u00B0", MapStrings.ROTATE_90)
    }
}
