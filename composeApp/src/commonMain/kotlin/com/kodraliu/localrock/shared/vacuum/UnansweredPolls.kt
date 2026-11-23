package com.kodraliu.localrock.shared.vacuum

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Counts status polls in a row that got no answer; any reply or push from the robot starts the count over. */
class UnansweredPolls(private val threshold: Int) {
    private var missed = 0
    private val _unresponsive = MutableStateFlow(false)
    val unresponsive: StateFlow<Boolean> = _unresponsive

    fun heardFromRobot() {
        missed = 0
        _unresponsive.value = false
    }

    fun pollFailed() {
        if (++missed >= threshold) _unresponsive.value = true
    }
}
