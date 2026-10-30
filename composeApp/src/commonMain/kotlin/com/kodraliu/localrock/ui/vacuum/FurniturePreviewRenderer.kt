package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kodraliu.localrock.resources.*
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.roundToInt

/** Original raster illustrations for the catalog and Items list. */
internal enum class FurniturePreviewArtwork(
    val symbol: FurnitureSymbol,
    val fileName: String,
    val resource: DrawableResource,
) {
    WIDE_BED(FurnitureSymbol.WIDE_BED, "furniture_wide_bed.png", Res.drawable.furniture_wide_bed),
    BED(FurnitureSymbol.BED, "furniture_narrow_bed.png", Res.drawable.furniture_narrow_bed),
    TV_STAND(FurnitureSymbol.TV_STAND, "furniture_tv_stand.png", Res.drawable.furniture_tv_stand),
    SOFA(FurnitureSymbol.SOFA, "furniture_multi_seat_sofa.png", Res.drawable.furniture_multi_seat_sofa),
    DOUBLE_SOFA(FurnitureSymbol.DOUBLE_SOFA, "furniture_double_sofa.png", Res.drawable.furniture_double_sofa),
    ARMCHAIR(FurnitureSymbol.ARMCHAIR, "furniture_single_sofa.png", Res.drawable.furniture_single_sofa),
    CHAISE_LEFT(FurnitureSymbol.CHAISE_LEFT, "furniture_left_chaise_sofa.png", Res.drawable.furniture_left_chaise_sofa),
    CHAISE_RIGHT(FurnitureSymbol.CHAISE_RIGHT, "furniture_right_chaise_sofa.png", Res.drawable.furniture_right_chaise_sofa),
    DINING_SET(FurnitureSymbol.DINING_SET, "furniture_dining_set.png", Res.drawable.furniture_dining_set),
    SQUARE_TABLE(FurnitureSymbol.SQUARE_TABLE, "furniture_square_table.png", Res.drawable.furniture_square_table),
    COFFEE_TABLE(FurnitureSymbol.COFFEE_TABLE, "furniture_coffee_table.png", Res.drawable.furniture_coffee_table),
    SHOE_RACK(FurnitureSymbol.SHOE_RACK, "furniture_shoe_rack.png", Res.drawable.furniture_shoe_rack),
    WARDROBE(FurnitureSymbol.WARDROBE, "furniture_wardrobe.png", Res.drawable.furniture_wardrobe),
    NIGHTSTAND(FurnitureSymbol.NIGHTSTAND, "furniture_nightstand.png", Res.drawable.furniture_nightstand),
    LITTER_BOX(FurnitureSymbol.LITTER_BOX, "furniture_litter_box.png", Res.drawable.furniture_litter_box),
    COVERED_LITTER_BOX(FurnitureSymbol.COVERED_LITTER_BOX, "furniture_enclosed_litter_box.png", Res.drawable.furniture_enclosed_litter_box),
    PET_CAGE(FurnitureSymbol.PET_CAGE, "furniture_pet_cage.png", Res.drawable.furniture_pet_cage),
    PET_BED(FurnitureSymbol.PET_BED, "furniture_pet_bed.png", Res.drawable.furniture_pet_bed),
    PET_BOWLS(FurnitureSymbol.PET_BOWLS, "furniture_pet_bowls.png", Res.drawable.furniture_pet_bowls),
    MIRROR(FurnitureSymbol.MIRROR, "furniture_floor_mirror.png", Res.drawable.furniture_floor_mirror),
    CAT_TOWER(FurnitureSymbol.CAT_TOWER, "furniture_cat_tower.png", Res.drawable.furniture_cat_tower),
}

internal fun furniturePreviewArtwork(type: Int, subtype: Int): FurniturePreviewArtwork? {
    val symbol = furnitureSymbol(type, subtype)
    return FurniturePreviewArtwork.entries.firstOrNull { it.symbol == symbol }
}

/** Fit the illustration's own proportions, independently of the saved map footprint. */
internal fun drawFurniturePreviewImage(
    canvas: Canvas,
    image: ImageBitmap,
    bounds: Rect,
    padding: Float,
) {
    val availableWidth = bounds.width - padding * 2
    val availableHeight = bounds.height - padding * 2
    if (availableWidth <= 0f || availableHeight <= 0f) return
    val background = Paint().apply { color = Color.White; isAntiAlias = true }
    val radius = minOf(bounds.width, bounds.height) * .125f
    canvas.drawRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, radius, radius, background)
    val scale = minOf(availableWidth / image.width, availableHeight / image.height)
    val width = (image.width * scale).roundToInt().coerceAtLeast(1)
    val height = (image.height * scale).roundToInt().coerceAtLeast(1)
    canvas.drawImageRect(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(
            (bounds.left + (bounds.width - width) / 2).roundToInt(),
            (bounds.top + (bounds.height - height) / 2).roundToInt(),
        ),
        dstSize = IntSize(width, height),
        paint = Paint().apply { filterQuality = FilterQuality.High },
    )
}
