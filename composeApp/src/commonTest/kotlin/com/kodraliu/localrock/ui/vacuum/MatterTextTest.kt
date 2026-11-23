package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.MatterState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatterTextTest {
    @Test
    fun countdownIsMinutesAndSeconds() {
        assertEquals("15:00", countdownText(900))
        assertEquals("0:05", countdownText(5))
        assertEquals("0:00", countdownText(-3))
    }

    @Test
    fun failureTextNamesWhatTheRobotReported() {
        assertTrue("could not use it" in matterFailureText(MatterState.KeyCorrupt))
        assertTrue("could not download" in matterFailureText(MatterState.KeyDownloadFailure))
        assertFalse("Wi-Fi is stronger" in matterFailureText(MatterState.KeyDownloadFailure))
    }
}
