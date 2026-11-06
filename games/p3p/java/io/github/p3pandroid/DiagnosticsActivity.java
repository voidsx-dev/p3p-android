package io.github.p3pandroid;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Read-only device and game diagnostics; it does not change game settings. */
public final class DiagnosticsActivity extends Activity {
    private static final int BG = Color.rgb(7, 14, 31);
    private static final int PANEL = Color.rgb(17, 29, 51);
    private TextView gpuValue;
    private String report = "";

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        build();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(20));
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("DIAGNÓSTICO", 23, Color.WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button back = new Button(this);
        back.setAllCaps(false);
        back.setText("Voltar");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        top.addView(back);
        root.addView(top);

        TextView intro = text("Informações para identificar problemas de compatibilidade e desempenho. Esta tela não altera as configurações.",
                13, Color.LTGRAY, false);
        intro.setPadding(0, dp(8), 0, dp(12));
        root.addView(intro);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);

        StringBuilder out = new StringBuilder();
        section(content, out, "DISPOSITIVO");
        item(content, out, "Modelo", Build.MANUFACTURER + " " + Build.MODEL);
        item(content, out, "SoC / Hardware", Build.VERSION.SDK_INT >= 31 && !Build.SOC_MODEL.isEmpty()
                ? Build.SOC_MODEL : Build.HARDWARE);
        item(content, out, "Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        item(content, out, "Arquitetura", join(Build.SUPPORTED_ABIS));
        gpuValue = text("Consultando GPU…", 13, Color.rgb(175, 195, 220), false);
        addItem(content, "GPU (OpenGL ES)", gpuValue);

        section(content, out, "MEMÓRIA");
        ActivityManager am = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        item(content, out, "RAM total", formatBytes(mi.totalMem));
        item(content, out, "RAM disponível", formatBytes(mi.availMem));
        item(content, out, "Low memory", mi.lowMemory ? "Sim" : "Não");

        section(content, out, "P3P ANDROID");
        String appVersion = "desconhecida";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            appVersion = pi.versionName + " (" + pi.getLongVersionCode() + ")";
        } catch (Exception ignored) {}
        item(content, out, "Versão do app", appVersion);
        File data = Launcher.dataDir();
        item(content, out, "Pasta de dados", data.getAbsolutePath());
        item(content, out, "Permissão de arquivos", Build.VERSION.SDK_INT < 30
                || Environment.isExternalStorageManager() ? "Concedida" : "Não concedida");
        item(content, out, "Exefs", new File(data, "exefs/main").isFile() ? "Encontrado" : "Não encontrado");
        item(content, out, "Recompiled", new File(data, "analysis/main.bin").isFile() ? "Encontrado" : "Não encontrado");
        item(content, out, "RomFS extraído", new File(data, "extract/romfs").isDirectory() ? "Encontrado" : "Não encontrado");
        File mods = new File(data, "mods");
        item(content, out, "Pasta de mods", mods.isDirectory() ? "Encontrada" : "Não encontrada");

        section(content, out, "CONFIGURAÇÃO ATUAL");
        java.util.Map<String, String> cfg = Launcher.readConfig(new File(data, "config.txt"));
        item(content, out, "Limite de FPS", cfg.getOrDefault("NX_FPS_CAP", "padrão do runtime"));
        item(content, out, "Contador FPS", "1".equals(cfg.get("NX_SHOW_FPS")) ? "Ativado" : "Desativado");
        item(content, out, "Resolução", cfg.getOrDefault("RESOLUTION", "padrão"));
        item(content, out, "Gráficos", cfg.getOrDefault("GRAPHICS", "padrão"));

        File storage = data.exists() ? data : Environment.getExternalStorageDirectory();
        StatFs fs = new StatFs(storage.getAbsolutePath());
        item(content, out, "Espaço livre", formatBytes(fs.getAvailableBytes()));

        report = out.toString();
        Button copy = new Button(this);
        copy.setAllCaps(false);
        copy.setText("Copiar diagnóstico");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String full = report + "\nGPU (OpenGL ES): " + gpuValue.getText();
                ClipboardManager cb = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if (cb != null) {
                    cb.setPrimaryClip(ClipData.newPlainText("P3P diagnostics", full));
                    Toast.makeText(DiagnosticsActivity.this, "Diagnóstico copiado", Toast.LENGTH_SHORT).show();
                }
            }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(12);
        root.addView(copy, bp);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        // GLES reports the physical renderer name on most Android devices. It is informational;
        // the game itself uses Vulkan, so this value is not presented as a Vulkan benchmark.
        GLSurfaceView probe = new GLSurfaceView(this);
        probe.setEGLContextClientVersion(2);
        probe.setRenderer(new GLSurfaceView.Renderer() {
            @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                final String vendor = gl.glGetString(GL10.GL_VENDOR);
                final String renderer = gl.glGetString(GL10.GL_RENDERER);
                final String version = gl.glGetString(GL10.GL_VERSION);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        gpuValue.setText((vendor == null ? "" : vendor + " • ") +
                                (renderer == null ? "Não identificado" : renderer) +
                                (version == null ? "" : "\n" + version));
                    }
                });
            }
            @Override public void onSurfaceChanged(GL10 gl, int width, int height) {}
            @Override public void onDrawFrame(GL10 gl) {}
        });
        root.addView(probe, new LinearLayout.LayoutParams(1, 1));
    }

    private void section(LinearLayout root, StringBuilder out, String title) {
        TextView h = text(title, 12, Color.rgb(110, 175, 255), true);
        h.setPadding(0, dp(16), 0, dp(6));
        root.addView(h);
        out.append("\n").append(title).append("\n");
    }

    private void item(LinearLayout root, StringBuilder out, String key, String value) {
        String safe = value == null || value.isEmpty() ? "—" : value;
        addItem(root, key, text(safe, 13, Color.rgb(175, 195, 220), false));
        out.append(key).append(": ").append(safe).append("\n");
    }

    private void addItem(LinearLayout root, String key, TextView value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(5);
        root.addView(card, p);
        card.addView(text(key, 11, Color.LTGRAY, true));
        value.setPadding(0, dp(2), 0, 0);
        card.addView(value);
    }

    private String join(String[] values) {
        if (values == null || values.length == 0) return "—";
        StringBuilder b = new StringBuilder();
        for (String s : values) {
            if (b.length() > 0) b.append(", ");
            b.append(s);
        }
        return b.toString();
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "—";
        if (bytes >= (1L << 30)) return String.format(Locale.US, "%.2f GB", bytes / (double)(1L << 30));
        return String.format(Locale.US, "%.0f MB", bytes / (double)(1L << 20));
    }
}
