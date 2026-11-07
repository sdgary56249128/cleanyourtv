package com.gathin.tvhome;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Add the personal video controls to the existing settings page. */
public final class VideoLauncherSettingsActivity extends LauncherSettingsActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView remote = findViewById(R.id.remoteKeysChoice);
        remote.setVisibility(android.view.View.VISIBLE);
        remote.setText("6 個影音快捷鍵");
        remote.setOnClickListener(v -> showRemoteSlots());
        LinearLayout rows = (LinearLayout) findViewById(R.id.sceneChoices).getParent();
        TextView choice = new TextView(this, null, 0, R.style.SettingsRow);
        choice.setText("影片背景");
        choice.setOnClickListener(v -> startActivity(new Intent(this, VideoSettingsActivity.class)));
        float density = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, (int) (52 * density));
        layout.topMargin = (int) (10 * density);
        rows.addView(choice, rows.indexOfChild(findViewById(R.id.sceneChoices)) + 1, layout);
    }

    private void showRemoteSlots() {
        String[] labels = new String[6];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = KeyLearningActivity.slotName(this, i)
                    + (KeyLearningActivity.slotKey(this, i) == null ? "（尚未記錄）" : "");
        }
        new android.app.AlertDialog.Builder(this).setTitle("6 個影音快捷鍵")
                .setItems(labels, (dialog, slot) -> showSlot(slot))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showSlot(int slot) {
        String title = KeyLearningActivity.slotName(this, slot);
        String key = KeyLearningActivity.slotKey(this, slot);
        String[] actions = {"記錄這顆按鈕", "設定名稱", "短按開啟 App", "長按開啟 App", "跟隨主題預設"};
        new android.app.AlertDialog.Builder(this).setTitle(title)
                .setItems(actions, (dialog, action) -> {
                    if (action == 0) {
                        startActivity(new Intent(this, KeyLearningActivity.class).putExtra("slot", slot));
                    } else if (action == 1) {
                        android.widget.EditText name = new android.widget.EditText(this);
                        name.setText(title); name.setSingleLine(true);
                        new android.app.AlertDialog.Builder(this).setTitle("按鈕名稱").setView(name)
                                .setPositiveButton(android.R.string.ok, (d, which) -> {
                                    String value = name.getText().toString().trim();
                                    if (!value.isEmpty()) getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                                            .edit().putString("video_key_name_" + slot, value).apply();
                                }).setNegativeButton(android.R.string.cancel, null).show();
                    } else if (key == null) {
                        android.widget.Toast.makeText(this, "請先記錄這顆按鈕", android.widget.Toast.LENGTH_LONG).show();
                    } else if (action == 4) {
                        getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).edit()
                                .remove(RemoteKeys.CHOICES_PREFIX + key)
                                .remove(RemoteKeys.CHOICES_PREFIX + "hold:" + key).apply();
                        HomeKeyService.reloadAppKeys(); InputKeyService.reloadAppKeys();
                    } else {
                        showAppPicker(title, RemoteKeys.CHOICES_PREFIX + (action == 3 ? "hold:" : "") + key, true);
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
