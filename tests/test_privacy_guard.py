import sys
import tempfile
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from privacy_guard import check

class PublicGuardTest(unittest.TestCase):
    def test_clean_file_passes(self):
        with tempfile.TemporaryDirectory() as root:
            (Path(root) / "test.md").write_text("Safe local-only Android project", encoding="utf-8")
            self.assertEqual(check(root), [])

    def test_ipv4_literal_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            value = ".".join(["192", "168", "10", "25"])
            (Path(root) / "test.md").write_text(value, encoding="utf-8")
            self.assertEqual(len(check(root)), 1)

    def test_keystore_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            (Path(root) / "sign.jks").write_bytes(b"test")
            self.assertEqual(len(check(root)), 1)

if __name__ == "__main__":
    unittest.main()
