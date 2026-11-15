package com.gathin.tvhome;

import android.content.Context;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class WeeklyWallpapers {
    static final String KEY = "weekly_wallpaper_mode";
    static final String[] MODES = {"weekly", "a", "b", "all"};

    private WeeklyWallpapers() {}

    static String mode(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(KEY, "weekly");
    }

    /** Epoch week zero starts on Monday, so Sundays and year boundaries have a stable group. */
    static String group(LocalDate date) {
        long week = Math.floorDiv(date.toEpochDay() - 4, 7);
        return Math.floorMod(week, 2) == 0 ? "a" : "b";
    }

    static List<File> select(Context context, List<File> files) {
        return select(files, mode(context), LocalDate.now());
    }

    static List<File> select(List<File> files, String mode, LocalDate date) {
        // A single on-TV wallpaper choice takes precedence over the weekly collection.
        if (files.size() < 2 || "all".equals(mode)) return files;
        String selected = "a".equals(mode) || "b".equals(mode) ? mode : group(date);
        List<File> result = new ArrayList<>();
        for (File file : files) {
            boolean second = file.getName().startsWith("week-b-");
            if (second == "b".equals(selected)) result.add(file);
        }
        return result.isEmpty() ? files : result;
    }
}
