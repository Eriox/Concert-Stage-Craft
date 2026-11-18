package com.concertstage.vision;

import com.concertstage.light.core.Bytes;
import com.concertstage.light.net.LightNet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * CSC 1.3.0 - LiveVision messages, carried by the CSC light channel ({@link LightNet}: bytes, both directions, types 100 and above).
 *
 * <p>Server to players of a dimension: the wall registry, the cameras, the processor outputs (only when something changes: a TAKE,
 * a transition, a wall placed). No video frame ever goes through the network: every client renders the cameras and decodes the media
 * itself, from the same game-clock timing. Images of the server's media folder are sent once, in chunks, to the clients that ask
 * for them; video files are read from each player's own media folder (see {@link #mediaFolder}).
 *
 * <p>Players to server: regie, wall and camera commands. Checked on the server: the player may build (not adventure / spectator) and
 * is within {@link #REACH} blocks of the processor, the camera or the wall.
 */
public final class VisionNet {
    public static final int S_FULL = 100, S_WALL = 101, S_WALL_GONE = 102, S_OUTPUTS = 103, S_CAMERAS = 104, S_MEDIA_LIST = 105, S_MEDIA_CHUNK = 106;
    public static final int C_HELLO = 110, C_PROC = 111, C_WALL = 112, C_CAM = 113, C_MEDIA = 114;
    // processor operations
    public static final int P_TAKE = 1, P_PREVIEW = 2, P_PROGRAM = 3, P_TRANSITION = 4, P_AUTO = 5, P_AUTO_PAUSE = 6, P_MIRROR = 7,
        P_BRIGHTNESS = 8, P_MEDIA = 9, P_ASSIGN = 10, P_UNASSIGN = 11, P_NAME = 12;
    // wall operations
    public static final int W_NAME = 1, W_BRIGHTNESS = 2, W_ANGLE = 3, W_QUALITY = 4;   // rev. 1.3.0-3: angle + pivot, quality + fps
    // camera operations
    public static final int K_MOVE = 1, K_STORE = 2, K_RECALL = 3, K_NAME = 4, K_NUDGE = 5, K_VIDEO = 6;
    public static final double REACH = 96;
    public static final int MEDIA_CHUNK = 512 * 1024, MEDIA_MAX = 16 << 20;

    private static Consumer<byte[]> clientHandler = b -> { };

    private VisionNet() {}

    /** LiveVision messages use the types 100..127 (one varint byte). */
    public static boolean isVision(byte[] data) {
        return data != null && data.length > 0 && (data[0] & 0xFF) >= 100 && (data[0] & 0xFF) < 128;
    }

    public static void setClientHandler(Consumer<byte[]> h) { clientHandler = h; }

    public static void receiveOnClient(byte[] data) { clientHandler.accept(data); }

    // ------------------------------------------------------------------ server -> clients
    private static void toDimension(ServerLevel level, byte[] data) {
        for (ServerPlayer p : level.players()) LightNet.toClient(p, data);
    }

    static void writeWall(Bytes.Writer w, WallData.Wall wall) {
        w.sLong(wall.id).str(wall.name).varInt(wall.facing.get3DDataValue()).sInt(wall.plane).sInt(wall.u0).sInt(wall.v0).sInt(wall.u1).sInt(wall.v1)
            .varInt(wall.count).varInt(wall.brightness).sLong(wall.processor).varInt(wall.output)
            .f32(wall.angle).varInt(wall.pivot).varInt(wall.quality).varInt(wall.fps);
        long[] bits = wall.panels.toLongArray();
        w.varInt(bits.length);
        for (long b : bits) w.sLong(b);
    }

    public static WallData.Wall readWall(Bytes.Reader r) {
        WallData.Wall w = new WallData.Wall();
        w.id = r.sLong(); w.name = r.str(64); w.facing = net.minecraft.core.Direction.from3DDataValue(r.varInt()); w.plane = r.sInt();
        w.u0 = r.sInt(); w.v0 = r.sInt(); w.u1 = r.sInt(); w.v1 = r.sInt(); w.count = r.varInt(); w.brightness = r.varInt();
        w.processor = r.sLong(); w.output = r.varInt();
        w.angle = WallData.clampAngle(r.f32()); w.pivot = Math.max(0, Math.min(2, r.varInt()));
        w.quality = WallData.clampQuality(r.varInt()); w.fps = Math.max(0, Math.min(60, r.varInt()));
        int n = r.count(WallData.MAX_SIDE * WallData.MAX_SIDE / 64 + 2);
        long[] bits = new long[n];
        for (int i = 0; i < n; i++) bits[i] = r.sLong();
        w.panels = java.util.BitSet.valueOf(bits);
        return w;
    }

    static void writeOutputs(Bytes.Writer w, long pos, OutputState[] outs) {
        w.sLong(pos).varInt(outs.length);
        for (OutputState o : outs) o.write(w);
    }

    static void writeCameras(Bytes.Writer w, CameraData d) {
        w.varInt(d.cams.size());
        for (CameraData.Cam c : d.cams.values()) w.sLong(c.pos()).varInt(c.kind()).varInt(c.number()).str(c.name());
    }

    public static void wallChanged(ServerLevel level, WallData.Wall wall) {
        Bytes.Writer w = new Bytes.Writer().varInt(S_WALL);
        writeWall(w, wall);
        toDimension(level, w.toBytes());
    }

    public static void wallRemoved(ServerLevel level, long id) {
        toDimension(level, new Bytes.Writer().varInt(S_WALL_GONE).sLong(id).toBytes());
    }

    public static void camerasChanged(ServerLevel level) {
        Bytes.Writer w = new Bytes.Writer().varInt(S_CAMERAS);
        writeCameras(w, CameraData.get(level));
        toDimension(level, w.toBytes());
    }

    public static void outputsChanged(ServerLevel level, VisionProcessorBlockEntity be) {
        WallData d = WallData.get(level);
        OutputState[] snap = new OutputState[VisionProcessorBlockEntity.OUTPUTS];
        for (int i = 0; i < snap.length; i++) snap[i] = be.effective(i);
        d.processors.put(be.getBlockPos().asLong(), snap);
        d.setDirty();
        Bytes.Writer w = new Bytes.Writer().varInt(S_OUTPUTS);
        writeOutputs(w, be.getBlockPos().asLong(), snap);
        toDimension(level, w.toBytes());
    }

    /** Everything of the dimension, to one player (on joining, on changing dimension). */
    public static void sendFull(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        WallData d = WallData.get(level);
        Bytes.Writer w = new Bytes.Writer().varInt(S_FULL).str(level.dimension().location().toString()).varInt(d.walls.size());
        for (WallData.Wall wall : d.walls.values()) writeWall(w, wall);
        w.varInt(d.processors.size());
        for (var e : d.processors.entrySet()) writeOutputs(w, e.getKey(), e.getValue());
        writeCameras(w, CameraData.get(level));
        LightNet.toClient(p, w.toBytes());
    }

    // ------------------------------------------------------------------ media
    /** The server's media folder: {@code <world>/csc_livevision/media} (created with a README on first use). */
    public static Path mediaFolder(net.minecraft.server.MinecraftServer server) {
        Path p = server.getWorldPath(LevelResource.ROOT).resolve("csc_livevision").resolve("media");
        try {
            if (!Files.isDirectory(p)) {
                Files.createDirectories(p);
                Files.writeString(p.resolve("README.txt"), "CSC LiveVision media folder (server).\n"
                    + "Images (.png .jpg .jpeg .bmp .webp) and animated GIF placed here are listed in the regie and sent to the players (up to 16 MB each).\n"
                    + "Videos (.mp4 .mov .webm .mkv .avi .m4v .flv .wmv .mpg .ts .ogv) are listed here but read by each player from his own folder\n"
                    + "<minecraft>/config/concertstage/livevision/media (same file name), decoded with FFmpeg (see the LiveVision guide).\n");
            }
        } catch (Exception ignored) { }
        return p;
    }

    public static boolean isVideo(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".webm") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".m4v")
            || n.endsWith(".flv") || n.endsWith(".wmv") || n.endsWith(".mpg") || n.endsWith(".mpeg") || n.endsWith(".ts") || n.endsWith(".ogv");   // 1.2.1
    }

    public static boolean isImage(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif")
            || n.endsWith(".bmp") || n.endsWith(".webp");                       // 1.2.1 (WebP read by the built-in FFmpeg)
    }

    private static void sendMediaList(ServerPlayer p) {
        Path dir = mediaFolder(p.server);
        java.util.List<String> names = new java.util.ArrayList<>();
        try (var s = Files.list(dir)) {
            s.map(f -> f.getFileName().toString()).filter(n -> isImage(n) || isVideo(n)).sorted().limit(512).forEach(names::add);
        } catch (Exception ignored) { }
        Bytes.Writer w = new Bytes.Writer().varInt(S_MEDIA_LIST).varInt(names.size());
        for (String n : names) w.str(n);
        LightNet.toClient(p, w.toBytes());
    }

    private static void sendMedia(ServerPlayer p, String name) {
        if (name.contains("/") || name.contains("\\") || name.contains("..") || !isImage(name)) return;   // no path outside the folder
        Path f = mediaFolder(p.server).resolve(name);
        try {
            long size = Files.size(f);
            if (size > MEDIA_MAX) return;
            byte[] all = Files.readAllBytes(f);
            for (int off = 0; off < all.length || off == 0; off += MEDIA_CHUNK) {
                int len = Math.min(MEDIA_CHUNK, all.length - off);
                byte[] part = java.util.Arrays.copyOfRange(all, off, off + len);
                LightNet.toClient(p, new Bytes.Writer().varInt(S_MEDIA_CHUNK).str(name).varInt(all.length).varInt(off).bytes(part).toBytes());
                if (all.length == 0) break;
            }
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ players -> server
    private static boolean allowed(ServerPlayer p, BlockPos pos) {
        return p.mayBuild() && !p.isSpectator() && p.blockPosition().distSqr(pos) <= REACH * REACH;
    }

    public static void receiveOnServer(ServerPlayer p, byte[] data) {
        Bytes.Reader r = new Bytes.Reader(data);
        int type = r.varInt();
        ServerLevel level = p.serverLevel();
        switch (type) {
            case C_HELLO -> sendFull(p);
            case C_MEDIA -> {
                String name = r.str(200);
                if (name.isEmpty()) sendMediaList(p); else sendMedia(p, name);
            }
            case C_PROC -> {
                BlockPos pos = LightNet.readPos(r);
                if (!allowed(p, pos) || !(level.getBlockEntity(pos) instanceof VisionProcessorBlockEntity be)) return;
                processor(level, be, r);
            }
            case C_WALL -> {
                long id = r.sLong();
                WallData d = WallData.get(level);
                WallData.Wall w = d.walls.get(id);
                if (w == null || !allowed(p, w.pos((w.u0 + w.u1) / 2, (w.v0 + w.v1) / 2))) return;
                int op = r.varInt();
                if (op == W_NAME) w.name = r.str(48);
                else if (op == W_BRIGHTNESS) w.brightness = Math.max(0, Math.min(100, r.varInt()));
                else if (op == W_ANGLE) { w.angle = WallData.clampAngle(r.f32()); w.pivot = Math.max(0, Math.min(2, r.varInt())); }
                else if (op == W_QUALITY) { w.quality = WallData.clampQuality(r.varInt()); w.fps = Math.max(0, Math.min(60, r.varInt())); }
                d.setDirty();
                wallChanged(level, w);
            }
            case C_CAM -> {
                BlockPos pos = LightNet.readPos(r);
                if (!allowed(p, pos) || !(level.getBlockEntity(pos) instanceof CameraBlockEntity be)) return;
                camera(level, be, r);
            }
            default -> { }
        }
    }

    private static void processor(ServerLevel level, VisionProcessorBlockEntity be, Bytes.Reader r) {
        int op = r.varInt();
        int out = Math.max(0, Math.min(VisionProcessorBlockEntity.OUTPUTS - 1, r.varInt()));
        OutputState o = be.outputs[out];
        long now = level.getGameTime();
        switch (op) {
            case P_TAKE -> be.take(out, now);
            case P_PREVIEW -> { o.preview = r.str(256); be.changed(); }
            case P_PROGRAM -> be.cut(out, r.str(256), now);
            case P_TRANSITION -> { o.transition = Math.max(0, Math.min(2, r.varInt())); o.duration = Math.max(4, Math.min(60, r.varInt())); be.changed(); }
            case P_AUTO -> {
                o.auto = r.bool(); o.random = r.bool(); o.interval = Math.max(2, Math.min(120, r.varInt()));
                int n = r.count(32);
                o.autoCams.clear();
                for (int i = 0; i < n; i++) o.autoCams.add(r.sLong());
                o.nextSwitch = now + o.interval * 20L;
                o.autoPaused = false;
                be.changed();
            }
            case P_AUTO_PAUSE -> {
                o.autoPaused = r.bool();
                if (!o.autoPaused) o.nextSwitch = now + o.interval * 20L;
                be.changed();
            }
            case P_MIRROR -> { int m = r.sInt(); o.mirrorOf = m == out ? -1 : Math.max(-1, Math.min(VisionProcessorBlockEntity.OUTPUTS - 1, m)); be.changed(); }
            case P_BRIGHTNESS -> { o.brightness = Math.max(0, Math.min(100, r.varInt())); be.changed(); }
            case P_MEDIA -> {
                int action = r.varInt();                 // 0 play, 1 pause, 2 restart, 3 loop on, 4 loop off
                if (action == 0 && o.pausedAt >= 0) { o.mediaStart += now - o.pausedAt; o.pausedAt = -1; }
                else if (action == 1 && o.pausedAt < 0) o.pausedAt = now;
                else if (action == 2) { o.mediaStart = now; if (o.pausedAt >= 0) o.pausedAt = now; }
                else if (action == 3) o.loop = true;
                else if (action == 4) o.loop = false;
                be.changed();
            }
            case P_ASSIGN, P_UNASSIGN -> {
                long id = r.sLong();
                WallData d = WallData.get(level);
                WallData.Wall w = d.walls.get(id);
                if (w == null) return;
                if (op == P_ASSIGN) { w.processor = be.getBlockPos().asLong(); w.output = out; }
                else if (w.processor == be.getBlockPos().asLong()) w.processor = 0;
                d.setDirty();
                wallChanged(level, w);
                outputsChanged(level, be);
            }
            case P_NAME -> { be.name = r.str(32); be.changed(); }
            default -> { }
        }
    }

    private static void camera(ServerLevel level, CameraBlockEntity be, Bytes.Reader r) {
        int op = r.varInt();
        long now = level.getGameTime();
        switch (op) {
            case K_MOVE, K_NUDGE -> {
                Float[] v = new Float[CameraBlockEntity.AXES];
                int mask = r.varInt();
                for (int a = 0; a < CameraBlockEntity.AXES; a++) {
                    if ((mask & (1 << a)) == 0) continue;
                    float x = r.f32();
                    v[a] = op == K_NUDGE ? be.target(a) + x : x;
                }
                be.moveTo(v, now);
            }
            case K_STORE -> be.store(r.varInt());
            case K_RECALL -> be.recall(r.varInt(), now);
            case K_NAME -> { be.number = Math.max(1, Math.min(99, r.varInt())); be.name = r.str(32); be.changed(); }
            case K_VIDEO -> {                                   // rev. 4c: quality / images per second of the camera
                int h = r.varInt(), fps = r.varInt();
                be.videoHeight = java.util.Arrays.stream(CameraBlockEntity.HEIGHTS).anyMatch(v -> v == h) ? h : 0;
                be.videoFps = java.util.Arrays.stream(CameraBlockEntity.FPS).anyMatch(v -> v == fps) ? fps : 0;
                be.changed();
            }
            default -> { }
        }
    }
}
