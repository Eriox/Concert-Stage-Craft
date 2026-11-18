package com.concertstage.client.audio;

import com.concertstage.audio.core.AudioFiles;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Local copies of the tracks (the ones this player imported and the ones received from a server), in
 * {@code .minecraft/concertstage/audio_cache/<sha1>.<mp3|wav>}. Named by their SHA-1 (checked after every download), oldest unused
 * files removed beyond the size chosen in the audio settings. The music folder {@code .minecraft/concertstage/music} is where the
 * player puts files to import.
 */
public final class AudioCache {
    private AudioCache() {}

    public static Path root() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("concertstage");
    }

    public static Path dir() {
        return root().resolve("audio_cache");
    }

    public static Path musicDir() {
        Path p = root().resolve("music");
        try { Files.createDirectories(p); } catch (IOException ignored) { }
        return p;
    }

    public static Path file(String sha1, String type) {
        return dir().resolve(sha1 + "." + type);
    }

    public static boolean has(String sha1, String type) {
        Path f = file(sha1, type);
        if (!Files.isRegularFile(f)) return false;
        try { Files.setLastModifiedTime(f, FileTime.fromMillis(System.currentTimeMillis())); } catch (IOException ignored) { }
        return true;
    }

    /** Copies an imported file into the cache (background thread). */
    public static void store(Path source, String sha1, String type) throws IOException {
        Files.createDirectories(dir());
        Path f = file(sha1, type);
        if (Files.isRegularFile(f)) return;
        Path tmp = dir().resolve(sha1 + ".copy");
        Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        trim();
    }

    public static Path partFile(String sha1) throws IOException {
        Files.createDirectories(dir());
        return dir().resolve(sha1 + ".part");
    }

    /** A finished download: kept only if its SHA-1 is right (background thread). */
    public static boolean accept(Path part, String sha1, String type) {
        try {
            if (!sha1.equals(AudioFiles.sha1(part)) || !type.equals(AudioFiles.detect(part))) { Files.deleteIfExists(part); return false; }
            Files.move(part, file(sha1, type), StandardCopyOption.REPLACE_EXISTING);
            trim();
            return true;
        } catch (IOException e) {
            try { Files.deleteIfExists(part); } catch (IOException ignored) { }
            return false;
        }
    }

    /** Removes the oldest files beyond the cache size of the settings. */
    public static synchronized void trim() {
        long limit = AudioSettings.cacheMb * 1024L * 1024L;
        List<Path> files = new ArrayList<>();
        long total = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir())) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                if (n.endsWith(".part") || n.endsWith(".copy")) {
                    if (Files.getLastModifiedTime(p).toMillis() < System.currentTimeMillis() - 3600_000L) Files.deleteIfExists(p);
                    continue;
                }
                files.add(p);
                total += Files.size(p);
            }
        } catch (IOException e) {
            return;
        }
        if (total <= limit) return;
        files.sort(Comparator.comparingLong(p -> { try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0L; } }));
        for (Path p : files) {
            if (total <= limit) break;
            try { long s = Files.size(p); Files.deleteIfExists(p); total -= s; } catch (IOException ignored) { }
        }
    }
}
