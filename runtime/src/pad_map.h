// The player's own controller mapping (launcher: Controls → Controller mapping, written to config.txt as NX_PAD_MAP).
// For pads Android has no standard layout for (PS2 / generic USB adapters, odd Bluetooth pads) and for anyone who wants
// other buttons. Kept apart from the Android code (plain ints for key codes and axes) so it is tested on the host
// (tests/pad_map_test.cpp). The same format is read and written by games/p3p/java/.../PadMapping.java.
//
// NX_PAD_MAP is a list of items separated by ';':
//   k<keycode>:<ACTION>      Android key code pressed -> action         k190:A
//   a<axis><+|->:<ACTION>    axis pushed past half, in that direction   a15-:LEFT   (AXIS_HAT_X is 15, HAT_Y 16)
//   s<LX|LY|RX|RY>:<axis>[i] the axis that moves a stick; "i" turns it round    sRX:11  sLY:1i
// ACTION: A B X Y L R ZL ZR PLUS MINUS L3 R3 UP DOWN LEFT RIGHT (the game's own Nintendo-named buttons).
// A stick's axis value is taken as it comes for X (right is positive) and turned round for Y (Android's Y is down-positive,
// the game's up-positive), as the runtime always did; "i" reverses that. Anything malformed is skipped.
#pragma once

#include <algorithm>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "platform.h"

namespace nx::plat {

struct PadMap {
    struct Key {
        int32_t code;
        uint64_t button;
    };
    struct AxisButton {
        int32_t axis;
        int sign;  // +1 or -1: the direction that presses it
        uint64_t button;
    };
    struct StickAxis {
        bool set = false;
        int32_t axis = 0;
        bool invert = false;
    };
    enum Stick { kLX, kLY, kRX, kRY };

    std::vector<Key> keys;
    std::vector<AxisButton> axes;
    StickAxis stick[4];

    bool empty() const {
        return keys.empty() && axes.empty() && !stick[0].set && !stick[1].set && !stick[2].set && !stick[3].set;
    }
    // The button a key code is mapped to, or 0.
    uint64_t key(int32_t code) const {
        for (const auto& k : keys)
            if (k.code == code) return k.button;
        return 0;
    }
};

// ACTION name -> NpadButton bit, 0 when unknown.
inline uint64_t pad_action_bit(const std::string& name) {
    static const struct {
        const char* name;
        uint64_t bit;
    } kNames[] = {{"A", kA},       {"B", kB},         {"X", kX},       {"Y", kY},        {"L", kL},
                  {"R", kR},       {"ZL", kZL},       {"ZR", kZR},     {"PLUS", kPlus},  {"MINUS", kMinus},
                  {"L3", kStickL}, {"R3", kStickR},   {"UP", kUp},     {"DOWN", kDown},  {"LEFT", kLeft},
                  {"RIGHT", kRight}};
    for (const auto& n : kNames)
        if (name == n.name) return n.bit;
    return 0;
}

inline bool pad_parse_int(const std::string& s, int32_t& out) {
    if (s.empty() || s.size() > 6) return false;
    for (char c : s)
        if (c < '0' || c > '9') return false;
    out = int32_t(std::atoi(s.c_str()));
    return true;
}

inline PadMap parse_pad_map(const char* text) {
    PadMap m;
    if (!text) return m;
    const std::string all = text;
    size_t pos = 0;
    while (pos <= all.size()) {
        size_t end = all.find(';', pos);
        if (end == std::string::npos) end = all.size();
        std::string item = all.substr(pos, end - pos);
        pos = end + 1;
        const size_t colon = item.find(':');
        if (item.size() < 3 || colon == std::string::npos || colon < 2) continue;
        const char kind = item[0];
        const std::string head = item.substr(1, colon - 1), value = item.substr(colon + 1);
        if (kind == 'k') {
            int32_t code;
            const uint64_t bit = pad_action_bit(value);
            if (!bit || !pad_parse_int(head, code)) continue;
            m.keys.erase(std::remove_if(m.keys.begin(), m.keys.end(), [&](const PadMap::Key& k) { return k.code == code; }),
                         m.keys.end());
            m.keys.push_back({code, bit});
        } else if (kind == 'a') {
            const char sign = head.back();
            int32_t axis;
            const uint64_t bit = pad_action_bit(value);
            if ((sign != '+' && sign != '-') || !bit || !pad_parse_int(head.substr(0, head.size() - 1), axis)) continue;
            m.axes.push_back({axis, sign == '+' ? 1 : -1, bit});
        } else if (kind == 's') {
            int which = head == "LX" ? PadMap::kLX : head == "LY" ? PadMap::kLY : head == "RX" ? PadMap::kRX
                      : head == "RY" ? PadMap::kRY : -1;
            if (which < 0) continue;
            std::string num = value;
            bool invert = false;
            if (!num.empty() && num.back() == 'i') {
                invert = true;
                num.pop_back();
            }
            int32_t axis;
            if (!pad_parse_int(num, axis)) continue;
            m.stick[which] = {true, axis, invert};
        }
    }
    return m;
}

// A stick's value from the raw axis reading (-1..1): `down_positive` is true for the Y sticks (Android reports down as
// positive; the game wants up positive), flipped again by the player's "i". Inside the dead zone it is 0.
inline float pad_stick_value(float raw, bool down_positive, bool invert, float dead_zone = 0.15f) {
    if (raw > -dead_zone && raw < dead_zone) return 0.f;
    float v = raw > 1.f ? 1.f : raw < -1.f ? -1.f : raw;
    if (down_positive) v = -v;
    return invert ? -v : v;
}

}  // namespace nx::plat
