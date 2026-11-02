// The import on the phone (libp3pimport.so, loaded by ImportActivity in its own process ":import"): the player's NSP
// and keys -> the game checked (title) and the shaders the game handed to NVN so far (captured by the runtime in
// <data>/shader_misses) translated to SPIR-V (libspirvharness.so) into <data>/spirv.
//   Importer.run(nspFd, nspName, keysPath, titleKeysPath, dataDir, workDir, jobs) -> 0, or an error (Importer.error())
//   Importer.progress() -> {phase, done, total}: 1 opening, 2 collecting, 3 translating, 4 finishing, 5 done
//
// Diagnostic: every run also writes <data>/shader_origin.txt, saying where the captured shaders sit inside the game's
// ExeFS modules (main, sdk), so they can be extracted without having to play first.
#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <jni.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "nxgame.h"

namespace {

constexpr uint64_t kTitle = 0x0100DCD01525A000ull;
constexpr const char* kBuildId = "";  // any version

std::atomic<int> g_phase{0}, g_done{0}, g_total{0};
std::mutex g_error_lock;
std::string g_error;

using HarnessMain = int (*)(int, const char**);
using HarnessProgress = void (*)(int*, int*);
HarnessProgress g_harness_progress = nullptr;

void set_error(const std::string& e) {
    std::lock_guard lk(g_error_lock);
    g_error = e;
}

std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

void remove_tree(const std::string& path) {
    if (DIR* d = opendir(path.c_str())) {
        while (dirent* e = readdir(d)) {
            std::string n = e->d_name;
            if (n == "." || n == "..") continue;
            std::string p = path + "/" + n;
            struct stat st{};
            if (lstat(p.c_str(), &st) == 0 && S_ISDIR(st.st_mode)) remove_tree(p);
            else unlink(p.c_str());
        }
        closedir(d);
    }
    rmdir(path.c_str());
}

int fail(const std::string& e) {
    set_error(e);
    g_phase = 0;
    return 1;
}

uint32_t le32(const uint8_t* p) { return uint32_t(p[0]) | uint32_t(p[1]) << 8 | uint32_t(p[2]) << 16 | uint32_t(p[3]) << 24; }

bool read_file(const std::string& path, std::vector<uint8_t>& out) {
    FILE* f = std::fopen(path.c_str(), "rb");
    if (!f) return false;
    std::fseek(f, 0, SEEK_END);
    long n = std::ftell(f);
    std::fseek(f, 0, SEEK_SET);
    out.assign(n > 0 ? size_t(n) : 0, 0);
    bool ok = n >= 0 && std::fread(out.data(), 1, out.size(), f) == out.size();
    std::fclose(f);
    return ok;
}

bool write_file(const std::string& path, const void* p, size_t n) {
    FILE* f = std::fopen(path.c_str(), "wb");
    if (!f) return false;
    bool ok = std::fwrite(p, 1, n, f) == n;
    return std::fclose(f) == 0 && ok;
}

// ---- where the captured shaders sit in the game's modules ----------------------------------------------------------

// The captured code blocks (code_<hash>.bin in <data>/shader_misses).
std::vector<std::pair<std::string, std::vector<uint8_t>>> captured_codes(const std::string& data_dir) {
    std::vector<std::pair<std::string, std::vector<uint8_t>>> out;
    std::vector<std::string> hashes;
    const std::string src = data_dir + "/shader_misses";
    if (DIR* d = opendir(src.c_str())) {
        while (dirent* e = readdir(d)) {
            const std::string n = e->d_name;
            if (n.size() == 25 && n.compare(0, 5, "code_") == 0 && n.compare(21, 4, ".bin") == 0)
                hashes.push_back(n.substr(5, 16));
        }
        closedir(d);
    }
    std::sort(hashes.begin(), hashes.end());
    for (const std::string& h : hashes) {
        std::vector<uint8_t> code;
        if (read_file(src + "/code_" + h + ".bin", code) && code.size() >= 0x100) out.push_back({h, std::move(code)});
    }
    return out;
}

// A 64-byte window of the program with plenty of non-zero bytes (not padding), to look for.
bool pick_window(const std::vector<uint8_t>& c, size_t& off) {
    for (size_t o = 0x40; o + 64 <= c.size(); o += 0x20) {
        int nonzero = 0;
        for (size_t k = 0; k < 64; k++) nonzero += c[o + k] != 0;
        if (nonzero >= 40) return off = o, true;
    }
    return false;
}

void report_origin(const nxi::GameImage& game, const std::string& data_dir) {
    const auto caps = captured_codes(data_dir);
    std::string rep = "captured shaders: " + std::to_string(caps.size()) + "\n";
    char line[512];
    for (const auto& [name, bytes] : game.exefs()) {
        if (bytes.size() < 0x100 || std::memcmp(bytes.data(), "NSO0", 4) != 0) continue;
        std::vector<uint8_t> flat;
        std::string err;
        if (!nxi::nso_to_flat_image(bytes, flat, err)) {
            rep += name + ": cannot be flattened: " + err + "\n";
            continue;
        }
        size_t code_magic = 0, ctrl_magic = 0;
        for (size_t o = 0; o + 0x30 <= flat.size(); o += 4) {
            const uint32_t v = le32(flat.data() + o);
            if (v == 0x98761234u) ctrl_magic++;
            if (v == 0x12345678u) {
                bool zeros = true;
                for (size_t k = 4; k < 0x30 && zeros; k++) zeros = flat[o + k] == 0;
                if (zeros) code_magic++;
            }
        }
        std::snprintf(line, sizeof line, "module %s: %zu bytes flat, %zu code blocks (magic 12345678 + zeros), %zu control sections (98761234)\n",
                      name.c_str(), flat.size(), code_magic, ctrl_magic);
        rep += line;
        size_t found = 0, full = 0;
        for (const auto& [hash, code] : caps) {
            size_t off = 0;
            if (!pick_window(code, off)) {
                rep += "  " + hash + ": no usable window\n";
                continue;
            }
            auto it = std::search(flat.begin(), flat.end(), code.begin() + off, code.begin() + off + 64);
            if (it == flat.end()) {
                if (found < 1000) rep += "  " + hash + " (" + std::to_string(code.size()) + " bytes): not in " + name + "\n";
                continue;
            }
            found++;
            const size_t pos = size_t(it - flat.begin());
            const bool whole = pos >= off && pos - off + code.size() <= flat.size() &&
                               std::memcmp(flat.data() + pos - off, code.data(), code.size()) == 0;
            full += whole;
            std::string before;
            if (pos >= off + 16)
                for (size_t k = 0; k < 16; k++) {
                    char b[4];
                    std::snprintf(b, sizeof b, "%02x", flat[pos - off - 16 + k]);
                    before += b;
                }
            std::snprintf(line, sizeof line, "  %s (%zu bytes): IN %s at %zx (block start %zx), whole block %s, 16 bytes before: %s\n",
                          hash.c_str(), code.size(), name.c_str(), pos, pos >= off ? pos - off : pos, whole ? "matches" : "differs",
                          before.c_str());
            rep += line;
        }
        std::snprintf(line, sizeof line, "module %s: %zu of %zu found, %zu whole\n", name.c_str(), found, caps.size(), full);
        rep += line;
    }
    write_file(data_dir + "/shader_origin.txt", rep.data(), rep.size());
}

// The shaders the game handed to NVN that had no translation (the runtime saves each one as code_<hash>.bin and
// ctrl_<hash>.bin in <data>/shader_misses, NX_DUMP_SHADER_MISSES): copied into <out>/misses with the manifest.tsv the
// translator reads. Returns how many.
long prepare_captured(const std::string& data_dir, const std::string& out_dir) {
    const std::string src = data_dir + "/shader_misses";
    std::vector<std::string> hashes;
    if (DIR* d = opendir(src.c_str())) {
        while (dirent* e = readdir(d)) {
            const std::string n = e->d_name;
            if (n.size() == 25 && n.compare(0, 5, "code_") == 0 && n.compare(21, 4, ".bin") == 0)
                hashes.push_back(n.substr(5, 16));
        }
        closedir(d);
    }
    if (hashes.empty()) return 0;
    std::sort(hashes.begin(), hashes.end());
    const std::string group_dir = out_dir + "/misses";
    mkdir(out_dir.c_str(), 0700);
    mkdir(group_dir.c_str(), 0700);
    std::string manifest =
        "id\tstage\tcode_off\tcode_size\tctrl_off\tctrl_size\tprogram_start\tprogram_bytes\tconst_off\tconst_size\t"
        "code_sha1_16\tcode_file\tctrl_file\n";
    long n = 0;
    char line[512];
    for (const std::string& h : hashes) {
        std::vector<uint8_t> code, ctrl;
        if (!read_file(src + "/code_" + h + ".bin", code) || !read_file(src + "/ctrl_" + h + ".bin", ctrl)) continue;
        if (ctrl.size() < 0x880 || code.size() < 0x34 || le32(ctrl.data()) != 0x98761234u) continue;
        const bool compute = le32(code.data()) == 0x12345679u || le32(ctrl.data() + 0x708) == 0;
        const uint32_t t = (le32(code.data() + 0x30) >> 10) & 0xF;
        const char* stage = compute ? "compute" : t == 1 ? "vertex" : t == 5 ? "pixel" : "other";
        if (!write_file(group_dir + "/code_" + h + ".bin", code.data(), code.size()) ||
            !write_file(group_dir + "/ctrl_" + h + ".bin", ctrl.data(), 0x880))
            continue;
        const unsigned long long off = (unsigned long long)(n + 1) * 0x1000;
        std::snprintf(line, sizeof line, "misses/0x%llx/%s\t%s\t0x%llx\t0x%llx\t0x%llx\t0x%x\t0x%x\t0x%x\t0x%x\t0x%x\t%s\tcode_%s.bin\tctrl_%s.bin\n",
                      off, stage, stage, off, (unsigned long long)code.size(), off, 0x880, le32(ctrl.data() + 0x708),
                      le32(ctrl.data() + 0x6f8), le32(ctrl.data() + 0x700), le32(ctrl.data() + 0x6fc), h.c_str(),
                      h.c_str(), h.c_str());
        manifest += line;
        n++;
    }
    if (n == 0) return 0;
    if (!write_file(group_dir + "/manifest.tsv", manifest.data(), manifest.size())) return 0;
    return n;
}

int run(int nsp_fd, const std::string& nsp_name, const std::string& keys, const std::string& title_keys,
        const std::string& data_dir, const std::string& work_dir, int jobs) {
    g_phase = 1, g_done = 0, g_total = 0;
    set_error("");
    // 1. the game file and the keys
    std::vector<nxi::InputFile> in;
    in.push_back({nsp_name, nsp_fd});
    int kfd = open(keys.c_str(), O_RDONLY | O_CLOEXEC);
    if (kfd < 0) return fail("cannot read prod.keys");
    in.push_back({"prod.keys", kfd});
    int tfd = title_keys.empty() ? -1 : open(title_keys.c_str(), O_RDONLY | O_CLOEXEC);
    if (tfd >= 0) in.push_back({"title.keys", tfd});
    std::string err;
    auto game = nxi::GameImage::open(in, kTitle, err);
    close(kfd);
    if (tfd >= 0) close(tfd);
    if (!game) return fail(err);
    auto main_it = game->exefs().find("main");
    if (main_it == game->exefs().end() || main_it->second.size() < 0x48) return fail("the game file has no main");
    char id[17];
    for (int i = 0; i < 8; i++) std::snprintf(id + 2 * i, 3, "%02X", main_it->second[0x40 + i]);
    if (kBuildId[0] && std::strcmp(id, kBuildId) != 0)
        return fail(std::string("this is not the expected version (build ") + id + ")");

    // diagnostic: where do the shaders the game uses sit inside its own modules?
    mkdir(data_dir.c_str(), 0755);
    try {
        report_origin(*game, data_dir);
    } catch (...) {
    }

    // 2. the shaders the game has asked for so far
    g_phase = 2;
    remove_tree(work_dir);
    mkdir(work_dir.c_str(), 0700);
    const std::string extracted = work_dir + "/shaders";
    const long blocks = prepare_captured(data_dir, extracted);
    if (blocks == 0) {  // nothing captured yet: play once, then prepare again
        mkdir((data_dir + "/spirv").c_str(), 0755);
        if (FILE* f0 = std::fopen((data_dir + "/spirv/import.txt").c_str(), "w")) {
            std::fprintf(f0, "%s\n%s\n0 shaders\n", nsp_name.c_str(), id);
            std::fclose(f0);
        }
        remove_tree(work_dir);
        g_phase = 5;
        return 0;
    }

    // 3. translated (the harness counts its own progress)
    g_phase = 3, g_done = 0, g_total = int(blocks);
    void* lib = dlopen("libspirvharness.so", RTLD_NOW);
    if (!lib) return fail(std::string("the shader translator is missing: ") + dlerror());
    auto harness = reinterpret_cast<HarnessMain>(dlsym(lib, "spirv_harness_main"));
    g_harness_progress = reinterpret_cast<HarnessProgress>(dlsym(lib, "spirv_harness_progress"));
    if (!harness || !g_harness_progress) return fail("the shader translator is incomplete");
    const std::string out_new = data_dir + "/spirv.new";
    remove_tree(out_new);
    std::string jobs_s = std::to_string(jobs > 0 ? jobs : 4);
    const char* args[] = {"--batch", extracted.c_str(), "--out", out_new.c_str(), "--jobs", jobs_s.c_str(), "--no-val"};
    int rc = harness(int(sizeof args / sizeof args[0]), args);
    if (rc != 0) return fail("the shader translation failed (code " + std::to_string(rc) + ")");

    // 4. in place of the old set; a note of what it was made from
    g_phase = 4;
    const std::string out = data_dir + "/spirv", old = data_dir + "/spirv.old";
    remove_tree(old);
    rename(out.c_str(), old.c_str());
    if (rename(out_new.c_str(), out.c_str()) != 0) return fail("cannot write " + out);
    remove_tree(old);
    remove_tree(work_dir);
    if (FILE* f = std::fopen((out + "/import.txt").c_str(), "w")) {
        std::fprintf(f, "%s\n%s\n%ld shaders\n", nsp_name.c_str(), id, blocks);
        std::fclose(f);
    }
    g_phase = 5;
    return 0;
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL Java_io_github_p3pandroid_Importer_run(JNIEnv* env, jclass, jint nsp_fd, jstring nsp_name,
                                                              jstring keys, jstring title_keys, jstring data_dir,
                                                              jstring work_dir, jint jobs) {
    return run(nsp_fd, jstr(env, nsp_name), jstr(env, keys), jstr(env, title_keys), jstr(env, data_dir),
               jstr(env, work_dir), jobs);
}

JNIEXPORT jintArray JNICALL Java_io_github_p3pandroid_Importer_progress(JNIEnv* env, jclass) {
    int v[3] = {g_phase.load(), g_done.load(), g_total.load()};
    if (v[0] == 3 && g_harness_progress) g_harness_progress(&v[1], &v[2]);
    jintArray a = env->NewIntArray(3);
    env->SetIntArrayRegion(a, 0, 3, v);
    return a;
}

JNIEXPORT jstring JNICALL Java_io_github_p3pandroid_Importer_error(JNIEnv* env, jclass) {
    std::lock_guard lk(g_error_lock);
    return env->NewStringUTF(g_error.c_str());
}

}  // extern "C"
