package io.github.p3pandroid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's own controller mapping (NX_PAD_MAP in config.txt), without any Android class so it is tested on the host
 * (tests/PadMappingTest.java). The runtime reads the same text (runtime/src/pad_map.h, which documents the format):
 *   k190:A       Android key code 190 acts as the game's A button
 *   a15-:LEFT    axis 15 (the hat's X) pushed negative acts as LEFT
 *   sLX:0 sLY:1i the axis that moves a stick; "i" turns it round
 * Every action has at most one input and every input at most one action: giving an input a new action takes it from
 * the old one. Android key codes and axes are plain ints here.
 */
final class PadMapping {
    static final String KEY = "NX_PAD_MAP";

    /** The game's buttons (Nintendo names) in the order the mapping screen lists them. */
    static final String[] ACTIONS = {"A", "B", "X", "Y", "L", "R", "ZL", "ZR", "PLUS", "MINUS", "L3", "R3",
            "UP", "DOWN", "LEFT", "RIGHT"};
    static final String[] STICKS = {"LX", "LY", "RX", "RY"};

    // The Android values of the few constants the defaults need (KeyEvent / MotionEvent), so this class needs no Android.
    static final int HAT_X = 15, HAT_Y = 16, LTRIGGER = 17, RTRIGGER = 18, GAS = 22, BRAKE = 23;
    static final int MAX_AXIS = 47;  // AXIS_GENERIC_16

    /** token ("k190", "a15-") -> action, in the order they were made. */
    private final LinkedHashMap<String, String> inputs = new LinkedHashMap<String, String>();
    /** For LX, LY, RX, RY: "0", "1i" ... or null. */
    private final String[] sticks = new String[4];

    static boolean validAction(String a) {
        for (String s : ACTIONS) if (s.equals(a)) return true;
        return false;
    }

    static String keyToken(int code) { return "k" + code; }

    static String axisToken(int axis, int sign) { return "a" + axis + (sign > 0 ? "+" : "-"); }

    private static boolean digits(String s) {
        if (s.isEmpty() || s.length() > 6) return false;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        return true;
    }

    /** Reads the config text; anything malformed is skipped, as the runtime does. Null or empty gives an empty mapping. */
    static PadMapping parse(String text) {
        PadMapping m = new PadMapping();
        if (text == null) return m;
        for (String item : text.split(";", -1)) {
            int colon = item.indexOf(':');
            if (item.length() < 3 || colon < 2) continue;
            char kind = item.charAt(0);
            String head = item.substring(1, colon), value = item.substring(colon + 1);
            if (kind == 'k') {
                if (digits(head) && validAction(value)) m.bind("k" + Integer.parseInt(head), value);
            } else if (kind == 'a') {
                char sign = head.charAt(head.length() - 1);
                String num = head.substring(0, head.length() - 1);
                if ((sign == '+' || sign == '-') && digits(num) && validAction(value))
                    m.bind("a" + Integer.parseInt(num) + sign, value);
            } else if (kind == 's') {
                int which = indexOfStick(head);
                String num = value.endsWith("i") ? value.substring(0, value.length() - 1) : value;
                if (which >= 0 && digits(num)) m.sticks[which] = Integer.parseInt(num) + (value.endsWith("i") ? "i" : "");
            }
        }
        return m;
    }

    private static int indexOfStick(String name) {
        for (int i = 0; i < STICKS.length; i++) if (STICKS[i].equals(name)) return i;
        return -1;
    }

    /** The config text; empty when nothing is mapped. */
    String format() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : inputs.entrySet()) {
            if (out.length() > 0) out.append(';');
            out.append(e.getKey()).append(':').append(e.getValue());
        }
        for (int i = 0; i < sticks.length; i++) {
            if (sticks[i] == null) continue;
            if (out.length() > 0) out.append(';');
            out.append('s').append(STICKS[i]).append(':').append(sticks[i]);
        }
        return out.toString();
    }

    boolean isEmpty() {
        if (!inputs.isEmpty()) return false;
        for (String s : sticks) if (s != null) return false;
        return true;
    }

    /** How many buttons (keys and axes) are mapped. */
    int buttonCount() { return inputs.size(); }

    /** The action an input (token) is mapped to, or null. */
    String actionOf(String token) { return inputs.get(token); }

    /** The input (token) mapped to an action, or null. */
    String inputOf(String action) {
        for (Map.Entry<String, String> e : inputs.entrySet()) if (e.getValue().equals(action)) return e.getKey();
        return null;
    }

    /** Maps the input to the action; the action's old input and the input's old action are let go. */
    void bind(String token, String action) {
        clear(action);
        inputs.remove(token);
        inputs.put(token, action);
    }

    /** Lets go of the action's input (back to the built-in one). */
    void clear(String action) {
        List<String> gone = new ArrayList<String>();
        for (Map.Entry<String, String> e : inputs.entrySet()) if (e.getValue().equals(action)) gone.add(e.getKey());
        for (String g : gone) inputs.remove(g);
    }

    void bindStick(int which, int axis, boolean invert) { sticks[which] = axis + (invert ? "i" : ""); }

    void clearStick(int which) { sticks[which] = null; }

    boolean hasStick(int which) { return sticks[which] != null; }

    int stickAxis(int which) {
        String s = sticks[which];
        return s == null ? -1 : Integer.parseInt(s.endsWith("i") ? s.substring(0, s.length() - 1) : s);
    }

    boolean stickInverted(int which) { return sticks[which] != null && sticks[which].endsWith("i"); }

    void clearAll() {
        inputs.clear();
        for (int i = 0; i < sticks.length; i++) sticks[i] = null;
    }

    // ---- what the runtime does without a mapping (platform_android_input.cpp: key_to_button), for the live test ----

    /**
     * The action an Android key code has with no custom mapping, or null. `style` is the launcher's button layout style
     * ("switch", "xbox", "ps2"): the face buttons follow it by position. Keep in step with key_to_button().
     */
    static String defaultAction(int code, String style) {
        boolean south = "xbox".equals(style) || "ps2".equals(style);
        switch (code) {
            case 96: return south ? "A" : "B";   // BUTTON_A, the bottom button
            case 97: return south ? "B" : "A";   // BUTTON_B, the right one
            case 99: return south ? "X" : "Y";   // BUTTON_X, the left one
            case 100: return south ? "Y" : "X";  // BUTTON_Y, the top one
            case 102: return "L";
            case 103: return "R";
            case 104: return "ZL";
            case 105: return "ZR";
            case 108: return "PLUS";
            case 109: return "MINUS";
            case 106: return "L3";
            case 107: return "R3";
            case 21: return "LEFT";
            case 19: return "UP";
            case 22: return "RIGHT";
            case 20: return "DOWN";
            case 66: case 160: case 62: case 23: return "A";  // Enter, keypad Enter, Space, D-pad centre
            case 111: return "B";                             // Escape
            // BUTTON_1..12: generic / PlayStation 2 USB adapters ("PC TWIN SHOCK" order).
            case 188: return south ? "Y" : "X";  // Triangle
            case 189: return south ? "B" : "A";  // Circle
            case 190: return south ? "A" : "B";  // Cross
            case 191: return south ? "X" : "Y";  // Square
            case 192: return "ZL";
            case 193: return "ZR";
            case 194: return "L";
            case 195: return "R";
            case 196: return "MINUS";
            case 197: return "PLUS";
            case 198: return "L3";
            case 199: return "R3";
            default: return null;
        }
    }

    /** The action an axis has with no custom mapping when pushed in `sign`'s direction past the threshold, or null. */
    static String defaultAxisAction(int axis, int sign) {
        if (axis == HAT_X) return sign < 0 ? "LEFT" : "RIGHT";
        if (axis == HAT_Y) return sign < 0 ? "UP" : "DOWN";
        if (sign > 0 && (axis == LTRIGGER || axis == BRAKE)) return "ZL";
        if (sign > 0 && (axis == RTRIGGER || axis == GAS)) return "ZR";
        return null;
    }
}
