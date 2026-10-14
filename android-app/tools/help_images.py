#!/usr/bin/env python3
"""Makes the pictures of the help page (Settings › Help & guide) from the screenshot tests.

Run after recording screenshots (./gradlew testGithubDebugUnitTest -Proborazzi.test.record=true):
    python3 tools/help_images.py
The spots (where to tap) are set in ui/HelpScreen.kt as fractions of these pictures.
"""
from pathlib import Path

from PIL import Image

APP = Path(__file__).resolve().parent.parent / "app"
SHOTS = APP / "src/test/screenshots"
OUT = APP / "src/main/res/drawable-nodpi"
WIDTH = 540

# picture name -> (screenshot, crop as fractions (left, top, right, bottom) or None)
PICTURES = {
    "help_pair": ("11_pair.png", None),
    "help_demo": ("02c_servers_empty.png", None),
    "help_servers": ("01_servers.png", None),
    "help_overview": ("03_dashboard.png", None),
    "help_offline": ("09_offline.png", None),
    "help_history": ("20_history.png", (0, 0, 1, 0.42)),
    "help_customize": ("26_dashboard_edit.png", (0, 0, 1, 0.62)),
    # Worlds, scheduled tasks, plugins and files at the bottom of the overview.
    "help_manage": ("27_dashboard_full.png", (0, 0.54, 1, 0.865)),
    "help_players": ("05_players.png", (0, 0, 1, 0.6)),
    "help_players_selected": ("05b_players_selected.png", None),
    "help_inventory": ("05c_inventory.png", (0, 0, 1, 0.5)),
    "help_whitelist": ("21_whitelist.png", (0, 0, 1, 0.55)),
    "help_console_suggest": ("06b_console_suggestions.png", None),
    "help_console": ("06_console.png", None),
    "help_chat": ("07_chat.png", None),
    "help_files": ("24_files.png", (0, 0, 1, 0.6)),
    "help_settings": ("14_settings.png", None),
}


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (shot, crop) in PICTURES.items():
        image = Image.open(SHOTS / shot).convert("RGB")
        if crop:
            w, h = image.size
            image = image.crop((round(crop[0] * w), round(crop[1] * h), round(crop[2] * w), round(crop[3] * h)))
        height = round(image.height * WIDTH / image.width)
        image.resize((WIDTH, height), Image.LANCZOS).save(OUT / f"{name}.webp", "WEBP", quality=82, method=6)
        print(f"{name}.webp  {WIDTH}x{height}")


if __name__ == "__main__":
    main()
