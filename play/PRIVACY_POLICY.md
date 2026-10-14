# XR18 Bubble privacy policy (draft)

**Effective date:** 14 October 2026 (draft; confirm at publication)  
**Product developer credit:** Pavol Škoda  
**Legal publisher/developer:** Pavol Škoda (verify against Play Console publisher details)  
**Contact:** pavolskoda@azet.sk  
**Public URL:** https://palkoskoda.github.io/xr18-bubble/privacy.html (planned; not deployed)

XR18 Bubble is a local Android companion for a compatible X AIR mixer. The product is developed by Pavol Škoda. This draft must be published at a stable public URL and updated with the publisher's verified legal identity and contact details before a Google Play submission.

## Information used on the device

The app stores one preferred Wi‑Fi/mixer profile and the selected control layout locally. The profile can include a user-entered Wi‑Fi network name (SSID), optional user-entered network identifier (BSSID), mixer address, and a Wi‑Fi password. The password is protected using Android Keystore-backed local encryption. These settings are not uploaded to the developer, a cloud service, or an advertising service.

The app provides the saved SSID, optional user-entered BSSID and credentials to Android to request the chosen access point. After a successful mixer connection, it can remember the current Wi‑Fi SSID and security type for that mixer, but only after the mixer responds. It does not automatically pin an access point by BSSID. Reading the current Wi‑Fi identity is a location-sensitive Android Wi‑Fi operation, so the app may request precise location permission and require Android Location to be enabled. The app uses that permission only for the Wi‑Fi identity; it does not use GPS, derive physical location, scan nearby access points, or build a location history. On Android 13 and newer it separately requests Nearby Wi‑Fi Devices for Wi‑Fi connection operations. On older Android versions, the legacy Wi‑Fi permission requirements apply. Mixer discovery sends read-only UDP requests over an existing Wi‑Fi connection.

When the user connects to a mixer, the app sends mixer control commands and receives mixer state over local UDP. These packets are not encrypted by the X AIR control protocol. They are exchanged with the mixer on the user's local network and are not remotely stored or collected by the developer. Users should protect the local network.

The optional app shortcut displays the icon of Mixing Station or another app selected by the user. Its picker reads the locally installed apps that provide a launcher activity and stores only the chosen package name in local settings. This list and selection are never uploaded. The shortcut opens the selected app using Android's normal launcher intent; it does not access that app's private data. The app does not request `QUERY_ALL_PACKAGES`.

## Information we collect and share

The developer does not collect or share personal data from the app. There are no accounts, cloud backend, telemetry, analytics, advertising SDKs, crash reporting service, or developer-controlled remote storage in the release design. The app does not sell user data.

Google Play and Android may process information under their own policies when the app is installed, permissions are managed, or the Play services are used. This policy describes the app's own behavior.

## Overlay and notifications

The floating bubble is optional and appears only after the user enables the Android “display over other apps” permission and starts the feature. The app uses a user-visible notification for the running foreground service. The user can stop the bubble and revoke these permissions in Android settings.

## Retention and deletion

Local profiles remain on the device until the user edits, removes, or uninstalls the app. Clearing app storage removes the local profiles. Because the developer does not receive these settings, there is no developer-side copy to request or delete.

## Security limits

The app protects the saved Wi‑Fi password locally with Android Keystore-backed encryption. Mixer UDP commands are sent in the protocol's unencrypted form on the local network. No security promise is made for an untrusted Wi‑Fi network or for third-party hardware and firmware.

## Changes and contact

We may update this policy when the app's data practices change. Contact pavolskoda@azet.sk for privacy questions.

## Google Play Data safety declaration draft

Based on the current implementation and dependencies, answer the Play Console Data safety form as follows after checking the final release bundle:

- Does the app collect or share user data? **No**, provided no dependency added before release transmits data off-device.
- Is data encrypted in transit? **Not applicable for developer collection**. The local mixer UDP protocol is unencrypted; do not claim that mixer commands are encrypted in transit.
- Can users request deletion? **Not applicable to developer-held data**; local data can be deleted by clearing app storage or uninstalling.
- Ads or analytics: **No**.
- Account creation or login: **No**.

Google requires a Data safety form and privacy-policy link for published apps, including testing tracks other than apps exclusively active on internal testing. Recheck the form whenever dependencies or network behavior change.
