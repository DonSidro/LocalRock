# Camera remote viewing: protocol notes

What the robot and the local server do when LocalRock shows live video, and what has been seen on real robots. For using the feature, models and limits, see [camera-live-view.md](camera-live-view.md).

Each claim is marked with where it comes from.

- **Robot**: seen on a real robot (a Qrevo Curv 2 Flow, model a245), by the author or in a report the author is relaying.
- **Decompile**: read from the official Roborock plugin (decompiled JavaScript), not exercised against a robot by LocalRock.
- **Code**: what this repository does, whether or not a robot has confirmed it.

## What has and has not been checked

- A Curv 2 Flow ran the first camera attempts with a server name that did not fit the 15 character limit below. The PIN was accepted and `camera_status` read 28071 (two-key remote viewing already active), but `start_camera_preview` failed with -10014 because the robot could not reach the relay bootstrap. **Robot**.
- A Curv 2 Flow streamed video in the official iOS app and in an earlier LocalRock build on 2026-11-12, after the server was moved to a name that fits, TURN was provided and the robot was paired again. **Robot**, from the author's notes. That build used its own offer shaping (see "The WebRTC offer"); the code on current master was not run against a robot again.
- Another user reported a Curv 2 Flow working by moving the API host to `api-r.<domain>` on port 443, giving DNS for both names, a certificate covering both and pairing again. That robot needed no activation (`camera_status` 8613, bit 2 already set). **Robot**, from the server's issue tracker.
- Android voice calls, the retry of transient start errors and the TURN polling below are built from the official plugin and the code. None of it is verified on a robot.

## The pieces

```
phone (LocalRock)                 local_roborock_server               robot
-----------------                 ---------------------               -----
check_homesec_password --MQTT-->                                 -->  PIN check
start_camera_preview   --MQTT-->                                 -->  asks for a relay:
                                  POST /<name>iot.roborock.com/  <--   POST .../fwapi/createca
                                  fwapi/createca  -> TURN creds
get_turn_server        --MQTT-->                                 -->  relay address and login
offer (SDP)            --MQTT-->                                 -->
answer (SDP), ICE      <--MQTT--                                 <--
video + audio          <================ TURN relay (or LAN) =========>
```

Signalling goes over the MQTT connection LocalRock already uses for commands (`send_sdp_to_robot`, `get_device_sdp`, `send_ice_to_robot`, `get_device_ice`). Media goes over WebRTC, through the server's TURN relay or directly on the LAN. No Roborock cloud is involved. **Code**.

## Remote viewing activation

The robot refuses `start_camera_preview` with -10012 until remote viewing is active, and LocalRock does not activate it (see camera-live-view.md). What is known about `camera_status`, as the official app packs it (**Decompile**; values checked against a Curv 2 Flow: 259 off, 8615 and 28071 on):

| Bits | Meaning |
| --- | --- |
| 2 | remote viewing on, set by the robot after a three-button hold; writes to it are refused |
| 5 | privacy policy agreed |
| 7 | pet-mode alert shown |
| 12-13 | two-key state: 0 off, 1 requested, 2 active |

Two-key robots are activated by writing the pet-mode alert bit, then the privacy bit, then state 1, and pressing Power within a minute. The sequence 257, 385, 417, 4513 was captured from the official app on an a226 by another reporter. Bit 32, 33, 48 or 73 of the robot's feature set marks a robot with real-time video.

## The PIN and the pattern

The camera PIN lives on the robot. The official app draws it as a pattern on a 3 by 3 grid; the PIN string is the 1-based numbers of the visited dots in order, at least 4 dots, and the robot is sent the MD5 of that string in lower-case hex. **Decompile**.

- `check_homesec_password {password: md5}` verifies it. -10010 means too many wrong tries; the official app then locks its prompt for 30 seconds. -1007 means the PIN feature is switched off until remote viewing is activated.
- The official app never calls `reset_homesec_password`: the robot answers a reset by switching remote viewing off.

## `start_camera_preview` and `createca`

`start_camera_preview {client_id, quality, password}` asks the robot to start streaming. Before it answers, the robot fetches a relay: it POSTs to `https://<token.r, first 15 characters>iot.roborock.com/fwapi/createca`. Some firmware (an a144 in the server's notes) uses the region's `country_domain` instead. The local server answers that request with TURN credentials; this is the server's camera support (local_roborock_server PRs #144 and #155, and its `docs/camera.md`). **Robot**, from the server's reports; LocalRock does not make this call.

If the robot cannot complete that request, `start_camera_preview` fails with -10014 ("request turnserver failed").

### The 15 character limit

The robot builds that hostname from `token.r`, truncated to 15 characters. `token.r` is the server name without its `api-` prefix, with a trailing `/`, and with the port kept when it is not 443. The input has to fit in 15 characters including the slash and any port, so a hostname without a port can be at most 14 characters. A longer name is cut and the robot looks up a hostname that does not exist, which gives -10014. **Robot**, and the server's documentation.

For example, `api-r.example.com` on port 443 gives `r.example.com/` (14 characters) and fits; `api-vacuum.example.com` does not. DNS and a certificate must cover the stripped name (`r.example.com`) as well as the API name. A wildcard for `*.example.com` covers `api-r.example.com` but not `example.com` or `r.example.com`. The same truncation limits Matter key requests.

### TURN

A TURN relay is required for viewing from outside the LAN, and the server hands the robot and the app the same relay. `get_turn_server` returns the relay to the app; the robot answers `{"url":"retry"}` until it has one, so the app asks up to 5 times, 500 ms apart, which is the official app's cadence (**Decompile**). LAN viewing does not need TURN.

## The WebRTC offer

A stock libwebrtc offer was accepted by `send_sdp_to_robot` on a Curv 2 Flow, and then `get_device_sdp` answered `{"dev_sdp":"retry"}` for ever. **Robot**. The offer that worked had the audio section first, `sendrecv`, Opus and telephone-event only; the video section `recvonly` with H.264 packetization-mode=1 only, plus rtx, red and ulpfec; and no `a=extmap-allow-mixed`. The offer is sent before any ICE candidate.

The official app's own offer, from a decoded capture, is an audio `sendrecv` section with Opus, red and telephone-event and a `sendrecv` video section with the full libwebrtc codec list (H.264 `640c29` and `42e029`, VP8, VP9, AV1, red, ulpfec, rtx), extmap-allow-mixed, no `b=AS` and no `x-google` hints. **Decompile and log**.

Current master builds the offer in `RtcPeer.android.kt` with codec preferences and offers the connection tests A to D in the viewer. If a model accepts the offer but never answers, test C (compact offer) is the closest to the trimmed offer above. That has not been tried on a Curv 2 Flow with current master.

## Error codes

The official app maps the robot's code to its own error number and fix text. LocalRock shows the same text and number for `start_camera_preview` refusals (`CameraStartErrors`), except -1007, -10012 and -10014, which keep their own explanations. **Decompile**.

| Robot code | App code | Shown |
| --- | --- | --- |
| -10009 | -105 | reset the robot and try again |
| -10010 | -106 | reset the robot and try again |
| -10011 | -101 | move the robot off the dock to view video |
| -10013 | -104 | check whether another phone is showing the video |
| -10015 | -103 | disable and re-enable remote viewing, or restart |
| -10016 | -107 | resource occupied, try later |
| -10021 | -116 | unable to view while emptying |
| anything else | -103 | as -10015 |
| no code | -100 | network failed |

Errors -100, -103, -114 and -107 are retried up to 8 times, 500 ms apart, then -117 ("the maximum number of retries has been reached"). **Decompile**.

## Voice calls

A call sends `start_voice_chat {play: true, record: true}` after the phone's microphone is on, and `stop_voice_chat` when it ends. The robot reports state 28 ("In call") during a call. The robot has no `get_voice_chat_volume` (a Curv 2 Flow answers "not recognized"); `set_voice_chat_volume {volume}` sets the speaker volume. **Decompile**, and **Robot** for the missing getter.

On Android the call uses the audio line already in the offer: a microphone track is attached to the audio sender when the call starts, the robot's audio track stays disabled until then, and the audio is routed to the loudspeaker for the call. **Code**; not run on a phone against a robot.

## Server requirements

- A local_roborock_server with camera support: `createca` bootstrap and TURN, a server name that fits the 15 character limit, DNS and certificates for both names, and the TURN ports reachable by the robot and the phone.
- The robot has to be paired after the name is chosen, since it remembers the server name it was given.
