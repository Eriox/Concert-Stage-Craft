package com.concertstage.audio.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** What kind of audio file a file is (by its content, not its name), its length, its SHA-1, and a decoder for it. */
public final class AudioFiles {
    public static final String MP3 = "mp3", WAV = "wav";

    private AudioFiles() {}

    /** "mp3", "wav" or null, from the first bytes of the file. */
    public static String detect(Path file) {
        byte[] h = new byte[12];
        try (InputStream in = Files.newInputStream(file)) {
            int n = in.readNBytes(h, 0, 12);
            return detect(h, n);
        } catch (IOException e) {
            return null;
        }
    }

    public static String detect(byte[] h, int n) {
        if (n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'A' && h[10] == 'V' && h[11] == 'E') return WAV;
        if (n >= 3 && h[0] == 'I' && h[1] == 'D' && h[2] == '3') return MP3;
        if (n >= 2 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xE0) == 0xE0 && (h[1] & 0x06) != 0) return MP3;     // MPEG audio frame sync
        return null;
    }

    /** File name extension the player used, when it is one we read ("mp3", "wav"), else null. */
    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return null;
        String e = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return e.equals(MP3) || e.equals(WAV) ? e : null;
    }

    public static long durationMs(Path file, String type) {
        return MP3.equals(type) ? Mp3Decoder.durationMs(file) : WAV.equals(type) ? WavDecoder.durationMs(file) : -1;
    }

    public static TrackDecoder open(Path file, String type) throws IOException {
        if (MP3.equals(type)) return new Mp3Decoder(file);
        if (WAV.equals(type)) return new WavDecoder(file);
        throw new IOException("unknown audio type " + type);
    }

    /** True when the start of the file really decodes (a renamed or damaged file is refused). */
    public static boolean decodes(Path file, String type) {
        try (TrackDecoder d = open(file, type)) {
            short[] buf = new short[4096];
            int total = 0;
            for (int i = 0; i < 64 && total < 8192; i++) {
                int n = d.read(buf);
                if (n < 0) break;
                total += n;
            }
            return total > 0;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** A SHA-1 as sent over the network: 40 lowercase hex characters, nothing else (also keeps paths safe). */
    public static boolean validSha1(String s) {
        if (s == null || s.length() != 40) return false;
        for (int i = 0; i < 40; i++) {
            char c = s.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) return false;
        }
        return true;
    }
}
