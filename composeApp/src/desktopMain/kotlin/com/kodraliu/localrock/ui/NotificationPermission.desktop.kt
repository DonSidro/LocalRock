package com.kodraliu.localrock.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/** Desktop apps need no permission to alert; in-app alerts are always allowed. */
@Composable
actual fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    return { currentOnResult(true) }
}
