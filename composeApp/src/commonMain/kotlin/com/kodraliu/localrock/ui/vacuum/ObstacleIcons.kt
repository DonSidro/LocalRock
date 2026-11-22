package com.kodraliu.localrock.ui.vacuum

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.map.ObstacleKind

/** A 24x24 icon from filled path data, plus optional stroked path data. */
private fun glyph(name: String, fill: String? = null, stroke: String? = null): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        if (fill != null) addPath(addPathNodes(fill), fill = SolidColor(Color.White))
        if (stroke != null) addPath(
            addPathNodes(stroke), stroke = SolidColor(Color.White), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

// Our own simple drawings of the subjects the official obstacle_new_p* icons show; Material where one fits.
private val Cone = glyph("cone", fill = "M10.2 3h3.6l4.1 14H6.1z M4 18h16v2.5H4z")
private val UnknownCone = glyph("cone_more", fill = "M8.2 5h3l3.4 12H4.8z M2.5 18h14v2.5h-14z M19.5 2a3.5 3.5 0 1 1 0 7a3.5 3.5 0 0 1 0-7z")
private val PetWaste = glyph("pet_waste", fill = "M5 20.5h14a3 3 0 0 0 0-6h-0.8a3 3 0 0 0-2.2-5h-0.5a3.5 3.5 0 0 0-4-5.5c0.8 1.5 0.2 3-1.3 3.5a3 3 0 0 0-2.4 5H7a3 3 0 0 0-2 6z")
private val Footwear = glyph("footwear", fill = "M2 15.5V9l3.5-1 1.5 2.5h2l1.5-3 2 0.5c0.5 2.5 2.5 4 5 4.5l3.5 1V15.5z M2 17h20v2.5H2z")
private val Pedestal = glyph("pedestal", fill = "M10.8 3h2.4v12.5h-2.4z M12 15a8 3.5 0 1 1 0 7a8 3.5 0 1 1 0-7z")
private val PowerStrip = glyph("power_strip", fill = "M3 6h15a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", stroke = "M18 13c0 4 2 5 4 5")
private val Fabric = glyph("fabric", fill = "M8 2.5h7v9l3.6 4.5a3.2 3.2 0 0 1-5 4L8 13.5z")
private val Dustpan = glyph("dustpan", fill = "M3 11h18l-2.5 9.5h-13z M10.8 2.5h2.4V11h-2.4z")
private val Cords = glyph("cords", stroke = "M2.5 15c1.5-6 5.5-7 5.5-2.5S6 18 8.5 18s3-8 6-8 1 8 3.5 8 2-4 3.5-4")
private val Mirror = glyph("mirror", fill = "M9 2h7a1 1 0 0 1 1 1v15a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z M6 21.5l2.5-3h1.5l-2 3z")
private val PlasticBag = glyph("plastic_bag", fill = "M8.5 2.5l3.5 3 3.5-3-0.5 4.5c3.5 2 5.5 6 4.5 13.5H4.5C3.5 13 5.5 9 9 7z")

private fun ObstacleKind.vector(): ImageVector = when (this) {
    ObstacleKind.WIRE -> Icons.Filled.Power
    ObstacleKind.PET_WASTE -> PetWaste
    ObstacleKind.FOOTWEAR -> Footwear
    ObstacleKind.PEDESTAL -> Pedestal
    ObstacleKind.POWER_STRIP -> PowerStrip
    ObstacleKind.SCALE -> Icons.Filled.MonitorWeight
    ObstacleKind.FABRIC -> Fabric
    ObstacleKind.OBSTACLE -> Cone
    ObstacleKind.DUSTPAN -> Dustpan
    ObstacleKind.FURNITURE -> Icons.Filled.Chair
    ObstacleKind.CORDS -> Cords
    ObstacleKind.PET -> Icons.Filled.Pets
    ObstacleKind.FABRIC_BALL -> Icons.Filled.Cloud
    ObstacleKind.MIRROR -> Mirror
    ObstacleKind.GREENERY -> Icons.Filled.LocalFlorist
    ObstacleKind.HANGER -> Icons.Filled.Checkroom
    ObstacleKind.PLASTIC_BAG -> PlasticBag
    ObstacleKind.CARRYOUT_BAG -> Icons.Filled.ShoppingBag
    ObstacleKind.CARD -> Icons.Filled.CreditCard
    ObstacleKind.UNKNOWN -> UnknownCone
}

/** One painter per obstacle kind, for drawing markers inside a Canvas. */
@Composable
internal fun rememberObstacleIcons(): Map<ObstacleKind, Painter> =
    ObstacleKind.entries.associateWith { rememberVectorPainter(it.vector()) }
