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

// The byte offset a branch instruction jumps by (B, B.cond, CBZ/CBNZ, TBZ/TBNZ); is_branch false for anything else.
int64_t branch_offset(uint32_t w, bool& is_branch) {
    is_branch = true;
    if ((w & 0xFC000000u) == 0x14000000u) {  // B
        int32_t v = int32_t(w & 0x3FFFFFFu);
        if (v & 0x2000000) v -= 0x4000000;
        return int64_t(v) * 4;
    }
    if ((w & 0xFF000010u) == 0x54000000u || (w & 0x7E000000u) == 0x34000000u) {  // B.cond, CBZ/CBNZ
        int32_t v = int32_t((w >> 5) & 0x7FFFFu);
        if (v & 0x40000) v -= 0x80000;
        return int64_t(v) * 4;
    }
    if ((w & 0x7E000000u) == 0x36000000u) {  // TBZ/TBNZ
        int32_t v = int32_t((w >> 5) & 0x3FFFu);
        if (v & 0x2000) v -= 0x4000;
        return int64_t(v) * 4;
    }
    is_branch = false;
    return 0;
}

void dump_code(uint64_t from, int words) {
    for (int i = 0; i < words; i += 4) {
        const uint32_t* w = reinterpret_cast<const uint32_t*>(from + 4 * uint64_t(i));
        log("p3p: code main+%llx: %08x %08x %08x %08x", (unsigned long long)(from + 4 * uint64_t(i) - g_main_base), w[0],
            w[1], w[2], w[3]);
    }
}

// nn::diag::detail::AbortImpl(expr, func, file, line): the SDK's assertion failure. The release SDK has no texts, so
// find what failed: the registers, the stack frame, the start of the function, and every branch that jumps to the
// abort block (6 instructions before the return address) with the code just before it (the comparison).
void abort_hook(CPU& c) {
    const uint64_t lr = c.x[30];
    log("p3p: ABORT expr=\"%s\" func=\"%s\" file=\"%s\" line=%d, from %s", guest_str(c.x[0]).c_str(),
        guest_str(c.x[1]).c_str(), guest_str(c.x[2]).c_str(), int(c.x[3]), describe(lr).c_str());
    log("p3p: regs x19-x22 %llx %llx %llx %llx", (unsigned long long)c.x[19], (unsigned long long)c.x[20],
        (unsigned long long)c.x[21], (unsigned long long)c.x[22]);
    log("p3p: regs x23-x28 %llx %llx %llx %llx %llx %llx", (unsigned long long)c.x[23], (unsigned long long)c.x[24],
        (unsigned long long)c.x[25], (unsigned long long)c.x[26], (unsigned long long)c.x[27], (unsigned long long)c.x[28]);
    log("p3p: regs x4-x8 %llx %llx %llx %llx %llx, x29 %llx sp %llx", (unsigned long long)c.x[4],
        (unsigned long long)c.x[5], (unsigned long long)c.x[6], (unsigned long long)c.x[7], (unsigned long long)c.x[8],
        (unsigned long long)c.x[29], (unsigned long long)c.x[31]);
    const uint64_t sp = c.x[31];
    for (int row = 0; row < 5; row++) {
        const uint64_t* q = reinterpret_cast<const uint64_t*>(sp + 32 * uint64_t(row));
        log("p3p: stack sp+%x: %llx %llx %llx %llx", 32 * row, (unsigned long long)q[0], (unsigned long long)q[1],
            (unsigned long long)q[2], (unsigned long long)q[3]);
    }

    const uint64_t block = lr - 0x18;
    uint64_t start = 0;
    for (uint64_t a = block; a > block - 0x4000 && a > g_main_base; a -= 4)
        if (*reinterpret_cast<const uint32_t*>(a) == 0xd10403ffu) {  // sub sp, sp, #0x100: the prologue
            start = a;
            break;
        }
    if (start) {
        log("p3p: function start main+%llx (%llx bytes before the abort block)", (unsigned long long)(start - g_main_base),
            (unsigned long long)(block - start));
        dump_code(start, 8);
    } else {
        log("p3p: function start not found");
    }

    int hits = 0;
    for (uint64_t a = start ? start : block - 0x2000; a < block && hits < 12; a += 4) {
        const uint32_t w = *reinterpret_cast<const uint32_t*>(a);
        bool is_branch;
        const int64_t off = branch_offset(w, is_branch);
        if (!is_branch || a + uint64_t(off) != block) continue;
        hits++;
        log("p3p: branch to the abort block at main+%llx (%08x); the 16 words up to it:", (unsigned long long)(a - g_main_base), w);
        dump_code(a - 0x3c, 16);
    }
    if (!hits) log("p3p: no branch to the abort block found");

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
    if (!a) {  // from the crash log: AbortImpl is at sdk+8d9f0
        if (const LoadedModule* s = module_at(find_symbol("nnosInitialize"))) a = s->base + 0x8d9f0;
    }
    if (a && (g_abort_orig = hook_function(a, abort_hook))) log("p3p: AbortImpl hooked");
    else log("p3p: AbortImpl could not be hooked");
}
void game::renderer_start() { log("p3p: renderer_start"); }
void game::first_frame() { log("p3p: first_frame"); }

}  // namespace nx
