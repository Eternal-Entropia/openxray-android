## Notes

Thanks to the [OpenXRay](https://github.com/OpenXRay/xray-16) project and the [openxray-android](https://github.com/Eternal-Entropia/openxray-android) fork for making this engine recreation possible.

This port bundles the OpenXRay engine build for ARM64 Linux with OpenGL ES 3.2. It requires the original S.T.A.L.K.E.R. game data, which is **not** included.

## Requirements

Place the following S.T.A.L.K.E.R. game files into the `openxray/` folder:

- `fsgame.ltx`
- `gamedata/` (full game data, including `gamedata/shaders/gl` for OpenGL support)

Works with Shadow of Chernobyl, Clear Sky and Call of Pripyat.

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