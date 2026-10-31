// Persona 3 Portable (Nintendo Switch): what the shared runtime needs to know about the game.
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

#include <nx/game.h>
#include <nx/runtime.h>

#include "platform.h"

namespace nx {

constexpr uint64_t kP3pTitleId = 0x0100DCD01525A000ull;

const game::Info game::kInfo = {"p3p", "Persona 3 Portable", kP3pTitleId, "io.github.p3pandroid", "private/p3p",
                                0 /* system resource size */, nullptr /* build ID: any version */};

namespace {

uint64_t g_main_base = 0;
uint64_t g_sdk_base = 0;
uint64_t g_abort_orig = 0;

// Target of the BL at `addr` (0 if it is not a BL).
uint64_t bl_target(uint64_t addr) {
    const uint32_t w = *reinterpret_cast<const uint32_t*>(addr);
    if ((w & 0xFC000000u) != 0x94000000u) return 0;
    int64_t v = int64_t(w & 0x3FFFFFFu);
    if (v & 0x2000000) v -= 0x4000000;
    return addr + uint64_t(v * 4);
}

// A PLT stub is: adrp x16, page / ldr x17, [x16, #off] / add x16, x16, #off / br x17. `got` is the slot it loads from,
// `target` what the slot holds (the function the import was linked to).
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

struct Probe {
    const char* name;
    uint64_t orig;
    int logged;
};
Probe g_probe[3] = {{"A's callee", 0, 0}, {"B's callee", 0, 0}, {"call after the 32 MiB allocation", 0, 0}};

template <int I>
void probe_hook(CPU& c) {
    Probe& p = g_probe[I];
    const uint64_t a0 = c.x[0], a1 = c.x[1], a2 = c.x[2], a3 = c.x[3], lr = c.x[30];
    call(c, p.orig);
    if (p.logged < 10) {
        p.logged++;
        log("p3p: %s (x0=%llx x1=%llx x2=%llx x3=%llx) from %s -> x0=%llx", p.name, (unsigned long long)a0,
            (unsigned long long)a1, (unsigned long long)a2, (unsigned long long)a3, describe(lr).c_str(),
            (unsigned long long)c.x[0]);
    }
}

void (*const kHooks[3])(CPU&) = {probe_hook<0>, probe_hook<1>, probe_hook<2>};
uint64_t g_hooked[3] = {0, 0, 0};

// What the BL at main+bl_off calls, through its PLT stub: the name of the import, and a logging hook on it when it is
// in the sdk module (hooks on main functions do not work: its trampoline area is out of branch range).
void resolve(int idx, const char* name, uint64_t bl_off) {
    const uint64_t stub = bl_target(g_main_base + bl_off);
    if (!stub) {
        log("p3p: %s: no BL at main+%llx", name, (unsigned long long)bl_off);
        return;
    }
    uint64_t got = 0, target = 0;
    if (plt_target(stub, got, target)) {
        log("p3p: %s: BL main+%llx -> PLT main+%llx, GOT main+%llx holds %llx = %s", name, (unsigned long long)bl_off,
            (unsigned long long)(stub - g_main_base), (unsigned long long)(got - g_main_base), (unsigned long long)target,
            target ? describe(target).c_str() : "(null)");
    } else {
        target = stub;
        log("p3p: %s: BL main+%llx goes straight to main+%llx (not a PLT stub)", name, (unsigned long long)bl_off,
            (unsigned long long)(stub - g_main_base));
    }
    if (!target) return;
    for (uint64_t h : g_hooked)
        if (h == target) {
            log("p3p: %s: same function as another probe, not hooked twice", name);
            return;
        }
    const LoadedModule* m = module_at(target);
    if (m && m->base == g_sdk_base) {
        g_probe[idx].orig = hook_function(target, kHooks[idx]);
        g_hooked[idx] = target;
        log("p3p: %s: hook %s", name, g_probe[idx].orig ? "ok" : "FAILED");
    } else {
        log("p3p: %s: target is not in the sdk module, not hooked", name);
    }
}

// The imports of the video and graphics libraries, by name, out of the game's PLT.
void scan_plt() {
    static const char* const kWords[] = {"2vi", "nnvi", "nvdrv", "Graphics", "nvInit", "nvSet", "Layer", "Display"};
    int decoded = 0, shown = 0;
    for (uint64_t off = 0x38b800; off < 0x38d800; off += 16) {
        uint64_t got = 0, target = 0;
        if (!plt_target(g_main_base + off, got, target) || !target) continue;
        decoded++;
        const std::string d = describe(target);
        bool hit = false;
        for (const char* k : kWords)
            if (d.find(k) != std::string::npos) hit = true;
        if (hit && shown < 60) {
            shown++;
            log("p3p: PLT main+%llx -> %s", (unsigned long long)off, d.c_str());
        }
    }
    log("p3p: PLT scan: %d stubs decoded, %d shown", decoded, shown);
}

void abort_hook(CPU& c) {
    log("p3p: ABORT (AbortImpl) from %s", describe(c.x[30]).c_str());
    if (g_abort_orig) call(c, g_abort_orig);
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

void game::after_init() {
    log("p3p: after_init");
    if (const LoadedModule* m = module_at(find_symbol("nnMain"))) g_main_base = m->base;
    if (const LoadedModule* s = module_at(find_symbol("nnosInitialize"))) g_sdk_base = s->base;
    uint64_t a = find_symbol("_ZN2nn4diag6detail9AbortImplEPKcS3_S3_i");
    if (!a && g_sdk_base) a = g_sdk_base + 0x8d9f0;
    if (a && (g_abort_orig = hook_function(a, abort_hook))) log("p3p: AbortImpl hooked");
    if (!g_main_base || !g_sdk_base) {
        log("p3p: main or sdk base not found");
        return;
    }
    scan_plt();
    resolve(2, "call after the 32 MiB allocation", 0x237ba4);
    resolve(0, "A's callee", 0x23ef00 + 0x18);
    resolve(1, "B's callee", 0x240fb0 + 0x18);
}
void game::renderer_start() { log("p3p: renderer_start"); }
void game::first_frame() { log("p3p: first_frame"); }

}  // namespace nx
