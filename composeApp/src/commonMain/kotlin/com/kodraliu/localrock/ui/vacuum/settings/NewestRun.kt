package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.ui.vacuum.LaunchedOnEntry
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel

import com.kodraliu.localrock.shared.vacuum.CleanRecord
import com.kodraliu.localrock.shared.vacuum.VacuumRepository
import com.kodraliu.localrock.shared.vacuum.getCleanRecord
import com.kodraliu.localrock.shared.vacuum.parseCleanRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Each robot's newest run for the Settings header card, so the robot preload can fill it before Settings opens. */
internal object NewestRunCache {
    private val runs = mutableMapOf<VacuumRepository, MutableStateFlow<CleanRecord?>>()

    private fun state(repository: VacuumRepository) = runs.getOrPut(repository) { MutableStateFlow(null) }

    fun flow(repository: VacuumRepository): StateFlow<CleanRecord?> = state(repository)

    /** Reads only the newest run; the History page reads the rest. */
    suspend fun load(repository: VacuumRepository) {
        val id = repository.fetchCleanSummary().firstOrNull() ?: return
        repository.session.getCleanRecord(id).result?.let { parseCleanRecord(it, id) }?.let { state(repository).value = it }
    }
}
