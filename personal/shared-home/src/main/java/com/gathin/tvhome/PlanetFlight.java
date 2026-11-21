package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;

/** A stationary sky and one independently moving planet, behind the Home controls. */
final class PlanetFlight extends FrameLayout {
    private final View sky;
    private final ImageView planet;
    private final int spriteSize;
    private float phase = 0f;

    PlanetFlight(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        sky = new Stars(context);
        addView(sky, new LayoutParams(-1, -1));
        planet = new ImageView(context);
        planet.setImageResource(R.drawable.flight_planet);
        planet.setScaleType(ImageView.ScaleType.FIT_CENTER);
        spriteSize = planet.getDrawable().getIntrinsicWidth();
        planet.setAlpha(.88f);
        addView(planet, new LayoutParams(spriteSize, spriteSize, Gravity.TOP | Gravity.LEFT));
    }

    boolean setPhase(float next) {
        if (phase == next) return false;
        phase = next;
        positionLayers();
        return true;
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        positionLayers();
    }

    private void positionLayers() {
        if (getHeight() == 0) return;
        float p = phase;
        float scale = getHeight() * 2.4f / (spriteSize * (1f + 5f * p + 16f * p * p));
        // The inner bend makes the close pass cross the viewer's sightline before exiting at upper right.
        float centerX = getWidth() * (.77f - .75f * p + 1.14f * p * p);
        float centerY = getHeight() * (.59f - .90f * p + .20f * p * p);
        planet.setTranslationX(centerX - spriteSize * .5f);
        planet.setTranslationY(centerY - spriteSize * .5f);
        planet.setScaleX(scale);
        planet.setScaleY(scale);
        planet.setRotation(-7f + 27f * p);
        float blend = p * p * (3f - 2f * p);
        sky.setAlpha(1f - blend);
        setVisibility(p >= 1f ? INVISIBLE : VISIBLE);
    }

    JSONObject snapshot() throws JSONException {
        return new JSONObject().put("size_px", spriteSize * planet.getScaleX())
                .put("center_x", planet.getTranslationX() + spriteSize * .5f)
                .put("center_y", planet.getTranslationY() + spriteSize * .5f)
                .put("rotation", planet.getRotation()).put("visible", getVisibility() == VISIBLE);
    }

    private static final class Stars extends View {
        private final Paint skyPaint = new Paint();
        private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] stars = new float[140 * 3];

        Stars(Context context) {
            super(context);
            Random random = new Random(30941);
            for (int i = 0; i < stars.length; i++) stars[i] = random.nextFloat();
        }

        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            if (w > 0) skyPaint.setShader(new RadialGradient(.68f * w, .30f * h, .78f * w,
                    0xff192139, 0xff070a14, Shader.TileMode.CLAMP));
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.drawRect(0, 0, w, h, skyPaint);
            for (int i = 0; i < stars.length; i += 3) {
                float brightness = stars[i + 2];
                starPaint.setColor(Color.argb((int) (55 + 155 * brightness), 205, 219, 244));
                canvas.drawCircle(stars[i] * w, stars[i + 1] * h,
                        h * (.00045f + .00095f * brightness), starPaint);
            }
        }
    }
}
