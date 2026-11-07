package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ResolveInfo;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;

/** Local, silent video behind Home's app tiles. Opt-in and off by default. */
public final class VideoSettingsActivity extends Activity {
    private static final int PICK_VIDEO = 40;
    private TextView toggle;
    private TextView choose;
    private TextView status;

    static File videoFile(Context context) {
        File directory = context.getExternalFilesDir(null);
        if (directory == null) directory = context.getFilesDir();
        return new File(directory, "background.mp4");
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (40 * getResources().getDisplayMetrics().density);
        rows.setPadding(padding, padding, padding, padding);
        rows.setBackgroundColor(0xff101419);
        setContentView(rows);
        TextView title = new TextView(this);
        title.setText("影片背景"); title.setTextSize(30); title.setTextColor(Color.WHITE);
        rows.addView(title);
        status = new TextView(this); status.setTextSize(18); status.setTextColor(0xffaab4be); rows.addView(status);
        toggle = addButton(rows, "");
        toggle.setOnClickListener(v -> {
            if (!videoFile(this).isFile()) {
                Toast.makeText(this, "請先選擇 MP4 影片", Toast.LENGTH_LONG).show();
                return;
            }
            boolean enabled = getSharedPreferences("video_background", MODE_PRIVATE).getBoolean("enabled", false);
            getSharedPreferences("video_background", MODE_PRIVATE).edit().putBoolean("enabled", !enabled).apply();
            refresh();
        });
        choose = addButton(rows, "選擇 MP4 影片（最多 128 MB）");
        choose.setOnClickListener(v -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/mp4")
                    .addCategory(Intent.CATEGORY_OPENABLE);
            ResolveInfo handler = getPackageManager().resolveActivity(picker, 0);
            if (handler == null || handler.activityInfo.name.contains("DocumentsStub")) {
                new AlertDialog.Builder(this).setTitle("匯入影片")
                        .setMessage("這台電視沒有影片檔案選擇器。可由電腦匯入 128 MB 以內的 MP4，再使用此頁開關影片背景。")
                        .setPositiveButton(android.R.string.ok, null).show();
                return;
            }
            try {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/mp4")
                        .addCategory(Intent.CATEGORY_OPENABLE), PICK_VIDEO);
            } catch (ActivityNotFoundException missing) {
                Toast.makeText(this, "這台電視沒有檔案選擇器，請由 Mac 匯入影片", Toast.LENGTH_LONG).show();
            }
        });
        TextView home = addButton(rows, "返回首頁");
        home.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        refresh(); toggle.requestFocus();
    }

    private TextView addButton(LinearLayout rows, String label) {
        TextView button = new TextView(this, null, 0, R.style.SettingsRow);
        button.setText(label);
        float density = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, (int) (52 * density));
        layout.topMargin = (int) (12 * density);
        rows.addView(button, layout);
        return button;
    }

    private void refresh() {
        boolean enabled = getSharedPreferences("video_background", MODE_PRIVATE).getBoolean("enabled", false);
        toggle.setText(enabled ? "停用影片背景" : "啟用影片背景");
        File file = videoFile(this);
        status.setText((enabled ? "目前開啟" : "目前關閉") + " · 靜音循環，進入其他 App 時停止\n"
                + (file.isFile() ? "已匯入影片（" + file.length() / 1024 + " KB）" : "尚未匯入影片"));
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_VIDEO || result != RESULT_OK || data == null || data.getData() == null) return;
        choose.setEnabled(false); toggle.setEnabled(false); status.setText("正在匯入影片…");
        new Thread(() -> {
            String message;
            try (InputStream input = getContentResolver().openInputStream(data.getData())) {
                if (input == null) throw new java.io.IOException("No input");
                VideoFiles.replace(input, videoFile(this));
                message = "影片已匯入";
            } catch (Exception error) { message = "匯入失敗，請選擇 128 MB 以內的本機 MP4"; }
            final String notice = message;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                choose.setEnabled(true); toggle.setEnabled(true); refresh();
                Toast.makeText(this, notice, Toast.LENGTH_LONG).show();
            });
        }, "video-import").start();
    }
}
