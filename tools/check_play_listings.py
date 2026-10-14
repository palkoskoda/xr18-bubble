"""Validate the ready-to-paste Google Play text without external dependencies."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PLAY = ROOT / "play"
LIMITS = {"title.txt": 30, "short-description.txt": 80,
          "full-description.txt": 4000, "release-notes.txt": 500}


def main():
    document = (PLAY / "STORE_LISTING.md").read_text(encoding="utf-8")
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    android = "{http://schemas.android.com/apk/res/android}"
    version = manifest.attrib[android + "versionName"]
    code = manifest.attrib[android + "versionCode"]
    if f"version {version} (version code {code})" not in document:
        raise ValueError("Store preparation version differs from the app manifest")
    for locale in ("en-US", "sk-SK"):
        for filename, limit in LIMITS.items():
            path = PLAY / "listings" / locale / filename
            text = path.read_text(encoding="utf-8")
            if not text or text != text.strip() or "\ufffd" in text or "\ufeff" in text:
                raise ValueError(f"{path}: empty text, extra whitespace or encoding damage")
            # UTF-16 length is conservative for supplementary Unicode characters.
            count = len(text.encode("utf-16-le")) // 2
            if count > limit:
                raise ValueError(f"{path}: {count} characters exceeds {limit}")
            if filename != "release-notes.txt" and text not in document:
                raise ValueError(f"{path}: differs from STORE_LISTING.md")
            print(f"{locale}/{filename}: {count}/{limit}")
    print(f"Play listing checks passed; app {version}, code {code}")


if __name__ == "__main__":
    main()
