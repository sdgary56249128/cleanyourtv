package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.TreeMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Read-only measurements for the paired Mac's regression script. Never accepts commands or preferences. */
public final class HomeDiagnosticsProvider extends ContentProvider {
    private JSONObject snapshot() throws Exception {
        Context context = getContext();
        android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        TreeMap<String, Object> preferences = new TreeMap<>(context.getSharedPreferences(Config.SETTINGS_NAME, 0).getAll());
        preferences.remove(Config.SETTINGS_REVISION_KEY);
        String settings = new JSONObject(preferences).toString();
        Order order = Order.load(Order.file(context));
        return new JSONObject().put("schema", 1).put("model", Build.MODEL)
                .put("version_name", info.versionName)
                .put("version_code", Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode)
                .put("settings_fingerprint", hash(settings))
                .put("order_fingerprint", hash(order == null ? "" : Order.encode(order)))
                .put("motion", WallpaperDepth.describe(context));
    }

    private static String hash(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        getContext().enforceCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS, "Paired ADB access required");
        if (!"/snapshot.json".equals(uri.getPath()) || !"r".equals(mode)) throw new FileNotFoundException("Read-only diagnostics");
        try {
            FutureTask<JSONObject> task = new FutureTask<>(this::snapshot);
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) task.run();
            else new android.os.Handler(android.os.Looper.getMainLooper()).post(task);
            byte[] data = task.get(10, TimeUnit.SECONDS).toString().getBytes(StandardCharsets.UTF_8);
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    out.write(data);
                } catch (IOException ignored) { /* Reader disconnected. */ }
            }, "home-diagnostics").start();
            return pipe[0];
        } catch (Exception error) { throw new FileNotFoundException(error.toString()); }
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
}
