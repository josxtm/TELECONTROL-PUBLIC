import sys
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from inspect_apk import inspect


class ApkInspectionTest(unittest.TestCase):
    def run_inspection(self, extra=None, permissions=("android.permission.RECORD_AUDIO", "android.permission.INTERNET"), version="10003", compilation="3", resource="safe synthetic text"):
        def command(args, **kwargs):
            if "apksigner" in args[0]:
                return "Signer #1 certificate SHA-256 digest: synthetic-test-only"
            if "badging" in args:
                return ("package: name='es.josxtm.telecontrol.community' versionCode='" + version
                        + "' versionName='v.1.0 public - comp. " + compilation + "'\n"
                        + "".join("uses-permission: name='" + p + "'\n" for p in permissions))
            if "xmltree" in args:
                return "allowBackup(0x01010280)=(type 0x12)0x0\nio.github.josxtm.mangossh.community.MainActivity\n:mangossh"
            return resource
        with tempfile.TemporaryDirectory() as root:
            apk = Path(root) / "synthetic.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("AndroidManifest.xml", "synthetic")
                if extra:
                    archive.writestr(extra, "synthetic")
            with patch("inspect_apk.subprocess.check_output", side_effect=command):
                return inspect(apk, root)

    def test_reviewed_package_passes(self):
        self.assertIn("PASS APK", self.run_inspection())

    def test_assets_and_unknown_payloads_blocked(self):
        for name in ["assets/catalog.txt", "res/raw/catalog.xml", "unknown.bin"]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.run_inspection(extra=name)

    def test_network_permission_and_wrong_version_blocked(self):
        with self.assertRaises(ValueError):
            self.run_inspection(permissions=("android.permission.INTERNET",))
        with self.assertRaises(ValueError):
            self.run_inspection(version="10001")
        with self.assertRaises(ValueError):
            self.run_inspection(compilation="8")

    def test_fake_launcher_image_blocked(self):
        with self.assertRaises(ValueError):
            self.run_inspection(extra="res/mipmap-mdpi-v4/ic_launcher.png")

    def test_sensitive_resource_blocked(self):
        with self.assertRaises(ValueError):
            self.run_inspection(resource=".".join(["192", "168", "77", "12"]))
