# Apple preview testing

These checks are intended for the initial iOS 17+ port. They separate deterministic simulator checks from behaviour that can only be proven on a physical iPhone or iPad connected to the relevant Wi-Fi network.

## macOS setup

Install Xcode and XcodeGen, then generate the project:

```sh
cd apple
brew install xcodegen
xcodegen generate
open XR18Panel.xcodeproj
```

Run the unit tests for the shared protocol/model package:

```sh
swift test --package-path Packages/XR18Core
```

Select an iOS 17 or later iPhone/iPad simulator in Xcode. The preview CI workflow can also build the simulator target and collect screenshots. A simulator artifact is for Mac review only; it cannot be installed on an iPhone and does not test the device’s Wi-Fi behaviour.

## Simulator and fixture checks

Use the launch arguments `--demo --ui-testing` for deterministic offline UI checks. Demo mode must never contact an XR18 or send an OSC/UDP packet.

Review the following manually or through the first-pass UI tests:

- English and Slovak language selection, including relaunch persistence.
- Last-used profile selection and the initial profile chooser.
- Master, DCA, mute-group, bus, and channel control rows.
- Volume controls with a mute action where supported, and mute-only controls without an unnecessary fader.
- The full set of large control sizes and independent text sizes, including the extra-large accessibility options.
- Loading, connected, stale, and error states. Controls must stay disabled until the fixture supplies a fresh validated state.
- Safe handling of a failed or rejected action: the displayed value must be refreshed from acknowledged state rather than assuming that a local tap succeeded.
- iPhone compact layout and iPad regular, Slide Over, and windowed layouts where the selected simulator supports them.
- Saved profile editing, removal, and return-profile selection without exposing credentials in logs or screenshots.

## Physical-device checks

The following require a real iPhone or iPad, a Mac with Xcode signing, and an XR18 or compatible test fixture on the relevant network:

- Grant Local Network permission and confirm that the app can reach the manually configured XR18 host.
- Join a configured mixer Wi-Fi profile through `NEHotspotConfiguration`, including the system approval flow.
- Confirm that Wi-Fi association alone does not turn the panel green; the app must receive a fresh XR18 response first.
- Verify actual OSC/UDP reads and writes, meter updates, mute state, and reconnect behaviour while the mixer is running.
- Configure a known return profile and verify the user-confirmed return flow. Do not expect the app to read an arbitrary saved Wi-Fi password from iOS.
- Test loss of network, mixer restart, stale state, and recovery without replaying unacknowledged destructive commands.

No physical-device Wi-Fi, join, return-network, or App Store validation is claimed by this document. Record the device model, iOS version, XR18 firmware, network names without passwords, and the result of each physical test when those tests become available.

## Security and release checks

Never commit provisioning profiles, signing certificates, private keys, Wi-Fi passwords, or mixer credentials. Use test profiles and local Keychain data. Before any store submission, repeat the tests with release configuration, review the Local Network usage text, verify the privacy declarations, and validate the archive on a real device.

Slovak note: Simulátor je vhodný na kontrolu vzhľadu, jazykov, veľkostí a demo komunikácie. Skutočné prepínanie Wi‑Fi, povolenie lokálnej siete a ovládanie XR18 sa musí overiť na fyzickom iPhone/iPade.
