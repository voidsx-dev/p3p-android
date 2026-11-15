# Mods (RomFS overlay, LayeredFS style)

Put mods in `Internal storage/Persona 3 Portable/mods/<ModName>/romfs/...`, using the same paths as inside the game's RomFS.

- A file replaces the game file with the same path; a new file is added.
- Folders starting with `.` or `_` are ignored. Rename `MyMod` to `_MyMod` to turn it off.
- Several mods apply in alphabetical order; the last wins.
- Paths are case sensitive on Android: match the game's case exactly.
- ZIP imports accept archives with a `romfs/` directory anywhere inside (for example `PTBR/romfs/data_EN/file.cpk`) or a ZIP containing the RomFS paths directly.
- A CPK placed directly at the root of `romfs/` during ZIP/CPK import is routed to `romfs/data_EN/`, which is the game's language-pack location. Explicit paths such as `romfs/data_EN/` are preserved.
- CPK import starts with `data_EN/<filename>.cpk` as the suggested destination; edit the path when the mod requires another location.
- Mods are scanned each time the game starts. The log (`adb logcat -s p3p:V`) shows `fs: mod active: <name>` and `fs: mod file <path>` for each file served from a mod.
- The game file is never modified.
