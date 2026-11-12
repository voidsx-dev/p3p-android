// Selftest of runtime/src/audio_fold.h: the old fold (add, clamp) against the limited one.
#include <cmath>
#include <cstdio>
#include <vector>

#include "../runtime/src/audio_fold.h"

static int fails = 0;
#define CHECK(c, ...) do { if (!(c)) { fails++; std::printf("FAIL: " __VA_ARGS__); std::printf("\n"); } } while (0)

// The fold the runtime had: sum, then hard clamp.
static void old_fold(const int16_t* pcm, size_t frames, std::vector<int16_t>& out) {
    for (size_t i = 0; i < frames; i++) {
        const int16_t* f = pcm + i * 6;
        int l = f[0] + (f[2] * 707 + f[4] * 707) / 1000, r = f[1] + (f[2] * 707 + f[5] * 707) / 1000;
        out[2 * i] = int16_t(std::clamp(l, -32768, 32767));
        out[2 * i + 1] = int16_t(std::clamp(r, -32768, 32767));
    }
}

static std::vector<int16_t> loud(size_t frames, double amp) {  // every channel a loud sine: the worst case
    std::vector<int16_t> v(frames * 6);
    for (size_t i = 0; i < frames; i++)
        for (int c = 0; c < 6; c++) v[i * 6 + c] = int16_t(amp * std::sin(2 * M_PI * (110.0 + 37 * c) * i / 48000.0));
    return v;
}

int main() {
    const size_t N = 48000;
    // 1. Loud mix: the old fold clamps many samples, the new one none, and nothing passes the threshold.
    {
        auto in = loud(N, 24000);
        std::vector<int16_t> a(N * 2), b(N * 2);
        old_fold(in.data(), N, a);
        nx::PeakLimiter lim;
        nx::fold_to_stereo(in.data(), N, 6, lim, b.data());
        size_t clipped_old = 0, clipped_new = 0;
        int peak_new = 0;
        for (size_t k = 0; k < N * 2; k++) {
            clipped_old += a[k] >= 32767 || a[k] <= -32768;
            clipped_new += b[k] >= 32767 || b[k] <= -32768;
            peak_new = std::max(peak_new, std::abs(int(b[k])));
        }
        std::printf("loud: old clamped %zu samples, new %zu, new peak %d\n", clipped_old, clipped_new, peak_new);
        CHECK(clipped_old > 1000, "the test signal should clip the old fold");
        CHECK(clipped_new == 0, "limited fold must not clamp");
        CHECK(peak_new <= int(0.95f * 32768.0f) + 1, "peak %d over the threshold", peak_new);
    }
    // 2. A mix with headroom passes through the same as before (the limiter stays at unity).
    {
        auto in = loud(N, 6000);
        std::vector<int16_t> a(N * 2), b(N * 2);
        old_fold(in.data(), N, a);
        nx::PeakLimiter lim;
        nx::fold_to_stereo(in.data(), N, 6, lim, b.data());
        int worst = 0;
        for (size_t k = 0; k < N * 2; k++) worst = std::max(worst, std::abs(a[k] - b[k]));
        std::printf("quiet: worst difference to the old fold %d, gain %.3f\n", worst, lim.gain());
        CHECK(worst <= 2, "quiet signal changed by %d", worst);
        CHECK(lim.gain() == 1.0f, "gain left unity");
    }
    // 3. In place gives the same as out of place; 4. the gain recovers after a burst.
    {
        auto in = loud(1000, 30000);
        std::vector<int16_t> out(2000), inplace = in;
        nx::PeakLimiter l1, l2;
        nx::fold_to_stereo(in.data(), 1000, 6, l1, out.data());
        nx::fold_to_stereo(inplace.data(), 1000, 6, l2, inplace.data());
        bool same = true;
        for (size_t k = 0; k < 2000; k++) same &= out[k] == inplace[k];
        CHECK(same, "in-place fold differs");
        const float low = l1.gain();
        auto quiet = loud(48000, 2000);
        std::vector<int16_t> o2(96000);
        nx::fold_to_stereo(quiet.data(), 48000, 6, l1, o2.data());
        std::printf("burst: gain %.3f, after 1 s of quiet %.3f\n", low, l1.gain());
        CHECK(low < 0.9f, "the burst should have lowered the gain");
        CHECK(l1.gain() > 0.999f, "gain did not recover in 1 s (%.4f)", l1.gain());
    }
    std::printf(fails ? "audio_fold: %d FAILED\n" : "audio_fold: ok\n", fails);
    return fails ? 1 : 0;
}
