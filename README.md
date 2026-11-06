Persona 3 Portable — Android

<p align="center">
  An unofficial Android project bringing <strong>Persona 3 Portable</strong> to ARM64 Android devices.
</p><p align="center">
  <strong>Experimental · Open Source · Community Driven</strong>
</p>---

About

P3P Android is an unofficial project focused on adapting Persona 3 Portable to run natively on Android devices.

Built upon the Android architecture of the original P5R Android project, it combines a customized runtime, game-specific modifications, and an Android launcher to provide a portable way to experience Persona 3 Portable.

This project does not work like a traditional Nintendo Switch emulator. It uses a native execution approach designed for ARM64 Android devices.

«Development status: Experimental. Compatibility and performance are still being improved.»

Download

<p align="center">
  <a href="https://github.com/voidsx-dev/p3p-android/releases">
    <strong>Download the latest APK</strong>
  </a>
</p>All official builds are published on the GitHub Releases page.

Features

- Game Launcher — Game preparation, configuration, and launching.
- Performance Options — Configurable frame rate settings.
- Graphics Configuration — Resolution and texture quality options.
- Stretch Screen — Screen scaling for different display formats.
- Touch Controls — On-screen controls with customizable layouts.
- Controller Support — Input support for compatible game controllers.
- Save Management — Save import and export.
- Automatic Language — Game text and subtitles follow the device language.
- Vulkan Renderer — Graphics rendering through Vulkan.

Features may vary depending on device compatibility and project development.

Requirements

Before installing, make sure you have:

- An ARM64 Android device.
- Android 10 or newer.
- Vulkan 1.1 support.
- Permission to access files and folders.
- Your own legally obtained copy of Persona 3 Portable for Nintendo Switch (version 1.0.0, NSP or XCI).
- Your own "prod.keys" file.

Important: Game files and encryption keys are not included in this project.

Installation Guide

1. Download the latest APK from "GitHub Releases" (https://github.com/voidsx-dev/p3p-android/releases).
2. Install the APK on your Android device.
3. Open the application and grant the requested file access permission.
4. Select your Persona 3 Portable game file and "prod.keys".
5. Tap Prepare the game and wait for the process to finish.
6. Once preparation is complete, tap Play.

The preparation process is normally required only once, unless the game data needs to be prepared again.

Compatibility & Performance

Performance depends on several factors, including:

- CPU and GPU capabilities.
- Available RAM.
- Vulkan driver compatibility.
- Device thermal limits.
- Game settings and rendering resolution.

Lower-end devices may experience frame drops, loading stutters, or other performance issues.

Not every Android device is guaranteed to work correctly.

Known Limitations

As the project is still experimental, users may encounter:

- Performance drops in demanding scenes.
- Longer loading times on devices with limited resources.
- Device-specific graphical issues.
- Inconsistent compatibility across Android devices.

Reports and feedback from users are valuable for improving the project.

Building from Source

The project uses GitHub Actions to automate Android APK builds.

For developers who want to build the project:

1. Fork or clone this repository.
2. Review the repository's build configuration.
3. Open the Actions tab.
4. Select the "build-apk" workflow.
5. Run the workflow on the desired branch.
6. Download the generated "p3p-apk" artifact after a successful build.

Build requirements and dependencies may change during development.

Project Structure

Directory| Purpose
"android/"| Android application and build scripts
"games/p3p/"| Persona 3 Portable-specific implementation
"runtime/"| Native runtime components
"recomp/"| Recompilation-related components
"tools/"| Development and build utilities
"docs/"| Project documentation
"tests/"| Tests and validation

Credits & Acknowledgements

This project builds upon the work and architecture of the original P5R Android project.

- "Original P5R Android Project" (https://github.com/Brandon04Gonzalez/p5r-android) — Original Android architecture and project foundation.
- "ATLUS" (https://www.atlus.com/) — Creator of Persona 3 Portable.
- SEGA — Publisher of Persona 3 Portable.

Special thanks to the original developers, contributors, and everyone who helps test and improve this project.

Community & Support

Have questions, want to share your experience, or need help?

Join the P4G | Android Community on Discord to connect with other users and discuss Android game ports.

<p align="center">
  <a href="https://discord.gg/yF6FtNK6rM">
    <strong>Join the Discord Community</strong>
  </a>
</p>Disclaimer

P3P Android is an unofficial, community-developed project.

It is not affiliated with, endorsed by, or associated with ATLUS, SEGA, or Nintendo.

Persona 3 Portable, its characters, trademarks, and related intellectual property belong to their respective owners.

This repository does not distribute copyrighted game files, game assets, or encryption keys. Users are responsible for providing their own legally obtained game files and complying with applicable laws.

License

This project is distributed under the MIT License, subject to the licenses and notices of third-party components.

See "LICENSE" (LICENSE) and "THIRD_PARTY_NOTICES.md" (THIRD_PARTY_NOTICES.md) for details.
