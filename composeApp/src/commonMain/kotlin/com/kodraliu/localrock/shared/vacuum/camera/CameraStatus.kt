package com.kodraliu.localrock.shared.vacuum.camera

/** `newFeatureSet` bit for two-key real-time video (python-roborock NewFeatureStrBit). */
const val TWO_KEY_VIDEO_FEATURE_BIT = 32

/** Bits that mark a robot with real-time video: two-key (32), two-key while charging (33), video patrol (48), video while charging and standby (73). */
val CAMERA_FEATURE_BITS = listOf(32, 33, 48, 73)
