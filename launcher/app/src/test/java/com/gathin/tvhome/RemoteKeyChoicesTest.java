package com.gathin.tvhome;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteKeyChoicesTest {
    @Test public void overridesTapsAndHoldsIndependentlyWithoutChangingTheTheme() {
        List<String> theme = Arrays.asList("190:a.b", "hold:190:c.d", "191:e.f", "scan:490:g.h");
        Map<String, Object> saved = new HashMap<>();
        saved.put(RemoteKeys.CHOICES_PREFIX + "190", "new.app");
        saved.put(RemoteKeys.CHOICES_PREFIX + "hold:191", "ignore");
        List<String> merged = RemoteKeys.applyChoices(theme, saved);
        assertEquals("new.app", RemoteKeys.parseAppKeys(merged, key -> 0).get(190));
        assertEquals("c.d", RemoteKeys.parseHoldAppKeys(merged, key -> 0).get(190));
        assertEquals("e.f", RemoteKeys.parseAppKeys(merged, key -> 0).get(191));
        assertEquals("ignore", RemoteKeys.parseHoldAppKeys(merged, key -> 0).get(191));
        assertEquals("g.h", RemoteKeys.parseAppScancodes(merged).get(490));
        assertEquals(4, theme.size());
    }

    @Test public void removingAChoiceRestoresTheThemeAndUnrelatedPreferencesAreIgnored() {
        List<String> theme = Arrays.asList("190:a.b");
        Map<String, Object> saved = new HashMap<>();
        saved.put(RemoteKeys.CHOICES_PREFIX + "190", "new.app");
        saved.remove(RemoteKeys.CHOICES_PREFIX + "190");
        saved.put("columns", 5);
        saved.put(RemoteKeys.CHOICES_PREFIX + "0", "bad.app");
        saved.put(RemoteKeys.CHOICES_PREFIX + "190", 42);
        saved.put(RemoteKeys.CHOICES_PREFIX + "hold:191", "  ");
        assertEquals(theme, RemoteKeys.applyChoices(theme, saved));
    }
}
