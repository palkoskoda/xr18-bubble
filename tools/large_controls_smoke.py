"""Test seven overlay sizes, independent fonts and drag-to-dismiss on emulator-5584.

Requires an installed current debug APK; never installs or uninstalls the app.
Builds its own separate instrumentation APK, restores app preferences and device
display/font overrides, and saves unchanged native screenshots under build/.
No mixer session, network request, radio change, or mixer SET is used.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build" / "large-controls-instrumentation"
TARGET = "sk.palko.xr18bubble"
TEST = TARGET + ".largecontrolstest"
SHOTS = ("maximum-mixed-top", "maximum-mixed-bottom", "maximum-mute-only", "dismiss-target-armed",
         "small-font300-top", "small-font300-bottom", "maximum-font300-top", "maximum-font300-bottom")


def command(args: list[str], timeout: int = 120) -> str:
    result = subprocess.run(args, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            timeout=timeout, encoding="utf-8", errors="replace")
    if result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {args[0]}\n{result.stdout}")
    return result.stdout


def build(sdk: Path) -> Path:
    tools = sdk / "build-tools" / "35.0.0"
    android = sdk / "platforms" / "android-36" / "android.jar"
    for folder in (OUT, OUT / "classes", OUT / "dex", OUT / "temp"):
        folder.mkdir(parents=True, exist_ok=True)
    manifest = OUT / "AndroidManifest.xml"
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{TEST}">
    <uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36" />
    <application android:label="XR18 large controls test" android:debuggable="true" />
    <instrumentation android:name="{TEST}.LargeControlsInstrumentation" android:targetPackage="{TARGET}" />
</manifest>
''', encoding="utf-8")
    base = OUT / "base.apk"
    command([str(tools / "aapt2.exe"), "link", "-I", str(android), "--manifest",
             str(manifest.relative_to(ROOT)), "-o", str(base.relative_to(ROOT))])
    command(["javac", "-encoding", "UTF-8", "--release", "8", "-classpath", str(android),
             "-d", str(OUT / "classes"), "tests/android/LargeControlsInstrumentation.java"])
    classes = OUT / "classes.jar"
    with zipfile.ZipFile(classes, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for item in (OUT / "classes").rglob("*.class"):
            archive.write(item, item.relative_to(OUT / "classes").as_posix())
    command([str(tools / "d8.bat"), "--lib", str(android), "--min-api", "29",
             "--output", str(OUT / "dex"), str(classes)])
    unsigned = OUT / "unsigned.apk"
    shutil.copyfile(base, unsigned)
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(OUT / "dex" / "classes.dex", "classes.dex")
    aligned = OUT / "aligned.apk"
    command([str(tools / "zipalign.exe"), "-f", "4", str(unsigned), str(aligned)])
    signed = OUT / "large-controls-test.apk"
    command([str(tools / "apksigner.bat"), "sign", "--ks", str(ROOT / "build" / "debug.keystore"),
             "--ks-pass", "pass:android", "--out", str(signed), str(aligned)])
    command([str(tools / "apksigner.bat"), "verify", str(signed)])
    print("Separate large controls instrumentation APK built and verified", flush=True)
    return signed


def run(adb: str, serial: str, apk: Path, drawing_only: bool = False) -> None:
    if serial != "emulator-5584":
        raise ValueError("Only dedicated emulator-5584 is allowed")

    def device(*args: str, timeout: int = 120) -> str:
        return command([adb, "-s", serial, *args], timeout)

    assert device("get-state").strip() == "device", "Emulator unavailable"
    assert device("shell", "getprop ro.kernel.qemu").strip() == "1", "Not an emulator"
    assert device("shell", f"run-as {TARGET} pwd").strip().endswith(TARGET), "Debug target required"
    size_before = device("shell", "wm size")
    override = re.search(r"Override size: (\d+x\d+)", size_before)
    restore_size = override.group(1) if override else "reset"
    font_before = device("shell", "settings get system font_scale").strip()
    configurations = (("normal", None, "1.0"), ("small-large-font", "320x640", "1.6"),
                      ("landscape-large-font", "640x320", "1.6"))
    device("shell", f"appops set {TARGET} SYSTEM_ALERT_WINDOW allow")
    device("shell", f"pm grant {TARGET} android.permission.POST_NOTIFICATIONS")
    device("install", "-r", str(apk))
    try:
        for name, size, font in configurations:
            result_name = ("drawing-" if drawing_only else "") + name
            device("shell", f"am force-stop {TARGET}")
            device("shell", "wm", "size", size or restore_size)
            device("shell", "settings", "put", "system", "font_scale", font)
            instrumentation_args = ["-e", "drawing_only", "true"] if drawing_only else []
            output = device("shell", "am", "instrument", "-w", "-r", *instrumentation_args,
                            TEST + "/.LargeControlsInstrumentation", timeout=300)
            (OUT / (result_name + "-result.txt")).write_text(output, encoding="utf-8")
            print(result_name + ":\n" + output, flush=True)
            for shot in (("small-font300-cap", "maximum-font300-cap") if drawing_only else SHOTS):
                raw = subprocess.run([adb, "-s", serial, "exec-out", "run-as", TARGET,
                                      "cat", "cache/large-controls-" + shot + ".png"],
                                     stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
                if raw.returncode == 0 and raw.stdout.startswith(b"\x89PNG"):
                    (OUT / (result_name + "-" + shot + ".png")).write_bytes(raw.stdout)
            assert "LargeControlsInstrumentation passed:" in output and "INSTRUMENTATION_CODE: -1" in output, "Instrumentation failed; inspect result log"
    finally:
        device("shell", f"am force-stop {TARGET}")
        device("shell", "wm", "size", restore_size)
        if font_before in ("null", ""):
            device("shell", "settings", "delete", "system", "font_scale")
        else:
            device("shell", "settings", "put", "system", "font_scale", font_before)
        device("uninstall", TEST)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    parser.add_argument("--adb", help="Defaults to platform-tools/adb.exe inside --sdk")
    parser.add_argument("--serial", default="emulator-5584", choices=["emulator-5584"])
    parser.add_argument("--build-only", action="store_true")
    parser.add_argument("--drawing-only", action="store_true", help="Focused canvas/cap validation; preserve full regression logs")
    args = parser.parse_args()
    if args.sdk is None:
        parser.error("Pass --sdk or set ANDROID_HOME/ANDROID_SDK_ROOT")
    apk = build(args.sdk)
    if not args.build_only:
        run(args.adb or str(args.sdk / "platform-tools" / "adb.exe"), args.serial, apk, args.drawing_only)


if __name__ == "__main__":
    main()
