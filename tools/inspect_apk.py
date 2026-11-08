#!/usr/bin/env python3
"""Inspect the actual preview package, permissions, resources and signing certificate."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import struct
import tempfile
import zipfile
from privacy_guard import check, APPROVED_UI_ASSETS, IP, KEY, PASSWORD, TOPOLOGY

OCR_SHA256 = {
    "assets/ocr/eng.traineddata": "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
    "assets/ocr/spa.traineddata": "6f2e04d02774a18f01bed44b1111f2cd7f3ba7ac9dc4373cd3f898a40ea6b464",
}


def inspect(apk, build_tools):
    apk = Path(apk)
    tools = Path(build_tools)
    def run(name, *args):
        return subprocess.check_output([str(tools / name), *map(str, args)], text=True)
    signature = run("apksigner", "verify", "--print-certs", apk)
    badging = run("aapt", "dump", "badging", apk)
    version = re.search(
        r"package: name='es\.josxtm\.telecontrol\.community' versionCode='([0-9]+)' versionName='v\.1\.0 public - comp\. ([0-9]+)'",
        badging
    )
    if not version or int(version.group(1)) != 10000 + int(version.group(2)):
        raise ValueError("Unexpected public application identity or compilation version")
    manifest = run("aapt", "dump", "xmltree", apk, "AndroidManifest.xml")
    permissions = re.findall(r"uses-permission: name='([^']+)'", badging)
    required_permissions = {"android.permission.RECORD_AUDIO", "android.permission.INTERNET", "android.permission.CAMERA"}
    permitted_permissions = required_permissions | {
        "android.permission.POST_NOTIFICATIONS", "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.SCHEDULE_EXACT_ALARM", "android.permission.WAKE_LOCK",
        "android.permission.ACCESS_NETWORK_STATE", "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC", "android.permission.VIBRATE",
        "android.permission.ACCESS_WIFI_STATE"
    }
    if not required_permissions.issubset(permissions) or set(permissions) - permitted_permissions:
        raise ValueError("Unexpected permissions")
    if "io.github.josxtm.mangossh.community.MainActivity" not in manifest or ":mangossh" not in manifest:
        raise ValueError("Isolated MangoSSH activity missing")
    if not re.search(r"allowBackup\([^\n]*=\(type 0x12\)0x0", manifest):
        raise ValueError("Automatic backup must be disabled")
    dex_strings = []
    xml_resources = []
    metadata = ""
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if name.endswith("/"):
                continue
            # Frontend files are audited by exact Git blob ID; OCR models are
            # public-licensed downloads with pinned SHA-256, never private CT data.
            if name.startswith("assets/"):
                ui_relative = "app/src/main/" + name
                content = archive.read(name)
                if ui_relative in APPROVED_UI_ASSETS:
                    header = b"blob " + str(len(content)).encode("ascii") + b"\x00"
                    git_sha = hashlib.sha1(header + content).hexdigest()
                    if git_sha != APPROVED_UI_ASSETS[ui_relative]:
                        raise ValueError("Unreviewed frontend content")
                elif name in OCR_SHA256:
                    if hashlib.sha256(content).hexdigest() != OCR_SHA256[name]:
                        raise ValueError("Unverified OCR language model")
                else:
                    raise ValueError("Unreviewed bundled asset")
                continue
            if not (re.fullmatch(r"classes\d*\.dex", name) or name in {"AndroidManifest.xml", "resources.arsc", "META-INF/com/android/build/gradle/app-metadata.properties"}
                    or re.fullmatch(r"res/[A-Za-z0-9_+/-]+\.xml", name)
                    or re.fullmatch(r"res/mipmap-(?:mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi)(?:-v[0-9]+)?/ic_launcher\.png", name)
                    or re.fullmatch(r"META-INF/[A-Za-z0-9_.-]+", name)):
                raise ValueError("Unreviewed packaged resource")
            if name.startswith("res/raw") or name.startswith("assets/"):
                raise ValueError("Bundled data is forbidden")
            if name.startswith("res/mipmap-") and name.endswith("/ic_launcher.png"):
                artwork = archive.read(name)
                if not artwork.startswith(bytes.fromhex("89504e470d0a1a0a")) or len(artwork) > 100000:
                    raise ValueError("Unreviewed launcher image payload")
            if name.startswith("res/") and name.endswith(".xml"):
                xml_resources.append(run("aapt", "dump", "xmltree", apk, name))
            if name.endswith("app-metadata.properties"):
                metadata = archive.read(name).decode("utf-8")
            if re.fullmatch(r"classes\d*\.dex", name):
                data = archive.read(name)
                if not data.startswith(b"dex\n"):
                    raise ValueError("Invalid bytecode container")
                count, table = struct.unpack_from("<II", data, 56)
                for index in range(count):
                    offset, = struct.unpack_from("<I", data, table + 4 * index)
                    for _ in range(5):
                        byte = data[offset]
                        offset += 1
                        if byte < 128:
                            break
                    end = data.index(b"\0", offset)
                    dex_strings.append(data[offset:end].decode("utf-8", errors="replace"))
    resources = run("aapt", "dump", "resources", apk)
    # Corporate branding and field captions may legitimately exist in public
    # GUI bytecode. Reject credentials, literal operational IPs and topology,
    # rather than treating every public brand name as confidential.
    compiled = resources + "\n" + "\n".join(xml_resources) + "\n" + metadata + "\n" + "\n".join(dex_strings)
    whitelist_ip = {".".join(parts) for parts in (("0", "0", "0", "0"), ("127", "0", "0", "1"), ("255", "255", "255", "255"))}
    if any(value not in whitelist_ip for value in IP.findall(compiled)) or KEY.search(compiled) or PASSWORD.search(compiled) or TOPOLOGY.search(compiled):
        raise ValueError("Compiled output contains potentially private operational content")
    certs = "\n".join(line for line in signature.splitlines() if "certificate SHA-256 digest:" in line)
    if not certs:
        raise ValueError("No verified certificate fingerprint")
    return ("PASS APK identity, version, signature, permission allowlist and resource inspection\n"
            + certs + "\nAPK SHA-256: " + hashlib.sha256(apk.read_bytes()).hexdigest()
            + "\nPreview only: independent public application; ephemeral debug signing.\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    parser.add_argument("build_tools")
    args = parser.parse_args()
    print(inspect(args.apk, args.build_tools), end="")
