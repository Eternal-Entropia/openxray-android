## Notes

Thanks to the [OpenXRay](https://github.com/OpenXRay/xray-16) project and the [openxray-android](https://github.com/Eternal-Entropia/openxray-android) fork for making this engine recreation possible.

This port bundles the OpenXRay engine build for ARM64 Linux with OpenGL ES 3.1 and the engine's required `gamedata` overlay (configs, scripts, OpenGL shaders). It requires the original S.T.A.L.K.E.R. game data **archives**, which are **not** included.

## Requirements

Copy the original game archives into the `openxray/` folder (alongside `fsgame.ltx`):

- `gamedata.db0` ... `gamedata.dbd` (the 5.5 GB archive set from Steam/GOG Shadow of Chernobyl)

The engine reads the archives directly; a separate `gamedata/` folder is provided by the port and overlaid per-file (your mods placed in `openxray/gamedata/` win over it).

Works with Shadow of Chernobyl, Clear Sky and Call of Pripyat.

## MuOS (manual layout, e.g. Anbernic RG40XX H)

MuOS runs ports from `/mnt/sdcard/roms/Ports/`. Use the layout the launcher expects:

1. Copy the whole `openxray/` folder (engine + libs + `gamedata/` overlay + `fsgame.ltx`) to `/mnt/sdcard/roms/Ports/Stalker/`.
2. Drop the original game archives `gamedata.db0` ... `gamedata.dbd` into `/mnt/sdcard/roms/Ports/Stalker/`.
3. Copy the supplied `stalker.sh` to `/mnt/sdcard/roms/Ports/stalker.sh`.
   (On MuOS a `.sh` launcher next to the port folder is what the frontend shows and runs.)
4. Ensure PortMaster is installed at `/mnt/mmc/MUOS/PortMaster` (it provides `gptokeyb`, the controller config and `control.txt`).

The port auto-detects the MuOS setup by the PortMaster location and the `Stalker/` folder; no editing needed.

The standard `openxray.zip` installs via the PortMaster frontend into `/mnt/sdcard/roms/Ports/openxray/` instead — that also works on MuOS, just with the default GAMEDIR detection.

## Controls

| Button | Action |
|--|--|
| D-Pad / Left Stick | Move |
| A | Jump / Confirm |
| B | Use / Cancel |
| X | Reload |
| Y | Crouch |
| Start | Pause / Menu |

## Build (aarch64)

The engine is cross-compiled for ARM64 Linux using the bundled CMake toolchain:

```bash
cmake -S . -B build-aarch64 -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=cmake/toolchains/aarch64-linux-gnu.cmake \
  -DCMAKE_BUILD_TYPE=Release
ninja -C build-aarch64
```

## Compile

```bash
git clone https://github.com/lesad710/openxray-android.git
cd openxray-android
git checkout arm-linux
cmake -S . -B build-aarch64 -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=cmake/toolchains/aarch64-linux-gnu.cmake \
  -DCMAKE_BUILD_TYPE=Release
cmake --build build-aarch64
```

See `tools/` in the port folder for the packaging script.