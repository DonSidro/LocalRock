package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatterPollTest {
    @Test
    fun aJoinedRobotIsNeverAskedForAKeyEvenWhenRenewing() {
        assertFalse(matterNeedsKey(MatterState.Joined, attempt = 1, renewing = true, asked = false))
        assertFalse(matterKeyRequestAllowed(MatterState.Joined))
        assertTrue(matterKeyRequestAllowed(MatterState.Ready))
        assertTrue(matterKeyRequestAllowed(null))
    }

    @Test
    fun aReadyRobotIsAskedOnceWhenRenewing() {
        assertTrue(matterNeedsKey(MatterState.Ready, attempt = 1, renewing = true, asked = false))
        assertFalse(matterNeedsKey(MatterState.Ready, attempt = 1, renewing = true, asked = true))
    }

    @Test
    fun aMissingKeyIsAskedForAndAFailureOnlyOnARetry() {
        assertTrue(matterNeedsKey(MatterState.KeyNotExit, 0, renewing = false, asked = false))
        assertFalse(matterNeedsKey(MatterState.KeyDownloadFailure, 0, renewing = false, asked = false))
        assertTrue(matterNeedsKey(MatterState.KeyDownloadFailure, 1, renewing = false, asked = false))
        assertFalse(matterNeedsKey(MatterState.Preparing, 0, renewing = false, asked = false))
    }

    @Test
    fun aRenewingPageStopsAtJoinedWithoutWaiting() {
        assertTrue(matterPollIsDone(MatterState.Joined, renewing = true, asked = false, unchangedCode = true, pollsSinceAsk = 0))
    }

    @Test
    fun aRenewingPageWaitsForANewCodeThenAcceptsTheOldOneAfterFourPolls() {
        assertFalse(matterPollIsDone(MatterState.Ready, renewing = true, asked = false, unchangedCode = false, pollsSinceAsk = 0))
        assertFalse(matterPollIsDone(MatterState.Ready, renewing = true, asked = true, unchangedCode = true, pollsSinceAsk = 3))
        assertTrue(matterPollIsDone(MatterState.Ready, renewing = true, asked = true, unchangedCode = false, pollsSinceAsk = 1))
        assertTrue(matterPollIsDone(MatterState.Ready, renewing = true, asked = true, unchangedCode = true, pollsSinceAsk = 4))
    }

    @Test
    fun aFirstLookAtAReadyCodeStopsAtOnce() {
        assertTrue(matterPollIsDone(MatterState.Ready, renewing = false, asked = false, unchangedCode = false, pollsSinceAsk = 0))
        assertFalse(matterPollIsDone(MatterState.Preparing, renewing = false, asked = false, unchangedCode = false, pollsSinceAsk = 0))
    }

    @Test
    fun theCountdownNeverRestartsOnACodeThatDidNotChange() {
        assertFalse(matterCodeAgeKnown(MatterState.Ready, sawWorking = false, renewing = true, asked = true, unchangedCode = true))
        assertTrue(matterCodeAgeKnown(MatterState.Ready, sawWorking = false, renewing = true, asked = true, unchangedCode = false))
    }

    @Test
    fun theCountdownNeedsAWatchedAppearanceAndNeverRunsWhileJoined() {
        assertFalse(matterCodeAgeKnown(MatterState.Ready, sawWorking = false, renewing = false, asked = false, unchangedCode = false))
        assertTrue(matterCodeAgeKnown(MatterState.Ready, sawWorking = true, renewing = false, asked = false, unchangedCode = false))
        assertFalse(matterCodeAgeKnown(MatterState.Joined, sawWorking = true, renewing = false, asked = true, unchangedCode = false))
    }
}
