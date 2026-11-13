package io.github.p3pandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The home screen in front of the game (GameActivity, a NativeActivity in its own process, libp3p.so): checks for the player's data in Internal
 * storage/Persona 3 Portable (asking for all-files access), shows the options and writes them to that folder's
 * config.txt, whose NX_* lines the runtime turns into its switches:
 *   game text   the language of the game's texts and subtitles: always language=auto (the device's, English when
 *               the game lacks it), written by save(), with no option on screen. The voices are the game's own
 *               option.
 *   resolution  720p (NX_HANDHELD=1, default) or 1080p (NX_HANDHELD=0). Sizes below 720p are not offered: the handheld
 *               mode ignores them (games/p3p/PROGRESO.md, the engine's target sizes)
 *   FSR         off (default) or on (NX_FSR=1): the present pass upscales the picture with FSR 1 instead of plain scaling
 *   stretch     keep the 16:9 picture (default) or fill the screen (NX_STRETCH=1)
 *   frame rate  30 (NX_FPS_CAP=30, default) or 60 (NX_FPS_CAP=60: the cap is the runtime's, nvn_queue.cpp)
 *   CPU cores   the scheduler's choice (NX_AFFINITY=0, default) or the fast cores for the replay and main threads (1)
 *   FPS counter off (default) or on (NX_SHOW_FPS)
 *   audio       low latency (NX_AUDIO_MODE=lowlatency, default) or the normal path ("none"): headphones often only
 *               get the normal one, and the runtime reopens its stream when the output changes
 *   on-screen controls on (default) or off (NX_TOUCH_SHOW), their size (NX_TOUCH_SIZE) and opacity (NX_TOUCH_OPACITY),
 *               and where they are (NX_TOUCH_LAYOUT, written by ControlsEditor)
 * and imports or exports the saves (user/save, SaveFiles.java): a ZIP or a folder in, a ZIP in Download out.
 * The game: the player picks their own NSP or XCI (Persona 3 Portable 1.0.0) and prod.keys here; ImportActivity checks
 * them and translates the game's shaders once, and every start reads the game from that file as it is (the runtime's
 * game_image.cpp, through NX_GAME_FD / NX_GAME_NAME / NX_KEYS). Nothing of the game is copied but its shaders. A data
 * folder made on a PC (exefs/ or analysis/, extract/romfs, spirv/) still works when no file was picked.
 * The icon: a home screen shortcut with a picture of the player's own (Android lets an app pin shortcuts, not change
 * its own icon).
 * Other lines of config.txt are kept as they were. The launcher itself speaks the device's language (Lang.java:
 * Spanish, English, French, German, Italian, Portuguese, Japanese, Korean, Chinese; English otherwise).
 */
public class Launcher extends Activity {
    static final String DATA = "Persona 3 Portable";

    static final class Option {
        final String key, es, en;
        final String[] values, labelsEs, labelsEn;
        int pick;
        final List<TextView> pills = new ArrayList<>();  // the choices on screen (build())

        Option(String key, String es, String en, String[] values, String[] labelsEs, String[] labelsEn, int pick) {
            this.key = key;
            this.es = es;
            this.en = en;
            this.values = values;
            this.labelsEs = labelsEs;
            this.labelsEn = labelsEn;
            this.pick = pick;
        }
    }

    final List<Option> options = new ArrayList<>();
    final Map<String, String> config = new LinkedHashMap<>();
    TextView status;
    Button play;
    static final int PICK_ZIP = 1, PICK_FOLDER = 2, PICK_GAME = 3, PICK_KEYS = 4, PICK_ICON = 5, PREPARE = 6;

    String t(String spanish, String english) { return Lang.t(spanish, english); }

    String label(Option o) { return t(o.labelsEs[o.pick], o.labelsEn[o.pick]); }

    static File dataDir() { return new File(Environment.getExternalStorageDirectory(), DATA); }

    boolean hasAccess() { return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager(); }

    // ---- the game file, read in place at every start ----

    SharedPreferences prefs() { return getSharedPreferences("game", MODE_PRIVATE); }

    String gameUri() { return prefs().getString("uri", null); }

    String gameName() { return prefs().getString("name", ""); }

    static File keysFile(Context c, String name) { return new File(new File(c.getFilesDir(), "keys"), name); }

    boolean hasKeys() { return keysFile(this, "prod.keys").length() > 0; }

    boolean gameReadable() {
        try (ParcelFileDescriptor f = getContentResolver().openFileDescriptor(Uri.parse(gameUri()), "r")) {
            return f != null;
        } catch (Exception e) {
            return false;
        }
    }

    // The shaders were translated from this game file (ImportActivity writes spirv/import.txt last).
    boolean prepared() {
        File note = new File(dataDir(), "spirv/import.txt");
        if (!note.canRead()) return false;
        try (BufferedReader r = new BufferedReader(new FileReader(note))) {
            return gameName().equals(r.readLine()) && PreparationFiles.ready(new File(dataDir(), "spirv"));
        } catch (Exception e) {
            return false;
        }
    }

    String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "" : last.substring(last.lastIndexOf('/') + 1);
    }

    void pick(int request, String type, boolean multiple) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        if (multiple) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, request);
    }

    void onGamePicked(Uri uri) {
        String name = displayName(uri);
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".nsp") && !lower.endsWith(".xci")) {
            tell(t("Archivo no válido", "Not a game file"),
                    t("Elige el archivo .nsp o .xci del juego base (no el de la actualización).",
                            "Choose the base game's .nsp or .xci file (not the update's)."));
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {  // a provider without persistable grants: works while the app runs
        }
        prefs().edit().putString("uri", uri.toString()).putString("name", name).apply();
    }

    // prod.keys (and title.keys, when the dump needs it): copied into the app's own storage, never shared or logged.
    void onKeysPicked(Intent data) {
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null)
            for (int k = 0; k < clip.getItemCount(); k++) uris.add(clip.getItemAt(k).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        boolean prod = false;
        for (Uri uri : uris) {
            String name = displayName(uri).toLowerCase(Locale.ROOT);
            String target = name.contains("title") ? "title.keys" : "prod.keys";
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                byte[] buf = new byte[1 << 20];
                int n = 0, r;
                while (in != null && n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
                String text = new String(buf, 0, n, "UTF-8");
                if (target.equals("prod.keys") && !text.contains("header_key") && !text.contains("master_key_00")) continue;
                File f = keysFile(this, target);
                f.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(f)) {
                    out.write(buf, 0, n);
                }
                prod |= target.equals("prod.keys");
            } catch (Exception ignored) {
            }
        }
        if (!prod && !hasKeys())
            tell(t("Claves no válidas", "Not a keys file"),
                    t("Ese archivo no tiene las claves (header_key). Elige el prod.keys de tu consola.",
                            "That file has no keys (header_key). Choose your console's prod.keys."));
    }

    void prepare() {
        Intent i = new Intent(this, ImportActivity.class);
        i.putExtra(ImportActivity.EXTRA_URI, gameUri());
        i.putExtra(ImportActivity.EXTRA_NAME, gameName());
        startActivityForResult(i, PREPARE);
    }

    // Starts the game, in its own process (GameActivity): it gets the picked game file's Uri and opens it there. Checked
    // here first, so a file that can no longer be read is told about instead of a game that closes.
    boolean startGame(boolean fromFile) {
        Intent game = new Intent(this, GameActivity.class);
        if (fromFile) {
            if (!gameReadable()) return false;
            game.putExtra(GameActivity.EXTRA_URI, gameUri());
            game.putExtra(GameActivity.EXTRA_NAME, gameName());
        }
        startActivity(game);
        return true;
    }

    // A home screen shortcut with the player's picture (cropped to a square; an adaptive icon, so the home screen gives
    // it its own shape).
    void onIconPicked(Uri uri) {
        try {
            Bitmap src;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                src = BitmapFactory.decodeStream(in);
            }
            if (src == null) throw new IllegalArgumentException("not a picture");
            int side = Math.min(src.getWidth(), src.getHeight());
            Bitmap square = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
            Bitmap icon = Bitmap.createScaledBitmap(square, 432, 432, true);
            ShortcutManager sm = getSystemService(ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) {
                tell(t("Icono", "Icon"), t("Tu pantalla de inicio no admite accesos directos.",
                        "Your home screen does not support shortcuts."));
                return;
            }
            Intent launch = new Intent(this, Launcher.class).setAction(Intent.ACTION_MAIN);
            ShortcutInfo info = new ShortcutInfo.Builder(this, "picture_" + System.currentTimeMillis())
                    .setShortLabel("P3P").setLongLabel("Persona 3 Portable").setIcon(Icon.createWithAdaptiveBitmap(icon))
                    .setIntent(launch).build();
            sm.requestPinShortcut(info, null);
            tell(t("Icono", "Icon"), t("Acepta el aviso para poner el acceso directo con tu imagen en la pantalla de inicio. "
                            + "El icono del cajón de apps no cambia: Android no deja que una app cambie el suyo.",
                    "Accept the prompt to put the shortcut with your picture on the home screen. The icon in the app "
                            + "drawer stays: Android does not let an app change its own."));
        } catch (Exception e) {
            tell(t("Icono", "Icon"), t("No se pudo usar esa imagen.", "That picture could not be used."));
        }
    }

    // The recompiled build reads analysis/main.bin, the native one (the game's own code) exefs/main.
    boolean hasData() {
        return new File(dataDir(), "exefs/main").canRead() || new File(dataDir(), "analysis/main.bin").canRead();
    }

    // The build ID the port is made for (Persona 3 Portable 1.0.0, the base game): the start of exefs/main's module ID
    // (NSO header +0x40). The native build refuses another (runtime main.cpp); say so here instead of closing.
    static final String BUILD_ID = "663052C2C7E8C357";

    // Null when exefs/main is missing or the right version, else its build ID.
    String wrongBuild() {
        File main = new File(dataDir(), "exefs/main");
        if (!main.canRead()) return null;
        byte[] id = new byte[8];
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(main, "r")) {
            f.seek(0x40);
            f.readFully(id);
        } catch (java.io.IOException e) {
            return "?";
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : id) hex.append(String.format("%02X", b & 0xFF));
        return hex.toString().equals(BUILD_ID) ? null : hex.toString();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        applyImmersiveMode();
        // Image: the render size (the game draws at it; the phone's screen scales it) and how it is scaled up.
        options.add(new Option("RESOLUTION", "Resolución", "Resolution",
                new String[] {"720p", "1080p"},
                new String[] {"720p (recomendada)", "1080p (pesada)"},
                new String[] {"720p (recommended)", "1080p (heavy)"}, 0));
        options.add(new Option("NX_FSR", "Escalado nítido (FSR)", "Sharp upscaling (FSR)", new String[] {"0", "1"},
                new String[] {"Desactivado", "Activado"}, new String[] {"Off", "On"}, 0));
        options.add(new Option("NX_STRETCH", "Pantalla completa (estirar)", "Fullscreen (stretch)",
                new String[] {"0", "1"},
                new String[] {"Desactivada", "Activada"},
                new String[] {"Off", "On"}, 0));
        // Performance: what really moves the speed of this game on a phone.
        options.add(new Option("NX_FPS_CAP", "Fotogramas por segundo", "Frame rate", new String[] {"30", "60"},
                new String[] {"30 fps", "60 fps"}, new String[] {"30 fps", "60 fps"}, 0));
        options.add(new Option("NX_AFFINITY", "Núcleos de la CPU", "CPU cores", new String[] {"0", "1"},
                new String[] {"Automático", "Priorizar los rápidos"}, new String[] {"Automatic", "Prefer the fast ones"}, 0));
        options.add(new Option("NX_SHOW_FPS", "Contador de FPS", "FPS counter", new String[] {"0", "1"},
                new String[] {"Desactivado", "Activado"}, new String[] {"Off", "On"}, 0));
        // Audio: how the phone's audio path is asked for (some outputs, headphones among them, do not give the fast one).
        options.add(new Option("NX_AUDIO_MODE", "Salida de audio", "Audio output", new String[] {"lowlatency", "none"},
                new String[] {"Baja latencia", "Normal (más estable)"}, new String[] {"Low latency", "Normal (more stable)"}, 0));
        options.add(new Option("NX_TOUCH_SHOW", "Controles táctiles", "On-screen controls", new String[] {"1", "0"},
                new String[] {"Activados", "Desactivados"}, new String[] {"On", "Off"}, 0));
        options.add(new Option("NX_TOUCH_SIZE", "Tamaño de los botones", "Button size",
                new String[] {"80", "100", "125"}, new String[] {"Pequeño", "Normal", "Grande"},
                new String[] {"Small", "Normal", "Large"}, 1));
        options.add(new Option("NX_TOUCH_OPACITY", "Opacidad de los botones", "Button opacity",
                new String[] {"50", "85", "100"}, new String[] {"Baja", "Normal", "Alta"},
                new String[] {"Low", "Normal", "High"}, 1));
        build();
    }

    // Keep both Android system bars hidden in the launcher, not just in the game.
    // Reapply after returning from dialogs, file pickers, or another activity.
    private void applyImmersiveMode() {
        final View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveMode();
        refresh();
    }

    String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    void build() {
        final LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(Ui.background());
        // Immersive mode owns the full display; do not re-add system-bar insets as empty padding.

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));

        TextView title = new TextView(this);
        title.setText("PERSONA 3 PORTABLE");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.04f);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Android recomp  \u2022  v" + versionName());
        subtitle.setTextColor(Ui.ACCENT);
        subtitle.setTextSize(13);
        subtitle.setTypeface(Ui.medium());
        subtitle.setPadding(0, dp(2), 0, 0);
        root.addView(subtitle);

        status = new TextView(this);
        status.setTextColor(Ui.TEXT);
        status.setTextSize(14.5f);
        status.setLineSpacing(0, 1.1f);
        Ui.card(this, root).addView(status);

        LinearLayout files = section(root, t("Archivos del juego", "Game files"), null);
        addRow(files, t("Archivo del juego (.nsp / .xci)", "Game file (.nsp / .xci)"), t("Elegir", "Choose"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pick(PICK_GAME, "*/*", false);
                    }
                });
        addRow(files, t("Claves (prod.keys)", "Keys (prod.keys)"), t("Elegir", "Choose"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick(PICK_KEYS, "*/*", true);
            }
        });

        // The options, a card per section; every choice is on screen at once (one tap, no cycling).
        LinearLayout card = files;
        for (final Option o : options) {
            String[] section = sectionOf(o.key);
            if (section != null) {
                String[] advice = hintOf(o.key);
                card = section(root, t(section[0], section[1]), advice == null ? null : t(advice[0], advice[1]));
            }
            card.addView(optionBlock(o));
        }

        // Still in the Controls card: where the buttons are.
        addRow(card, t("Posición de los controles", "Controls position"), t("Mover", "Move"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                save();
                startActivity(new Intent(Launcher.this, ControlsEditor.class));
            }
        });

        // Mods manager: scans the existing mods/ folder and manages RomFS overlays.
        LinearLayout tools = section(root, t("Herramientas", "Tools"), null);
        addRow(tools, t("Gestor de mods", "Mod manager"), t("Abrir", "Open"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                startActivity(new Intent(Launcher.this, ModManagerActivity.class));
            }
        });
        addRow(tools, t("Diagnóstico del dispositivo", "Device diagnostics"), t("Abrir", "Open"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(Launcher.this, DiagnosticsActivity.class));
                    }
                });

        // Saves: in from a ZIP or a folder (a Switch backup or another player's), out to a ZIP in Download.
        LinearLayout saves = section(root, t("Partidas guardadas", "Saves"), null);
        LinearLayout saveButtons = new LinearLayout(this);
        saveButtons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        right.leftMargin = dp(8);
        saveButtons.addView(Ui.action(this, t("Importar", "Import"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                askImport();
            }
        }), left);
        saveButtons.addView(Ui.action(this, t("Exportar", "Export"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                exportSaves();
            }
        }), right);
        saves.addView(saveButtons);

        // The Play button stays at the bottom while the options scroll.
        play = Ui.primary(this);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                if (gameUri() != null) {  // the game file the player picked
                    if (!gameReadable()) pick(PICK_GAME, "*/*", false);
                    else if (!hasKeys()) pick(PICK_KEYS, "*/*", true);
                    else if (!prepared()) prepare();
                    else if (gameReadable()) {
                        save();
                        startGame(true);
                    } else {
                        tell(t("No se pudo abrir el juego", "The game could not be opened"), gameName());
                    }
                    return;
                }
                if (!hasData()) {
                    pick(PICK_GAME, "*/*", false);
                    return;
                }
                if (wrongBuild() != null) {
                    refresh();
                    return;
                }
                save();
                startGame(false);
            }
        });
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Color.rgb(4, 8, 20));
        bar.setPadding(dp(20), dp(10), dp(20), dp(12));
        bar.addView(play, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(root);
        outer.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        View line = new View(this);
        line.setBackgroundColor(Ui.STROKE);
        outer.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        outer.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(outer);
        updateButtons(null);
    }

    // One option: its name, and its choices side by side (the chosen one filled).
    LinearLayout optionBlock(final Option o) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(6), 0, dp(8));
        block.addView(Ui.label(this, t(o.es, o.en)));
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        o.pills.clear();
        for (int k = 0; k < o.values.length; k++) {
            final int choice = k;
            TextView pill = Ui.pill(this, t(o.labelsEs[k], o.labelsEn[k]));
            pill.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    o.pick = choice;
                    updateButtons(null);
                    save();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
            if (k > 0) lp.leftMargin = dp(6);
            seg.addView(pill, lp);
            o.pills.add(pill);
        }
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        segLp.topMargin = dp(8);
        block.addView(seg, segLp);
        return block;
    }

    void refresh() {
        load();
        for (Option o : options) {
            String v = config.get(o.key);
            if ("RESOLUTION".equals(o.key)) {
                // Older configs: sizes below 720p never worked here (the handheld mode ignores them) and land on 720p,
                // 900p on 1080p; without a choice, docked mode meant 1080p.
                if ("900p".equals(v) || "1080p".equals(v) || (v == null && "0".equals(config.get("NX_HANDHELD"))))
                    v = "1080p";
                else v = "720p";
            }
            for (int k = 0; v != null && k < o.values.length; k++)
                if (o.values[k].equals(v)) o.pick = k;
        }
        updateButtons(getWindow().getDecorView());
        if (!hasAccess()) {
            status.setText(t("Hace falta el permiso de acceso a todos los archivos para leer los datos del juego.",
                    "All-files access is needed to read the game data."));
            play.setText(t("Dar permiso", "Grant access"));
        } else if (gameUri() != null && !gameReadable()) {
            status.setText(Lang.f("Ya no se puede leer el archivo del juego (%s): vuelve a elegirlo.",
                    "The game file can no longer be read (%s): choose it again.", gameName()));
            play.setText(t("Elegir el juego", "Choose the game"));
        } else if (gameUri() != null && !hasKeys()) {
            status.setText(t("Falta tu archivo prod.keys.", "Your prod.keys file is missing."));
            play.setText(t("Elegir prod.keys", "Choose prod.keys"));
        } else if (gameUri() != null && !prepared()) {
            status.setText(Lang.f("Juego: %s. Falta prepararlo: solo una vez, unos minutos.",
                    "Game: %s. Prepare the local shader cache to continue.", gameName()) +
                    (PreparationFiles.failure(new File(dataDir(), "spirv")).isEmpty() ? "" :
                     "\nShader generation failed:\n" + PreparationFiles.failure(new File(dataDir(), "spirv"))));
            play.setText(t("Preparar el juego", "Prepare the game"));
        } else if (gameUri() != null) {
            status.setText(Lang.f("Juego listo: %s", "Game ready: %s", gameName()));
            play.setText(t("Jugar", "Play"));
        } else if (!hasData()) {
            status.setText(t("Elige el archivo de tu juego (.nsp o .xci de Persona 3 Portable 1.0.0) y tu prod.keys.",
                    "Choose your game file (a Persona 3 Portable 1.0.0 .nsp or .xci) and your prod.keys."));
            play.setText(t("Elegir el juego", "Choose the game"));
        } else if (wrongBuild() != null) {
            status.setText(Lang.f("Almacenamiento interno/%s/exefs/main no es Persona 3 Portable 1.0.0 (build %s). Copia el "
                            + "exefs del juego base, sin la actualización.",
                    "Internal storage/%s/exefs/main is not Persona 3 Portable 1.0.0 (build %s). Copy the exefs of the base "
                            + "game, without the update.", DATA, wrongBuild()));
            play.setText(t("Volver a buscar", "Look again"));
        } else {
            status.setText(Lang.f("Datos listos: %s", "Data ready: %s", dataDir().getPath()));
            play.setText(t("Jugar", "Play"));
        }
    }

    // Marks the chosen choice of every option (the View argument is a leftover of the old cycling buttons).
    void updateButtons(View unused) {
        for (Option o : options)
            for (int k = 0; k < o.pills.size(); k++) Ui.setSelected(this, o.pills.get(k), k == o.pick);
    }

    void tell(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }

    void askImport() {
        new AlertDialog.Builder(this)
                .setTitle(t("Importar partidas", "Import saves"))
                .setMessage(t("Selecione o ZIP de backup ou a pasta que contém os arquivos salvos. "
                                + "Suas partidas atuais serão copiadas para um backup antes da importação.",
                        "Select a backup ZIP or the folder containing the save files. "
                                + "Your current saves will be backed up before importing."))
                .setPositiveButton(t("Archivo ZIP", "ZIP file"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/zip",
                                "application/x-zip-compressed", "application/octet-stream"});
                        startActivityForResult(i, PICK_ZIP);
                    }
                })
                .setNeutralButton(t("Carpeta", "Folder"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER);
                    }
                })
                .setNegativeButton(t("Cancelar", "Cancel"), null)
                .show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PREPARE) {
            refresh();
            return;
        }
        if (result != RESULT_OK || data == null) return;
        if (request == PICK_KEYS) {
            onKeysPicked(data);
            refresh();
            return;
        }
        if (data.getData() == null) return;
        if (request == PICK_GAME) {
            onGamePicked(data.getData());
            refresh();
            return;
        }
        if (request == PICK_ICON) {
            onIconPicked(data.getData());
            return;
        }
        try {
            SaveFiles.Result r = request == PICK_ZIP ? SaveFiles.importZip(getContentResolver(), data.getData())
                    : request == PICK_FOLDER ? SaveFiles.importTree(getContentResolver(), data.getData()) : null;
            if (r == null) {
                tell(t("Nenhum save encontrado", "No saves found"),
                        t("Não encontrei arquivos de save válidos nesse ZIP ou pasta. "
                                        + "Escolha um backup exportado pelo P3P ou a pasta correta dos saves.",
                                "No valid save files were found in that ZIP or folder. "
                                        + "Choose a P3P backup or the correct save folder."));
                return;
            }
            String kept = r.backup == null ? "" : Lang.f("\n\nBackup anterior: user/%s.",
                    "\n\nPrevious backup: user/%s.", r.backup);
            tell(t("Partidas restauradas", "Saves restored"),
                    Lang.f("%d arquivos restaurados com sucesso.", "%d files restored successfully.", r.files) + kept);
        } catch (Exception e) {
            tell(t("No se pudo importar", "Import failed"), String.valueOf(e.getMessage()));
        }
    }

    void exportSaves() {
        try {
            java.io.File out = SaveFiles.export();
            // Listed at once by the file pickers and the Files app (written straight to disk, it would wait for a scan).
            if (out != null) android.media.MediaScannerConnection.scanFile(this, new String[] {out.getPath()}, null, null);
            if (out == null) tell(t("No hay partidas", "No saves"), t("Todavía no hay partidas guardadas.", "There are no saves yet."));
            else tell(t("Partidas exportadas", "Saves exported"),
                    Lang.f("Guardadas en Descargas/%s. Se importan tal cual con el botón Importar.",
                            "Saved to Download/%s. The Import button takes it back as is.", out.getName()));
        } catch (Exception e) {
            tell(t("No se pudo exportar", "Export failed"), String.valueOf(e.getMessage()));
        }
    }

    void askAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    // config.txt: KEY=value lines, in order.
    static Map<String, String> readConfig(File f) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!f.canRead()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (eq > 0) out.put(line.substring(0, eq), line.substring(eq + 1));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static boolean writeConfig(File f, Map<String, String> config) {
        try (FileWriter w = new FileWriter(f)) {
            for (Map.Entry<String, String> e : config.entrySet()) w.write(e.getKey() + "=" + e.getValue() + "\n");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    void load() {
        config.clear();
        config.putAll(readConfig(new File(dataDir(), "config.txt")));
    }

    void save() {
        if (!hasAccess()) return;
        dataDir().mkdirs();
        if (!dataDir().isDirectory()) return;
        load();
        for (Option o : options) config.put(o.key, o.values[o.pick]);
        // Game text and subtitles always follow the device's language (no option on screen).
        config.put("language", "auto");
        final String res = config.get("RESOLUTION");
        // 720p is the handheld mode (the game's own 1280x720). The other sizes run docked with NX_RENDER_RES: in handheld
        // mode the game ignores the size asked (logs: "480p" still drew every pass at 1280x720), docked it follows it
        // (1080p drew at 1920x1080).
        String size = "1280x720";
        if ("480p".equals(res)) size = "854x480";
        else if ("540p".equals(res)) size = "960x540";
        else if ("900p".equals(res)) size = "1600x900";
        else if ("1080p".equals(res)) size = "1920x1080";
        final boolean handheld = !"480p".equals(res) && !"540p".equals(res) && !"900p".equals(res) && !"1080p".equals(res);
        config.put("NX_HANDHELD", handheld ? "1" : "0");
        config.put("resolution", size);
        config.put("NX_RENDER_RES", size);
        config.put("NX_RES", handheld ? "720p" : res);
        // Keys of older launchers: the texture preset (GRAPHICS) only biased the mip level of some 3D draws, which this
        // game's small textures hardly show, so it is gone and its runtime switches are cleared.
        for (String k : new String[] {"GRAPHICS", "RENDER_SCALE", "NX_ANISO_MAX", "NX_LOD_BIAS", "NX_HUD_1080", "NX_SHADOWS", "NX_GPU_PROFILE"})
            config.remove(k);
        writeConfig(new File(dataDir(), "config.txt"), config);
    }

    // Section titles of the options screen: the first option of each section carries one.
    String[] sectionOf(String key) {
        if ("RESOLUTION".equals(key)) return new String[] {"Imagen", "Display"};
        if ("NX_FPS_CAP".equals(key)) return new String[] {"Rendimiento", "Performance"};
        if ("NX_AUDIO_MODE".equals(key)) return new String[] {"Audio", "Audio"};
        if ("NX_TOUCH_SHOW".equals(key)) return new String[] {"Controles", "Controls"};
        return null;
    }

    // A line of advice under a section's title.
    String[] hintOf(String key) {
        if ("RESOLUTION".equals(key))
            return new String[] {"720p es la resolución de la consola portátil y la que mejor va; 1080p necesita un móvil potente.",
                    "720p is the handheld console's resolution and the one that runs best; 1080p needs a powerful phone."};
        if ("NX_FPS_CAP".equals(key))
            return new String[] {"En móviles modestos, 30 fps suele ir más fluido que 60. \"Priorizar los rápidos\" ayuda cuando la GPU no está al límite.",
                    "On modest phones 30 fps is usually smoother than 60. \"Prefer the fast ones\" helps when the GPU is not at its limit."};
        if ("NX_AUDIO_MODE".equals(key))
            return new String[] {"Si el sonido falla con auriculares, prueba \"Normal\".",
                    "If the sound breaks up with headphones, try \"Normal\"."};
        return null;
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // A rounded panel with a title (and a line of advice); the rows go inside it.
    LinearLayout section(LinearLayout root, String title, String advice) {
        LinearLayout card = Ui.card(this, root);
        if (!title.isEmpty()) card.addView(Ui.title(this, title));
        if (advice != null) card.addView(Ui.hint(this, advice));
        return card;
    }

    // A name on the left, a small button on the right.
    void addRow(LinearLayout card, String label, String button, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));
        row.addView(Ui.label(this, label), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(12);
        row.addView(Ui.action(this, button, click), lp);
        card.addView(row);
    }
}
