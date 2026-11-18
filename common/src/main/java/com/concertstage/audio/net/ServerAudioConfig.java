package com.concertstage.audio.net;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Server settings of the sound system, in {@code config/concertstage-server.properties} (created with the defaults on first start):
 * who may import music, how big a file and the whole library may be, how fast files are sent to the players.
 */
public final class ServerAudioConfig {
    /** Permission level needed to import a track (0 = everyone, 2 = operators). The owner of a single player world always may. */
    public int uploadPermissionLevel = 2;
    public int maxFileMb = 80;
    public int maxLibraryMb = 2048;
    /** Sending speed of a track to one player, kB per second. */
    public int downloadKbps = 1024;
    /** Players further than this from a console that plays a track are not sent it. */
    public int distributionRange = 192;

    public long maxFileBytes() { return maxFileMb * 1024L * 1024L; }
    public long maxLibraryBytes() { return maxLibraryMb * 1024L * 1024L; }

    public static ServerAudioConfig load(Path configDir) {
        ServerAudioConfig c = new ServerAudioConfig();
        Path f = configDir.resolve("concertstage-server.properties");
        Properties p = new Properties();
        try {
            if (Files.isRegularFile(f)) try (Reader r = Files.newBufferedReader(f)) { p.load(r); }
        } catch (IOException ignored) {
            // defaults
        }
        c.uploadPermissionLevel = clamp(p, "upload_permission_level", c.uploadPermissionLevel, 0, 4);
        c.maxFileMb = clamp(p, "max_file_mb", c.maxFileMb, 1, 500);
        c.maxLibraryMb = clamp(p, "max_library_mb", c.maxLibraryMb, 10, 100000);
        c.downloadKbps = clamp(p, "download_kbps", c.downloadKbps, 64, 20000);
        c.distributionRange = clamp(p, "distribution_range", c.distributionRange, 16, 1024);
        if (!Files.isRegularFile(f)) {
            p.setProperty("upload_permission_level", String.valueOf(c.uploadPermissionLevel));
            p.setProperty("max_file_mb", String.valueOf(c.maxFileMb));
            p.setProperty("max_library_mb", String.valueOf(c.maxLibraryMb));
            p.setProperty("download_kbps", String.valueOf(c.downloadKbps));
            p.setProperty("distribution_range", String.valueOf(c.distributionRange));
            try {
                Files.createDirectories(configDir);
                try (Writer w = Files.newBufferedWriter(f)) {
                    p.store(w, "Concert Stage Craft - sound system (server). upload_permission_level: 0 = every player may import music, "
                        + "2 = operators only (the owner of a single player world always may). Only import music you have the right to play.");
                }
            } catch (IOException ignored) {
                // read-only config folder: defaults
            }
        }
        return c;
    }

    private static int clamp(Properties p, String key, int def, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim())));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
