#!/bin/bash

# S.T.A.L.K.E.R.: OpenXRay - PortMaster launch script

XDG_DATA_HOME=${XDG_DATA_HOME:-$HOME/.local/share}

if [ -d "/opt/system/Tools/PortMaster/" ]; then
  controlfolder="/opt/system/Tools/PortMaster"
elif [ -d "/opt/tools/PortMaster/" ]; then
  controlfolder="/opt/tools/PortMaster"
elif [ -d "/mnt/mmc/MUOS/PortMaster" ]; then
  controlfolder="/mnt/mmc/MUOS/PortMaster"
elif [ -d "$XDG_DATA_HOME/PortMaster/" ]; then
  controlfolder="$XDG_DATA_HOME/PortMaster"
else
  controlfolder="/roms/ports/PortMaster"
fi

source $controlfolder/control.txt

[ -f "${controlfolder}/mod_${CFW_NAME}.txt" ] && source "${controlfolder}/mod_${CFW_NAME}.txt"

get_controls

# Game data root. PortMaster (standard layout) keeps the files in
# /ports/openxray/. MuOS uses a separate launcher (stalker.sh) that exports
# XRAY_GAMEDIR pointing at /mnt/sdcard/roms/Ports/Stalker.
if [ -n "${XRAY_GAMEDIR:-}" ]; then
  GAMEDIR="$XRAY_GAMEDIR"
  case "$GAMEDIR" in
    */) ;;
    *) GAMEDIR="$GAMEDIR/" ;;
  esac
elif [ -d "/mnt/mmc/MUOS/PortMaster" ] && [ -d "/mnt/sdcard/roms/Ports/Stalker" ]; then
  GAMEDIR="/mnt/sdcard/roms/Ports/Stalker/"
else
  GAMEDIR=/$directory/ports/openxray/
fi
CONFDIR="$GAMEDIR/conf/"

mkdir -p "$GAMEDIR/conf"
cd "$GAMEDIR"

> "$GAMEDIR/log.txt" && exec > >(tee "$GAMEDIR/log.txt") 2>&1

export XDG_DATA_HOME="$CONFDIR"
export LD_LIBRARY_PATH="$GAMEDIR/libs.${DEVICE_ARCH}:$LD_LIBRARY_PATH"
export SDL_GAMECONTROLLERCONFIG="$sdl_controllerconfig"

if [ ! -f "$GAMEDIR/conf/user.ltx" ] && \
   [ -z "$(ls "$GAMEDIR"/*.db* 2>/dev/null)" ] && \
   [ ! -d "$GAMEDIR/gamedata/spawns" ] && \
   [ ! -d "$GAMEDIR/gamedata/levels" ]; then
  echo "S.T.A.L.K.E.R. game data is missing."
  echo "Place the original game archives (gamedata.db*, like from Steam/GOG)"
  echo "or an unpacked gamedata/ folder into:" "$GAMEDIR"
  sleep 5
  exit 1
fi

$GPTOKEYB "xr_3da.${DEVICE_ARCH}" &
pm_platform_helper "$GAMEDIR/xr_3da.${DEVICE_ARCH}"

./xr_3da.${DEVICE_ARCH} -fsltx "$GAMEDIR/fsgame.ltx" -rgl -nosplash

pm_finish