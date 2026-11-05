# Persona 3 Portable Android (P3P)

**Version:** 0.1.0  
**Status:** Experimental — Early Development

An Android port of Persona 3 Portable, focused on bringing the game to mobile devices with customizable graphics, performance settings, touchscreen controls, and save management.

## Features

- Multiple resolution options: 480p, 720p, and 1080p.
- Stretch Screen support.
- Graphics quality settings: Low, Medium, and High.
- FPS options: 30 and 60 FPS.
- FPS counter.
- Customizable touchscreen controls.
- Adjustable touch control size and opacity.
- Save import and export.
- Android input support.
- Game and shader preparation.

## Requirements

- Android device with ARM64 support.
- Android 11 or newer recommended.
- Vulkan 1.1 support.
- Your own legally obtained copy of Persona 3 Portable (version 1.0.0).
- Required game keys and files.

## Installation

1. Download the latest APK from the GitHub Releases page when available.
2. Install the APK on your Android device.
3. Prepare your own game files.
4. Follow the in-app instructions to import the game.

Development builds may also be available through GitHub Actions artifacts.

## Building

The project includes Android build scripts:

- `android/build_apk.sh`
- `android/build_apk.ps1`

You can also use GitHub Actions to build the APK.

## Project Structure

- `android/` — Android application and build scripts.
- `games/p3p/` — Persona 3 Portable game integration.
- `runtime/` — Game runtime and platform support.
- `tools/` — Build and compatibility tools.

## Roadmap

- Further performance optimizations without sacrificing graphical quality.
- Improved Android compatibility.
- Save system improvements.
- Additional customization options.
- Mod Manager support planned for a future release.

## Credits

- Original game: ATLUS / SEGA.
- Android port development inspired by the P5R Android project.

## Disclaimer

This is an unofficial fan-made project. It is not affiliated with, endorsed by, or associated with ATLUS or SEGA.

This project does not distribute copyrighted game files, assets, or keys. Users must provide their own legally obtained game files.

## License

This project uses the MIT License. See `LICENSE` and `THIRD_PARTY_NOTICES` for details.
