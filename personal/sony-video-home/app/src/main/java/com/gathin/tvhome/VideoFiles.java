package com.gathin.tvhome;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Replace a local video only after the whole import succeeds. */
final class VideoFiles {
    static final long MAX_BYTES = 128L * 1024 * 1024;
    static void replace(InputStream input, File destination) throws IOException {
        File temporary = File.createTempFile("video-", ".part", destination.getParentFile());
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_BYTES) throw new IOException("Video exceeds 128 MB");
                    output.write(buffer, 0, count);
                }
                if (total == 0) throw new IOException("Empty video");
                output.getFD().sync();
            }
            if (!temporary.renameTo(destination)) throw new IOException("Could not replace video");
        } finally {
            temporary.delete();
        }
    }
}
