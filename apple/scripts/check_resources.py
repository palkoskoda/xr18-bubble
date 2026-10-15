"""Small dependency-free preflight for the native preview resources."""
import json
import plistlib
import re
import struct
from pathlib import Path

root = Path(__file__).resolve().parents[1]
resources = root / "App/Resources"
catalogs = {}
for language in ["en", "sk"]:
    text = (resources / f"{language}.lproj/Localizable.strings").read_text(encoding="utf-8")
    keys = re.findall(r'^"([^"]+)"\s*=', text, re.M)
    assert len(keys) == len(set(keys)), f"Duplicate keys in {language}"
    catalogs[language] = set(keys)
assert catalogs["en"] == catalogs["sk"], "Locale keys differ"
for source in (root / "App").rglob("*.swift"):
    for key in re.findall(r'localized\("([^"]+)"\)', source.read_text(encoding="utf-8")):
        assert key in catalogs["en"], f"Missing error translation: {key}"
for name in ["PrivacyInfo.xcprivacy", "Simulator.entitlements"]:
    plistlib.loads((root / "App" / name).read_bytes())
icon = resources / "Assets.xcassets/AppIcon.appiconset"
assert struct.unpack(">II", (icon / "AppIcon.png").read_bytes()[16:24]) == (1024, 1024)
json.loads((icon / "Contents.json").read_text())
print(f"Resource preflight passed: {len(catalogs['en'])} EN/SK keys, valid privacy/entitlements, 1024px icon.")
