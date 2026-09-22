#!/bin/bash

# MuOS launcher for S.T.A.L.K.E.R. (OpenXRay) PortMaster port.
#
# Install layout (MuOS):
#   /mnt/sdcard/roms/Ports/stalker.sh      <- this file (the launcher the frontend runs)
#   /mnt/sdcard/roms/Ports/Stalker/        <- the port data (openxray/ contents,
#                                             including the original gamedata.db* archives)
# PortMaster itself is expected at /mnt/mmc/MUOS/PortMaster.
#
# This wrapper locates the data folder next to itself and forwards to the
# shared launcher script inside it. The port folder name is case-sensitive
# (Stalker), keep it as-is.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DATA_DIR="$SCRIPT_DIR/Stalker"

if [ ! -f "$DATA_DIR/OpenXRay.sh" ]; then
    echo "S.T.A.L.K.E.R. data folder not found at $DATA_DIR"
    echo "Expected: $SCRIPT_DIR/Stalker/ with OpenXRay.sh and xr_3da.aarch64"
    sleep 5
    exit 1
fi

export XRAY_GAMEDIR="$DATA_DIR"
exec bash "$DATA_DIR/OpenXRay.sh" "$@"