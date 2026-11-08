#!/usr/bin/env python3
"""Conservative non-secret public-source gate. Never print matched contents."""
from pathlib import Path
import re
import sys

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
        if not p.is_file() or p.is_symlink():
            continue
        rel = p.relative_to(root).as_posix()
        if any(part in SKIP for part in p.relative_to(root).parts):
            continue
        if p.suffix.lower() in BLOCK_EXT or NAME.search(rel):
            problems.append((rel, "filename or file type"))
            continue
        # The guard itself necessarily contains literal detection patterns.
        if rel == "tools/privacy_guard.py":
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
