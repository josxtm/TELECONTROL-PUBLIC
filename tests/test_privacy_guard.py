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

    def test_isolated_embedded_ssh_permission_is_allowed(self):
        with tempfile.TemporaryDirectory() as root:
            target = Path(root) / "app/src/main/AndroidManifest.xml"
            target.parent.mkdir(parents=True)
            target.write_text(
                '<manifest><uses-permission android:name="android.permission.INTERNET"/>'
                '<application><activity '
                'android:name="io.github.josxtm.mangossh.community.MainActivity" '
                'android:exported="false" '
                'android:process=":mangossh"/></application></manifest>',
                encoding="utf-8"
            )
            self.assertEqual(check(root), [])

    def test_exported_or_shared_process_ssh_is_rejected(self):
        for exported, process in [("true", ":mangossh"), ("false", ":other")]:
            with self.subTest(exported=exported, process=process):
                with tempfile.TemporaryDirectory() as root:
                    target = Path(root) / "app/src/main/AndroidManifest.xml"
                    target.parent.mkdir(parents=True)
                    target.write_text(
                        '<manifest><uses-permission android:name="android.permission.INTERNET"/>'
                        '<application><activity '
                        'android:name="io.github.josxtm.mangossh.community.MainActivity" '
                        f'android:exported="{exported}" '
                        f'android:process="{process}"/></application></manifest>',
                        encoding="utf-8"
                    )
                    self.assertTrue(check(root))

    def test_network_permission_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            (Path(root) / "AndroidManifest.xml").write_text("android.permission.INTERNET", encoding="utf-8")
            self.assertEqual(len(check(root)), 1)

if __name__ == "__main__":
    unittest.main()
