#!/usr/bin/env bash
# Assemble the PortMaster port folder for openxray from a build-aarch64 tree.
# Usage: tools/make_portmaster.sh [build-dir] [port-root]
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../../../.." && pwd)"
BIN_DIR="${1:-$REPO/bin/aarch64/Release}"
PORT_ROOT="${2:-$REPO/portmaster/ports/openxray/openxray}"

if [ ! -f "$BIN_DIR/xr_3da" ]; then
    echo "error: no xr_3da in $BIN_DIR. Build first." >&2
    exit 1
fi

LIBDIR="$PORT_ROOT/libs.aarch64"
mkdir -p "$LIBDIR"
rm -f "$LIBDIR"/*.so*
rm -f "$PORT_ROOT"/xr_3da.aarch64

# Engine executable (renamed with multiarch suffix)
cp "$BIN_DIR/xr_3da" "$PORT_ROOT/xr_3da.aarch64"

# Default file-system layout (same as res/fsgame.ltx). The user overrides this
# by dropping their own fsgame.ltx next to gamedata/.
if [ ! -f "$PORT_ROOT/fsgame.ltx" ]; then
    cp "$REPO/res/fsgame.ltx" "$PORT_ROOT/fsgame.ltx"
fi

# Bundle the engine-required gamedata overlay (configs, scripts and the
# OpenGL/GLES shader sources). Vanilla S.T.A.L.K.E.R. ships no gamedata/shaders/gl,
# so without this the GL renderer cannot compile any shader. User-provided
# gamedata (from the game archives or mods) overrides these files per-name.
rm -rf "$PORT_ROOT/gamedata"
cp -r "$REPO/res/gamedata" "$PORT_ROOT/gamedata"

# Engine shared libraries (loaded next to the executable / via LD_LIBRARY_PATH)
for so in "$BIN_DIR"/*.so; do
    cp "$so" "$LIBDIR/"
done

# Third-party runtime libraries required by the engine.
# Locate them in the local sysroot; copy only the needed sonames.
declare -a DEPS=(
    libSDL2-2.0.so.0
    libopenal.so.1
    libogg.so.0
    libvorbis.so.0
    libvorbisfile.so.3
    libtheora.so.1
    liblzo2.so.2
    libjpeg.so.62
    libz.so.1
)
SYSROOT_LIB="${SYSROOT_LIB:-/usr/aarch64-linux-gnu/lib}"

for lib in "${DEPS[@]}"; do
    # Prefer full path resolution via readelf/ldd later; simple search for now.
    found="$(find "$SYSROOT_LIB" /lib/aarch64-linux-gnu /usr/lib/aarch64-linux-gnu \
        -name "$lib*" 2>/dev/null | head -1 || true)"
    if [ -n "$found" ]; then
        cp -L "$found" "$LIBDIR/$lib"
    else
        echo "warning: $lib not found in sysroot; add it manually" >&2
    fi
done

# GLES/EGL libs from the SDL EGL link are resolved by the system driver;
# Mesa userspace drivers ship with the OS, so we do not bundle them.

# Sanitize cross-rootfs shared libraries: some sysroot builds (e.g. the
# distro libtheora) carry desktop-only NEEDED entries (libcairo/X11 stack)
# that do not exist on handheld firmware. The engine never calls those code
# paths, so pointing the NEEDED entry at an already-loaded system library is
# enough for the loader. Rewrites must not change the loader-visible ABI.
sanitize_needed() {
    local f="$1" needle="$2" repl="$3"
    if ! command -v python3 >/dev/null 2>&1; then
        echo "warning: python3 unavailable, cannot sanitize $f" >&2
        return 0
    fi
    python3 - "$f" "$needle" "$repl" <<'PY'
import sys
path, needle, repl = sys.argv[1], sys.argv[2].encode(), sys.argv[3].encode()
data = open(path, "rb").read()
n = data.count(needle + b"\0")
if n == 0:
    sys.exit(0)
# Guard: only rewrite when the string is unique, otherwise different
# libraries sharing the table slot (or code refs) could be corrupted.
if n != 1:
    print(f"!! {path}: '{needle}' appears {n} times, refusing to patch")
    sys.exit(1)
data = data.replace(needle + b"\0", repl + b"\0" + b"\0" * (len(needle) - len(repl)))
open(path, "wb").write(data)
print(f"  patch {path}: {needle} -> {repl}")
PY
}
for so in "$LIBDIR"/*.so*; do
    [ -e "$so" ] || continue
    sanitize_needed "$so" libcairo.so.2 libc.so.6
done

# Verify that the engine's DT_NEEDED entries are all resolvable from the port.
# Remaining unresolved entries must come from the device firmware.
echo "--- dependency check (warn only; O/S libs are expected to be absent) ---"
MISSING=0
while IFS= read -r needed; do
    if [ -z "$needed" ]; then
        continue
    fi
    if find "$LIBDIR" -name "$needed*" -print -quit | grep -q . ; then
        echo "  ok   $needed"
    elif find /usr/aarch64-linux-gnu/lib /lib/aarch64-linux-gnu /usr/lib/aarch64-linux-gnu \
        -name "$needed*" -print -quit 2>/dev/null | grep -q . ; then
        echo "  sys  $needed"
    else
        echo "  ???  $needed"
        MISSING=$((MISSING + 1))
    fi
done < <(readelf -d "$PORT_ROOT/xr_3da.aarch64" \
    "$LIBDIR"/*.so "$LIBDIR"/lib*.so* \
    | sed -n 's/.*(NEEDED).*Shared library: \[\(.*\)\]/\1/p' | sort -u)
[ "$MISSING" -eq 0 ] && echo "  (all NEEDED entries are bundled or in sysroot)"
[ "$MISSING" -ne 0 ] && echo "  note: $MISSING NEEDED entries not found - verify they are provided by the device firmware"

echo "Port assets staged in $PORT_ROOT"
ls -la "$PORT_ROOT"

# Build the PortMaster install archive (the port folder contents at the zip root).
PORT_DIR="$(dirname "$PORT_ROOT")"
PORT_ZIP="$PORT_DIR/openxray.zip"
rm -f "$PORT_ZIP"
cd "$PORT_DIR" || exit 1
if command -v zip >/dev/null 2>&1; then
    zip -r -q "$PORT_ZIP" OpenXRay.sh stalker.sh port.json gameinfo.xml README.md "$(basename "$PORT_ROOT")"/ -x "$(basename "$PORT_ROOT")/.gitignore"
    echo "PortMaster archive: $PORT_ZIP ($(du -h "$PORT_ZIP" | cut -f1))"
else
    echo "warning: 'zip' not found; port staged but no archive produced" >&2
fi