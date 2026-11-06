## Persona 3 Portable Android

An unofficial Android port project for Persona 3 Portable, based on the native Android architecture of the original P5R Android project.

The goal is to adapt and improve the existing runtime and Android implementation to bring Persona 3 Portable to Android devices.

«Status: Experimental / Work in Progress»

## Download

Get the latest APK from the "Releases page" (https://github.com/voidsx-dev/p3p-android/releases).

## Overview

P3P Android is a community-driven project focused on adapting the native Android runtime for Persona 3 Portable.

The project builds upon the foundation of the original P5R Android repository, with game-specific modifications, runtime adjustments, and Android-side improvements.

This is not a traditional Nintendo Switch emulator. It uses a native execution approach targeting ARM64 Android devices.

## Features

- Launcher – Game setup, configuration and launching.
- Frame rate – Configurable frame rate options.
- Graphics settings – Resolution and texture quality options.
- Stretch screen – Fullscreen scaling option.
- Touch and controller support – On-screen controls with a layout editor, plus gamepad input.
- Save management – Save import and export.
- Automatic language – Game text and subtitles follow your device language.
- Native Android runtime – Adapted runtime components with a Vulkan renderer.

Features and compatibility are still being developed and tested.

## Requirements

- ARM64 Android device, Android 10 or newer.
- Vulkan 1.1 support.
- All-files access (the launcher asks for it).
- Your own legally obtained copy of Persona 3 Portable (Nintendo Switch, version 1.0.0, NSP or XCI) and your own "prod.keys".

## Installation

1. Download the APK from the "Releases page" (https://github.com/voidsx-dev/p3p-android/releases).
2. Install and open the application.
3. Grant all-files access when requested.
4. Select your game file and "prod.keys".
5. Tap Prepare the game. This is required only once and may take a while.
6. Tap Play.

## Known Issues

- Performance depends heavily on the device's GPU and CPU.
- Demanding scenes may experience frame rate drops, especially on lower-end devices.
- Devices with limited RAM may experience stuttering while loading.
- Higher frame rate settings require more powerful hardware.
- Compatibility and performance may vary between devices.

## Project Structure

Directory| Description
"android/"| Android application and build scripts
"games/p3p/"| Persona 3 Portable specific implementation
"runtime/"| Native runtime components
"recomp/"| Recompilation-related components
"tools/"| Development and build utilities
"docs/"| Project documentation
"tests/"| Tests and validation

## Building (for Developers)

Android builds are handled through GitHub Actions.

1. Open the repository's Actions tab.
2. Select the build-apk workflow.
3. Click Run workflow on the desired branch.
4. Download the p3p-apk artifact when it finishes.

Build availability and compatibility may vary as development progresses.

## Compatibility

The intended target is ARM64 Android devices.

Compatibility, performance, graphics behavior, and Android version support are still being evaluated.

A successful build does not guarantee that every device will run the application correctly.

## Game Files

This repository does not distribute copyrighted game files or proprietary game assets.

Users must provide their own legally obtained copy of the game and are responsible for complying with applicable laws.

## Credits

- "Original P5R Android Project" (https://github.com/Brandon04Gonzalez/p5r-android) – Original project foundation and architecture.
- "ATLUS" (https://www.atlus.com/) – Creator of Persona 3 Portable.
- SEGA – Publisher of Persona 3 Portable.

Special thanks to the original project's contributors and everyone helping with testing and development.

## Community & Support

Need help, have questions, or want to share your gameplay?

Join the P4G | Android Community Discord server to connect with other users, share your gameplay, discuss Android ports, and get community support.

"Join the Discord server" (https://discord.gg/yF6FtNK6rM)

## Disclaimer

This is an unofficial, community-made project.

It is not affiliated with, endorsed by, or associated with ATLUS, SEGA, or Nintendo.

Persona 3 Portable and all related trademarks and intellectual property belong to their respective owners.

## License

This project is distributed under the MIT License, subject to the applicable licenses and notices of third-party components.

See "LICENSE" (LICENSE) and "THIRD_PARTY_NOTICES.md" (THIRD_PARTY_NOTICES.md) for details.
