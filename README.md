# Persona 3 Portable Android

**A native Android port of Persona 3 Portable, based on the P5R Android project.**

## About

P3P Android is an experimental project based on [P5R Android](https://github.com/Brandon04Gonzalez/p5r-android), aiming to bring **Persona 3 Portable (Nintendo Switch version)** to Android devices through native execution.

The goal is to adapt the existing native Android runtime, loader, and system implementations to support Persona 3 Portable.

Unlike traditional Nintendo Switch emulation, this project aims to run the game's code natively on Android, using the device's ARM64 CPU and Vulkan graphics support.

## Project Goals

- Port Persona 3 Portable to Android.
- Adapt the existing native execution architecture.
- Reuse and modify the upstream Android runtime where applicable.
- Implement the system functionality required by Persona 3 Portable.
- Support native Android input, audio, and graphics.
- Investigate compatibility and performance across Android devices.

## Development Status

<badge color="warning">Experimental — Work in Progress</badge>

This project is in its early development stages.

The existing P5R Android implementation serves as the foundation, but substantial modifications may be required to support Persona 3 Portable.

Compatibility and performance have not yet been established.

## Technical Direction

The project builds upon the architecture of P5R Android:

- **Native Execution:** Running game code directly on ARM64 hardware.
- **Android Runtime:** Adapting Nintendo Switch system services to Android.
- **Graphics:** Vulkan-based rendering.
- **Audio:** Native Android audio integration.
- **Input:** Android touchscreen and controller support.

These components are subject to modification as development progresses.

## Requirements

The intended target platform is Android on ARM64 devices.

Final Android version, hardware requirements, and Vulkan compatibility requirements are still to be determined.

## Disclaimer

This project is an unofficial fan-made development project.

It is not affiliated with, endorsed by, or associated with ATLUS, SEGA, or Nintendo.

Persona 3 Portable and all related intellectual property belong to their respective owners.

No copyrighted game files or proprietary game assets are intended to be distributed through this repository.

Users are responsible for their own game copies.

## Credits

- **Original Android project:** [Brandon04Gonzalez/p5r-android](https://github.com/Brandon04Gonzalez/p5r-android)
- **ATLUS / SEGA:** Original creators of Persona 3 Portable.
- **Nintendo:** Nintendo Switch platform.

Special thanks to the original P5R Android project and its contributors for providing the foundation for this work.

## License

This project is based on P5R Android, originally released under the MIT License.

See the [LICENSE](LICENSE) file for licensing terms and attribution.
