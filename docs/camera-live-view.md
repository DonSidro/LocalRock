# Camera live view

LocalRock displays live camera video through its existing MQTT connection and
the local server's camera and TURN support. S7 MaxV is the only model tested with
LocalRock's video implementation. Other recognized models with official Roborock
remote viewing support can opt in to video and audio-call testing.

## Models and testing

The map-page video icon and **More → Video (experimental)** use the same exact
model list. Both entries also require a build with WebRTC enabled.

| Model | Model ID | LocalRock access |
| --- | --- | --- |
| S7 MaxV (including + / Ultra dock bundles) | `roborock.vacuum.a27` | Tested; existing video, driving and iPhone call controls |
| [S6 MaxV](https://support.roborock.com/hc/en-us/articles/900002181746-How-do-I-access-the-remote-viewing-feature-of-S6-MaxV-on-Roborock-APP) | `roborock.vacuum.a10` | Video testing opt-in |
| [S8 MaxV Ultra](https://us.roborock.com/products/roborock-s8-maxv-ultra/) | `roborock.vacuum.a97` | Video testing opt-in |
| [Qrevo MaxV](https://help.roborock.com/us/product/roborock-qrevo-maxv-message?category=faqs) | `roborock.vacuum.a87` | Video testing opt-in |
| [Qrevo Master](https://support.roborock.com/hc/en-us/articles/33953902919705-What-s-the-key-features-of-Roborock-Qrevo-Master) | `roborock.vacuum.a117` | Video testing opt-in |
| [Qrevo Slim](https://support.roborock.com/hc/en-us/articles/37614754458009-What-are-the-key-features-of-Qrevo-Slim) | `roborock.vacuum.a21` | Video testing opt-in |
| [Qrevo Curv](https://ca.roborock.com/products/roborock-qrevo-curv) | `roborock.vacuum.a135` | Video testing opt-in |
| [Qrevo Edge](https://us.roborock.com/pages/roborock-qrevo-edge-series) | `roborock.vacuum.a156` | Video testing opt-in |
| [Saros 10](https://ca.roborock.com/pages/roborock-saros-10) | `roborock.vacuum.a147` | Video testing opt-in |
| [Saros 10R](https://support.roborock.com/hc/en-us/articles/43407368729497-Does-Saros-10R-support-obstacle-avoidance-and-video-call-features) | `roborock.vacuum.a144` | Video testing opt-in |

This is a conservative list of recognized IDs, not a complete catalog of every
Roborock regional variant or newer model. Unknown IDs remain hidden. A camera or
obstacle-avoidance sensor alone does not establish remote viewing support; even
similarly named Qrevo variants can differ. Official remote viewing support does
not establish compatibility with LocalRock or the local server.

On an untested model, check **Enable video and audio testing for this robot** before entering
the existing camera PIN or pattern. The checkbox starts unchecked and resets when
leaving the screen or backgrounding the app. Unchecking it cancels a pending
credential check and clears the entry. No credential check or video connection is
started without opting in. Retries and connection tests use the same permission.

Experimental models have video viewing, quality selection, fullscreen framing and
audio calls on iPhone under the same opt-in. Opening video leaves audio off; tap
the microphone icon to start a call explicitly. Microphone, robot-audio mute and
speaker-volume controls are available during the call. Audio behavior can differ
by model, so report video and audio results separately. Driving, Pin-and-Go,
docking and cleaning controls are unavailable in their video screen. If a robot
cannot stream while docked, move it off the dock using its normal controls before
testing. S7 MaxV retains its existing controls and requires no additional checkbox.

Use **Report your result** to open the video PR and share whether video and audio calls work,
along with the model ID, firmware version, phone platform and any displayed error.
The entry screen shows the model ID and firmware for reference. Do not include
PINs, account credentials or private connection details.

### Capability sources

The model links above document Roborock's remote viewing/video-call features.
Model IDs and video-capable product families are cross-checked against
[python-roborock's product mappings](https://github.com/Python-roborock/python-roborock/blob/bc298d16687ec45dcf99ef14659b3f480027a880/roborock/data/code_mappings.py),
[device records](https://github.com/Python-roborock/python-roborock/blob/bc298d16687ec45dcf99ef14659b3f480027a880/device_info.yaml),
and [product video features](https://github.com/Python-roborock/python-roborock/blob/bc298d16687ec45dcf99ef14659b3f480027a880/roborock/device_features.py).
The [camera protocol](https://github.com/Python-roborock/python-roborock/blob/bc298d16687ec45dcf99ef14659b3f480027a880/docs/CAMERA_PROTOCOL.md)
also documents the video-monitor flag, but LocalRock uses this explicit model list
rather than inferring compatibility from general feature bits or product names.
To add a model, verify its exact ID and official remote viewing capability, add
it to `CameraModelSupport.kt` and its tests, and keep it experimental until tested.

## Setup

Activate remote viewing in the official Roborock app while connected to Roborock's
servers, before switching to the local server. Keep your existing camera PIN or
pattern. The robot, phone and server must be able to reach the configured TURN endpoint.

Tap the **video icon beside Settings** (or **More → Video (experimental)**) and
opt in if prompted, then draw your existing pattern. Pattern entry is the default; an accepted pattern opens
the viewer automatically. Choose **Use a numeric PIN** for typed entry, then tap
**Open video** or the keyboard's Done button. The shortcut appears only for supported
vacuums. The viewer uses the OEM matching connection and requests HD by default. On S7 MaxV while docked, choose a
destination on the map and tap **Go**. Video connects once the robot leaves the dock.

The credential remains in memory only while the screen is in the foreground.
Leaving or backgrounding the app closes the viewer and clears the credential.
Opening the map or returning to the app does not start video automatically.

## Video and cleaning controls

The robot controls in this section are available on S7 MaxV only.

- **Pause** and **Resume** control an existing cleaning task while off the dock.
  Room and zone tasks retain their scope. The viewer does not offer Start cleaning;
  use **Map → Go** to move an idle or docked robot.
- **Map → Go** sends an idle robot to the selected floor location.
- **Controls** opens the directional pad. Hold a direction to move; release to stop.
  If Pin-and-Go was started in this viewer, holding a direction first stops that
  navigation and waits for the robot to become idle. Keep holding to begin driving;
  releasing during the handoff cancels the pending drive.
- **Stop movement** stops manual driving, navigation started here, or a return to dock.
- **Home** offers to end video and return the robot to its dock.
- **Expand** fills the screen. Turn the phone horizontally for the inset map and
  overlaid controls. Tap the inset map to expand it for Pin-and-Go, then close it to
  return to video. Controls fade after five seconds without interaction; tap video
  to show them again. Robot status, battery, the map, call icon and **Exit** remain visible.
  The call icon stays beside the right-side controls, clear of the expanded map's **Go** button.
  **Exit** leaves fullscreen without ending the video session in either orientation.
  Landscape video fills the screen by default. Choose **Fit** in video settings to
  show the whole camera frame with black margins, or **Fill** to cover the display
  while trimming edges. Portrait video always fits the whole frame.
- **End video** closes the viewer. A cleaning task already running when you opened
  video continues. If you started movement here, the exit prompt also offers to
  return to dock; ending video alone stops viewer-owned movement.

Manual driving requires a connected viewer and an idle robot. Go works before video
connects, including while docked. Leaving video stops a held drive and Go navigation
started here; a requested return to dock continues.

The viewer shows progress while checking status, stopping Pin-and-Go or entering
manual control. Startup and status replies can take up to five seconds; movement
and stop acknowledgements retain a 1.5-second deadline. A failed operation shows
its stage and command, plus a numeric robot error code when available. Check the
robot before retrying. Other viewers' navigation and existing cleaning tasks are
not taken over by a direction press.

Tap **HD/SD** to change the requested quality. Video settings also show the received
frame dimensions when available, updating while the settings dialog is open.
The quality label confirms a request; received dimensions describe the actual stream.
If HD cannot connect, choose **Retry in SD**.
## Voice calls on iPhone

Audio is off when video starts. Tap the **green microphone icon** after video connects to hear the
robot and speak through its speaker. Allow microphone access if iOS asks. The call
uses the existing video connection; audio is not saved. The icon becomes a red
hang-up button during a call; tap it to stop audio while keeping video open.
The same Start/End call actions are also available as labeled buttons in video settings.

Video settings have independent **Microphone** and **Robot audio** switches. Use
your phone's volume buttons for incoming sound. The **Robot speaker volume** presets
change the volume heard at the robot only when selected; starting a call does not
change that volume. Exiting or backgrounding video ends the call. An interruption
mutes the microphone; unmute it explicitly to resume speaking.

Voice calls are experimental. They are currently
available in the iPhone build, including opted-in untested models; Android call
controls remain unavailable. Video and audio calls have only been tested on S7 MaxV.

**Activate Remote Viewing** and **Deactivate Remote Viewing** remain disabled and
send no commands. Stopping a viewer does not deactivate remote viewing. LocalRock
does not reset the PIN or take over another viewer.

## Known limitations

- HD/SD switching does not reliably change the received stream on the S7 MaxV.
  The selected label confirms the requested setting, not a verified quality change.
- Enabling or disabling remote viewing is unavailable in LocalRock. Use the
  official app for initial activation as described above; the disabled controls
  send no commands.
- Manual driving can intermittently report an unconfirmed or timed-out command.
  Wait for the robot to stop and for any task to finish before retrying explicitly.
  LocalRock does not automatically replay failed motion commands.

## Connection and retry

The screen shows the connection stage and requires decoded frames before reporting
live video.

If another viewer owns the camera, close that viewer and retry. LocalRock releases
only its own preview session. Wait for cleanup to finish before retrying. If release
cannot be confirmed, wait for the robot's preview timeout.

The **Connection tests (SD)** section keeps these diagnostic choices available:

| Choice | Behavior |
| --- | --- |
| A · OEM match | Default signaling approach. |
| B · Escaped JSON | Standard escaped JSON line breaks in the offer envelope. |
| C · Compact offer | Opus audio and baseline H.264 video with RTX. |
| D · Answer first | Requests robot ICE after applying its SDP answer. |

All choices request SD and create no phone microphone or camera capture source.
Stop the current viewer before selecting another choice.

