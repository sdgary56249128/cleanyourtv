package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.AtomicFile;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/** Public Home preferences and theme fields suitable for per-device Git profiles. */
final class HomeProfile {
    static final Object LOCK = new Object();
    private static final String PENDING = "home-profile-pending.json";
    private static final String LOCAL = "home-profile-backup.json";
    private static final Set<String> THEME = new HashSet<>(Arrays.asList(
            "wallpaper", "wallpaper_rotation_minutes", "wallpaper_order", "wallpaper_dim", "wallpaper_true_color",
            "ambient_glow", "app_glow", "dock_apps", "columns", "rows_below_dock", "dock_opacity", "dock_glass",
            "show_inputs_pill", "live_tv_package", "stock_home_redirect", "app_keys", "remote_key_names",
            "launcher_settings_button", "home_idle_clock_minutes", "clock_wallpaper", "clock_wallpaper_dim",
            "clock_glow", "clock_format", "app_labels", "launch_actions", "hidden_apps", "key_filter",
            "inputs_connected_only", "inputs_cec_state_only", "wallpaper_captions"));
    private static final Set<String> NUMBERS = new HashSet<>(Arrays.asList(
            "columns", "rows_below_dock", "dock_opacity", "home_idle_clock_minutes",
            "wallpaper_rotation_minutes", "wallpaper_dim"));
    private static final Set<String> BOOLEANS = new HashSet<>(Arrays.asList("animations", "advanced_effects"));
    private static final Set<String> STRINGS = new HashSet<>(Arrays.asList(
            "clock_format", "wallpaper_choice", "wallpaper_order", "open_on_start", "personal_home_font",
            WeeklyWallpapers.KEY));

    private HomeProfile() {}

    private static Set<String> keys(JSONObject object) {
        Set<String> result = new TreeSet<>();
        java.util.Iterator<String> names = object.keys();
        while (names.hasNext()) result.add(names.next());
        return result;
    }

    private static boolean allowed(String key) {
        return NUMBERS.contains(key) || BOOLEANS.contains(key) || STRINGS.contains(key)
                || key.startsWith(RemoteKeys.CHOICES_PREFIX)
                && RemoteKeys.validChoiceKey(key.substring(RemoteKeys.CHOICES_PREFIX.length()));
    }

    static JSONObject snapshot(Context context) throws IOException, JSONException {
        synchronized (LOCK) {
            File pending = new File(context.getFilesDir(), PENDING);
            if (pending.exists()) apply(context, read(pending));
            File directory = Config.dir(context);
            if (directory == null) throw new IOException("Theme storage is unavailable");
            Config config = Config.load(context);
            JSONObject theme = new JSONObject();
            File file = new File(Config.dir(context), Config.FILE_NAME);
            if (file.isFile()) {
                JSONObject source = read(file);
                for (String key : new TreeSet<>(THEME)) if (source.has(key)) theme.put(key, source.get(key));
            }
            JSONObject prefs = new JSONObject();
            SharedPreferences saved = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
            for (String key : new TreeSet<>(saved.getAll().keySet())) {
                if (allowed(key)) prefs.put(key, saved.getAll().get(key));
            }
            prefs.put("personal_home_font", FontChoice.VALUES[FontChoice.selected(context)])
                    .put(WeeklyWallpapers.KEY, WeeklyWallpapers.mode(context));
            Order order = Order.load(Order.file(context));
            if (order == null) order = new Order(Order.dockKey(config.dockApps), java.util.Collections.emptyList());
            JSONArray wallpapers = new JSONArray();
            for (File image : WallpaperRotation.images(new File(Config.dir(context), "wallpapers"))) {
                wallpapers.put(image.getName());
            }
            return new JSONObject().put("schema", 1).put("model", Build.MODEL)
                    .put("theme", theme).put("preferences", prefs).put("order", Order.encode(order))
                    .put("wallpapers", wallpapers);
        }
    }

    static void validate(JSONObject profile) throws JSONException {
        if (profile.getInt("schema") != 1 || !Build.MODEL.equals(profile.getString("model"))) {
            throw new JSONException("Profile schema or device model does not match");
        }
        JSONObject prefs = profile.getJSONObject("preferences");
        for (String key : keys(prefs)) {
            Object value = prefs.get(key);
            boolean valid = allowed(key);
            if (NUMBERS.contains(key)) valid &= value instanceof Integer && (Integer) value >= -1 && (Integer) value <= 1440;
            else if (BOOLEANS.contains(key)) valid &= value instanceof Boolean;
            else valid &= value instanceof String && ((String) value).length() <= 256;
            if (!valid) throw new JSONException("Invalid preference: " + key);
        }
        for (String key : keys(profile.getJSONObject("theme"))) {
            if (!THEME.contains(key)) throw new JSONException("Unsupported theme field: " + key);
        }
        if (Order.decode(profile.getString("order")) == null) throw new JSONException("Invalid app order");
        JSONArray images = profile.getJSONArray("wallpapers");
        for (int i = 0; i < images.length(); i++) {
            if (!images.getString(i).matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,119}")) {
                throw new JSONException("Invalid wallpaper filename");
            }
        }
    }

    static JSONObject read(File file) throws IOException, JSONException {
        if (file.length() > 131072) throw new IOException("Profile exceeds 128 KB");
        return new JSONObject(new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8));
    }

    static void write(File file, JSONObject profile) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream stream = atomic.startWrite();
        try {
            stream.write(profile.toString().getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(stream);
        } catch (IOException failure) {
            atomic.failWrite(stream);
            throw failure;
        }
    }

    static void backup(Context context) throws IOException, JSONException {
        synchronized (LOCK) { write(new File(context.getFilesDir(), LOCAL), snapshot(context)); }
    }

    static void restoreLocal(Context context) throws IOException, JSONException {
        restore(context, read(new File(context.getFilesDir(), LOCAL)));
    }

    static void restore(Context context, JSONObject profile) throws IOException, JSONException {
        synchronized (LOCK) {
            validate(profile);
            File directory = Config.dir(context);
            if (directory == null) throw new IOException("Theme storage is unavailable");
            JSONArray images = profile.getJSONArray("wallpapers");
            for (int i = 0; i < images.length(); i++) {
                if (!new File(new File(directory, "wallpapers"), images.getString(i)).isFile()) {
                    throw new IOException("Install the profile's wallpapers before restoring");
                }
            }
            write(new File(context.getFilesDir(), PENDING), profile);
            apply(context, profile);
        }
    }

    /** A durable target lets an interrupted restore finish before the next Home is created. */
    static void recover(Context context) {
        synchronized (LOCK) {
            File pending = new File(context.getFilesDir(), PENDING);
            if (!pending.exists()) return;
            try { apply(context, read(pending)); }
            catch (IOException | JSONException failure) { Log.w("HomeProfile", "Restore pending", failure); }
        }
    }

    private static void apply(Context context, JSONObject profile) throws IOException, JSONException {
        validate(profile);
        write(new File(Config.dir(context), Config.FILE_NAME), profile.getJSONObject("theme"));
        // Consume the theme's revision before committing restored on-TV overrides.
        Config.load(context);
        if (!Order.save(Order.file(context), Order.decode(profile.getString("order")))) {
            throw new IOException("Could not restore app order");
        }
        SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : prefs.getAll().keySet()) if (allowed(key)) edit.remove(key);
        JSONObject values = profile.getJSONObject("preferences");
        for (String key : keys(values)) {
            Object value = values.get(key);
            if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else edit.putString(key, (String) value);
        }
        edit.putLong(Config.SETTINGS_REVISION_KEY, prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1);
        if (!edit.commit()) throw new IOException("Could not save restored preferences");
        new AtomicFile(new File(context.getFilesDir(), PENDING)).delete();
    }
}
