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

uint64_t g_sdk_base = 0;
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

// The printable runs (4+ characters) of a buffer, joined with " | ".
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

// A HIPC request (the 0x100-byte message at TLS): its pointer (X) and send (A) buffers are where the log service's
// messages, file paths and the like travel. Logs the text found in them (the first 80 requests that carry some).
void note_request(uint64_t handle, uint64_t msg) {
    if (!readable(msg, 0x100)) return;
    const uint32_t* w = reinterpret_cast<const uint32_t*>(msg);
    const uint32_t type = w[0] & 0xFFFF, nstat = (w[0] >> 16) & 0xF, nsend = (w[0] >> 20) & 0xF,
                   nrecv = (w[0] >> 24) & 0xF, nexch = (w[0] >> 28) & 0xF;
    if (type != 4 && type != 6) return;
    size_t i = 2;
    if (w[1] >> 31) {  // special header: pid and handles
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
        if (k >= nsend) continue;  // only the send buffers (A)
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

// nn::sf::hipc::SendSyncRequest(handle, message, size): every IPC call of the sdk goes through it.
void send_hook(CPU& c) {
    note_request(c.x[0], c.x[1]);
    call(c, g_send_orig);
}

// AbortImpl(expr, func, file, line): the plain variant.
void abort_hook(CPU& c) {
    log("p3p: ABORT expr=\"%s\" func=\"%s\" file=\"%s\" line=%d, from %s", guest_str(c.x[0]).c_str(),
        guest_str(c.x[1]).c_str(), guest_str(c.x[2]).c_str(), int(c.x[3]), describe(c.x[30]).c_str());
    if (g_abort_orig) call(c, g_abort_orig);
}

// AbortImpl(expr, func, file, line, fmt, ...): the variant with a message, the one the game's abort() ends in.
void abort_msg_hook(CPU& c) {
    log("p3p: ABORT+msg expr=\"%s\" func=\"%s\" file=\"%s\" line=%d fmt=\"%s\" args %llx %llx %llx, from %s",
        guest_str(c.x[0]).c_str(), guest_str(c.x[1]).c_str(), guest_str(c.x[2]).c_str(), int(c.x[3]),
        guest_str(c.x[4]).c_str(), (unsigned long long)c.x[5], (unsigned long long)c.x[6], (unsigned long long)c.x[7],
        describe(c.x[30]).c_str());
    if (g_abort_msg_orig) call(c, g_abort_msg_orig);
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
    if (const LoadedModule* s = module_at(find_symbol("nnosInitialize"))) g_sdk_base = s->base;
    auto hook = [](const char* sym, uint64_t fallback, void (*fn)(CPU&), uint64_t& orig, const char* what) {
        uint64_t a = find_symbol(sym);
        if (!a && g_sdk_base) a = g_sdk_base + fallback;
        orig = a ? hook_function(a, fn) : 0;
        log("p3p: hook %s: %s", what, orig ? "ok" : "FAILED");
    };
    hook("_ZN2nn4diag6detail9AbortImplEPKcS3_S3_i", 0x8d9f0, abort_hook, g_abort_orig, "AbortImpl");
    hook("_ZN2nn4diag6detail9AbortImplEPKcS3_S3_iS3_z", 0x8d980, abort_msg_hook, g_abort_msg_orig, "AbortImpl(msg)");
    hook("_ZN2nn2sf4hipc15SendSyncRequestENS_3svc6HandleEPvm", 0x13f290, send_hook, g_send_orig, "SendSyncRequest");
}
void game::renderer_start() { log("p3p: renderer_start"); }
void game::first_frame() { log("p3p: first_frame"); }

}  // namespace nx
