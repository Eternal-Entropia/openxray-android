#!/usr/bin/env bash
# Assemble the PortMaster port folder for openxray from a build-aarch64 tree.
# Usage: tools/make_portmaster.sh [build-dir] [port-root]
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
BUILD_DIR="${1:-$REPO/build-aarch64}"
BIN_DIR="$BUILD_DIR/bin/aarch64/Release"
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
    libtheora.so.0
    libtheoradec.so.1
    libtheoraenc.so.1
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
        cp -L "$found" "$LIBDIR/"
    else
        echo "warning: $lib not found in sysroot; add it manually" >&2
    fi
done

# GLES/EGL libs from the SDL EGL link are resolved by the system driver;
# Mesa userspace drivers ship with the OS, so we do not bundle them.

echo "Port assets staged in $PORT_ROOT"
ls -la "$PORT_ROOT"