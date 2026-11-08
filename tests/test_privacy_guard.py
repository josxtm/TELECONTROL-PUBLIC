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

    def test_bundled_data_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            target = Path(root) / "app/src/main/assets"
            target.mkdir(parents=True)
            (target / "catalog.txt").write_text("synthetic", encoding="utf-8")
            self.assertEqual(len(check(root)), 1)

    def test_symlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            (Path(root) / "link.md").symlink_to("missing.md")
            self.assertEqual(len(check(root)), 1)

    def test_network_permission_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            (Path(root) / "AndroidManifest.xml").write_text("android.permission.INTERNET", encoding="utf-8")
            self.assertEqual(len(check(root)), 1)

if __name__ == "__main__":
    unittest.main()
