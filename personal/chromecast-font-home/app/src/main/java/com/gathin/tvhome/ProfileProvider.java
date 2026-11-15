package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Paired ADB access is guarded by WRITE_SECURE_SETTINGS on the provider declaration. */
public final class ProfileProvider extends ContentProvider {
    private <T> T onMain(java.util.concurrent.Callable<T> operation) throws Exception {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return operation.call();
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(operation);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(task);
        return task.get(15, java.util.concurrent.TimeUnit.SECONDS);
    }

    private File incoming(String token) throws FileNotFoundException {
        if (token == null || !token.matches("[a-f0-9]{32}")) throw new FileNotFoundException("Invalid import token");
        return new File(getContext().getCacheDir(), "home-profile-" + token + ".json");
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> parts = uri.getPathSegments();
        if (parts.size() == 2 && "incoming".equals(parts.get(0)) && "w".equals(mode)) {
            return ParcelFileDescriptor.open(incoming(parts.get(1)), ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        if (parts.size() != 1 || !"snapshot.json".equals(parts.get(0)) || !"r".equals(mode)) {
            throw new FileNotFoundException("Unsupported profile path");
        }
        try {
            byte[] data = onMain(() -> HomeProfile.snapshot(getContext())).toString().getBytes(StandardCharsets.UTF_8);
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    out.write(data);
                } catch (IOException ignored) { /* The reader may disconnect. */ }
            }, "profile-export").start();
            return pipe[0];
        } catch (Exception failure) { throw new FileNotFoundException(failure.getMessage()); }
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        getContext().enforceCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS,
                "Profile operations require a paired ADB shell");
        Bundle answer = new Bundle();
        try {
            if ("restore".equals(method)) {
                File file = incoming(arg);
                try {
                    JSONObject profile = HomeProfile.read(file);
                    onMain(() -> { HomeProfile.restore(getContext(), profile); return null; });
                }
                finally { file.delete(); }
            } else if ("synced".equals(method) && arg != null && arg.matches("[a-f0-9]{7,40}")) {
                getContext().getSharedPreferences("git_profile", 0).edit()
                        .putString("commit", arg).putLong("time", System.currentTimeMillis()).apply();
            } else throw new IllegalArgumentException("Unsupported profile operation");
            answer.putBoolean("ok", true);
        } catch (Exception failure) { answer.putString("error", failure.getMessage()); }
        return answer;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
}
