// Persona 3 Portable (Nintendo Switch): what the shared runtime needs to know about the game.
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <string>
#include <system_error>

#include <nx/game.h>
#include <nx/runtime.h>

#include "platform.h"

namespace nx {

constexpr uint64_t kP3pTitleId = 0x0100DCD01525A000ull;

const game::Info game::kInfo = {"p3p", "Persona 3 Portable", kP3pTitleId, "io.github.p3pandroid", "private/p3p",
                                0 /* system resource size */, nullptr /* build ID: any version */};

namespace {

uint64_t g_main_base = 0, g_sdk_base = 0;
uint64_t g_abort_orig = 0, g_abort_msg_orig = 0, g_send_orig = 0;

bool readable(uint64_t p, uint64_t n) { return p >= 0x100000 && n <= 0x100000 && p + n < 0x7000000000ull; }

std::string guest_str(uint64_t p) {
    if (!p) return "(null)";
    if (!readable(p, 1)) return "(not a guest pointer)";
    const char* s = reinterpret_cast<const char*>(p);
    std::string out(s, strnlen(s, 300));
    for (char& ch : out)
        if (ch < 32 || ch > 126) ch = '.';
    return out;
}

std::string text_of(uint64_t p, uint64_t n) {
    std::string out, run;
    const uint8_t* b = reinterpret_cast<const uint8_t*>(p);
    auto flush = [&] {
        if (run.size() >= 4 && out.size() < 600) {
            if (!out.empty()) out += " | ";
            out += run;
        }
        run.clear();
    };
    for (uint64_t i = 0; i < n; i++) {
        const uint8_t ch = b[i];
        if (ch >= 32 && ch < 127) {
            run += char(ch);
            if (run.size() > 300) flush();
        } else {
            flush();
        }
    }
    flush();
    return out;
}

// The text in the buffers of an IPC request (the log service's messages, file paths and the like).
void note_request(uint64_t handle, uint64_t msg) {
    if (!readable(msg, 0x100)) return;
    const uint32_t* w = reinterpret_cast<const uint32_t*>(msg);
    const uint32_t type = w[0] & 0xFFFF, nstat = (w[0] >> 16) & 0xF, nsend = (w[0] >> 20) & 0xF,
                   nrecv = (w[0] >> 24) & 0xF, nexch = (w[0] >> 28) & 0xF;
    if (type != 4 && type != 6) return;
    size_t i = 2;
    if (w[1] >> 31) {
        const uint32_t sp = w[2];
        i = 3;
        if (sp & 1) i += 2;
        i += (sp >> 1) & 0xF;
        i += (sp >> 5) & 0xF;
    }
    std::string text;
    auto add = [&](uint64_t addr, uint64_t size) {
        if (size == 0 || size > 0x2000 || !readable(addr, size)) return;
        const std::string t = text_of(addr, size);
        if (t.empty()) return;
        if (!text.empty()) text += " ## ";
        text += t;
    };
    for (uint32_t k = 0; k < nstat && i + 2 <= 64; k++, i += 2) {
        const uint64_t addr = uint64_t(w[i + 1]) | (uint64_t((w[i] >> 12) & 0xF) << 32) | (uint64_t((w[i] >> 6) & 7) << 36);
        add(addr, w[i] >> 16);
    }
    for (uint32_t k = 0; k < nsend + nrecv + nexch && i + 3 <= 64; k++, i += 3) {
        if (k >= nsend) continue;
        const uint64_t size = uint64_t(w[i]) | (uint64_t((w[i + 2] >> 24) & 0xF) << 32);
        const uint64_t addr = uint64_t(w[i + 1]) | (uint64_t((w[i + 2] >> 28) & 0xF) << 32) |
                              (uint64_t((w[i + 2] >> 2) & 7) << 36);
        add(addr, size);
    }
    if (text.size() < 6) return;
    static int logged = 0;
    if (logged++ < 80)
        log("p3p: ipc text (handle %llx, type %u): %s", (unsigned long long)handle, type, text.substr(0, 400).c_str());
}

void send_hook(CPU& c) {
    note_request(c.x[0], c.x[1]);
    call(c, g_send_orig);
}

void abort_hook(CPU& c) {
    log("p3p: ABORT expr=\"%s\" func=\"%s\" file=\"%s\" line=%d, from %s", guest_str(c.x[0]).c_str(),
        guest_str(c.x[1]).c_str(), guest_str(c.x[2]).c_str(), int(c.x[3]), describe(c.x[30]).c_str());
    if (g_abort_orig) call(c, g_abort_orig);
}

void abort_msg_hook(CPU& c) {
    log("p3p: ABORT+msg expr=\"%s\" func=\"%s\" file=\"%s\" line=%d fmt=\"%s\", from %s", guest_str(c.x[0]).c_str(),
        guest_str(c.x[1]).c_str(), guest_str(c.x[2]).c_str(), int(c.x[3]), guest_str(c.x[4]).c_str(),
        describe(c.x[30]).c_str());
    if (g_abort_msg_orig) call(c, g_abort_msg_orig);
}

// A PLT stub is: adrp x16, page / ldr x17, [x16, #off] / add x16, x16, #off / br x17. `target` is what its GOT slot
// holds: the function the import was linked to.
bool plt_target(uint64_t stub, uint64_t& got, uint64_t& target) {
    const uint32_t* w = reinterpret_cast<const uint32_t*>(stub);
    if ((w[0] & 0x9F00001Fu) != 0x90000010u) return false;
    if ((w[1] & 0xFFC003FFu) != 0xF9400211u) return false;
    if (w[3] != 0xD61F0220u) return false;
    int64_t imm = (int64_t((w[0] >> 5) & 0x7FFFFu) << 2) | int64_t((w[0] >> 29) & 3u);
    if (imm & 0x100000) imm -= 0x200000;
    const uint64_t page = (stub & ~uint64_t(0xFFF)) + uint64_t(imm * 4096);
    got = page + uint64_t((w[1] >> 10) & 0xFFFu) * 8;
    target = *reinterpret_cast<const uint64_t*>(got);
    return true;
}

// The audio functions the game imports, by name, out of its PLT.
void scan_plt() {
    int decoded = 0, shown = 0;
    for (uint64_t off = 0x38b000; off < 0x38e000; off += 16) {
        uint64_t got = 0, target = 0;
        if (!plt_target(g_main_base + off, got, target) || !target) continue;
        decoded++;
        const std::string d = describe(target);
        if (d.find("5audio") != std::string::npos && shown < 60) {
            shown++;
            log("p3p: PLT main+%llx -> %s", (unsigned long long)off, d.c_str());
        }
    }
    log("p3p: PLT scan: %d stubs decoded, %d audio shown", decoded, shown);
}

void glslc_fail(CPU& c) {
    static int n = 0;
    if (n++ < 20)
        log("p3p: %s called from %s (x0=%llx x1=%llx x2=%llx): run-time shader compilation is not supported",
            hle_current_name(), describe(c.x[30]).c_str(), (unsigned long long)c.x[0], (unsigned long long)c.x[1],
            (unsigned long long)c.x[2]);
    c.x[0] = 0;
}

void glslc_nop(CPU& c) {
    static int n = 0;
    if (n++ < 20) log("p3p: %s called from %s", hle_current_name(), describe(c.x[30]).c_str());
    c.x[0] = 0;
}

void fputs_logged(CPU& c) {
    static const uint64_t real = find_symbol("fputs");
    const char* s = reinterpret_cast<const char*>(c.x[0]);
    if (s) {
        std::string line(s, strnlen(s, 1024));
        while (!line.empty() && (line.back() == '\n' || line.back() == '\r')) line.pop_back();
        log("[guest fputs] %s", line.c_str());
    }
    if (real) call(c, real);
    else c.x[0] = 0;
}

// ---- mod CPKs ------------------------------------------------------------------------------------------------------
// The Switch plugin "modCPK" (a Skyline plugin: exefs/subsdk9 + romfs/plugins/libmodCPK.nro, which the Mod Manager cannot
// run) makes the game bind three more CPKs next to its own: app0:/data/mod.cpk, mod_extra.cpk and mod_bgm.cpk. Music mods
// such as "P3P Music Replacer" are only a mod_bgm.cpk and depend on it. This is what the plugin does (read from its code):
//   - it replaces the game's function at main+0x1e6280, which binds one CPK: f(x0, x1, path in x2). Before the game's own
//     bind it calls the original once for each extra path, with the same x0 and x1; the first bound wins in CRI, so the
//     mods' files come before the game's;
//   - it writes the instruction 0x14000024 (b +0x90) at main+0x1c5424.
// Here it only acts when an active mod has one of the three files (mods/<Name>/romfs/data/<file>), the call to the
// original stays as it was for the game's own CPK, and the extra binds happen only if x2 looks like a CPK path.
// The two addresses are those of the game's version 1.0.0, which the plugin was made for. NX_MODCPK=0 turns it off;
// NX_MODCPK_PATCH=0 / force skips / forces the instruction patch.
constexpr uint64_t kCpkBindOffset = 0x1e6280, kCpkPatchOffset = 0x1c5424;
constexpr uint32_t kCpkPatchWord = 0x14000024;  // b +0x90
const char* const kModCpkPath[3] = {"app0:/data/mod.cpk", "app0:/data/mod_extra.cpk", "app0:/data/mod_bgm.cpk"};
const char* const kModCpkRel[3] = {"data/mod.cpk", "data/mod_extra.cpk", "data/mod_bgm.cpk"};
uint64_t g_cpk_bind_orig = 0;
bool g_modcpk_have[3] = {};

// The branch distance in bytes of a conditional branch (b.cond, cbz/cbnz, tbz/tbnz); 0 when `w` is not one.
int64_t cond_branch_distance(uint32_t w) {
    auto sx = [](uint32_t v, int bits) { return int64_t(v ^ (1u << (bits - 1))) - int64_t(1u << (bits - 1)); };
    if ((w & 0xff000010u) == 0x54000000u) return sx((w >> 5) & 0x7ffffu, 19) * 4;
    if ((w & 0x7e000000u) == 0x34000000u) return sx((w >> 5) & 0x7ffffu, 19) * 4;
    if ((w & 0x7e000000u) == 0x36000000u) return sx((w >> 5) & 0x3fffu, 14) * 4;
    return 0;
}

// "app0:/....cpk" in guest memory.
bool looks_like_cpk_path(uint64_t p) {
    if (!readable(p, 1)) return false;
    const char* s = reinterpret_cast<const char*>(p);
    const size_t n = strnlen(s, 300);
    if (n < 11 || n >= 300 || std::strncmp(s, "app0:/", 6) != 0) return false;
    const char* e = s + n - 4;
    return e[0] == '.' && (e[1] | 32) == 'c' && (e[2] | 32) == 'p' && (e[3] | 32) == 'k';
}

void cpk_bind_hook(CPU& c) {
    static int seen = 0;
    const uint64_t path = c.x[2];
    if (!looks_like_cpk_path(path)) {  // not the function it was taken for: the game goes on as it was
        if (seen++ < 3) log("p3p: modcpk: bind hook called with x2=%s, not a CPK path: passed through", guest_str(path).c_str());
        call(c, g_cpk_bind_orig);
        return;
    }
    uint64_t saved[8];
    for (int k = 0; k < 8; k++) saved[k] = c.x[k];
    const uint64_t lr = c.x[30];
    if (seen++ < 6) log("p3p: modcpk: the game binds %s", guest_str(path).c_str());
    for (int i = 0; i < 3; i++) {
        if (!g_modcpk_have[i]) continue;
        for (int k = 0; k < 8; k++) c.x[k] = saved[k];
        c.x[2] = reinterpret_cast<uint64_t>(kModCpkPath[i]);
        c.x[30] = lr;
        call(c, g_cpk_bind_orig);
    }
    for (int k = 0; k < 8; k++) c.x[k] = saved[k];  // the game's own bind, exactly as it was called
    c.x[30] = lr;
    call(c, g_cpk_bind_orig);
}

// Which of the three CPKs an active mod has (the Mod Manager's folders: mods/<Name>/romfs/..., names starting with '.' or '_'
// are off).
bool scan_mod_cpks() {
    namespace fs = std::filesystem;
    std::error_code ec;
    const fs::path root = fs::path(plat::data_root()) / "mods";
    if (!fs::is_directory(root, ec)) return false;
    bool any = false;
    for (const auto& e : fs::directory_iterator(root, ec)) {
        if (!e.is_directory(ec)) continue;
        const std::string name = e.path().filename().string();
        if (name.empty() || name[0] == '.' || name[0] == '_') continue;
        for (int i = 0; i < 3; i++) {
            if (fs::is_regular_file(e.path() / "romfs" / kModCpkRel[i], ec)) {
                g_modcpk_have[i] = true;
                any = true;
            }
        }
    }
    return any;
}

void install_mod_cpk() {
    if (const char* off = std::getenv("NX_MODCPK"); off && off[0] == '0') return;
    if (!scan_mod_cpks()) return;  // no mod needs it: the game is left exactly as it is
    log("p3p: modcpk: mods have mod.cpk=%d mod_extra.cpk=%d mod_bgm.cpk=%d", g_modcpk_have[0], g_modcpk_have[1],
        g_modcpk_have[2]);
    const LoadedModule* m = module_at(find_symbol("nnMain"));
    if (!m || kCpkBindOffset + 16 > m->info->text_size || kCpkPatchOffset + 4 > m->info->text_size) {
        log("p3p: modcpk: the game's main is not the one the plugin was made for: not installed");
        return;
    }
    const uint64_t text = m->base + m->info->text;
    const uint32_t* bind = reinterpret_cast<const uint32_t*>(text + kCpkBindOffset);
    const uint32_t at = *reinterpret_cast<const uint32_t*>(text + kCpkPatchOffset);
    log("p3p: modcpk: main+%llx: %08x %08x %08x %08x (bind), main+%llx: %08x (patch site)", (unsigned long long)kCpkBindOffset,
        bind[0], bind[1], bind[2], bind[3], (unsigned long long)kCpkPatchOffset, at);

    const char* mode = std::getenv("NX_MODCPK_PATCH");  // 0: skip, force: always, else only when it looks right
    const int64_t dist = cond_branch_distance(at);
    if (at == kCpkPatchWord) {
        log("p3p: modcpk: the patch is already there");
    } else if (mode && mode[0] == '0') {
        log("p3p: modcpk: instruction patch off (NX_MODCPK_PATCH=0)");
    } else if (dist == 0x90 || (mode && std::strcmp(mode, "force") == 0)) {
        log("p3p: modcpk: patch main+%llx: %08x -> %08x: %s", (unsigned long long)kCpkPatchOffset, at, kCpkPatchWord,
            patch_guest_word(text + kCpkPatchOffset, kCpkPatchWord) ? "ok" : "FAILED");
    } else {
        log("p3p: modcpk: main+%llx holds %08x, not a branch of +0x90: patch left out", (unsigned long long)kCpkPatchOffset, at);
    }
    g_cpk_bind_orig = hook_function(text + kCpkBindOffset, cpk_bind_hook);
    log("p3p: modcpk: hook of the CPK bind: %s", g_cpk_bind_orig ? "ok" : "FAILED");
}

void hid_nop(CPU& c) { c.x[0] = 0; }

}  // namespace

NX_HLE("fputs", fputs_logged);

NX_HLE("glslcInitialize", glslc_fail);
NX_HLE("glslcCompile", glslc_fail);
NX_HLE("glslcCompilePreSpecialized", glslc_fail);
NX_HLE("glslcCompileSpecialized", glslc_fail);
NX_HLE("glslcFinalize", glslc_nop);
NX_HLE("glslcSetAllocator", glslc_nop);
NX_HLE("glslcGetDefaultOptions", glslc_nop);
NX_HLE("glslcGetVersion", glslc_nop);

// P3P reads touch through nn::hid's gesture API, which the runtime does not provide yet: no gestures for now.
NX_HLE("_ZN2nn3hid17InitializeGestureEv", hid_nop);
NX_HLE("_ZN2nn3hid16GetGestureStatesEPNS0_12GestureStateEi", hid_nop);

void game::after_init() {
    log("p3p: after_init");
    setenv("NX_DUMP_SHADER_MISSES", "1", 0);
      {   // P3P's window origin: Vulkan's own viewport mapping, the final picture turned over (nvn_draw.cpp origin_flags)
        const std::string flags_path = plat::data_root() + "/live_flags.txt";
        if (FILE* f = plat::fopen_utf8(flags_path, "rb")) {
            std::fclose(f);  // already there: left as the player has it
        } else if (FILE* w = plat::fopen_utf8(flags_path, "wb")) {
            std::fputs("vp_noflip present_flip\n", w);
            std::fclose(w);
        }
    }
#if defined(__ANDROID__)
    // Android render defaults, from the P5R branch and measured on a Helio G99 (Mali-G57) through config.txt, where
    // each NX_* line still wins (setenv without overwrite; config.txt is already in the environment here):
    // - NX_IMAGE_STORAGE=0: colour images without STORAGE usage keep the GPU's framebuffer compression (nvn_image.cpp);
    // - NX_CLEARS_V2=1: pending clears become loadOps instead of standalone transfer clears (nvn_clears.cpp);
    // - NX_EXP_NO_WB=1: no CPU write-back of compute results the GPU already reads in place (nvn_written.cpp).
    setenv("NX_IMAGE_STORAGE", "0", 0);
    setenv("NX_CLEARS_V2", "1", 0);
    setenv("NX_EXP_NO_WB", "1", 0);
#endif
    if (const LoadedModule* m = module_at(find_symbol("nnMain"))) g_main_base = m->base;
    if (const LoadedModule* s = module_at(find_symbol("nnosInitialize"))) g_sdk_base = s->base;
    auto hook = [](const char* sym, uint64_t fallback, void (*fn)(CPU&), uint64_t& orig, const char* what) {
        uint64_t a = find_symbol(sym);
        if (!a && g_sdk_base) a = g_sdk_base + fallback;
        orig = a ? hook_function(a, fn) : 0;
        log("p3p: hook %s: %s", what, orig ? "ok" : "FAILED");
    };
    hook("_ZN2nn4diag6detail9AbortImplEPKcS3_S3_i", 0x8d9f0, abort_hook, g_abort_orig, "AbortImpl");
    hook("_ZN2nn4diag6detail9AbortImplEPKcS3_S3_iS3_z", 0x8d980, abort_msg_hook, g_abort_msg_orig, "AbortImpl(msg)");
    // IPC text tracing is diagnostic-only. Keep it off in normal play to avoid inspecting and allocating strings
    // for every guest IPC request; set P3P_TRACE_IPC=1 in config.txt when troubleshooting.
    if (const char* trace = std::getenv("P3P_TRACE_IPC"); trace && trace[0] == '1')
        hook("_ZN2nn2sf4hipc15SendSyncRequestENS_3svc6HandleEPvm", 0x13f290, send_hook, g_send_orig, "SendSyncRequest");
    if (g_main_base) scan_plt();
    install_mod_cpk();
}
void game::renderer_start() { log("p3p: renderer_start"); }
void game::first_frame() { log("p3p: first_frame"); }

}  // namespace nx
