package io.github.p3pandroid;

import java.io.*;
import java.nio.file.*;

public class P3pPreparationTest {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static void text(File f, String text) throws IOException { Files.write(f.toPath(), text.getBytes("UTF-8")); }
    static byte[] module() {
        byte[] bytes = new byte[20];
        bytes[0] = 3; bytes[1] = 2; bytes[2] = 35; bytes[3] = 7; bytes[12] = 1;
        return bytes;
    }
    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("p3p-preparation-test").toFile();
        File marker = new File(dir, "prepared.tsv");
        File shader = new File(dir, "0123456789abcdef.spv"), metadata = new File(dir, "0123456789abcdef.resources");
        try {
            text(new File(dir, "import.txt"), "game.nsp\nold captured cache\n");
            check(!PreparationFiles.ready(dir));
            text(marker, PreparationFiles.FORMAT + "\n");
            check(PreparationFiles.ready(dir)); // zero ExeFS seeds allowed after native compiler probe
            text(marker, PreparationFiles.FORMAT + "\nseed\t0123456789abcdef\n");
            check(!PreparationFiles.ready(dir));
            Files.write(shader.toPath(), module());
            check(!PreparationFiles.ready(dir)); // descriptor metadata is required
            text(metadata, ""); check(PreparationFiles.ready(dir)); // descriptor-free shader
            Files.write(shader.toPath(), new byte[20]); check(!PreparationFiles.ready(dir));
            Files.write(shader.toPath(), module());
            text(new File(dir, "failed.txt"), "translation error");
            check(!PreparationFiles.ready(dir));
            check("translation error".equals(PreparationFiles.failure(dir)));
            Files.delete(new File(dir, "failed.txt").toPath());
            text(marker, "format\tunknown\n"); check(!PreparationFiles.ready(dir));
            text(marker, PreparationFiles.FORMAT + "\n"); check(PreparationFiles.ready(dir));
            Files.delete(metadata.toPath()); check(!PreparationFiles.ready(dir)); // first-use module, not a seed
            System.out.println("PASS: P3P local cache readiness, metadata, corruption, migration and failures");
        } finally {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) Files.deleteIfExists(f.toPath());
            Files.deleteIfExists(dir.toPath());
        }
    }
}
