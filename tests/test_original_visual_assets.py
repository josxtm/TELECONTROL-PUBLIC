import hashlib
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
VISUAL = ROOT / "design" / "original"

class ApprovedVisualAssetsTest(unittest.TestCase):
    def test_original_theme_palette_and_navigation_landmarks(self):
        css = (VISUAL / "themes.css").read_text(encoding="utf-8")
        self.assertIn("#open-search", css)
        self.assertIn("#home", css)
        self.assertIn(".logo.brand-primary", css)
        for value in ("--background", "--surface", "--button", "--accent"):
            self.assertIn(value, css)

    def test_six_original_themes_are_preserved(self):
        script = (VISUAL / "themes.js").read_text(encoding="utf-8")
        for theme in ("light", "dark", "red", "olive", "blue", "custom"):
            self.assertIn(theme + ":", script)

    def test_original_icon_library_is_present(self):
        script = (VISUAL / "icons.js").read_text(encoding="utf-8")
        for glyph in ("terminal:", "weekly:", "camera:", "journal:"):
            self.assertIn(glyph, script)

    def test_original_screen_skeleton_is_present(self):
        html = (VISUAL / "index.html").read_text(encoding="utf-8")
        for screen in ("home", "detail", "config", "help", "work", "weekly"):
            self.assertIn('id="' + screen + '"', html)
        for control in ("open-search", "search-panel", "map", "catalog-file"):
            self.assertIn('id="' + control + '"', html)
        self.assertIn('class="logos"', html)

    def test_no_network_literals(self):
        import re
        ip = re.compile(r"(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])")
        for path in VISUAL.iterdir():
            if path.suffix in {".js", ".css"}:
                self.assertIsNone(ip.search(path.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()
