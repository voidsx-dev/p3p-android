// 5.1 -> stereo fold-down with a linked peak limiter (header only, so tests/audio_fold_test.cpp can use it).
//
// The game opens its AudioOut with 6 channels (FL FR FC LFE RL RR) and expects the system to fold them. Adding
// FC and the rears at -3 dB to full-scale FL/FR and then clamping to s16 clips hard whenever the mix is loud;
// headphones and the DAC show it as harsh crackle ("estouros"), a small speaker hides most of it.
// The fold keeps the same coefficients (nothing is attenuated while the mix has headroom) and the limiter only
// turns the gain down on the samples that would pass the threshold: instant attack, ~80 ms release, one gain
// for both channels (the stereo image does not move).
#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>

namespace nx {

class PeakLimiter {
public:
    explicit PeakLimiter(float threshold = 0.95f * 32768.0f, float release_s = 0.08f, float rate = 48000.0f)
        : thr_(threshold), rel_(1.0f - std::exp(-1.0f / (release_s * rate))) {}

    void process(float l, float r, int16_t& ol, int16_t& orr) {
        const float peak = std::max(std::fabs(l), std::fabs(r));
        const float target = peak > thr_ ? thr_ / peak : 1.0f;
        gain_ = target < gain_ ? target : std::min(target, gain_ + (1.0f - gain_) * rel_);
        ol = to_s16(l * gain_);
        orr = to_s16(r * gain_);
    }
    float gain() const { return gain_; }

private:
    static int16_t to_s16(float v) { return int16_t(std::clamp(std::lrintf(v), -32768L, 32767L)); }
    float thr_, rel_, gain_ = 1.0f;
};

// `pcm` holds `frames` frames of `ch` interleaved s16 channels; the stereo result goes to out[2 * i], out[2 * i + 1].
// out may be pcm itself (frame i is read before it is written, and 2 * i + 1 < i * ch for every i >= 1 when ch >= 3).
inline void fold_to_stereo(const int16_t* pcm, uint64_t frames, uint32_t ch, PeakLimiter& lim, int16_t* out) {
    for (uint64_t i = 0; i < frames; i++) {
        const int16_t* f = pcm + i * ch;
        float l = f[0], r = f[1];
        if (ch >= 6) {  // FL FR FC LFE RL RR (the LFE is left out, as before)
            l += 0.707f * (float(f[2]) + float(f[4]));
            r += 0.707f * (float(f[2]) + float(f[5]));
        }
        lim.process(l, r, out[2 * i], out[2 * i + 1]);
    }
}

}  // namespace nx
