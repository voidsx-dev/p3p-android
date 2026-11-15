package io.github.p3pandroid;

import android.app.Dialog;
import android.app.NativeActivity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.content.Intent;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * The game (libp3p.so), in its own process (":game", AndroidManifest.xml). Two jobs:
 *  - The runtime ends its process when this activity is destroyed (platform_android.cpp: _exit(0)). In the launcher's
 *    own process that took the launcher with it, so the game has a process of its own: leaving it lands on the
 *    launcher, which keeps running.
 *  - Back opens the in-game Quick Menu. The explicit "Exit game" action asks for confirmation before returning to the Launcher.
 * The launcher cannot hand over the game file as an open descriptor to another process (an Intent may not carry one),
 * nor as environment variables, so it passes the file's Uri and name and this activity opens the file itself, in the
 * same way (the launcher holds a persistent read grant) and sets the NX_* variables the runtime reads (game_image.cpp).
 * Started without extras (the data folder made on a PC, or adb), it sets nothing.
 */
public class GameActivity extends NativeActivity {
    static final String EXTRA_URI = "game_uri", EXTRA_NAME = "game_name";

    private Dialog quickMenu;
    private java.util.Map<String, String> liveConfig;

    @Override
    protected void onCreate(Bundle state) {
        // Before super.onCreate: that is what starts the native side, which reads these at start.
        String uri = getIntent().getStringExtra(EXTRA_URI);
        if (uri != null) {
            try {
                ParcelFileDescriptor f = getContentResolver().openFileDescriptor(Uri.parse(uri), "r");
                if (f != null) {
                    Os.setenv("NX_GAME_FD", String.valueOf(f.detachFd()), true);
                    String name = getIntent().getStringExtra(EXTRA_NAME);
                    Os.setenv("NX_GAME_NAME", name == null ? "" : name, true);
                    Os.setenv("NX_KEYS", Launcher.keysFile(this, "prod.keys").getPath(), true);
                    java.io.File tk = Launcher.keysFile(this, "title.keys");
                    if (tk.exists()) Os.setenv("NX_TITLE_KEYS", tk.getPath(), true);
                }
            } catch (Exception ignored) {  // the runtime says what is missing
            }
        }
        super.onCreate(state);
        liveConfig = Launcher.readConfig(new java.io.File(Launcher.dataDir(), "config.txt"));
    }

    private int dp(float value) { return Ui.dp(this, value); }

    private String value(String key, String fallback) {
        String v = liveConfig == null ? null : liveConfig.get(key);
        return v == null ? fallback : v;
    }

    private void applyLive(String key, String val) {
        if (liveConfig == null) liveConfig = Launcher.readConfig(new java.io.File(Launcher.dataDir(), "config.txt"));
        liveConfig.put(key, val);
        Launcher.writeConfig(new java.io.File(Launcher.dataDir(), "config.txt"), liveConfig);
        try { Os.setenv(key, val, true); } catch (Exception ignored) {}
    }

    private TextView menuTitle(String text) {
        TextView t = Ui.title(this, text);
        t.setTextSize(14);
        return t;
    }

    private LinearLayout menuRow(String label, String hint) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(8));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.label(this, label);
        name.setTextSize(14);
        copy.addView(name);
        if (hint != null) copy.addView(Ui.hint(this, hint));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private void showQuickMenu() {
        if (quickMenu != null && quickMenu.isShowing()) return;
        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        quickMenu = dialog;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(Ui.shape(this, Ui.BG_TOP, 20, Ui.STROKE));
        panel.addView(menuTitle(Lang.t("Menú rápido", "Quick menu")));
        panel.addView(Ui.hint(this, Lang.t("Ajustes rápidos durante la partida", "Quick settings while playing")));

        LinearLayout stretch = menuRow(Lang.t("Estirar pantalla", "Stretch screen"), Lang.t("Llena la pantalla; puede distorsionar la imagen.", "Fills the screen; may distort the image."));
        Switch stretchSwitch = new Switch(this);
        stretchSwitch.setChecked("1".equals(value("NX_STRETCH", "0")));
        stretchSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                applyLive("NX_STRETCH", checked ? "1" : "0");
            }
        });
        stretch.addView(stretchSwitch); panel.addView(stretch);

        LinearLayout controls = menuRow(Lang.t("Controles en pantalla", "On-screen controls"), Lang.t("Mostrar u ocultar los botones virtuales.", "Show or hide the virtual buttons."));
        Switch controlsSwitch = new Switch(this);
        controlsSwitch.setChecked(!"0".equals(value("NX_TOUCH_SHOW", "1")));
        controlsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                applyLive("NX_TOUCH_SHOW", checked ? "1" : "0");
            }
        });
        controls.addView(controlsSwitch); panel.addView(controls);

        LinearLayout opacity = menuRow(Lang.t("Opacidad de los controles", "Control opacity"), Lang.t("El cambio se aplica inmediatamente.", "Change applies immediately."));
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        String currentOpacity = value("NX_TOUCH_OPACITY", "85");
        for (String amount : new String[] {"35", "60", "85"}) {
            final String selectedAmount = amount;
            TextView pill = Ui.pill(this, selectedAmount + "%");
            Ui.setSelected(this, pill, selectedAmount.equals(currentOpacity));
            pill.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    applyLive("NX_TOUCH_OPACITY", selectedAmount);
                    if (quickMenu != null) quickMenu.dismiss();
                    quickMenu = null;
                    showQuickMenu();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(4); choices.addView(pill, lp);
        }
        opacity.addView(choices); panel.addView(opacity);

        TextView note = Ui.hint(this, Lang.t("Las demás opciones permanecen en el Launcher porque deben configurarse antes de iniciar el juego.", "Other options stay in the Launcher because they must be set before the game starts."));
        note.setPadding(0, dp(10), 0, dp(8)); panel.addView(note);

        TextView exit = Ui.label(this, Lang.t("Salir del juego", "Exit game"));
        exit.setGravity(Gravity.CENTER); exit.setTextColor(Color.rgb(255, 150, 150)); exit.setTextSize(15);
        exit.setPadding(0, dp(13), 0, dp(13));
        exit.setBackground(Ui.pressable(this, Ui.shape(this, Color.rgb(62, 25, 39), 12, Color.rgb(115, 49, 67)), 12));
        exit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                dialog.dismiss();
                finish(); // GameActivity is isolated from the Launcher process; finishing returns to it.
            }
        });
        panel.addView(exit);

        TextView resume = Ui.label(this, Lang.t("Continuar jugando", "Resume game"));
        resume.setGravity(Gravity.CENTER); resume.setTextColor(Color.WHITE);
        resume.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        resume.setPadding(0, dp(13), 0, dp(13));
        resume.setBackground(Ui.pressable(this, Ui.shape(this, Ui.ACCENT_DARK, 12, 0), 12));
        resume.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { dialog.dismiss(); }
        });
        LinearLayout.LayoutParams resumeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resumeLp.topMargin = dp(8); panel.addView(resume, resumeLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface dismissed) {
                if (quickMenu == dialog) quickMenu = null;
            }
        });
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams a = w.getAttributes();
            a.width = Math.min(dp(390), getResources().getDisplayMetrics().widthPixels - dp(32));
            a.height = Math.min(dp(520), getResources().getDisplayMetrics().heightPixels - dp(64));
            a.dimAmount = 0.55f; w.setAttributes(a);
        }
    }

    @Override
    public void onBackPressed() {
        if (quickMenu != null && quickMenu.isShowing()) {
            quickMenu.dismiss();
            return;
        }
        showQuickMenu();
    }

    @Override
    protected void onDestroy() {
        if (quickMenu != null && quickMenu.isShowing()) quickMenu.dismiss();
        super.onDestroy();
    }
}
