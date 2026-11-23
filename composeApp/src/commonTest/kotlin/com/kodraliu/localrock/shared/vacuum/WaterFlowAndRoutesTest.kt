package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaterFlowAndRoutesTest {
    private val curvNfs = "00000000000202058CC57FFDA86836DD5BBFAF7F7EFEFFFF"
    private val curvFeatureSet = "4499197267967999"
    private val proNfs = "42BA8F5C7EDAFFFE"
    private val proFeatureSet = "2247397454282751"

    @Test
    fun levelAndCodeRoundTrip() {
        for (level in 1..30) assertEquals(level, WaterFlow.levelOf(WaterFlow.codeFor(level)))
        assertEquals(221, WaterFlow.codeFor(1))
        assertEquals(235, WaterFlow.codeFor(15))
        assertEquals(250, WaterFlow.codeFor(30))
        assertEquals(221, WaterFlow.codeFor(0))
        assertEquals(250, WaterFlow.codeFor(31))
    }

    @Test
    fun legacyCodesFollowConvertWaterMode() {
        assertEquals(5, WaterFlow.levelOf(WaterBoxMode.LOW))
        assertEquals(15, WaterFlow.levelOf(WaterBoxMode.MEDIUM))
        assertEquals(25, WaterFlow.levelOf(WaterBoxMode.HIGH))
        assertEquals(30, WaterFlow.levelOf(208))
        assertNull(WaterFlow.levelOf(WaterBoxMode.OFF))
        assertNull(WaterFlow.levelOf(WaterBoxMode.SMART))
        assertNull(WaterFlow.levelOf(null))
    }

    @Test
    fun bandsMatchGetPureWaterModeText() {
        val expected = mapOf(
            1 to WaterFlowBand.SLIGHT, 3 to WaterFlowBand.SLIGHT,
            4 to WaterFlowBand.LOW, 7 to WaterFlowBand.LOW,
            8 to WaterFlowBand.GENTLE, 12 to WaterFlowBand.GENTLE,
            13 to WaterFlowBand.MEDIUM, 15 to WaterFlowBand.MEDIUM, 17 to WaterFlowBand.MEDIUM,
            18 to WaterFlowBand.MODERATE, 22 to WaterFlowBand.MODERATE,
            23 to WaterFlowBand.HIGH, 27 to WaterFlowBand.HIGH,
            28 to WaterFlowBand.EXTREME, 30 to WaterFlowBand.EXTREME,
        )
        expected.forEach { (level, band) -> assertEquals(band, WaterFlow.band(level), "level $level") }
    }

    @Test
    fun warningFromSuperBegin() {
        assertFalse(WaterFlow.showsWarning(27))
        assertTrue(WaterFlow.showsWarning(28))
        assertTrue(WaterFlow.showsWarning(30))
    }

    @Test
    fun robotNewFeatureBitReadsTheHighWord() {
        assertEquals(true, robotNewFeatureBit(curvFeatureSet, 41))
        assertEquals(true, robotNewFeatureBit(proFeatureSet, 41))
        assertEquals(false, robotNewFeatureBit((1uL shl 40).toString(), 41))
        assertNull(robotNewFeatureBit("1", 41))
        assertNull(robotNewFeatureBit(null, 41))
        assertNull(robotNewFeatureBit("junk", 41))
    }

    @Test
    fun curvOffersFastStandardDeepOnEveryTab() {
        val gates = routeGates("roborock.vacuum.a245", curvFeatureSet, curvNfs)
        val expected = listOf(
            RouteOption(MopRoute.FAST, RouteName.FAST),
            RouteOption(MopRoute.STANDARD, RouteName.STANDARD),
            RouteOption(MopRoute.DEEP_PLUS, RouteName.DEEP),
        )
        assertEquals(expected, routeOptions(gates, mopOnly = false))
        assertEquals(expected, routeOptions(gates, mopOnly = true))
    }

    @Test
    fun proOffersDeepRoutesOnlyWhenMopping() {
        val gates = routeGates("roborock.vacuum.a101", proFeatureSet, proNfs)
        assertEquals(
            listOf(RouteOption(MopRoute.FAST, RouteName.FAST), RouteOption(MopRoute.STANDARD, RouteName.STANDARD)),
            routeOptions(gates, mopOnly = false),
        )
        assertEquals(
            listOf(MopRoute.FAST, MopRoute.STANDARD, MopRoute.DEEP, MopRoute.DEEP_PLUS),
            routeOptions(gates, mopOnly = true).map { it.code },
        )
        assertEquals(RouteName.DEEP_PLUS, routeOptions(gates, mopOnly = true).last().name)
    }

    @Test
    fun routeGatesFallBacks() {
        val noFast = RouteGates(cleanEfficiency = false, carefulSlow = false, fast = false, twoRoutesOnly = false)
        assertEquals(listOf(MopRoute.STANDARD, MopRoute.DEEP), routeOptions(noFast, mopOnly = true).map { it.code })
        assertTrue(routeGates("roborock.vacuum.a84", null, null).twoRoutesOnly)
        assertTrue(routeGates(null, null, null).carefulSlow)
    }
}
