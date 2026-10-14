# Testing XR18 Bubble 0.4.0

## Automated coverage

- OSC/protocol tests: 1,209 checks.
- Adaptive panel sizing: 39 checks.
- Real-format meter decoding: 5,131 checks for banks 1 and 8, little-endian values, dB/256 conversion and malformed packets.
- Local fake mixer self-test: discovery, handshake, GET/SET and meter blobs.
- Dedicated Android 15 emulator: verified mixer identity and SSID persistence, changing master/DCA/bus/channel meters, incomplete synchronization disabling all controls, stale meter clearing, subscriptions stopping while collapsed and renewing on reopen, and no unexpected SET commands.
- Return-Wi-Fi instrumentation: 49 checks of actual Android dialog/button handlers using a simulated request lifecycle. Includes Stay -> reopen -> close without another prompt, manual return, Cancel/Return, a new loan and stale-dialog guards. This does not test radio association.
- Native UI checks: system/light/dark themes, language selection, shortcut search/selection/launch/persistence, and all twelve locale renderings. All twelve resource sets retain 151 matching keys and placeholders. Final French/Hungarian/Polish/Spanish terminology corrections were rebuilt and visually checked.
- Seven native 1080 x 1920 EN/SK screenshots were visually verified. The demo meter screenshot used a local simulator and sent zero SET commands.

## Public release APK

The publisher-signed, non-debuggable 0.4.0 APK was installed cleanly and cold-started on the dedicated Android emulator. Version code 5, target API 36, disabled debugging, mixer/settings screens, author credit and contact were verified. No app crash was observed. This installation check did not connect to a mixer or change Wi-Fi, faders or mute.

## Physical hardware checked

On a connected Android 16 phone with an XR18, a verified mixer response saved the network identity; a network request connected to the mixer, loaded master state and received stereo meter packets. Releasing the request allowed Android to return to the previous saved Wi-Fi. This check did not change a physical fader or mute.

## Coverage still needed

More phones, Android/OEM versions, router configurations and mixer firmware; long-running overlays, screen-off behavior and repeated radio transitions. Native-speaker proofreading of the wider translations is also welcome.

## Running tests

See [BUILDING.md](BUILDING.md). ADB scripts are restricted to the dedicated test emulator `emulator-5584`; they must not operate on a physical phone. The fake mixer uses loopback and the emulator host alias `10.0.2.2`. Ensure UDP port 10024 is available before starting a simulator.
