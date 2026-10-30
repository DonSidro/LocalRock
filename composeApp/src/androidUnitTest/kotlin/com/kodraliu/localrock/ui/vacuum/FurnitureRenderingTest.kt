package com.kodraliu.localrock.ui.vacuum

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.kodraliu.localrock.shared.vacuum.map.FURNITURE_CATALOG
import com.kodraliu.localrock.ui.VacDarkColorScheme
import com.kodraliu.localrock.ui.VacLightColorScheme
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FurnitureRenderingTest {
    private val gray = AndroidColor.rgb(236, 236, 236)
    private val blue = AndroidColor.rgb(123, 170, 223)
    private val corners = listOf(Offset(10f, 10f), Offset(210f, 10f), Offset(210f, 210f), Offset(10f, 210f))

    private fun render(type: Int, subtype: Int, background: Int = AndroidColor.TRANSPARENT,
        dark: Boolean = false, points: List<Offset> = corners): Bitmap {
        val bitmap = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(background)
        drawFurnitureSymbol(Canvas(bitmap.asImageBitmap()), points, type, subtype, dark, 1.5f)
        return bitmap
    }

    private fun Bitmap.at(x: Float, y: Float) = getPixel((10 + 200 * x).toInt(), (10 + 200 * y).toInt())

    @Test fun allSymbolsUseOnlyGrayAndRealTransparencyWithoutClearingTheMap() {
        for (kind in FURNITURE_CATALOG) {
            val clear = render(kind.type, kind.subtype)
            val colored = render(kind.type, kind.subtype, blue)
            val dark = render(kind.type, kind.subtype, dark = true)
            var opaque = 0
            var transparent = 0
            for (y in 0 until 220) for (x in 0 until 220) {
                val pixel = clear.getPixel(x, y)
                assertEquals(pixel, dark.getPixel(x, y), kind.label)
                assertEquals(255, AndroidColor.alpha(colored.getPixel(x, y)), "Map erased by ${kind.label}")
                when (AndroidColor.alpha(pixel)) {
                    255 -> { assertEquals(gray, pixel, kind.label); opaque++ }
                    0 -> { assertEquals(blue, colored.getPixel(x, y), kind.label); transparent++ }
                }
            }
            assertTrue(opaque > 400, kind.label)
            assertTrue(transparent > 400, kind.label)
        }
    }

    @Test fun approvedDetailsStayOpenOrClosedAsDesigned() {
        val tv = render(43, 0, blue)
        assertEquals(blue, tv.at(.5f, .3f))
        assertEquals(blue, tv.at(.5f, .55f))
        assertEquals(gray, tv.at(.5f, .8f))
        val bowls = render(56, 0, blue)
        assertEquals(gray, bowls.at(.5f, .5f), "Both bowls share one holder")
        assertEquals(blue, bowls.at(.5f, .12f), "Holder has a curved waist")
        assertEquals(gray, render(52, 0, blue).at(.5f, .94f), "Litter tray has a closed front")
        assertEquals(blue, render(55, 0, blue).at(.5f, .94f), "Pet bed has an open front")
        val mirror = render(57, 0, blue)
        assertEquals(blue, mirror.at(.5f, .4f), "Inverted V has no rear crossbar")
        assertEquals(gray, mirror.at(.5f, .8f))
    }

    @Test fun rotatedOverlappingSymbolsPreserveUnderlyingPixels() {
        val bitmap = render(48, 2, blue)
        val canvas = Canvas(bitmap.asImageBitmap())
        val rotated = listOf(Offset(70f, 20f), Offset(200f, 70f), Offset(150f, 200f), Offset(20f, 150f))
        drawFurnitureSymbol(canvas, rotated, 43, 0, false, 1.5f)
        val screenCenter = furniturePoint(rotated, .5f, .3f)
        assertEquals(gray, bitmap.getPixel(screenCenter.x.toInt(), screenCenter.y.toInt()))
        assertEquals(blue, bitmap.getPixel(0, 0))
    }

    @Test fun renderAllCatalogSymbolsForVisualReview() {
        val output = File("build/furniture-preview").apply { mkdirs() }
        val ratios = listOf(1f, .5f, 4.5f, 2.7f, 2.1f, 1.4f, 1.7f, 1.7f, 1.3f,
            1.1f, 2.2f, 2.4f, 2.5f, 1f, 2f, 1.7f, 1.7f, 1.8f, 2.1f, 2.2f, 1.2f)
        for ((name, background) in listOf("black" to AndroidColor.BLACK, "colored" to blue)) {
            for (row in 0 until 7) {
                val bitmap = Bitmap.createBitmap(720, 210, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(background)
                val canvas = Canvas(bitmap.asImageBitmap())
                for (column in 0 until 3) {
                    val index = row * 3 + column
                    val kind = FURNITURE_CATALOG[index]
                    val ratio = ratios[index]
                    val w = minOf(210f, 162f * ratio)
                    val h = w / ratio
                    val x = column * 240 + (240 - w) / 2
                    val y = 12 + (162 - h) / 2
                    drawFurnitureSymbol(canvas, listOf(Offset(x,y),Offset(x+w,y),Offset(x+w,y+h),Offset(x,y+h)),
                        kind.type, kind.subtype, false, 1.5f)
                    android.graphics.Canvas(bitmap).drawText("${index+1}. ${kind.label}", column * 240 + 12f, 198f,
                        android.graphics.Paint().apply { color = AndroidColor.WHITE; textSize = 15f; isAntiAlias = true })
                }
                File(output, "$name-$row.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
        }
        renderCatalogPreviews(output)
    }

    // Review the same 64dp catalog and 48dp Items artwork on the actual theme surfaces.
    private fun renderCatalogPreviews(output: File) {
        for (dark in listOf(false, true)) for (previewSize in listOf(64, 48)) {
            val scheme = if (dark) VacDarkColorScheme else VacLightColorScheme
            val bitmap = Bitmap.createBitmap(336, 840, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(scheme.surface.toArgb())
            val canvas = Canvas(bitmap.asImageBitmap())
            val native = android.graphics.Canvas(bitmap)
            val card = android.graphics.Paint().apply {
                color = scheme.surfaceContainer.toArgb(); isAntiAlias = true
            }
            val label = android.graphics.Paint().apply {
                color = scheme.onSurface.toArgb(); textSize = 10f; isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            for ((index, kind) in FURNITURE_CATALOG.withIndex()) {
                val left = (index % 3) * 112f
                val top = (index / 3) * 120f
                native.drawRoundRect(left + 4, top + 4, left + 108, top + 116, 16f, 16f, card)
                val artwork = assertNotNull(furniturePreviewArtwork(kind.type, kind.subtype), kind.label)
                val source = File("src/commonMain/composeResources/drawable", artwork.fileName)
                val image = assertNotNull(BitmapFactory.decodeFile(source.path), source.path).asImageBitmap()
                val x = left + (112 - previewSize) / 2f
                val y = top + 8 + (64 - previewSize) / 2f
                drawFurniturePreviewImage(canvas, image, Rect(x, y, x + previewSize, y + previewSize), 3f)
                val words = kind.label.split(" ")
                val lines = if (label.measureText(kind.label) <= 96f) listOf(kind.label)
                    else listOf(words.dropLast(1).joinToString(" "), words.last())
                for ((line, text) in lines.withIndex()) native.drawText(text, left + 56, top + 88 + line * 12, label)
            }
            val theme = if (dark) "dark" else "light"
            File(output, "catalog-$theme-$previewSize.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}

