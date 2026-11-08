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

    def test_no_real_catalog_or_network_literals(self):
        import re
        for path in VISUAL.iterdir():
            if path.suffix not in {".js", ".css"}:
                continue
            text = path.read_text(encoding="utf-8")
            self.assertNotRegex(text, r"(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])")
            self.assertNotRegex(text, r"\\b(?:UFD|Naturgy|Eiffage|NaturalCor|TERED|GNFT)\\b")

if __name__ == "__main__":
    unittest.main()
