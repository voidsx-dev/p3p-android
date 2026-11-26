package io.github.p3pandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Controller mapping (Controls → Controller mapping in the launcher). For pads Android has no standard layout for (a PS2
 * pad on a USB adapter, odd Bluetooth pads) and for anyone who wants other buttons: the player taps Set (or Guided setup),
 * presses the button they want on their controller, and it is kept as NX_PAD_MAP in config.txt, which the runtime reads
 * (runtime/src/pad_map.h; the format is PadMapping.java). What is not mapped keeps working as before. A live test line shows
 * what the controller sends and what the game does with it, and the controllers Android sees are listed with their ids
 * (handy in a bug report).
 */
public final class ControllerMapActivity extends Activity implements InputManager.InputDeviceListener {
    /** One thing to capture: a button of the game, or one direction of a stick. */
    static final class Step {
        static final int BUTTON = 0, STICK = 1;
        final int kind;
        final String action;  // BUTTON
        final int stick;      // STICK: index into PadMapping.STICKS (LX, LY, RX, RY)

        Step(int kind, String action, int stick) {
            this.kind = kind;
            this.action = action;
            this.stick = stick;
        }
    }

    static final long SETTLE_MS = 450;  // inputs right after a capture are ignored: a d-pad press arrives as a key and an axis
    static final float PUSH = 0.6f;     // how far an axis moves from rest to count as pushed

    PadMapping map = new PadMapping();
    boolean mapOn = true;  // NX_PAD_MAP_ON: off keeps the mapping saved but unused (another controller)
    TextView onPill, offPill;
    String style = "switch";  // the launcher's button layout style: what the live test says an unmapped key does

    // the screen
    FrameLayout frame;
    TextView devicesText, liveText, hintText;
    final Map<String, LinearLayout> rows = new HashMap<String, LinearLayout>();
    final Map<String, TextView> bindings = new HashMap<String, TextView>();
    final Map<String, Button> clears = new HashMap<String, Button>();
    final TextView[] stickBindings = new TextView[2];
    final Button[] stickClears = new Button[2];
    LinearLayout overlay;
    TextView overlayStep, overlayTitle, overlayHint;
    Button skipButton;

    // what the controller does now
    final float[] rest = new float[PadMapping.MAX_AXIS + 1];
    final boolean[] restKnown = new boolean[PadMapping.MAX_AXIS + 1];
    final int[] axisDir = new int[PadMapping.MAX_AXIS + 1];
    final Set<Integer> keysDown = new HashSet<Integer>();

    // capture
    boolean capturing;
    List<Step> steps = new ArrayList<Step>();
    int stepIndex;
    long armedAt;
    final Set<Integer> usedAxes = new HashSet<Integer>();

    String t(String spanish, String english) { return Lang.t(spanish, english); }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    File configFile() { return new File(Launcher.dataDir(), "config.txt"); }

    // ---- life cycle ----

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        applyImmersiveMode();
        Map<String, String> config = Launcher.readConfig(configFile());
        map = PadMapping.parse(config.get(PadMapping.KEY));
        mapOn = !"0".equals(config.get("NX_PAD_MAP_ON"));
        String s = config.get("NX_PAD_STYLE");
        if (s == null) s = config.get("NX_TOUCH_STYLE");
        if (s != null) style = s;
        build();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveMode();
        InputManager im = (InputManager) getSystemService(Context.INPUT_SERVICE);
        if (im != null) im.registerInputDeviceListener(this, null);
        refreshDevices();
    }

    @Override
    protected void onPause() {
        super.onPause();
        InputManager im = (InputManager) getSystemService(Context.INPUT_SERVICE);
        if (im != null) im.unregisterInputDeviceListener(this);
        if (capturing) endCapture(false);
        keysDown.clear();
        for (int i = 0; i < axisDir.length; i++) axisDir[i] = 0;
    }

    @Override public void onInputDeviceAdded(int id) { refreshDevices(); }

    @Override public void onInputDeviceRemoved(int id) { refreshDevices(); }

    @Override public void onInputDeviceChanged(int id) { refreshDevices(); }

    private void applyImmersiveMode() {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    // ---- names ----

    /** What the player sees for an action: the game's own button name, with what it does where that is known. */
    String actionLabel(String a) {
        if ("A".equals(a)) return "A · " + t("Confirmar", "Confirm");
        if ("B".equals(a)) return "B · " + t("Volver", "Back");
        if ("PLUS".equals(a)) return "+ (Start)";
        if ("MINUS".equals(a)) return "\u2212 (Select)";
        if ("L3".equals(a)) return t("Clic palanca izquierda", "Left stick click");
        if ("R3".equals(a)) return t("Clic palanca derecha", "Right stick click");
        if ("UP".equals(a)) return t("Cruceta arriba", "D-pad up");
        if ("DOWN".equals(a)) return t("Cruceta abajo", "D-pad down");
        if ("LEFT".equals(a)) return t("Cruceta izquierda", "D-pad left");
        if ("RIGHT".equals(a)) return t("Cruceta derecha", "D-pad right");
        return a;  // X, Y, L, R, ZL, ZR
    }

    /** Android's name of a key code or axis, as the player will see it on the pad's sheet ("BUTTON_3", "HAT_X (-)"). */
    String inputLabel(String token) {
        try {
            if (token.startsWith("k")) {
                String n = KeyEvent.keyCodeToString(Integer.parseInt(token.substring(1)));
                return n.startsWith("KEYCODE_") ? n.substring(8) : n;
            }
            if (token.startsWith("a")) {
                char sign = token.charAt(token.length() - 1);
                String n = MotionEvent.axisToString(Integer.parseInt(token.substring(1, token.length() - 1)));
                return (n.startsWith("AXIS_") ? n.substring(5) : n) + " (" + sign + ")";
            }
        } catch (NumberFormatException ignored) {
        }
        return token;
    }

    String axisName(int axis) {
        String n = MotionEvent.axisToString(axis);
        return n.startsWith("AXIS_") ? n.substring(5) : n;
    }

    /** The game controllers Android sees (name and vendor:product ids), without the phone's own virtual devices. */
    static List<String> padNames(boolean withIds) {
        List<String> out = new ArrayList<String>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || d.isVirtual()) continue;
            int src = d.getSources();
            if ((src & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD
                    && (src & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) continue;
            String name = d.getName();
            out.add(withIds ? String.format(Locale.ROOT, "%s (%04x:%04x)", name, d.getVendorId(), d.getProductId()) : name);
        }
        return out;
    }

    // ---- the screen ----

    void build() {
        frame = new FrameLayout(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackground(Ui.background());
        page.setPadding(dp(22), dp(22), dp(22), dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = new TextView(this);
        eyebrow.setText("P3P ANDROID  \u2022  " + t("Mando", "Controller").toUpperCase(Locale.getDefault()));
        eyebrow.setTextColor(Ui.ACCENT);
        eyebrow.setTextSize(10);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        heading.addView(eyebrow);
        TextView title = new TextView(this);
        title.setText(t("Mapeo del mando", "Controller mapping"));
        title.setTextColor(Ui.TEXT);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        heading.addView(title);
        top.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(Ui.action(this, t("Volver", "Back"), new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        }));
        page.addView(top);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(16));

        // Controllers and the live test
        LinearLayout pads = Ui.card(this, content);
        pads.addView(Ui.title(this, t("Mandos conectados", "Connected controllers")));
        devicesText = new TextView(this);
        devicesText.setTextColor(Ui.TEXT);
        devicesText.setTextSize(14);
        devicesText.setLineSpacing(0, 1.1f);
        pads.addView(devicesText);

        LinearLayout live = Ui.card(this, content);
        live.addView(Ui.title(this, t("Prueba en vivo", "Live test")));
        liveText = new TextView(this);
        liveText.setTextColor(Ui.TEXT);
        liveText.setTextSize(17);
        liveText.setTypeface(Typeface.DEFAULT_BOLD);
        liveText.setPadding(0, 0, 0, dp(6));
        liveText.setText("\u2014");
        live.addView(liveText);
        hintText = Ui.hint(this, t("Pulsa un botón o mueve una palanca del mando para ver qué hace.",
                "Press a button or move a stick on your controller to see what it does."));
        live.addView(hintText);

        // Use my mapping: off keeps it saved but unused, to play with another controller
        LinearLayout use = Ui.card(this, content);
        LinearLayout useRow = new LinearLayout(this);
        useRow.setGravity(Gravity.CENTER_VERTICAL);
        useRow.addView(Ui.label(this, t("Usar mi mapeo", "Use my mapping")),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        onPill = Ui.pill(this, t("Activado", "On"));
        offPill = Ui.pill(this, t("Desactivado", "Off"));
        onPill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setMapOn(true); }
        });
        offPill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setMapOn(false); }
        });
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT);
        pillLp.leftMargin = dp(6);
        useRow.addView(onPill, pillLp);
        useRow.addView(offPill, pillLp);
        use.addView(useRow);
        use.addView(Ui.hint(this, t("Desactívalo para jugar con otro mando sin perder tu mapeo.",
                "Turn it off to play with another controller without losing your mapping.")));
        Ui.setSelected(this, onPill, mapOn);
        Ui.setSelected(this, offPill, !mapOn);

        // Buttons
        LinearLayout buttons = Ui.card(this, content);
        buttons.addView(Ui.title(this, t("Botones", "Buttons")));
        buttons.addView(Ui.hint(this, t("Toca Asignar y pulsa el botón que quieras en el mando. Lo que no toques sigue "
                        + "funcionando como antes. Los cambios se guardan solos.",
                "Tap Set, then press the button you want on your controller. Whatever you leave alone keeps working "
                        + "as before. Changes are saved automatically.")));
        Button guided = Ui.primary(this);
        guided.setText(t("Configuración guiada", "Guided setup"));
        guided.setTextSize(15);
        guided.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startGuided(); }
        });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        gp.bottomMargin = dp(8);
        buttons.addView(guided, gp);
        for (final String action : PadMapping.ACTIONS) buttons.addView(actionRow(action));

        // Sticks
        LinearLayout sticks = Ui.card(this, content);
        sticks.addView(Ui.title(this, t("Palancas", "Sticks")));
        sticks.addView(Ui.hint(this, t("Toca Asignar y sigue los pasos. Solo hace falta si una palanca no responde o se mueve al revés.",
                "Tap Set and follow the steps. Only needed if a stick does not respond or moves the wrong way.")));
        sticks.addView(stickRow(0, t("Palanca izquierda", "Left stick")));
        sticks.addView(stickRow(1, t("Palanca derecha", "Right stick")));

        // Reset
        LinearLayout reset = Ui.card(this, content);
        LinearLayout resetRow = new LinearLayout(this);
        resetRow.setGravity(Gravity.CENTER_VERTICAL);
        resetRow.addView(Ui.label(this, t("Restaurar valores", "Restore defaults")),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        resetRow.addView(Ui.action(this, t("Restaurar", "Reset"), new View.OnClickListener() {
            @Override public void onClick(View v) { askRestore(); }
        }));
        reset.addView(resetRow);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        frame.addView(page, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        frame.addView(buildOverlay(), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(frame);
    }

    /** A button's row: its name, what it is mapped to, Set and a small cross to go back to the built-in one. */
    LinearLayout actionRow(final String action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(3), dp(4), dp(3));
        row.addView(Ui.label(this, actionLabel(action)), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView bound = new TextView(this);
        bound.setTextSize(13.5f);
        bound.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.addView(bound, new LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT));
        Button set = Ui.action(this, t("Asignar", "Set"), new View.OnClickListener() {
            @Override public void onClick(View v) {
                List<Step> one = new ArrayList<Step>();
                one.add(new Step(Step.BUTTON, action, 0));
                startCapture(one);
            }
        });
        set.setMinWidth(dp(84));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = dp(10);
        row.addView(set, sp);
        Button clear = Ui.action(this, "\u2715", new View.OnClickListener() {
            @Override public void onClick(View v) {
                map.clear(action);
                save();
                refreshAll();
            }
        });
        clear.setMinWidth(dp(44));
        clear.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = dp(6);
        row.addView(clear, cp);
        rows.put(action, row);
        bindings.put(action, bound);
        clears.put(action, clear);
        return row;
    }

    /** A stick's row: `side` 0 is the left stick (LX, LY), 1 the right one (RX, RY). */
    LinearLayout stickRow(final int side, String name) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(3), dp(4), dp(3));
        row.addView(Ui.label(this, name), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView bound = new TextView(this);
        bound.setTextSize(13.5f);
        bound.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.addView(bound, new LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT));
        Button set = Ui.action(this, t("Asignar", "Set"), new View.OnClickListener() {
            @Override public void onClick(View v) {
                List<Step> two = new ArrayList<Step>();
                two.add(new Step(Step.STICK, null, side * 2));
                two.add(new Step(Step.STICK, null, side * 2 + 1));
                startCapture(two);
            }
        });
        set.setMinWidth(dp(84));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = dp(10);
        row.addView(set, sp);
        Button clear = Ui.action(this, "\u2715", new View.OnClickListener() {
            @Override public void onClick(View v) {
                map.clearStick(side * 2);
                map.clearStick(side * 2 + 1);
                save();
                refreshAll();
            }
        });
        clear.setMinWidth(dp(44));
        clear.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = dp(6);
        row.addView(clear, cp);
        stickBindings[side] = bound;
        stickClears[side] = clear;
        return row;
    }

    /** The dark panel over the screen while the player is asked to press something. */
    View buildOverlay() {
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(Color.argb(238, 3, 6, 16));
        overlay.setClickable(true);  // takes the touches: nothing behind it is pressed by accident
        overlay.setVisibility(View.GONE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setBackground(Ui.shape(this, Ui.CARD, 20, Ui.STROKE));
        panel.setPadding(dp(24), dp(22), dp(24), dp(18));

        overlayStep = Ui.title(this, "");
        overlayStep.setGravity(Gravity.CENTER);
        panel.addView(overlayStep);
        overlayTitle = new TextView(this);
        overlayTitle.setTextColor(Color.WHITE);
        overlayTitle.setTextSize(23);
        overlayTitle.setTypeface(Typeface.DEFAULT_BOLD);
        overlayTitle.setGravity(Gravity.CENTER);
        overlayTitle.setPadding(0, dp(4), 0, dp(8));
        panel.addView(overlayTitle);
        overlayHint = Ui.hint(this, "");
        overlayHint.setGravity(Gravity.CENTER);
        panel.addView(overlayHint);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        skipButton = Ui.action(this, t("Omitir", "Skip"), new View.OnClickListener() {
            @Override public void onClick(View v) { advance(); }
        });
        bar.addView(skipButton);
        Button cancel = Ui.action(this, t("Cancelar", "Cancel"), new View.OnClickListener() {
            @Override public void onClick(View v) { endCapture(false); }
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = dp(10);
        bar.addView(cancel, cp);
        panel.addView(bar);

        overlay.addView(panel, new LinearLayout.LayoutParams(Math.min(dp(520),
                getResources().getDisplayMetrics().widthPixels - dp(48)), LinearLayout.LayoutParams.WRAP_CONTENT));
        return overlay;
    }

    // ---- showing the mapping ----

    void refreshAll() {
        for (String action : PadMapping.ACTIONS) {
            TextView bound = bindings.get(action);
            String token = map.inputOf(action);
            if (token != null) {
                bound.setText(inputLabel(token));
                bound.setTextColor(Ui.ACCENT);
                bound.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                bound.setText(t("Predeterminado", "Default"));
                bound.setTextColor(Ui.MUTED);
                bound.setTypeface(Typeface.DEFAULT);
            }
            clears.get(action).setVisibility(token != null ? View.VISIBLE : View.INVISIBLE);
        }
        for (int side = 0; side < 2; side++) {
            TextView bound = stickBindings[side];
            boolean any = map.hasStick(side * 2) || map.hasStick(side * 2 + 1);
            if (any) {
                bound.setText(stickText(side * 2) + ", " + stickText(side * 2 + 1));
                bound.setTextColor(Ui.ACCENT);
                bound.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                bound.setText(t("Predeterminado", "Default"));
                bound.setTextColor(Ui.MUTED);
                bound.setTypeface(Typeface.DEFAULT);
            }
            stickClears[side].setVisibility(any ? View.VISIBLE : View.INVISIBLE);
        }
        updateHeld();
    }

    String stickText(int which) {
        if (!map.hasStick(which)) return t("Predeterminado", "Default");
        return axisName(map.stickAxis(which)) + (map.stickInverted(which) ? "\u21C5" : "");
    }

    void refreshDevices() {
        List<String> names = padNames(true);
        if (names.isEmpty()) {
            devicesText.setText(t("Ningún mando detectado. Conéctalo (USB / adaptador OTG o Bluetooth) y pulsa cualquier botón.",
                    "No controller detected. Plug it in (USB / OTG adapter or Bluetooth), then press any button."));
            devicesText.setTextColor(Ui.MUTED);
        } else {
            StringBuilder b = new StringBuilder();
            for (String n : names) {
                if (b.length() > 0) b.append('\n');
                b.append("\uD83C\uDFAE  ").append(n);
            }
            devicesText.setText(b.toString());
            devicesText.setTextColor(Ui.TEXT);
        }
    }

    /** Lights up the rows of the buttons held down now, so the player sees which row a press lands on. */
    void updateHeld() {
        Set<String> held = new HashSet<String>();
        for (int code : keysDown) {
            String a = customAction(PadMapping.keyToken(code));
            if (a == null) a = PadMapping.defaultAction(code, style);
            if (a != null) held.add(a);
        }
        for (int axis = 0; axis < axisDir.length; axis++) {
            if (axisDir[axis] == 0) continue;
            String a = customAction(PadMapping.axisToken(axis, axisDir[axis]));
            if (a == null) a = PadMapping.defaultAxisAction(axis, axisDir[axis]);
            if (a != null) held.add(a);
        }
        for (Map.Entry<String, LinearLayout> e : rows.entrySet())
            e.getValue().setBackground(held.contains(e.getKey()) ? Ui.shape(this, Ui.ACCENT_DARK, 12, 0) : null);
    }

    /** What an input does now: its mapped (or built-in) action, or nothing. */
    void showLive(String token, String builtIn) {
        String custom = customAction(token);
        String action = custom != null ? custom : builtIn;
        String result;
        if (action == null) result = t("No lo usa el juego", "Not used by the game");
        else result = actionLabel(action) + "  \u00B7  " + (custom != null ? t("Personalizado", "Custom")
                : t("Predeterminado", "Default"));
        liveText.setText(inputLabel(token) + "   \u2192   " + result);
    }

    // ---- capturing ----

    void startGuided() {
        List<Step> all = new ArrayList<Step>();
        for (String action : PadMapping.ACTIONS) all.add(new Step(Step.BUTTON, action, 0));
        for (int i = 0; i < 4; i++) all.add(new Step(Step.STICK, null, i));
        startCapture(all);
    }

    void startCapture(List<Step> list) {
        steps = list;
        stepIndex = 0;
        usedAxes.clear();
        capturing = true;
        armedAt = SystemClock.uptimeMillis() + SETTLE_MS;
        overlay.setVisibility(View.VISIBLE);
        showStep();
    }

    void showStep() {
        Step s = steps.get(stepIndex);
        overlayStep.setText(steps.size() > 1
                ? Lang.f("Paso %d de %d", "Step %d of %d", stepIndex + 1, steps.size()).toUpperCase(Locale.getDefault()) : "");
        if (s.kind == Step.BUTTON) {
            overlayTitle.setText(Lang.f("Pulsa el botón para %s", "Press the button for %s", actionLabel(s.action)));
            overlayHint.setText(t("Púlsalo en tu mando, u Omitir para dejar el actual.",
                    "Press it on your controller, or Skip to keep the current one."));
        } else {
            overlayTitle.setText(stickPrompt(s.stick));
            overlayHint.setText(t("Llévala hasta el final y suéltala.", "Push it all the way, then let go."));
        }
        skipButton.setVisibility(steps.size() > 1 ? View.VISIBLE : View.GONE);
    }

    String stickPrompt(int which) {
        if (which == 0) return t("Mueve la palanca izquierda a la derecha", "Push the left stick to the right");
        if (which == 1) return t("Mueve la palanca izquierda hacia arriba", "Push the left stick up");
        if (which == 2) return t("Mueve la palanca derecha a la derecha", "Push the right stick to the right");
        return t("Mueve la palanca derecha hacia arriba", "Push the right stick up");
    }

    /** Next step, or done after the last one. */
    void advance() {
        armedAt = SystemClock.uptimeMillis() + SETTLE_MS;
        stepIndex++;
        if (stepIndex >= steps.size()) endCapture(true);
        else showStep();
    }

    void endCapture(boolean done) {
        capturing = false;
        overlay.setVisibility(View.GONE);
        armedAt = SystemClock.uptimeMillis() + SETTLE_MS;
        refreshAll();
        if (done) Toast.makeText(this, t("Controles guardados", "Controls saved"), Toast.LENGTH_SHORT).show();
    }

    void bindInput(String token) {
        Step s = steps.get(stepIndex);
        map.bind(token, s.action);
        save();
        refreshAll();
        advance();
    }

    /** A stick axis was pushed in `sign`'s direction: right (X) and up (Y) are what the step asked for. */
    void bindStickAxis(int axis, int sign) {
        Step s = steps.get(stepIndex);
        boolean up = s.stick % 2 == 1;
        int expected = up ? -1 : 1;  // Android: right is positive, up is negative
        map.bindStick(s.stick, axis, sign != expected);
        usedAxes.add(axis);
        save();
        refreshAll();
        advance();
    }

    void setMapOn(boolean on) {
        mapOn = on;
        Ui.setSelected(this, onPill, on);
        Ui.setSelected(this, offPill, !on);
        save();
        updateHeld();
    }

    /** What the player's mapping says for an input, or null (also null while the mapping is switched off). */
    String customAction(String token) { return mapOn ? map.actionOf(token) : null; }

    void save() {
        File dir = Launcher.dataDir();
        if (!dir.isDirectory()) dir.mkdirs();
        File f = configFile();
        Map<String, String> config = Launcher.readConfig(f);
        String text = map.format();
        if (text.isEmpty()) config.remove(PadMapping.KEY);
        else config.put(PadMapping.KEY, text);
        if (mapOn) config.remove("NX_PAD_MAP_ON");
        else config.put("NX_PAD_MAP_ON", "0");
        if (!Launcher.writeConfig(f, config))
            Toast.makeText(this, t("No se pudo escribir config.txt", "Could not write config.txt"), Toast.LENGTH_LONG).show();
    }

    void askRestore() {
        new AlertDialog.Builder(this)
                .setTitle(t("Restaurar valores", "Restore defaults"))
                .setMessage(t("Se quitan todos tus botones y palancas personalizados.",
                        "This removes all your custom buttons and sticks."))
                .setPositiveButton(t("Restaurar", "Reset"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        map.clearAll();
                        save();
                        refreshAll();
                    }
                })
                .setNegativeButton(t("Cancelar", "Cancel"), null)
                .show();
    }

    // ---- the controller's own events (all of them come here: nothing a pad sends presses a button of the screen) ----

    static boolean fromPad(int source) {
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {  // the phone's Back: leaves the capture, then the screen
            if (ev.getAction() == KeyEvent.ACTION_UP) {
                if (capturing) endCapture(false);
                else finish();
            }
            return true;
        }
        if (!fromPad(ev.getSource()) && !KeyEvent.isGamepadButton(code)) return super.dispatchKeyEvent(ev);
        if (ev.getAction() == KeyEvent.ACTION_DOWN) {
            if (ev.getRepeatCount() == 0) onPadKey(code, true);
        } else if (ev.getAction() == KeyEvent.ACTION_UP) {
            onPadKey(code, false);
        }
        return true;
    }

    void onPadKey(int code, boolean down) {
        if (down) keysDown.add(code);
        else keysDown.remove(code);
        if (down && capturing) {
            Step s = steps.get(stepIndex);
            if (s.kind == Step.BUTTON && SystemClock.uptimeMillis() >= armedAt) bindInput(PadMapping.keyToken(code));
            return;
        }
        if (down) showLive(PadMapping.keyToken(code), PadMapping.defaultAction(code, style));
        updateHeld();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if ((ev.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && ev.getAction() == MotionEvent.ACTION_MOVE) {
            onPadMotion(ev);
            return true;
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    /**
     * An axis counts as pushed when it moves more than PUSH from where it rests (0, or -1 for a trigger that rests at -1);
     * only the moment it is pushed counts, so letting go, or an axis that is still held while another moves, is not a press.
     */
    void onPadMotion(MotionEvent ev) {
        int newAxis = -1, newDir = 0;
        float best = 0;
        for (int a = 0; a < axisDir.length; a++) {
            float v = ev.getAxisValue(a);
            if (!restKnown[a]) {
                restKnown[a] = true;
                boolean hat = a == PadMapping.HAT_X || a == PadMapping.HAT_Y;
                rest[a] = !hat && v <= -0.95f ? -1f : 0f;
            }
            float d = v - rest[a];
            int dir = d > PUSH ? 1 : d < -PUSH ? -1 : 0;
            if (dir != 0 && axisDir[a] == 0 && Math.abs(d) > best) {
                best = Math.abs(d);
                newAxis = a;
                newDir = dir;
            }
            axisDir[a] = dir;
        }
        if (newAxis >= 0) {
            if (capturing) {
                if (SystemClock.uptimeMillis() >= armedAt) onCaptureAxis(newAxis, newDir);
            } else {
                showLive(PadMapping.axisToken(newAxis, newDir), PadMapping.defaultAxisAction(newAxis, newDir));
            }
        }
        if (!capturing) updateHeld();
    }

    void onCaptureAxis(int axis, int dir) {
        Step s = steps.get(stepIndex);
        if (s.kind == Step.BUTTON) {
            bindInput(PadMapping.axisToken(axis, dir));
        } else if (axis != PadMapping.HAT_X && axis != PadMapping.HAT_Y && !usedAxes.contains(axis)) {
            bindStickAxis(axis, dir);
        }
    }
}
