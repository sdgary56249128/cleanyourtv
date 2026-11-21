package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;

final class DepthSettings {
    static final String KEY = "wallpaper_depth";
    static final String[] VALUES = {"default", "off", "subtle", "standard", "planet"};
    private DepthSettings() {}

    static String saved(Context context) {
        String value = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(KEY, "default");
        return Arrays.asList(VALUES).contains(value) ? value : "default";
    }

    static String mode(Context context) {
        String saved = saved(context);
        return "default".equals(saved) ? "standard" : saved;
    }

    static void add(Activity activity, LinearLayout rows, int index) {
        TextView row = new TextView(activity, null, 0, R.style.SettingsRow);
        row.setId(R.id.personalDepthChoice);
        row.setText(R.string.depth_wallpaper);
        row.setOnClickListener(view -> new AlertDialog.Builder(activity).setTitle(R.string.depth_wallpaper)
                .setMessage(null)
                .setSingleChoiceItems(R.array.depth_modes, Arrays.asList(VALUES).indexOf(saved(activity)), (dialog, which) -> {
                    SharedPreferences prefs = activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
                    SharedPreferences.Editor edit = prefs.edit();
                    if (which == 0) edit.remove(KEY); else edit.putString(KEY, VALUES[which]);
                    edit.putLong(Config.SETTINGS_REVISION_KEY, prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
                    activity.setResult(Activity.RESULT_OK);
                    dialog.dismiss();
                }).setNegativeButton(android.R.string.cancel, null).show());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,
                Math.round(52 * activity.getResources().getDisplayMetrics().density));
        params.topMargin = Math.round(10 * activity.getResources().getDisplayMetrics().density);
        rows.addView(row, index, params);
    }
}
