package com.gathin.tvhome;

import java.io.File;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeeklyWallpapersTest {
    @Test public void switchesAtMondayAndRepeatsAfterTwoWeeks() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        assertNotEquals(WeeklyWallpapers.group(monday.minusDays(1)), WeeklyWallpapers.group(monday));
        assertEquals(WeeklyWallpapers.group(monday), WeeklyWallpapers.group(monday.plusDays(6)));
        assertEquals(WeeklyWallpapers.group(monday), WeeklyWallpapers.group(monday.plusDays(14)));
    }

    @Test public void crossesNewYearWithoutRestartingTheCycle() {
        LocalDate thursday = LocalDate.of(2026, 12, 31);
        assertEquals(WeeklyWallpapers.group(thursday), WeeklyWallpapers.group(thursday.plusDays(1)));
        assertNotEquals(WeeklyWallpapers.group(thursday), WeeklyWallpapers.group(thursday.plusDays(4)));
    }

    @Test public void fixedWallpaperAndIncompleteUploadsRemainUsable() {
        List<File> one = Collections.singletonList(new File("01-original.jpg"));
        assertEquals(one, WeeklyWallpapers.select(one, "b", LocalDate.now()));
        List<File> originals = Arrays.asList(new File("01-original.jpg"), new File("02-original.jpg"));
        assertEquals(originals, WeeklyWallpapers.select(originals, "b", LocalDate.now()));
        assertTrue(WeeklyWallpapers.select(Collections.emptyList(), "weekly", LocalDate.now()).isEmpty());
    }

    @Test public void groupsKeepTheirOwnOrderAndAllModeIncludesBoth() {
        File a = new File("01-original.jpg"), b = new File("week-b-01-sea.jpg");
        List<File> files = Arrays.asList(a, b);
        assertEquals(Collections.singletonList(a), WeeklyWallpapers.select(files, "a", LocalDate.now()));
        assertEquals(Collections.singletonList(b), WeeklyWallpapers.select(files, "b", LocalDate.now()));
        assertEquals(files, WeeklyWallpapers.select(files, "all", LocalDate.now()));
    }
}
