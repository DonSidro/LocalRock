package com.kodraliu.localrock.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A robot vacuum in profile: squat domed body with the LiDAR tower on top and a wheel below.
 * Material Icons ships no vacuum glyph (it predates Material Symbols' `robot_vacuum`), and the
 * nearest stand-in, `SmartToy`, is a humanoid robot head — the wrong object entirely.
 *
 * Drawn from the side rather than from above on purpose: a top-down ring with a turret dot and a
 * brush bar inside it reads as a face at list-icon sizes.
 */
val RobotVacuum: ImageVector by lazy {
    ImageVector.Builder(
        name = "RobotVacuum",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // LiDAR tower.
        path(fill = SolidColor(Color.Black)) {
            moveTo(9.4f, 4.4f)
            lineTo(14.6f, 4.4f)
            lineTo(14.6f, 8.2f)
            lineTo(9.4f, 8.2f)
            close()
        }
        // Body: flat deck, domed sides, flat floor — a disc seen edge-on.
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(3f, 12.2f)
            curveTo(3f, 9.1f, 7f, 7.4f, 12f, 7.4f)
            curveTo(17f, 7.4f, 21f, 9.1f, 21f, 12.2f)
            lineTo(21f, 15.4f)
            curveTo(21f, 16.5f, 20.1f, 17.4f, 19f, 17.4f)
            lineTo(5f, 17.4f)
            curveTo(3.9f, 17.4f, 3f, 16.5f, 3f, 15.4f)
            close()
            // Bumper seam across the front.
            moveTo(4.6f, 12.6f)
            lineTo(19.4f, 12.6f)
            lineTo(19.4f, 14.1f)
            lineTo(4.6f, 14.1f)
            close()
        }
        // Drive wheel.
        path(fill = SolidColor(Color.Black)) {
            moveTo(9.8f, 17.4f)
            lineTo(14.2f, 17.4f)
            lineTo(14.2f, 19.6f)
            lineTo(9.8f, 19.6f)
            close()
        }
    }.build()
}
