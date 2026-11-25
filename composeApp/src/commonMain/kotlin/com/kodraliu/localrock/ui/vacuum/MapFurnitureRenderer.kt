package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import com.kodraliu.localrock.shared.vacuum.map.MapFurniture
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import kotlin.math.cos
import kotlin.math.sin

/** Uses the shared map origin and Y flip, including for rotated furniture. */
internal fun furnitureCorners(map: ParsedMap, item: MapFurniture): List<Offset> =
    item.cornersMm.map { map.mmToCell(it.x, it.y) }

internal enum class FurnitureSymbol {
    SOFA, DOUBLE_SOFA, ARMCHAIR, CHAISE_LEFT, CHAISE_RIGHT, DINING_SET, BED, WIDE_BED,
    TV_STAND, SQUARE_TABLE, COFFEE_TABLE, SHOE_RACK, WARDROBE, NIGHTSTAND,
    LITTER_BOX, COVERED_LITTER_BOX, PET_CAGE, PET_BED, PET_BOWLS, MIRROR, CAT_TOWER, UNKNOWN,
}

/** All 21 type/subtype pairs were observed in the OEM picker and save commands. */
internal fun furnitureSymbol(type: Int, subtype: Int): FurnitureSymbol = when (type to subtype) {
    45 to 2 -> FurnitureSymbol.WIDE_BED
    45 to 1 -> FurnitureSymbol.BED
    43 to 0 -> FurnitureSymbol.TV_STAND
    46 to 3 -> FurnitureSymbol.SOFA
    46 to 2 -> FurnitureSymbol.DOUBLE_SOFA
    46 to 1 -> FurnitureSymbol.ARMCHAIR
    46 to 4 -> FurnitureSymbol.CHAISE_LEFT
    46 to 5 -> FurnitureSymbol.CHAISE_RIGHT
    47 to 0 -> FurnitureSymbol.DINING_SET
    48 to 2 -> FurnitureSymbol.SQUARE_TABLE
    48 to 1 -> FurnitureSymbol.COFFEE_TABLE
    49 to 0 -> FurnitureSymbol.SHOE_RACK
    51 to 0 -> FurnitureSymbol.WARDROBE
    50 to 0 -> FurnitureSymbol.NIGHTSTAND
    52 to 0 -> FurnitureSymbol.LITTER_BOX
    53 to 0 -> FurnitureSymbol.COVERED_LITTER_BOX
    54 to 0 -> FurnitureSymbol.PET_CAGE
    55 to 0 -> FurnitureSymbol.PET_BED
    56 to 0 -> FurnitureSymbol.PET_BOWLS
    57 to 0 -> FurnitureSymbol.MIRROR
    58 to 0 -> FurnitureSymbol.CAT_TOWER
    else -> FurnitureSymbol.UNKNOWN
}

/** Bilinear detail coordinates keep the record's exact footprint and corner order. */
internal fun furniturePoint(corners: List<Offset>, x: Float, y: Float): Offset =
    corners[0] * ((1 - x) * (1 - y)) + corners[1] * (x * (1 - y)) +
        corners[2] * (x * y) + corners[3] * ((1 - x) * y)

internal fun drawMapFurniture(canvas: Canvas, map: ParsedMap, dark: Boolean) {
    map.furniture.filter { it.isVisible }.forEach {
        drawFurnitureSymbol(canvas, furnitureCorners(map, it), it.type, it.subtype, dark)
    }
}

/** Shared monochrome plan-view artwork. Clear details are isolated from the map below. */
@Suppress("UNUSED_PARAMETER") // Retain the shared caller API; artwork is identical in both themes.
internal fun drawFurnitureSymbol(
    canvas: Canvas, corners: List<Offset>, type: Int, subtype: Int, dark: Boolean, strokeWidth: Float = .5f,
) {
    if (corners.size != 4 || corners.any { !it.x.isFinite() || !it.y.isFinite() }) return
    val shortest = corners.indices.minOf { (corners[(it + 1) % 4] - corners[it]).getDistance() }
    if (shortest <= 0f) return
    // Twice the former detail weight, capped proportionally so small map items retain their fill.
    val detailWidth = (strokeWidth * 2f).coerceAtMost(shortest * .02f)
    val fill = Paint().apply { color = Color(0xFFECECEC); isAntiAlias = true }
    val cut = Paint().apply {
        blendMode = androidx.compose.ui.graphics.BlendMode.Clear
        style = PaintingStyle.Stroke; this.strokeWidth = detailWidth; isAntiAlias = true
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        strokeJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    }
    val hole = Paint().apply { blendMode = androidx.compose.ui.graphics.BlendMode.Clear; isAntiAlias = true }
    fun path(points: List<Pair<Float, Float>>, closed: Boolean = true): Path = Path().apply {
        points.forEachIndexed { i, (x, y) ->
            val p = furniturePoint(corners, x, y)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        if (closed) close()
    }
    fun shape(points: List<Pair<Float, Float>>, outline: Boolean = true, erase: Boolean = false) {
        val p = path(points)
        canvas.drawPath(p, if (erase) hole else fill)
        if (outline && !erase) canvas.drawPath(p, cut)
    }
    fun line(vararg points: Pair<Float, Float>) = canvas.drawPath(path(points.toList(), false), cut)
    fun curve(vararg points: Pair<Float, Float>): List<Pair<Float, Float>> =
        (0 until (points.size - 1) / 3).flatMap { segment ->
            val p = points.drop(segment * 3).take(4)
            (0..16).map { step ->
                val t = step / 16f; val u = 1f - t
                fun value(axis: (Pair<Float, Float>) -> Float) =
                    u*u*u*axis(p[0]) + 3*u*u*t*axis(p[1]) + 3*u*t*t*axis(p[2]) + t*t*t*axis(p[3])
                value { it.first } to value { it.second }
            }
        }
    fun rounded(l: Float, t: Float, r: Float, b: Float, radius: Float = .035f): List<Pair<Float, Float>> {
        val rx = radius.coerceAtMost((r - l) / 2)
        val ry = radius.coerceAtMost((b - t) / 2)
        return listOf(Triple(r-rx, t+ry, -90), Triple(r-rx, b-ry, 0),
            Triple(l+rx, b-ry, 90), Triple(l+rx, t+ry, 180)).flatMap { (cx, cy, start) ->
            (0..8).map { i ->
                val a = (start + i * 90.0 / 8) * kotlin.math.PI / 180
                cx + cos(a).toFloat() * rx to cy + sin(a).toFloat() * ry
            }
        }
    }
    fun box(l: Float, t: Float, r: Float, b: Float, radius: Float = .035f, erase: Boolean = false) =
        shape(rounded(l,t,r,b,radius), erase = erase)
    fun oval(l: Float, t: Float, r: Float, b: Float) = shape((0 until 64).map { i ->
        val a = i * 2 * kotlin.math.PI / 64
        (l+r)/2 + cos(a).toFloat()*(r-l)/2 to (t+b)/2 + sin(a).toFloat()*(b-t)/2
    })
    // Clear applies only to this symbol, never to the map or another furniture item.
    val bounds = androidx.compose.ui.geometry.Rect(corners.minOf { it.x } - detailWidth,
        corners.minOf { it.y } - detailWidth, corners.maxOf { it.x } + detailWidth,
        corners.maxOf { it.y } + detailWidth)
    canvas.saveLayer(bounds, Paint())
    try {
        when (val symbol = furnitureSymbol(type, subtype)) {
            FurnitureSymbol.BED, FurnitureSymbol.WIDE_BED -> {
                box(.04f,.02f,.96f,.98f,.055f)
                box(.04f,.02f,.96f,.14f,.055f)
                box(.08f,.14f,.92f,.96f,.04f)
                if (symbol == FurnitureSymbol.WIDE_BED) {
                    box(.12f,.17f,.48f,.37f); box(.52f,.17f,.88f,.37f)
                } else box(.15f,.17f,.85f,.37f)
                box(.08f,.4f,.92f,.55f)
                line(.08f to .55f, .92f to .55f)
            }
            FurnitureSymbol.SOFA, FurnitureSymbol.DOUBLE_SOFA, FurnitureSymbol.ARMCHAIR,
            FurnitureSymbol.CHAISE_LEFT, FurnitureSymbol.CHAISE_RIGHT -> {
                val chaise = symbol == FurnitureSymbol.CHAISE_LEFT || symbol == FurnitureSymbol.CHAISE_RIGHT
                val bottom = if (chaise) .62f else .95f
                val seats = when (symbol) { FurnitureSymbol.ARMCHAIR -> 1; FurnitureSymbol.DOUBLE_SOFA -> 2; else -> 3 }
                for (i in 0 until seats) {
                    val l = .12f + i * .76f / seats
                    val r = .12f + (i+1) * .76f / seats
                    val extended = (symbol == FurnitureSymbol.CHAISE_LEFT && i == 0) ||
                        (symbol == FurnitureSymbol.CHAISE_RIGHT && i == seats-1)
                    box(l,.08f,r,if (extended) .96f else bottom)
                    box(l,.08f,r,.28f)
                }
                box(.02f,.12f,.12f,bottom); box(.88f,.12f,.98f,bottom)
            }
            FurnitureSymbol.TV_STAND -> {
                box(.02f,.1f,.98f,.9f)
                box(.13f,.22f,.87f,.43f,.015f,erase=true)
                box(.4f,.47f,.6f,.66f,.005f,erase=true)
            }
            FurnitureSymbol.DINING_SET -> {
                for (x in listOf(.2f,.58f)) {
                    box(x,.04f,x+.22f,.32f,.055f); box(x,.68f,x+.22f,.96f,.055f)
                    line(*curve(x to .12f, x+.07f to .07f, x+.15f to .07f, x+.22f to .12f).toTypedArray())
                    line(*curve(x to .88f, x+.07f to .93f, x+.15f to .93f, x+.22f to .88f).toTypedArray())
                }
                box(.08f,.25f,.92f,.75f,.02f)
            }
            FurnitureSymbol.SQUARE_TABLE, FurnitureSymbol.COFFEE_TABLE,
            FurnitureSymbol.WARDROBE, FurnitureSymbol.NIGHTSTAND -> {
                box(.02f,.02f,.98f,.98f,.025f); box(.07f,.08f,.93f,.92f,.005f)
            }
            FurnitureSymbol.SHOE_RACK -> {
                box(.02f,.05f,.98f,.95f,.01f)
                for (i in 1..5) { val y=.05f+i*.15f; line(.04f to y,.96f to y) }
                box(.02f,.02f,.07f,.98f,.01f); box(.93f,.02f,.98f,.98f,.01f)
                for (x in listOf(.17f,.3f,.63f,.76f)) oval(x,.19f,x+.12f,.83f)
            }
            FurnitureSymbol.LITTER_BOX -> {
                box(.03f,.04f,.97f,.96f,.09f); box(.08f,.12f,.92f,.88f,.07f)
            }
            FurnitureSymbol.COVERED_LITTER_BOX -> {
                box(.02f,.4f,.07f,.6f,.02f); box(.93f,.4f,.98f,.6f,.02f)
                box(.06f,.04f,.94f,.96f,.17f); box(.1f,.1f,.9f,.9f,.14f)
                line(.5f to .1f,.5f to .9f); box(.45f,.41f,.55f,.59f,.02f)
            }
            FurnitureSymbol.PET_CAGE -> {
                box(.03f,.03f,.97f,.97f,.03f); box(.09f,.09f,.91f,.91f,.005f)
                for (i in 1..4) { val x=.09f+i*.164f; line(x to .09f,x to .91f) }
                for (i in 1..2) { val y=.09f+i*.273f; line(.09f to y,.91f to y) }
                for (x in listOf(.02f,.88f)) for (y in listOf(.02f,.88f)) box(x,y,x+.1f,y+.1f,.02f)
            }
            FurnitureSymbol.PET_BED -> {
                box(.02f,.04f,.98f,.96f,.09f); box(.05f,.08f,.95f,.92f,.065f)
                box(.15f,.24f,.85f,.77f,.005f)
                line(.05f to .08f,.15f to .24f); line(.95f to .08f,.85f to .24f)
                line(.05f to .92f,.15f to .77f); line(.95f to .92f,.85f to .77f)
                box(.15f,.77f,.32f,.96f,.055f); box(.68f,.77f,.85f,.96f,.055f)
                box(.32f,.79f,.68f,.84f,0f,erase=true)
                box(.32f,.89f,.68f,1f,0f,erase=true)
            }
            FurnitureSymbol.PET_BOWLS -> {
                // 19C: one curved, waisted holder with two recessed bowls.
                val holder = curve(
                    .02f to .5f, .02f to .12f, .18f to -.04f, .34f to .09f,
                    .46f to .23f, .54f to .23f, .66f to .09f,
                    .82f to -.04f, .98f to .12f, .98f to .5f,
                    .98f to .88f, .82f to 1.04f, .66f to .91f,
                    .54f to .77f, .46f to .77f, .34f to .91f,
                    .18f to 1.04f, .02f to .88f, .02f to .5f,
                )
                shape(holder)
                oval(.07f,.15f,.45f,.85f); oval(.55f,.15f,.93f,.85f)
                oval(.1f,.2f,.42f,.8f); oval(.58f,.2f,.9f,.8f)
            }
            FurnitureSymbol.MIRROR -> {
                // 20C: original inverted-V support; shallow overhead projection at 8 degrees.
                // Rounded ends on the original two diagonal supports, with no rear crossbar.
                fun support(x0: Float, x1: Float) {
                    val aspect = 2.2f
                    val angle = kotlin.math.atan2(.07f - .96f, (x1 - x0) * aspect)
                    shape(listOf(Triple(x0, .96f, angle + kotlin.math.PI.toFloat()/2),
                        Triple(x1, .07f, angle - kotlin.math.PI.toFloat()/2)).flatMap { (x,y,start) ->
                        (0..16).map { i ->
                            val a = start + i * kotlin.math.PI.toFloat()/16
                            x + cos(a)*.05f/aspect to y + sin(a)*.05f
                        }
                    })
                }
                support(.15f,.467f); support(.85f,.533f)
                shape(listOf(.02f to .68f,.98f to .68f,.94f to .91f,.06f to .91f))
                line(.07f to .91f,.04f to .72f,.96f to .72f,.93f to .91f)
            }
            FurnitureSymbol.CAT_TOWER -> {
                shape(curve(.25f to .12f, .4f to .10f, .8f to .10f, .9f to .1f,
                    .96f to .1f, .99f to .18f, .94f to .26f,
                    .8f to .5f, .65f to .78f, .56f to .94f,
                    .52f to 1f, .47f to 1f, .42f to .93f,
                    .28f to .7f, .13f to .43f, .08f to .3f,
                    .03f to .17f, .13f to .12f, .25f to .12f))
                oval(.39f,.42f,.87f,.91f); oval(.03f,.02f,.58f,.59f); oval(.79f,.13f,.91f,.26f)
            }
            FurnitureSymbol.UNKNOWN -> box(.02f,.02f,.98f,.98f)
        }
    } finally {
        canvas.restore()
    }
}

