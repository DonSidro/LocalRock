package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import com.kodraliu.localrock.shared.vacuum.map.MapFurniture
import com.kodraliu.localrock.shared.vacuum.map.FURNITURE_CATALOG
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MapFurnitureGeometryTest {
    @Test fun every_catalog_choice_has_a_distinct_named_symbol() {
        val symbols = FURNITURE_CATALOG.map { furnitureSymbol(it.type, it.subtype) }
        symbols.forEach { assertNotEquals(FurnitureSymbol.UNKNOWN, it) }
        assertEquals(21, symbols.distinct().size)
    }

    @Test fun verified_types_get_recognizable_symbols_and_unknown_pairs_keep_footprints() {
        assertEquals(FurnitureSymbol.SOFA, furnitureSymbol(46, 3))
        assertEquals(FurnitureSymbol.ARMCHAIR, furnitureSymbol(46, 1))
        assertEquals(FurnitureSymbol.DINING_SET, furnitureSymbol(47, 0))
        assertEquals(FurnitureSymbol.BED, furnitureSymbol(45, 1))
        assertEquals(FurnitureSymbol.UNKNOWN, furnitureSymbol(45, 9))
        assertEquals(FurnitureSymbol.UNKNOWN, furnitureSymbol(247, 0))
    }

    @Test fun symbol_details_follow_a_rotated_quad_without_changing_its_size() {
        val corners = listOf(Offset(2f, 4f), Offset(4f, 6f), Offset(2f, 8f), Offset(0f, 6f))
        assertEquals(corners[0], furniturePoint(corners, 0f, 0f))
        assertEquals(corners[1], furniturePoint(corners, 1f, 0f))
        assertEquals(corners[2], furniturePoint(corners, 1f, 1f))
        assertEquals(corners[3], furniturePoint(corners, 0f, 1f))
        assertEquals(Offset(2f, 6f), furniturePoint(corners, .5f, .5f))
    }

    @Test
    fun rotated_furniture_uses_the_map_origin_and_y_flip() {
        val coords = listOf(150, 350, 250, 250, 150, 150, 50, 250)
        val bytes = coords.flatMap { listOf(it and 255, it ushr 8) } + listOf(0, 0, 46, 1, 1, 5, 1)
        val map = ParsedMap(20, 10, ByteArray(200), emptyList(), 0.05f, pixelOffsetLeft = 1, pixelOffsetTop = 2)
        assertEquals(
            listOf(Offset(2f, 4f), Offset(4f, 6f), Offset(2f, 8f), Offset(0f, 6f)),
            furnitureCorners(map, MapFurniture(bytes)),
        )
    }
}
