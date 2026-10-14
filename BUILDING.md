# Building XR18 Bubble

XR18 Bubble is a native Java Android application. The checked-in PowerShell build
script uses the Android SDK directly; Android Studio and Gradle are not required.
The application supports Android 10 / API 29 and later and targets API 36.

## Requirements

- Windows with PowerShell, with `java`, `javac`, `jar`, and `keytool` on `PATH`.
- JDK **17 or later**, required by the Android build tools. App Java sources are
  compiled with `--release 8`.
- Android SDK **Platform 36** and **Build-Tools 35.0.0**.
- SDK Platform-Tools and an Android emulator only for the optional ADB tests.
- Python 3 for the local mixer simulator and ADB test drivers. Pillow is needed
  only by image-generating or screenshot-inspecting helper scripts, not to build
  the Android application or run the pure Java tests.
- Google **bundletool 1.18.3** only when building an Android App Bundle (AAB).

Install SDK components using Android Studio's SDK Manager or the official SDK
command-line tools. Build-tool binaries, bundletool, signing keys, generated
artifacts, and personal mixer profiles are not included in this repository.

Run commands below from the repository root. Set `ANDROID_HOME` to the SDK
directory, pass `-SdkRoot` explicitly, or use the older `ANDROID_SDK_ROOT`
variable. The script prefers an explicit parameter, then `ANDROID_HOME`, then
`ANDROID_SDK_ROOT`.

## Build a development APK

```powershell
.\build.ps1 -Mode debug -SdkRoot 'C:\Android\sdk'
```

Output: `dist/xr18-bubble-debug.apk`. This APK is debuggable and signed with a
locally generated development key at `build/debug.keystore`. Retaining that key
allows updates to development builds produced on this checkout. It is not a
publisher signing key and must not be committed.

## Build an unsigned release APK

```powershell
.\build.ps1 -Mode release-apk -SdkRoot 'C:\Android\sdk'
```

Output: `dist/xr18-bubble-release-unsigned.apk`. This APK is aligned and
non-debuggable. It cannot be installed until the publisher signs it. This mode
does not generate or use a production signing key.

Before publishing an APK through GitHub Releases or another distribution
channel, sign it using Android SDK `apksigner` and a publisher-controlled key,
then verify its signature. Keep the private key and its backups outside the
repository. Updates must use the same application ID and signing key; losing
the key prevents ordinary updates for existing installations.

**Development and publisher-signed APKs normally have different signatures.**
Android will refuse to install one as an update over the other. Switching from
a development installation can require uninstalling it first, which removes its
saved mixer, Wi-Fi, and control preferences. Record those settings before making
that switch. Do not overwrite or delete a development installation as part of a
routine build.

## Build an unsigned Android App Bundle

Download `bundletool-all-1.18.3.jar` from Google's official
[bundletool 1.18.3 release](https://github.com/google/bundletool/releases/tag/1.18.3).
Place it at `build/tools/bundletool-all-1.18.3.jar`, or pass its path with
`-BundletoolJar`.

```powershell
.\build.ps1 -Mode release -SdkRoot 'C:\Android\sdk'
```

Output: `dist/xr18-bubble-release-unsigned.aab`. The script validates the bundle
with bundletool. The bundle configuration keeps every supported language
available offline. The AAB remains unsigned; Google Play submission requires
publisher-controlled upload signing and the relevant Play Console setup.
Bundletool is not needed for either APK mode.

Builds share the `build/apk` workspace. Run one build at a time. Generated paths
stay inside `build/`, and Java temporary files use that workspace to support
Windows checkouts whose paths contain non-ASCII characters.

## Pure Java and simulator tests

These tests require no Android device or physical mixer:

```powershell
New-Item -ItemType Directory -Force -Path build/tests | Out-Null
javac -encoding UTF-8 --release 8 -d build/tests `
  app/src/main/java/sk/palko/xr18bubble/Osc.java `
  app/src/main/java/sk/palko/xr18bubble/XAirProtocol.java `
  app/src/main/java/sk/palko/xr18bubble/PanelSizing.java `
  app/src/main/java/sk/palko/xr18bubble/MeterProtocol.java `
  tests/ProtocolTest.java tests/PanelSizingTest.java tests/MeterProtocolTest.java
java -cp build/tests ProtocolTest
java -cp build/tests PanelSizingTest
java -cp build/tests MeterProtocolTest
python tools/fake_mixer.py --self-test
```

The expected Java results are **1,209 protocol checks**, **6,492 panel sizing
checks**, and **5,131 meter protocol checks**. The simulator self-test exercises
local UDP handshake, control messages, and meter encoding; it does not connect
to a real mixer.

## Optional dedicated-emulator tests

Use a disposable Android emulator with serial **`emulator-5584`**, a 432 × 960
display, and density 160 (mdpi). The drivers deliberately reject physical-device
serials. They write test preferences and operate the UI, so do not point them at
an emulator containing settings you want to preserve. Run tests sequentially and
keep other automation away from this emulator. Port 10024 on the host must be
free. Python and Pillow are needed by the screenshot-based drivers.

Build the debug APK first. With the dedicated emulator running:

```powershell
$adb = 'C:\Android\sdk\platform-tools\adb.exe'
& $adb -s emulator-5584 shell wm size 432x960
& $adb -s emulator-5584 shell wm density 160
& $adb -s emulator-5584 install -r dist/xr18-bubble-debug.apk
& $adb -s emulator-5584 shell input keyevent KEYCODE_HOME
python tools/adb_smoke_v3.py --adb $adb --serial emulator-5584
python tools/return_wifi_smoke.py --sdk 'C:\Android\sdk' --adb $adb --serial emulator-5584
python tools/large_controls_smoke.py --sdk 'C:\Android\sdk' --adb $adb --serial emulator-5584
```

The meter/transport smoke test starts a local fake mixer and checks connection,
state synchronization, meter freshness, subscription lifecycle, and unexpected
control writes. The Wi-Fi return instrumentation runs 49 checks of dialog,
button, and lifecycle behavior using simulated network callbacks. The large-control
instrumentation checks sizing, scrolling and drag-to-stop without a mixer, in
three configurations: normal, 320 × 640 with system text scale 1.6, and 640 × 320
with text scale 1.6. It restores the previous screen-size override and system
text scale in its cleanup and removes its separate instrumentation package.
These tests
do not establish real radio switching or compatibility with every mixer firmware.
Physical Wi-Fi and mixer behavior still needs testing on the intended hardware.
