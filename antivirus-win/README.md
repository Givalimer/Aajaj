# Aajaj Defender 2026 Ultra

A joke "antivirus" for Windows. Pure Win32 + GDI, no runtime deps, single `.exe`.

**Parody software. It does not scan anything. It does not protect anything. It is a meme.**

## Features

- Fake deep scan with animated progress bar and live scrolling file log
- Random meme "threats" like `your_mom.exe`, `skibidi_toilet.dll`, `amogus_miner.bat`, `ohio_rizz.scr`
- Meme signature names (`Trojan.Skibidi.Gen`, `Worm.Ohio.Rizz.A`, ...)
- GDI effects:
  - window shake
  - red flash borders on threat hit
  - color invert
  - green "matrix" rain overlay (quarantine button)
- Sounds via `PlaySound` aliases + `Beep` melodies (Windows error, bruh, vine-boom-ish, metal-pipe-ish)
- Joke buttons:
  - **REMOVE THREATS** -> storm of fake popup dialogs
  - **QUARANTINE** -> triggers matrix rain + smug popup
  - **PANIC!!!** -> combo of every effect at once
  - **TROLL CURSOR** -> swaps to a procedurally-drawn custom cursor with a red X
- DPI-aware (PerMonitorV2), common-controls v6 visual styles
- Single static `.exe`, no installer

## Download

Grab the latest `AajajDefender.exe` from the CI artifacts:

1. Open the repo on GitHub -> **Actions** tab
2. Pick the latest **antivirus-win** run on your branch
3. Download the `AajajDefender-windows-x64` artifact
4. Unzip, run `AajajDefender.exe`

Tags pushed to `refs/tags/*` will also attach the `.exe` to a GitHub Release automatically.

## Build locally (Windows)

Requires: Visual Studio 2022 (Desktop C++ workload) or standalone Build Tools, and CMake 3.16+.

```pwsh
cd antivirus-win
cmake -S . -B build -G "Visual Studio 17 2022" -A x64
cmake --build build --config Release
.\build\Release\AajajDefender.exe
```

Or with Ninja inside an MSVC dev shell:

```pwsh
cd antivirus-win
cmake -S . -B build -G "Ninja" -DCMAKE_BUILD_TYPE=Release
cmake --build build
.\build\AajajDefender.exe
```

## Build with MinGW-w64 (optional, e.g. on Linux cross-build)

```bash
cd antivirus-win
x86_64-w64-mingw32-cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
```

## Layout

```
antivirus-win/
  CMakeLists.txt
  src/main.cpp          # everything: window, painting, scan engine, effects, sounds
  res/
    resources.rc        # version info + manifest reference
    app.manifest        # common-controls v6, DPI-aware
```

## Legal / disclaimer

This program is a parody. It does **not** modify files, does **not** touch the registry, does **not** scan anything, does **not** quarantine anything. All "threats" are randomly generated strings. All "actions" are message boxes and GDI effects in its own window. Do not use it to actually defend anything. You've been warned, skibidi.
