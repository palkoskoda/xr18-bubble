# App access and foreground service — reviewer notes

Prepared for XR18 Bubble 0.4.1 (version code 6). These are submission drafts, not a statement of Play approval. A publisher-signed APK is already distributed through [GitHub Releases](https://github.com/palkoskoda/xr18-bubble/releases/tag/v0.4.1-beta); Google Play publication is being prepared separately.

## App access

No account, login, subscription, purchase or test credentials are required. The app is a local remote for a compatible Behringer XR18/X18 or Midas MR18 mixer on Wi-Fi. Live control requires that hardware and network; this dependency should be disclosed in the listing and reviewer instructions.

Without a mixer, reviewers can inspect the mixer setup, control selection, app shortcut, appearance, language, support and permission disclosures. Live faders remain disabled until the app verifies a compatible mixer and synchronizes all selected values. The production app has no hidden simulator, bypass login or remote developer backend.

The intended workflow is to prepare the complete mix on a tablet, computer or another mixer controller, then keep only frequently used controls on the phone. Both controllers independently communicate with the same mixer over the local network. XR18 Bubble synchronizes live mixer values; it does not transfer app profiles or settings between devices and does not replace a complete mixing application.

There are seven control sizes, from Small to Maximum (4XL). The larger sizes enlarge labels, numeric values, faders, meters and mute buttons. Users can select just the master, a DCA, a channel or a mute group they need, with horizontal and vertical scrolling when the selection exceeds the screen. This can help people who find small phone controls difficult to read or use. No certified accessibility or medical benefit is claimed.

The development repository has a local-only protocol simulator used for automated checks. It is not included in the release app. Any screenshots or demonstration captured with that simulator should be described as a demonstration, not claimed as an additional real-hardware test.

## Foreground service declaration draft

- Type: `specialUse`, protected by `FOREGROUND_SERVICE_SPECIAL_USE`.
- User-facing feature: a user-started floating mixer controller that remains available while the musician uses another app.
- Start: the user explicitly enables display-over-other-apps access and chooses Show bubble or Connect.
- Visibility: an ongoing notification identifies the feature and provides Stop; the floating bubble opens a panel with current connection state.
- Task: maintain a local UDP mixer session and optional user-requested mixer Wi-Fi, synchronize selected fader/mute values, and receive meter data while the panel is open.
- Why continued execution matters: the user needs immediate access to a live, synchronized remote during a performance. Stale controls are disabled and disconnected commands are not replayed.
- Stop: Hide bubble in settings, Stop in the ongoing notification, or dragging the collapsed bubble onto the bottom × target ends the service and releases its network request. Releasing the bubble elsewhere only repositions it; closing the expanded panel leaves the service running.
- Meter subscriptions are renewed only while the panel is expanded. There is no advertising, remote telemetry, upload or background location collection.

Use the final implementation to answer Play Console's exact fields. Eligibility is decided by Google's review; a matching manifest declaration alone is not approval.

## Suggested declaration video

Record the final build on a real supported phone and mixer. Avoid showing Wi-Fi passwords, private notification content or other people's personal data.

1. Open settings, show the author/support details and identify the app version.
2. Start the bubble through the visible user action and show the ongoing notification.
3. Open a different ordinary app, then tap the bubble to show the live controls above it.
4. Show synchronization before controls enable and a signal meter responding to actual signal. If safe in the test setup, demonstrate one mute or fader change.
5. In control selection, choose a small set of frequently used controls. Select Maximum (4XL), reopen the panel and demonstrate the enlarged labels, fader, meter and mute button. Show scrolling if the controls exceed the screen.
6. If a second controller is available, change a value there and show the corresponding live value updating in XR18 Bubble. Explain that the devices control the same hardware and do not exchange app profiles.
7. Collapse and reopen the panel. Show the return-Wi-Fi option when a network was actually changed.
8. Drag the collapsed bubble to show the bottom × target, then release elsewhere to demonstrate repositioning. Drag it onto × and release; show that the overlay and ongoing service notification disappear.
9. Restart through the visible app action and demonstrate Stop in the ongoing notification as the alternative way to end the service.

Attach an accessible video URL only after the video is prepared and reviewed. No video URL is invented in these materials.
