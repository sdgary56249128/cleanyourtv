package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Shell-only import endpoint, protected by the manifest's WRITE_SECURE_SETTINGS permission. */
public final class VideoFileProvider extends ContentProvider {
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"/background.mp4".equals(uri.getPath())) throw new FileNotFoundException("Unknown video path");
        File target = VideoSettingsActivity.videoFile(getContext());
        if (!mode.contains("w")) return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY);
        try {
            File temporary = File.createTempFile("import-", ".part", target.getParentFile());
            return ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_TRUNCATE, new Handler(Looper.getMainLooper()), error -> {
                if (error != null || temporary.length() == 0 || temporary.length() > VideoFiles.MAX_BYTES
                        || !temporary.renameTo(target)) temporary.delete();
            });
        } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "video/mp4"; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
