package com.kodraliu.localrock.shared.vacuum.map

/** Type/subtype pairs observed for all 21 S7 MaxV picker choices. Sizes are LocalRock starting
 * sizes based on saved footprints, not claims about OEM defaults or firmware size limits. */
data class FurnitureKind(val type: Int, val subtype: Int, val label: String, val widthMm: Int, val depthMm: Int)

val FURNITURE_CATALOG: List<FurnitureKind> = listOf(
    FurnitureKind(45, 2, "Wide bed", 2400, 2400),
    FurnitureKind(45, 1, "Narrow bed", 1200, 2400),
    FurnitureKind(43, 0, "TV stand", 1000, 400),
    FurnitureKind(46, 3, "Multi-seat sofa", 2000, 836),
    FurnitureKind(46, 2, "Double sofa", 2000, 1000),
    FurnitureKind(46, 1, "Single sofa", 1000, 1000),
    FurnitureKind(46, 4, "Left chaise sofa", 2000, 800),
    FurnitureKind(46, 5, "Right chaise sofa", 2000, 806),
    FurnitureKind(47, 0, "Dining set", 1200, 800),
    FurnitureKind(48, 2, "Square table", 1500, 1500),
    FurnitureKind(48, 1, "Coffee table", 1200, 900),
    FurnitureKind(49, 0, "Shoe rack", 1000, 400),
    FurnitureKind(51, 0, "Wardrobe", 2500, 700),
    FurnitureKind(50, 0, "Nightstand", 400, 400),
    FurnitureKind(52, 0, "Litter box", 350, 500),
    FurnitureKind(53, 0, "Enclosed litter box", 500, 700),
    FurnitureKind(54, 0, "Pet cage", 400, 400),
    FurnitureKind(55, 0, "Pet bed", 411, 400),
    FurnitureKind(56, 0, "Pet bowls", 300, 150),
    FurnitureKind(57, 0, "Floor mirror", 400, 300),
    FurnitureKind(58, 0, "Cat tower", 400, 400),
)

fun furnitureKind(type: Int, subtype: Int): FurnitureKind? =
    FURNITURE_CATALOG.firstOrNull { it.type == type && it.subtype == subtype }
