"""Render the project's existing code-drawn brand mark at Apple's 1024px size."""
import json
import sys
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root / "tools"))
from create_store_assets import CHARCOAL, fader, draw_bubble

out = root / "apple/App/Resources/Assets.xcassets/AppIcon.appiconset"
out.mkdir(parents=True, exist_ok=True)
image = Image.new("RGB", (1024, 1024), CHARCOAL[:3])
draw = ImageDraw.Draw(image)
for x, value in [(240, .76), (440, .48), (640, .67), (840, .31)]:
    fader(draw, x, 192, 832, value, 1.8)
draw_bubble(draw, 512, 510, 210, 2)
draw.arc((320, 320, 704, 704), 205, 335, fill=(117, 188, 236), width=10)
image.save(out / "AppIcon.png")
(out / "Contents.json").write_text(json.dumps({"images": [{"filename": "AppIcon.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}], "info": {"author": "xcode", "version": 1}}, indent=2) + "\n")
(out.parent / "Contents.json").write_text('{"info":{"author":"xcode","version":1}}\n')
