package com.kodraliu.localrock.ui

import androidx.compose.runtime.Composable

/** Lets a voice call ask for the microphone while this is in the composition; a no-op where the OS handles it. */
@Composable
expect fun MicrophonePermissionHost()
