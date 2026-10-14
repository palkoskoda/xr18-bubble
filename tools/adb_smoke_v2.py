"""ADB-only v0.2 integration tests on the dedicated emulator-5584.

Requires the current debug APK to be installed first. Never installs an APK,
opens another emulator, touches a phone, or contacts a physical mixer.
Run: python tools/adb_smoke_v2.py [--adb PATH] [--serial emulator-5584]
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import socket
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from fake_mixer import osc_encode

ROOT = Path(__file__).resolve().parents[1]
PKG = "sk.palko.xr18bubble"
OUT = ROOT / "build" / "smoke-v2"


class Smoke:
    def __init__(self, adb_path: str, serial: str):
        if serial != "emulator-5584":
            raise ValueError("Safety guard: only the dedicated emulator-5584 is permitted")
        self.adb_path, self.serial = adb_path, serial
        self.fake: subprocess.Popen | None = None
        self.fake_output = None
        self.last_ui = ET.Element("hierarchy")
        OUT.mkdir(parents=True, exist_ok=True)
        self.log = OUT / "writes.jsonl"
        self.log.write_text("", encoding="utf-8")

    def adb(self, *command: str, data: bytes | None = None, timeout: float = 15) -> bytes:
        result = subprocess.run([self.adb_path, "-s", self.serial, *command], input=data,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        if result.returncode:
            raise RuntimeError(f"ADB {command!r}: {result.stderr.decode(errors='replace')}")
        return result.stdout

    def ui(self, timeout: float = 12) -> ET.Element:
        response = self.adb("shell", "uiautomator dump /data/local/tmp/xr18-smoke-v2.xml", timeout=timeout)
        if b"dumped to" not in response:
            raise RuntimeError("UIAutomator did not produce a fresh dump: " + response.decode(errors="replace"))
        self.last_ui = ET.fromstring(self.adb("shell", "cat /data/local/tmp/xr18-smoke-v2.xml"))
        return self.last_ui

    def wait(self, predicate, message: str, seconds: float = 45) -> ET.Element:
        deadline = time.monotonic() + seconds
        last_error = ""
        while time.monotonic() < deadline:
            try:
                tree = self.ui(timeout=min(12, max(1, deadline - time.monotonic())))
                if predicate(tree):
                    return tree
            except (RuntimeError, subprocess.TimeoutExpired, ET.ParseError) as error:
                last_error = str(error)
            time.sleep(.3)
        raise AssertionError(f"{message}; texts={texts(self.last_ui)!r}; last dump error={last_error}")

    def capture(self, name: str) -> None:
        (OUT / f"{name}.png").write_bytes(self.adb("exec-out", "screencap", "-p"))
        ET.ElementTree(self.last_ui).write(OUT / f"{name}.xml", encoding="utf-8", xml_declaration=True)

    def click(self, node: ET.Element) -> None:
        x1, y1, x2, y2 = bounds(node)
        self.adb("shell", f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")

    def seed(self, selected=("main", "dca1", "mute1"), language="en", **prefs) -> None:
        self.adb("shell", f"am force-stop {PKG}")
        root = ET.Element("map")
        values = dict(host="10.0.2.2", name="TestMixer", model="XR18", security="OPEN",
                      language=language, control_size="small", bubble_size="medium", auto_connect="on")
        values.update(prefs)
        for key, value in values.items():
            ET.SubElement(root, "string", name=key).text = value
        group = ET.SubElement(root, "set", name="selected")
        for key in selected:
            ET.SubElement(group, "string").text = key
        ET.SubElement(root, "int", name="x", value="380")
        ET.SubElement(root, "int", name="y", value="120")
        profile = ET.tostring(root, encoding="utf-8", xml_declaration=True)
        # run-as itself refuses non-debuggable builds. No device-wide data access.
        self.adb("shell", f"run-as {PKG} sh -c 'mkdir -p shared_prefs; rm -f shared_prefs/profile.xml.bak; cat > shared_prefs/profile.xml'", data=profile)
        self.adb("shell", f"am start -n {PKG}/.MainActivity")

    def open_bubble(self) -> ET.Element:
        tree = self.wait(lambda t: any(n.get("text") == "Show bubble" for n in nodes(t)), "Show bubble action missing")
        self.click(next(n for n in nodes(tree) if n.get("text") == "Show bubble"))
        # A NOT_FOCUSABLE overlay may be absent from UIAutomator's active window.
        # These coordinates are seeded and guarded by the fixed emulator geometry.
        time.sleep(.5)
        try:
            tree = self.ui(timeout=2)
        except (RuntimeError, subprocess.TimeoutExpired, ET.ParseError):
            tree = self.last_ui
        self.capture("v2-bubble")
        bubble = next((n for n in nodes(tree) if n.get("content-desc", "").startswith("XR18 Bubble,")), None)
        if bubble is not None:
            x1, y1, x2, y2 = bounds(bubble)
            assert 40 <= x2 - x1 <= 56 and 40 <= y2 - y1 <= 56, bounds(bubble)
            self.click(bubble)
        else:
            self.adb("shell", "input tap 404 168")
        return self.wait(lambda t: any(n.get("text") == "×" for n in nodes(t)), "Panel did not open")

    def start_fake(self, withhold=False) -> None:
        assert self.fake is None
        self.fake_output = (OUT / "fake-mixer.log").open("ab")
        command = [sys.executable, "-u", str(ROOT / "tools" / "fake_mixer.py"),
                   "--host", "127.0.0.1", "--log", str(self.log)]
        if withhold:
            command += ["--withhold", "/dca/1/fader"]
        self.fake = subprocess.Popen(command, cwd=ROOT, stdout=self.fake_output, stderr=subprocess.STDOUT)
        time.sleep(.3)
        assert self.fake.poll() is None, "Local fake mixer could not start; check fake-mixer.log"

    def stop_fake(self) -> None:
        if self.fake is not None:
            if self.fake.poll() is None:
                self.fake.terminate()
            self.fake.wait(timeout=8)
            self.fake = None
        if self.fake_output is not None:
            self.fake_output.close()
            self.fake_output = None

    def events(self) -> list[dict]:
        return [json.loads(line) for line in self.log.read_text(encoding="utf-8").splitlines() if line]

    def osc(self, path: str, *args) -> None:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.sendto(osc_encode(path, *args), ("127.0.0.1", 10024))

    def ready(self, count: int) -> ET.Element:
        return self.wait(lambda t: len(mute_buttons(t)) == count
                         and all(n.get("enabled") == "true" for n in mute_buttons(t))
                         and ("Ready" in texts(t) or "Connected" in texts(t)), "Controls did not become ready")

    def protocol_flow(self) -> None:
        print("v2: handshake with a withheld DCA value", flush=True)
        self.start_fake(withhold=True)
        self.seed()
        self.open_bubble()  # Tapping alone must start connection, without the Connect action.
        tree = self.wait(lambda t: "-4.0" in texts(t) and len(mute_buttons(t)) == 3
                         and all(n.get("enabled") == "false" for n in mute_buttons(t))
                         and "Syncing" in texts(t), "Partial sync did not keep all controls disabled")
        assert len(faders(tree)) == 2, "Expected Main and DCA faders"
        assert all(n.get("enabled") == "false" for n in faders(tree))
        assert not self.events(), "Connecting/syncing must never send SET"
        self.capture("v2-syncing")
        self.osc("/__test/release")
        tree = self.ready(3)
        assert "-4.0" in texts(tree) and any(t in ("+0.0", "0.0") for t in texts(tree)), texts(tree)
        assert len(faders(tree)) == 2 and all(n.get("enabled") == "true" for n in faders(tree))
        assert not self.events(), "Completing sync must never send SET"
        self.capture("v2-connected")

        print("v2: mute/unmute, external value, relative fader", flush=True)
        main = sorted([n for n in mute_buttons(tree) if n.get("text") == "Mute"], key=lambda n: bounds(n)[0])[0]
        self.click(main)
        tree = self.wait(lambda t: "Muted" in texts(t), "Main mute did not update")
        assert any(e["path"] == "/lr/mix/on" and e["value"] == 0 for e in self.events())
        self.click(next(n for n in mute_buttons(tree) if n.get("text") == "Muted"))
        tree = self.wait(lambda t: "Muted" not in texts(t), "Main unmute did not update")
        assert any(e["path"] == "/lr/mix/on" and e["value"] == 1 for e in self.events())
        self.osc("/lr/mix/fader", .5)
        tree = self.wait(lambda t: "-10.0" in texts(t), "External fader value did not arrive")
        fader = sorted(faders(tree), key=lambda n: bounds(n)[0])[0]
        x1, y1, x2, y2 = bounds(fader)
        x, y = (x1 + x2) // 2, (y1 + y2) // 2
        before = len(self.events())
        self.adb("shell", f"input swipe {x} {y} {x} {y - 8} 350")
        deadline = time.monotonic() + 5
        writes = []
        while time.monotonic() < deadline:
            writes = [e["value"] for e in self.events()[before:] if e["path"] == "/lr/mix/fader"]
            if writes:
                break
            time.sleep(.1)
        expected = .5 + 8 / max(1, y2 - y1 - 22)
        assert writes and abs(writes[-1] - expected) < .025, (writes, expected, bounds(fader))
        assert all(.49 <= value <= expected + .025 for value in writes), "Touching the rail jumped the fader"
        self.capture("v2-relative-fader")

        print("v2: timeout and recovery without replay", flush=True)
        self.stop_fake()
        tree = self.wait(lambda t: len(mute_buttons(t)) == 3
                         and all(n.get("enabled") == "false" for n in mute_buttons(t)), "Stale controls stayed enabled")
        assert all(n.get("enabled") == "false" for n in faders(tree))
        self.capture("v2-disconnected")
        count = len(self.events())
        self.start_fake()
        self.ready(3)
        time.sleep(1.2)
        assert len(self.events()) == count, "SET replayed after reconnect"
        self.capture("v2-reconnected")

    def layouts(self) -> None:
        scenarios = [
            ("mute-only", ("mute1",), {}, 0, 1, 120, 130),
            ("channel-both", ("ch1",), {}, 1, 1, 120, 260),
            ("channel-volume", ("ch1",), {"mute_ch1": "off"}, 1, 0, 120, 260),
            ("four-groups", ("mute1", "mute2", "mute3", "mute4"), {}, 0, 4, 320, 130),
        ]
        for name, selected, prefs, fader_count, mute_count, max_width, max_height in scenarios:
            print(f"v2: layout {name}", flush=True)
            before = len(self.events())
            self.seed(selected, **prefs)
            self.open_bubble()
            tree = self.ready(mute_count)
            assert len(faders(tree)) == fader_count, (name, len(faders(tree)))
            assert len(mute_buttons(tree)) == mute_count, (name, len(mute_buttons(tree)))
            panel = panel_node(tree)
            x1, y1, x2, y2 = bounds(panel)
            assert x2 - x1 <= max_width and y2 - y1 <= max_height, (name, bounds(panel))
            assert len(self.events()) == before, f"Layout {name} sent a SET while loading"
            self.capture(f"v2-{name}")

    def languages(self) -> None:
        for lang, folder in (("en", "values"), ("sk", "values-sk"), ("cs", "values-cs"), ("de", "values-de")):
            print(f"v2: language {lang}", flush=True)
            resource = ET.parse(ROOT / "app" / "src" / "main" / "res" / folder / "strings.xml")
            expected = resource.find(".//string[@name='mixers']").text
            self.seed(language=lang)
            self.wait(lambda t: expected in texts(t), f"{lang} mixer header missing")
            self.capture(f"v2-language-{lang}")

    def run(self) -> None:
        assert self.adb("get-state").strip() == b"device", "Emulator is unavailable"
        assert self.adb("shell", "getprop ro.kernel.qemu").strip() == b"1", "Target is not an emulator"
        sizes = re.findall(rb"(\d+)x(\d+)", self.adb("shell", "wm size"))
        densities = re.findall(rb"density:\s*(\d+)", self.adb("shell", "wm density"))
        assert sizes and sizes[-1] == (b"432", b"960"), "Dedicated test emulator must be 432x960"
        assert densities and densities[-1] == b"160", "Dedicated test emulator must use mdpi (160 dpi)"
        assert self.adb("shell", f"run-as {PKG} pwd").strip().endswith(PKG.encode()), "Debug-only run-as unavailable"
        self.adb("shell", f"appops set {PKG} SYSTEM_ALERT_WINDOW allow")
        self.adb("shell", f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
        try:
            self.protocol_flow()
            self.layouts()
            self.languages()
            print("ADB smoke v2 passed: partial sync guard, automatic connect, no SET on connect/recovery, mute, relative fader, compact layouts, EN/SK/CS/DE", flush=True)
        except BaseException:
            try:
                self.capture("v2-failure")
            except Exception:
                pass
            raise
        finally:
            self.stop_fake()
            self.adb("shell", f"am force-stop {PKG}")


def nodes(tree):
    return tree.iter("node")


def texts(tree):
    return [n.get("text", "") for n in nodes(tree)]


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.attrib["bounds"])))


def faders(tree):
    return [n for n in nodes(tree) if n.get("content-desc") == "Volume"]


def mute_buttons(tree):
    return [n for n in nodes(tree) if n.get("class") == "android.widget.Button"
            and (n.get("text") in ("Mute", "Muted", "—", "…")
                 or n.get("text", "").startswith("Group "))]


def panel_node(tree):
    candidates = [n for n in nodes(tree) if any(x.get("text") == "×" for x in n.iter("node"))
                  and (faders(n) or mute_buttons(n))]
    assert candidates, "Panel root not found"
    return min(candidates, key=lambda n: (bounds(n)[2] - bounds(n)[0]) * (bounds(n)[3] - bounds(n)[1]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=r"D:\AndroidLab\sdk\platform-tools\adb.exe")
    parser.add_argument("--serial", default="emulator-5584", choices=["emulator-5584"])
    args = parser.parse_args()
    Smoke(args.adb, args.serial).run()


if __name__ == "__main__":
    main()
