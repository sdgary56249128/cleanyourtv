package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** The personal font choice survives theme changes and APK updates. */
final class FontChoice {
    private static final String KEY = "personal_home_font";
    static final String[] VALUES = {"original", "serif", "blackletter"};

    private FontChoice() {}

    static int selected(Context context) {
        String value = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(KEY, VALUES[0]);
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(value)) return i;
        return 0;
    }

    static void select(Context context, int index) {
        SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY, VALUES[index])
                .putLong(Config.SETTINGS_REVISION_KEY, prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1)
                .apply();
    }

    static Typeface face(Context context, int index) {
        if (index == 1) return Typeface.create("serif", Typeface.NORMAL);
        if (index == 2) return context.getResources().getFont(R.font.unifraktur_cook);
        return null;
    }

    static void apply(View root, Typeface face) {
        if (face == null) return;
        if (root instanceof TextView) {
            TextView text = (TextView) root;
            text.setTypeface(face);
            text.setLetterSpacing(0);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i), face);
        }
    }
}
