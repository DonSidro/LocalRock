package com.kodraliu.localrock.shared.vacuum.camera

/** Official remote viewing capability is separate from LocalRock compatibility. */
enum class CameraModelSupport {
    UNAVAILABLE, TESTED, EXPERIMENTAL;

    val showsVideoEntry: Boolean get() = this != UNAVAILABLE
    val allowsRobotControls: Boolean get() = this == TESTED

    fun canConnect(testingEnabled: Boolean): Boolean =
        this == TESTED || (this == EXPERIMENTAL && testingEnabled)
}

// Exact model IDs only: related names and obstacle-avoidance sensors do not imply
// remote viewing. Sources and the procedure for extending this list are in
// docs/camera-live-view.md. Official support does not verify LocalRock's protocol.
private val experimentalVideoModels = setOf(
    "roborock.vacuum.a10",  // S6 MaxV
    "roborock.vacuum.a97",  // S8 MaxV Ultra
    "roborock.vacuum.a87",  // Qrevo MaxV
    "roborock.vacuum.a117", // Qrevo Master
    "roborock.vacuum.a21",  // Qrevo Slim
    "roborock.vacuum.a135", // Qrevo Curv
    "roborock.vacuum.a156", // Qrevo Edge
    "roborock.vacuum.a147", // Saros 10
    "roborock.vacuum.a144", // Saros 10R
)

fun cameraModelSupport(model: String?): CameraModelSupport = when (model) {
    "roborock.vacuum.a27" -> CameraModelSupport.TESTED
    in experimentalVideoModels -> CameraModelSupport.EXPERIMENTAL
    else -> CameraModelSupport.UNAVAILABLE
}

fun experimentalCameraSupported(model: String?): Boolean = cameraModelSupport(model).showsVideoEntry
