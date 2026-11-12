package com.kodraliu.localrock.shared.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QrCodeTest {
    @Test
    fun aMatterPayloadFitsVersionTwoWithThreeFinderPatterns() {
        val qr = QrEncoder.encode("MT:Y.K9042C00KA0648G00")
        assertEquals(25, qr.size)
        for ((ox, oy) in listOf(0 to 0, 18 to 0, 0 to 18)) {
            assertTrue(qr[ox, oy] && qr[ox + 6, oy + 6] && !qr[ox + 1, oy + 1] && qr[ox + 3, oy + 3])
        }
    }

    @Test
    fun longerTextGetsAHigherVersion() {
        assertTrue(QrEncoder.encode("x".repeat(140)).size > 41)
    }
}
