# PERSONA 3 PORTABLE — ANDROID

**An unofficial Android project bringing Persona 3 Portable to ARM64 Android devices.**

<p align="center">
  <strong>EXPERIMENTAL · OPEN SOURCE · COMMUNITY DRIVEN</strong>
</p>

---

# ABOUT

P3P Android is an unofficial project focused on bringing **Persona 3 Portable** to Android devices.

Built upon the Android architecture of the original P5R Android project, this project combines a customized runtime, game-specific modifications, and a dedicated Android launcher.

Unlike a traditional Nintendo Switch emulator, P3P Android uses a native execution approach designed for ARM64 Android devices.

> **Development Status:** Experimental. Compatibility and performance are continuously being improved.

---

# DOWNLOAD

Get the latest APK from the official GitHub Releases page.

[**DOWNLOAD P3P ANDROID**](https://github.com/voidsx-dev/p3p-android/releases)

---

# FEATURES

- **Game Launcher** — Prepare, configure, and launch the game.
- **Performance Settings** — Configurable frame rate options.
- **Graphics Settings** — Resolution and texture quality options.
- **Stretch Screen** — Screen scaling for different display formats.
- **Touch Controls** — On-screen controls with customizable layouts.
- **Controller Support** — Compatible game controller input.
- **Save Management** — Import and export save files.
- **Automatic Language** — Game text and subtitles follow your device language.
- **Vulkan Renderer** — Graphics rendering through Vulkan.

---

# REQUIREMENTS

- ARM64 Android device.
- Android 10 or newer.
- Vulkan 1.1 support.
- File access permission.
- Your own legally obtained Persona 3 Portable Nintendo Switch game (version 1.0.0, NSP or XCI).
- Your own `prod.keys` file.

**Game files and encryption keys are not included.**

---

# INSTALLATION GUIDE

1. Download the latest APK from [GitHub Releases](https://github.com/voidsx-dev/p3p-android/releases).
2. Install the APK on your Android device.
3. Open the app and grant the requested file access permission.
4. Select your game file and `prod.keys`.
5. Tap **Prepare the game** and wait for the process to finish.
6. Once preparation is complete, tap **Play**.

Game preparation is normally required only once, unless the game data needs to be prepared again.

---

# COMPATIBILITY & PERFORMANCE

Performance depends on your device's hardware and software:

- CPU and GPU capabilities.
- Available RAM.
- Vulkan driver compatibility.
- Device temperature and thermal limits.
- Graphics settings and rendering resolution.

Lower-end devices may experience frame drops, longer loading times, or stuttering.

Compatibility is not guaranteed on every Android device.

---

# KNOWN LIMITATIONS

As an experimental project, some issues may occur:

- Performance drops in demanding scenes.
- Longer loading times on devices with limited resources.
- Device-specific graphical issues.
- Different compatibility levels across Android devices.

Bug reports and user feedback are welcome.

---

# BUILDING FROM SOURCE

Android APK builds are automated through GitHub Actions.

1. Fork or clone this repository.
2. Open the repository's **Actions** tab.
3. Select the `build-apk` workflow.
4. Run the workflow on the desired branch.
5. After a successful build, download the `p3p-apk` artifact.

Build dependencies and requirements may change during development.

---

# PROJECT STRUCTURE

| Directory | Description |
|:--|:--|
| `android/` | Android application and build scripts |
| `games/p3p/` | Persona 3 Portable implementation |
| `runtime/` | Native runtime components |
| `recomp/` | Recompilation-related components |
| `tools/` | Development and build utilities |
| `docs/` | Project documentation |
| `tests/` | Tests and validation |

---

# CREDITS & ACKNOWLEDGEMENTS

P3P Android builds upon the architecture and work of the original P5R Android project.

- [Original P5R Android Project](https://github.com/Brandon04Gonzalez/p5r-android) — Original Android architecture and project foundation.
- [ATLUS](https://www.atlus.com/) — Creator of Persona 3 Portable.
- **SEGA** — Publisher of Persona 3 Portable.

Special thanks to the original developers, contributors, and everyone helping test and improve this project.

---

# COMMUNITY & SUPPORT

Have questions, feedback, or want to share your experience?

Join the **P4G | Android Community** on Discord.

[**JOIN THE DISCORD COMMUNITY**](https://discord.gg/yF6FtNK6rM)

---

# DISCLAIMER

P3P Android is an unofficial, community-developed project.

It is not affiliated with, endorsed by, or associated with ATLUS, SEGA, or Nintendo.

Persona 3 Portable, its characters, trademarks, and related intellectual property belong to their respective owners.

This repository does not distribute copyrighted game files, game assets, or encryption keys. Users are responsible for providing their own legally obtained files and complying with applicable laws.

---

# LICENSE

This project is distributed under the MIT License, subject to the licenses and notices of third-party components.

See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details.
