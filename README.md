Persona 3 Portable Android

An unofficial Android port project for Persona 3 Portable, based on the native Android architecture of the original P5R Android project.

The goal is to adapt and improve the existing runtime and Android implementation to bring Persona 3 Portable to Android devices.

«Status: Experimental / Work in Progress»

Overview

P3P Android is a community-driven project focused on adapting the native Android runtime for Persona 3 Portable.

The project builds upon the foundation of the original P5R Android repository, with game-specific modifications, runtime adjustments, and Android-side improvements.

This is not a traditional Nintendo Switch emulator. It uses a native execution approach targeting ARM64 Android devices.

Features

The project currently includes:

- Stretch Screen – Screen scaling adjustments.
- FPS options – Frame-rate related improvements and configuration.
- Save System – Android save handling.
- Save Fix – Improvements to save functionality.
- Native Android Runtime – Adapted runtime components.
- Touch and Controller Support – Android input integration inherited and adapted from the project foundation.

Features and compatibility are still being developed and tested.

Project Structure

Directory| Description
"android/"| Android application and build scripts
"games/p3p/"| Persona 3 Portable specific implementation
"runtime/"| Native runtime components
"recomp/"| Recompilation-related components
"tools/"| Development and build utilities
"docs/"| Project documentation
"tests/"| Tests and validation

Building

Android builds are handled through GitHub Actions.

1. Open the repository's Actions tab.
2. Select the available Android build workflow.
3. Run the workflow on the desired branch.
4. Download the generated build artifact if the workflow provides one.

Build availability and compatibility may vary as development progresses.

Compatibility

The intended target is ARM64 Android devices.

Compatibility, performance, graphics behavior, and Android version support are still being evaluated.

A successful build does not guarantee that every device will run the application correctly.

Game Files

This repository does not distribute copyrighted game files or proprietary game assets.

Users must provide their own legally obtained copy of the game and are responsible for complying with applicable laws.

Credits

- "Original P5R Android Project" (https://github.com/Brandon04Gonzalez/p5r-android) – Original project foundation and architecture.
- "ATLUS" (https://www.atlus.com/) – Creator of Persona 3 Portable.
- SEGA – Publisher of Persona 3 Portable.

Special thanks to the original project's contributors and everyone helping with testing and development.

Disclaimer

This is an unofficial, community-made project.

It is not affiliated with, endorsed by, or associated with ATLUS, SEGA, or Nintendo.

Persona 3 Portable and all related trademarks and intellectual property belong to their respective owners.

License

This project is distributed under the MIT License, subject to the applicable licenses and notices of third-party components.

See "LICENSE" (LICENSE) and "THIRD_PARTY_NOTICES.md" (THIRD_PARTY_NOTICES.md) for details.
