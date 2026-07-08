import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // Inject the Swift-backed platform pieces into the shared Kotlin code:
        //  - CocoaMQTT transport for MQTT
        //  - WebRTC.framework peer factory for the camera live view (pass nil to disable it)
        MainViewControllerKt.MainViewController(
            mqttTransport: CocoaMqttTransport(),
            rtcPeerFactory: WebRtcNativeFactory()
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}



