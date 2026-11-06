package io.github.p3pandroid;

import android.app.AlertDialog;
import android.app.NativeActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.system.Os;

/**
 * The game (libp3p.so), in its own process (":game", AndroidManifest.xml). Two jobs:
 *  - The runtime ends its process when this activity is destroyed (platform_android.cpp: _exit(0)). In the launcher's
 *    own process that took the launcher with it, so the game has a process of its own: leaving it lands on the
 *    launcher, which keeps running.
 *  - Back asks "Quit the game?" first; Yes closes the game and shows the launcher again, No goes on playing.
 * The launcher cannot hand over the game file as an open descriptor to another process (an Intent may not carry one),
 * nor as environment variables, so it passes the file's Uri and name and this activity opens the file itself, in the
 * same way (the launcher holds a persistent read grant) and sets the NX_* variables the runtime reads (game_image.cpp).
 * Started without extras (the data folder made on a PC, or adb), it sets nothing.
 */
public class GameActivity extends NativeActivity {
    static final String EXTRA_URI = "game_uri", EXTRA_NAME = "game_name";

    private AlertDialog confirm;

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
    }

    @Override
    public void onBackPressed() {
        if (confirm != null && confirm.isShowing()) return;
        confirm = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Lang.t("¿Salir del juego?", "Quit the game?"))
                .setMessage(Lang.t("Volverás a la pantalla de inicio.", "You will go back to the home screen."))
                .setPositiveButton(Lang.t("Sí", "Yes"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        finish();
                    }
                })
                .setNegativeButton(Lang.t("No", "No"), null)
                .create();
        confirm.show();
    }

    @Override
    protected void onDestroy() {
        if (confirm != null && confirm.isShowing()) confirm.dismiss();
        super.onDestroy();
    }
}
