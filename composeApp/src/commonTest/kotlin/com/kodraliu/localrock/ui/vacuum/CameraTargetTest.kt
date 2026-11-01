package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import com.kodraliu.localrock.shared.vacuum.map.*
import kotlin.test.*

class CameraTargetTest {
    private val map=ParsedMap(width=10,height=10,grid=ByteArray(100){128.toByte()},rooms=emptyList(),
        resolution=.05f,pixelOffsetLeft=400,pixelOffsetTop=400,format=MapFormat.LEGACY)
    @Test fun target_uses_robot_coordinates_and_flipped_y() {
        assertEquals(20100 to 20300,cameraTargetAt(map,Offset(.2f,.3f)))
    }
    @Test fun empty_space_walls_edges_and_invalid_numbers_are_rejected() {
        assertNull(cameraTargetAt(map.copy(grid=ByteArray(100)),Offset(.2f,.3f)))
        assertNull(cameraTargetAt(map.copy(grid=ByteArray(100){127}),Offset(.2f,.3f)))
        for(p in listOf(Offset(-.1f,.5f),Offset(1f,.5f),Offset(Float.NaN,.5f))) assertNull(cameraTargetAt(map,p))
    }
    @Test fun no_go_zones_and_other_map_formats_are_rejected() {
        val zone=MapZone.rect(20000,20000,20500,20500,ZoneKind.NO_GO)
        assertNull(cameraTargetAt(map.copy(noGoZones=listOf(zone)),Offset(.2f,.3f)))
        assertNull(cameraTargetAt(map.copy(format=MapFormat.B01),Offset(.2f,.3f)))
    }
}
