package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.webrtc.selectedPairDiagnostics
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoResolutionTest {
    @Test fun decoded_dimensions_are_available_without_addresses_or_credentials() {
        val stats=selectedPairDiagnostics("relay","host","udp",true,1280,720)
        assertEquals(1280,stats.videoWidth)
        assertEquals(720,stats.videoHeight)
    }
    @Test fun invalid_or_unbounded_dimensions_are_ignored() {
        val stats=selectedPairDiagnostics("host","host","udp",true,-1,999999)
        assertEquals(0,stats.videoWidth)
        assertEquals(0,stats.videoHeight)
    }
}
