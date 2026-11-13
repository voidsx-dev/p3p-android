<h1 align="center">PERSONA 3 PORTABLE — ANDROID</h1>

<p align="center">
  <strong>An unofficial Android project bringing Persona 3 Portable to ARM64 devices.</strong>
</p>

<p align="center">
  <a href="https://github.com/voidsx-dev/p3p-android/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/voidsx-dev/p3p-android?label=release"></a>
  <img alt="Android 10+" src="https://img.shields.io/badge/Android-10%2B-3DDC84">
  <img alt="ARM64" src="https://img.shields.io/badge/arch-ARM64-blue">
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-green"></a>
</p>

<p align="center">
  <strong>EXPERIMENTAL · OPEN SOURCE · COMMUNITY DRIVEN</strong>
</p>

<p align="center">
  <a href="https://github.com/voidsx-dev/p3p-android/releases"><strong>DOWNLOAD</strong></a> ·
  <a href="CHANGELOG.md"><strong>CHANGELOG</strong></a> ·
  <a href="https://discord.gg/yF6FtNK6rM"><strong>DISCORD</strong></a>
</p>

---

## About

P3P Android is an unofficial project focused on bringing **Persona 3 Portable** to Android devices.

It is built upon the Android architecture of the original P5R Android project and combines a customized runtime, game-specific modifications, and a dedicated Android launcher. Unlike a traditional Nintendo Switch emulator, it uses a native execution approach designed for ARM64 Android devices.

> **Development status:** experimental. Compatibility and performance keep improving with every release.

---

## Download

Get the latest APK from the [GitHub Releases](https://github.com/voidsx-dev/p3p-android/releases) page. See the [changelog](CHANGELOG.md) for what changed in each version.

---

## Features

- **Game launcher** — prepare, configure, and launch the game from one screen, available in 10 languages.
- **Display options** — 720p or 1080p rendering, optional FSR sharp upscaling, and a fullscreen stretch mode.
- **Performance options** — 30 or 60 fps, a CPU cores setting, and an FPS counter.
- **Audio** — stereo downmix with a soft limiter against clipping, a selectable audio output mode, and automatic recovery when headphones are connected or disconnected.
- **Touch controls** — on-screen buttons with adjustable size, opacity, and a layout editor.
- **Controller support** — compatible game controllers.
- **Save management** — import and export your save files.
- **Mod manager** — manage RomFS mods from inside the app.
- **Device diagnostics** — a read-only report of your device, memory, GPU, and game data.
- **Automatic language** — game text and subtitles follow your device language.
- **Vulkan renderer** — graphics rendering through Vulkan.

---

## Requirements

- An ARM64 Android device running **Android 10 or newer**.
- **Vulkan 1.1** support.
- File access permission.
- Your own legally obtained **Persona 3 Portable** Nintendo Switch game (**version 1.0.0**, NSP or XCI).
- Your own `prod.keys` file.

**Game files and encryption keys are not included.**

---

## Installation

1. Download the latest APK from [GitHub Releases](https://github.com/voidsx-dev/p3p-android/releases).
2. Install the APK on your Android device.
3. Open the app and grant the requested file access permission.
4. Select your game file and your `prod.keys`.
5. Tap **Prepare the game** and wait for it to finish.
6. Tap **Play**.

Preparation is normally needed only once, unless the game data has to be prepared again.

---

## Settings guide

| Setting | Options | Notes |
|:--|:--|:--|
| Resolution | 720p (default), 1080p | 720p is the handheld resolution and runs best. 1080p needs a powerful phone. |
| Sharp upscaling (FSR) | Off (default), On | Sharper scaling to your screen. It uses some GPU. |
| Fullscreen (stretch) | Off (default), On | Fills the whole screen instead of keeping the 16:9 picture. |
| Frame rate | 30 fps (default), 60 fps | On modest phones, 30 fps is usually smoother than an unstable 60. |
| CPU cores | Automatic (default), Prefer the fast ones | Experimental. It may help on phones with few fast cores. |
| FPS counter | Off (default), On | Shows the current frame rate. |
| Audio output | Low latency (default), Normal | If the sound breaks up on some outputs, such as headphones, try Normal. |
| On-screen controls | Show or hide, size, opacity, layout editor | Adjust the touch buttons to your hands. |

---

## Performance tips

Performance depends on your device's CPU, GPU, available RAM, Vulkan driver, and thermal limits. On weaker devices:

- Use **720p** and **30 fps**.
- Close other apps before playing: the game needs free RAM, and low memory can cause stutter.
- Keep the device cool. Thermal throttling lowers performance during long sessions.
- Try **CPU cores → Prefer the fast ones** and compare.

Lower-end devices may still show frame drops, longer loading times, or stuttering. Compatibility is not guaranteed on every Android device.

---

## Troubleshooting

- **Crackling or no sound with headphones:** update to the latest release and try **Audio output → Normal**. The app reopens the audio when you connect or disconnect headphones.
- **The game will not prepare:** check that your game is version 1.0.0 and that your `prod.keys` is valid.
- **Low frame rate or stutter:** see the performance tips above.
- **Reporting a bug:** open an issue with your device model, the app version, and the `nx.log` file.

---

## Known limitations

- Performance drops in demanding scenes.
- Longer loading times on devices with limited resources.
- Device-specific graphical issues.
- Different compatibility levels across Android devices.

Bug reports and feedback are welcome.

---

## Building from source

Android APK builds are automated through GitHub Actions.

1. Fork or clone this repository.
2. Open the repository's **Actions** tab.
3. Select the `build-apk` workflow.
4. Run the workflow on the desired branch.
5. After a successful build, download the `p3p-apk` artifact.

Build dependencies and requirements may change during development.

---

## Project structure

| Directory | Description |
|:--|:--|
| `android/` | Android application and build scripts |
| `games/p3p/` | Persona 3 Portable implementation and launcher |
| `runtime/` | Native runtime components |
| `recomp/` | Recompilation-related components |
| `tools/` | Development and build utilities |
| `docs/` | Project documentation |
| `tests/` | Tests and validation |

---

## Credits & acknowledgements

P3P Android builds upon the architecture and work of the original P5R Android project.

- [Original P5R Android project](https://github.com/Brandon04Gonzalez/p5r-android) — original Android architecture and project foundation.
- [ATLUS](https://www.atlus.com/) — creator of Persona 3 Portable.
- **SEGA** — publisher of Persona 3 Portable.

Special thanks to the original developers, contributors, and everyone helping test and improve this project.

---

## Community & support

Have questions, feedback, or want to share your experience? Join the **P4G | Android Community** on Discord:

[**Join the Discord community**](https://discord.gg/yF6FtNK6rM)

---

## Disclaimer

P3P Android is an unofficial, community-developed project. It is not affiliated with, endorsed by, or associated with ATLUS, SEGA, or Nintendo.

Persona 3 Portable, its characters, trademarks, and related intellectual property belong to their respective owners.

This repository does not distribute copyrighted game files, game assets, or encryption keys. Users are responsible for providing their own legally obtained files and complying with applicable laws.

---

## License

This project is distributed under the MIT License, subject to the licenses and notices of third-party components.

See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details.
