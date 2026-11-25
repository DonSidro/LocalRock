package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.webrtc.RtcOfferOptions

/** Temporary OEM comparison. B, C and D each change one detail relative to A. */
enum class CameraVideoTest(
    val code: String,
    val label: String,
    val description: String,
    val offerOptions: RtcOfferOptions = RtcOfferOptions(),
    val literalSdpLineBreaks: Boolean = true,
    val earlyRemoteIce: Boolean = true,
) {
    OEM_MATCH("A", "OEM match", "Closest match to the captured OEM request."),
    ESCAPED_JSON("B", "Escaped JSON", "A with standard JSON line breaks.", literalSdpLineBreaks = false),
    COMPACT_CODECS("C", "Compact offer", "A with Opus and H.264 codecs only.", RtcOfferOptions(compactCodecs = true)),
    ANSWER_FIRST_ICE("D", "Answer first", "A waits for the answer before requesting ICE.", earlyRemoteIce = false);

    val previewQuality: String get() = "SD"
}

/** Requested robot encoder quality; signaling choices remain independent. */
enum class CameraVideoQuality { HD, SD }
