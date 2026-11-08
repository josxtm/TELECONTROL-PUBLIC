#!/usr/bin/env python3
"""Prevents publishing a public APK while approved GUI components are missing.

This is a structural safety gate, NOT proof of pixel-perfect visual parity.
Only checks public files and safe interface markers; never reads private repos.
"""
from pathlib import Path
import sys

REQUIRED = {
    "app/src/main/assets/index.html": [
        "id=\"home\"",
        "id=\"detail\"",
        "id=\"config\"",
        "themes.css"
    ],
    "app/src/main/assets/themes.css": [
        "html:root"
    ],
    "app/src/main/assets/app.js": [
        "fieldNames"
    ],
    "app/src/main/res/drawable/ic_mangossh.xml": []
}


def check(root, required=REQUIRED):
    root = Path(root)
    problems = []
    for relative, markers in required.items():
        candidate = root / relative
        if not candidate.is_file():
            problems.append(relative + ": missing approved GUI source")
            continue
        if markers:
            try:
                source = candidate.read_text(encoding="utf-8")
            except UnicodeError:
                problems.append(relative + ": unreadable GUI source")
                continue
            for marker in markers:
                if marker not in source:
                    problems.append(relative + ": required UI marker missing")
    return problems


if __name__ == "__main__":
    errors = check(sys.argv[1] if len(sys.argv) > 1 else ".")
    for message in errors:
        print("GUI PARITY BLOCK:", message)
    if errors:
        print("NOT APPROVED: original GUI must be restored and visually tested.")
        sys.exit(1)
    print("GUI source structure present; visual/manual comparison still required.")
