"""Only the app-hosted integration test may write: one fader and one mute."""
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
events = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
assert len(events) == 2, f"Unexpected writes (demo/offline commands must be discarded): {events!r}"
assert events[0]["path"] == "/lr/mix/fader" and abs(events[0]["value"] - .42) < 1e-5
assert events[1]["path"] == "/config/mute/1" and events[1]["value"] == 1
print("Fixture audit passed: exactly two intended writes; no offline or demo writes.")
