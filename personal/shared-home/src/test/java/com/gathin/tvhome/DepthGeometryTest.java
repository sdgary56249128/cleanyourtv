package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class DepthGeometryTest {
    @Test public void rowsMoveAwayWhileHorizontalFocusKeepsTheSameDepth() {
        assertEquals(0f, DepthGeometry.progress(0, 11, 4), 0f);
        assertEquals(0f, DepthGeometry.progress(3, 11, 4), 0f);
        assertEquals(.5f, DepthGeometry.progress(4, 11, 4), 0f);
        assertEquals(1f, DepthGeometry.progress(10, 11, 4), 0f);
        assertTrue(DepthGeometry.scale(0, "standard") > DepthGeometry.scale(1, "standard"));
    }
    @Test public void emptySingleRowAndTransientNoPositionStayNear() {
        assertEquals(0f, DepthGeometry.progress(-1, 20, 4), 0f);
        assertEquals(0f, DepthGeometry.progress(0, 0, 4), 0f);
        assertEquals(0f, DepthGeometry.progress(3, 4, 4), 0f);
        assertEquals(0f, DepthGeometry.progress(3, 4, 0), 0f);
    }
    @Test public void bothStrengthsAlwaysCoverTheFrameIncludingTheTranslation() {
        for (String mode : new String[]{"subtle", "standard"}) {
            for (int height : new int[]{720, 1080, 2160}) {
                for (int step = 0; step <= 100; step++) {
                    float progress = step / 100f;
                    float extra = (DepthGeometry.scale(progress, mode) - 1) * height / 2;
                    assertTrue(mode, Math.abs(DepthGeometry.translation(progress, height, mode)) <= extra);
                }
            }
        }
    }
    @Test public void offIsUnchangedAndProgressIsBounded() {
        assertEquals(1f, DepthGeometry.scale(.8f, "off"), 0f);
        assertEquals(0f, DepthGeometry.translation(.8f, 1080, "off"), 0f);
        assertEquals(DepthGeometry.scale(0, "standard"), DepthGeometry.scale(-5, "standard"), 0f);
        assertEquals(DepthGeometry.scale(1, "standard"), DepthGeometry.scale(5, "standard"), 0f);
        assertEquals(0f, DepthGeometry.clamp(Float.NaN), 0f);
    }
}
