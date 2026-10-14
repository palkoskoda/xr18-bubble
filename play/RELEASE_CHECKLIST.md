# XR18 Bubble Google Play release checklist

Google Play is now a planned distribution channel. Version 0.4.1 (version code 6) has a validated, non-debuggable unsigned App Bundle and a publisher-signed public APK. The Play upload bundle is not signed or submitted, and Play Console account setup and approval are still pending.

Current distribution is the public beta on [GitHub Releases](https://github.com/palkoskoda/xr18-bubble/releases/tag/v0.4.1-beta). This checklist tracks preparation for the first Google Play submission.

## Completed in repository

- [x] English and Slovak store descriptions highlight seven sizes up to Maximum (4XL), people who find small controls hard to read or touch, and using a tablet/other controller for the full mix while keeping chosen controls on the phone. Ready-to-paste UTF-8 title, short/full description and release notes are in `play/listings/`; `python tools/check_play_listings.py` checks limits and document parity.
- [x] Privacy-policy draft with local-only storage, Keystore-protected password, unencrypted local mixer UDP, and no developer collection claims.
- [x] Permission and foreground-service rationale mapped to the current manifest.
- [x] Official Google Play/Android references recorded below.
- [x] Native build script supports separate debug APK and unsigned release AAB paths using SDK 36 and pinned bundletool 1.18.3.
- [x] Original 512×512 icon, SVG source, and 1024×500 feature graphic generated in `play/assets/` with the reproducible Pillow script.
- [x] Release bundle config disables LANGUAGE splits so all twelve offline in-app translations ship together; density/ABI split behavior remains available to downstream delivery.
- [x] Source and built artifacts target/compile Android 16 (API 36), version 0.4.1 / code 6; release manifest is non-debuggable. The current unsigned AAB was rebuilt and validated with bundletool 1.18.3 on 14 October 2026.
- [x] User-facing strings in English, Slovak, Czech, German, Polish, Hungarian, French, Spanish, Italian, Portuguese, Dutch and Ukrainian; explicit overlay opt-in, system light/dark theme and language/size settings implemented.
- [x] Optional shortcut can launch a user-selected installed mixer app (including Mixing Station) without requesting `QUERY_ALL_PACKAGES`.
- [x] Product credit prepared as “Developed by Pavol Škoda”; the Play developer account's legal identity still needs verification before submission.
- [x] Distribution plan is free, with no ads, subscriptions, in-app purchases, accounts, or analytics in the current release design.
- [x] System, light, and dark theme behavior passed native ADB checks; Spanish language selection passed, and all twelve locale resource sets have matching keys and placeholders.
- [x] Shortcut search, selection, launch, and persistence passed native ADB checks; the shortcut is configurable from the refresh area.
- [x] Version 0.4.1 smoke coverage passed for live meters, stale data handling, reconnect behavior and zero unexpected SET commands; return-Wi-Fi passed 49 checks. Large-control and drag-to-stop instrumentation passed 516 checks across normal, narrow and landscape layouts, including system font scale 1.6. See `TESTING.md` for scope and physical-hardware limits.

- [x] Seven existing native 1080×1920 EN/SK screenshots are captured and visually verified, including a floating panel with simulated live meters and zero SET commands. Current large-control/drag screenshots were saved for test QA; additional store-ready captures are still listed below.

## Required before a publisher can submit

- [ ] Confirm the legal publisher name, effective date, and public privacy-policy URL. The support email is set to `pavolskoda@azet.sk` and the user-authorized product credit is “Pavol Škoda”. Verify the Play account legal identity separately.
- [ ] Create the Play Console app and reserve/confirm package `sk.palko.xr18bubble`.
- [ ] At Play App Signing enrollment, provide the existing publisher app-signing key if GitHub and Play APKs should update the same installation. Follow Play Console's current encrypted-key import flow; do not accept a different Google-generated distribution key for that continuity plan. Compare the Play app-signing certificate against the public certificate SHA-256 `5b60b0171fefe5bb1e619bfbbe5f3f8ffc74d00a3adc2401cfc64e8e5a7220af`. This is a public fingerprint, not a private key.
- [ ] Finish hardware/OS coverage beyond the verified Android 15 emulator and connected Android 16 phone.
- [ ] Produce a non-debuggable signed `.aab` with a unique version code and release version name.
- [ ] Generate/protect and register a separate upload key for signing the AAB. Distinguish the upload certificate from the app-signing certificate used on APKs delivered to users. Existing publisher credentials remain private outside this repository; no key was generated or exported in this preparation.
- [ ] Complete App content declarations: Data safety, privacy policy, ads (none), target audience/content rating, and special-use foreground-service declaration.
- [ ] Complete the overlay permission and notification disclosures in the app and verify the user-started service behavior.
- [ ] Upload `play/assets/xr18-bubble-icon-512.png`, `play/assets/xr18-bubble-feature-1024x500.png`, and the prepared en-US/sk-SK phone screenshots. The seven native 1080×1920 screenshots passed visual verification; the XR18 Demo panel uses a local simulator. Confirm all assets are original or licensed and contain no third-party marks.
- [ ] Add current EN/SK store screenshots of a large mute-only panel, a large fader with mute, the seven size choices and drag-to-stop. Follow the screenshot brief in `STORE_LISTING.md`; do not claim the test QA screenshots establish tablet hardware coverage.
- [ ] Test the saved-profile flow on the actual target phone: current Wi‑Fi → preferred mixer Wi‑Fi → selected mixer → state sync → fader/mute change. Record any Android/OEM confirmation step.
- [ ] Test with a real XR18 on the intended firmware and network. Transport and UI tests use a simulator. A connection, master state and stereo meters were also checked on the connected Android 16 phone with the user’s XR18; wider hardware/firmware coverage is still required.
- [ ] Run internal testing, then closed testing if required by the account.
- [ ] For a new personal developer account created after 13 November 2023, keep at least 12 testers opted in continuously for 14 days and apply for production access.
- [ ] Complete developer identity/account verification and provide a real support contact.
- [ ] Upload the AAB to the chosen testing track and resolve Play pre-launch/review warnings before production.

## Local build commands

The current source manifest carries version `0.4.1`, code `6`, target/compile API 36 and `android:debuggable="false"`. Confirm the next unused code in Play Console before a real upload. A subsequent update for existing public code-6 installations should use a higher code. The script changes only the generated debug manifest copy to `android:debuggable="true"`.

```powershell
.\build.ps1 -Mode debug -SdkRoot 'C:\Android\sdk'
.\build.ps1 -Mode release -SdkRoot 'C:\Android\sdk' -BundletoolJar 'build\tools\bundletool-all-1.18.3.jar'
```

The debug output is `dist/xr18-bubble-debug.apk` and uses a local development key in `build/debug.keystore`. The release output is `dist/xr18-bubble-release-unsigned.aab`; bundletool 1.18.3 validates it locally. No production signing key is generated or used by this script. Supply the registered upload key through a separate publisher-controlled signing step before Play upload. The unsigned bundle is a preparation artifact and cannot be uploaded as a signed release.

Release builds pass `tools/bundle-config.json` to bundletool. Its `LANGUAGE` dimension has `negate: true`, so all twelve localized resources remain together for the offline in-app language chooser. The configuration does not disable density or ABI delivery dimensions.

After the publisher has configured Play App Signing, sign a copy of the unsigned bundle without placing secrets in the repository or printing them:

```powershell
$env:XR18_UPLOAD_KEYSTORE = 'C:\secure\xr18-upload.jks'
$env:XR18_UPLOAD_ALIAS = 'xr18-upload'
$env:XR18_UPLOAD_STOREPASS = [System.Net.NetworkCredential]::new('',(Read-Host 'Keystore password' -AsSecureString)).Password
$env:XR18_UPLOAD_KEYPASS = [System.Net.NetworkCredential]::new('',(Read-Host 'Key password' -AsSecureString)).Password
jarsigner -keystore $env:XR18_UPLOAD_KEYSTORE -storepass:env XR18_UPLOAD_STOREPASS -keypass:env XR18_UPLOAD_KEYPASS -signedjar dist/xr18-bubble-release-signed.aab dist/xr18-bubble-release-unsigned.aab $env:XR18_UPLOAD_ALIAS
# Clear secret environment values immediately after the command.
Remove-Item Env:XR18_UPLOAD_STOREPASS,Env:XR18_UPLOAD_KEYPASS
```

Use the exact keystore/password handling supported by the publisher's JDK; never commit the keystore or passwords. Re-run bundletool validation on the signed copy before upload.

## Official references checked 14 October 2026

- [Create and set up an app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en): title 30 characters, short description 80 and full description 4,000.
- [Developer registration](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en): the developer-account registration fee is a one-time US$25 payment.
- [Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-EN): new apps and updates must target Android 16/API 36 or higher from 31 August 2026; an extension may be available until 1 November 2026. Confirm the Play Console status for this app.
- [Foreground service types](https://developer.android.com/about/versions/14/changes/fgs-types-required): Android 14+ requires a declared type and matching permission; `specialUse` requires `FOREGROUND_SERVICE_SPECIAL_USE` and a manifest use-case property.
- [App signing](https://developer.android.com/studio/publish/app-signing): sign the AAB with an upload key; Play App Signing signs distribution APKs.
- [Command-line App Bundle configuration](https://developer.android.com/build/building-cmdline): `BundleConfig.json` accepts `optimizations.splitsConfig.splitDimension`; setting `LANGUAGE` with `negate: true` keeps all language resources in the base/feature APK.
- [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en-EN): developers must accurately declare collection/sharing and provide a privacy-policy link for published apps; verify the final dependency graph.
- [New personal-account testing](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en-EN): post-13 November 2023 personal accounts require a closed test with at least 12 continuously opted-in testers for 14 days before production access.

The materials above are release preparation only. They do not mean the app is Play-ready, approved, or published.

## Published GitHub Pages destination

Account: `palkoskoda`. Public repository: `https://github.com/palkoskoda/xr18-bubble`. Website: `https://palkoskoda.github.io/xr18-bubble/`. Public privacy policy: `https://palkoskoda.github.io/xr18-bubble/privacy.html`. HTTPS and the served source/assets were verified after deployment. Source lives in `site/` on main and publishes from the root of gh-pages.
