package com.concertstage.light;

import com.concertstage.audio.AudioConsoleBlockEntity;
import com.concertstage.audio.core.AudioFiles;
import com.concertstage.audio.core.TrackDecoder;
import com.concertstage.audio.net.AudioLibrary;
import com.concertstage.audio.net.AudioServer;
import com.concertstage.light.analysis.Analysis;
import com.concertstage.light.analysis.Analyzer;
import com.concertstage.light.analysis.AutoShow;
import com.concertstage.light.analysis.LiveAutoShow;
import com.concertstage.light.core.Bytes;
import com.concertstage.light.core.Commands;
import com.concertstage.light.core.Engine;
import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.core.Live;
import com.concertstage.light.core.Movement;
import com.concertstage.light.core.Show;
import com.concertstage.light.net.LightNet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the LUMICORE X1: permission checks, commands (applied, saved, sent to the clients that see the desk), the actions only
 * the server can do (music analysis and AutoShow, show library, undo / redo, auto patch, operators, show import), the live AutoShow
 * sessions and the patch bookkeeping when fixtures are placed, moved by a lift or broken.
 *
 * <p>The music analysis runs on one background thread (never on the server thread), decodes the track of the audio library once and
 * caches the result next to it ({@code <sha1>.cscana}); a bounded cache keeps the last results in memory.
 */
public final class LightServer {
    private LightServer() {}

    private static final double REACH = 12.0;
    private static ExecutorService analysisThread;
    private static final Map<String, Analysis> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Analysis> e) { return size() > 8; }
    };
    /** Running analyses: desk -> job. */
    private static final Map<BlockPos, Job> JOBS = new HashMap<>();
    private static final Map<BlockPos, LiveSession> LIVE = new HashMap<>();
    private static final Map<UUID, Import> IMPORTS = new HashMap<>();

    private record Job(Future<?> future, ServerLevel level, boolean live) {}

    private static final class Import {
        BlockPos desk;
        String name;
        byte[] data;
        int received;
    }

    /** Live AutoShow: the track decoded progressively as it plays, analysed causally. */
    private static final class LiveSession {
        final ServerLevel level;
        final String sha1;
        final int session;
        final Analyzer.Features features = new Analyzer.Features();
        final LiveAutoShow brain = new LiveAutoShow();
        TrackDecoder decoder;
        long decodedMs;
        int listId;
        final short[] buf = new short[4410 * 2];

        LiveSession(ServerLevel level, String sha1, int session) {
            this.level = level;
            this.sha1 = sha1;
            this.session = session;
        }
    }

    // ------------------------------------------------------------ messages
    public static void receive(ServerPlayer p, byte[] data) {
        Bytes.Reader r = new Bytes.Reader(data);
        int type = r.varInt();
        BlockPos pos = LightNet.readPos(r);
        ServerLevel level = p.serverLevel();
        // rev. 1.3.0-2: the configuration card and the patch of a fixture from its own screen (no desk at {@code pos} for these)
        if (type == LightNet.C_CARD) { QuickPatch.saveCard(p, r); return; }
        if (type == LightNet.C_FIXTURE_PATCH) { QuickPatch.fixturePatch(p, level, pos, r); return; }
        // 1.2.0 beta 2: loaded for the next ask. 1.2.0: only the ticket — recording the player here made its next ask (desk
        // loaded) look like a renewal, and it never got the desk (visual test v139: a desk 489 blocks away, no copy, no light)
        if (type == LightNet.C_SYNC && !level.isLoaded(pos)) { keepLoaded(p, level, pos); return; }
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof LumicoreBlockEntity desk)) return;
        boolean near = p.distanceToSqr(Vec3.atCenterOf(pos)) <= 64 * 64;
        switch (type) {
            case LightNet.C_SYNC -> {
                // 1.2.0 beta 2: a player far from the desk too (its fixtures are in its view): full desk now, then every change
                if (!near) {
                    Map<UUID, Long> subs = SUBSCRIBERS.get(key(level, pos));
                    boolean known = subs != null && subs.containsKey(p.getUUID()) && subs.get(p.getUUID()) >= level.getGameTime();
                    subscribe(p, level, pos);
                    if (known) return;                               // a renewal: it already has the desk
                }
                sendFull(p, desk);
            }
            case LightNet.C_CMD -> {
                byte[] cmd = r.bytes(LightNet.MAX_MESSAGE);
                if (!r.ok() || cmd.length == 0) return;
                if (p.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH * 4) { status(p, desk, "message.concertstage.light_too_far"); return; }
                int op = new Bytes.Reader(cmd).varInt();
                if (!desk.mayOperate(p)) { status(p, desk, "message.concertstage.light_no_permission"); return; }
                if (op == Commands.PATCH_ADD || op == Commands.PATCH_MOVE) return;            // the patch follows the world, not the clients
                apply(level, desk, cmd);
            }
            case LightNet.C_ACTION -> {
                if (!near) return;
                int action = r.varInt();
                action(p, level, desk, action, r);
            }
            default -> { }
        }
    }

    /** Applies a command on the server copy and sends it to every client that sees the desk. Returns false if it was rejected. */
    public static boolean apply(ServerLevel level, LumicoreBlockEntity desk, byte[] cmd) {
        return apply(level, desk, cmd, true);
    }

    /** Same; {@code history} false for undo / redo themselves. */
    public static boolean apply(ServerLevel level, LumicoreBlockEntity desk, byte[] cmd, boolean history) {
        int op = new Bytes.Reader(cmd).varInt();
        // rev. 1.3.0-2: a fixture taken out of the patch (desk, external, undo of a patch...) is unlinked at once, so it does not keep
        // answering a fixture number the desk will give to the next fixture patched
        java.util.List<BlockPos> removed = null;
        if (op == Commands.PATCH_REMOVE) {
            Bytes.Reader rr = new Bytes.Reader(cmd); rr.varInt();
            Show.Patch gone = desk.show.fixture(rr.varInt());
            if (gone != null) removed = java.util.List.of(new BlockPos(gone.x, gone.y, gone.z));
        } else if (op == Commands.SHOW_REPLACE) {
            removed = new java.util.ArrayList<>();
            for (Show.Patch q : desk.show.patch) removed.add(new BlockPos(q.x, q.y, q.z));
        }
        byte[] before = history && Commands.editsShow(op) && op != Commands.PATCH_ADD && op != Commands.PATCH_MOVE ? desk.show.toBytes() : null;
        long now = desk.nowMs();
        int result = Commands.apply(desk.show, desk.live, cmd, now, stage(desk));
        if (result == Commands.REJECTED) return false;
        if (before != null && (result & Commands.SHOW) != 0) {
            desk.undo.push(before);
            while (desk.undo.size() > LumicoreBlockEntity.UNDO_DEPTH) desk.undo.removeLast();
            desk.redo.clear();
        }
        desk.setChanged();
        if (removed != null)
            for (BlockPos b : removed)
                if (level.isLoaded(b) && level.getBlockEntity(b) instanceof FixtureBlockEntity fe && desk.getBlockPos().equals(fe.console()) && !desk.patches(fe))
                    fe.patch(null, 0);
        if (op == Commands.SHOW_REPLACE)                       // undo / redo / show loaded: the fixtures of the new patch link again
            for (Show.Patch q : desk.show.patch) {
                BlockPos b = new BlockPos(q.x, q.y, q.z);
                if (!level.isLoaded(b) || !(level.getBlockEntity(b) instanceof FixtureBlockEntity fe) || fe.block() == null
                    || !fe.block().profileId.equals(q.type)) continue;
                if (fe.console() != null && !fe.console().equals(desk.getBlockPos()) && linkedDesk(level, fe) != null) continue;
                if (!desk.patches(fe) || fe.fid() != q.fid) fe.patch(desk.getBlockPos(), q.fid, desk.deskId);
            }
        byte[] msg = LightNet.message(LightNet.S_CMD, desk.getBlockPos()).varLong(desk.live.revision).varLong(now).bytes(cmd).toBytes();
        for (ServerPlayer pl : watchers(level, desk.getBlockPos())) LightNet.toClient(pl, msg);
        if (op == Commands.LINK_AUDIO || op == Commands.STOP_ALL || op == Commands.AUTO_ENABLE) liveCheck(level, desk);
        return true;
    }

    static List<ServerPlayer> watchers(ServerLevel level, BlockPos pos) {
        List<ServerPlayer> near = level.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false);
        Map<UUID, Long> subs = SUBSCRIBERS.get(key(level, pos));
        if (subs == null || subs.isEmpty()) return near;
        List<ServerPlayer> all = new ArrayList<>(near);
        long now = level.getGameTime();
        for (Iterator<Map.Entry<UUID, Long>> it = subs.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || p.serverLevel() != level || e.getValue() < now) { it.remove(); continue; }
            if (!all.contains(p)) all.add(p);
        }
        return all;
    }

    // ------------------------------------------------------------ 1.2.0 beta 2: multiplayer, the lights for everyone
    /**
     * A player whose game does not have the desk loaded (the desk out of its view distance, while its fixtures are in it) still sees
     * the lights: its game asks the desk ({@link LightNet#C_SYNC}) every few seconds, the server then sends it the full desk and every
     * change, as to the players near the desk, for {@link #SUB_TICKS}. The desk's chunk is kept loaded meanwhile (it computes the show).
     * Reported 15 November: "in multiplayer one player sees the lights and the other does not".
     */
    static final Map<String, Map<UUID, Long>> SUBSCRIBERS = new HashMap<>();
    static final int SUB_TICKS = 300, SUB_RANGE = 768;
    static final net.minecraft.server.level.TicketType<ChunkPos> DESK_TICKET =
        net.minecraft.server.level.TicketType.create("concertstage_desk", java.util.Comparator.comparingLong(ChunkPos::toLong), SUB_TICKS);

    static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    /** The desk's chunk kept loaded for a while (a distant player within {@link #SUB_RANGE} asks it); false if too far. */
    static boolean keepLoaded(ServerPlayer p, ServerLevel level, BlockPos pos) {
        if (p.distanceToSqr(Vec3.atCenterOf(pos)) > (double) SUB_RANGE * SUB_RANGE) return false;
        ChunkPos c = new ChunkPos(pos);
        level.getChunkSource().addRegionTicket(DESK_TICKET, c, 2, c);
        return true;
    }

    /** A distant player asks a desk: keeps it loaded and sends it everything for a while. */
    static void subscribe(ServerPlayer p, ServerLevel level, BlockPos pos) {
        if (!keepLoaded(p, level, pos)) return;
        SUBSCRIBERS.computeIfAbsent(key(level, pos), k -> new HashMap<>()).put(p.getUUID(), level.getGameTime() + SUB_TICKS);
    }

    public static void sendFull(ServerPlayer p, LumicoreBlockEntity desk) {
        Bytes.Writer w = LightNet.message(LightNet.S_FULL, desk.getBlockPos()).bytes(desk.show.toBytes()).bytes(desk.live.toBytes());
        w.varInt(desk.library.size());
        for (String n : desk.library.keySet()) w.str(n);
        w.str(desk.status);
        w.varLong(desk.deskId);                  // 1.2.0: the copy of a desk out of view checks its fixtures with it (patches)
        LightNet.toClient(p, w.toBytes());
        ExternalControl.sendFull(p, desk);
    }

    private static void broadcastFull(ServerLevel level, LumicoreBlockEntity desk) {
        for (ServerPlayer pl : watchers(level, desk.getBlockPos())) sendFull(pl, desk);
        desk.changed();
    }

    private static void status(ServerPlayer p, LumicoreBlockEntity desk, String key, Object... args) {
        Bytes.Writer w = LightNet.message(LightNet.S_STATUS, desk.getBlockPos()).str(key).varInt(args.length);
        for (Object a : args) w.str(String.valueOf(a));
        LightNet.toClient(p, w.toBytes());
    }

    private static void setStatus(ServerLevel level, LumicoreBlockEntity desk, String text) {
        desk.status = text;
        desk.changed();
    }

    // ------------------------------------------------------------ actions
    private static void action(ServerPlayer p, ServerLevel level, LumicoreBlockEntity desk, int action, Bytes.Reader r) {
        boolean operate = desk.mayOperate(p);
        boolean owner = desk.isOwner(p);
        switch (action) {
            case LightNet.A_AUTOSHOW -> { if (operate) startAnalysis(p, level, desk, false); }
            case LightNet.A_LIVE_START -> { if (operate) startAnalysis(p, level, desk, true); }
            case LightNet.A_LIVE_STOP -> { if (operate) { LIVE.remove(desk.getBlockPos()); setStatus(level, desk, "Live AutoShow stopped"); } }
            case LightNet.A_LIB_SAVE -> {
                String name = Commands.clean(r.str(256));
                if (!operate || name.isEmpty()) return;
                if (!desk.library.containsKey(name) && desk.library.size() >= LumicoreBlockEntity.MAX_LIBRARY) { status(p, desk, "message.concertstage.light_library_full"); return; }
                desk.library.put(name, desk.show.toBytes());
                broadcastFull(level, desk);
            }
            case LightNet.A_LIB_LOAD, LightNet.A_LIB_DUPLICATE, LightNet.A_LIB_DELETE -> {
                String name = r.str(256);
                byte[] b = desk.library.get(name);
                if (!operate || b == null) return;
                if (action == LightNet.A_LIB_DELETE) { desk.library.remove(name); broadcastFull(level, desk); return; }
                if (action == LightNet.A_LIB_DUPLICATE) {
                    if (desk.library.size() >= LumicoreBlockEntity.MAX_LIBRARY) { status(p, desk, "message.concertstage.light_library_full"); return; }
                    String copy = name + " (2)";
                    for (int i = 3; desk.library.containsKey(copy); i++) copy = name + " (" + i + ")";
                    desk.library.put(copy, b.clone());
                    broadcastFull(level, desk);
                    return;
                }
                replaceShow(level, desk, b, true);
            }
            case LightNet.A_LIB_NEW -> {
                if (!operate) return;
                Show s = LumicoreBlockEntity.newShow();
                s.patch.addAll(desk.show.patch);
                replaceShow(level, desk, s.toBytes(), false);
            }
            case LightNet.A_UNDO, LightNet.A_REDO -> {
                if (!operate) return;
                boolean undo = action == LightNet.A_UNDO;
                var from = undo ? desk.undo : desk.redo;
                var to = undo ? desk.redo : desk.undo;
                if (from.isEmpty()) { status(p, desk, undo ? "message.concertstage.light_nothing_undo" : "message.concertstage.light_nothing_redo"); return; }
                to.push(desk.show.toBytes());
                apply(level, desk, Commands.op(Commands.SHOW_REPLACE).bytes(from.pop()).bool(false).toBytes(), false);
            }
            case LightNet.A_AUTOPATCH -> {
                int radius = Math.max(4, Math.min(64, r.varInt()));
                if (operate) autopatch(p, level, desk, radius);
            }
            case LightNet.A_UNPATCH_MISSING -> {
                if (!operate) return;
                for (Show.Patch fx : new ArrayList<>(desk.show.patch)) {
                    BlockPos b = new BlockPos(fx.x, fx.y, fx.z);
                    if (level.isLoaded(b) && !(level.getBlockEntity(b) instanceof FixtureBlockEntity fe && fe.fid() == fx.fid && desk.getBlockPos().equals(fe.console())))
                        apply(level, desk, Commands.op(Commands.PATCH_REMOVE).varInt(fx.fid).toBytes());
                }
            }
            case LightNet.A_PUBLIC -> { if (owner) { desk.publicAccess = r.bool(); broadcastFull(level, desk); } }
            case LightNet.A_OPERATOR_ADD, LightNet.A_OPERATOR_REMOVE -> {
                String name = r.str(64).strip();
                if (!owner || name.isEmpty()) return;
                if (action == LightNet.A_OPERATOR_REMOVE) { desk.operators.values().removeIf(n -> n.equalsIgnoreCase(name)); broadcastFull(level, desk); return; }
                ServerPlayer target = level.getServer().getPlayerList().getPlayerByName(name);
                if (target == null) { status(p, desk, "message.concertstage.light_player_unknown", name); return; }
                desk.operators.put(target.getUUID(), target.getGameProfile().getName());
                broadcastFull(level, desk);
            }
            case LightNet.A_AUTOSAVE -> { if (operate) { desk.autosaveMinutes = Math.max(0, Math.min(60, r.varInt())); broadcastFull(level, desk); } }
            case LightNet.A_LINK_AUDIO -> { if (operate) linkNearestAudio(p, level, desk); }
            case LightNet.A_POSITIONS -> { if (operate) apply(level, desk, Commands.op(Commands.POSITIONS).toBytes()); }
            case LightNet.A_EXT -> ExternalControl.action(p, level, desk, r, operate);
            case LightNet.A_IMPORT_BEGIN -> {
                String name = Commands.clean(r.str(256));
                int size = r.varInt();
                if (!operate || size <= 0 || size > 2 << 20) return;
                Import im = new Import();
                im.desk = desk.getBlockPos(); im.name = name.isEmpty() ? "Imported" : name; im.data = new byte[size];
                IMPORTS.put(p.getUUID(), im);
            }
            case LightNet.A_IMPORT_CHUNK -> {
                Import im = IMPORTS.get(p.getUUID());
                byte[] chunk = r.bytes(LightNet.CHUNK + 16);
                if (im == null || !im.desk.equals(desk.getBlockPos()) || im.received + chunk.length > im.data.length) { IMPORTS.remove(p.getUUID()); return; }
                System.arraycopy(chunk, 0, im.data, im.received, chunk.length);
                im.received += chunk.length;
                if (im.received == im.data.length) {
                    IMPORTS.remove(p.getUUID());
                    Show s = Show.fromBytes(im.data);
                    if (s == null) { status(p, desk, "message.concertstage.light_import_bad"); return; }
                    if (!desk.library.containsKey(im.name) && desk.library.size() >= LumicoreBlockEntity.MAX_LIBRARY) { status(p, desk, "message.concertstage.light_library_full"); return; }
                    s.name = im.name;
                    desk.library.put(im.name, s.toBytes());
                    broadcastFull(level, desk);
                    status(p, desk, "message.concertstage.light_imported", im.name);
                }
            }
            default -> { }
        }
    }

    private static void replaceShow(ServerLevel level, LumicoreBlockEntity desk, byte[] bytes, boolean keepRig) {
        apply(level, desk, Commands.op(Commands.SHOW_REPLACE).bytes(bytes).bool(keepRig).toBytes());
    }

    // ------------------------------------------------------------ patch
    /** Patches every unpatched CSC fixture around the desk (radius in blocks). */
    private static void autopatch(ServerPlayer p, ServerLevel level, LumicoreBlockEntity desk, int radius) {
        BlockPos c = desk.getBlockPos();
        int added = 0;
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-radius, -16, -radius), c.offset(radius, 48, radius))) {
            if (!level.isLoaded(b)) continue;
            if (!(level.getBlockEntity(b) instanceof FixtureBlockEntity fe)) continue;
            if (linkedDesk(level, fe) != null) continue;            // rev. 1.3.0-2: only a real link counts (not a stale one)
            if (patch(level, desk, fe)) added++;
            if (desk.show.patch.size() >= Show.MAX_FIXTURES) break;
        }
        status(p, desk, "message.concertstage.light_patched", added, desk.show.patch.size());
        if (added > 0) apply(level, desk, Commands.op(Commands.POSITIONS).toBytes());
    }

    /** Patches one fixture into a desk (from the patch tool or the auto patch). */
    public static boolean patch(ServerLevel level, LumicoreBlockEntity desk, FixtureBlockEntity fe) {
        FixtureBlock b = fe.block();
        if (b == null) return false;
        // already in another desk: leave it there first (rev. 1.3.0-2: only when that desk really has it, never the fixture number
        // of another fixture)
        LumicoreBlockEntity other = linkedDesk(level, fe);
        if (other != null && other != desk) apply(level, other, Commands.op(Commands.PATCH_REMOVE).varInt(fe.fid()).toBytes());
        if (other == desk) return false;
        int fid = desk.show.nextFid();
        BlockPos pos = fe.getBlockPos();
        var st = fe.getBlockState();
        boolean ok = apply(level, desk, Commands.op(Commands.PATCH_ADD).varInt(fid).str(b.profileId).str("")
            .sInt(pos.getX()).sInt(pos.getY()).sInt(pos.getZ()).f32(yaw(fe)).varInt(st.getValue(FixtureBlock.HUNG) ? 1 : 0).toBytes());
        if (ok) {
            fe.patch(desk.getBlockPos(), fid, desk.deskId);
            sendPose(level, desk, fe, fid);                                     // 1.2.1
        }
        return ok;
    }

    /** 1.2.1: the yaw of a fixture's base, its fine yaw included. */
    public static float yaw(FixtureBlockEntity fe) {
        return yaw(fe.getBlockState().getValue(FixtureBlock.FACING)) + fe.rotYaw;
    }

    /** 1.2.1: the offset, pitch and roll of a fixture's free pose into its desk (the desk aims it from there). */
    static void sendPose(Level level, LumicoreBlockEntity desk, FixtureBlockEntity fe, int fid) {
        if (!(level instanceof ServerLevel sl)) return;
        Show.Patch p = desk.show.fixture(fid);
        if (p == null) return;
        if (p.gel != fe.gel) apply(sl, desk, Commands.op(Commands.PATCH_GEL).varInt(fid).varInt(fe.gel & 0xFFFFFF).toBytes());   // 1.2.1
        if (!fe.posed() && !p.posed()) return;
        apply(sl, desk, Commands.op(Commands.PATCH_POSE).varInt(fid).f32(fe.offX).f32(fe.offY).f32(fe.offZ).f32(fe.rotPitch).f32(fe.rotRoll).toBytes());
    }

    /** Model rotation (degrees about +Y) that turns the north-facing model to {@code facing}. */
    public static float yaw(net.minecraft.core.Direction facing) {
        return 180f - facing.toYRot();
    }

    public static void unpatch(ServerLevel level, FixtureBlockEntity fe) {
        LumicoreBlockEntity desk = linkedDesk(level, fe);
        if (desk != null) apply(level, desk, Commands.op(Commands.PATCH_REMOVE).varInt(fe.fid()).toBytes());
        fe.patch(null, 0);
    }

    /** Rev. 1.3.0-2: the desk {@code fe} is really patched on (see {@link LumicoreBlockEntity#patches}), or null. */
    public static LumicoreBlockEntity linkedDesk(Level level, FixtureBlockEntity fe) {
        BlockPos c = fe.console();
        if (c == null || !level.isLoaded(c) || !(level.getBlockEntity(c) instanceof LumicoreBlockEntity desk)) return null;
        return desk.patches(fe) ? desk : null;
    }

    /** Rev. 1.3.0-2, every 2 s per patched fixture: drops a link that is no longer true. */
    static void checkLink(Level level, BlockPos pos, FixtureBlockEntity fe) {
        if (!(level instanceof ServerLevel sl) || fe.console() == null || !sl.isLoaded(fe.console())) return;   // desk chunk unloaded: wait
        if (!(sl.getBlockEntity(fe.console()) instanceof LumicoreBlockEntity desk)) { fe.patch(null, 0); return; }
        if (!desk.patches(fe)) { fe.patch(null, 0); return; }
        Show.Patch p = desk.show.fixture(fe.fid());
        if (p.x != pos.getX() || p.y != pos.getY() || p.z != pos.getZ()) {
            // another fixture of the same type has this number in the desk (the desk has it elsewhere): this one is not it
            BlockPos at = new BlockPos(p.x, p.y, p.z);
            if (sl.isLoaded(at) && sl.getBlockEntity(at) instanceof FixtureBlockEntity there && there != fe && desk.patches(there)) fe.patch(null, 0);
            else if (fe.deskId() == desk.deskId) fixtureMoved(level, pos, fe);               // carried by a lift: the desk follows
            else fe.patch(null, 0);
        }
        else if (fe.deskId() == 0) fe.patch(desk.getBlockPos(), fe.fid(), desk.deskId);   // patched before rev. 2: takes the id now
        // 1.2.1: the desk aims the fixture from its free pose: a desk that has another pose (show loaded, pose changed while the desk
        // was unloaded) gets this one
        else if (p.gel != fe.gel || p.ox != fe.offX || p.oy != fe.offY || p.oz != fe.offZ || p.pitch != fe.rotPitch || p.roll != fe.rotRoll
            || Math.abs(net.minecraft.util.Mth.wrapDegrees(p.yaw - yaw(fe))) > 0.01f) fixtureMoved(level, pos, fe);
    }

    public static void fixtureRemoved(Level level, BlockPos pos, FixtureBlockEntity fe) {
        if (!(level instanceof ServerLevel sl) || fe.console() == null) return;
        if (!sl.isLoaded(fe.console()) || !(sl.getBlockEntity(fe.console()) instanceof LumicoreBlockEntity desk)) return;
        Show.Patch p = desk.show.fixture(fe.fid());
        // a lift takes the block away and puts it back later: only forget fixtures broken where the desk still has them
        if (p != null && p.x == pos.getX() && p.y == pos.getY() && p.z == pos.getZ() && !com.concertstage.lift.LiftControl.capturing)
            apply(sl, desk, Commands.op(Commands.PATCH_REMOVE).varInt(fe.fid()).toBytes());
    }

    public static void fixtureMoved(Level level, BlockPos pos, FixtureBlockEntity fe) {
        if (!(level instanceof ServerLevel sl) || fe.console() == null) return;
        if (!sl.isLoaded(fe.console()) || !(sl.getBlockEntity(fe.console()) instanceof LumicoreBlockEntity desk)) return;
        var st = fe.getBlockState();
        if (desk.show.fixture(fe.fid()) == null || fe.deskId() != 0 && fe.deskId() != desk.deskId) return;
        apply(sl, desk, Commands.op(Commands.PATCH_MOVE).varInt(fe.fid()).sInt(pos.getX()).sInt(pos.getY()).sInt(pos.getZ())
            .f32(yaw(fe)).varInt(st.getValue(FixtureBlock.HUNG) ? 1 : 0).toBytes());
        sendPose(sl, desk, fe, fe.fid());                                       // 1.2.1
    }

    public static void deskRemoved(Level level, BlockPos pos, LumicoreBlockEntity desk) {
        JOBS.remove(pos);
        LIVE.remove(pos);
        if (!(level instanceof ServerLevel sl)) return;
        for (Show.Patch p : desk.show.patch) {
            BlockPos b = new BlockPos(p.x, p.y, p.z);
            if (sl.isLoaded(b) && sl.getBlockEntity(b) instanceof FixtureBlockEntity fe && pos.equals(fe.console())) fe.patch(null, 0);
        }
    }

    private static void linkNearestAudio(ServerPlayer p, ServerLevel level, LumicoreBlockEntity desk) {
        BlockPos c = desk.getBlockPos(), best = null;
        double bd = Double.MAX_VALUE;
        int cr = 10;                                                    // chunks around the desk (160 blocks)
        for (int cx = (c.getX() >> 4) - cr; cx <= (c.getX() >> 4) + cr; cx++) {
            for (int cz = (c.getZ() >> 4) - cr; cz <= (c.getZ() >> 4) + cr; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof AudioConsoleBlockEntity)) continue;
                    double d = be.getBlockPos().distSqr(c);
                    if (d < bd && d <= 160 * 160) { bd = d; best = be.getBlockPos(); }
                }
            }
        }
        if (best == null) { status(p, desk, "message.concertstage.light_no_audio"); return; }
        apply(level, desk, Commands.op(Commands.LINK_AUDIO).bool(true).sInt(best.getX()).sInt(best.getY()).sInt(best.getZ()).toBytes());
        status(p, desk, "message.concertstage.light_audio_linked", best.getX(), best.getY(), best.getZ());
    }

    /** Geometry of the stage seen from this desk (for positions and the AutoShow). */
    public static Movement.Stage stage(LumicoreBlockEntity desk) {
        BlockPos b = desk.getBlockPos();
        return Movement.stage(desk.show, b.getX(), b.getY(), b.getZ());
    }

    // ------------------------------------------------------------ music
    /** The linked audio console of a desk, or null. */
    public static AudioConsoleBlockEntity audio(Level level, Live live) {
        if (!live.hasAudio) return null;
        BlockPos a = new BlockPos(live.audioX, live.audioY, live.audioZ);
        return level.isLoaded(a) && level.getBlockEntity(a) instanceof AudioConsoleBlockEntity c ? c : null;
    }

    /** Shared music clock of the linked audio console (the same on the server and on every client). */
    public static Engine.Music music(Level level, Live live, double gameTime, long clientOffsetMs) {
        AudioConsoleBlockEntity c = audio(level, live);
        if (c == null) return Engine.Music.NONE;
        AudioConsoleBlockEntity.Track t = c.currentTrack();
        if (t == null || c.state() == AudioConsoleBlockEntity.STOPPED) return Engine.Music.NONE;
        return new Engine.Music(true, t.sha1(), c.state() == AudioConsoleBlockEntity.PLAYING, c.positionMs(gameTime) - clientOffsetMs);
    }

    private static void startAnalysis(ServerPlayer p, ServerLevel level, LumicoreBlockEntity desk, boolean live) {
        AudioConsoleBlockEntity audio = audio(level, desk.live);
        if (audio == null) { status(p, desk, "message.concertstage.light_no_audio"); return; }
        AudioConsoleBlockEntity.Track track = audio.currentTrack();
        if (track == null) { status(p, desk, "message.concertstage.light_no_track"); return; }
        AudioLibrary lib = AudioServer.library(level.getServer());
        AudioLibrary.Meta meta = lib == null ? null : lib.get(track.sha1());
        if (meta == null) { status(p, desk, "message.concertstage.light_track_missing"); return; }
        if (desk.show.patch.isEmpty()) { status(p, desk, "message.concertstage.light_no_fixtures"); return; }
        BlockPos pos = desk.getBlockPos();
        if (live) {
            LIVE.remove(pos);
            int id = LiveAutoShow.prepare(desk.show, stage(desk));
            desk.show.edits++;
            broadcastFull(level, desk);
            LiveSession s = new LiveSession(level, track.sha1(), audio.session());
            s.listId = id;
            try {
                s.decoder = AudioFiles.open(lib.file(meta), meta.type());
            } catch (IOException e) {
                status(p, desk, "message.concertstage.light_track_missing");
                return;
            }
            LIVE.put(pos, s);
            setStatus(level, desk, "Live AutoShow: " + track.name());
            return;
        }
        if (JOBS.containsKey(pos)) { status(p, desk, "message.concertstage.light_busy"); return; }
        Path file = lib.file(meta);
        Path cache = file.resolveSibling(track.sha1() + ".cscana");
        String sha1 = track.sha1(), type = meta.type(), name = track.name();
        setStatus(level, desk, "Analysing " + name + "…");
        MinecraftServer server = level.getServer();
        Future<?> f = executor().submit(() -> {
            Analysis a = cached(sha1, cache);
            String error = null;
            if (a == null) {
                try (TrackDecoder dec = AudioFiles.open(file, type)) {
                    a = Analyzer.analyze(new Analyzer.Pcm() {
                        @Override public int sampleRate() { return dec.sampleRate(); }
                        @Override public int read(short[] out) throws IOException { return dec.read(out); }
                    }, sha1, () -> Thread.currentThread().isInterrupted());
                    if (a != null) {
                        synchronized (CACHE) { CACHE.put(sha1, a); }
                        try { Files.write(cache, a.toBytes()); } catch (IOException ignored) { }
                    }
                } catch (IOException | RuntimeException e) {
                    error = e.getMessage();
                }
            }
            final Analysis done = a;
            final String err = error;
            server.execute(() -> finishAnalysis(level, pos, done, err, name));
        });
        JOBS.put(pos, new Job(f, level, false));
    }

    private static Analysis cached(String sha1, Path cache) {
        synchronized (CACHE) {
            Analysis a = CACHE.get(sha1);
            if (a != null) return a;
        }
        try {
            if (Files.isRegularFile(cache)) {
                Analysis a = Analysis.fromBytes(Files.readAllBytes(cache));
                if (a != null && sha1.equals(a.sha1)) { synchronized (CACHE) { CACHE.put(sha1, a); } return a; }
            }
        } catch (IOException ignored) { }
        return null;
    }

    private static void finishAnalysis(ServerLevel level, BlockPos pos, Analysis a, String error, String trackName) {
        JOBS.remove(pos);
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof LumicoreBlockEntity desk)) return;
        if (a == null) { setStatus(level, desk, "Analysis failed" + (error == null ? "" : ": " + error)); return; }
        Show s = desk.show.copy();
        AutoShow.Report r = AutoShow.generate(s, a, stage(desk));
        s.timeline.trackName = trackName;
        apply(level, desk, Commands.op(Commands.SHOW_REPLACE).bytes(s.toBytes()).bool(true).toBytes());
        if (!desk.live.timelineArmed) apply(level, desk, Commands.op(Commands.TIMELINE_ARM).bool(true).toBytes());
        setStatus(level, desk, String.format(java.util.Locale.ROOT, "AutoShow: %d cues, %d sections, %.1f BPM, %d accents — %s",
            r.cues(), r.sections(), r.bpm(), r.accents(), trackName));
    }

    private static ExecutorService executor() {
        if (analysisThread == null || analysisThread.isShutdown()) {
            analysisThread = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "CSC music analysis");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
        }
        return analysisThread;
    }

    /** Server stopped: jobs cancelled, files closed. */
    public static void stopped() {
        for (Job j : JOBS.values()) j.future().cancel(true);
        JOBS.clear();
        for (LiveSession s : LIVE.values()) closeQuietly(s);
        LIVE.clear();
        IMPORTS.clear();
        synchronized (CACHE) { CACHE.clear(); }
        ExternalControl.stopped();
        SUBSCRIBERS.clear();
    }

    private static void closeQuietly(LiveSession s) {
        try { if (s.decoder != null) s.decoder.close(); } catch (IOException ignored) { }
    }

    private static void liveCheck(ServerLevel level, LumicoreBlockEntity desk) {
        if (!desk.live.autoEnabled) {
            LiveSession s = LIVE.remove(desk.getBlockPos());
            if (s != null) closeQuietly(s);
        }
    }

    // ------------------------------------------------------------ ticks
    /** Every server tick for each loaded desk: live AutoShow, autosave. */
    public static void tickDesk(Level level, LumicoreBlockEntity desk) {
        if (!(level instanceof ServerLevel sl)) return;
        if (desk.owner == null && desk.ownerName.isEmpty()) desk.ownerName = "";
        LiveSession s = LIVE.get(desk.getBlockPos());
        if (s != null) tickLive(sl, desk, s);
        ExternalControl.tick(sl, desk);
        if (desk.autosaveMinutes > 0 && level.getGameTime() - desk.lastAutosaveTick > desk.autosaveMinutes * 1200L) {
            desk.lastAutosaveTick = level.getGameTime();
            if (desk.library.size() < LumicoreBlockEntity.MAX_LIBRARY || desk.library.containsKey("Autosave")) {
                desk.library.put("Autosave", desk.show.toBytes());
                desk.setChanged();
            }
        }
    }

    private static void tickLive(ServerLevel level, LumicoreBlockEntity desk, LiveSession s) {
        if (!desk.live.autoEnabled) { LIVE.remove(desk.getBlockPos()); closeQuietly(s); return; }
        AudioConsoleBlockEntity audio = audio(level, desk.live);
        AudioConsoleBlockEntity.Track t = audio == null ? null : audio.currentTrack();
        if (audio == null || t == null || !t.sha1().equals(s.sha1)) {
            LIVE.remove(desk.getBlockPos());
            closeQuietly(s);
            setStatus(level, desk, "Live AutoShow stopped (track changed)");
            return;
        }
        if (audio.state() != AudioConsoleBlockEntity.PLAYING) return;
        long play = audio.positionMs(level.getGameTime());
        if (play + 500 < s.decodedMs) {
            // seek backwards: start over from the beginning of the file (causal analysis)
            closeQuietly(s);
            LiveSession n = new LiveSession(level, s.sha1, audio.session());
            n.listId = s.listId;
            try {
                AudioLibrary lib = AudioServer.library(level.getServer());
                AudioLibrary.Meta meta = lib.get(s.sha1);
                n.decoder = AudioFiles.open(lib.file(meta), meta.type());
            } catch (IOException | RuntimeException e) { LIVE.remove(desk.getBlockPos()); return; }
            LIVE.put(desk.getBlockPos(), n);
            return;
        }
        // decode up to the playhead (never ahead: only what has been heard), at most 2 s of audio per tick
        int budget = 0;
        try {
            while (s.decodedMs < play && budget < 20) {
                int n = s.decoder.read(s.buf);
                if (n < 0) break;
                s.features.add(s.buf, n, s.decoder.sampleRate());
                s.decodedMs += n * 1000L / Math.max(1, s.decoder.sampleRate());
                budget++;
            }
        } catch (IOException e) {
            LIVE.remove(desk.getBlockPos());
            closeQuietly(s);
            return;
        }
        int state = s.brain.update(s.features, s.decodedMs);
        if (state >= 0 && desk.show.list(s.listId) != null) {
            apply(level, desk, Commands.op(Commands.GOTO).varInt(s.listId).varInt(state).toBytes());
        }
    }

    /** Fixture placed by a player holding nothing special: nothing to do (patched with the tool or the auto patch). */
    public static FixtureProfile profileOf(FixtureBlockEntity fe) {
        return fe.block() == null ? FixtureProfile.VORTEX_X1 : fe.block().profile();
    }

}
