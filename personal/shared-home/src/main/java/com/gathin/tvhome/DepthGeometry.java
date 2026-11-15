package com.gathin.tvhome;

/** Camera geometry only; no bitmap allocation or work while navigation is idle. */
final class DepthGeometry {
    private DepthGeometry() {}

    static float progress(int position, int count, int spans) {
        if (position < 0 || count < 2 || spans < 1) return 0f;
        int lastRow = (count - 1) / spans;
        return lastRow == 0 ? 0f : clamp((float) (position / spans) / lastRow);
    }

    static float clamp(float progress) {
        return Float.isNaN(progress) ? 0f : Math.max(0f, Math.min(1f, progress));
    }

    static float scale(float progress, String mode) {
        float p = clamp(progress);
        if ("off".equals(mode)) return 1f;
        return "subtle".equals(mode) ? 1.05f - .035f * p : 1.10f - .08f * p;
    }

    static float translation(float progress, int height, String mode) {
        if ("off".equals(mode)) return 0f;
        return -Math.max(0, height) * clamp(progress) * ("subtle".equals(mode) ? .005f : .008f);
    }
}
