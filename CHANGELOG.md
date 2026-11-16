Changelog

All notable changes to P3P Android are documented here.

[0.4.0] - Released

Added

- Pressing the Android Back button opens the in-game Quick Menu (no floating trigger), styled to match the Launcher, with Resume and Exit actions.
- Live Stretch Screen toggle, virtual touch controls visibility, and touch-control opacity (35%, 60%, 85%).
- Nintendo Switch / Xbox control profile, with a roomier touch layout for P3P.

Improved

- Rendering performance: pending clears are merged into render passes, buffer uploads are reused between draws and frames, descriptor sets are cached, and CPU write-backs the GPU does not need are skipped. Render targets no longer request storage usage on Android, which lets the GPU keep its framebuffer compression.
- The game's main and render threads prefer the fast CPU cores by default (CPU Cores option), and the render thread runs at a higher scheduler priority.
- The FPS cap (30/60) now holds on 90/120 Hz displays, using an absolute frame period.
- The runtime and the recompiled game code are built with -O3, -fomit-frame-pointer and -fno-math-errno.
- Mod Manager: ZIP imports find the romfs folder at any depth (for example PTBR/romfs/data_EN/file.cpk); a CPK placed directly in romfs/ goes to data_EN/; the list scrolls with the whole page.
- Saves: the import finds the save files (P4G.ini, dataNNNN.bin) wherever they sit inside a ZIP or folder.
- Device Diagnostics and Mod Manager now match the Launcher's look.
- Localized the Nintendo Switch / Xbox control profile and its explanation in all 10 launcher languages.

Preserved

- Existing FSR, 720p/1080p, FPS cap, CPU affinity, heap tuning, GPU scheduling, audio, saves, and Stretch Screen behavior.

Notes

- Performance varies depending on the device and drivers.


[0.3.0] - Released

Added

- FSR, CPU Cores, and Audio Output settings.
- Fullscreen support for the Launcher, Mod Manager, and Device Diagnostics.
- Device Diagnostics.
- Support for 10 launcher languages.

Improved

- Redesigned launcher interface.
- Updated Mod Manager interface.
- Audio downmixing with soft limiting to reduce clipping.
- Audio buffering and recovery.
- Audio stream handling when headphones are connected or disconnected.
- CPU affinity registration for the game main thread.
- NDK download script with resume and retry support.

Changed

- Removed the ineffective Texture Quality option.
- Limited resolution options to 720p and 1080p.

Notes

- Save behavior was left unchanged.
- Performance varies depending on the device and drivers.
