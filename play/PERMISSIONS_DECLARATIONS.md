# Android and Play declarations (draft)

This file records the rationale for the permissions currently present in `app/src/main/AndroidManifest.xml`. It is a publisher/reviewer aid, not proof of Play approval.

## Manifest mapping

| Permission / declaration | Why XR18 Bubble uses it | User or Play action |
|---|---|---|
| `INTERNET` | Local UDP communication with the selected mixer | Explain local network use in the listing/privacy policy |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Read current network and Wi‑Fi state | Required for connection status and network selection |
| `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_STATE` | Request or manage the selected network where Android permits it | Android may show system confirmation; verify per OS/device |
| `NEARBY_WIFI_DEVICES` with `neverForLocation` | Wi‑Fi connection operations on Android 13+; the app asserts it does not derive physical location from Wi‑Fi | Request at runtime and explain the nearby Wi‑Fi purpose |
| `ACCESS_FINE_LOCATION` | Android exposes the current Wi‑Fi identity (`WifiInfo` SSID/BSSID) as location-sensitive information, including on current Android versions | Request at runtime when the user chooses to remember the current mixer Wi‑Fi; explain that it is used only for Wi‑Fi identity, not GPS or physical location |
| `ACCESS_COARSE_LOCATION` | Legacy companion permission on versions where Wi‑Fi identity APIs use the location permission group | Request together where required by the OS; do not request background location |
| `SYSTEM_ALERT_WINDOW` | `TYPE_APPLICATION_OVERLAY` floating bubble | User must explicitly enable “display over other apps” in system settings |
| `POST_NOTIFICATIONS` | Visible foreground-service status on Android 13+ | Request at runtime and explain the status notification |
| `FOREGROUND_SERVICE` | Run the user-started connection/bubble service | Declare service type and show an ongoing notification |
| `FOREGROUND_SERVICE_SPECIAL_USE` | The service keeps a user-visible local mixer remote control available over the bubble | Play Console special-use declaration required; reviewer decides eligibility |

The service currently declares `android:foregroundServiceType="specialUse"` and property `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` describing the user-started persistent floating audio mixer remote control over local Wi‑Fi. Keep the property specific and consistent with the product behavior. Android 14+ requires the type and matching permission; Play reviews the stated use case.

## Important implementation checks before upload

- Target/compile SDK is **36**. The debug APK and non-debuggable release bundle are built separately. Physical mixer and Wi-Fi switching tests remain required before release.
- A foreground service cannot be treated as an invisible background connection. Start it from a user action, keep the notification visible, and validate Android 15/16 background-start rules when the bubble is reopened.
- `SYSTEM_ALERT_WINDOW` is a special app access, not a normal runtime permission. Provide a clear in-app explanation and a route to Android settings; do not imply that the system will grant it automatically.
- Wi‑Fi network selection may require a system confirmation or user action depending on Android version and OEM. Verify “preferred Wi‑Fi → mixer → reconnect” on the target phone and document the actual flow.
- The app remembers SSID/BSSID only after the selected mixer responds. A secured network still needs its passphrase once because Android does not expose saved Wi‑Fi passwords to ordinary apps.
- The precise-location permission and enabled Location setting are used only to read the current Wi‑Fi identity. The app does not request background location and does not use GPS or derive physical location.
- Recheck the merged manifest and release dependency graph for any SDK that transmits data. Update the Data safety form and privacy policy if that changes.

## App shortcut visibility

The manifest declares a `<queries>` MAIN/LAUNCHER intent so the user can choose a launchable installed app for the optional shortcut. It does not request `QUERY_ALL_PACKAGES`. The package list stays on the device. See [Android package visibility declarations](https://developer.android.com/training/package-visibility/declaring).
