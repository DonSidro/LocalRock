import Foundation
import WebRTC

/// The same native offer setup is exercised by tools/rtc-offer-check on macOS CI.
/// Stream identifiers describe the offer only: this creates no capture source or local track.
enum WebRtcOffer {
    enum SetupError: Error { case unavailable }

    static func configure(_ peer: RTCPeerConnection, factory: RTCPeerConnectionFactory,
                          compactCodecs: Bool) throws {
        let audioInit = RTCRtpTransceiverInit()
        audioInit.direction = .sendRecv
        audioInit.streamIds = [UUID().uuidString.lowercased()]
        let videoInit = RTCRtpTransceiverInit()
        videoInit.direction = .sendRecv
        guard let audio = peer.addTransceiver(of: .audio, init: audioInit),
              let video = peer.addTransceiver(of: .video, init: videoInit) else {
            throw SetupError.unavailable
        }
        let audioCodecs = factory.rtpReceiverCapabilities(forKind: kRTCMediaStreamTrackKindAudio).codecs
            .filter { codec in
                guard codec.clockRate?.intValue == 48000 else { return false }
                switch codec.name.lowercased() {
                case "opus": return codec.numChannels?.intValue == 2
                case "red": return !compactCodecs && codec.numChannels?.intValue == 2
                case "telephone-event": return !compactCodecs
                default: return false
                }
            }.sorted { audioRank($0.name) < audioRank($1.name) }
        let videoCodecs = factory.rtpReceiverCapabilities(forKind: kRTCMediaStreamTrackKindVideo).codecs
            .filter { videoRank($0, compact: compactCodecs) != nil }
            .sorted { videoRank($0, compact: compactCodecs)! < videoRank($1, compact: compactCodecs)! }
        guard audioCodecs.contains(where: { $0.name.lowercased() == "opus" }),
              videoCodecs.contains(where: { $0.name.lowercased() == "h264" &&
                  $0.parameters["profile-level-id"]?.lowercased().hasPrefix("42e0") == true }) else {
            throw SetupError.unavailable
        }
        try audio.setCodecPreferences(audioCodecs)
        try video.setCodecPreferences(videoCodecs)
    }

    private static func audioRank(_ name: String) -> Int {
        switch name.lowercased() { case "opus": return 0; case "red": return 1; default: return 2 }
    }

    private static func videoRank(_ codec: RTCRtpCodecCapability, compact: Bool) -> Int? {
        guard codec.clockRate?.intValue == 90000 else { return nil }
        switch codec.name.lowercased() {
        case "h264":
            guard codec.parameters["packetization-mode"] == "1" else { return nil }
            let profile = codec.parameters["profile-level-id"]?.lowercased() ?? ""
            if profile.hasPrefix("640c") { return compact ? nil : 0 }
            return profile.hasPrefix("42e0") ? 1 : nil
        case "vp8": return compact ? nil : 2
        case "vp9": return !compact && (codec.parameters["profile-id"] ?? "0") == "0" ? 3 : nil
        case "av1": return !compact && (codec.parameters["profile"] ?? "0") == "0" ? 4 : nil
        case "red": return compact ? nil : 5
        case "ulpfec": return compact ? nil : 6
        // Native WebRTC assigns the matching RTX payload for each retained video codec.
        case "rtx": return 7
        default: return nil
        }
    }
}
