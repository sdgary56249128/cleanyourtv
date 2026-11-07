package com.gathin.tvhome;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;

/** Personal opt-in video layer. The reference launcher continues to own navigation and still photos. */
public final class VideoHomeActivity extends MainActivity implements TextureView.SurfaceTextureListener {
    private TextureView video;
    private View shade;
    private MediaPlayer player;
    private Surface surface;
    private boolean resumed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = findViewById(R.id.root);
        video = new TextureView(this);
        video.setFocusable(false);
        video.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        video.setSurfaceTextureListener(this);
        video.setVisibility(View.GONE);
        root.addView(video, 1, new FrameLayout.LayoutParams(-1, -1));
        shade = new View(this);
        shade.setBackgroundColor((Config.load(this).wallpaperDim * 255 / 100) << 24);
        shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        shade.setVisibility(View.GONE);
        root.addView(shade, 2, new FrameLayout.LayoutParams(-1, -1));
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        shade.setBackgroundColor((Config.load(this).wallpaperDim * 255 / 100) << 24);
        File file = VideoSettingsActivity.videoFile(this);
        boolean enabled = getSharedPreferences("video_background", MODE_PRIVATE).getBoolean("enabled", false);
        if (enabled && file.isFile() && file.length() > 0) {
            video.setVisibility(View.VISIBLE);
            if (video.isAvailable()) play(video.getSurfaceTexture());
        } else hideVideo();
    }

    private void play(SurfaceTexture texture) {
        releasePlayer();
        if (!resumed) return;
        MediaPlayer next = new MediaPlayer();
        player = next;
        surface = new Surface(texture);
        try {
            next.setDataSource(VideoSettingsActivity.videoFile(this).getAbsolutePath());
            next.setSurface(surface);
            next.setVolume(0f, 0f);
            next.setLooping(true);
            next.setOnVideoSizeChangedListener((p, width, height) -> crop(width, height));
            next.setOnPreparedListener(p -> {
                if (player == p && resumed) {
                    p.start();
                    shade.setVisibility(View.VISIBLE);
                }
            });
            next.setOnErrorListener((p, what, extra) -> {
                if (player == p) failed();
                return true;
            });
            next.prepareAsync();
        } catch (IOException | RuntimeException error) { failed(); }
    }

    private void crop(int width, int height) {
        if (width <= 0 || height <= 0 || video.getWidth() == 0 || video.getHeight() == 0) return;
        float scale = Math.max((float) video.getWidth() / width, (float) video.getHeight() / height);
        Matrix matrix = new Matrix();
        matrix.setScale(scale * width / video.getWidth(), scale * height / video.getHeight(),
                video.getWidth() / 2f, video.getHeight() / 2f);
        video.setTransform(matrix);
    }

    private void failed() {
        hideVideo();
        Toast.makeText(this, "影片無法播放，請重新選擇 MP4", Toast.LENGTH_LONG).show();
    }

    private void hideVideo() {
        releasePlayer();
        video.setVisibility(View.GONE);
        shade.setVisibility(View.GONE);
    }

    private void releasePlayer() {
        if (player != null) {
            MediaPlayer old = player;
            player = null;
            old.release();
        }
        if (surface != null) { surface.release(); surface = null; }
    }

    @Override protected void onPause() {
        resumed = false;
        hideVideo();
        super.onPause();
    }
    @Override protected void onDestroy() { releasePlayer(); super.onDestroy(); }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture t, int w, int h) { play(t); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t, int w, int h) {
        if (player != null) crop(player.getVideoWidth(), player.getVideoHeight());
    }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t) { releasePlayer(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture t) { }
}
