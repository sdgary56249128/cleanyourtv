package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.lang.ref.WeakReference;

/** Finite, navigation-driven transforms of the existing background texture. */
final class WallpaperDepth {
    private static WeakReference<WallpaperDepth> current = new WeakReference<>(null);
    private final Activity activity;
    private final FrameLayout root;
    private final RecyclerView grid;
    private ValueAnimator animator;
    private float progress;
    private boolean active, queued;
    private String mode = "off";
    private long frames;
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener = (old, next) -> schedule();
    private final View.OnLayoutChangeListener layoutListener = (v, l, t, r, b, ol, ot, or, ob) -> schedule();
    private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override public void onScrolled(RecyclerView view, int dx, int dy) { schedule(); }
    };
    private final Runnable update = () -> {
        queued = false;
        if (active) move(target(), true);
    };

    WallpaperDepth(Activity activity, FrameLayout root, RecyclerView grid) {
        this.activity = activity; this.root = root; this.grid = grid;
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
        root.addOnLayoutChangeListener(layoutListener);
        grid.addOnScrollListener(scrollListener);
        current = new WeakReference<>(this);
    }

    void start() {
        active = true;
        Config config = Config.load(activity);
        mode = config.reducedMotion || !config.animations ? "off" : DepthSettings.mode(activity);
        move(target(), false);
        schedule();
    }

    void stop() {
        active = false;
        root.removeCallbacks(update); queued = false;
        cancel();
    }

    void close() {
        stop();
        if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
        root.removeOnLayoutChangeListener(layoutListener);
        grid.removeOnScrollListener(scrollListener);
        if (current.get() == this) current.clear();
    }

    private void cancel() {
        if (animator != null) { animator.cancel(); animator = null; }
    }

    private void schedule() {
        if (!active || queued) return;
        queued = true;
        root.postOnAnimation(update);
    }

    private int position() {
        View focus = root.findFocus();
        RecyclerView.ViewHolder holder = focus == null ? null : grid.findContainingViewHolder(focus);
        return holder == null ? -1 : holder.getBindingAdapterPosition();
    }

    private float target() {
        RecyclerView.LayoutManager layout = grid.getLayoutManager();
        if (!(layout instanceof GridLayoutManager)) return 0f;
        GridLayoutManager manager = (GridLayoutManager) layout;
        if (manager.getOrientation() != RecyclerView.VERTICAL) return 0f;
        return DepthGeometry.progress(position(), grid.getAdapter() == null ? 0 : grid.getAdapter().getItemCount(), manager.getSpanCount());
    }

    private void move(float next, boolean animate) {
        next = DepthGeometry.clamp(next);
        if (Math.abs(next - progress) < .0001f) { cancel(); progress = next; apply(); return; }
        cancel();
        if (!animate || "off".equals(mode) || !ValueAnimator.areAnimatorsEnabled()) {
            progress = next; apply(); return;
        }
        animator = ValueAnimator.ofFloat(progress, next);
        animator.setDuration(320);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> { progress = (float) value.getAnimatedValue(); apply(); });
        animator.start();
    }

    private void apply() {
        float scale = DepthGeometry.scale(progress, mode);
        float translation = DepthGeometry.translation(progress, root.getHeight(), mode);
        for (String tag : new String[]{"personal.wallpaper", "personal.video"}) {
            View layer = root.findViewWithTag(tag);
            if (layer == null) continue;
            layer.setScaleX(scale); layer.setScaleY(scale);
            layer.setTranslationY(translation);
        }
        frames++;
    }

    static JSONObject describe(Context context) throws Exception {
        JSONObject result = new JSONObject();
        WallpaperDepth depth = current.get();
        result.put("active", depth != null && depth.active)
                .put("saved_mode", DepthSettings.saved(context));
        if (depth == null) return result;
        View layer = depth.root.findViewWithTag("personal.wallpaper");
        return result.put("effective_mode", depth.mode).put("progress", depth.progress)
                .put("scale", layer == null ? 1f : layer.getScaleX())
                .put("translation_y", layer == null ? 0f : layer.getTranslationY())
                .put("height", depth.root.getHeight()).put("width", depth.root.getWidth())
                .put("image_ready", layer instanceof ImageView && ((ImageView) layer).getDrawable() != null)
                .put("animating", depth.animator != null && depth.animator.isRunning())
                .put("frames", depth.frames).put("focus_position", depth.position())
                .put("item_count", depth.grid.getAdapter() == null ? 0 : depth.grid.getAdapter().getItemCount());
    }
}
