"""Create the original XR18 Bubble Google Play artwork.

Only Pillow and a Windows system font are used. The artwork is decorative brand
art and intentionally does not depict a screenshot or claim hardware support.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "play" / "assets"
FONT = Path(r"C:\Windows\Fonts\segoeui.ttf")
FONT_BOLD = Path(r"C:\Windows\Fonts\segoeuib.ttf")
CHARCOAL = (25, 29, 35, 255)
CHARCOAL_2 = (35, 41, 49, 255)
SLATE = (89, 101, 115, 255)
SILVER = (210, 218, 226, 255)
BLUE = (66, 157, 230, 255)
BLUE_DARK = (31, 92, 151, 255)
WHITE = (244, 247, 250, 255)


def font(path: Path, size: int) -> ImageFont.FreeTypeFont:
    if not path.is_file():
        raise FileNotFoundError(path)
    return ImageFont.truetype(str(path), size)


def rounded_rect(draw: ImageDraw.ImageDraw, box, radius: int, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def fader(draw: ImageDraw.ImageDraw, x: int, top: int, bottom: int, value: float, scale: float = 1.0):
    draw.line((x, top, x, bottom), fill=SLATE, width=max(2, int(5 * scale)))
    y = int(bottom - value * (bottom - top))
    draw.line((x, y, x, bottom), fill=BLUE, width=max(2, int(5 * scale)))
    cap_w = int(25 * scale)
    cap_h = int(15 * scale)
    rounded_rect(draw, (x - cap_w, y - cap_h, x + cap_w, y + cap_h), int(6 * scale), SILVER)
    draw.line((x - cap_w + 5, y, x + cap_w - 5, y), fill=(154, 164, 175, 255), width=max(1, int(2 * scale)))


def draw_bubble(draw: ImageDraw.ImageDraw, cx: int, cy: int, radius: int, scale: float = 1.0):
    draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=BLUE_DARK, outline=BLUE, width=max(2, int(4 * scale)))
    # Three compact fader marks make the symbol readable at launcher size.
    for offset, value in [(-radius // 3, .63), (0, .40), (radius // 3, .73)]:
        x = cx + offset
        top = cy - radius // 2
        bottom = cy + radius // 2
        draw.line((x, top, x, bottom), fill=(178, 215, 244, 255), width=max(2, int(3 * scale)))
        y = int(bottom - value * (bottom - top))
        draw.ellipse((x - int(7 * scale), y - int(7 * scale), x + int(7 * scale), y + int(7 * scale)), fill=WHITE)


def make_icon() -> Image.Image:
    im = Image.new("RGBA", (512, 512), CHARCOAL)
    draw = ImageDraw.Draw(im)
    # Full square opaque background. There is no outer corner treatment.
    for x in (120, 220, 320, 420):
        fader(draw, x, 96, 416, {120: .76, 220: .48, 320: .67, 420: .31}[x], .9)
    draw_bubble(draw, 256, 255, 105, 1.0)
    draw.arc((160, 160, 352, 352), 205, 335, fill=(117, 188, 236, 255), width=5)
    return im


def make_feature() -> Image.Image:
    width, height = 1024, 500
    im = Image.new("RGBA", (width, height), CHARCOAL)
    draw = ImageDraw.Draw(im)
    # Subtle geometric field, kept decorative and free of UI claims.
    for i in range(9):
        y = 42 + i * 52
        draw.line((40, y, 984, y), fill=(46, 54, 64, 255), width=1)
    for i in range(14):
        x = 42 + i * 72
        draw.line((x, 28, x, 472), fill=(39, 46, 55, 255), width=1)
    draw_bubble(draw, 772, 248, 112, 1.05)
    for x, value in [(570, .72), (650, .46), (730, .61)]:
        fader(draw, x, 118, 382, value, .86)
    title = font(FONT_BOLD, 56)
    tagline = font(FONT, 30)
    draw.text((72, 142), "XR18 Bubble", font=title, fill=WHITE)
    draw.text((76, 225), "Your mix. One tap away.", font=tagline, fill=(179, 201, 220, 255))
    draw.line((76, 286, 422, 286), fill=BLUE, width=5)
    return im


def make_svg() -> str:
    tracks = "\n".join(
        f'<line x1="{x}" y1="96" x2="{x}" y2="416" stroke="#596573" stroke-width="5"/><line x1="{x}" y1="{y}" x2="{x}" y2="416" stroke="#429DE6" stroke-width="5"/>'
        for x, y in [(120, 172), (220, 262), (320, 202), (420, 317)]
    )
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <title>XR18 Bubble original app icon</title>
  <rect width="512" height="512" fill="#191D23"/>
  {tracks}
  <g>
    <circle cx="256" cy="255" r="105" fill="#1F5C97" stroke="#429DE6" stroke-width="4"/>
    <path d="M160 272a96 96 0 0 0 192 0" fill="none" stroke="#75BCEC" stroke-width="5"/>
    <g stroke="#B2D7F4" stroke-width="3" fill="#F4F7FA">
      <line x1="221" y1="203" x2="221" y2="307"/><circle cx="221" cy="247" r="7"/>
      <line x1="256" y1="203" x2="256" y2="307"/><circle cx="256" cy="266" r="7"/>
      <line x1="291" y1="203" x2="291" y2="307"/><circle cx="291" cy="235" r="7"/>
    </g>
  </g>
</svg>
'''


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    icon = make_icon()
    icon.save(OUT / "xr18-bubble-icon-512.png", format="PNG", optimize=True)
    (OUT / "xr18-bubble-icon.svg").write_text(make_svg(), encoding="utf-8", newline="\n")
    feature = make_feature()
    feature.save(OUT / "xr18-bubble-feature-1024x500.png", format="PNG", optimize=True)
    for path, size in ((OUT / "xr18-bubble-icon-512.png", (512, 512)), (OUT / "xr18-bubble-feature-1024x500.png", (1024, 500))):
        with Image.open(path) as check:
            if check.size != size or check.mode != "RGBA":
                raise RuntimeError(f"Unexpected asset format for {path}: {check.size} {check.mode}")
            if check.getextrema()[-1] != (255, 255):
                raise RuntimeError(f"Asset is not opaque: {path}")
            if path.stat().st_size >= 8 * 1024 * 1024:
                raise RuntimeError(f"Asset is unexpectedly large: {path}")
    print(f"Created assets in {OUT}")


if __name__ == "__main__":
    main()
