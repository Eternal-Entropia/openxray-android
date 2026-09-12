#!/usr/bin/env python3
"""Fetch a standalone GN binary (via public CIPD endpoint) into the project.

Puts gn.exe into build_angle/tools/ so nothing is installed system-wide.
"""
import io
import sys
import urllib.request
import zipfile
from pathlib import Path

WORKSPACE_ROOT = Path(__file__).resolve().parent.parent
TOOLS_DIR = WORKSPACE_ROOT / "build_angle" / "tools"
GN_URL = "https://chrome-infra-packages.appspot.com/dl/gn/gn/windows-amd64/+/latest"


def main() -> int:
    TOOLS_DIR.mkdir(parents=True, exist_ok=True)
    gn_exe = TOOLS_DIR / "gn.exe"
    if gn_exe.exists():
        print(f"[+] GN already present: {gn_exe}")
        return 0
    print(f"[*] Downloading GN from CIPD into {TOOLS_DIR} ...")
    with urllib.request.urlopen(GN_URL, timeout=300) as r:
        data = r.read()
    print(f"[*] Downloaded {len(data)} bytes, extracting...")
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        z.extractall(TOOLS_DIR)
    if not gn_exe.exists():
        print("[!] gn.exe not found after extraction!", file=sys.stderr)
        return 1
    print(f"[+] GN installed: {gn_exe}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
