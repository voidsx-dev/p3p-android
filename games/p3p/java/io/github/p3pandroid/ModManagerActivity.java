package io.github.p3pandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
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
    private static final int BG = Color.rgb(7, 14, 31);
    private static final int PANEL = Color.rgb(17, 29, 51);
    private static final int BLUE = Color.rgb(54, 135, 245);
    private LinearLayout list;
    private TextView summary;

    private File modsDir() { return new File(Launcher.dataDir(), "mods"); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        build();
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
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

    private Button button(String label) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        return b;
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(20));
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("GERENCIADOR DE MODS", 23, Color.WHITE, true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button back = button("Voltar");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        top.addView(back);
        root.addView(top);

        summary = text("", 13, Color.LTGRAY, false);
        summary.setPadding(0, dp(8), 0, dp(12));
        root.addView(summary);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cpk = button("Importar CPK");
        Button zip = button("Importar ZIP");
        actions.addView(cpk, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(zip, new LinearLayout.LayoutParams(0, -2, 1));
        cpk.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pick(PICK_CPK); }
        });
        zip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pick(PICK_ZIP); }
        });
        root.addView(actions);

        TextView note = text("Mods são lidos de Persona 3 Portable/mods. Pastas iniciadas por _ ficam desativadas. Reinicie o jogo para aplicar alterações.",
                12, Color.rgb(170, 190, 215), false);
        note.setPadding(0, dp(8), 0, dp(12));
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
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
                destination.setText(filename);
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
        try {
            // First pass: locate a romfs/ directory if the archive contains one.
            String prefix = null;
            int entries = 0;
            try (ZipInputStream z = new ZipInputStream(getContentResolver().openInputStream(uri))) {
                for (ZipEntry e; (e = z.getNextEntry()) != null;) {
                    if (++entries > MAX_ENTRIES) throw new Exception("O ZIP contém arquivos demais.");
                    String p = safeZipName(e.getName());
                    if (p == null) continue;
                    int at = p.toLowerCase(Locale.ROOT).indexOf("romfs/");
                    if (at >= 0) {
                        String candidate = p.substring(0, at);
                        if (prefix == null || candidate.length() < prefix.length()) prefix = candidate;
                    }
                }
            }
            File mod = uniqueMod(name);
            File romfs = new File(mod, "romfs");
            if (!romfs.mkdirs()) throw new Exception("Não foi possível criar a pasta romfs.");
            long total = 0;
            entries = 0;
            try (ZipInputStream z = new ZipInputStream(getContentResolver().openInputStream(uri))) {
                for (ZipEntry e; (e = z.getNextEntry()) != null;) {
                    if (e.isDirectory()) continue;
                    if (++entries > MAX_ENTRIES) throw new Exception("O ZIP contém arquivos demais.");
                    String path = safeZipName(e.getName());
                    if (path == null) continue;
                    String rel;
                    if (prefix != null) {
                        int at = path.toLowerCase(Locale.ROOT).indexOf("romfs/");
                        if (at < 0) continue;
                        rel = path.substring(at + 6);
                    } else {
                        rel = path;
                    }
                    if (rel.isEmpty()) continue;
                    File out = new File(romfs, rel);
                    String rootPath = romfs.getCanonicalPath() + File.separator;
                    if (!out.getCanonicalPath().startsWith(rootPath)) continue;
                    File parent = out.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new Exception("Não foi possível criar " + parent);
                    try (OutputStream stream = new FileOutputStream(out)) {
                        total += copy(z, stream, MAX_UNPACKED - total);
                    }
                    if (total > MAX_UNPACKED) throw new Exception("O ZIP excede o limite de extração.");
                }
            }
            if (!hasFiles(romfs)) {
                deleteTree(mod);
                throw new Exception("Não encontrei arquivos RomFS válidos no ZIP.");
            }
            refresh();
            toast("Mod importado: " + mod.getName());
        } catch (Exception e) { error("Falha ao importar ZIP", e); }
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
        Map<String, List<String>> owners = new HashMap<>();
        for (File mod : mods) if (!mod.getName().startsWith("_"))
            collectFiles(new File(mod, "romfs"), "", mod.getName(), owners);
        Set<String> conflicts = new HashSet<>();
        for (Map.Entry<String, List<String>> e : owners.entrySet())
            if (e.getValue().size() > 1) conflicts.addAll(e.getValue());

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

    private void collectFiles(File dir, String rel, String mod, Map<String, List<String>> owners) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String p = rel.isEmpty() ? f.getName() : rel + "/" + f.getName();
            if (f.isDirectory()) collectFiles(f, p, mod, owners);
            else {
                List<String> list = owners.get(p);
                if (list == null) {
                    list = new ArrayList<>();
                    owners.put(p, list);
                }
                list.add(mod);
            }
        }
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
        card.setBackgroundColor(PANEL);
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
        Button toggle = button(active ? "Desativar" : "Ativar");
        Button remove = button("Remover");
        actions.addView(toggle);
        actions.addView(remove);
        card.addView(actions);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggle(folder); }
        });
        remove.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(ModManagerActivity.this)
                    .setTitle("Remover " + display + "?")
                    .setMessage("Somente os arquivos deste mod serão removidos.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Remover", new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface dialog, int which) {
                            if (deleteTree(folder)) refresh();
                            else toast("Não foi possível remover o mod.");
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
