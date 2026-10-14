"""Build and run a separate reflection instrumentation APK on emulator-5584 only.

No app test hooks, real mixer traffic, network requests or radio switching.
Requires an installed current debug build and the existing project debug key.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build" / "return-wifi-instrumentation"
TARGET = "sk.palko.xr18bubble"
TEST = TARGET + ".returnwifitest"


def command(args: list[str], timeout: int = 90) -> str:
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
    base = OUT / "base.apk"
    command([str(tools / "aapt2.exe"), "link", "-I", str(android), "--manifest",
             "tests/android/AndroidManifest.xml", "-o", str(base.relative_to(ROOT))])
    command(["javac", "-encoding", "UTF-8", "--release", "8", "-classpath", str(android),
             "-d", str(OUT / "classes"), "tests/android/ReturnWifiInstrumentation.java"])
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
    signed = OUT / "return-wifi-test.apk"
    command([str(tools / "apksigner.bat"), "sign", "--ks", str(ROOT / "build" / "debug.keystore"),
             "--ks-pass", "pass:android", "--out", str(signed), str(aligned)])
    command([str(tools / "apksigner.bat"), "verify", str(signed)])
    print("Separate return Wi-Fi instrumentation APK built and verified", flush=True)
    return signed


def run(adb: str, serial: str, apk: Path) -> None:
    if serial != "emulator-5584":
        raise ValueError("Only dedicated emulator-5584 is allowed")
    def device(*args: str, timeout: int = 90) -> str:
        return command([adb, "-s", serial, *args], timeout)
    assert device("get-state").strip() == "device", "Emulator unavailable"
    assert device("shell", "getprop ro.kernel.qemu").strip() == "1", "Not an emulator"
    assert device("shell", f"run-as {TARGET} pwd").strip().endswith(TARGET), "Debug target required"
    sizes = re.findall(r"(\d+)x(\d+)", device("shell", "wm size"))
    assert sizes and sizes[-1] == ("432", "960"), "Dedicated test geometry required"
    device("shell", f"appops set {TARGET} SYSTEM_ALERT_WINDOW allow")
    device("shell", f"pm grant {TARGET} android.permission.POST_NOTIFICATIONS")
    device("shell", f"am force-stop {TARGET}")
    device("install", "-r", str(apk))
    try:
        output = device("shell", "am", "instrument", "-w", "-r",
                        TEST + "/.ReturnWifiInstrumentation", timeout=90)
        (OUT / "instrumentation-result.txt").write_text(output, encoding="utf-8")
        print(output, flush=True)
        assert "ReturnWifiInstrumentation passed:" in output and "INSTRUMENTATION_CODE: -1" in output, "Instrumentation failed; inspect result log"
    finally:
        device("shell", f"am force-stop {TARGET}")
        device("uninstall", TEST)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", default=r"D:\AndroidLab\sdk", type=Path)
    parser.add_argument("--adb", default=r"D:\AndroidLab\sdk\platform-tools\adb.exe")
    parser.add_argument("--serial", default="emulator-5584", choices=["emulator-5584"])
    parser.add_argument("--build-only", action="store_true")
    args = parser.parse_args()
    apk = build(args.sdk)
    if not args.build_only:
        run(args.adb, args.serial, apk)


if __name__ == "__main__":
    main()
