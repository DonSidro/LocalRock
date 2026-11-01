import Foundation
import AVFoundation
import UIKit
import WebRTC
import ComposeApp

// iOS WebRTC bridge for LocalRock's camera live view. Implements the Kotlin `RtcNativePeer*`
// contracts (see `RtcPeer.ios.kt`) on top of Apple's WebRTC.framework. The shared Kotlin code
// (`CameraLiveSession`) drives signaling over MQTT; this class is only the peer-connection engine
// plus the render view.
//
// The target's synchronized source folder includes this file. WebRTC is linked via SPM;
// ContentView injects WebRtcNativeFactory. A microphone track is attached only for an explicit call.

final class WebRtcNativeFactory: NSObject, RtcNativePeerFactory {

    private let factory: RTCPeerConnectionFactory

    override init() {
        // Native diagnostics may contain SDP/ICE; the app exposes allowlisted stats instead.
        RTCSetMinDebugLogLevel(.none)
        RTCInitializeSSL()
        // Opening video alone must not initialize the VoIP recording/playback unit.
        let audioSession = RTCAudioSession.sharedInstance()
        audioSession.useManualAudio = true
        audioSession.isAudioEnabled = false
        let encoder = RTCDefaultVideoEncoderFactory()
        let decoder = RTCDefaultVideoDecoderFactory()
        self.factory = RTCPeerConnectionFactory(encoderFactory: encoder, decoderFactory: decoder)
        super.init()
    }

    func create(iceServers: [RtcNativeIceServer], listener: RtcNativePeerListener) -> RtcNativePeer? {
        return WebRtcPeer(factory: factory, iceServers: iceServers, listener: listener)
    }
}

final class WebRtcPeer: NSObject, RtcNativePeer, RTCPeerConnectionDelegate {

    private let listener: RtcNativePeerListener
    private let factory: RTCPeerConnectionFactory
    private let peerConnection: RTCPeerConnection
    private let videoView: RTCMTLVideoView
    private var remoteVideoTrack: RTCVideoTrack?
    private var remoteAudioTrack: RTCAudioTrack?
    private var microphoneTrack: RTCAudioTrack?
    private var microphoneSource: RTCAudioSource?
    private var microphoneSender: RTCRtpSender?
    private var voiceActive = false
    private var remoteAudioMuted = true
    private var activatedAudioSession = false
    private var previousAudioCategory: (AVAudioSession.Category, AVAudioSession.Mode, AVAudioSession.CategoryOptions)?
    private static weak var audioOwner: WebRtcPeer?
    // Access renderer state only on the main queue.
    private var rendererClosed = false

    init?(factory: RTCPeerConnectionFactory, iceServers: [RtcNativeIceServer], listener: RtcNativePeerListener) {
        self.listener = listener
        self.factory = factory

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
            return nil
        }
        self.peerConnection = pc

        // RTCMTLVideoView is a UIView and must be created/configured on the main thread.
        self.videoView = WebRtcPeer.onMain { RTCMTLVideoView(frame: .zero) }
        super.init()
        self.peerConnection.delegate = self
        WebRtcPeer.onMainVoid {
            self.videoView.videoContentMode = .scaleAspectFit
            self.videoView.backgroundColor = .black
            self.videoView.clipsToBounds = true
            self.videoView.isUserInteractionEnabled = false
        }
    }

    // MARK: RtcNativePeer

    func createOffer(compactCodecs: Bool,
                     onSuccess: @escaping (String) -> Void, onError: @escaping (String) -> Void) {
        do {
            try WebRtcOffer.configure(peerConnection, factory: factory, compactCodecs: compactCodecs)
        } catch {
            onError("Video offer configuration failed")
            return
        }

        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        peerConnection.offer(for: constraints) { [weak self] sdp, error in
            guard let self = self else { return }
            if error != nil { onError("Video offer creation failed"); return }
            guard let sdp = sdp else { onError("createOffer returned a nil description"); return }
            self.peerConnection.setLocalDescription(sdp) { error in
                if error != nil { onError("Video offer creation failed"); return }
                onSuccess(sdp.sdp)
            }
        }
    }

    func setRemoteAnswer(sdp: String, onSuccess: @escaping () -> Void, onError: @escaping () -> Void) {
        peerConnection.setRemoteDescription(RTCSessionDescription(type: .answer, sdp: sdp)) { error in
            if error != nil { onError() } else { onSuccess() }
        }
    }

    func addRemoteCandidate(sdpMid: String?, sdpMLineIndex: Int32, candidate: String,
                            onSuccess: @escaping () -> Void, onError: @escaping () -> Void) {
        peerConnection.add(RTCIceCandidate(sdp: candidate, sdpMLineIndex: sdpMLineIndex, sdpMid: sdpMid)) { error in
            if error != nil { onError() } else { onSuccess() }
        }
    }

    func diagnostics(onResult: @escaping (RtcDiagnostics) -> Void) {
        peerConnection.statistics { report in
            let stats = report.statistics
            // Use the selected pair on the video RTP transport. A successful/nominated or
            // gathered candidate is not proof it carries this stream.
            let video = stats.values.first {
                $0.type == "inbound-rtp" &&
                    (($0.values["kind"] as? String ?? $0.values["mediaType"] as? String) == "video")
            }
            let transportId = video?.values["transportId"] as? String
            let transport = transportId.flatMap { stats[$0] }
            let pairId = transport?.values["selectedCandidatePairId"] as? String
            let pair = pairId.flatMap { stats[$0] }
            let localId = pair?.values["localCandidateId"] as? String
            let remoteId = pair?.values["remoteCandidateId"] as? String
            let local = localId.flatMap { stats[$0] }
            let remote = remoteId.flatMap { stats[$0] }
            let frames = (video?.values["framesDecoded"] as? NSNumber)?.int64Value ?? 0
            onResult(RtcDiagnosticsKt.selectedPairDiagnostics(
                localType: local?.values["candidateType"] as? String,
                remoteType: remote?.values["candidateType"] as? String,
                protocol: local?.values["protocol"] as? String,
                videoReceived: frames > 0,
                videoWidth: (video?.values["frameWidth"] as? NSNumber)?.int32Value ?? 0,
                videoHeight: (video?.values["frameHeight"] as? NSNumber)?.int32Value ?? 0
            ))
        }
    }

    func close() {
        WebRtcPeer.onMainVoid {
            self.stopVoiceCall()
            self.rendererClosed = true
            self.remoteVideoTrack?.remove(self.videoView)
            self.remoteVideoTrack = nil
        }
        peerConnection.close()
    }

    func renderView() -> UIView? {
        return videoView
    }

    func setVideoFill(fill: Bool) {
        WebRtcPeer.onMainVoid {
            self.videoView.videoContentMode = fill ? .scaleAspectFill : .scaleAspectFit
        }
    }

    func requestMicrophonePermission(onGranted: @escaping () -> Void, onDenied: @escaping () -> Void) {
        WebRtcPeer.onMainVoid {
            guard !self.rendererClosed else { onDenied(); return }
            let session = AVAudioSession.sharedInstance()
            switch session.recordPermission {
            case .granted: onGranted()
            case .denied: onDenied()
            case .undetermined:
                session.requestRecordPermission { granted in
                    WebRtcPeer.onMainVoid {
                        if granted && !self.rendererClosed { onGranted() } else { onDenied() }
                    }
                }
            @unknown default: onDenied()
            }
        }
    }

    func startVoiceCall(onSuccess: @escaping () -> Void, onError: @escaping () -> Void) {
        WebRtcPeer.onMainVoid {
            guard !self.rendererClosed,
                  UIApplication.shared.applicationState == .active,
                  AVAudioSession.sharedInstance().recordPermission == .granted,
                  WebRtcPeer.audioOwner == nil || WebRtcPeer.audioOwner === self,
                  let transceiver = self.peerConnection.transceivers.first(where: { $0.mediaType == .audio }) else {
                onError(); return
            }
            if self.voiceActive { onSuccess(); return }
            let audio = RTCAudioSession.sharedInstance()
            self.previousAudioCategory = (audio.session.category, audio.session.mode, audio.session.categoryOptions)
            WebRtcPeer.audioOwner = self
            audio.lockForConfiguration()
            do {
                try audio.setCategory(.playAndRecord,
                    mode: .voiceChat, options: [.defaultToSpeaker, .allowBluetooth])
                try audio.setActive(true)
                self.activatedAudioSession = true
                audio.unlockForConfiguration()
            } catch {
                audio.unlockForConfiguration()
                self.stopVoiceCall()
                onError(); return
            }
            // Reuse the negotiated audio sender. Do not replace the peer, SDP or video renderer.
            let source = self.factory.audioSource(with: RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil))
            let track = self.factory.audioTrack(with: source, trackId: UUID().uuidString.lowercased())
            track.isEnabled = false
            self.microphoneSource = source
            self.microphoneTrack = track
            self.microphoneSender = transceiver.sender
            transceiver.sender.track = track
            self.voiceActive = true
            self.remoteAudioMuted = false
            self.remoteAudioTrack?.isEnabled = true
            audio.isAudioEnabled = true
            track.isEnabled = true
            onSuccess()
        }
    }

    func setMicrophoneMuted(muted: Bool) {
        WebRtcPeer.onMainVoid { self.microphoneTrack?.isEnabled = self.voiceActive && !muted }
    }

    func setRemoteAudioMuted(muted: Bool) {
        WebRtcPeer.onMainVoid {
            self.remoteAudioMuted = muted
            self.remoteAudioTrack?.isEnabled = self.voiceActive && !muted
        }
    }

    func stopVoiceCall() {
        WebRtcPeer.onMainVoid {
            self.microphoneTrack?.isEnabled = false
            self.remoteAudioTrack?.isEnabled = false
            self.microphoneSender?.track = nil
            self.microphoneSender = nil
            self.microphoneTrack = nil
            self.microphoneSource = nil
            self.voiceActive = false
            self.remoteAudioMuted = true
            guard WebRtcPeer.audioOwner === self else { return }
            let audio = RTCAudioSession.sharedInstance()
            audio.isAudioEnabled = false
            audio.lockForConfiguration()
            if self.activatedAudioSession { try? audio.setActive(false) }
            if let previous = self.previousAudioCategory {
                try? audio.setCategory(previous.0, mode: previous.1, options: previous.2)
            }
            audio.unlockForConfiguration()
            self.activatedAudioSession = false
            self.previousAudioCategory = nil
            WebRtcPeer.audioOwner = nil
        }
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
        if let track = rtpReceiver.track as? RTCAudioTrack {
            WebRtcPeer.onMainVoid {
                guard !self.rendererClosed else { return }
                self.remoteAudioTrack?.isEnabled = false
                self.remoteAudioTrack = track
                track.isEnabled = self.voiceActive && !self.remoteAudioMuted
            }
        }
        if let track = rtpReceiver.track as? RTCVideoTrack {
            WebRtcPeer.onMainVoid {
                guard !self.rendererClosed else { return }
                self.remoteVideoTrack?.remove(self.videoView)
                self.remoteVideoTrack = track
                track.add(self.videoView)
            }
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


