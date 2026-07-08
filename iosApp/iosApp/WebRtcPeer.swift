import Foundation
import UIKit
import WebRTC
import ComposeApp

// iOS WebRTC bridge for LocalRock's camera live view. Implements the Kotlin `RtcNativePeer*`
// contracts (see `RtcPeer.ios.kt`) on top of Apple's WebRTC.framework. The shared Kotlin code
// (`CameraLiveSession`) drives signaling over MQTT; this class is only the peer-connection engine
// plus the render view.
//
// SETUP (once, in Xcode — this file will not compile until then):
//   1. Add the WebRTC package to the iosApp target. Easiest is Swift Package Manager:
//        https://github.com/stasel/WebRTC  (products: "WebRTC")
//      or CocoaPods `pod 'GoogleWebRTC'`.
//   2. Add this file to the iosApp target (new files are not auto-added to the .pbxproj).
//   3. Info.plist: NSCameraUsageDescription / NSMicrophoneUsageDescription are REQUIRED even for
//      pure receive-only viewing. App Store static analysis (ITMS-90683) flags the linked
//      WebRTC.framework's references to the capture APIs regardless of whether we call them.
//      Both keys are already present in iosApp/Info.plist.
//
// Delegate signatures below target WebRTC ~M1xx (stasel/GoogleWebRTC). If the compiler flags a
// mismatch, adjust the selector to match your installed version — same fixup dance as CocoaMQTT.

final class WebRtcNativeFactory: NSObject, RtcNativePeerFactory {

    private let factory: RTCPeerConnectionFactory

    override init() {
        RTCInitializeSSL()
        let encoder = RTCDefaultVideoEncoderFactory()
        let decoder = RTCDefaultVideoDecoderFactory()
        self.factory = RTCPeerConnectionFactory(encoderFactory: encoder, decoderFactory: decoder)
        super.init()
    }

    func create(iceServers: [RtcNativeIceServer], listener: RtcNativePeerListener) -> RtcNativePeer {
        return WebRtcPeer(factory: factory, iceServers: iceServers, listener: listener)
    }
}

final class WebRtcPeer: NSObject, RtcNativePeer, RTCPeerConnectionDelegate {

    private let listener: RtcNativePeerListener
    private let peerConnection: RTCPeerConnection
    private let videoView: RTCMTLVideoView
    private var remoteVideoTrack: RTCVideoTrack?

    init(factory: RTCPeerConnectionFactory, iceServers: [RtcNativeIceServer], listener: RtcNativePeerListener) {
        self.listener = listener

        let config = RTCConfiguration()
        config.sdpSemantics = .unifiedPlan
        config.iceServers = iceServers.map { server in
            RTCIceServer(
                urlStrings: [server.url],
                username: server.username.isEmpty ? nil : server.username,
                credential: server.password.isEmpty ? nil : server.password
            )
        }

        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        guard let pc = factory.peerConnection(with: config, constraints: constraints, delegate: nil) else {
            fatalError("RTCPeerConnectionFactory.peerConnection returned nil")
        }
        self.peerConnection = pc

        // RTCMTLVideoView is a UIView and must be created/configured on the main thread.
        self.videoView = WebRtcPeer.onMain { RTCMTLVideoView(frame: .zero) }
        super.init()
        self.peerConnection.delegate = self
        WebRtcPeer.onMainVoid { self.videoView.videoContentMode = .scaleAspectFit }
    }

    // MARK: RtcNativePeer

    func createOffer(onSuccess: @escaping (String) -> Void, onError: @escaping (String) -> Void) {
        let recvVideo = RTCRtpTransceiverInit()
        recvVideo.direction = .recvOnly
        peerConnection.addTransceiver(of: .video, init: recvVideo)

        let recvAudio = RTCRtpTransceiverInit()
        recvAudio.direction = .recvOnly
        peerConnection.addTransceiver(of: .audio, init: recvAudio)

        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        peerConnection.offer(for: constraints) { [weak self] sdp, error in
            guard let self = self else { return }
            if let error = error { onError(error.localizedDescription); return }
            guard let sdp = sdp else { onError("createOffer returned a nil description"); return }
            self.peerConnection.setLocalDescription(sdp) { error in
                if let error = error { onError(error.localizedDescription); return }
                onSuccess(sdp.sdp)
            }
        }
    }

    func setRemoteAnswer(sdp: String) {
        let description = RTCSessionDescription(type: .answer, sdp: sdp)
        peerConnection.setRemoteDescription(description) { error in
            if let error = error {
                print("[LocalRock] setRemoteDescription failed: \(error.localizedDescription)")
            }
        }
    }

    func addRemoteCandidate(sdpMid: String?, sdpMLineIndex: Int32, candidate: String) {
        let ice = RTCIceCandidate(sdp: candidate, sdpMLineIndex: sdpMLineIndex, sdpMid: sdpMid)
        peerConnection.add(ice) { error in
            if let error = error {
                print("[LocalRock] addIceCandidate failed: \(error.localizedDescription)")
            }
        }
    }

    func close() {
        remoteVideoTrack?.remove(videoView)
        remoteVideoTrack = nil
        peerConnection.close()
    }

    func renderView() -> UIView? {
        return videoView
    }

    // MARK: RTCPeerConnectionDelegate

    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        listener.onLocalCandidate(
            sdpMid: candidate.sdpMid,
            sdpMLineIndex: candidate.sdpMLineIndex,
            candidate: candidate.sdp
        )
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        listener.onConnectionState(state: WebRtcPeer.stateString(newState))
    }

    // Unified-plan track arrival: attach the remote video track to the render view.
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd rtpReceiver: RTCRtpReceiver, streams: [RTCMediaStream]) {
        if let track = rtpReceiver.track as? RTCVideoTrack {
            self.remoteVideoTrack = track
            WebRtcPeer.onMainVoid { track.add(self.videoView) }
        }
    }

    // Required-but-unused delegate methods.
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}
    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}

    // MARK: Helpers

    private static func stateString(_ state: RTCIceConnectionState) -> String {
        switch state {
        case .new: return "new"
        case .checking: return "connecting"
        case .connected, .completed: return "connected"
        case .disconnected: return "disconnected"
        case .failed: return "failed"
        case .closed: return "closed"
        default: return "new"
        }
    }

    private static func onMain<T>(_ block: () -> T) -> T {
        if Thread.isMainThread { return block() }
        return DispatchQueue.main.sync { block() }
    }

    private static func onMainVoid(_ block: @escaping () -> Void) {
        if Thread.isMainThread { block() } else { DispatchQueue.main.async { block() } }
    }
}
