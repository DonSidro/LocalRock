package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatterAndPetGatesTest {
    // Hex flag strings with only one bit set: bit 67 is a leading 8 over 16 nibbles, bit 48 a 1 over 12, bit 50 a 4 over 12.
    private val matterOnly = "8" + "0".repeat(16)
    private val videoPatrolOnly = "1" + "0".repeat(12)
    private val petSnapshotOnly = "4" + "0".repeat(12)

    @Test
    fun matterFollowsTheRobotsOwnFlags() {
        assertTrue(isMatterSupported(matterOnly))
        assertFalse(isMatterSupported(videoPatrolOnly))
        assertFalse(isMatterSupported(null))
    }

    @Test
    fun findMyPetNeedsEitherPatrolBit() {
        assertTrue(isPetSearchSupported(videoPatrolOnly))
        assertTrue(isPetSearchSupported(petSnapshotOnly))
        assertFalse(isPetSearchSupported(matterOnly))
        assertFalse(isPetSearchSupported(null))
    }

    @Test
    fun aNewCodeIsOnlyOfferedBeforeTheRobotHasJoined() {
        assertTrue(MatterState.Ready.canRenewCode)
        assertFalse(MatterState.Joined.canRenewCode)
        assertFalse(MatterState.Preparing.canRenewCode)
    }
}
