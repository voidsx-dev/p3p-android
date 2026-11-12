#include <cstdlib>
// Android host (NativeActivity): memory (mmap at fixed addresses), pthreads, the app's window (ANativeWindow),
// game controllers + touch, AAudio, the Vulkan loader and android surface, a signal crash handler and
// android_main. Game data lives in the app's external files dir (see docs/ANDROID.md):
//   <files>/analysis/{main,sdk}.bin   module images (tools/nso2bin.py)
//   <files>/extract/romfs/...         the RomFS
//   <files>/user/...                  saves
//   <files>/config.txt                optional: resolution=WxH
#include <aaudio/AAudio.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/window.h>
#include <android_native_app_glue.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <jni.h>
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <sys/mman.h>
#include <time.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cerrno>
#include <cmath>
#include <cstring>
#include <deque>
#include <filesystem>
#include <fstream>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#define VK_NO_PROTOTYPES
#define VK_USE_PLATFORM_ANDROID_KHR
#include <vulkan/vulkan.h>

#include <nx/game.h>
#include <nx/runtime.h>

#include "kernel.h"
#include "platform.h"
#include "platform_android.h"

// Android audio out: AAudio (split from platform_android.cpp).
namespace nx::plat {

// ---- audio: AAudio (callback pulls from a queue of submitted buffers) ------------------------------------------

namespace {
// The game's buffers are copied into a ring at submit and played from there. Each is handed back (done) when the
// device's read position reaches its end minus the A/V delay: with no delay, when it has played (as before); with a
// delay of D, D earlier, so the game queues D more sound ahead and what it mixes reaches the speaker D later, while
// its own audio clock keeps real time (audio_set_delay_ms, set from the frame rate: nvn_queue.cpp).
constexpr uint64_t kRingFrames = 1 << 16;  // ~1.4 s of 48 kHz stereo s16
struct Pending {
    uint64_t end;  // ring position (frames) after its last frame
    void* ctx;
};
std::mutex g_audio;
std::vector<uint32_t> g_ring(kRingFrames);  // one stereo s16 frame per element
uint64_t g_written = 0, g_read = 0;         // frames, since the stream opened
uint64_t g_device = 0;                      // frames the callback gave the device (silence included): AAudio's count
std::atomic<uint64_t> g_latency_ns{0};      // audio_latency_ns
std::deque<Pending> g_pending;
std::atomic<uint32_t> g_delay_frames{0};
// The game keeps only ~3 buffers (15 ms) in its AudioOut queue and gets one back when the device has played it. A
// device that pulls a big burst at once (headphones: AAudio fell back to performance mode 10 with 960-frame bursts)
// finds the ring shorter than the burst and gets silence in the middle of the sound (the crackle). So buffers are
// handed back at least g_floor_frames early (the same mechanism as the A/V delay: the game queues that much more
// ahead): the burst at first, raised by 10 ms each time a 2 s window still had underruns, never lowered, at most 100 ms.
std::atomic<uint32_t> g_floor_frames{0};
constexpr uint32_t kFloorStep = 480, kFloorMax = 4800;
AudioDone g_done = nullptr;
AAudioStream* g_stream = nullptr;
// Headphones plugged or unplugged (or any route change) make AAudio disconnect the stream: no more callbacks, no sound
// until a new stream is opened on the new route. The error callback starts that on a thread of its own (a stream
// cannot be closed from its own callbacks). While there is no stream, audio_submit says so and the caller paces the
// buffers itself, so the game keeps running (silent) until the new stream is up.
std::mutex g_stream_mu;                  // g_stream: the reopen thread's swap, against audio_pause and the latency measure
std::atomic<bool> g_have_stream{false};  // a stream is open and running (audio_submit)
std::atomic<bool> g_reopening{false};
std::atomic<bool> g_paused{false};       // the app is in the background
std::atomic<int> g_cb_windows{0};        // stats windows since the stream opened (the first one is not judged)
// Per ~2 s, logged from the callback: how the device pulls (callbacks, frames per call) and how often the game's
// buffers ran out (underruns: silence played, and the game's buffers come back late, its clock stalls).
struct CbStats {
    uint64_t from_ns = 0, calls = 0, frames = 0, underruns = 0, silent_frames = 0, max_gap_ns = 0, last_ns = 0;
    int32_t min_frames = 1 << 30, max_frames = 0;
} g_cb;

void cb_stats(int32_t frames, size_t missing_bytes) {
    CbStats& s = g_cb;
    const uint64_t now = monotonic_ns();
    if (!s.from_ns) s.from_ns = now;
    if (s.last_ns) s.max_gap_ns = std::max(s.max_gap_ns, now - s.last_ns);
    s.last_ns = now;
    s.calls++, s.frames += uint64_t(frames);
    s.min_frames = std::min(s.min_frames, frames), s.max_frames = std::max(s.max_frames, frames);
    if (missing_bytes) s.underruns++, s.silent_frames += missing_bytes / 4;
    if (now - s.from_ns < 2'000'000'000ull) return;
    if (s.underruns || std::getenv("NX_AUDIO_STATS"))
        nx::log("audio: device pulled %llu times in %.1f s (%d..%d frames, longest %.1f ms apart); %llu underruns, "
                "%.0f ms of silence", (unsigned long long)s.calls, double(now - s.from_ns) / 1e9, s.min_frames,
                s.max_frames, double(s.max_gap_ns) / 1e6, (unsigned long long)s.underruns,
                double(s.silent_frames) / 48.0);
    // the stream's first window is the boot's loading (or the reopen's gap): no judgement from it
    if (g_cb_windows.fetch_add(1, std::memory_order_relaxed) && s.underruns) {
        const uint32_t f = g_floor_frames.load(std::memory_order_relaxed);
        if (f < kFloorMax) {
            g_floor_frames.store(std::min(f + kFloorStep, kFloorMax), std::memory_order_relaxed);
            nx::log("audio: underruns persist: sound queued %u ms ahead (was %u)", std::min(f + kFloorStep, kFloorMax) / 48,
                    f / 48);
        }
    }
    s = {};
    s.from_ns = s.last_ns = now;
}

aaudio_data_callback_result_t audio_cb(AAudioStream*, void*, void* out, int32_t frames) {
    auto* dst = static_cast<uint32_t*>(out);
    size_t missing = 0;
    std::vector<void*> finished;
    {
        std::lock_guard lk(g_audio);
        const uint64_t n = std::min<uint64_t>(uint64_t(frames), g_written - g_read);
        for (uint64_t k = 0; k < n; k++) dst[k] = g_ring[(g_read + k) % kRingFrames];
        g_read += n;
        g_device += uint64_t(frames);
        missing = size_t(frames) - size_t(n);
        if (missing) std::memset(dst + n, 0, missing * 4);  // underrun: silence
        const uint64_t due = g_read + std::max(g_delay_frames.load(std::memory_order_relaxed), g_floor_frames.load(std::memory_order_relaxed));
        while (!g_pending.empty() && g_pending.front().end <= due) {
            finished.push_back(g_pending.front().ctx);
            g_pending.pop_front();
        }
    }
    cb_stats(frames, missing * 4);
    for (void* c : finished) g_done(c);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
}  // namespace

static bool open_stream();
static void start_reopen();

static void audio_err(AAudioStream*, void*, aaudio_result_t error) {
    nx::log("audio: stream error %d (%s): opening a new stream", int(error),
            error == AAUDIO_ERROR_DISCONNECTED ? "disconnected, the output changed" : "other");
    start_reopen();
}

// Opens and starts a stream on the current default output into g_stream (g_stream_mu held, or nothing else running).
static bool open_stream() {
    AAudioStreamBuilder* b = nullptr;
    if (AAudio_createStreamBuilder(&b) != AAUDIO_OK) return false;
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(b, 2);
    AAudioStreamBuilder_setSampleRate(b, 48000);
    // NX_AUDIO_MODE=none|powersave (default: lowlatency) to compare the device paths: with headphones some phones
    // route a low-latency stream differently from a normal one.
    aaudio_performance_mode_t mode = AAUDIO_PERFORMANCE_MODE_LOW_LATENCY;
    if (const char* m = std::getenv("NX_AUDIO_MODE")) {
        if (!std::strcmp(m, "none")) mode = AAUDIO_PERFORMANCE_MODE_NONE;
        else if (!std::strcmp(m, "powersave")) mode = AAUDIO_PERFORMANCE_MODE_POWER_SAVING;
    }
    AAudioStreamBuilder_setPerformanceMode(b, mode);
    AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_GAME);
    AAudioStreamBuilder_setDataCallback(b, audio_cb, nullptr);
    AAudioStreamBuilder_setErrorCallback(b, audio_err, nullptr);
    AAudioStream* st = nullptr;
    bool ok = AAudioStreamBuilder_openStream(b, &st) == AAUDIO_OK;
    AAudioStreamBuilder_delete(b);
    if (!ok) return false;
    // The device asks for whatever its buffer has free: on the fallback path (burst 960, buffer 11724 frames) that
    // meant tiny pulls all the time and a ring that never held enough. 4 bursts keep the pulls regular.
    const int32_t burst = std::max<int32_t>(AAudioStream_getFramesPerBurst(st), 128);
    AAudioStream_setBufferSizeInFrames(st, std::min<int32_t>(burst * 4, AAudioStream_getBufferCapacityInFrames(st)));
    g_floor_frames.store(uint32_t(burst), std::memory_order_relaxed);  // per route: headphones have bigger bursts
    nx::log("audio: sound queued %u ms ahead at least (one device burst), device buffer %d frames", uint32_t(burst) / 48,
            int(AAudioStream_getBufferSizeInFrames(st)));
    nx::log("audio: AAudio stream: %d frames per burst, buffer %d of %d frames, performance mode %d",
            int(AAudioStream_getFramesPerBurst(st)), int(AAudioStream_getBufferSizeInFrames(st)),
            int(AAudioStream_getBufferCapacityInFrames(st)), int(AAudioStream_getPerformanceMode(st)));
    g_stream = st;
    if (AAudioStream_requestStart(st) != AAUDIO_OK) {
        AAudioStream_close(st);
        g_stream = nullptr;
        return false;
    }
    if (g_paused.load()) AAudioStream_requestPause(st);
    g_have_stream.store(true, std::memory_order_release);
    return true;
}

// Thread: closes the dead stream and opens a new one, retrying until the new route is there (a plugged-in headset
// can take a moment to show up as the default output).
static void reopen_stream() {
    for (int attempt = 1;; attempt++) {
        {
            std::lock_guard lk(g_stream_mu);
            if (g_stream) AAudioStream_close(g_stream);
            g_stream = nullptr;
            {
                std::lock_guard a(g_audio);
                g_device = 0;  // AAudio's frame count starts over with the new stream (measure_latency)
            }
            g_latency_ns.store(0, std::memory_order_relaxed);
            g_cb = {};
            g_cb_windows.store(0, std::memory_order_relaxed);
            if (open_stream()) {
                nx::log("audio: new stream running (attempt %d)", attempt);
                g_reopening.store(false);
                return;
            }
        }
        if (attempt == 1 || attempt % 20 == 0) nx::log("audio: no stream yet (attempt %d), trying again", attempt);
        std::this_thread::sleep_for(std::chrono::milliseconds(attempt < 20 ? 250 : 1000));
    }
}

static void start_reopen() {
    g_have_stream.store(false, std::memory_order_release);
    if (g_reopening.exchange(true)) return;  // one at a time
    std::thread(reopen_stream).detach();
}

bool audio_open(AudioDone done) {
    g_done = done;
    // NX_MUTE=1 (and headless test runs): no device, the callers pace themselves with a timer (as on Windows and macOS).
    if (headless() || std::getenv("NX_MUTE")) return false;
    std::lock_guard lk(g_stream_mu);
    return open_stream();
}

void audio_pause(bool paused) {
    g_paused.store(paused);
    std::lock_guard lk(g_stream_mu);
    if (!g_stream) return;  // being reopened: the new stream follows g_paused
    if (paused)
        AAudioStream_requestPause(g_stream);
    else if (AAudioStream_requestStart(g_stream) != AAUDIO_OK)  // the route changed while in the background
        start_reopen();
}

// When the buffer starting at ring position `start` reaches the speaker: AAudio's last timestamp (a frame the device
// presented and when) moved by the frames between, the ring's queue ahead of it and the device's own buffer included.
// Every 8th buffer, smoothed (A/V sync, nvn_queue.cpp).
static void measure_latency(uint64_t start, uint64_t read, uint64_t device) {
    static uint32_t n = 0;
    if (n++ % 8) return;
    int64_t pos = 0, at = 0;
    std::unique_lock lk(g_stream_mu, std::try_to_lock);  // not while a reopen holds it
    if (!lk || !g_stream) return;
    if (AAudioStream_getTimestamp(g_stream, CLOCK_MONOTONIC, &pos, &at) != AAUDIO_OK || at <= 0) return;
    lk.unlock();
    const int64_t frame = int64_t(device + (start - read));  // the device's index of the buffer's first frame
    const int64_t when = at + (frame - pos) * 1000000000ll / 48000;
    const int64_t now = int64_t(monotonic_ns());
    if (when <= now || when - now > 2000000000ll) return;
    const uint64_t l = uint64_t(when - now), old = g_latency_ns.load(std::memory_order_relaxed);
    g_latency_ns.store(old ? old + (int64_t(l) - int64_t(old)) / 8 : l, std::memory_order_relaxed);
}

bool audio_submit(const void* data, size_t bytes, void* ctx) {
    if (!g_have_stream.load(std::memory_order_acquire)) return false;  // no stream (being reopened): the caller paces
    uint64_t start, read, device;
    std::vector<void*> finished;
    {
        std::lock_guard lk(g_audio);
        start = g_written, read = g_read, device = g_device;
        const uint64_t n = std::min<uint64_t>(bytes / 4, kRingFrames - (g_written - g_read));  // never more than a full ring
        const auto* src = static_cast<const uint8_t*>(data);
        for (uint64_t k = 0; k < n; k++) std::memcpy(&g_ring[(g_written + k) % kRingFrames], src + 4 * k, 4);
        g_written += n;
        g_pending.push_back({g_written, ctx});
        // Buffers already due (the floor / A/V delay reaches past what the ring holds) go back now, not at the next
        // device pull: a ring that a big pull emptied is otherwise refilled only to the game's own 3 buffers, and the
        // next pull is short again. The data is already copied into the ring, the game may reuse the buffer.
        const uint64_t due = g_read + std::max(g_delay_frames.load(std::memory_order_relaxed),
                                               g_floor_frames.load(std::memory_order_relaxed));
        while (!g_pending.empty() && g_pending.front().end <= due) {
            finished.push_back(g_pending.front().ctx);
            g_pending.pop_front();
        }
    }
    measure_latency(start, read, device);
    for (void* c : finished) g_done(c);
    return true;
}

uint64_t audio_latency_ns() { return g_latency_ns.load(std::memory_order_relaxed); }

void audio_set_delay_ms(uint32_t ms) { g_delay_frames.store(std::min<uint32_t>(ms, 500) * 48, std::memory_order_relaxed); }

uint32_t audio_queued() {
    std::lock_guard lk(g_audio);
    return uint32_t(g_pending.size());
}

}  // namespace nx::plat
