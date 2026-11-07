package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** A foreground-only learning session, handled before configured app launches. */
public final class KeyLearningActivity extends Activity {
    private static KeyLearningActivity active;
    private int slot;

    static String slotName(Context context, int slot) {
        String fallback = slot == 0 ? "YouTube" : slot == 1 ? "myVideo" : "快捷鍵 " + (slot + 1);
        return context.getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                .getString("video_key_name_" + slot, fallback);
    }

    static String slotKey(Context context, int slot) {
        String fallback = slot == 0 ? "scan:485" : slot == 1 ? "scan:492" : null;
        return context.getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                .getString("video_key_slot_" + slot, fallback);
    }

    static boolean isAppKey(int code, int scan) {
        switch (code) {
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_TV:
            case KeyEvent.KEYCODE_TV_INPUT:
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_ASSIST:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
                return false;
            default:
                return code >= KeyEvent.KEYCODE_BUTTON_1 && code <= KeyEvent.KEYCODE_BUTTON_16
                        || scan >= 485 && scan <= 511;
        }
    }

    static boolean matchesSlot(Context context, KeyEvent event) {
        String scan = "scan:" + event.getScanCode();
        String code = "" + event.getKeyCode();
        for (int i = 0; i < 6; i++) {
            String key = slotKey(context, i);
            if (scan.equals(key) || code.equals(key)) return true;
        }
        return false;
    }

    static boolean capture(KeyEvent event) {
        KeyLearningActivity waiting = active;
        if (waiting == null || !isAppKey(event.getKeyCode(), event.getScanCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            String key = event.getScanCode() > 0 ? "scan:" + event.getScanCode() : "" + event.getKeyCode();
            waiting.accept(key);
        }
        return true;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        slot = getIntent().getIntExtra("slot", -1);
        if (slot < 0 || slot >= 6) { finish(); return; }
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(80, 80, 80, 80); rows.setBackgroundColor(0xff101419);
        TextView text = new TextView(this);
        text.setText("請按下「" + slotName(this, slot) + "」\n\n只記錄影音快捷鍵。按返回可取消。");
        text.setTextSize(24); text.setTextColor(0xffffffff); rows.addView(text);
        setContentView(rows);
    }

    private void accept(String key) {
        for (int i = 0; i < 6; i++) {
            if (i != slot && key.equals(slotKey(this, i))) {
                Toast.makeText(this, "這顆已在「" + slotName(this, i) + "」設定，請按其他按鈕", Toast.LENGTH_LONG).show();
                return;
            }
        }
        SharedPreferences choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        String previous = slotKey(this, slot);
        SharedPreferences.Editor edit = choices.edit().putString("video_key_slot_" + slot, key);
        if (previous != null && !previous.equals(key)) {
            // Moving a slot must not leave a hidden mapping active on its previous button.
            edit.remove(RemoteKeys.CHOICES_PREFIX + previous).remove(RemoteKeys.CHOICES_PREFIX + "hold:" + previous);
        }
        edit.apply();
        HomeKeyService.reloadAppKeys(); InputKeyService.reloadAppKeys();
        Toast.makeText(this, "已記錄 " + key, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override protected void onResume() { super.onResume(); active = this; }
    @Override protected void onPause() { if (active == this) active = null; super.onPause(); }
    @Override protected void onDestroy() { if (active == this) active = null; super.onDestroy(); }
}
