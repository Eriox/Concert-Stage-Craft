package com.concertstage.audio.net;

import com.concertstage.StageContent;
import com.concertstage.audio.AudioConsoleBlockEntity;
import com.concertstage.audio.AudioGroups;
import com.concertstage.audio.SpeakerBlockEntity;
import com.concertstage.audio.core.AudioFiles;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the sound system: console commands, array angles, the import of a track (upload in chunks, size limit, permission,
 * check of the whole file off the game thread: SHA-1, real MP3 / WAV content, length) and the paced sending of a track to the
 * players near a console that plays it.
 */
public final class AudioServer {
    private static final ExecutorService CHECKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CSC audio import check");
        t.setDaemon(true);
        return t;
    });
    private static final Map<UUID, long[]> SNAPSHOT_BUDGETS = new HashMap<>();
    private static MinecraftServer server;
    private static ServerAudioConfig config;
    private static AudioLibrary library;
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private static final Map<UUID, Deque<Download>> DOWNLOADS = new HashMap<>();

    private static final java.util.Map<java.util.UUID,long[]> PLANNER_BUDGET=new java.util.HashMap<>();
    private AudioServer() {}

    private static final class Upload {
        final UUID player;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim;
        final BlockPos console;
        final String sha1, name, type, owner;
        final long size;
        final Path tmp;
        OutputStream out;
        long received;
        int next;
        long lastTick;

        Upload(ServerPlayer p, BlockPos console, String sha1, String name, String type, long size, Path tmp) {
            this.player = p.getUUID();
            this.dim = p.level().dimension();
            this.console = console;
            this.sha1 = sha1;
            this.name = name;
            this.type = type;
            this.owner = p.getGameProfile().getName();
            this.size = size;
            this.tmp = tmp;
        }
    }

    private static final class Download {
        final String sha1;
        final Path file;
        final long size;
        FileChannel channel;
        long sent;
        int index;

        Download(String sha1, Path file, long size) {
            this.sha1 = sha1;
            this.file = file;
            this.size = size;
        }
    }

    private static void ensure(MinecraftServer s) {
        if (server == s) return;
        stopped();
        server = s;
        config = ServerAudioConfig.load(s.getServerDirectory().toPath().resolve("config"));
        library = new AudioLibrary(s.getWorldPath(LevelResource.ROOT).resolve("concertstage_audio"));
    }

    /** 1.2.0: the audio library of the world (the lighting analysis reads the tracks from it). */
    public static AudioLibrary library(MinecraftServer s) {
        ensure(s);
        return library;
    }

    public static ServerAudioConfig config(MinecraftServer s) {
        ensure(s);
        return config;
    }

    /** Server stopped (or a new world): open files closed, unfinished imports deleted. */
    public static void stopped() {
        for (Upload u : UPLOADS.values()) abortQuietly(u);
        UPLOADS.clear();
        if (server != null) AudioContinuity.clear();
        PLANNER_BUDGET.clear();
        SNAPSHOT_BUDGETS.clear();
        for (Deque<Download> q : DOWNLOADS.values()) for (Download d : q) closeQuietly(d);
        DOWNLOADS.clear();
        server = null;
        library = null;
    }

    public static boolean canUpload(ServerPlayer p) {
        MinecraftServer s = p.getServer();
        if (s == null) return false;
        ensure(s);
        return s.isSingleplayerOwner(p.getGameProfile()) || p.hasPermissions(config.uploadPermissionLevel);
    }

    private static AudioConsoleBlockEntity console(ServerPlayer p, BlockPos pos, double reach) {
        ServerLevel level = p.serverLevel();
        if (!level.isLoaded(pos) || p.distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach) return null;
        return level.getBlockEntity(pos) instanceof AudioConsoleBlockEntity c ? c : null;
    }

    // ------------------------------------------------------------------ receive
    static void receive(ServerPlayer p, FriendlyByteBuf b) {
        ensure(p.getServer());
        int type = b.readVarInt();
        switch (type) {
            case AudioNet.C_GROUP_CONFIG, AudioNet.C_DELAY_PLAN -> {
                BlockPos pos=b.readBlockPos();AudioConsoleBlockEntity c=console(p,pos,12);if(c==null)return;
                long tick=p.serverLevel().getGameTime();long[] budget=PLANNER_BUDGET.computeIfAbsent(p.getUUID(),k->new long[]{tick,0});if(tick-budget[0]>=20){budget[0]=tick;budget[1]=0;}if(budget[1]++>=8)return;
                if(type==AudioNet.C_GROUP_CONFIG){int group=b.readVarInt();float delay=b.readFloat();String name=b.readUtf(32);if(group<0||group>=6||!Float.isFinite(delay)||delay<0||delay>1500)return;c.setGroupDelay(group,delay);c.setGroupLabel(group,name);}
                else{var tag=b.readNbt();if(tag!=null)c.setPlannerPlan(tag);}
            }
            case AudioNet.C_STATE -> {
                BlockPos anchor = b.readBlockPos();
                if (!p.serverLevel().isLoaded(anchor) || p.distanceToSqr(Vec3.atCenterOf(anchor)) > 544 * 544) return;
                if (!(p.serverLevel().getBlockEntity(anchor) instanceof SpeakerBlockEntity a) || a.console() == null) return;
                long tick = p.serverLevel().getGameTime();
                long[] budget = SNAPSHOT_BUDGETS.computeIfAbsent(p.getUUID(), k -> new long[] {tick, 0});
                if (tick - budget[0] >= 20) { budget[0] = tick; budget[1] = 0; }
                if (budget[1]++ >= 8) return;
                AudioContinuity.keep(p.serverLevel(), a.console());
                if (!p.serverLevel().isLoaded(a.console())) return;
                var packet = AudioNet.message(AudioNet.S_STATE); packet.writeBlockPos(a.console());
                packet.writeNbt(p.serverLevel().getBlockEntity(a.console()) instanceof AudioConsoleBlockEntity c ? c.getUpdateTag() : null);
                AudioNet.toClient(p, packet);
            }
            case AudioNet.C_CONSOLE -> consoleCommand(p, b.readBlockPos(), b.readVarInt(), b.readVarInt(), b.readFloat());
            case AudioNet.C_ARRAY -> {
                BlockPos pos = b.readBlockPos();
                float yaw = b.readFloat(), tilt = b.readFloat();
                int n = b.readVarInt();
                if (n < 0 || n > SpeakerBlockEntity.MAX_LINE) return;
                float[] splays = new float[n];
                for (int i = 0; i < n; i++) splays[i] = b.readFloat();
                if (!(p.getMainHandItem().is(StageContent.ARRAY_TOOL) || p.getOffhandItem().is(StageContent.ARRAY_TOOL))) return;
                if (p.distanceToSqr(Vec3.atCenterOf(pos)) > 48 * 48 || !p.serverLevel().isLoaded(pos)) return;
                if (p.serverLevel().getBlockEntity(pos) instanceof SpeakerBlockEntity s && Float.isFinite(yaw) && Float.isFinite(tilt))
                    if (!s.tryApplyAngles(yaw, tilt, splays)) p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.concertstage.array_obstructed"), true);
            }
            case AudioNet.C_INFO -> {
                BlockPos pos = b.readBlockPos();
                FriendlyByteBuf o = AudioNet.message(AudioNet.S_INFO);
                o.writeBlockPos(pos);
                o.writeBoolean(canUpload(p));
                o.writeLong(config.maxFileBytes());
                o.writeLong(Math.max(0, config.maxLibraryBytes() - library.usedBytes()));
                AudioNet.toClient(p, o);
            }
            case AudioNet.C_UPLOAD_BEGIN -> uploadBegin(p, b.readBlockPos(), b.readUtf(64), b.readUtf(200), b.readUtf(8), b.readLong());
            case AudioNet.C_UPLOAD_CHUNK -> uploadChunk(p, b.readUtf(64), b.readVarInt(), b.readByteArray(AudioNet.CHUNK + 64));
            case AudioNet.C_UPLOAD_CANCEL -> {
                Upload u = UPLOADS.remove(p.getUUID());
                if (u != null) abortQuietly(u);
            }
            case AudioNet.C_DOWNLOAD -> download(p, b.readBlockPos(), b.readUtf(64));
            default -> { }
        }
    }

    private static void consoleCommand(ServerPlayer p, BlockPos pos, int action, int a, float f) {
        AudioConsoleBlockEntity c = console(p, pos, 12);
        if (c == null) return;
        switch (action) {
            case AudioNet.PLAY -> c.play();
            case AudioNet.PAUSE -> c.pause();
            case AudioNet.STOP -> c.stop();
            case AudioNet.NEXT -> c.next();
            case AudioNet.PREV -> c.previous();
            case AudioNet.SELECT -> c.select(a);
            case AudioNet.REMOVE -> c.removeTrack(a);
            case AudioNet.MOVE -> c.moveTrack(a, f < 0 ? -1 : 1);
            case AudioNet.MASTER -> c.setMaster(f);
            case AudioNet.FADER -> c.setFader(a, f);
            case AudioNet.MUTE -> c.toggleMute(a);
            case AudioNet.EQ -> c.setEq(a, f);
            case AudioNet.REPEAT -> c.toggleRepeat();
            case AudioNet.SEEK -> c.seek(a);
            case AudioNet.UNLINK, AudioNet.SET_GROUP -> {
                if (a < 0 || a >= c.speakers().size()) return;
                BlockPos sp = c.speakers().get(a);
                SpeakerBlockEntity s = p.serverLevel().isLoaded(sp) && p.serverLevel().getBlockEntity(sp) instanceof SpeakerBlockEntity x ? x : null;
                if (action == AudioNet.UNLINK) {
                    c.unlink(sp);
                    if (s != null && pos.equals(s.console())) s.unlink();
                } else if (s != null) {
                    s.setGroup(Math.max(0, Math.min(AudioGroups.COUNT - 1, (int) f)));
                    c.setChanged();
                    p.serverLevel().sendBlockUpdated(pos, c.getBlockState(), c.getBlockState(), 2);
                }
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ upload
    private static void uploadStatus(ServerPlayer p, String sha1, int status, long value, String key) {
        FriendlyByteBuf o = AudioNet.message(AudioNet.S_UPLOAD);
        o.writeUtf(sha1);
        o.writeVarInt(status);
        o.writeLong(value);
        o.writeUtf(key == null ? "" : key);
        AudioNet.toClient(p, o);
    }

    private static void uploadBegin(ServerPlayer p, BlockPos pos, String sha1, String name, String type, long size) {
        if (!AudioFiles.validSha1(sha1)) return;
        AudioConsoleBlockEntity c = console(p, pos, 12);
        if (c == null) { uploadStatus(p, sha1, AudioNet.UP_ERROR, 0, "audio.concertstage.err_console"); return; }
        if (!canUpload(p)) { uploadStatus(p, sha1, AudioNet.UP_ERROR, 0, "audio.concertstage.err_permission"); return; }
        if (!type.equals(AudioFiles.MP3) && !type.equals(AudioFiles.WAV)) { uploadStatus(p, sha1, AudioNet.UP_ERROR, 0, "audio.concertstage.err_format"); return; }
        name = cleanName(name);
        AudioLibrary.Meta known = library.get(sha1);
        if (known != null) {                                              // already on the server: nothing to send
            addToConsole(p, c, known);
            return;
        }
        if (size <= 0 || size > config.maxFileBytes()) { uploadStatus(p, sha1, AudioNet.UP_ERROR, config.maxFileBytes(), "audio.concertstage.err_size"); return; }
        if (library.usedBytes() + size > config.maxLibraryBytes()) { uploadStatus(p, sha1, AudioNet.UP_ERROR, 0, "audio.concertstage.err_library_full"); return; }
        Upload old = UPLOADS.remove(p.getUUID());
        if (old != null) abortQuietly(old);
        try {
            Path tmp = library.tempFile(sha1 + "-" + p.getUUID());
            Upload u = new Upload(p, pos, sha1, name, type, size, tmp);
            u.out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            u.lastTick = server.getTickCount();
            UPLOADS.put(p.getUUID(), u);
            uploadStatus(p, sha1, AudioNet.UP_SEND, 0, null);
        } catch (IOException e) {
            uploadStatus(p, sha1, AudioNet.UP_ERROR, 0, "audio.concertstage.err_io");
        }
    }

    private static String cleanName(String name) {
        StringBuilder sb = new StringBuilder();
        for (char ch : name.toCharArray()) if (ch >= 32 && ch != 127 && ch != '§') sb.append(ch);
        String s = sb.toString().trim();
        if (s.isEmpty()) s = "track";
        return s.length() > 96 ? s.substring(0, 96) : s;
    }

    private static void uploadChunk(ServerPlayer p, String sha1, int index, byte[] data) {
        Upload u = UPLOADS.get(p.getUUID());
        if (u == null || !u.sha1.equals(sha1)) return;
        if (index != u.next || u.received + data.length > u.size) {
            UPLOADS.remove(p.getUUID());
            abortQuietly(u);
            uploadStatus(p, sha1, AudioNet.UP_ERROR, 0, "audio.concertstage.err_transfer");
            return;
        }
        try {
            u.out.write(data);
        } catch (IOException e) {
            UPLOADS.remove(p.getUUID());
            abortQuietly(u);
            uploadStatus(p, sha1, AudioNet.UP_ERROR, 0, "audio.concertstage.err_io");
            return;
        }
        u.next++;
        u.received += data.length;
        u.lastTick = server.getTickCount();
        if (u.received < u.size) return;
        // complete: checked off the game thread
        UPLOADS.remove(p.getUUID());
        try { u.out.close(); } catch (IOException ignored) { }
        uploadStatus(p, sha1, AudioNet.UP_CHECKING, u.size, null);
        MinecraftServer s = server;
        AudioLibrary lib = library;
        CHECKER.execute(() -> {
            String error = null;
            long duration = -1;
            try {
                if (!AudioFiles.sha1(u.tmp).equals(u.sha1)) error = "audio.concertstage.err_transfer";
                else if (!u.type.equals(AudioFiles.detect(u.tmp)) || !AudioFiles.decodes(u.tmp, u.type)) error = "audio.concertstage.err_format";
                else if ((duration = AudioFiles.durationMs(u.tmp, u.type)) <= 0) error = "audio.concertstage.err_format";
            } catch (IOException e) {
                error = "audio.concertstage.err_io";
            }
            final String err = error;
            final long dur = duration;
            s.execute(() -> finishUpload(s, lib, u, err, dur));
        });
    }

    private static void finishUpload(MinecraftServer s, AudioLibrary lib, Upload u, String error, long duration) {
        ServerPlayer p = s.getPlayerList().getPlayer(u.player);
        if (error != null || s != server) {
            abortQuietly(u);
            if (p != null) uploadStatus(p, u.sha1, AudioNet.UP_ERROR, 0, error == null ? "audio.concertstage.err_io" : error);
            return;
        }
        AudioLibrary.Meta m;
        try {
            m = lib.add(u.tmp, new AudioLibrary.Meta(u.sha1, u.name, u.type, u.size, duration, u.owner));
        } catch (IOException e) {
            abortQuietly(u);
            if (p != null) uploadStatus(p, u.sha1, AudioNet.UP_ERROR, 0, "audio.concertstage.err_io");
            return;
        }
        ServerLevel level = s.getLevel(u.dim);
        if (level != null && level.isLoaded(u.console) && level.getBlockEntity(u.console) instanceof AudioConsoleBlockEntity c && p != null) addToConsole(p, c, m);
        else if (p != null) uploadStatus(p, u.sha1, AudioNet.UP_ERROR, 0, "audio.concertstage.err_console");
    }

    private static void addToConsole(ServerPlayer p, AudioConsoleBlockEntity c, AudioLibrary.Meta m) {
        boolean ok = c.addTrack(new AudioConsoleBlockEntity.Track(m.sha1(), m.name(), m.type(), m.size(), m.durationMs(), m.owner()));
        uploadStatus(p, m.sha1(), ok ? AudioNet.UP_DONE : AudioNet.UP_ERROR, m.durationMs(), ok ? null : "audio.concertstage.err_playlist_full");
    }

    private static void abortQuietly(Upload u) {
        try { if (u.out != null) u.out.close(); } catch (IOException ignored) { }
        try { Files.deleteIfExists(u.tmp); } catch (IOException ignored) { }
    }

    // ------------------------------------------------------------------ download
    private static void download(ServerPlayer p, BlockPos pos, String sha1) {
        if (!AudioFiles.validSha1(sha1)) return;
        AudioConsoleBlockEntity c = console(p, pos, config.distributionRange);
        if (c == null && p.serverLevel().isLoaded(pos) && AudioContinuity.canHear(p, pos)
                && p.serverLevel().getBlockEntity(pos) instanceof AudioConsoleBlockEntity remote) c = remote;
        AudioLibrary.Meta m = library.get(sha1);
        boolean listed = false;
        if (c != null) for (AudioConsoleBlockEntity.Track t : c.playlist()) if (t.sha1().equals(sha1)) { listed = true; break; }
        if (c == null || m == null || !listed) {
            FriendlyByteBuf o = AudioNet.message(AudioNet.S_FILE_ERROR);
            o.writeUtf(sha1);
            AudioNet.toClient(p, o);
            return;
        }
        Deque<Download> q = DOWNLOADS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        for (Download d : q) if (d.sha1.equals(sha1)) return;
        if (q.size() >= 4) return;
        q.add(new Download(sha1, library.file(m), m.size()));
        FriendlyByteBuf o = AudioNet.message(AudioNet.S_FILE_BEGIN);
        o.writeUtf(sha1);
        o.writeLong(m.size());
        o.writeUtf(m.type());
        AudioNet.toClient(p, o);
    }

    private static void closeQuietly(Download d) {
        try { if (d.channel != null) d.channel.close(); } catch (IOException ignored) { }
    }

    /** Every server tick: sends the next chunks of the tracks being downloaded, drops stalled imports. */
    public static void tick(MinecraftServer s) {
        if (s == null) return;
        AudioContinuity.tick(s);
        if (server != s) return;
        if (!UPLOADS.isEmpty()) {
            Iterator<Upload> it = UPLOADS.values().iterator();
            while (it.hasNext()) {
                Upload u = it.next();
                if (s.getTickCount() - u.lastTick > 20 * 60 || s.getPlayerList().getPlayer(u.player) == null) { abortQuietly(u); it.remove(); }
            }
        }
        if (DOWNLOADS.isEmpty()) return;
        long budget = Math.max(AudioNet.CHUNK, config.downloadKbps * 1024L / 20);
        Iterator<Map.Entry<UUID, Deque<Download>>> it = DOWNLOADS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
            Deque<Download> q = e.getValue();
            if (p == null) { for (Download d : q) closeQuietly(d); it.remove(); continue; }
            long left = budget;
            while (left > 0 && !q.isEmpty()) {
                Download d = q.peek();
                try {
                    if (d.channel == null) d.channel = FileChannel.open(d.file, StandardOpenOption.READ);
                    int n = (int) Math.min(AudioNet.CHUNK, d.size - d.sent);
                    ByteBuffer buf = ByteBuffer.allocate(n);
                    while (buf.hasRemaining() && d.channel.read(buf, d.sent + buf.position()) > 0) { }
                    FriendlyByteBuf o = AudioNet.message(AudioNet.S_FILE_CHUNK);
                    o.writeUtf(d.sha1);
                    o.writeVarInt(d.index++);
                    o.writeByteArray(java.util.Arrays.copyOf(buf.array(), buf.position()));
                    AudioNet.toClient(p, o);
                    d.sent += buf.position();
                    left -= buf.position();
                    if (buf.position() == 0 || d.sent >= d.size) { closeQuietly(d); q.poll(); }
                } catch (IOException ex) {
                    closeQuietly(d);
                    q.poll();
                    FriendlyByteBuf o = AudioNet.message(AudioNet.S_FILE_ERROR);
                    o.writeUtf(d.sha1);
                    AudioNet.toClient(p, o);
                }
            }
            if (q.isEmpty()) it.remove();
        }
    }
}
