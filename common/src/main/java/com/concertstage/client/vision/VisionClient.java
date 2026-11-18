package com.concertstage.client.vision;

import com.concertstage.light.core.Bytes;
import com.concertstage.light.net.LightNet;
import com.concertstage.vision.CameraData;
import com.concertstage.vision.OutputState;
import com.concertstage.vision.VisionNet;
import com.concertstage.vision.VisionProcessorBlockEntity;
import com.concertstage.vision.WallData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * CSC 1.3.0 - client side of LiveVision: the walls, processor outputs and cameras of the current dimension as the server sent them
 * (so walls are drawn beyond the loaded chunks), the media list, the media images received, and the commands to the server.
 */
public final class VisionClient {
    public static final Map<Long, WallData.Wall> WALLS = new LinkedHashMap<>();
    public static final Map<Long, OutputState[]> OUTPUTS = new HashMap<>();
    public static final Map<Long, CameraData.Cam> CAMERAS = new LinkedHashMap<>();
    public static final List<String> MEDIA = new ArrayList<>();
    /** Incoming images, by name: the bytes received so far. */
    private static final Map<String, byte[]> PARTS = new HashMap<>();
    private static final Map<String, Integer> RECEIVED = new HashMap<>();
    private static Object lastLevel;
    private static String dimension = "";
    private static boolean initialised;

    private VisionClient() {}

    public static void init() {
        if (initialised) return;
        initialised = true;
        VisionSettings.load();
        VisionNet.setClientHandler(VisionClient::handle);
        com.concertstage.vision.VisionClientHooks.wall = (pos, id) -> Minecraft.getInstance().setScreen(new WallScreen(pos, id));
        com.concertstage.vision.VisionClientHooks.regie = pos -> Minecraft.getInstance().setScreen(new RegieScreen(pos));
        com.concertstage.vision.VisionClientHooks.camera = pos -> Minecraft.getInstance().setScreen(new CameraScreen(pos));
    }

    /** Leaving a world: everything is forgotten. */
    public static void reset() {
        WALLS.clear(); OUTPUTS.clear(); CAMERAS.clear(); MEDIA.clear(); PARTS.clear(); RECEIVED.clear();
        lastLevel = null;
        dimension = "";
        MediaLibrary.clear();
        CameraFeeds.clear();
    }

    /** Every client tick (end). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            WALLS.clear(); OUTPUTS.clear(); CAMERAS.clear();
            if (mc.level != null) LightNet.toServer(new Bytes.Writer().varInt(VisionNet.C_HELLO).toBytes());
        }
        if (mc.level == null) return;
        MediaLibrary.tick();
        CameraFeeds.tick();
    }

    public static String dimension() { return dimension; }

    // ------------------------------------------------------------------ server messages
    private static void handle(byte[] data) {
        Bytes.Reader r = new Bytes.Reader(data);
        int type = r.varInt();
        switch (type) {
            case VisionNet.S_FULL -> {
                dimension = r.str(256);
                WALLS.clear(); OUTPUTS.clear(); CAMERAS.clear();
                int n = r.count(100000);
                for (int i = 0; i < n && r.ok(); i++) { WallData.Wall w = VisionNet.readWall(r); WALLS.put(w.id, w); }
                int p = r.count(100000);
                for (int i = 0; i < p && r.ok(); i++) readOutputs(r);
                readCameras(r);
            }
            case VisionNet.S_WALL -> { WallData.Wall w = VisionNet.readWall(r); if (r.ok()) WALLS.put(w.id, w); }
            case VisionNet.S_WALL_GONE -> WALLS.remove(r.sLong());
            case VisionNet.S_OUTPUTS -> readOutputs(r);
            case VisionNet.S_CAMERAS -> readCameras(r);
            case VisionNet.S_MEDIA_LIST -> {
                int n = r.count(4096);
                List<String> l = new ArrayList<>();
                for (int i = 0; i < n; i++) l.add(r.str(256));
                if (r.ok()) { MEDIA.clear(); MEDIA.addAll(l); }
            }
            case VisionNet.S_MEDIA_CHUNK -> {
                String name = r.str(256);
                int total = r.varInt(), off = r.varInt();
                byte[] part = r.bytes(VisionNet.MEDIA_CHUNK);
                if (!r.ok() || total < 0 || total > VisionNet.MEDIA_MAX || off < 0 || off + part.length > total) return;
                byte[] all = PARTS.computeIfAbsent(name, k -> new byte[total]);
                if (all.length != total) { all = new byte[total]; PARTS.put(name, all); RECEIVED.remove(name); }
                System.arraycopy(part, 0, all, off, part.length);
                int got = RECEIVED.merge(name, part.length, Integer::sum);
                if (got >= total) {
                    PARTS.remove(name); RECEIVED.remove(name);
                    MediaLibrary.received(name, all);
                }
            }
            default -> { }
        }
    }

    private static void readOutputs(Bytes.Reader r) {
        long pos = r.sLong();
        int n = r.count(16);
        OutputState[] o = new OutputState[n];
        for (int i = 0; i < n; i++) o[i] = OutputState.read(r);
        if (r.ok()) OUTPUTS.put(pos, o);
    }

    private static void readCameras(Bytes.Reader r) {
        int n = r.count(4096);
        Map<Long, CameraData.Cam> m = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            long pos = r.sLong();
            int kind = r.varInt(), num = r.varInt();
            String name = r.str(64);
            m.put(pos, new CameraData.Cam(pos, kind, num, name));
        }
        if (r.ok()) { CAMERAS.clear(); CAMERAS.putAll(m); }
    }

    /** The output state shown by a wall (null when the wall has no processor or it is unknown). */
    public static OutputState output(WallData.Wall w) {
        if (w.processor == 0) return null;
        OutputState[] o = OUTPUTS.get(w.processor);
        if (o == null) {
            // rev. 3: a processor just placed has sent nothing yet; its block entity holds the defaults (BARS), so the wall is not black
            var level = Minecraft.getInstance().level;
            if (level != null && w.output >= 0 && w.output < VisionProcessorBlockEntity.OUTPUTS
                    && level.getBlockEntity(BlockPos.of(w.processor)) instanceof VisionProcessorBlockEntity be) return be.effective(w.output);
            return null;
        }
        if (w.output < 0 || w.output >= o.length) return null;
        return o[w.output];
    }

    /** Outputs of a processor as last sent by the server, or the block entity's when nothing was sent yet. */
    public static OutputState[] outputs(BlockPos pos) {
        OutputState[] o = OUTPUTS.get(pos.asLong());
        if (o != null) return o;
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(pos) instanceof VisionProcessorBlockEntity be) {
            o = new OutputState[VisionProcessorBlockEntity.OUTPUTS];
            for (int i = 0; i < o.length; i++) o[i] = be.effective(i);
            return o;
        }
        return null;
    }

    /** Label of a source for the screens. */
    public static String label(String src) {
        if (src == null || src.equals(OutputState.BLACK)) return "BLACK";
        if (src.equals(OutputState.BARS)) return "BARS";
        if (src.equals(OutputState.GRID)) return "GRID";
        if (src.startsWith("media:")) return src.substring(6);
        BlockPos p = OutputState.camPos(src);
        if (p != null) {
            CameraData.Cam c = CAMERAS.get(p.asLong());
            if (c != null) return "CAM " + c.number() + (c.name().isEmpty() ? "" : " " + c.name());
            return "CAM ?";
        }
        return src;
    }

    // ------------------------------------------------------------------ commands
    public static Bytes.Writer proc(BlockPos pos, int op, int output) {
        return LightNet.message(VisionNet.C_PROC, pos).varInt(op).varInt(output);
    }

    public static Bytes.Writer cam(BlockPos pos, int op) {
        return LightNet.message(VisionNet.C_CAM, pos).varInt(op);
    }

    public static Bytes.Writer wall(long id, int op) {
        return new Bytes.Writer().varInt(VisionNet.C_WALL).sLong(id).varInt(op);
    }

    public static void send(Bytes.Writer w) {
        LightNet.toServer(w.toBytes());
    }

    public static void requestMediaList() {
        send(new Bytes.Writer().varInt(VisionNet.C_MEDIA).str(""));
    }

    public static void requestMedia(String name) {
        send(new Bytes.Writer().varInt(VisionNet.C_MEDIA).str(name));
    }
}
