#!/usr/bin/env python3
"""Draws the pictures of the Windows installer (WiX) in RemoteMC's colours.

    python3 installer/make_images.py

dialog.bmp (493x312): left part of the welcome and finish pages (dark green with the logo; the
installer writes its text on the white right part). banner.bmp (493x58): top of the other pages
(white with the logo on the right; the installer writes the title on the left).
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
LOGO = HERE.parent / "src/main/resources/icon.png"
DARK_TOP = (27, 46, 34)
DARK_BOTTOM = (14, 20, 17)


def gradient(width: int, height: int) -> Image.Image:
    im = Image.new("RGB", (width, height))
    for y in range(height):
        t = y / max(1, height - 1)
        c = tuple(round(a + (b - a) * t) for a, b in zip(DARK_TOP, DARK_BOTTOM))
        for x in range(width):
            im.putpixel((x, y), c)
    return im


def main() -> None:
    logo = Image.open(LOGO).convert("RGBA")

    dialog = Image.new("RGB", (493, 312), "white")
    left = gradient(164, 312)
    big = logo.resize((112, 112), Image.LANCZOS)
    left.paste(big, ((164 - 112) // 2, 70), big)
    dialog.paste(left, (0, 0))
    dialog.save(HERE / "dialog.bmp")

    banner = Image.new("RGB", (493, 58), "white")
    small = logo.resize((44, 44), Image.LANCZOS)
    banner.paste(small, (493 - 44 - 10, 7), small)
    banner.save(HERE / "banner.bmp")
    print("dialog.bmp, banner.bmp")


if __name__ == "__main__":
    main()
