package com.gathin.tvhome;

import android.app.AlertDialog;
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
