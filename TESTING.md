# Testing XR18 Bubble

## 0.4.1 completed verification

- Pure Java: 1,209 OSC/protocol, 6,492 panel sizing and 5,131 meter decoding checks passed; local fake-mixer self-test passed.
- Real Android overlay instrumentation: 516 checks passed across 432 × 960 with font scale 1.0, 320 × 640 with font scale 1.6, and 640 × 320 with font scale 1.6. Covers seven live layouts, scaled text and touch areas, horizontal/vertical reachability, standalone mute, relative fader movement, drag threshold, cancel, edge snapping, saved position, configuration/rebuild cleanup and dropping onto the cross to stop the service. No mixer session or SET commands were used. Twelve unchanged native screenshots were saved and inspected.
- Dedicated-emulator meter/transport smoke passed again: verified identity and Wi-Fi learning, partial synchronization guard, live and stale meters, subscriptions on collapse/reopen, and zero unexpected SET commands.
- Return-Wi-Fi instrumentation passed all 49 regression checks; it uses simulated network callbacks and does not test physical radio association.
- All twelve language resource sets contain 156 matching keys with matching placeholders.
- The non-debuggable APK is version 0.4.1 / code 6, min API 29, target API 36. Its publisher signature was verified and matches the previous public beta.
- Publisher-signed 0.4.0 was updated in place to 0.4.1 on the dedicated emulator. Slovak language, Dark theme and Large control size survived; the actual settings dialog showed all seven choices and Maximum could be selected. This upgrade check did not configure or connect to a physical mixer or Wi-Fi network.
- English/Slovak website and privacy pages passed twelve responsive checks at desktop, 375 px and 320 px widths, including image loading, links and language switching.

The emulator checks do not establish compatibility with every phone, Android font setting, mixer firmware or physical Wi-Fi network.

## 0.4.1 manual release checklist

Version 0.4.1 (version code 6) adds seven control sizes and a drag-to-stop target. The checklist below describes broader manual coverage; completed automated checks are recorded above. Results from the previous beta are recorded separately below.

- Select each control size, reopen the panel and restart the app; confirm that the selection persists. Bubble size must still offer only Small, Medium and Large.
- Try a mute group alone, a channel with volume only, a channel with volume and mute, and multiple mixed controls. Check labels, numeric values, fader handles, meter segments and mute states at every size.
- On a narrow portrait screen, a short landscape screen and with enlarged Android system text, select Maximum (4XL). Reach every control by horizontal and vertical scrolling; check that mute buttons, panel actions and the Wi-Fi return action remain accessible. Confirm that dragging a fader still adjusts its level instead of scrolling the panel.
- Drag the collapsed bubble a short distance. Confirm that the bottom **×** target appears only while dragging. Release elsewhere: the target disappears, the bubble remains running and snaps to an edge.
- Drag the collapsed bubble over the **×** target. Confirm the target highlights before release. Release over it: the bubble, target and foreground-service notification disappear. Open the app and show the bubble again.
- Cancel a drag, rotate the screen during a drag, and reload or stop the service. Confirm that no target window remains and that a cancelled drag does not stop the service.
- Update a publisher-signed 0.4.0 installation to the publisher-signed 0.4.1 APK without uninstalling it. Confirm that mixer, Wi-Fi, language, theme and control preferences survive.
- Run protocol, panel sizing, meter and simulator tests, then the dedicated-emulator meter/transport and Wi-Fi return checks. Record the actual results before publishing.

## Previous beta verification (0.4.0)

### Automated coverage

- OSC/protocol tests: 1,209 checks.
- Adaptive panel sizing: 39 checks.
- Real-format meter decoding: 5,131 checks for banks 1 and 8, little-endian values, dB/256 conversion and malformed packets.
- Local fake mixer self-test: discovery, handshake, GET/SET and meter blobs.
- Dedicated Android 15 emulator: verified mixer identity and SSID persistence, changing master/DCA/bus/channel meters, incomplete synchronization disabling all controls, stale meter clearing, subscriptions stopping while collapsed and renewing on reopen, and no unexpected SET commands.
- Return-Wi-Fi instrumentation: 49 checks of actual Android dialog/button handlers using a simulated request lifecycle. Includes Stay -> reopen -> close without another prompt, manual return, Cancel/Return, a new loan and stale-dialog guards. This does not test radio association.
- Native UI checks: system/light/dark themes, language selection, shortcut search/selection/launch/persistence, and all twelve locale renderings. All twelve resource sets retain 151 matching keys and placeholders. Final French/Hungarian/Polish/Spanish terminology corrections were rebuilt and visually checked.
- Seven native 1080 x 1920 EN/SK screenshots were visually verified. The demo meter screenshot used a local simulator and sent zero SET commands.

### Public release APK

The publisher-signed, non-debuggable 0.4.0 APK was installed cleanly and cold-started on the dedicated Android emulator. Version code 5, target API 36, disabled debugging, mixer/settings screens, author credit and contact were verified. No app crash was observed. This installation check did not connect to a mixer or change Wi-Fi, faders or mute.

## Physical hardware checked

On a connected Android 16 phone with an XR18, a verified mixer response saved the network identity; a network request connected to the mixer, loaded master state and received stereo meter packets. Releasing the request allowed Android to return to the previous saved Wi-Fi. This check did not change a physical fader or mute.

## Coverage still needed

More phones, Android/OEM versions, router configurations and mixer firmware; long-running overlays, screen-off behavior and repeated radio transitions. Native-speaker proofreading of the wider translations is also welcome.

## Running tests

See [BUILDING.md](BUILDING.md). ADB scripts are restricted to the dedicated test emulator `emulator-5584`; they must not operate on a physical phone. The fake mixer uses loopback and the emulator host alias `10.0.2.2`. Ensure UDP port 10024 is available before starting a simulator.

## Website

The English and Slovak home/privacy pages were checked at 1280, 375 and 320 CSS pixels. Language switches preserve home/privacy context; local links and all image resources resolve; no horizontal overflow or browser errors were found. Desktop/mobile renders and localized screenshot cards were visually checked. Site assets are original app screenshots, presented without changing their source pixels.
