# Persona 3 Portable Android

An unofficial Android port project for Persona 3 Portable, based on the native Android architecture of the original P5R Android project.

The goal is to adapt and improve the existing runtime and Android implementation to bring Persona 3 Portable to Android devices.

> **Status: Experimental / Work in Progress**

## Download

Get the latest APK from the [Releases page](https://github.com/voidsx-dev/p3p-android/releases).

## Overview

P3P Android is a community-driven project focused on adapting the native Android runtime for Persona 3 Portable.

The project builds upon the foundation of the original P5R Android repository, with game-specific modifications, runtime adjustments, and Android-side improvements.

This is not a traditional Nintendo Switch emulator. It uses a native execution approach targeting ARM64 Android devices.

## Features

- **Launcher** – Game setup, options and a Play button.
- **Frame rate** – 30 fps (default) or 60 fps.
- **Resolution and texture quality** – 480p / 720p / 1080p and texture quality presets.
- **Stretch screen** – Fullscreen scaling option.
- **Touch and controller support** – On-screen controls with a layout editor, plus gamepad input.
- **Saves** – Save import and export.
- **Automatic language** – Game text and subtitles follow your device language.
- **Quit confirmation** – Pressing Back in-game asks before returning to the launcher.
- **Native Android runtime** – Adapted runtime components with a Vulkan renderer.

Features and compatibility are still being developed and tested.

## Requirements

- ARM64 Android device, Android 10 or newer
- Vulkan 1.1 support
- All-files access (the launcher asks for it)
- Your own legally obtained copy of Persona 3 Portable (Switch, version 1.0.0, NSP or XCI) and your own `prod.keys`

## Installation

1. Download `p3p.apk` from the [Releases page](https://github.com/voidsx-dev/p3p-android/releases) and install it.
2. Open the app and grant all-files access.
3. Choose your game file, then your `prod.keys`.
4. Tap **Prepare the game** (first time only; it may take a while).
5. Tap **Play**.

## Known Issues

- Performance depends heavily on the GPU. On low-end GPUs (for example Mali-G57 MC2), heavy effect scenes such as persona summons can drop well below 30 fps. Optimization for these GPUs is planned.
- Devices with about 4 GB of RAM may stutter while loading. Closing other apps helps.
- 60 fps needs a strong GPU. Use 30 fps if it is unstable.
- Releases are currently signed with a debug key. Uninstall the previous build before installing a new one, and back up your saves first.

## Project Structure

| Directory | Description |
| --- | --- |
| `android/` | Android application and build scripts |
| `games/p3p/` | Persona 3 Portable specific implementation |
| `runtime/` | Native runtime components |
| `recomp/` | Recompilation-related components |
| `tools/` | Development and build utilities |
| `docs/` | Project documentation |
| `tests/` | Tests and validation |

## Building (for developers)

Android builds are handled through GitHub Actions.

1. Open the repository's **Actions** tab.
2. Select the **build-apk** workflow.
3. Click **Run workflow** on the desired branch.
4. Download the **p3p-apk** artifact when it finishes (a zip containing `p3p.apk`).

Build availability and compatibility may vary as development progresses.

## Compatibility

The intended target is ARM64 Android devices.

Compatibility, performance, graphics behavior, and Android version support are still being evaluated.

A successful build does not guarantee that every device will run the application correctly.

## Game Files

This repository does not distribute copyrighted game files or proprietary game assets.

Users must provide their own legally obtained copy of the game and are responsible for complying with applicable laws.

## Credits

- [Original P5R Android Project](https://github.com/Brandon04Gonzalez/p5r-android) – Original project foundation and architecture.
- [ATLUS](https://www.atlus.com/) – Creator of Persona 3 Portable.
- SEGA – Publisher of Persona 3 Portable.

Special thanks to the original project's contributors and everyone helping with testing and development.

## Community & Support

Need help, have questions, or want to share your gameplay?

Join the P4G | Android Community Discord server to connect with other users, share your gameplay, discuss Android ports, and get community support.

"Join our Discord Server" (https://discord.gg/yF6FtNK6rM)

## Disclaimer

This is an unofficial, community-made project.

It is not affiliated with, endorsed by, or associated with ATLUS, SEGA, or Nintendo.

Persona 3 Portable and all related trademarks and intellectual property belong to their respective owners.

## License

This project is distributed under the MIT License, subject to the applicable licenses and notices of third-party components.

See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details.
