package com.gathin.tvhome;

import org.junit.Test;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class VideoFilesTest {
    @Test public void failedImportPreservesThePreviousVideo() throws Exception {
        File directory = Files.createTempDirectory("video-test").toFile();
        File video = new File(directory, "background.mp4");
        Files.write(video.toPath(), new byte[]{1, 2, 3});
        try {
            VideoFiles.replace(new InputStream() {
                @Override public int read() throws IOException { throw new IOException("Interrupted import"); }
            }, video);
            fail("Expected an interrupted import");
        } catch (IOException expected) { }
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(video.toPath()));
        assertEquals(1, directory.list().length);
        video.delete(); directory.delete();
    }
    @Test public void completeImportReplacesTheVideoAndEmptyImportDoesNot() throws Exception {
        File directory = Files.createTempDirectory("video-test").toFile();
        File video = new File(directory, "background.mp4");
        VideoFiles.replace(new ByteArrayInputStream(new byte[]{4, 5}), video);
        assertArrayEquals(new byte[]{4, 5}, Files.readAllBytes(video.toPath()));
        try { VideoFiles.replace(new ByteArrayInputStream(new byte[0]), video); fail("Empty video"); }
        catch (IOException expected) { }
        assertArrayEquals(new byte[]{4, 5}, Files.readAllBytes(video.toPath()));
        video.delete(); directory.delete();
    }
}
