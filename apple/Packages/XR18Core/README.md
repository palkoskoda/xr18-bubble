# XR18Core

Foundation-only Swift 5.9 package for the Apple client. Supports iOS 17 and macOS 13 or newer. It has no network, Wi-Fi, keychain, SwiftUI, or device dependencies, so its protocol tests can run on a macOS CI runner without a mixer.

Run `swift test --package-path apple/Packages/XR18Core` from the repository root on a Swift-enabled host.

## Transport integration

1. Own `SessionSafety` on one serial executor. Open a UDP connection to the **explicitly chosen unicast host**, port `XAir.port` (10024). Filter all incoming packets to that endpoint; OSC has no authentication or request IDs.
2. Call `begin(selectedIDs:now:)` with a monotonic uptime timestamp and capture the returned UUID in every callback. Send `OSCCodec.enquiry("/xinfo")`.
3. Decode each datagram atomically with `OSCCodec.decode`. Feed messages to `receive(_:generation:now:)`. Only a supported `/xinfo` identity (`XR18`, `X18`, `MR18`) permits state replies to enter the cache.
4. After the handshake, send `/xremote` every four seconds and query the selected `requiredPaths` every second using address-only enquiries. Every selected fader and mute must have a current validated reply before `isReady(at:)` becomes true. Faders must be finite normalized float32 values in 0...1; mute switches must be int32 0 or 1.
5. On the transport executor, evaluate `command(path:value:generation:now:)` immediately before a user-initiated SET. A nil result means discard the request permanently. Send accepted messages exactly once, then enquire the same path. Never persist, retry, or replay SET commands; only a device reply updates confirmed state.
6. Call `expire(at:)` periodically. A stale connection clears identity, values, and timestamps and rotates the generation. Cancel its socket/callbacks; recovery requires a fresh socket and `begin`. Changing Wi-Fi, backgrounding the app, or disconnecting should likewise cancel transport and call `disconnect`.
7. Subscribe with `MeterCodec.subscription` only while the panel is visible. Renew after four seconds and clear meter displays after `MeterCodec.freshness` (one second) without a valid frame. Meter packets never extend connection readiness or authorize a SET.

`MixerProfile` contains no password field. Store credentials in the platform keychain, outside exported profiles. The catalog has 31 controls, including four independently addressed mute groups. `master` is the Apple profile ID; lookup also accepts Android's `main` alias.

## Protocol reference

This is an original Swift port of this repository's independently implemented Android protocol classes (`XAirProtocol`, `Osc`, `MeterProtocol`, and the safety policy of `MixerClient`), not of Mixing Station. Their reference protocol is the MUSIC Group X AIR Mixer Series Remote Control Protocol, firmware 1.11+. Meter blobs contain a little-endian uint32 count followed by signed little-endian int16 values in dB/256. Bank 1 has 40 values; bank 8 has four DCA values. This differs from X32 float32 linear meters.

The tests cover wire vectors, UTF-8 and zero padding, bundle flattening and resource limits, truncation and atomic rejection, all catalog paths and mute polarity, taper breakpoints and 10,001 round trips, malformed meter frames and routing, profile credential separation, supported identities, selected-state readiness, malformed state, stale timeout, connection generations, and rejection of stale writes. Passing these tests does not establish live hardware or Wi-Fi behavior.
