package com.concertstage.light;

import com.concertstage.StageContent;
import com.concertstage.light.core.Engine;
import com.concertstage.light.core.Live;
import com.concertstage.light.core.Show;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CSC LUMICORE X1 lighting desk: the current show, the live state (programmer, playbacks, masters), the show library of the desk, its
 * owner and operators. The server applies the commands and saves everything with the world; each client keeps a copy (sent with the
 * chunk, then kept up to date by the same commands) and computes the light of the fixtures itself.
 */
public class LumicoreBlockEntity extends BlockEntity {
    public static final int MAX_LIBRARY = 16, UNDO_DEPTH = 25;

    public Show show = newShow();
    public Live live = new Live();
    /** Show library of the desk: name -> show bytes (names only on the clients). */
    public final Map<String, byte[]> library = new LinkedHashMap<>();
    public UUID owner;
    public String ownerName = "";
    public boolean publicAccess;
    public final Map<UUID, String> operators = new LinkedHashMap<>();
    /** Copies the current show into the library every N minutes (0 = off). */
    public int autosaveMinutes;
    public long lastAutosaveTick;
    /** Text shown on the desk (analysis progress, last error...), synced. */
    public String status = "";

    // server only
    public final Deque<byte[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    // client only: the engine of this desk (cached per frame)
    private final Engine engine = new Engine();
    /** External Show Control: the DMX driving this desk in External / Playback mode (server: receiver or player; client: from the server). */
    public final com.concertstage.light.dmx.DmxData dmx = new com.concertstage.light.dmx.DmxData();
    private long evalFrame = Long.MIN_VALUE;
    private Map<Integer, Engine.Output> lastOutputs = Map.of();

    /**
     * Rev. 1.3.0-2: identity of this desk (random, saved). A fixture keeps the id of the desk that patched it: a desk broken and placed
     * again at the same spot is another desk, and the fixtures of the old one no longer answer its fixture numbers (reported 13
     * November: "fixtures seem linked", "random ones turned on", auto patch skipping them).
     */
    public long deskId = newId();

    public LumicoreBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.LUMICORE_ENTITY, pos, state);
    }

    private static long newId() {
        long id;
        do id = java.util.concurrent.ThreadLocalRandom.current().nextLong(); while (id == 0);
        return id;
    }

    /**
     * Whether {@code fe} is really patched on this desk: its desk position and id, a patch with its fixture number and its type. Fixtures
     * patched before rev. 2 have no desk id: their patch must be at their position.
     */
    public boolean patches(FixtureBlockEntity fe) {
        if (fe == null || fe.console() == null || !fe.console().equals(worldPosition)) return false;
        com.concertstage.light.core.Show.Patch p = show.fixture(fe.fid());
        if (p == null) return false;
        FixtureBlock b = fe.block();
        if (b != null && !b.profileId.equals(p.type)) return false;
        if (fe.deskId() != 0) return fe.deskId() == deskId;
        BlockPos at = fe.getBlockPos();
        return p.x == at.getX() && p.y == at.getY() && p.z == at.getZ();
    }

    static Show newShow() {
        Show s = new Show();
        s.id = UUID.randomUUID().toString();
        s.name = "Show";
        s.created = System.currentTimeMillis();
        return s;
    }

    public long nowMs() {
        return level == null ? 0 : level.getGameTime() * 50L;
    }

    /** Outputs of every fixture for this frame (computed once per frame and shared by every fixture renderer). */
    public Map<Integer, Engine.Output> outputs(long frame, long nowMs, Engine.Music music) {
        if (frame != evalFrame) {
            evalFrame = frame;
            lastOutputs = engine.evaluate(show, live, nowMs, music, dmx);
            evalNanos = System.nanoTime();
        }
        return lastOutputs;
    }

    /** Client, rev. 1: when the outputs were last computed (System.nanoTime), for the camera images that reuse them. */
    public long evalNanos = Long.MIN_VALUE / 2;

    /** Client, rev. 1: the outputs computed for the last frame drawn (empty before the first). */
    public Map<Integer, Engine.Output> lastOutputs() {
        return lastOutputs;
    }

    /** Saves the show before an edit (undo history, server side). */
    public void remember() {
        undo.push(show.toBytes());
        while (undo.size() > UNDO_DEPTH) undo.removeLast();
        redo.clear();
    }

    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState s = getBlockState();
            level.sendBlockUpdated(worldPosition, s, s, Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------ permissions
    /** Owner, operators, server operators and the single player owner may program; anyone on a public desk. */
    public boolean mayOperate(net.minecraft.server.level.ServerPlayer p) {
        if (publicAccess || owner == null || p.getUUID().equals(owner) || operators.containsKey(p.getUUID())) return true;
        return p.hasPermissions(2) || (p.getServer() != null && p.getServer().isSingleplayerOwner(p.getGameProfile()));
    }

    public boolean isOwner(net.minecraft.server.level.ServerPlayer p) {
        return owner == null || p.getUUID().equals(owner) || p.hasPermissions(2)
            || (p.getServer() != null && p.getServer().isSingleplayerOwner(p.getGameProfile()));
    }

    // ------------------------------------------------------------ saving / syncing
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeShared(tag);
        ListTag lib = new ListTag();
        for (Map.Entry<String, byte[]> e : library.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("Name", e.getKey());
            t.putByteArray("Show", e.getValue());
            lib.add(t);
        }
        tag.put("Library", lib);
    }

    private void writeShared(CompoundTag tag) {
        tag.putLong("DeskId", deskId);
        tag.putByteArray("Show", show.toBytes());
        tag.putByteArray("Live", live.toBytes());
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.putBoolean("Public", publicAccess);
        ListTag ops = new ListTag();
        for (Map.Entry<UUID, String> e : operators.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", e.getKey());
            t.putString("Name", e.getValue());
            ops.add(t);
        }
        tag.put("Operators", ops);
        tag.putInt("Autosave", autosaveMinutes);
        tag.putString("Status", status);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        Show s = Show.fromBytes(tag.getByteArray("Show"));
        if (s != null) {
            int edits = show.edits + 1;
            show = s;
            show.edits = edits;
            if (s.legacyUpgraded > 0 || s.legacyKept > 0)                          // 1.2.1
                com.mojang.logging.LogUtils.getLogger().info("[CSC] desk {}: {} fixtures moved from an old DMX layout to the current one (RGB), {} kept (overlap)",
                    worldPosition.toShortString(), s.legacyUpgraded, s.legacyKept);
        }
        if (tag.contains("Live")) live = Live.fromBytes(tag.getByteArray("Live"));
        if (show.tiltMigrated) {                                   // rev. 1.3.0-2: the programmer keeps its physical tilts too
            for (Map.Entry<Integer, com.concertstage.light.core.Look> e : live.programmer.entrySet()) {
                com.concertstage.light.core.Show.Patch p = show.fixture(e.getKey());
                if (p != null) com.concertstage.light.core.Show.migrateTilt(e.getValue(), p.profile());
            }
            show.tiltMigrated = false;
        }
        if (show.tiltMigratedNoPan) {                              // rev. 4c: the same for the bars and strobes of a format-7 show
            for (Map.Entry<Integer, com.concertstage.light.core.Look> e : live.programmer.entrySet()) {
                com.concertstage.light.core.Show.Patch p = show.fixture(e.getKey());
                if (p != null && !p.profile().hasPan()) com.concertstage.light.core.Show.migrateTilt(e.getValue(), p.profile());
            }
            show.tiltMigratedNoPan = false;
        }
        if (tag.contains("DeskId") && tag.getLong("DeskId") != 0) deskId = tag.getLong("DeskId");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        publicAccess = tag.getBoolean("Public");
        operators.clear();
        for (Tag t : tag.getList("Operators", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (c.hasUUID("Id")) operators.put(c.getUUID("Id"), c.getString("Name"));
        }
        autosaveMinutes = tag.getInt("Autosave");
        status = tag.getString("Status");
        if (tag.contains("Library")) {
            library.clear();
            for (Tag t : tag.getList("Library", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                library.put(c.getString("Name"), c.getByteArray("Show"));
            }
        } else if (tag.contains("LibraryNames")) {
            library.clear();
            for (Tag t : tag.getList("LibraryNames", Tag.TAG_STRING)) library.put(t.getAsString(), new byte[0]);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeShared(tag);
        ListTag names = new ListTag();
        for (String n : library.keySet()) names.add(net.minecraft.nbt.StringTag.valueOf(n));
        tag.put("LibraryNames", names);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public List<String> libraryNames() {
        return new ArrayList<>(library.keySet());
    }

    /** Forge only (no @Override: the common code is also compiled for Fabric, where this method does not exist). */
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(1.5);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LumicoreBlockEntity be) {
        LightServer.tickDesk(level, be);
    }
}
