package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.VirtualWall
import kotlin.math.abs

/*
 * Read-back checks run after every map edit: the change only counts as applied when the next map
 * from the robot shows it. Comparisons ignore entry order and corner order and allow one map pixel
 * (50 mm) of drift, because it is not known whether the robot stores the exact values it was sent.
 */

internal const val READBACK_TOLERANCE_MM = 50

internal fun restrictionsMatch(
    map: ParsedMap,
    noGoZones: List<MapZone>,
    noMopZones: List<MapZone>,
    walls: List<VirtualWall>,
): Boolean =
    sameZones(noGoZones, map.noGoZones) &&
        sameZones(noMopZones, map.noMopZones) &&
        sameWalls(walls, map.virtualWalls)

private fun zoneCorners(z: MapZone): List<Pair<Int, Int>> =
    listOf(z.x0 to z.y0, z.x1 to z.y1, z.x2 to z.y2, z.x3 to z.y3).sortedWith(compareBy({ it.first }, { it.second }))

private fun near(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean =
    abs(a.first - b.first) <= READBACK_TOLERANCE_MM && abs(a.second - b.second) <= READBACK_TOLERANCE_MM

private fun zoneMatches(a: MapZone, b: MapZone): Boolean =
    zoneCorners(a).zip(zoneCorners(b)).all { (p, q) -> near(p, q) }

private fun wallMatches(a: VirtualWall, b: VirtualWall): Boolean {
    val a0 = a.x0 to a.y0; val a1 = a.x1 to a.y1
    val b0 = b.x0 to b.y0; val b1 = b.x1 to b.y1
    return (near(a0, b0) && near(a1, b1)) || (near(a0, b1) && near(a1, b0))
}

internal fun sameZones(expected: List<MapZone>, actual: List<MapZone>): Boolean =
    matchAll(expected, actual, ::zoneMatches)

internal fun sameWalls(expected: List<VirtualWall>, actual: List<VirtualWall>): Boolean =
    matchAll(expected, actual, ::wallMatches)

/** True when every expected item pairs with a distinct actual item and nothing is left over. */
private fun <T> matchAll(expected: List<T>, actual: List<T>, matches: (T, T) -> Boolean): Boolean {
    if (expected.size != actual.size) return false
    val remaining = actual.toMutableList()
    for (e in expected) {
        val i = remaining.indexOfFirst { matches(e, it) }
        if (i < 0) return false
        remaining.removeAt(i)
    }
    return true
}
