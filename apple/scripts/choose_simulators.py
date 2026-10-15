"""Choose available iOS devices without assuming a runner's installed runtime."""
import json
import subprocess

devices = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "devices", "available", "--json"]))["devices"]
for prefix, kind in [("IPHONE", "iPhone"), ("IPAD", "iPad")]:
    choices = [d for runtime, entries in devices.items() if ".iOS-" in runtime
               for d in entries if d.get("isAvailable") and d["name"].startswith(kind)]
    if not choices:
        raise SystemExit(f"No available {kind} simulator. Install an iOS runtime in Xcode.")
    chosen = choices[-1]
    print(f"{prefix}_ID={chosen['udid']}")
