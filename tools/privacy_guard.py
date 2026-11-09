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


# Audited original GUI source. Only the exact reviewed Git blob contents are
# allowed to be shipped, and no real IP, token or internal topology may occur.
APPROVED_UI_ASSETS = {
    "app/src/main/assets/app.js": "64e49f1bc579ba9e756b488bb259fb9ef40a5baf",
    "app/src/main/assets/backup.js": "5bb9568de153b74f46d21f594575858e721765c7",
    "app/src/main/assets/naturgy.png": "025897fef73d6fd9bc2ce27f08a29414fc3ef119",
    "app/src/main/assets/eiffage.png": "145cf5bdf3bb23874e482aa285b138b3155f9bcc",
    "app/src/main/assets/catalog-store.js": "1b5f1ebd197680126706b8b7b8d11e96b76c5ace",
    "app/src/main/assets/catalog-worker.js": "08c6ba79d64db2b5c3a188cf4ef443130567e4b3",
    "app/src/main/assets/catalog.js": "7a501925da397a3d334702ead59da12371c0622a",
    "app/src/main/assets/icons.js": "bbbc10a065d0b64bdeeed3a57376172182bf3a9a",
    "app/src/main/assets/index.html": "0253825779fea92d55dee8ff2dea3fd08897d590",
    "app/src/main/assets/mangossh_launcher.jpg": "1d4ce8b2ab0abdfc919d13ea31755ed73953c6df",
    "app/src/main/assets/journal-search.js": "b57bf86bdeb183e4b6654cfb666637f8fa28e2be",
    "app/src/main/assets/ocr/LICENSE.txt": "d645695673349e3947e8e5ae42332d0ac3164cd7",
    "app/src/main/assets/reminders.js": "b4d7ad27eb83352e2b5ae6d9f46fd9611d467c99",
    "app/src/main/assets/speech.js": "6013c00a22896e95b5dff6950fe948abccf4f6d3",
    "app/src/main/assets/sync-core.js": "c87a359fcb9f1ca385cd43fafdcc99ab9e881dde",
    "app/src/main/assets/sync-ui.js": "0ceb38014c28ea709310f461254029d89361979d",
    "app/src/main/assets/tcs-clients.js": "acc77596093e09b7c40bdeb099a6d763aa2c3ddc",
    "app/src/main/assets/themes.css": "81ecca9ac224f2f0d6c1ee90d258abe7ecad91e3",
    "app/src/main/assets/themes.js": "84ed6bbf79cd1e58018d9417ff246231823a422b",
    "app/src/main/assets/weekly-core.js": "ccc0688eb46ffca8f0dcc61abc88e0ba082c16aa",
    "app/src/main/assets/weekly-ui.js": "6adca767c3b969ad9d270d8ee4f956b4bf07ed8d",
    "app/src/main/assets/work-store.js": "53439c911d6167b69d574b34851284e10ef6872a",
    "app/src/main/assets/work-ui.js": "292d45a86d88bbb5cd9a3015111d635e23bcf0d7"
}
TOPOLOGY = re.compile(r"LAN\\s*\\+\\s*(?:1|2|3|9|10|11|13|14)|\\b(?:GIC1|GIC2|GIC3|GIN1)\\b", re.I)

BLOCK_EXT = {".keystore", ".jks", ".p12", ".pfx", ".pem", ".key", ".csv",
             ".xls", ".xlsx", ".xlsm", ".sqlite", ".sqlite3", ".db",
             ".apk", ".aab", ".gz", ".zip", ".7z", ".pdf", ".docx"}
READ_EXT = {".gradle", ".java", ".kt", ".kts", ".py", ".sh", ".md",
            ".yml", ".yaml", ".xml", ".html", ".properties", ".txt"}
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
        if rel in APPROVED_UI_ASSETS:
            if not matches_approved_artwork(p, APPROVED_UI_ASSETS[rel]):
                problems.append((rel, "GUI asset differs from reviewed source"))
            elif p.suffix.lower() in {".jpg", ".png"}:
                signature = b"\xff\xd8" if p.suffix.lower() == ".jpg" else bytes.fromhex("89504e470d0a1a0a")
                if p.stat().st_size > 100_000 or not p.read_bytes().startswith(signature):
                    problems.append((rel, "invalid approved GUI image"))
                continue
            elif p.stat().st_size > 200_000:
                problems.append((rel, "oversized GUI asset"))
            else:
                try:
                    s = p.read_text(encoding="utf-8")
                except UnicodeError:
                    problems.append((rel, "invalid GUI source encoding"))
                    continue
                if IP.search(s) or KEY.search(s) or PASSWORD.search(s) or TOPOLOGY.search(s):
                    problems.append((rel, "private operational content in GUI source"))
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
        # External-library namespaces must come only from pinned Gradle
        # dependencies, never from locally added classes impersonating a vendor.
        if rel.startswith(("app/src/main/java/", "app/src/main/kotlin/")):
            approved_source_roots = (
                "app/src/main/java/es/josxtm/telecontrol/",
                "app/src/main/java/io/github/josxtm/mangossh/",
                "app/src/main/kotlin/es/josxtm/telecontrol/",
                "app/src/main/kotlin/io/github/josxtm/mangossh/",
            )
            if not rel.startswith(approved_source_roots):
                problems.append((rel, "unapproved local Java/Kotlin package"))
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
        # Reviewed GUI bridge and offline functionality may contain public field
        # labels and company names, but NEVER internal IPs, secrets or topology.
        if rel.startswith("app/src/main/java/es/josxtm/telecontrol/"):
            if IP.search(s) or KEY.search(s) or PASSWORD.search(s) or TOPOLOGY.search(s):
                problems.append((rel, "private operational content in native code"))
            continue
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
