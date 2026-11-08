import sys
import tempfile
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from gui_parity_gate import check


class GuiParityGateTest(unittest.TestCase):
    def test_missing_file_fails(self):
        with tempfile.TemporaryDirectory() as base:
            self.assertTrue(check(base, {"sample/original.html": ["id=home"]}))

    def test_missing_marker_fails(self):
        with tempfile.TemporaryDirectory() as base:
            f = Path(base) / "sample/original.html"
            f.parent.mkdir(parents=True)
            f.write_text("<html>generic placeholder</html>", encoding="utf-8")
            self.assertTrue(check(base, {"sample/original.html": ["id=home"]}))

    def test_present_marker_passes_structural_check(self):
        with tempfile.TemporaryDirectory() as base:
            f = Path(base) / "sample/original.html"
            f.parent.mkdir(parents=True)
            f.write_text("<html id=home>approved structure</html>", encoding="utf-8")
            self.assertEqual(check(base, {"sample/original.html": ["id=home"]}), [])


if __name__ == "__main__":
    unittest.main()
