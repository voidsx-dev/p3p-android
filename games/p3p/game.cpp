// Persona 3 Portable (Nintendo Switch): what the shared runtime needs to know about the game.
#include <algorithm>
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

std::string guest_str(uint64_t p) {
    if (p < 0x100000) return p ? "(bad pointer)" : "(null)";
    const char* s = reinterpret_cast<const char*>(p);
    std::string out(s, strnlen(s, 200));
    for (char& ch : out)
        if (ch < 32 || ch > 126) ch = '.';
    return out;
}

// nn::diag::detail::AbortImpl(expr, func, file, line): the SDK's assertion failure. Log what the game says and the
// code just before the call (the check that failed), then do the real abort.
void abort_hook(CPU& c) {
    log("p3p: ABORT expr=\"%s\" func=\"%s\" file=\"%s\" line=%d, from %s", guest_str(c.x[0]).c_str(),
        guest_str(c.x[1]).c_str(), guest_str(c.x[2]).c_str(), int(c.x[3]), describe(c.x[30]).c_str());
    const uint64_t lr = c.x[30];
    for (int row = 0; row < 8; row++) {
        const uint64_t a = lr - 0x80 + 16 * uint64_t(row);
        const uint32_t* w = reinterpret_cast<const uint32_t*>(a);
        log("p3p: code main+%llx: %08x %08x %08x %08x", (unsigned long long)(a - g_main_base), w[0], w[1], w[2], w[3]);
    }
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
    if (!a) {  // from the first crash log: AbortImpl is at sdk+8d9f0
        if (const LoadedModule* s = module_at(find_symbol("nnosInitialize"))) a = s->base + 0x8d9f0;
    }
    if (a && (g_abort_orig = hook_function(a, abort_hook))) log("p3p: AbortImpl hooked");
    else log("p3p: AbortImpl could not be hooked");
}
void game::renderer_start() { log("p3p: renderer_start"); }
void game::first_frame() { log("p3p: first_frame"); }

}  // namespace nx
