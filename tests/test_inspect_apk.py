import sys
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from inspect_apk import inspect


class ApkInspectionTest(unittest.TestCase):
    def run_inspection(self, extra=None, extra_data="synthetic", permissions=("android.permission.RECORD_AUDIO", "android.permission.INTERNET", "android.permission.CAMERA"), version="10003", compilation="3", resource="safe synthetic text"):
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
                    archive.writestr(extra, extra_data)
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

    def test_androidx_same_package_receiver_permission_allowed(self):
        permissions = ("android.permission.RECORD_AUDIO", "android.permission.INTERNET",
                       "android.permission.CAMERA",
                       "es.josxtm.telecontrol.community.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        self.assertIn("PASS APK", self.run_inspection(permissions=permissions))

    def test_unknown_package_receiver_permission_remains_blocked(self):
        permissions = ("android.permission.RECORD_AUDIO", "android.permission.INTERNET",
                       "android.permission.CAMERA",
                       "com.example.other.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        with self.assertRaisesRegex(ValueError, "unexpected:"):
            self.run_inspection(permissions=permissions)

    def test_fake_launcher_image_blocked(self):
        with self.assertRaises(ValueError):
            self.run_inspection(extra="res/mipmap-mdpi-v4/ic_launcher.png")

    def test_reviewed_native_dependency_elf_accepted(self):
        # Synthetic 64-bit ARM ELF header with the exact approved library basename.
        elf = bytearray(64)
        elf[:4] = bytes.fromhex("7f454c46")
        elf[4] = 2
        elf[5] = 1
        elf[18:20] = (183).to_bytes(2, "little")
        self.assertIn("PASS APK", self.run_inspection(
            extra="lib/arm64-v8a/libtesseract.so", extra_data=bytes(elf)))

    def test_unreviewed_native_dependency_rejected(self):
        elf = bytearray(64)
        elf[:4] = bytes.fromhex("7f454c46")
        elf[4] = 2
        elf[5] = 1
        elf[18:20] = (183).to_bytes(2, "little")
        with self.assertRaisesRegex(ValueError, "Unexpected native library path"):
            self.run_inspection(extra="lib/arm64-v8a/libunaudited.so", extra_data=bytes(elf))

    def test_mismatched_native_abi_rejected(self):
        elf = bytearray(64)
        elf[:4] = bytes.fromhex("7f454c46")
        elf[4] = 2
        elf[5] = 1
        elf[18:20] = (62).to_bytes(2, "little")
        with self.assertRaisesRegex(ValueError, "Unexpected native ELF"):
            self.run_inspection(extra="lib/arm64-v8a/libtesseract.so", extra_data=bytes(elf))

    def test_reviewed_androidx_drawable_png_allowed(self):
        # Synthetic image with a valid signature; source-policy gate separately
        # prevents arbitrary image binaries from entering public app sources.
        png = bytes.fromhex("89504e470d0a1a0a") + bytes(12)
        self.assertIn("PASS APK", self.run_inspection(
            extra="res/drawable-hdpi-v4/abc_sample.9.png", extra_data=png))

    def test_unknown_or_malformed_drawable_rejected(self):
        png = bytes.fromhex("89504e470d0a1a0a") + bytes(12)
        with self.assertRaises(ValueError):
            self.run_inspection(extra="res/raw/sample.png", extra_data=png)
        with self.assertRaisesRegex(ValueError, "image format"):
            self.run_inspection(extra="res/drawable-hdpi-v4/abc_sample.9.png",
                                extra_data=b"not a real PNG")

    def test_bounded_coroutine_service_descriptor_allowed(self):
        self.assertIn("PASS APK", self.run_inspection(
            extra="META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler",
            extra_data="kotlinx.coroutines.android.AndroidExceptionPreHandler\n"))

    def test_coroutine_descriptor_cannot_load_unreviewed_class(self):
        with self.assertRaisesRegex(ValueError, "Unexpected coroutine service registration"):
            self.run_inspection(
                extra="META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler",
                extra_data="com.example.secret.UnreviewedHandler\n")

    def test_known_kotlin_builtin_metadata_is_retained(self):
        self.assertIn("PASS APK", self.run_inspection(
            extra="kotlin/annotation/annotation.kotlin_builtins",
            extra_data=b"synthetic kotlin metadata"))

    def test_unrecognized_kotlin_metadata_path_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Unreviewed packaged resource path"):
            self.run_inspection(
                extra="kotlin/custom/private_data.kotlin_builtins",
                extra_data=b"synthetic kotlin metadata")

    def test_ssh_library_pem_header_is_not_a_private_key(self):
        # Public SSH parsers contain textual PEM header constants.
        header = "-----" + "BEGIN PRIVATE KEY" + "-----"
        self.assertIn("PASS APK", self.run_inspection(resource=header))

    def test_realistic_embedded_pem_body_is_blocked(self):
        header = "-----" + "BEGIN PRIVATE KEY" + "-----"
        footer = "-----" + "END PRIVATE KEY" + "-----"
        fake_key = header + "\\n" + ("A" * 160) + "\\n" + footer
        with self.assertRaisesRegex(ValueError, "embedded credential material"):
            self.run_inspection(resource=fake_key)

    def test_sensitive_resource_blocked(self):
        with self.assertRaises(ValueError):
            self.run_inspection(resource=".".join(["192", "168", "77", "12"]))
