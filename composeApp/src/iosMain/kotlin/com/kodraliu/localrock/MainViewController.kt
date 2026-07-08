package com.kodraliu.localrock

import androidx.compose.ui.window.ComposeUIViewController
import com.russhwolf.settings.NSUserDefaultsSettings
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.shared.mqtt.MqttNativeTransport
import com.kodraliu.localrock.shared.webrtc.IosRtc
import com.kodraliu.localrock.shared.webrtc.RtcNativePeerFactory
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

/**
 * iOS entry point. The Swift app supplies platform pieces that have no in-Kotlin implementation:
 *  - [mqttTransport]: a CocoaMQTT-backed transport (see `MqttNativeTransport.ios.kt`).
 *  - [rtcPeerFactory]: a WebRTC.framework-backed peer factory (see `WebRtcPeer.swift`). Pass `null`
 *    to disable the camera live view; [com.kodraliu.localrock.shared.webrtc.liveViewSupported]
 *    reflects whether it was provided.
 */
fun MainViewController(
    mqttTransport: MqttNativeTransport,
    rtcPeerFactory: RtcNativePeerFactory?,
): UIViewController {
    IosRtc.factory = rtcPeerFactory
    val defaults = NSUserDefaults(suiteName = "vaclocal") ?: NSUserDefaults.standardUserDefaults
    val container = AppContainer(NSUserDefaultsSettings(defaults), mqttTransport)
    return ComposeUIViewController { App(container) }
}
