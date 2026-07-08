# Privacy Policy for LocalRock

**Last updated:** 7 July 2026

LocalRock ("the app", "we", "us") is an open-source application that lets you
control Roborock vacuum robots through your **own self-hosted server** (the
`local_roborock_server` project). This policy explains what the app does and does
not do with your information.

**Summary:** LocalRock does not collect, transmit, or sell your personal data to
us or to any third party. The app runs entirely between your device and a server
that **you** configure and control. We operate no backend and receive nothing.

---

## 1. Who is responsible

This app is published by **Sidon Kodraliu** (the "developer"). For any privacy
question you can contact: **sidon@kodraliu.com**.

Because the app talks only to a server you host and configure yourself, you act
as the controller of your own data. The developer does not operate any server
that receives, stores, or processes your data.

## 2. Data stored on your device

The app stores the following locally on your device so it can function. This data
never leaves your device except to reach the server you configure (see Section 3):

- **Server address** — the URL of the local server you enter in Settings.
- **Login credentials / authentication token** — the email, login code, and the
  session token issued by your server, used to stay signed in.
- **Vacuum and home data** — device names, identifiers, cleaning maps, cleaning
  history, and status information returned by your server and robot.
- **App preferences** — such as whether you have seen the intro screen and a
  randomly generated installation identifier used to distinguish this app
  installation to your own server.

This data is held in the app's private storage. Uninstalling the app deletes it.
Automatic cloud/device-to-device backup of this data is disabled.

## 3. Data transmitted, and to whom

LocalRock communicates over the network **only with the server you configure**
and with the message broker (MQTT) that **your server** designates. This is used to:

- authenticate you,
- list and control your vacuum(s),
- receive status, maps, and cleaning records, and
- stream the live camera view (where supported by your robot).

The developer does **not** receive any of this traffic. The app contacts no
developer-operated or analytics server.

The only other outbound connections are **links you tap yourself**: an optional
link to the open-source project page (GitHub) and an optional donation link.
These open in your browser and are only visited if you choose to tap them.

## 4. Camera / live view

If your robot supports it, LocalRock can display a live camera stream. This video
is streamed directly between your robot/server and your device (via the broker
your server designates). It is not recorded by the app and is not sent to the
developer.

## 5. Permissions

The app requests only the permissions it needs to function:

- **Internet / network state** — to reach the server you configure.
- **Wi-Fi state / nearby Wi-Fi devices** — to pair a new vacuum with your Wi-Fi
  network during setup.
- **Location (Android 12 and below only)** — required by older versions of
  Android to scan for Wi-Fi networks during vacuum pairing. The app does not use
  or collect your location, and this permission is not requested on Android 13+.

## 6. Analytics, advertising, and tracking

LocalRock contains **no** analytics, advertising, crash-reporting, or tracking
software. No usage data, identifiers, or device information are collected or
shared for these purposes.

## 7. Data sharing and selling

We do not sell, rent, or share your personal data. We never receive it in the
first place.

## 8. Children

LocalRock is a utility for controlling home cleaning robots and is not directed
at children. It collects no data that could identify a child.

## 9. Your rights (GDPR / EEA and UK users)

Because your data stays on your device and on the server you control, you can
exercise the following at any time directly:

- **Access / portability** — your data resides on your own device and server.
- **Erasure** — uninstall the app to remove all locally stored data; manage
  server-side data through your own server.
- **Rectification / restriction / objection** — you control both endpoints.

The developer holds no copy of your data and therefore cannot access, export, or
delete it on your behalf. If you have questions about how your self-hosted server
processes data, please consult that server's documentation.

## 10. Data retention

The app retains data on your device only until you clear the app's data or
uninstall it. The developer retains nothing.

## 11. Changes to this policy

If this policy changes, the updated version will be published at the same public
location with a new "Last updated" date.

## 12. Contact

For any question about this policy: **sidon@kodraliu.com**.

---

*LocalRock is an independent, open-source project and is not affiliated with,
endorsed by, or sponsored by Roborock or Beijing Roborock Technology Co., Ltd.*
