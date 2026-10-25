# Server schedules (experimental)

The Schedule screen reads `/user/devices/{duid}/jobs` from the configured local
server. It includes imported native jobs that are absent from legacy `get_timer`.
It requires a server with the schedule job CRUD routes and scheduler enabled,
currently provided by pponce/local_roborock_server's SCHEDULE_SUPPORT branch.
A successful list request alone does not prove that an older server executes jobs.
Hardware execution remains to be validated.

## Schedule editor

- Compact tappable cards show start time, repeat summary, Full/Rooms, cleaning mode,
  and an enable switch. Robot timer diagnostics appear only in the editor’s
  More details section, using neutral text rather than an error warning.
- A full-screen editor groups Start, Repeat and Task controls. Timezone, saved map
  reference and next UTC time are available under More details.
- Repeat presets: Once, Every day, Weekdays, Weekends, and Custom weekday selection.
  Once writes `repeated=false` with a daily cron: the next matching occurrence is
  consumed and disabled by the server, including when the run fails. Re-enabling
  schedules its next occurrence; it does not replay a past run.
- Simple weekly cron patterns are editable for both recurring and one-time jobs.
  Dated/complex cron patterns stay visible and read-only.
- Full selects the whole map. Tap the loaded map name or Rooms to select named
  rooms using checkboxes or the existing interactive map renderer. Room selection
  is a draft until Done; dismissing a sheet leaves saved settings untouched.
- Cleaning modes: Vacuum, Vac & Mop, Mop, Custom, plus suction/water/pass controls.
  Custom uses the robot's existing per-room settings (106/204/302); it does not
  rewrite those settings. Mode changes explicitly set all three motor codes.
  Unknown modes are shown as Saved settings and preserved until explicitly edited.
- Enable/disable the server job; inspect the native robot timer independently.
  This switch does not turn an off native timer on. Imported native timers are
  not created/deleted/reconfigured by this screen.
- Create disabled server-managed jobs without a native timer name. These use the
  server's legacy execution path, not native `server_scheduled_start` dispatch.
  Review settings and enable explicitly in the editor or list. Room cleans require the intended map
  to stay loaded. Multi-floor automatic switching is not provided.
- Delete a server job after confirmation; any corresponding native timer remains.
- Legacy timer controls remain accessible through the schedule information button.

Room names in the editor come from the robot's loaded map. An imported job's
`map_index` is not assumed to equal the robot's mapFlag. Room changes require
explicit confirmation that the intended map is loaded; unknown room IDs block
room replacement until explicitly removed. The editor does not infer a mapping
between imported map indices and named floor maps or switch floors. Merely editing the time never replaces the param object.
Unknown settings, native timer names, map indices, and ordered segment strings
are preserved. Model-specific custom modes remain unchanged unless a supported
setting is explicitly edited; validate those combinations on hardware.

Writes use Hawk-signed exact JSON bytes. HTTP and application-code failures are
reported, writes are serialized in the screen, and failed edits stay open.
Before update/delete, the app re-reads the record and rejects a changed snapshot
(excluding calculated nextFireTime). There is still a read/write race because the
server API has no atomic client revision token. Do not edit simultaneously from
Homebridge, the dashboard, and LocalRock.

Creation uses a stable client-selected ID for retries while the editor stays
open. If a request times out, refresh to check whether it succeeded before trying
again; do not close and create another copy blindly. Cloud re-import can replace
local edits and restore deleted jobs.

No scheduler runs on the phone. Homebridge retains pause/delay policy. This app
contribution is separate from the server scheduler and admin dashboard changes.

## Validation

CI compiles Android and iOS shared code, runs Android-hosted common tests (including
schedule payload preservation, repeat presets, one-time payloads, motor mode codes,
cron parsing, native state classification, signed
CRUD and stale writes), and produces a debug Android APK. iOS device installation
requires an Xcode build and signing; the existing TestFlight app will not contain
these fork changes automatically.

Before enabling a real schedule: compare imported room IDs against the rebuilt
map, confirm the native timer is on when applicable, and use one short recurring
room clean. Check actual robot movement, target rooms and history. Then test an
off native timer, disabled server job, deletion, offline/reconnect, and restart.
Tests and successful HTTP writes do not establish firmware execution support.
