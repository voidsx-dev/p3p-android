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
#include <vector>

#define VK_NO_PROTOTYPES
#define VK_USE_PLATFORM_ANDROID_KHR
#include <vulkan/vulkan.h>

#include <nx/game.h>
#include <nx/runtime.h>

#include "kernel.h"
#include "pad_map.h"
#include "platform.h"
#include "platform_android.h"
#include "touch_layout.h"

// Android input: game controllers and touch (split from platform_android.cpp).
namespace nx::plat {

// ---- input: game controllers + touch --------------------------------------------------------------------------
// Touch: left half = virtual stick (relative to where the finger went down); right half = four buttons in a
// diamond around the thumb (Y attack up-left, X special up-right, B dash down-left, A interact down-right), top
// corners L / R, top centre +. Games with a drawn layout (touch_layout.cpp) use its buttons instead, and may make the
// rest of the right half a second stick (P5R: the camera).

namespace {
std::mutex g_in;
PadState g_pad_buttons;  // controller state (buttons: from keys)
uint64_t g_axis_btn = 0;   // buttons held through axes (hat, triggers, mapped axes), kept apart so a key never clears them
enum Stick : uint8_t { kNoStick, kLeftStick, kRightStick };
struct Touch {
    int32_t id;
    float x0, y0, x, y;
    Stick stick;
    uint64_t button;
    uint64_t down_ns;
};
std::vector<Touch> g_touches;
// A quick tap still reaches the game: a button let go sooner than kMinHoldNs after it went down stays held until
// then (some P5R menus missed taps of 100-200 ms that the game had read on several frames).
struct Latch {
    uint64_t button, until_ns;
};
std::vector<Latch> g_latched;
constexpr uint64_t kMinHoldNs = 50'000'000;

void release_touch(const Touch& t, uint64_t now) {
    if (t.button && now < t.down_ns + kMinHoldNs) g_latched.push_back({t.button, t.down_ns + kMinHoldNs});
}
float g_w = 1, g_h = 1;
bool g_gamepad = false;  // the last input came from a controller: the drawn touch controls hide
bool g_generic_pad = false;  // a BUTTON_1..12 key came in: a generic (PS2-style) pad is in use
uint64_t g_last_touch_ns = 0;  // the drawn controls dim after kTouchIdleNs without a touch
constexpr uint64_t kTouchIdleNs = 8'000'000'000;

// NX_TOUCH_STYLE (launcher: Button layout style): "switch" (default), "xbox" or "ps2". It decides what the four face
// buttons of a controller (and the drawn touch buttons) do, by position: Switch puts A (confirm) on the right and B
// (back) at the bottom; Xbox and PlayStation 2 put confirm at the bottom (A / Cross) and back on the right (B / Circle).
enum class Style { kSwitch, kXbox, kPs2 };
Style pad_style() {
    static const Style s = [] {
        const char* e = std::getenv("NX_TOUCH_STYLE");
        if (e && std::strcmp(e, "ps2") == 0) return Style::kPs2;
        if (e && std::strcmp(e, "xbox") == 0) return Style::kXbox;
        return Style::kSwitch;
    }();
    return s;
}

// The player's own mapping (NX_PAD_MAP, see pad_map.h), read once. It comes first: a key it names is never looked up
// in the built-in tables below.
const PadMap& pad_map() {
    static const PadMap m = [] {
        PadMap p = parse_pad_map(std::getenv("NX_PAD_MAP"));
        if (!p.empty()) {
            char line[160];
            std::snprintf(line, sizeof line, "pad: custom mapping: %zu keys, %zu axis buttons, sticks %d%d%d%d", p.keys.size(),
                          p.axes.size(), p.stick[0].set, p.stick[1].set, p.stick[2].set, p.stick[3].set);
            log_line(line);
        }
        return p;
    }();
    return m;
}

// True when the player mapped one of the axes `lo`..`hi` to a button (then the built-in use of those axes stands aside).
bool axis_remapped(int32_t lo, int32_t hi) {
    for (const auto& a : pad_map().axes)
        if (a.axis >= lo && a.axis <= hi) return true;
    return false;
}

// True when the player mapped a d-pad key (Android also makes these from the hat): the hat then no longer presses the
// built-in directions, or one press would act twice.
bool dpad_key_remapped() {
    for (const auto& k : pad_map().keys)
        if (k.code >= AKEYCODE_DPAD_UP && k.code <= AKEYCODE_DPAD_RIGHT) return true;
    return false;
}

uint64_t key_to_button(int32_t k) {
    const Style style = pad_style();
    const bool south_confirm = style != Style::kSwitch;
    switch (k) {
        // Android's standardized face keys describe physical positions. The selected profile
        // decides which logical face-button action those positions produce.
        case AKEYCODE_BUTTON_A: return south_confirm ? kA : kB;  // bottom (Cross)
        case AKEYCODE_BUTTON_B: return south_confirm ? kB : kA;  // right (Circle)
        case AKEYCODE_BUTTON_X: return south_confirm ? kX : kY;  // left (Square)
        case AKEYCODE_BUTTON_Y: return south_confirm ? kY : kX;  // top (Triangle)
        case AKEYCODE_BUTTON_L1: return kL;
        case AKEYCODE_BUTTON_R1: return kR;
        case AKEYCODE_BUTTON_L2: return kZL;
        case AKEYCODE_BUTTON_R2: return kZR;
        case AKEYCODE_BUTTON_START: return kPlus;
        case AKEYCODE_BUTTON_SELECT: return kMinus;
        case AKEYCODE_BUTTON_THUMBL: return kStickL;
        case AKEYCODE_BUTTON_THUMBR: return kStickR;
        case AKEYCODE_DPAD_LEFT: return kLeft;
        case AKEYCODE_DPAD_UP: return kUp;
        case AKEYCODE_DPAD_RIGHT: return kRight;
        case AKEYCODE_DPAD_DOWN: return kDown;
        // A keyboard: Enter / Space confirm (A), Escape goes back (B). (Some tablets keep BUTTON_B for their stylus.)
        case AKEYCODE_ENTER: case AKEYCODE_NUMPAD_ENTER: case AKEYCODE_SPACE: case AKEYCODE_DPAD_CENTER: return kA;
        case AKEYCODE_ESCAPE: return kB;
        default: break;
    }
    // PlayStation 2 pads on a USB adapter (and other generic HID pads) have no standard Android layout: their buttons
    // arrive as BUTTON_1..BUTTON_12 in the order the adapter lists them. The common "PC TWIN SHOCK" order is used:
    // Triangle, Circle, Cross, Square, L2, R2, L1, R1, Select, Start, L3, R3. They work with every profile (the face
    // buttons follow the chosen profile by position, like the standard ones); before, these keys were ignored.
    switch (k) {
        case AKEYCODE_BUTTON_1: g_generic_pad = true; return south_confirm ? kY : kX;  // Triangle (top)
        case AKEYCODE_BUTTON_2: g_generic_pad = true; return south_confirm ? kB : kA;  // Circle (right)
        case AKEYCODE_BUTTON_3: g_generic_pad = true; return south_confirm ? kA : kB;  // Cross (bottom)
        case AKEYCODE_BUTTON_4: g_generic_pad = true; return south_confirm ? kX : kY;  // Square (left)
        case AKEYCODE_BUTTON_5: g_generic_pad = true; return kZL;       // L2
        case AKEYCODE_BUTTON_6: g_generic_pad = true; return kZR;       // R2
        case AKEYCODE_BUTTON_7: g_generic_pad = true; return kL;        // L1
        case AKEYCODE_BUTTON_8: g_generic_pad = true; return kR;        // R1
        case AKEYCODE_BUTTON_9: g_generic_pad = true; return kMinus;    // Select
        case AKEYCODE_BUTTON_10: g_generic_pad = true; return kPlus;    // Start
        case AKEYCODE_BUTTON_11: g_generic_pad = true; return kStickL;  // L3
        case AKEYCODE_BUTTON_12: g_generic_pad = true; return kStickR;  // R3
        default: return 0;
    }
}

uint64_t touch_button(float x, float y) {
    float nx_ = x / g_w, ny = y / g_h;
    if (ny < 0.2f) return nx_ < 0.6f ? (nx_ < 0.45f ? kL : kPlus) : (nx_ > 0.85f ? kR : kZR);
    bool up = ny < 0.6f, right = nx_ > 0.8f;
    if (up) return right ? kX : kY;
    return right ? kA : kB;
}

}  // namespace

int32_t on_input(android_app*, AInputEvent* e) {
    std::lock_guard lk(g_in);
    int32_t type = AInputEvent_getType(e);
    int32_t src = AInputEvent_getSource(e);
    if (type == AINPUT_EVENT_TYPE_KEY) {
        const int32_t code = AKeyEvent_getKeyCode(e);
        uint64_t b = pad_map().key(code);
        if (!b) {
            // The d-pad keys Android makes from the hat stand aside when the player mapped the hat to other buttons.
            if (code >= AKEYCODE_DPAD_UP && code <= AKEYCODE_DPAD_RIGHT && axis_remapped(AMOTION_EVENT_AXIS_HAT_X, AMOTION_EVENT_AXIS_HAT_Y))
                return 1;
            b = key_to_button(code);
        }
        if (!b) {
            // A controller key nothing uses: said once in the log, so a pad that does not work can be mapped from it.
            static std::vector<int32_t> told;
            if ((src & AINPUT_SOURCE_GAMEPAD) == AINPUT_SOURCE_GAMEPAD && told.size() < 32 &&
                std::find(told.begin(), told.end(), code) == told.end()) {
                told.push_back(code);
                char line[96];
                std::snprintf(line, sizeof line, "pad: unmapped controller key %d", int(code));
                log_line(line);
            }
            return 0;
        }
        g_gamepad = true;
        if (AKeyEvent_getAction(e) == AKEY_EVENT_ACTION_DOWN)
            g_pad_buttons.buttons |= b;
        else if (AKeyEvent_getAction(e) == AKEY_EVENT_ACTION_UP)
            g_pad_buttons.buttons &= ~b;
        return 1;
    }
    if (type != AINPUT_EVENT_TYPE_MOTION) return 0;
    if ((src & AINPUT_SOURCE_JOYSTICK) == AINPUT_SOURCE_JOYSTICK) {
        g_gamepad = true;
        const PadMap& pm = pad_map();
        auto raw = [&](int32_t a) { return AMotionEvent_getAxisValue(e, a, 0); };
        auto axis = [&](int32_t a) {
            float v = raw(a);
            return std::fabs(v) < 0.15f ? 0 : int32_t(std::clamp(v, -1.f, 1.f) * kStickMax);
        };
        g_pad_buttons.lx = axis(AMOTION_EVENT_AXIS_X);
        g_pad_buttons.ly = -axis(AMOTION_EVENT_AXIS_Y);
        g_pad_buttons.rx = axis(AMOTION_EVENT_AXIS_Z);
        g_pad_buttons.ry = -axis(AMOTION_EVENT_AXIS_RZ);
        // PS2 pads on a USB adapter (the PS2 profile, or once a generic pad pressed a button) often report the right
        // stick as RX / RY instead of Z / RZ.
        if ((pad_style() == Style::kPs2 || g_generic_pad) && !g_pad_buttons.rx && !g_pad_buttons.ry) {
            g_pad_buttons.rx = axis(AMOTION_EVENT_AXIS_RX);
            g_pad_buttons.ry = -axis(AMOTION_EVENT_AXIS_RY);
        }
        // The player's own sticks (Y: down is positive in Android, up in the game).
        auto custom = [&](int which, int32_t& out, bool down_positive) {
            const PadMap::StickAxis& st = pm.stick[which];
            if (st.set) out = int32_t(pad_stick_value(raw(st.axis), down_positive, st.invert) * kStickMax);
        };
        custom(PadMap::kLX, g_pad_buttons.lx, false);
        custom(PadMap::kLY, g_pad_buttons.ly, true);
        custom(PadMap::kRX, g_pad_buttons.rx, false);
        custom(PadMap::kRY, g_pad_buttons.ry, true);
        // Buttons held through axes: the hat, the analog triggers, and the player's mapped axes.
        uint64_t held = 0;
        if (!axis_remapped(AMOTION_EVENT_AXIS_HAT_X, AMOTION_EVENT_AXIS_HAT_Y) && !dpad_key_remapped()) {
            float hx = raw(AMOTION_EVENT_AXIS_HAT_X), hy = raw(AMOTION_EVENT_AXIS_HAT_Y);
            if (hx < -0.5f) held |= kLeft;
            if (hx > 0.5f) held |= kRight;
            if (hy < -0.5f) held |= kUp;
            if (hy > 0.5f) held |= kDown;
        }
        if (!axis_remapped(AMOTION_EVENT_AXIS_LTRIGGER, AMOTION_EVENT_AXIS_RTRIGGER) &&
            !axis_remapped(AMOTION_EVENT_AXIS_GAS, AMOTION_EVENT_AXIS_BRAKE)) {
            if (raw(AMOTION_EVENT_AXIS_LTRIGGER) > 0.4f || raw(AMOTION_EVENT_AXIS_BRAKE) > 0.4f) held |= kZL;
            if (raw(AMOTION_EVENT_AXIS_RTRIGGER) > 0.4f || raw(AMOTION_EVENT_AXIS_GAS) > 0.4f) held |= kZR;
        }
        for (const auto& a : pm.axes) {
            const float v = raw(a.axis);
            if (a.sign > 0 ? v > 0.5f : v < -0.5f) held |= a.button;
        }
        g_axis_btn = held;
        return 1;
    }
    if ((src & AINPUT_SOURCE_TOUCHSCREEN) != AINPUT_SOURCE_TOUCHSCREEN) return 0;
    if (!touch_enabled()) return 0;  // NX_TOUCH_SHOW=0
    int32_t action = AMotionEvent_getAction(e);
    int32_t kind = action & AMOTION_EVENT_ACTION_MASK;
    size_t index = size_t((action & AMOTION_EVENT_ACTION_POINTER_INDEX_MASK) >> AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT);
    const uint64_t now = monotonic_ns();
    g_last_touch_ns = now;
    if (ANativeWindow* w = android_window()) {
        g_w = float(ANativeWindow_getWidth(w));
        g_h = float(ANativeWindow_getHeight(w));
    }
    if (kind == AMOTION_EVENT_ACTION_DOWN || kind == AMOTION_EVENT_ACTION_POINTER_DOWN) {
        float x = AMotionEvent_getX(e, index), y = AMotionEvent_getY(e, index);
        g_gamepad = false;
        size_t drawn = 0;
        touch_buttons(drawn);
        if (drawn) {  // a drawn layout (touch_layout.cpp): its buttons first, the rest of the left half is the stick
            uint64_t b = touch_hit(x / g_w, y / g_h, g_w / g_h);
            bool left = !b && touch_stick_side(x / g_w);
            Stick stick = b ? kNoStick : left ? kLeftStick : touch_right_stick() ? kRightStick : kNoStick;
            if (b || stick) g_touches.push_back({AMotionEvent_getPointerId(e, index), x, y, x, y, stick, b, now});
        } else {
            bool left = x < g_w * 0.5f;
            g_touches.push_back({AMotionEvent_getPointerId(e, index), x, y, x, y, left ? kLeftStick : kNoStick,
                                 left ? 0 : touch_button(x, y), now});
        }
    } else if (kind == AMOTION_EVENT_ACTION_UP || kind == AMOTION_EVENT_ACTION_POINTER_UP ||
               kind == AMOTION_EVENT_ACTION_CANCEL) {
        int32_t id = AMotionEvent_getPointerId(e, index);
        const bool all = kind == AMOTION_EVENT_ACTION_UP || kind == AMOTION_EVENT_ACTION_CANCEL;
        g_touches.erase(std::remove_if(g_touches.begin(), g_touches.end(),
                                       [&](const Touch& t) {
                                           if (!all && t.id != id) return false;
                                           release_touch(t, now);
                                           return true;
                                       }),
                        g_touches.end());
    } else if (kind == AMOTION_EVENT_ACTION_MOVE) {
        for (size_t k = 0; k < AMotionEvent_getPointerCount(e); k++) {
            int32_t id = AMotionEvent_getPointerId(e, k);
            for (auto& t : g_touches)
                if (t.id == id) {
                    t.x = AMotionEvent_getX(e, k);
                    t.y = AMotionEvent_getY(e, k);
                }
        }
    }
    return 1;
}

bool headless() {  // NX_HEADLESS (platform.h)
    static const bool on = std::getenv("NX_HEADLESS") != nullptr;
    return on;
}

void read_pad(PadState& p) {
    std::lock_guard lk(g_in);
    p.buttons |= g_pad_buttons.buttons | g_axis_btn;
    if (g_pad_buttons.lx || g_pad_buttons.ly) {
        p.lx = g_pad_buttons.lx;
        p.ly = g_pad_buttons.ly;
    }
    p.rx = g_pad_buttons.rx;
    p.ry = g_pad_buttons.ry;
    float radius = std::max(1.f, g_h * 0.12f);
    size_t drawn = 0;
    touch_buttons(drawn);
    const uint64_t now = monotonic_ns();
    g_latched.erase(std::remove_if(g_latched.begin(), g_latched.end(), [&](const Latch& l) { return now >= l.until_ns; }),
                    g_latched.end());
    for (const auto& l : g_latched) p.buttons |= l.button;
    for (const auto& t : g_touches) {
        float dx = (t.x - t.x0) / radius, dy = (t.y - t.y0) / radius;
        if (t.stick == kLeftStick) {
            p.lx = int32_t(std::clamp(dx, -1.f, 1.f) * kStickMax);
            p.ly = int32_t(std::clamp(-dy, -1.f, 1.f) * kStickMax);
            // A drawn layout's stick pushed nearly all the way may hold a button too (Wonder: Y, dash), so running
            // needs no second thumb on the right side.
            if (drawn && std::abs(dx) > 0.85f) p.buttons |= touch_stick_full_button();
        } else if (t.stick == kRightStick) {
            p.rx = int32_t(std::clamp(dx, -1.f, 1.f) * kStickMax);
            p.ry = int32_t(std::clamp(-dy, -1.f, 1.f) * kStickMax);
        } else {
            p.buttons |= t.button;
        }
    }
    input_script(p);  // NX_INPUT_SCRIPT (config.txt): scripted runs on the phone, as on the Mac
}

// The drawn layout's buttons (brighter while pressed) and the stick under the thumb; nothing while a controller is
// in use or for games without a drawn layout.
size_t touch_overlay(OverlayShape* out, size_t max) {
    size_t n = 0, k = 0;
    const TouchButton* b = touch_buttons(n);
    if (!n) return 0;
    std::lock_guard lk(g_in);
    if (g_gamepad || !touch_enabled()) return 0;
    // Dimmed to a third while nobody touches the screen (cutscenes, videos), back at once on the next touch.
    const bool idle = g_touches.empty() && monotonic_ns() - g_last_touch_ns > kTouchIdleNs;
    const float shade = touch_opacity_scale() * (idle ? 0.50f : 1.0f);
    const Touch* stick = nullptr;
    const Touch* camera = nullptr;
    uint64_t held = 0;
    for (const auto& t : g_touches) {
        if (t.stick == kLeftStick) stick = &t;
        if (t.stick == kRightStick) camera = &t;
        held |= t.button;
    }
    for (const auto& l : g_latched) held |= l.button;
    for (size_t i = 0; i < n && k < max; i++)
        out[k++] = {b[i].x, b[i].y, b[i].r, std::min(1.0f, (held & b[i].button ? 0.60f : 0.28f) * shade), b[i].label};
    // A stick under the thumb: its ring where the finger went down and the knob where it is now.
    auto draw_stick = [&](const Touch& t) {
        if (k + 2 > max) return;
        float r = 0.12f, dx = (t.x - t.x0) / g_h, dy = (t.y - t.y0) / g_h;
        float len = std::hypot(dx, dy), s = len > r ? r / len : 1.f;
        out[k++] = {t.x0 / g_w, t.y0 / g_h, r, std::min(1.0f, 0.18f * shade)};
        out[k++] = {(t.x0 / g_h + dx * s) * g_h / g_w, t.y0 / g_h + dy * s, 0.055f, std::min(1.0f, 0.45f * shade)};
    };
    if (stick) {
        draw_stick(*stick);
    } else if (k < max) {  // where the stick goes
        float sx, sy, sr;
        touch_stick_rest(sx, sy, sr);
        out[k++] = {sx, sy, sr, std::min(1.0f, 0.08f * shade)};
    }
    if (camera) draw_stick(*camera);
    return k;
}

}  // namespace nx::plat
