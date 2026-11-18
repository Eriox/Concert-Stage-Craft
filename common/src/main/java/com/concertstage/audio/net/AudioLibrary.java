package com.concertstage.audio.net;

import com.concertstage.audio.core.AudioFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The tracks imported on this world, in {@code <world>/concertstage_audio/<sha1>.<mp3|wav>}, with {@code library.json} (name, type,
 * size, length, who imported it). A file is stored once whatever the number of consoles that use it; files are named by their SHA-1,
 * so nothing a player sends can choose a path.
 */
public final class AudioLibrary {
    public record Meta(String sha1, String name, String type, long size, long durationMs, String owner) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path dir;
    private final Map<String, Meta> entries = new LinkedHashMap<>();

    public AudioLibrary(Path dir) {
        this.dir = dir;
        load();
    }

    public Path dir() { return dir; }

    public synchronized Meta get(String sha1) {
        Meta m = entries.get(sha1);
        return m != null && Files.isRegularFile(file(m)) ? m : null;
    }

    public Path file(Meta m) {
        return dir.resolve(m.sha1() + "." + m.type());
    }

    public synchronized long usedBytes() {
        long n = 0;
        for (Meta m : entries.values()) n += m.size();
        return n;
    }

    /** Moves a checked file into the library. */
    public synchronized Meta add(Path checked, Meta m) throws IOException {
        Files.createDirectories(dir);
        Files.move(checked, file(m), StandardCopyOption.REPLACE_EXISTING);
        entries.put(m.sha1(), m);
        save();
        return m;
    }

    public Path tempFile(String sha1) throws IOException {
        Files.createDirectories(dir.resolve("incoming"));
        return dir.resolve("incoming").resolve(sha1 + ".part");
    }

    private void load() {
        Path f = dir.resolve("library.json");
        if (!Files.isRegularFile(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            for (var e : o.entrySet()) {
                JsonObject m = e.getValue().getAsJsonObject();
                String sha = e.getKey(), type = m.get("type").getAsString();
                if (!AudioFiles.validSha1(sha) || !(type.equals(AudioFiles.MP3) || type.equals(AudioFiles.WAV))) continue;
                entries.put(sha, new Meta(sha, m.get("name").getAsString(), type, m.get("size").getAsLong(), m.get("duration").getAsLong(),
                    m.has("owner") ? m.get("owner").getAsString() : ""));
            }
        } catch (IOException | RuntimeException ignored) {
            // damaged index: the files stay, they are added again when imported again
        }
    }

    private void save() {
        JsonObject o = new JsonObject();
        for (Meta m : entries.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", m.name());
            j.addProperty("type", m.type());
            j.addProperty("size", m.size());
            j.addProperty("duration", m.durationMs());
            j.addProperty("owner", m.owner());
            o.add(m.sha1(), j);
        }
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve("library.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) { GSON.toJson(o, w); }
            Files.move(tmp, dir.resolve("library.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // kept in memory for this session
        }
    }
}
