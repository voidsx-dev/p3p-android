# Changelog

[0.6.0] - Unreleased

Changed

- The on-screen button appearance (On-screen button style: Nintendo Switch or Xbox) is now separate from the layout of a connected controller (Physical controller layout: Nintendo Switch, Xbox or PlayStation 2 / USB PS2). A PS2 setting saved by an older version is moved to the new option. The touch buttons no longer have a PS2 style.
- Launcher: the controls are reorganized into three cards (Controls, On-screen buttons, Physical controller), and there is a new app icon.
- Mod Manager: a repeated RomFS path is reported as a conflict only when the files differ (compared by SHA-256, with a hash cache); identical copies in several mods are no longer flagged.
- Mods that add mod.cpk, mod_extra.cpk or mod_bgm.cpk, such as P3P Music Replacer, now work: the Mod Manager places them in data/ and the game binds them, which is what the Switch "modCPK" plugin does. Confirmed in game with the Music Replacer ("Heaven" plays in Tartarus block 6).

Fixed

- Missing translations in the controls options and hints, and Portuguese text showing in Spanish (including the save management panel).

Notes

- The PS2 USB adapter support has not been tested on a real controller yet.
- The Mod Manager and Diagnostics screens are still in Portuguese only.

[0.5.0] - Released

Added

- Controller mapping screen (Controller → Controller mapping). For pads Android has no standard layout for, such as a PS2 pad on a USB/OTG adapter, and for anyone who wants other buttons: tap Set (or Guided setup), press the button you want on your controller, and it is kept. It covers all 16 buttons (A/B/X/Y, L/R, ZL/ZR, +/−, both stick clicks and the d-pad) and both sticks (it finds the axis and which way is up, for pads whose sticks do not respond or move backwards). Axes can be mapped to buttons too, for a d-pad that comes as an axis or for analog triggers. Whatever is not mapped keeps working as before; every button has its own reset, and there is Restore defaults.
- "Use my mapping" switch on that screen: off keeps the mapping saved but unused, so another controller can be played without losing it.
- Live test on the mapping screen: it shows what the controller sends and what the game does with it (custom, default or unused), lights up the row of the button held down, and lists the controllers Android sees with their vendor:product ids, for bug reports.
- PlayStation 2 control profile (Button layout style → PlayStation 2): Cross confirms, Circle goes back. PS2 pads on a USB/OTG adapter work with every profile with no setting needed (the common "PC TWIN SHOCK" button order: Triangle, Circle, Cross, Square, L2, R2, L1, R1, Select, Start, L3, R3; the right stick on Z/RZ or RX/RY). The same option drives the touch buttons' positions; their labels stay A / B / X / Y.
- The controller's Home (Guide) button opens and closes the in-game Quick Menu, so it can be reached without touching the screen.
- FPS counter switch in the Quick Menu, applied while playing (it no longer needs a restart).
- Reset settings (Tools): resolution, performance, audio and button options go back to their defaults. Saves, the controller mapping and the button positions are kept.
- The launcher's Controls card is split in two: Controls (touch buttons: on/off, size, opacity, position) and Controller (button layout style, the controllers connected now and whether a custom mapping is on, and the mapping screen).
- nx.log says once which controller keys nothing uses (`pad: unmapped controller key N`), and when a custom mapping is loaded or switched off.
- Everything new is translated in all 10 launcher languages.

Fixed

- Buttons held on a controller are no longer released when the d-pad or a trigger moves (keys and axes are tracked apart).
- The line under Button layout style shows the saved choice when the launcher opens, not always Nintendo.
- Plugging a controller in no longer restarts the launcher.

Preserved

- Existing FSR, 720p/1080p, FPS cap, CPU affinity, audio, saves, Mod Manager and Stretch Screen behavior. Controllers Android already knows (Xbox, PlayStation, Switch and most Bluetooth pads) keep their button positions.

Notes

- The controller mapping is the `NX_PAD_MAP` line of config.txt (format in runtime/src/pad_map.h and docs/ANDROID.md).
- Controller support is new and could only be tested on a few pads. PS2 adapters list their buttons in different orders, which is what the mapping screen is for. If a controller does not work, open an issue with your device, the controller's name and ids (shown on the mapping screen) and your nx.log.


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
