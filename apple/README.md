# XR18 Bubble for iPhone and iPad (preview)

This directory is the initial native Apple port of XR18 Bubble. It targets iOS 17 and later and is intended to provide the same quick-access workflow as the Android app: open a small, focused control panel, connect to a saved XR18 profile, verify a fresh mixer state, and adjust only the controls that are needed most often.

The Apple version is designed for iPhone and iPad rather than as an Android-style system overlay. On iPhone it is a normal app with a fast last-used panel and system shortcuts where appropriate. On iPad it can also be used in a compact Slide Over or windowed layout. The user can keep the app in the Dock for quick access.

Planned and implemented port scope:

- English and Slovak UI, with the structure ready for additional translations.
- Saved mixer profiles containing the known network details and the selected controls (master, DCA, mute groups, buses, and channels; up to 31 controls).
- Large control sizes and independent text-size choices, including extra-large accessibility options.
- XR18 OSC/UDP communication through Apple’s Network framework.
- Wi-Fi passwords stored in this device’s Keychain; profile names, addresses, SSIDs and preferences remain in local app storage.
- `NEHotspotConfiguration` joins for a configured mixer Wi-Fi network, with a user-visible system approval where iOS requires it.
- Explicit return profiles for a known previous Wi-Fi network. The app cannot silently read or recover a saved Wi-Fi password from iOS.
- Fresh-state gating: controls remain unavailable until the app has received a current mixer response after connecting. A successful Wi-Fi join alone is not treated as a connected mixer.
- An explicit offline demo mode for UI and interaction work. Demo mode never sends packets to a mixer.

The current preview uses manual host entry for the first connection. Broadcast discovery and automatic network scanning are not part of this first pass. Enter the mixer’s IP address or hostname; the protocol port is fixed at 10024.

## Build on macOS

Apple simulator builds require macOS and Xcode. Windows can inspect the source and download CI artifacts, but it cannot run the iOS simulator or install an iOS app directly. No provisioning credentials, certificates, or private keys belong in this repository.

From the `apple` directory on a Mac:

```sh
brew install xcodegen
xcodegen generate
open XR18Panel.xcodeproj
```

The shared protocol and model package can be tested without an Apple device:

```sh
swift test --package-path Packages/XR18Core
```

For a repeatable preview build, use the repository’s GitHub Actions workflow when available. The workflow produces simulator-oriented screenshots and a simulator app artifact; those artifacts are useful for review on a Mac, but they are not an installable iPhone IPA and are not an App Store release.

Launch arguments used by the preview contract:

- `--demo` starts the explicit offline mixer fixture. It does not contact the network.
- `--ui-testing` enables the deterministic UI-testing configuration.

## Network and privacy behaviour

The app needs Local Network permission before it can communicate with an XR18 on the same LAN. Joining a configured mixer network can also cause an iOS system confirmation. The app reports the mixer connection only after it receives and validates a fresh state response.

Returning to the previous network is offered only when the user has configured a known return profile. iOS does not expose arbitrary saved Wi-Fi passwords to an application, so the app cannot promise a silent return to every network from which the phone previously disconnected.

Relevant Apple documentation:

- [Network framework](https://developer.apple.com/documentation/network)
- [Local network privacy](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy)
- [NEHotspotConfigurationManager](https://developer.apple.com/documentation/networkextension/nehotspotconfigurationmanager)
- [WidgetKit controls](https://developer.apple.com/documentation/widgetkit/adding-interactivity-to-widgets-and-live-activities)

## Project status

This is a development preview. The simulator layout and protocol fixture can be reviewed, but no physical iPhone/iPad Wi-Fi join, mixer session, or return-network test has been validated yet. There is no signed IPA or App Store submission in this repository. Before release we still need Mac/Xcode builds on real hardware, privacy and permission review, app icons and screenshots, signing setup, and App Store Connect metadata.

Slovak note: Toto je prípravná natívna verzia pre iPhone a iPad. Emulátor overí rozhranie a demo protokol, ale Wi‑Fi pripojenie k reálnemu XR18 treba ešte overiť na Macu a skutočnom zariadení.
