import Foundation
import WebRTC

// Executed with a copy of the app's WebRtcOffer.swift, never a separate offer implementation.
// Reports only codec structure and lengths. Do not print SDP, candidates or native errors.
enum CheckFailure: Error { case failed(String) }
func require(_ condition: Bool, _ message: String) throws {
    if !condition { throw CheckFailure.failed(message) }
}

func waitFor<T>(_ start: (@escaping (Result<T, Error>) -> Void) -> Void) throws -> T {
    var result: Result<T, Error>?
    start { value in DispatchQueue.main.async { result = value } }
    let deadline = Date().addingTimeInterval(15)
    while result == nil && Date() < deadline {
        RunLoop.current.run(until: Date().addingTimeInterval(0.01))
    }
    guard let result = result else { throw CheckFailure.failed("Native callback timed out") }
    return try result.get()
}

func description(_ peer: RTCPeerConnection, answer: Bool = false) throws -> RTCSessionDescription {
    try waitFor { finish in
        let handler: (RTCSessionDescription?, Error?) -> Void = { sdp, error in
            if let sdp = sdp, error == nil { finish(.success(sdp)) }
            else { finish(.failure(CheckFailure.failed("Native SDP generation failed"))) }
        }
        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        if answer { peer.answer(for: constraints, completionHandler: handler) }
        else { peer.offer(for: constraints, completionHandler: handler) }
    }
}

func apply(_ sdp: RTCSessionDescription, to peer: RTCPeerConnection, local: Bool) throws {
    let _: Void = try waitFor { finish in
        let handler: (Error?) -> Void = { error in
            finish(error == nil ? .success(()) : .failure(CheckFailure.failed("Native SDP application failed")))
        }
        if local { peer.setLocalDescription(sdp, completionHandler: handler) }
        else { peer.setRemoteDescription(sdp, completionHandler: handler) }
    }
}

struct Section {
    let lines: [String]
    var payloads: [String] { Array(lines[0].split(separator: " ").dropFirst(3)).map(String.init) }
    var codecs: [String: String] {
        Dictionary(uniqueKeysWithValues: lines.filter { $0.hasPrefix("a=rtpmap:") }.map {
            let parts = $0.dropFirst(9).split(separator: " ", maxSplits: 1).map(String.init)
            return (parts[0], parts[1].lowercased())
        })
    }
    func fmtp(_ payload: String) -> [String: String] {
        let prefix = "a=fmtp:\(payload) "
        guard let line = lines.first(where: { $0.hasPrefix(prefix) }) else { return [:] }
        return Dictionary(uniqueKeysWithValues: line.dropFirst(prefix.count).split(separator: ";").compactMap {
            let pair = $0.split(separator: "=", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
            return pair.count == 2 ? (pair[0], pair[1]) : nil
        })
    }
    var stream: String? {
        lines.first { $0.hasPrefix("a=msid:") }?.dropFirst(7).split(separator: " ").first.map(String.init)
    }
}

func inspect(_ sdp: String, compact: Bool) throws -> [String: Any] {
    var sections = [[String]]()
    for line in sdp.components(separatedBy: "\r\n") where !line.isEmpty {
        if line.hasPrefix("m=") { sections.append([line]) }
        else if !sections.isEmpty { sections[sections.count - 1].append(line) }
    }
    try require(sections.count == 2, "Expected audio and video only")
    let audio = Section(lines: sections[0]), video = Section(lines: sections[1])
    try require(audio.lines[0].hasPrefix("m=audio ") && video.lines[0].hasPrefix("m=video "), "Media order")
    try require(audio.lines.contains("a=sendrecv") && video.lines.contains("a=sendrecv"), "Media directions")
    try require(audio.stream != nil && audio.stream != "-" && video.stream == "-", "Stream metadata")
    let expectedAudio = compact ? ["opus/48000/2"] : ["opus/48000/2", "red/48000/2", "telephone-event/48000"]
    try require(audio.payloads.compactMap { audio.codecs[$0] } == expectedAudio, "Audio codec selection")
    let expectedVideo = compact ? ["h264/90000", "rtx/90000"] :
        ["h264/90000", "rtx/90000", "h264/90000", "rtx/90000", "vp8/90000", "rtx/90000",
         "vp9/90000", "rtx/90000", "av1/90000", "rtx/90000", "red/90000", "rtx/90000", "ulpfec/90000"]
    try require(video.payloads.compactMap { video.codecs[$0] } == expectedVideo, "Video codec selection")
    for payload in video.payloads {
        let name = video.codecs[payload]!, params = video.fmtp(payload)
        if name == "h264/90000" {
            let profile = params["profile-level-id"] ?? ""
            try require(params["packetization-mode"] == "1" && params["level-asymmetry-allowed"] == "1", "H264 parameters")
            try require(profile.hasPrefix("42e0") || (!compact && profile.hasPrefix("640c")), "H264 profile")
        } else if name == "rtx/90000" {
            let target = params["apt"] ?? ""
            try require(video.payloads.contains(target) && video.codecs[target] != "rtx/90000", "RTX association")
        } else if name == "vp9/90000" {
            try require((params["profile-id"] ?? "0") == "0", "VP9 profile")
        }
    }
    return ["variant": compact ? "compact" : "oem", "sdp_bytes": sdp.utf8.count,
            "audio_payloads": audio.payloads, "audio_codecs": expectedAudio,
            "video_payloads": video.payloads, "video_codecs": expectedVideo,
            "named_audio_stream": true, "local_capture_tracks": 0]
}

RTCSetMinDebugLogLevel(.none)
RTCInitializeSSL()
let factory = RTCPeerConnectionFactory(encoderFactory: RTCDefaultVideoEncoderFactory(), decoderFactory: RTCDefaultVideoDecoderFactory())
func peer() throws -> RTCPeerConnection {
    let config = RTCConfiguration()
    config.sdpSemantics = .unifiedPlan
    // The check needs SDP only; no STUN/TURN services or network candidates.
    config.iceTransportPolicy = .none
    guard let peer = factory.peerConnection(with: config,
        constraints: RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil), delegate: nil) else {
        throw CheckFailure.failed("Native peer unavailable")
    }
    return peer
}

do {
    for compact in [false, true] {
        let offerer = try peer(), answerer = try peer()
        defer { offerer.close(); answerer.close() }
        try WebRtcOffer.configure(offerer, factory: factory, compactCodecs: compact)
        try WebRtcOffer.configure(answerer, factory: factory, compactCodecs: true)
        try require(offerer.senders.allSatisfy { $0.track == nil }, "A local capture track was created")
        let offer = try description(offerer)
        let summary = try inspect(offer.sdp, compact: compact)
        try apply(offer, to: offerer, local: true)
        try apply(offer, to: answerer, local: false)
        let answer = try description(answerer, answer: true)
        try apply(answer, to: answerer, local: true)
        try apply(answer, to: offerer, local: false)
        print(String(data: try JSONSerialization.data(withJSONObject: summary, options: [.sortedKeys]), encoding: .utf8)!)
    }
    print("Native offer structure, local descriptions and Opus/H264 answers passed.")
} catch CheckFailure.failed(let message) {
    print("Offer check failed: \(message)")
    exit(1)
} catch {
    print("Offer check failed in native WebRTC setup.")
    exit(1)
}
