# XR18 V2 discovery and connection fix

Some XR18 hardware reports `XR18V2`, rather than `XR18`, in its OSC `/xinfo` identity. The original exact model allowlist rejected that response in both discovery and the connection handshake. This made the mixer invisible even when its network and UDP replies were working.

The client now explicitly accepts `XR18V2` alongside `XR18`, `X18` and `MR18`. Other models remain rejected. Fresh control synchronization, timeout handling and write guards are unchanged.

Verified on 15 October 2026 with a physical XR18 V2 running firmware 1.23 and a Xiaomi phone on a shared Wi-Fi network: discovery returned the mixer, connection completed, Main LR and DCA values synchronized, and the current Wi-Fi was remembered. No fader or mute commands were sent during this hardware check. Access-point mode has not been retested with this repair.

Regression checks exercise the actual `MixerClient.identity` parser with a captured OSC response, the original supported identities, unsupported near matches and malformed arguments:

```powershell
$env:ANDROID_JAR = 'D:\AndroidLab\sdk\platforms\android-36\android.jar'
python tools/test_mixer_identity.py
```

Result: 16 checks passed; this regression test uses no device or network.
