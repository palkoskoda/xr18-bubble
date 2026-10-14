"""Check the inline Controls checkboxes through ADB on emulator-5584 only.

Requires the latest debug APK to be installed. No fake server or real mixer.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import time
import xml.etree.ElementTree as ET

import adb_smoke_v2 as helpers
from adb_smoke_v2 import Smoke, PKG, nodes, texts, bounds

ROOT = Path(__file__).resolve().parents[1]
helpers.OUT = ROOT / "build" / "checkbox-v2"


class CheckboxSmoke(Smoke):
    def ui(self, timeout=12):
        tree = super().ui(timeout)
        foreground = next((n.get("package") for n in nodes(tree) if n.get("package")), None)
        if getattr(self, "strict_foreground", False):
            assert foreground == PKG, f"Test interrupted by another foreground app: {foreground}"
        return tree

    def preferences(self):
        data = self.adb("shell", f"run-as {PKG} cat shared_prefs/profile.xml")
        root = ET.fromstring(data)
        selected = {n.text for n in root.findall("./set[@name='selected']/string")}
        strings = {n.get("name"): n.text or "" for n in root.findall("string")}
        return selected, strings

    def wait_prefs(self, predicate, message):
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline:
            selected, prefs = self.preferences()
            if predicate(selected, prefs):
                return selected, prefs
            time.sleep(.2)
        raise AssertionError(message + f": selected={selected}, prefs={prefs}")

    def visible_checkbox(self, tree, name):
        matches = [n for n in nodes(tree) if n.get("class") == "android.widget.CheckBox" and n.get("text") == name]
        for node in matches:
            x1, y1, x2, y2 = bounds(node)
            if x2 > x1 and y2 - y1 >= 44 and y1 >= 80 and y2 <= 908:
                return node
        return None

    def scroll_to(self, name):
        for _ in range(14):
            tree = self.wait(lambda t: "Controls" in texts(t), "Controls screen missing")
            self.assert_no_modal(tree)
            checkbox = self.visible_checkbox(tree, name)
            if checkbox is not None:
                return tree, checkbox
            self.adb("shell", "input swipe 210 800 210 360 300")
            time.sleep(.25)
        raise AssertionError(f"Could not scroll to {name}")

    def assert_no_modal(self, tree):
        assert "Save" not in texts(tree) and "Cancel" not in texts(tree), "Checkbox unexpectedly opened a dialog"

    def assert_row(self, tree, name):
        control = self.visible_checkbox(tree, name)
        volume = next(n for n in nodes(tree) if n.get("class") == "android.widget.CheckBox" and n.get("content-desc") == name + " · Volume")
        mute = next(n for n in nodes(tree) if n.get("class") == "android.widget.CheckBox" and n.get("content-desc") == name + " · Mute")
        first, second, third = bounds(control), bounds(volume), bounds(mute)
        assert first[2] <= second[0] and second[2] <= third[0], (name, first, second, third)
        assert first[1:4:2] == second[1:4:2] == third[1:4:2], (name, first, second, third)
        assert first[0] >= 0 and third[2] <= 432, (name, first, second, third)
        return control, volume, mute

    def run(self):
        # Repeat the dedicated-emulator guard before any app data mutation.
        assert self.adb("get-state").strip() == b"device"
        assert self.adb("shell", "getprop ro.kernel.qemu").strip() == b"1"
        sizes = re.findall(rb"(\d+)x(\d+)", self.adb("shell", "wm size"))
        density = re.findall(rb"density:\s*(\d+)", self.adb("shell", "wm density"))
        assert sizes[-1] == (b"432", b"960") and density[-1] == b"160"
        assert self.adb("shell", f"run-as {PKG} pwd").strip().endswith(PKG.encode())
        try:
            print("checkbox v2: default controls and DCA toggle", flush=True)
            self.seed()
            self.adb("shell", f"am start -n {PKG}/.MainActivity --ei tab 1")
            tree = self.wait(lambda t: self.visible_checkbox(t, "Main LR") is not None
                             and self.visible_checkbox(t, "DCA 1") is not None, "Default checkboxes missing")
            self.strict_foreground = True
            self.assert_no_modal(tree)
            main, _, _ = self.assert_row(tree, "Main LR")
            dca, _, _ = self.assert_row(tree, "DCA 1")
            assert main.get("checked") == "true" and dca.get("checked") == "true"
            self.capture("controls-default")
            self.click(dca)
            self.wait_prefs(lambda selected, _: "dca1" not in selected, "DCA was not removed")
            tree = self.wait(lambda t: self.visible_checkbox(t, "DCA 1") is not None
                             and self.visible_checkbox(t, "DCA 1").get("checked") == "false", "DCA checkbox remained checked")
            self.assert_no_modal(tree)
            self.click(self.visible_checkbox(tree, "DCA 1"))
            self.wait_prefs(lambda selected, _: "dca1" in selected, "DCA was not restored")

            print("checkbox v2: channel enable, parts, at least one part", flush=True)
            tree, channel = self.scroll_to("Channel 1")
            assert channel.get("checked") == "false"
            self.click(channel)
            self.wait_prefs(lambda selected, _: "ch1" in selected, "Channel was not enabled")
            tree = self.wait(lambda t: self.visible_checkbox(t, "Channel 1") is not None
                             and self.visible_checkbox(t, "Channel 1").get("checked") == "true", "Channel checkbox did not enable")
            self.assert_no_modal(tree)
            channel, volume, mute = self.assert_row(tree, "Channel 1")
            assert volume.get("checked") == mute.get("checked") == "true"
            assert volume.get("enabled") == mute.get("enabled") == "true"
            self.capture("channel-both")
            self.click(mute)
            self.wait_prefs(lambda _, p: p.get("mute_ch1") == "off" and p.get("volume_ch1") == "on", "Mute-only preference did not change")
            tree = self.wait(lambda t: any(n.get("content-desc") == "Channel 1 · Mute" and n.get("checked") == "false" for n in nodes(t)), "Mute checkbox stayed on")
            _, volume, _ = self.assert_row(tree, "Channel 1")
            assert volume.get("checked") == "true"
            self.capture("channel-volume-only")
            self.click(volume)
            self.wait_prefs(lambda _, p: p.get("volume_ch1") == "off" and p.get("mute_ch1") == "on", "Disabling final part did not enable the other part")
            tree = self.wait(lambda t: any(n.get("content-desc") == "Channel 1 · Mute" and n.get("checked") == "true" for n in nodes(t)), "Mute fallback did not check")
            channel, volume, mute = self.assert_row(tree, "Channel 1")
            assert volume.get("checked") == "false" and mute.get("checked") == "true"
            self.capture("channel-mute-only")
            self.click(channel)
            self.wait_prefs(lambda selected, _: "ch1" not in selected, "Channel was not removed")
            tree = self.wait(lambda t: self.visible_checkbox(t, "Channel 1") is not None
                             and self.visible_checkbox(t, "Channel 1").get("checked") == "false", "Channel checkbox remained checked")
            _, volume, mute = self.assert_row(tree, "Channel 1")
            assert volume.get("enabled") == mute.get("enabled") == "false"
            self.assert_no_modal(tree)

            print("checkbox v2: last channel and screen bounds", flush=True)
            tree, channel = self.scroll_to("Channel 16")
            self.assert_row(tree, "Channel 16")
            assert channel.get("checked") == "false"
            self.capture("controls-channel16")
            log = self.adb("logcat", "-d", "-b", "crash", "-t", "100").decode(errors="replace")
            assert f"Process: {PKG}" not in log, "App crash found in emulator crash buffer"
            print("Checkbox smoke v2 passed: inline DCA/channel selection, parts persistence, at-least-one-part guard, disabled parts, Channel 16, no modal or bounds collisions", flush=True)
        except BaseException:
            try:
                self.capture("checkbox-failure")
            except Exception:
                pass
            raise
        finally:
            self.adb("shell", f"am force-stop {PKG}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=r"D:\AndroidLab\sdk\platform-tools\adb.exe")
    parser.add_argument("--serial", choices=["emulator-5584"], default="emulator-5584")
    args = parser.parse_args()
    CheckboxSmoke(args.adb, args.serial).run()


if __name__ == "__main__":
    main()
