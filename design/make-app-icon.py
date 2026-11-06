#!/usr/bin/env python3
"""Renders ios/.../AppIcon.appiconset/icon-1024.png from the design tokens (needs Pillow).

Kraft ground with fluting, a stencil "M" in Big Shoulders Stencil Display (design/fonts, OFL),
and a band of packing tape at 78% opacity. No generated or stock imagery.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SIZE = 1024
img = Image.new("RGB", (SIZE, SIZE), (0xC9, 0xA5, 0x7A))
d = ImageDraw.Draw(img)
for y in range(0, SIZE, 24):
    d.line([(0, y), (SIZE, y)], fill=(0xB0, 0x8A, 0x5E), width=2)
font = ImageFont.truetype(str(ROOT / "design/fonts/BigShouldersStencilDisplay.ttf"), 820)
try:
    font.set_variation_by_axes([800])
except Exception:
    pass
x0, y0, x1, y1 = d.textbbox((0, 0), "M", font=font)
d.text(((SIZE - (x1 - x0)) / 2 - x0, (SIZE - (y1 - y0)) / 2 - y0), "M", font=font, fill=(0x1B, 0x16, 0x10))
tape = Image.new("RGBA", (SIZE, 150), (0xE9, 0xB9, 0x49, 200))
img.paste(tape, (0, 690), tape)
out = ROOT / "ios/Milkrun/Resources/Assets.xcassets/AppIcon.appiconset/icon-1024.png"
img.save(out)
print(out)
