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


    def test_approved_original_icons_pass(self):
        source_root = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as root:
            for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
                name = f"app/src/main/res/mipmap-{density}/ic_launcher.png"
                source = source_root / name
                self.assertTrue(source.is_file())
                target = Path(root) / name
                target.parent.mkdir(parents=True)
                target.write_bytes(source.read_bytes())
            self.assertEqual(check(root), [])

    def test_modified_icon_is_blocked(self):
        with tempfile.TemporaryDirectory() as root:
            target = Path(root) / "app/src/main/res/mipmap-mdpi/ic_launcher.png"
            target.parent.mkdir(parents=True)
            target.write_bytes(b"not approved")
            self.assertTrue(check(root))

    def test_audited_gui_asset_integrity(self):
        source = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as root:
            original = source / "app/src/main/assets/index.html"
            self.assertTrue(original.is_file())
            target = Path(root) / "app/src/main/assets/index.html"
            target.parent.mkdir(parents=True)
            target.write_bytes(original.read_bytes())
            self.assertEqual(check(root), [])
            target.write_text("overwritten", encoding="utf-8")
            self.assertTrue(check(root))

    def test_gui_asset_with_literal_private_address_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            target = Path(root) / "app/src/main/assets/index.html"
            target.parent.mkdir(parents=True)
            target.write_text(".".join(["192", "0", "2", "44"]), encoding="utf-8")
            self.assertTrue(check(root))

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
