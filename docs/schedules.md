# Schedules

The schedule screen lists jobs from the server's `/user/devices/{duid}/jobs` API. A server with the schedule API and executor is required for server-triggered cleaning. The server must remain running and the robot must be reachable when a job is due. Saving a native timer entry alone is not proof that a scheduled clean will execute.

If the jobs collection is unavailable with HTTP 404 or 405, the app uses the legacy timer interface. An empty supported list, authentication error, timeout or server error does not trigger that fallback.

## Editing and enablement

Tap a schedule to edit its time, repetition or cleaning settings. Model-specific SmartPlan, water and mop-route options are retained. Jobs in an unsupported format remain visible with a read-only editor rather than being silently converted.

Server jobs retain their IDs, native references, room order and unedited settings. Time and cleaning-setting edits do not recreate native timers. Changing Enabled verifies a linked native timer, changes it only when necessary, then saves and reads back the server job. Jobs without a native reference use only the server API. The HTTP job ID and native timer name are different identifiers.

The editor's More details section provides a read-only native-timer check. Not checked, unknown, missing and off are distinct states. The list does not display native-timer warnings.

## Unconfirmed or partial saves

A timeout may occur after a write succeeded. The app reads back state without repeating the write. A failed verification, concurrent edit or partial save is reported explicitly. It does not automatically recreate a timer or undo an uncertain write. Refresh and inspect both sides before retrying an unconfirmed operation.

Deletion of a native-linked job verifies native deletion before deleting the server record. Server-only jobs never send native delete commands. Ambiguous or shared native references block deletion.

Client read-back checks reduce stale writes but are not an atomic transaction across HTTP and MQTT. Other clients can still change a job between requests.
