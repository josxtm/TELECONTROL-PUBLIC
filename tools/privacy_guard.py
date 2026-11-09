#!/usr/bin/env python3
"""Conservative non-secret public-source gate. Never print matched contents."""
from pathlib import Path
import re
import sys
import hashlib

# User-approved artwork: exact single-purpose PNG blobs only.
APPROVED_ARTWORK = {
    "app/src/main/res/mipmap-mdpi/ic_launcher.png": "033c00101e47684bb6142a844f806e68b57090f1",
    "app/src/main/res/mipmap-hdpi/ic_launcher.png": "465f94b32d6730608dfee56284a1f1e5b938c53c",
    "app/src/main/res/mipmap-xhdpi/ic_launcher.png": "a9bfbb4163293ad5d7d00b5e9f6b40054a9578f1",
    "app/src/main/res/mipmap-xxhdpi/ic_launcher.png": "ad3e46913cd021a4c56f9cfa4d8e3192bcba22ca",
    "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png": "5b9d4485c9bfec9d14e5cea11668d10b96e98717"
}

def matches_approved_artwork(path, expected):
    payload = path.read_bytes()
    return hashlib.sha1(b"blob " + str(len(payload)).encode("ascii") + b"\x00" + payload).hexdigest() == expected


BLOCK_EXT = {".keystore", ".jks", ".p12", ".pfx", ".pem", ".key", ".csv",
             ".xls", ".xlsx", ".xlsm", ".sqlite", ".sqlite3", ".db",
             ".apk", ".aab", ".gz", ".zip", ".7z", ".pdf", ".docx"}
READ_EXT = {".gradle", ".java", ".kt", ".kts", ".py", ".sh", ".md",
            ".yml", ".yaml", ".xml", ".html", ".css", ".js", ".properties", ".txt"}
SKIP = {".git", ".gradle", "build", ".idea", "__pycache__"}
NAME = re.compile(r"ct_data|telecontrol_ct|privado|confidencial|credential|secret|mo[._ -]?01033", re.I)
CORPORATE = re.compile(r"\b(?:UFD|Naturgy|Eiffage|NaturalCor|TERED|GNFT)\b|MO[. -]?01033", re.I)
NET = re.compile(r"(?:IP\s+LAN|IP\s+TC|LAN\s*\+\s*(?:1|2|3|9|10|11|13|14)|eth0\.2)", re.I)
IP = re.compile(r"(?<!\d)(?:\d{1,3}\.){3}\d{1,3}(?!\d)")
KEY = re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----|github_pat_[A-Za-z0-9_]{16,}|gh[pousr]_[A-Za-z0-9]{16,}", re.I)
PASSWORD = re.compile(r"(?:storePassword|keyPassword|api[_-]?key|password)\s*(?:=|:)\s*['\"][^'\"]{5,}['\"]", re.I)

def check(root):
    root = Path(root)
    problems = []
    for p in root.rglob("*"):
        if not p.is_file() and not p.is_symlink():
            continue
        rel = p.relative_to(root).as_posix()
        if any(part in SKIP for part in p.relative_to(root).parts):
            continue
        if p.is_symlink():
            problems.append((rel, "unreviewed symbolic link"))
            continue
        if any(part in {"assets", "raw"} for part in p.relative_to(root).parts):
            problems.append((rel, "bundled data requires separate review"))
            continue
        if rel in APPROVED_ARTWORK:
            if not matches_approved_artwork(p, APPROVED_ARTWORK[rel]):
                problems.append((rel, "approved image bytes changed"))
            continue
        if p.suffix.lower() in BLOCK_EXT or NAME.search(rel):
            problems.append((rel, "filename or file type"))
            continue
        # The guard itself necessarily contains literal detection patterns.
        if rel == "tools/privacy_guard.py":
            continue
        # Git metadata configuration is not a source file but is safe to inspect.
        if rel == ".gitignore":
            continue
        if p.suffix.lower() not in READ_EXT or p.stat().st_size > 2_000_000:
            if p.suffix.lower() not in READ_EXT:
                problems.append((rel, "unreviewed file type"))
            else:
                problems.append((rel, "oversized source file"))
            continue
        try:
            s = p.read_text(encoding="utf-8")
        except UnicodeError:
            problems.append((rel, "non-text content"))
            continue
        if p.name == "AndroidManifest.xml" and "android.permission.INTERNET" in s:
            # The sole network exception is the non-exported, isolated MangoSSH activity.
            # No other manifest, service or dependency receives a privacy exemption.
            permitted_ssh = (
                rel == "app/src/main/AndroidManifest.xml"
                and 'android:name="io.github.josxtm.mangossh.community.MainActivity"' in s
                and 'android:exported="false"' in s
                and 'android:process=":mangossh"' in s
            )
            if not permitted_ssh:
                problems.append((rel, "network permission without isolated SSH component"))
        if CORPORATE.search(s) or NET.search(s) or KEY.search(s) or PASSWORD.search(s) or IP.search(s):
            problems.append((rel, "sensitive-pattern candidate"))
    return sorted(problems)

if __name__ == "__main__":
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    issues = check(root)
    for path, why in issues:
        print("BLOCK:", path, "—", why)
    print("PASS public-source static gate" if not issues else "FAIL: do not publish")
    sys.exit(1 if issues else 0)
