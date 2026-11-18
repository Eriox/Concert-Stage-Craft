package com.concertstage.client.audio;

import com.concertstage.audio.core.AudioFiles;
import com.concertstage.audio.net.AudioNet;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * Client side of the file transfers: the import of a track by this player (checked and hashed off the game thread, copied to the
 * cache, then sent to the server in chunks, two per tick) and the download of the tracks a nearby console plays (only if the player
 * allows it in the audio settings).
 */
public final class Transfers {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CSC audio import");
        t.setDaemon(true);
        return t;
    });

    /** Upload of the import in progress (one at a time). */
    private static Upload upload;
    /** 1.2.0 beta 2b: files waiting to be imported (a selection or "import all"), sent one after the other. */
    private static final java.util.ArrayDeque<Path> QUEUE = new java.util.ArrayDeque<>();
    private static BlockPos queueConsole;
    /** A file is being checked / hashed before its upload starts. */
    private static boolean checking;
    private static int batchTotal, batchDone, batchFailed;
    /** Status line of the last import, for the console screen. */
    private static volatile Component status = Component.empty();
    private static volatile int statusColor = 0xFFB8BEC6;
    private static final Map<String, Download> DOWNLOADS = new HashMap<>();
    /** SHA-1s the server refused to send: not asked again in this session. */
    private static final java.util.Set<String> REFUSED = new java.util.HashSet<>();

    /** Server limits for the console screen (S_INFO). */
    public static volatile boolean canUpload = true;
    public static volatile long maxFileBytes = 80L << 20, libraryFree = Long.MAX_VALUE;

    private Transfers() {}

    private static final class Upload {
        final BlockPos console;
        final Path file;
        final String sha1, type, name;
        final long size;
        RandomAccessFile in;
        long sent;
        int index;
        boolean sending;

        Upload(BlockPos console, Path file, String sha1, String type, String name, long size) {
            this.console = console; this.file = file; this.sha1 = sha1; this.type = type; this.name = name; this.size = size;
        }
    }

    private static final class Download {
        final String sha1, type;
        final long size;
        final Path part;
        OutputStream out;
        long received;
        int next;

        Download(String sha1, String type, long size, Path part) {
            this.sha1 = sha1; this.type = type; this.size = size; this.part = part;
        }
    }

    public static Component status() { return status; }
    public static int statusColor() { return statusColor; }

    private static void status(Component c, int color) {
        status = c;
        statusColor = color;
    }

    public static boolean busy() {
        return upload != null || checking || !QUEUE.isEmpty();
    }

    /** Files still waiting in the import queue (not counting the one being sent). */
    public static int queued() {
        return QUEUE.size();
    }

    /** Imports several files, one after the other (the selection of the picker, "import all", several files dropped or chosen). */
    public static void importFiles(BlockPos console, java.util.List<Path> files) {
        if (files.isEmpty()) return;
        if (!canUpload) { status(Component.translatable("audio.concertstage.err_permission"), 0xFFE5484D); return; }
        if (queueConsole != null && !queueConsole.equals(console)) QUEUE.clear();
        queueConsole = console;
        if (upload == null && !checking && QUEUE.isEmpty()) { batchTotal = 0; batchDone = 0; batchFailed = 0; }
        for (Path f : files) if (!QUEUE.contains(f)) { QUEUE.add(f); batchTotal++; }
        pump();
    }

    /** Starts the next file of the queue when nothing is being checked or sent. */
    private static void pump() {
        if (upload != null || checking) return;
        Path next = QUEUE.poll();
        if (next == null) {
            if (batchTotal > 1) status(Component.translatable("audio.concertstage.import_batch_done", batchDone, batchTotal, batchFailed),
                batchFailed == 0 ? 0xFF4CD08A : 0xFFF2B33D);
            batchTotal = 0;
            return;
        }
        start(queueConsole, next);
    }

    /** 0..1 progress of the import being sent, -1 when none. */
    public static double uploadProgress() {
        Upload u = upload;
        return u == null || u.size == 0 ? -1 : u.sent / (double) u.size;
    }

    /** Download progress of a track (0..1), -1 when it is not being downloaded. */
    public static double downloadProgress(String sha1) {
        Download d = DOWNLOADS.get(sha1);
        return d == null || d.size == 0 ? -1 : d.received / (double) d.size;
    }

    // ------------------------------------------------------------------ import
    /** Import of a file the player chose (music folder, file dialog or dropped on the screen): queued after the others. */
    public static void importFile(BlockPos console, Path file) {
        importFiles(console, java.util.List.of(file));
    }

    private static void start(BlockPos console, Path file) {
        String name = file.getFileName().toString();
        checking = true;
        status(Component.translatable("audio.concertstage.import_checking", name), 0xFFB8BEC6);
        WORKER.execute(() -> {
            try {
                if (!Files.isRegularFile(file)) throw new IOException("not a file");
                long size = Files.size(file);
                if (size > maxFileBytes) { fail(Component.translatable("audio.concertstage.err_size", maxFileBytes >> 20)); return; }
                if (size > libraryFree) { fail(Component.translatable("audio.concertstage.err_library_full")); return; }
                String type = AudioFiles.detect(file);
                if (type == null || !AudioFiles.decodes(file, type)) { fail(Component.translatable("audio.concertstage.err_format")); return; }
                String sha1 = AudioFiles.sha1(file);
                AudioCache.store(file, sha1, type);
                String title = name.replaceFirst("(?i)\\.(mp3|wav)$", "");
                Minecraft.getInstance().execute(() -> begin(new Upload(console, AudioCache.file(sha1, type), sha1, type, title, size)));
            } catch (IOException | RuntimeException e) {
                fail(Component.translatable("audio.concertstage.err_io"));
            }
        });
    }

    private static void fail(Component c) {
        Minecraft.getInstance().execute(() -> {
            status(c, 0xFFE5484D);
            checking = false;
            batchFailed++;
            pump();
        });
    }

    private static void begin(Upload u) {
        checking = false;
        if (upload != null) return;
        upload = u;
        FriendlyByteBuf b = AudioNet.message(AudioNet.C_UPLOAD_BEGIN);
        b.writeBlockPos(u.console);
        b.writeUtf(u.sha1);
        b.writeUtf(u.name.length() > 96 ? u.name.substring(0, 96) : u.name);
        b.writeUtf(u.type);
        b.writeLong(u.size);
        AudioNet.toServer(b);
        status(Component.translatable("audio.concertstage.import_sending", u.name), 0xFF35C9E8);
    }

    public static void cancelUpload() {
        Upload u = upload;
        if (u == null) return;
        closeQuietly(u);
        upload = null;
        QUEUE.clear();
        batchTotal = 0;
        AudioNet.toServer(AudioNet.message(AudioNet.C_UPLOAD_CANCEL));
        status(Component.translatable("audio.concertstage.import_cancelled"), 0xFFB8BEC6);
    }

    private static void closeQuietly(Upload u) {
        try { if (u.in != null) u.in.close(); } catch (IOException ignored) { }
    }

    /** Every client tick: next chunks of the import. */
    static void tick() {
        Upload u = upload;
        if (u == null || !u.sending) return;
        try {
            if (u.in == null) u.in = new RandomAccessFile(u.file.toFile(), "r");
            for (int k = 0; k < 2 && u.sent < u.size; k++) {
                int n = (int) Math.min(AudioNet.CHUNK, u.size - u.sent);
                byte[] data = new byte[n];
                u.in.seek(u.sent);
                u.in.readFully(data);
                FriendlyByteBuf b = AudioNet.message(AudioNet.C_UPLOAD_CHUNK);
                b.writeUtf(u.sha1);
                b.writeVarInt(u.index++);
                b.writeByteArray(data);
                AudioNet.toServer(b);
                u.sent += n;
            }
            if (u.sent >= u.size) { u.sending = false; closeQuietly(u); }
        } catch (IOException e) {
            cancelUpload();
            status(Component.translatable("audio.concertstage.err_io"), 0xFFE5484D);
        }
    }

    // ------------------------------------------------------------------ download
    /** Asks the server for a track the console at {@code console} plays (once; nothing if downloads are off). */
    public static void request(BlockPos console, String sha1, String type) {
        if (!AudioSettings.downloads || DOWNLOADS.containsKey(sha1) || REFUSED.contains(sha1) || DOWNLOADS.size() >= 3) return;
        if (upload != null && upload.sha1.equals(sha1)) return;
        DOWNLOADS.put(sha1, new Download(sha1, type, 0, null));
        FriendlyByteBuf b = AudioNet.message(AudioNet.C_DOWNLOAD);
        b.writeBlockPos(console);
        b.writeUtf(sha1);
        AudioNet.toServer(b);
    }

    public static boolean downloading(String sha1) {
        return DOWNLOADS.containsKey(sha1);
    }

    // ------------------------------------------------------------------ messages from the server
    static void receive(int type, FriendlyByteBuf b) {
        switch (type) {
            case AudioNet.S_STATE -> RemoteConsoles.receive(b.readBlockPos(), b.readNbt());
            case AudioNet.S_INFO -> {
                b.readBlockPos();
                canUpload = b.readBoolean();
                maxFileBytes = b.readLong();
                libraryFree = b.readLong();
            }
            case AudioNet.S_UPLOAD -> {
                String sha1 = b.readUtf();
                int st = b.readVarInt();
                long value = b.readLong();
                String key = b.readUtf();
                Upload u = upload;
                if (u == null || !u.sha1.equals(sha1)) {
                    if (st == AudioNet.UP_ERROR && !key.isEmpty()) status(Component.translatable(key, value >> 20), 0xFFE5484D);
                    return;
                }
                switch (st) {
                    case AudioNet.UP_SEND -> u.sending = true;
                    case AudioNet.UP_CHECKING -> status(Component.translatable("audio.concertstage.import_server_check", u.name), 0xFF35C9E8);
                    case AudioNet.UP_DONE -> {
                        closeQuietly(u);
                        upload = null;
                        batchDone++;
                        status(Component.translatable("audio.concertstage.import_done", u.name), 0xFF4CD08A);
                        pump();
                    }
                    default -> {
                        closeQuietly(u);
                        upload = null;
                        batchFailed++;
                        status(Component.translatable(key.isEmpty() ? "audio.concertstage.err_io" : key, value >> 20), 0xFFE5484D);
                        pump();
                    }
                }
            }
            case AudioNet.S_FILE_BEGIN -> {
                String sha1 = b.readUtf();
                long size = b.readLong();
                String t = b.readUtf();
                Download old = DOWNLOADS.get(sha1);
                if (old == null || !AudioFiles.validSha1(sha1) || !(t.equals(AudioFiles.MP3) || t.equals(AudioFiles.WAV))) return;
                try {
                    Path part = AudioCache.partFile(sha1);
                    Download d = new Download(sha1, t, size, part);
                    d.out = Files.newOutputStream(part, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    DOWNLOADS.put(sha1, d);
                } catch (IOException e) {
                    DOWNLOADS.remove(sha1);
                    REFUSED.add(sha1);
                }
            }
            case AudioNet.S_FILE_CHUNK -> {
                String sha1 = b.readUtf();
                int index = b.readVarInt();
                byte[] data = b.readByteArray(AudioNet.CHUNK + 64);
                Download d = DOWNLOADS.get(sha1);
                if (d == null || d.out == null) return;
                try {
                    if (index != d.next || d.received + data.length > d.size) throw new IOException("out of order");
                    d.out.write(data);
                    d.next++;
                    d.received += data.length;
                    if (d.received >= d.size) {
                        d.out.close();
                        WORKER.execute(() -> {
                            boolean ok = AudioCache.accept(d.part, d.sha1, d.type);
                            Minecraft.getInstance().execute(() -> {
                                DOWNLOADS.remove(d.sha1);
                                if (!ok) REFUSED.add(d.sha1);
                            });
                        });
                    }
                } catch (IOException e) {
                    try { d.out.close(); Files.deleteIfExists(d.part); } catch (IOException ignored) { }
                    DOWNLOADS.remove(sha1);
                    REFUSED.add(sha1);
                }
            }
            case AudioNet.S_FILE_ERROR -> {
                String sha1 = b.readUtf();
                Download d = DOWNLOADS.remove(sha1);
                if (d != null && d.out != null) try { d.out.close(); Files.deleteIfExists(d.part); } catch (IOException ignored) { }
                REFUSED.add(sha1);
            }
            default -> { }
        }
    }

    /** Left the world: everything dropped. */
    static void reset() {
        Upload u = upload;
        if (u != null) closeQuietly(u);
        upload = null;
        QUEUE.clear();
        checking = false;
        batchTotal = 0;
        for (Download d : DOWNLOADS.values()) if (d.out != null) try { d.out.close(); Files.deleteIfExists(d.part); } catch (IOException ignored) { }
        DOWNLOADS.clear();
        REFUSED.clear();
        status(Component.empty(), 0xFFB8BEC6);
    }
}
