package com.kodraliu.localrock.ui.vacuum

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive

/** Runs [block] once when a page is opened; not again on rotation or on coming back from a page above it. */
@Composable
internal fun LaunchedOnEntry(block: suspend CoroutineScope.() -> Unit) {
    var done by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (done) return@LaunchedEffect
        block()
        // A read cut short by leaving the page runs again on the next visit.
        if (isActive) done = true
    }
}
