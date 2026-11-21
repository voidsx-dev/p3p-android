# Android

The same recompiled code runs on Android (arm64-v8a): the generated C++ is portable, and the runtime talks to
the OS through `runtime/src/platform.h` (`platform_win.cpp` / `platform_android.cpp`).

| | Windows | Android |
|---|---|---|
| Memory | VirtualAlloc at fixed addresses | mmap `MAP_FIXED_NOREPLACE` (fails loudly if a range is taken) |
| Threads | Win32 threads | pthreads (64 MiB host stacks, 256 MiB for the guest main thread) |
| Window | Win32 window, F11 fullscreen | NativeActivity window, full screen under the camera cutout |
| Input | XInput pad + keyboard | game controllers (Bluetooth/USB) + touch |
| Audio | XAudio2 | AAudio (low latency, callback) |
| Vulkan | vulkan-1.dll, VK_KHR_win32_surface | libvulkan.so, VK_KHR_android_surface |

The game fills the whole screen in native widescreen (Hor+, like Hades II and the Hephaistos mod on PC): the
engine's virtual screen stays 1080 high and becomes 1080 x aspect wide, so a 19.5:9 phone shows more of each room
instead of a stretched 16:9 picture. The runtime rewrites the engine's 16:9 globals after its static initializers
(`games/hades/game.cpp`) and serves the Hephaistos Lua mod (`games/hades/mods/hephaistos`, MIT) to the game from memory.
FMVs (main menu) stay stretched for now, dialogue portraits stay 16:9.

The picture is drawn into the swapchain already rotated to the display's own orientation (present pass,
`vk_present.cpp`), so the display controller scans it out directly even with system UI on top; the system bars
are hidden (swipe from an edge shows them for a moment). Leaving the app and coming back re-makes the surface and
swapchain under the game.

`config.txt` in the data folder (`key=value` lines):

| Key | Default | What |
|---|---|---|
| `render_scale` | by GPU: 1 (Adreno 7xx+, Mali-G710+, Immortalis, Xclipse), 0.85 (Adreno 6xx), 0.67 | render at a fraction of the screen, scaled up by the present pass; never above 1080 lines |
| `resolution` | | exact render size, e.g. `1560x720` (overrides `render_scale`) |
| `fullscreen` | 1 | 0: the engine's own 16:9 with its side art |
| `widescreen` | 1 | 0: fullscreen stretched instead of Hor+ |
| `center_hud` | 0 | 1: HUD kept in the central 16:9 area |
| `Letterbox`, `UseSideBarArt`, `NoBorder`, `SwapInterval`, `NoFixedStep` | | engine settings, set as is |
| `trace` | 0 | 1: log file system calls and SVCs (slow) |

Diagnostics while it runs: `touch Hades/dump_threads` logs every guest thread; `echo 20 > Hades/profile` samples
every thread's host pc for 20 s into `Hades/profile.txt`. The log (`Hades/nx.log`, previous run in `nx.log.prev`)
reports fps per 5 s with the worst frame, and stalls over 100 ms with where each thread was.

## Install from your phone alone

1. Copy `hades.apk` and `Hades-data.zip` (`private\celular\`, made by `tools\pack_phone.ps1`) to the phone.
2. Extract `Hades-data.zip` into Internal storage (My Files > the zip > Extract): you get `Internal storage/Hades/`.
3. Install `hades.apk` (allow installing from this source), open it, turn on All files access when the
   system page appears, go back: the game starts. Log: `Internal storage/Hades/nx.log`.

## Build and install (USB)

```powershell
. .\dev.ps1
android\build_apk.ps1                      # libhades.so (NDK r28, ~45 MB stripped) -> private\apk\hades.apk (~15 MB)
tools\push_android.ps1                     # install + copy your data to the phone + start
tools\push_android.ps1 -Lean               # without the other languages' fonts (~45 MB less)
tools\push_android.ps1 -SkipData -Resolution 1560x720
adb logcat -s hades                        # the runtime's log (also in Android/data/<pkg>/nx.log)
```

Data on the phone (`/sdcard/Android/data/io.github.belmantegu.hadesrecomp/files/`):

| Path | What |
|---|---|
| `analysis/main.bin`, `analysis/sdk.bin` | module images (`tools/nso2bin.py`) |
| `extract/romfs/` | the RomFS with the update applied (3.9 GB) |
| `user/save/` | saves (created by the game) |
| `config.txt` | optional settings |

## Size

The APK carries only code (15 MB). The game data cannot shrink much: `Movies` (2.1 GB) are not cutscenes but the
characters' animations as Bink videos, `Audio` (961 MB) are FMOD banks and `Win/Packages` (583 MB) the 720p
textures, all already compressed and all used. Only the fonts of other languages are optional.

## Touch controls

No overlay is drawn yet: the left half of the screen is a virtual stick (drag from where the thumb lands), the
right half has four zones: up-left Y, up-right X, down-left B, down-right A; along the top: L, +, ZR, R.
A Bluetooth controller is the better way to play for now.

## Controllers

Standard controllers (Xbox, PlayStation, Switch pads, most Bluetooth pads) work as they are. The launcher's **Button layout style** (Nintendo Switch, Xbox, PlayStation 2) says which face button confirms. A PS2 pad on a USB/OTG adapter arrives as a generic pad (BUTTON_1..12) and works with every profile, in the common "PC TWIN SHOCK" order.

When a pad does something else, **Controller → Controller mapping** lets you map it yourself. Tap Set (or Guided setup), press the button on the controller; sticks are found the same way. It is saved as `NX_PAD_MAP` in config.txt, a list separated by `;`:

| Item | Meaning | Example |
|---|---|---|
| `k<keycode>:<ACTION>` | an Android key code acts as a button | `k190:A` |
| `a<axis><+/->:<ACTION>` | an axis pushed past half, that way, acts as a button | `a15-:LEFT` |
| `s<LX/LY/RX/RY>:<axis>[i]` | the axis that moves a stick (`i` turns it round) | `sRX:11` |

ACTION is one of A B X Y L R ZL ZR PLUS MINUS L3 R3 UP DOWN LEFT RIGHT (the game's own Nintendo names: A confirms, B goes back). What is not listed keeps its built-in meaning. nx.log tells once about each controller key nothing uses (`pad: unmapped controller key N`), which is the number to map.

## Known limits

- The address space: the guest ranges sit at 0x40_0000_0000-0x6F_0000_0000, below the 39-bit top where
  Android maps libraries; a failed reservation is reported in the log.
