package io.github.p3pandroid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The launcher's look: colors, rounded cards, segmented choices and buttons, built in code (the app has no layout XML). */
final class Ui {
    static final int BG_TOP = Color.rgb(11, 19, 42), BG_BOTTOM = Color.rgb(3, 6, 16);
    static final int CARD = Color.rgb(15, 26, 51), STROKE = Color.rgb(34, 53, 92);
    static final int ACCENT = Color.rgb(52, 133, 240), ACCENT_DARK = Color.rgb(30, 92, 196);
    static final int TEXT = Color.rgb(236, 242, 255), MUTED = Color.rgb(148, 164, 194), PILL = Color.rgb(24, 40, 76);

    private Ui() {}

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable shape(Context c, int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    /** `base` with a light ripple on press, clipped to the same rounded corners. */
    static Drawable pressable(Context c, Drawable base, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(70, 255, 255, 255)), base,
                shape(c, Color.WHITE, radiusDp, 0));
    }

    static Drawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {BG_TOP, BG_BOTTOM});
    }

    static Typeface medium() { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }

    /** A rounded panel for one section; the caller adds its rows. */
    static LinearLayout card(Context c, LinearLayout parent) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(shape(c, CARD, 16, STROKE));
        int p = dp(c, 16);
        card.setPadding(p, dp(c, 14), p, dp(c, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 14);
        parent.addView(card, lp);
        return card;
    }

    static TextView title(Context c, String text) {
        TextView h = new TextView(c);
        h.setText(text.toUpperCase(java.util.Locale.getDefault()));
        h.setTextColor(ACCENT);
        h.setTextSize(12);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setLetterSpacing(0.12f);
        h.setPadding(0, 0, 0, dp(c, 8));
        return h;
    }

    static TextView hint(Context c, String text) {
        TextView h = new TextView(c);
        h.setText(text);
        h.setTextColor(MUTED);
        h.setTextSize(12.5f);
        h.setLineSpacing(0, 1.1f);
        h.setPadding(0, 0, 0, dp(c, 10));
        return h;
    }

    static TextView label(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(TEXT);
        t.setTextSize(15.5f);
        return t;
    }

    /** One choice of a segmented control; style it with setSelected(). */
    static TextView pill(Context c, String text) {
        TextView p = new TextView(c);
        p.setText(text);
        p.setGravity(Gravity.CENTER);
        p.setMaxLines(2);
        p.setTextSize(13.5f);
        p.setClickable(true);
        p.setMinHeight(dp(c, 44));
        p.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8));
        setSelected(c, p, false);
        return p;
    }

    static void setSelected(Context c, TextView p, boolean on) {
        p.setBackground(pressable(c, shape(c, on ? ACCENT : PILL, 12, on ? 0 : STROKE), 12));
        p.setTextColor(on ? Color.WHITE : MUTED);
        p.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    /** A small outlined button for a row's action. */
    static Button action(Context c, String text, View.OnClickListener click) {
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(Color.rgb(160, 205, 255));
        b.setTypeface(medium());
        b.setMinWidth(dp(c, 96));
        b.setMinHeight(dp(c, 42));
        b.setMinimumHeight(dp(c, 42));
        b.setStateListAnimator(null);
        b.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        b.setBackground(pressable(c, shape(c, PILL, 12, STROKE), 12));
        b.setOnClickListener(click);
        return b;
    }

    /** The big call to action at the bottom of the screen. */
    static Button primary(Context c) {
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {ACCENT, ACCENT_DARK});
        g.setCornerRadius(dp(c, 16));
        b.setBackground(pressable(c, g, 16));
        return b;
    }
}
