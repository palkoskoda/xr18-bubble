"""ADB-only Wi-Fi identity / real-format signal-meter tests, dedicated emulator-5584.

Install the current debug APK first. Never installs an APK or contacts a real
mixer. This tests identity persistence and local UDP behavior; emulator Wi-Fi
does not verify the physical phone's radio association to another access point.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from PIL import Image
import adb_smoke_v2 as base
from adb_smoke_v2 import PKG, ROOT, bounds, faders, mute_buttons, nodes, texts

OUT = ROOT / "build" / "smoke-v3"
base.OUT = OUT


class SmokeV3(base.Smoke):
    def __init__(self, adb_path: str, serial: str):
        super().__init__(adb_path, serial)
        self.meter_log = OUT / "meter-events.jsonl"
        self.meter_log.write_text("", encoding="utf-8")

    def start_meter_fake(self) -> None:
        assert self.fake is None
        self.fake_output = (OUT / "fake-mixer.log").open("ab")
        command = [sys.executable, "-u", str(ROOT / "tools" / "fake_mixer.py"),
                   "--host", "127.0.0.1", "--log", str(self.log), "--meters",
                   "--meter-log", str(self.meter_log), "--model", "UNSUPPORTED",
                   "--withhold", "/dca/1/fader"]
        self.fake = subprocess.Popen(command, cwd=ROOT, stdout=self.fake_output, stderr=subprocess.STDOUT)
        time.sleep(.3)
        assert self.fake.poll() is None, "Fake mixer failed to start; inspect fake-mixer.log"

    def preferences(self) -> dict[str, str]:
        tree = ET.fromstring(self.adb("shell", f"run-as {PKG} cat shared_prefs/profile.xml"))
        return {entry.attrib["name"]: entry.text or entry.attrib.get("value", "") for entry in tree}

    def meter_events(self, event: str | None = None) -> list[dict]:
        all_events = [json.loads(line) for line in self.meter_log.read_text(encoding="utf-8").splitlines() if line]
        return [entry for entry in all_events if event is None or entry["event"] == event]

    def screenshot_meter_pixels(self, name: str, tree: ET.Element) -> list[int]:
        self.capture(name)
        picture = Image.open(OUT / f"{name}.png").convert("RGB")
        counts = []
        for node in sorted(faders(tree), key=lambda n: bounds(n)[0]):
            x1, y1, x2, y2 = bounds(node)
            # Signal bars are the last few pixels of each native fader View.
            crop = picture.crop((max(x1, x2 - 8), y1 + 8, x2, y2 - 8))
            pixels = crop.load()
            counts.append(sum(g > r * 1.2 and g > b * 1.1 and g > 25
                              for y in range(crop.height) for x in range(crop.width)
                              for r, g, b in [pixels[x, y]]))
        return counts

    def signal_flow(self) -> None:
        print("v3: Wi-Fi SSID must remain unlearned before verified mixer", flush=True)
        self.start_meter_fake()
        self.seed(selected=("main", "dca1", "ch1", "bus1"), wifi_identity_asked="yes")
        self.open_bubble()
        time.sleep(4.8)  # One unsupported handshake / recovery cycle.
        assert not self.preferences().get("ssid"), self.preferences()
        assert not self.meter_events("subscribe"), "Unsupported peer was subscribed to meters"
        assert not self.events(), "Unsupported peer received a SET"
        self.capture("v3-unverified")

        print("v3: verified mixer learns session Wi-Fi, meters do not unlock partial sync", flush=True)
        self.osc("/__test/model", "XR18")
        tree = self.wait(lambda t: len(faders(t)) == 4 and "Syncing" in texts(t)
                         and len(mute_buttons(t)) == 4
                         and all(n.get("enabled") == "false" for n in mute_buttons(t)),
                         "Partial synchronization did not keep controls disabled")
        deadline = time.monotonic() + 5
        while not self.preferences().get("ssid") and time.monotonic() < deadline:
            time.sleep(.1)
        remembered = self.preferences().get("ssid")
        assert remembered and remembered != "<unknown ssid>", self.preferences()
        (OUT / "wifi-status.txt").write_bytes(self.adb("shell", "cmd wifi status"))
        assert remembered in (OUT / "wifi-status.txt").read_text(encoding="utf-8", errors="replace"), remembered
        assert self.preferences().get("host") == "10.0.2.2", "Wi-Fi learning changed mixer host"
        assert all(n.get("enabled") == "false" for n in faders(tree))
        assert {event["bank"] for event in self.meter_events("frame")} == {"/meters/1", "/meters/8"}
        counts = self.screenshot_meter_pixels("v3-partial-meters", tree)
        assert all(value > 0 for value in counts), ("Missing meter while partial sync", counts)
        assert not self.events(), "Meters or learning sent a mixer SET"

        self.osc("/__test/release")
        tree = self.ready(4)
        counts = self.screenshot_meter_pixels("v3-meters-live-1", tree)
        assert all(value > 0 for value in counts), counts
        changes = False
        for index in range(3):
            time.sleep(.4)
            updated = self.screenshot_meter_pixels(f"v3-meters-live-{index + 2}", tree)
            changes |= updated != counts
        assert changes, "Changing incoming values did not change the native meter drawing"
        assert not self.events(), "Receiving meters generated SET writes"

        print("v3: stale meter clears independently of fader readiness", flush=True)
        self.osc("/__test/meters", 0)
        time.sleep(1.4)
        tree = self.ready(4)
        counts = self.screenshot_meter_pixels("v3-meters-stale", tree)
        assert counts == [0, 0, 0, 0], ("Stale meters remained visible", counts)
        assert all(n.get("enabled") == "true" for n in faders(tree)), "Meter loss disabled fresh fader controls"
        self.osc("/__test/meters", 1)
        time.sleep(.25)
        counts = self.screenshot_meter_pixels("v3-meters-resumed", tree)
        assert all(value > 0 for value in counts), counts

        print("v3: collapsed bubble stops renewing meter subscriptions", flush=True)
        self.click(next(n for n in nodes(tree) if n.get("text") == "×"))
        time.sleep(.3)
        subscriptions = len(self.meter_events("subscribe"))
        time.sleep(4.8)
        assert len(self.meter_events("subscribe")) == subscriptions, "Collapsed overlay renewed meter subscription"
        self.adb("shell", "input tap 404 168")
        tree = self.ready(4)
        assert len(self.meter_events("subscribe")) >= subscriptions + 2, "Reopening did not subscribe both banks"
        counts = self.screenshot_meter_pixels("v3-reopened", tree)
        assert all(value > 0 for value in counts), counts
        assert not self.events(), "Reconnect/reopen unexpectedly sent mixer SET"
        print(f"v3: learned emulator SSID={remembered!r}; meter rendering, expiry, partial-sync guard and collapse verified", flush=True)

    def run(self) -> None:
        assert self.adb("get-state").strip() == b"device", "Dedicated emulator unavailable"
        assert self.adb("shell", "getprop ro.kernel.qemu").strip() == b"1", "Target is not an emulator"
        sizes = re.findall(rb"(\d+)x(\d+)", self.adb("shell", "wm size"))
        density = re.findall(rb"density:\s*(\d+)", self.adb("shell", "wm density"))
        assert sizes and sizes[-1] == (b"432", b"960"), "Dedicated emulator must use 432x960"
        assert density and density[-1] == b"160", "Dedicated emulator must use 160 dpi"
        assert self.adb("shell", f"run-as {PKG} pwd").strip().endswith(PKG.encode()), "Debug run-as unavailable"
        self.adb("shell", f"appops set {PKG} SYSTEM_ALERT_WINDOW allow")
        for permission in ("POST_NOTIFICATIONS", "ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION", "NEARBY_WIFI_DEVICES"):
            self.adb("shell", f"pm grant {PKG} android.permission.{permission}")
        self.adb("shell", "cmd location set-location-enabled true")
        try:
            self.signal_flow()
            print("ADB smoke v3 passed", flush=True)
        except BaseException:
            try:
                self.capture("v3-failure")
            except Exception:
                pass
            raise
        finally:
            self.stop_fake()
            self.adb("shell", f"am force-stop {PKG}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=r"D:\AndroidLab\sdk\platform-tools\adb.exe")
    parser.add_argument("--serial", default="emulator-5584", choices=["emulator-5584"])
    args = parser.parse_args()
    SmokeV3(args.adb, args.serial).run()


if __name__ == "__main__":
    main()
