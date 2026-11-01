package com.kodraliu.localrock.shared.vacuum.camera

import kotlin.test.*

class CameraModelSupportTest {
    @Test fun s7_maxv_keeps_video_and_controls_without_an_opt_in() {
        val support = cameraModelSupport("roborock.vacuum.a27")
        assertEquals(CameraModelSupport.TESTED, support)
        assertTrue(support.showsVideoEntry)
        assertTrue(support.canConnect(false))
        assertTrue(support.allowsRobotControls)
    }

    @Test fun documented_video_models_show_an_entry_but_require_opt_in() {
        for (id in listOf("a10", "a97", "a87", "a117", "a21", "a135", "a156", "a147", "a144")) {
            val model = "roborock.vacuum.$id"
            val support = cameraModelSupport(model)
            assertTrue(experimentalCameraSupported(model), model)
            assertEquals(CameraModelSupport.EXPERIMENTAL, support, model)
            assertFalse(support.canConnect(false), model)
            assertTrue(support.canConnect(true), model)
            assertFalse(support.allowsRobotControls, model)
        }
    }

    @Test fun unknown_models_and_similar_variants_cannot_be_opted_in() {
        // S7, S8, Qrevo, Qrevo Pro, Edge variants and malformed IDs stay hidden.
        for (model in listOf(null, "", "roborock.vacuum.a15", "roborock.vacuum.a70",
            "roborock.vacuum.a75", "roborock.vacuum.a103", "roborock.vacuum.a187",
            "roborock.vacuum.a999", "roborock.vacuum.a270", "a27", "S7 MaxV", "roborock.vacuum.a97 ")) {
            val support = cameraModelSupport(model)
            assertFalse(experimentalCameraSupported(model), model)
            assertFalse(support.canConnect(false), model)
            assertFalse(support.canConnect(true), model)
            assertFalse(support.allowsRobotControls, model)
        }
    }
}
