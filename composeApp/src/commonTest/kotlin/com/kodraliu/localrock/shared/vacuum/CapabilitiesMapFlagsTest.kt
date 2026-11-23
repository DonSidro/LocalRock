package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals

class CapabilitiesMapFlagsTest {
    @Test
    fun buildCapabilities_carries_the_map_flags() {
        val curv = buildCapabilities("roborock.vacuum.a245", "4499197267967999", "00000000000202058CC57FFDA86836DD5BBFAF7F7EFEFFFF", null)
        val pro = buildCapabilities("roborock.vacuum.a101", "2247397454282751", "42BA8F5C7EDAFFFE", null)
        assertEquals(31, curv.maxRooms)
        assertEquals(15, pro.maxRooms)
        assertEquals(true, MapTool.REMOVE in curv.mapTools)
        assertEquals(false, MapTool.REMOVE in pro.mapTools)
    }
}
