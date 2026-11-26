package io.github.p3pandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONObject;

/** Safe manager for the RomFS overlay already implemented by runtime/src/hle_fs.cpp. */
public final class ModManagerActivity extends Activity {
    private static final int PICK_CPK = 71, PICK_ZIP = 72;
    private static final long MAX_UNPACKED = 8L * 1024L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 30000;
    private LinearLayout list;
    private TextView summary;

    // SHA-256 cache survives enable/disable renames during this app process.
    // File length + modification time invalidate entries after normal file edits.
    private static final Map<String, HashCacheEntry> HASH_CACHE = new HashMap<>();
    private static final int MAX_HASH_CACHE_ENTRIES = 100000;

    private static final class HashCacheEntry {
        final long length;
        final long modified;
        final String hash;
        HashCacheEntry(long length, long modified, String hash) {
            this.length = length;
            this.modified = modified;
            this.hash = hash;
        }
    }

    private File modsDir() { return new File(Launcher.dataDir(), "mods"); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    // Keep the utility screen edge-to-edge and hide both Android system bars.
    private void applyImmersiveMode() {
        View decor = getWindow().getDecorView();
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

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        applyImmersiveMode();
        if (list != null) refresh();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(dp(48));
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setStateListAnimator(null);
        b.setBackground(Ui.pressable(this,
                Ui.shape(this, primary ? Ui.ACCENT : Ui.PILL, 13, primary ? 0 : Ui.STROKE), 13));
        return b;
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setPadding(dp(22), dp(22), dp(22), dp(18));
        root.setBackground(Ui.background());

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("P3P ANDROID  •  PERSONALIZAÇÃO", 10, Color.rgb(110, 175, 255), true);
        TextView title = text("Gerenciador de mods", 25, Color.WHITE, true);
        heading.addView(eyebrow);
        heading.addView(title);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button back = button("Voltar", false);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        top.addView(back);
        root.addView(top);

        LinearLayout summaryCard = new LinearLayout(this);
        summaryCard.setOrientation(LinearLayout.VERTICAL);
        summaryCard.setPadding(dp(16), dp(13), dp(16), dp(13));
        summaryCard.setBackground(Ui.shape(this, Ui.CARD, 16, Ui.STROKE));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = dp(16);
        summaryParams.bottomMargin = dp(12);
        root.addView(summaryCard, summaryParams);
        summaryCard.addView(text("BIBLIOTECA", 10, Color.rgb(110, 175, 255), true));
        summary = text("", 14, Color.WHITE, true);
        summary.setPadding(0, dp(4), 0, 0);
        summaryCard.addView(summary);

        TextView actionTitle = text("Importar novo mod", 16, Color.WHITE, true);
        actionTitle.setPadding(0, dp(4), 0, dp(8));
        root.addView(actionTitle);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cpk = button("＋  Importar CPK", true);
        Button zip = button("＋  Importar ZIP", false);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, -2, 1);
        actionParams.rightMargin = dp(6);
        actions.addView(cpk, actionParams);
        LinearLayout.LayoutParams zipParams = new LinearLayout.LayoutParams(0, -2, 1);
        zipParams.leftMargin = dp(6);
        actions.addView(zip, zipParams);
        cpk.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pick(PICK_CPK); }
        });
        zip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pick(PICK_ZIP); }
        });
        root.addView(actions);

        TextView note = text("Os mods ficam em Persona 3 Portable/mods. Pastas iniciadas por _ ficam desativadas. Reinicie o jogo para aplicar alterações.",
                12, Color.rgb(170, 190, 215), false);
        note.setPadding(dp(2), dp(9), dp(2), dp(10));
        root.addView(note);

        TextView installed = text("MODS INSTALADOS", 10, Color.rgb(110, 175, 255), true);
        installed.setPadding(0, dp(4), 0, dp(8));
        root.addView(installed);
        // Keep the complete screen in one scroll container. The old layout pinned
        // the header/actions and gave the mod list only the remaining few pixels,
        // which made cards look cut off on landscape and short displays.
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(22));
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setClipToPadding(false);
        pageScroll.setVerticalScrollBarEnabled(false);
        pageScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        pageScroll.setBackground(Ui.background());
        pageScroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(pageScroll);
        applyImmersiveMode();
    }

    private void pick(int request) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        if (request == PICK_CPK) i.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[] {"application/octet-stream", "application/x-cpk"});
        else i.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[] {"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        startActivityForResult(i, request);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (request == PICK_CPK) {
                final String filename = displayName(uri);
                if (!filename.toLowerCase(Locale.ROOT).endsWith(".cpk")) {
                    toast("Selecione um arquivo .cpk");
                    return;
                }
                String suggested = cleanName(filename.substring(0, filename.length() - 4));
                android.widget.EditText destination = new android.widget.EditText(this);
                destination.setSingleLine(true);
                destination.setText((isModCpk(filename) ? "data/" : "data_EN/") + filename);
                destination.setHint("ex.: caminho/do/arquivo.cpk");
                destination.setSelectAllOnFocus(true);
                LinearLayout wrap = new LinearLayout(this);
                wrap.setOrientation(LinearLayout.VERTICAL);
                wrap.setPadding(dp(20), dp(8), dp(20), 0);
                TextView explanation = text(
                        "Informe o caminho EXATO do arquivo dentro do RomFS do jogo. " +
                        "Ele será criado em mods/" + suggested + "/romfs/.",
                        13, Color.LTGRAY, false);
                wrap.addView(explanation);
                wrap.addView(destination);
                new AlertDialog.Builder(this)
                    .setTitle("Destino do CPK")
                    .setView(wrap)
                    .setPositiveButton("Importar", new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface dialog, int which) {
                            importCpk(uri, suggested, destination.getText().toString());
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            } else if (request == PICK_ZIP) {
                importZip(uri, cleanName(displayName(uri).replaceFirst("(?i)\\.zip$", "")));
            }
        } catch (Exception e) {
            error("Não foi possível importar", e);
        }
    }

    // The three extra CPKs the game binds next to its own when a mod has them (the "modCPK" plugin of the Switch).
    private static boolean isModCpk(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.equals("mod.cpk") || n.equals("mod_extra.cpk") || n.equals("mod_bgm.cpk");
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) {}
        if (name == null || name.trim().isEmpty()) name = "Mod";
        return name;
    }

    private String cleanName(String name) {
        String s = name == null ? "Mod" : name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        s = s.replaceAll("[. ]+$", "");
        if (s.isEmpty() || s.equals(".") || s.equals("..")) s = "Mod";
        return s;
    }

    private File uniqueMod(String base) throws Exception {
        File root = modsDir();
        if (!root.isDirectory() && !root.mkdirs()) throw new Exception("Não foi possível criar a pasta mods.");
        File out = new File(root, base);
        int n = 2;
        while (out.exists()) out = new File(root, base + " (" + n++ + ")");
        return out;
    }

    private void importCpk(Uri uri, String name, String relativePath) {
        try {
            String rel = relativePath == null ? "" : relativePath.trim().replace('\\', '/');
            if (rel.isEmpty() || rel.startsWith("/") || rel.matches("^[A-Za-z]:.*"))
                throw new Exception("Informe um caminho relativo dentro do RomFS.");
            for (String part : rel.split("/"))
                if (part.isEmpty() || part.equals(".") || part.equals(".."))
                    throw new Exception("O caminho informado não é seguro.");
            if (!rel.toLowerCase(Locale.ROOT).endsWith(".cpk"))
                throw new Exception("O destino precisa terminar em .cpk.");
            File mod = uniqueMod(name);
            File out = new File(new File(mod, "romfs"), rel);
            String rootPath = new File(mod, "romfs").getCanonicalPath() + File.separator;
            if (!out.getCanonicalPath().startsWith(rootPath))
                throw new Exception("Caminho de destino inválido.");
            File parent = out.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new Exception("Não foi possível criar a pasta do mod.");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream stream = new FileOutputStream(out)) {
                if (in == null) throw new Exception("Não foi possível abrir o CPK.");
                copy(in, stream, MAX_UNPACKED);
            }
            refresh();
            toast("Mod importado: " + mod.getName());
        } catch (Exception e) { error("Falha ao importar CPK", e); }
    }

    private void importZip(Uri uri, String name) {
        File mod = null;
        try {
            // Find the actual RomFS root by path component, not by a loose substring.
            // This accepts archives such as PTBR/romfs/... and
            // MyTranslation/PTBR/romfs/data_EN/....
            String prefix = null;
            int entries = 0;
            try (InputStream raw = getContentResolver().openInputStream(uri);
                 ZipInputStream z = new ZipInputStream(raw)) {
                if (raw == null) throw new Exception("Não foi possível abrir o ZIP.");
                for (ZipEntry e; (e = z.getNextEntry()) != null;) {
                    if (++entries > MAX_ENTRIES) throw new Exception("O ZIP contém arquivos demais.");
                    String p = safeZipName(e.getName());
                    if (p == null) continue;
                    String[] parts = p.split("/");
                    for (int i = 0; i < parts.length; i++) {
                        if ("romfs".equalsIgnoreCase(parts[i])) {
                            StringBuilder candidate = new StringBuilder();
                            for (int j = 0; j < i; j++) candidate.append(parts[j]).append('/');
                            String value = candidate.toString();
                            if (prefix == null || value.length() < prefix.length()) prefix = value;
                            break;
                        }
                    }
                }
            }

            mod = uniqueMod(name);
            File romfs = new File(mod, "romfs");
            if (!romfs.mkdirs()) throw new Exception("Não foi possível criar a pasta romfs.");
            long total = 0;
            entries = 0;
            try (InputStream raw = getContentResolver().openInputStream(uri);
                 ZipInputStream z = new ZipInputStream(raw)) {
                if (raw == null) throw new Exception("Não foi possível abrir o ZIP.");
                for (ZipEntry e; (e = z.getNextEntry()) != null;) {
                    if (e.isDirectory()) continue;
                    if (++entries > MAX_ENTRIES) throw new Exception("O ZIP contém arquivos demais.");
                    String path = safeZipName(e.getName());
                    if (path == null) continue;
                    String rel = path;
                    if (prefix != null) {
                        String[] parts = path.split("/");
                        int at = -1;
                        for (int i = 0; i < parts.length; i++) {
                            if ("romfs".equalsIgnoreCase(parts[i])) { at = i; break; }
                        }
                        if (at < 0 || at + 1 >= parts.length) continue;
                        StringBuilder relative = new StringBuilder();
                        for (int i = at + 1; i < parts.length; i++) {
                            if (relative.length() > 0) relative.append('/');
                            relative.append(parts[i]);
                        }
                        rel = relative.toString();
                    }
                    if (rel.isEmpty()) continue;

                    // Translation packs often label the archive PTBR/ but put a language
                    // CPK directly in romfs/. The game loads that CPK from data_EN/.
                    // Preserve explicit paths (data_EN/, movie/, etc.) unchanged.
                    if (rel.indexOf('/') < 0 && isModCpk(rel)) {
                        // mod.cpk, mod_extra.cpk and mod_bgm.cpk are not the game's language CPKs: the game binds them from
                        // data/ (see install_mod_cpk in games/p3p/game.cpp).
                        rel = "data/" + rel;
                    } else if (rel.toLowerCase(Locale.ROOT).endsWith(".cpk")
                            && rel.indexOf('/') < 0) {
                        rel = "data_EN/" + rel;
                    } else if (rel.regionMatches(true, 0, "data_en/", 0, 8)) {
                        rel = "data_EN/" + rel.substring(8);
                    }

                    File out = new File(romfs, rel);
                    String rootPath = romfs.getCanonicalPath() + File.separator;
                    if (!out.getCanonicalPath().startsWith(rootPath)) continue;
                    File parent = out.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs())
                        throw new Exception("Não foi possível criar " + parent);
                    try (OutputStream stream = new FileOutputStream(out)) {
                        total += copy(z, stream, MAX_UNPACKED - total);
                    }
                    if (total > MAX_UNPACKED)
                        throw new Exception("O ZIP excede o limite de extração.");
                }
            }
            if (!hasFiles(romfs)) throw new Exception("Não encontrei arquivos RomFS válidos no ZIP.");
            refresh();
            toast("Mod importado: " + mod.getName());
        } catch (Exception e) {
            if (mod != null && mod.exists()) deleteTree(mod);
            error("Falha ao importar ZIP", e);
        }
    }

    private String safeZipName(String raw) {
        if (raw == null) return null;
        String p = raw.replace('\\', '/');
        if (p.startsWith("/") || p.matches("^[A-Za-z]:.*")) return null;
        for (String part : p.split("/")) if (part.equals("..")) return null;
        while (p.startsWith("./")) p = p.substring(2);
        return p.isEmpty() || p.endsWith("/") ? null : p;
    }

    private long copy(InputStream in, OutputStream out, long limit) throws Exception {
        byte[] buf = new byte[65536];
        long total = 0;
        for (int n; (n = in.read(buf)) > 0;) {
            total += n;
            if (total > limit) throw new Exception("O arquivo excede o limite permitido.");
            out.write(buf, 0, n);
        }
        return total;
    }

    private boolean hasFiles(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return false;
        for (File f : children) if (f.isFile() || hasFiles(f)) return true;
        return false;
    }

    private void refresh() {
        if (list == null) return;
        list.removeAllViews();
        File root = modsDir();
        if (!root.isDirectory()) root.mkdirs();
        File[] dirs = root.listFiles();
        List<File> mods = new ArrayList<>();
        if (dirs != null) for (File f : dirs)
            if (f.isDirectory() && !f.getName().startsWith(".") && hasFiles(new File(f, "romfs")))
                mods.add(f);
        Collections.sort(mods, new java.util.Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        // A repeated RomFS path is a conflict only when the file contents differ.
        // Identical copies from multiple mods are harmless and should not be flagged.
        Map<String, Map<String, Set<String>>> owners = new HashMap<>();
        for (File mod : mods) if (!mod.getName().startsWith("_"))
            collectFiles(new File(mod, "romfs"), "", mod, mod.getName(), owners);
        Set<String> conflicts = new HashSet<>();
        for (Map<String, Set<String>> versions : owners.values()) {
            if (versions.size() > 1) {
                for (Set<String> modsWithVersion : versions.values())
                    conflicts.addAll(modsWithVersion);
            }
        }

        int enabled = 0;
        for (File mod : mods) if (!mod.getName().startsWith("_")) enabled++;
        summary.setText(mods.size() + " mod(s) instalado(s) • " + enabled + " ativo(s)"
                + (conflicts.isEmpty() ? "" : " • conflitos em " + conflicts.size() + " mod(s)"));
        if (mods.isEmpty()) {
            TextView empty = text("Nenhum mod encontrado.\nImporte um CPK ou ZIP, ou coloque seus mods manualmente em /mods.", 15, Color.LTGRAY, false);
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            list.addView(empty);
            return;
        }
        for (File mod : mods) addModCard(mod, conflicts.contains(mod.getName()));
    }

    private void collectFiles(File dir, String rel, File modFolder, String mod,
            Map<String, Map<String, Set<String>>> owners) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String p = rel.isEmpty() ? f.getName() : rel + "/" + f.getName();
            if (f.isDirectory()) {
                collectFiles(f, p, modFolder, mod, owners);
            } else {
                String hash;
                try {
                    hash = cachedSha256(f, modFolder, p);
                } catch (Exception ignored) {
                    // If a file cannot be read, conservatively keep a warning for it.
                    hash = "unreadable:" + mod;
                }
                Map<String, Set<String>> versions = owners.get(p);
                if (versions == null) {
                    versions = new HashMap<>();
                    owners.put(p, versions);
                }
                Set<String> modsWithVersion = versions.get(hash);
                if (modsWithVersion == null) {
                    modsWithVersion = new HashSet<>();
                    versions.put(hash, modsWithVersion);
                }
                modsWithVersion.add(mod);
            }
        }
    }

    private String cachedSha256(File file, File modFolder, String relativePath) throws Exception {
        String folderName = modFolder.getName();
        // Toggling a mod only adds/removes the leading underscore. Keep the same cache key.
        if (folderName.startsWith("_")) folderName = folderName.substring(1);
        String key = modFolder.getParentFile().getCanonicalPath() + File.separator
                + folderName + "/" + relativePath;
        long length = file.length();
        long modified = file.lastModified();
        synchronized (HASH_CACHE) {
            HashCacheEntry cached = HASH_CACHE.get(key);
            if (cached != null && cached.length == length && cached.modified == modified)
                return cached.hash;
        }
        String hash = sha256(file);
        synchronized (HASH_CACHE) {
            if (HASH_CACHE.size() >= MAX_HASH_CACHE_ENTRIES) HASH_CACHE.clear();
            HASH_CACHE.put(key, new HashCacheEntry(length, modified, hash));
        }
        return hash;
    }

    private void invalidateHashCache(File modFolder) {
        if (modFolder == null) return;
        String folderName = modFolder.getName();
        if (folderName.startsWith("_")) folderName = folderName.substring(1);
        try {
            String prefix = modFolder.getParentFile().getCanonicalPath() + File.separator
                    + folderName + "/";
            synchronized (HASH_CACHE) {
                java.util.Iterator<String> it = HASH_CACHE.keySet().iterator();
                while (it.hasNext()) if (it.next().startsWith(prefix)) it.remove();
            }
        } catch (Exception ignored) {}
    }

    private String sha256(File file) throws Exception {
        java.security.MessageDigest digest =
                java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (n > 0) digest.update(buffer, 0, n);
            }
        }
        byte[] bytes = digest.digest();
        char[] hex = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            hex[i * 2] = digits[value >>> 4];
            hex[i * 2 + 1] = digits[value & 0x0f];
        }
        return new String(hex);
    }

    private void addModCard(File folder, boolean conflict) {
        boolean active = !folder.getName().startsWith("_");
        String display = active ? folder.getName() : folder.getName().substring(1);
        String version = "", description = "";
        File meta = new File(folder, "mod.json");
        if (meta.isFile()) {
            try (FileInputStream in = new FileInputStream(meta)) {
                byte[] b = new byte[(int)Math.min(meta.length(), 65536)];
                int n = in.read(b);
                if (n > 0) {
                    JSONObject j = new JSONObject(new String(b, 0, n, "UTF-8"));
                    display = j.optString("name", display);
                    version = j.optString("version", "");
                    description = j.optString("description", "");
                }
            } catch (Exception ignored) {}
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setBackground(Ui.shape(this, Ui.CARD, 15, Ui.STROKE));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = dp(9);
        list.addView(card, cp);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(display, 17, Color.WHITE, true);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = text(active ? "ATIVO" : "DESATIVADO", 11,
                active ? Color.rgb(105, 220, 150) : Color.LTGRAY, true);
        row.addView(state);
        card.addView(row);
        String detail = "RomFS" + (version.isEmpty() ? "" : " • v" + version);
        if (!description.isEmpty()) detail += "\n" + description;
        if (conflict) detail += "\n⚠ Conflito: compartilha arquivos com outro mod ativo";
        card.addView(text(detail, 12, conflict ? Color.rgb(255, 190, 95) : Color.rgb(175, 190, 212), false));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button toggle = button(active ? "Desativar" : "Ativar", false);
        Button remove = button("Remover", false);
        final String dialogDisplay = display;
        actions.addView(toggle);
        actions.addView(remove);
        card.addView(actions);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggle(folder); }
        });
        remove.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(ModManagerActivity.this)
                    .setTitle("Remover " + dialogDisplay + "?")
                    .setMessage("Somente os arquivos deste mod serão removidos.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Remover", new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface dialog, int which) {
                            if (deleteTree(folder)) {
                                invalidateHashCache(folder);
                                refresh();
                            } else toast("Não foi possível remover o mod.");
                        }
                    }).show();
            }
        });
    }

    private void toggle(File folder) {
        String n = folder.getName();
        File renamed = new File(folder.getParentFile(), n.startsWith("_") ? n.substring(1) : "_" + n);
        if (renamed.exists()) {
            toast("Já existe uma pasta com esse nome.");
            return;
        }
        if (folder.renameTo(renamed)) refresh();
        else toast("Não foi possível alterar o estado do mod.");
    }

    private boolean deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) if (!deleteTree(c)) return false;
        return !f.exists() || f.delete();
    }

    private void error(String title, Exception e) {
        new AlertDialog.Builder(this).setTitle(title)
            .setMessage(e.getMessage() == null ? e.toString() : e.getMessage())
            .setPositiveButton("OK", null).show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
