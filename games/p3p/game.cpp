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
uint64_t g_abort_orig = 0;

void dump_code(const char* what, uint64_t off, int words) {
    log("p3p: code of %s (main+%llx):", what, (unsigned long long)off);
    for (int i = 0; i < words; i += 4) {
        const uint32_t* w = reinterpret_cast<const uint32_t*>(g_main_base + off + 4 * uint64_t(i));
        log("p3p:   +%02x: %08x %08x %08x %08x", 4 * i, w[0], w[1], w[2], w[3]);
    }
}

// The three calls around the failing checks in main+237ab4 (the abort is "cbnz w0" after the first two).
struct Site {
    uint64_t off;
    const char* name;
    bool force_ok;     // experiment: a nonzero return becomes 0 so the game gets past the check
    uint64_t only_x0;  // log only calls whose first argument is this (0: all)
    uint64_t orig;
    int logged;
};

Site g_sites[3] = {
    {0x23ef00, "A (after the 32 MiB allocation)", true, 0, 0, 0},
    {0x240fb0, "B (gets two sizes)", true, 0, 0, 0},
    {0x38bf60, "allocator (32 MiB request)", false, 0x2000000, 0, 0},
};

template <int I>
void site_hook(CPU& c) {
    Site& s = g_sites[I];
    const uint64_t a0 = c.x[0], a1 = c.x[1], a2 = c.x[2], a3 = c.x[3], lr = c.x[30];
    call(c, s.orig);
    if ((!s.only_x0 || a0 == s.only_x0) && s.logged < 8) {
        s.logged++;
        log("p3p: %s (x0=%llx x1=%llx x2=%llx x3=%llx) from %s -> %llx", s.name, (unsigned long long)a0,
            (unsigned long long)a1, (unsigned long long)a2, (unsigned long long)a3, describe(lr).c_str(),
            (unsigned long long)c.x[0]);
    }
    if (s.force_ok && uint32_t(c.x[0]) != 0) {
        log("p3p: %s returned %x: forced to 0 (experiment)", s.name, uint32_t(c.x[0]));
        c.x[0] = 0;
    }
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
    uint64_t a = find_symbol("_ZN2nn4diag6detail9AbortImplEPKcS3_S3_i");
    if (!a) {
        if (const LoadedModule* s = module_at(find_symbol("nnosInitialize"))) a = s->base + 0x8d9f0;
    }
    if (a && (g_abort_orig = hook_function(a, abort_hook))) log("p3p: AbortImpl hooked");
    if (!g_main_base) {
        log("p3p: main not found");
        return;
    }
    // The code is read before hooking (a hook replaces a function's first instructions).
    for (const Site& s : g_sites) dump_code(s.name, s.off, 16);
    dump_code("callee after the allocation", 0x38c370, 16);
    g_sites[0].orig = hook_function(g_main_base + g_sites[0].off, site_hook<0>);
    g_sites[1].orig = hook_function(g_main_base + g_sites[1].off, site_hook<1>);
    g_sites[2].orig = hook_function(g_main_base + g_sites[2].off, site_hook<2>);
    for (const Site& s : g_sites) log("p3p: hook %s: %s", s.name, s.orig ? "ok" : "FAILED");
}
void game::renderer_start() { log("p3p: renderer_start"); }
void game::first_frame() { log("p3p: first_frame"); }

}  // namespace nx
