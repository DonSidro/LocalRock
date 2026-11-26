package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.CLEAN_TYPE_PET_PATROL
import com.kodraliu.localrock.shared.vacuum.CLEAN_TYPE_VIDEO_PATROL
import com.kodraliu.localrock.shared.vacuum.CleanRecord
import com.kodraliu.localrock.shared.vacuum.Supply
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecondaryLabelsTest {
    @Test
    fun everySupplyHasItsOwnOfficialName() {
        val names = Supply.entries.map { it.label() }
        assertEquals(names.size, names.toSet().size)
        assertEquals("On-Board Dirty Water Tank", Supply.DIRTY_WATER_TANK.label())
        assertEquals("Water Tank Filter", Supply.WATER_TANK_FILTER.label())
    }

    @Test
    fun everyResettableSupplyHasAConfirmation() {
        Supply.entries.filter { it.field != null }.forEach { assertTrue(it.resetConfirm().isNotBlank(), it.name) }
        assertEquals("Confirm if the main brush has been replaced.", Supply.MAIN_BRUSH.resetConfirm())
        assertEquals("Confirm if the on-board dirty water tank has been cleaned.", Supply.DIRTY_WATER_TANK.resetConfirm())
    }

    @Test
    fun cruiseDetailTitleFollowsTheRunType() {
        val run = CleanRecord(1L, 2L, 3L, 0L, 0, 1, cleanType = CLEAN_TYPE_VIDEO_PATROL)
        assertEquals("Cruise details", cruiseDetailTitle(run))
        assertEquals("Pet finding details", cruiseDetailTitle(run.copy(cleanType = CLEAN_TYPE_PET_PATROL)))
    }
}
