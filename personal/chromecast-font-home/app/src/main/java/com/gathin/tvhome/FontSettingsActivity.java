package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Add a font picker and real typeface previews to the existing settings. */
public final class FontSettingsActivity extends LauncherSettingsActivity {
    private TextView fontChoice;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        fontChoice = new TextView(this, null, 0, R.style.SettingsRow);
        fontChoice.setOnClickListener(view -> showFontPicker());
        fontChoice.setText(getString(R.string.font_choice, names()[FontChoice.selected(this)]));
        LinearLayout rows = (LinearLayout) findViewById(R.id.sceneChoices).getParent();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Math.round(18 * getResources().getDisplayMetrics().density);
        fontChoice.setMinHeight(Math.round(52 * getResources().getDisplayMetrics().density));
        rows.addView(fontChoice, rows.indexOfChild(findViewById(R.id.wallpaperChoice)), params);
        addRow(rows, R.string.weekly_wallpapers, this::showWeeklyPicker);
        addRow(rows, R.string.system_info_title, () -> startActivity(new Intent(this, SystemInfoActivity.class)));
        addRow(rows, R.string.profile_title, this::showBackup);
    }

    private void addRow(LinearLayout rows, int label, Runnable action) {
        TextView row = new TextView(this, null, 0, R.style.SettingsRow);
        row.setText(label);
        row.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,
                Math.round(52 * getResources().getDisplayMetrics().density));
        params.topMargin = Math.round(10 * getResources().getDisplayMetrics().density);
        rows.addView(row, rows.indexOfChild(findViewById(R.id.wallpaperChoice)), params);
    }

    private void showWeeklyPicker() {
        String mode = WeeklyWallpapers.mode(this);
        int selected = java.util.Arrays.asList(WeeklyWallpapers.MODES).indexOf(mode);
        new AlertDialog.Builder(this).setTitle(R.string.weekly_wallpapers)
                .setSingleChoiceItems(R.array.weekly_wallpaper_modes, Math.max(0, selected), (dialog, which) -> {
                    android.content.SharedPreferences prefs = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
                    prefs.edit().remove(Config.WALLPAPER_CHOICE_KEY).putString(WeeklyWallpapers.KEY, WeeklyWallpapers.MODES[which])
                            .putLong(Config.SETTINGS_REVISION_KEY, prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
                    setResult(RESULT_OK);
                    dialog.dismiss();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showBackup() {
        String commit = getSharedPreferences("git_profile", MODE_PRIVATE).getString("commit", "—");
        new AlertDialog.Builder(this).setTitle(R.string.profile_title)
                .setItems(R.array.profile_actions, (dialog, which) -> {
                    try {
                        if (which == 0) HomeProfile.backup(this);
                        else if (which == 1) {
                            new AlertDialog.Builder(this).setMessage(R.string.confirm_profile_restore)
                                    .setPositiveButton(android.R.string.ok, (d, button) -> restoreBackup())
                                    .setNegativeButton(android.R.string.cancel, null).show();
                            return;
                        } else {
                            new AlertDialog.Builder(this).setMessage(getString(R.string.git_profile_help, commit))
                                    .setPositiveButton(android.R.string.ok, null).show();
                            return;
                        }
                        android.widget.Toast.makeText(this, R.string.profile_saved, android.widget.Toast.LENGTH_LONG).show();
                    } catch (Exception failure) { showFailure(failure); }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void restoreBackup() {
        try {
            HomeProfile.restoreLocal(this);
            fontChoice.setText(getString(R.string.font_choice, names()[FontChoice.selected(this)]));
            HomeKeyService.reloadProfile();
            InputKeyService.reloadProfile();
            setResult(RESULT_OK);
            finish();
        } catch (Exception failure) { showFailure(failure); }
    }

    private void showFailure(Exception failure) {
        new AlertDialog.Builder(this).setMessage(getString(R.string.profile_failed, failure.getMessage()))
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private String[] names() { return getResources().getStringArray(R.array.personal_font_names); }

    private void showFontPicker() {
        String[] labels = names();
        int[] pending = {FontChoice.selected(this)};
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setPadding(padding, padding, padding, padding / 2);
        TextView heading = new TextView(this);
        heading.setText(R.string.font_picker_title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(20);
        title.addView(heading);
        TextView preview = new TextView(this);
        preview.setText(R.string.font_preview);
        preview.setTextColor(Color.WHITE);
        preview.setTextSize(26);
        preview.setPadding(0, padding / 2, 0, 0);
        showPreview(preview, pending[0]);
        title.addView(preview);
        new AlertDialog.Builder(this).setCustomTitle(title)
                .setSingleChoiceItems(labels, pending[0], (dialog, which) -> {
                    pending[0] = which;
                    showPreview(preview, which);
                }).setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    FontChoice.select(this, pending[0]);
                    fontChoice.setText(getString(R.string.font_choice, labels[pending[0]]));
                    setResult(RESULT_OK);
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showPreview(TextView preview, int index) {
        Typeface face = FontChoice.face(this, index);
        preview.setTypeface(face == null ? UiFont.get(this, R.string.font_medium) : face);
    }
}
