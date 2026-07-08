package com.kodraliu.localrock.shared.webrtc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


@Composable
expect fun RtcVideoView(peer: RtcPeer, modifier: Modifier)
