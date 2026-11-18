package com.concertstage.lift;

import com.concertstage.StageContent;
import com.concertstage.angle.AngledPiece;
import com.concertstage.block.AngledHolderBlockEntity;
import com.concertstage.block.MultiPartBlock;
import com.concertstage.block.TowerBlockEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The part of a stage rig that is being lifted: every truss (and curtain, tarp...) hanging on the joints of the towers. While the
 * towers move, those blocks are removed from the world and live in this entity, which moves smoothly and carries whoever stands on it;
 * when the lift stops on a block boundary they are put back as ordinary blocks.
 *
 * The entity also owns the movement: the position is a function of the game time (start tick, start / target lift in 1/20 block),
 * computed identically on the server and on every client, so no position packet is needed. Blocks are stored relative to {@code anchor}.
 */
public class LiftStructureEntity extends Entity {
    private static final EntityDataAccessor<CompoundTag> DATA =
        SynchedEntityData.defineId(LiftStructureEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final int UNITS = TowerBlockEntity.UNITS;

    /** Full persistence data stays on the server; client update data travels with blocks that have a special renderer. */
    public static final class Entry {
        public final BlockPos rel;
        public final BlockState state;
        public CompoundTag nbt;
        /** Client-visible update tag, separate from the complete server save (inventories, etc.). */
        public CompoundTag renderNbt;
        /** Cached angled pieces, used for rendering and for the bounds of the moving structure. */
        public java.util.List<com.concertstage.angle.AngledPiece> pieces;

        public Entry(BlockPos rel, BlockState state, CompoundTag nbt) {
            this.rel = rel;
            this.state = state;
            this.nbt = nbt;
        }

        public void captureRenderData(BlockEntity be) {
            if (be == null || state.getBlock() == StageContent.ANGLED_HOLDER) return;
            renderNbt = be.getUpdateTag().copy();
            // loadStatic needs the registered type; getUpdateTag normally omits metadata.
            renderNbt.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<double[]> supports = new ArrayList<>();   // x0, z0, x1, z1, top : tops of the solid boxes, relative to the entity
    private final List<BlockPos> towers = new ArrayList<>();
    private AABB relBox;
    /** Client only: blocks completely surrounded by opaque blocks of the structure are not drawn (the inside of a thick roof). */
    private boolean[] hidden = new boolean[0];
    private BlockPos anchor = BlockPos.ZERO;
    private int baseUnits;
    private long startTick;
    private int startUnits;
    private int targetUnits;
    private boolean finished;

    public LiftStructureEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // ------------------------------------------------------------ setup (server)
    public void setup(ServerLevel level, BlockPos anchor, List<Entry> blocks, int baseUnits, long startTick,
                      int startUnits, int targetUnits, List<BlockPos> towerPositions) {
        this.anchor = anchor;
        this.entries.clear();
        this.entries.addAll(blocks);
        this.payload = null;
        this.baseUnits = baseUnits;
        this.startTick = startTick;
        this.startUnits = startUnits;
        this.targetUnits = targetUnits;
        this.towers.clear();
        this.towers.addAll(towerPositions);
        rebuildDerived();
        setPos(anchor.getX(), anchor.getY() + (startUnits - baseUnits) / (double) UNITS, anchor.getZ());
        syncData();
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** True if the block at {@code index} of {@link #entries()} cannot be seen (all six neighbours are opaque blocks of the structure). */
    public boolean isHidden(int index) {
        return index >= 0 && index < hidden.length && hidden[index];
    }

    public long startTick() { return startTick; }
    public int startUnits() { return startUnits; }
    public int targetUnits() { return targetUnits; }

    /**
     * 1.1.1 audio fix (client and server): how far (blocks, vertically) the block that stood at {@code original} when this move started
     * has travelled so far; NaN when this structure does not carry it. The sound of a speaker array carried by a lift follows it.
     */
    public double travelled(BlockPos original, long now) {
        int startOffset = Math.round((startUnits - baseUnits) / (float) UNITS);
        for (Entry e : entries) {
            if (anchor.getX() + e.rel.getX() == original.getX() && anchor.getZ() + e.rel.getZ() == original.getZ()
                && anchor.getY() + e.rel.getY() + startOffset == original.getY())
                return (unitsAt(now) - startUnits) / (double) UNITS;
        }
        return Double.NaN;
    }

    public int unitsAt(long now) {
        long dt = Math.max(0L, now - startTick);
        int dir = Integer.signum(targetUnits - startUnits);
        long v = startUnits + dir * dt;
        return (int) (dir > 0 ? Math.min(v, targetUnits) : Math.max(v, targetUnits));
    }

    /** Stop request: keep going to the next block boundary in the direction of travel, then stop there. */
    public void requestStop(ServerLevel level) {
        long now = level.getGameTime();
        int u = unitsAt(now);
        int dir = Integer.signum(targetUnits - startUnits);
        int rem = Math.floorMod(u, UNITS);
        int newTarget = rem == 0 ? u : (dir > 0 ? u + (UNITS - rem) : u - rem);
        retarget(level, now, newTarget);
    }

    private void retarget(ServerLevel level, long now, int newTarget) {
        int u = unitsAt(now);
        startTick = now;
        startUnits = u;
        targetUnits = newTarget;
        for (BlockPos t : towers) {
            if (level.hasChunkAt(t) && level.getBlockEntity(t) instanceof TowerBlockEntity tb && tb.isController()
                && getUUID().equals(tb.session())) {
                tb.retarget(now, newTarget);
            }
        }
        syncData();
    }

    // ------------------------------------------------------------ data shared with the clients
    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA, new CompoundTag());
    }

    /** Server: the packed blocks sent to each player who starts seeing the structure (built once, see LiftPayload). */
    private byte[] payload;
    private int payloadId;

    /**
     * The movement, shared through the entity data (small: it is sent again at every stop / new target). The blocks themselves go
     * separately, to each player who sees the structure ({@link #startSeenByPlayer}), so their number is not limited by the packet size.
     */
    private void syncData() {
        if (level().isClientSide) return;
        CompoundTag t = new CompoundTag();
        t.putInt("N", entries.size());
        t.putLong("A", anchor.asLong());
        t.putLong("S", startTick);
        t.putInt("SU", startUnits);
        t.putInt("T", targetUnits);
        t.putInt("BU", baseUnits);
        this.entityData.set(DATA, t);
    }

    private byte[] payload() {
        if (payload == null) {
            payload = LiftPayload.encode(entries);
            payloadId = java.util.concurrent.ThreadLocalRandom.current().nextInt();
        }
        return payload;
    }

    @Override
    public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (entries.isEmpty()) return;
        byte[] packed = payload();
        LiftPayload.send(player, getId(), payloadId, packed);
    }

    /** Client: the blocks arrived (see LiftClient). */
    public void applyBlocks(List<Entry> blocks) {
        if (!level().isClientSide) return;
        entries.clear();
        entries.addAll(blocks);
        rebuildDerived();
        setPos(anchor.getX(), anchor.getY() + (unitsAt(level().getGameTime()) - baseUnits) / (double) UNITS, anchor.getZ());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA.equals(key) && level().isClientSide) readData(this.entityData.get(DATA));
    }

    private void readData(CompoundTag t) {
        if (!t.contains("A")) return;
        if (!t.contains("B")) {                                           // 1.1.0: movement only, the blocks come with LiftPayload
            anchor = BlockPos.of(t.getLong("A"));
            startTick = t.getLong("S");
            startUnits = t.getInt("SU");
            targetUnits = t.getInt("T");
            baseUnits = t.getInt("BU");
            if (relBox == null) rebuildDerived();
            setPos(anchor.getX(), anchor.getY() + (unitsAt(level().getGameTime()) - baseUnits) / (double) UNITS, anchor.getZ());
            return;
        }
        int[] arr = t.getIntArray("B");
        entries.clear();
        for (int i = 0; i + 3 < arr.length; i += 4) {
            BlockState st = Block.BLOCK_STATE_REGISTRY.byId(arr[i + 3]);
            if (st != null) entries.add(new Entry(new BlockPos(arr[i], arr[i + 1], arr[i + 2]), st, null));
        }
        ListTag angled = t.getList("P", Tag.TAG_COMPOUND);
        for (int i = 0; i < angled.size(); i++) {
            CompoundTag c = angled.getCompound(i);
            int at = c.getInt("I");
            if (at >= 0 && at < entries.size()
                && entries.get(at).state.getBlock() == StageContent.ANGLED_HOLDER) {
                entries.get(at).nbt = c.copy();
            }
        }
        ListTag rendered = t.getList("BE", Tag.TAG_COMPOUND);
        for (int i = 0; i < rendered.size(); i++) {
            CompoundTag c = rendered.getCompound(i);
            int at = c.getInt("I");
            if (at >= 0 && at < entries.size() && entries.get(at).state.hasBlockEntity()
                && entries.get(at).state.getBlock() != StageContent.ANGLED_HOLDER) {
                entries.get(at).renderNbt = c.getCompound("Data").copy();
            }
        }
        anchor = BlockPos.of(t.getLong("A"));
        startTick = t.getLong("S");
        startUnits = t.getInt("SU");
        targetUnits = t.getInt("T");
        baseUnits = t.getInt("BU");
        rebuildDerived();
        setPos(anchor.getX(), anchor.getY() + (unitsAt(level().getGameTime()) - baseUnits) / (double) UNITS, anchor.getZ());
    }

    /** Changes whenever the blocks of the structure change (the renderer rebuilds its mesh). */
    private int revision;

    public int revision() {
        return revision;
    }

    private void rebuildDerived() {
        revision++;
        supports.clear();
        hidden = new boolean[0];
        if (entries.isEmpty()) {
            relBox = new AABB(0, 0, 0, 1, 1, 1);
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Entry e : entries) {
            BlockPos r = e.rel;
            minX = Math.min(minX, r.getX()); maxX = Math.max(maxX, r.getX());
            minY = Math.min(minY, r.getY()); maxY = Math.max(maxY, r.getY());
            minZ = Math.min(minZ, r.getZ()); maxZ = Math.max(maxZ, r.getZ());
            e.state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                supports.add(new double[] {r.getX() + x1, r.getZ() + z1, r.getX() + x2, r.getZ() + z2, r.getY() + y2}));
        }
        relBox = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        // Angled models extend beyond the holder cell. Include their real extent
        // so looking at a beam with its holder off screen does not cull the beam.
        for (Entry e : entries) {
            if (e.renderNbt != null) {
                // Renderers may draw a yoke, hook or moving head outside their block cell.
                AABB renderBox = new AABB(e.rel).inflate(1.0);
                if (e.state.getBlock() instanceof com.concertstage.block.HoistBlock) {
                    renderBox = renderBox.expandTowards(0, -(com.concertstage.block.HoistBlock.RANGE + 1), 0);
                }
                relBox = relBox.minmax(renderBox);
            }
            if (e.state.getBlock() != StageContent.ANGLED_HOLDER || e.nbt == null) continue;
            e.pieces = AngledHolderBlockEntity.readPieces(e.nbt);
            for (AngledPiece p : e.pieces) {
                double[] s = p.start(e.rel);
                double[] end = p.end(e.rel);
                double y = p.absY(e.rel);
                relBox = relBox.minmax(new AABB(s[0], y, s[1], end[0], y + p.height(), end[1]).inflate(1.0));
            }
        }
        if (level().isClientSide) computeHidden();
    }

    private void computeHidden() {
        Map<Long, Boolean> opaque = new HashMap<>(entries.size() * 2);
        for (Entry e : entries) opaque.put(e.rel.asLong(), e.state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        hidden = new boolean[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (!Boolean.TRUE.equals(opaque.get(e.rel.asLong()))) continue;
            boolean all = true;
            for (Direction d : Direction.values()) {
                if (!Boolean.TRUE.equals(opaque.get(e.rel.relative(d).asLong()))) {
                    all = false;
                    break;
                }
            }
            hidden[i] = all;
        }
    }

    @Override
    protected AABB makeBoundingBox() {
        if (relBox == null) return super.makeBoundingBox();
        return relBox.move(getX(), getY(), getZ());
    }

    // ------------------------------------------------------------ movement
    @Override
    public void tick() {
        super.tick();
        Level level = level();
        long now = level.getGameTime();
        if (!level.isClientSide) {
            if (finished) return;
            if (!serverTick((ServerLevel) level, now)) return;
        }
        if (entries.isEmpty() && level.isClientSide) return;
        int u = unitsAt(now);
        double oldY = getY();
        double newY = anchor.getY() + (u - baseUnits) / (double) UNITS;
        if (oldY != newY) {
            carry(oldY, newY);
            setPos(anchor.getX(), newY, anchor.getZ());
        }
    }

    /** @return false if the movement is over (the entity has been removed). */
    private boolean serverTick(ServerLevel level, long now) {
        // the towers must still be there, and still ours
        for (BlockPos t : towers) {
            if (!level.hasChunkAt(t)) return true;                       // area unloaded: wait
            BlockEntity be = level.getBlockEntity(t);
            if (!(be instanceof TowerBlockEntity tb) || !tb.isController()) {
                finish(level, nearestUnits(now));
                return false;
            }
            if (!tb.isMoving() || !getUUID().equals(tb.session())) {
                finish(level, tb.isMoving() ? nearestUnits(now) : tb.restUnits());
                return false;
            }
        }
        int u = unitsAt(now);
        // on every block boundary, check that the next block of travel is free
        if (u != targetUnits && Math.floorMod(u, UNITS) == 0) {
            int dir = Integer.signum(targetUnits - startUnits);
            int next = (u - baseUnits) / UNITS + dir;
            boolean hookBlocked = false, hooking = false;
            if (dir < 0) {                            // hoists lowering: the hook (or the load under it) stops on the first obstacle
                int nextLift = Math.floorDiv(u, UNITS) + dir;
                for (BlockPos t : towers) {
                    if (!level.hasChunkAt(t) || !(level.getBlockState(t).getBlock() instanceof com.concertstage.block.HoistBlock)) continue;
                    BlockPos hook = t.below(1 + com.concertstage.block.HoistBlock.RANGE - nextLift);
                    BlockState hs = level.getBlockState(hook);
                    if (hs.canBeReplaced()) continue;
                    // something the hook can take (a S.E.F block...): it goes down into it, takes it, and stops there; else it stops above
                    if (LiftControl.hookCanTake(level, hook, hs)) hooking = true;
                    else hookBlocked = true;
                }
            }
            if (hooking && !hookBlocked && targetUnits != u + dir * UNITS && LiftControl.canOccupy(level, anchor, entries, next)) {
                retarget(level, now, u + dir * UNITS);
                for (var p : level.getPlayers(pl -> pl.distanceToSqr(getX(), getY(), getZ()) < 96.0 * 96.0))
                    p.displayClientMessage(Component.translatable("message.concertstage.hoist_hooked"), true);
            }
            int hit = hookBlocked ? -1 : LiftControl.obstacleIndex(level, anchor, entries, next);
            BlockPos obstacle = hit < 0 ? null : anchor.offset(entries.get(hit).rel).above(next);
            if (hookBlocked || obstacle != null) {
                retarget(level, now, u);
                for (BlockPos t : towers) {
                    if (level.getBlockEntity(t) instanceof TowerBlockEntity tb) tb.setFault(TowerBlockEntity.FAULT_BLOCKED);
                }
                var near = level.getPlayers(pl -> pl.distanceToSqr(getX(), getY(), getZ()) < 96.0 * 96.0);
                if (obstacle != null) LiftControl.tellObstacle(level, obstacle, entries.get(hit).state,
                    anchor.offset(entries.get(hit).rel).above(next - Integer.signum(targetUnits - startUnits)), near);
                else for (var p : near) p.displayClientMessage(Component.translatable("message.concertstage.lift_blocked"), true);
                u = unitsAt(now);
            }
        }
        if (u == targetUnits) {
            finish(level, u);
            return false;
        }
        return true;
    }

    private int nearestUnits(long now) {
        return Math.round(unitsAt(now) / (float) UNITS) * UNITS;
    }

    // ------------------------------------------------------------ ending
    private void finish(ServerLevel level, int units) {
        if (finished) return;
        finished = true;
        place(level, Math.round((units - baseUnits) / (float) UNITS));
        for (BlockPos t : towers) {
            if (level.hasChunkAt(t) && level.getBlockEntity(t) instanceof TowerBlockEntity tb && tb.isController()
                && (tb.isMoving() && getUUID().equals(tb.session()))) {
                tb.arrive(units);
            }
        }
        discard();
    }

    /** Puts the blocks back, {@code offset} blocks above where they were taken. If something is in the way, the nearest free spot is used. */
    private void place(ServerLevel level, int offset) {
        int[] tries = {0, -1, 1, -2, 2, -3, 3};
        for (int d : tries) {
            if (LiftControl.canOccupy(level, anchor, entries, offset + d)) {
                for (Entry e : entries) {
                    BlockPos p = anchor.offset(e.rel).above(offset + d);
                    level.setBlock(p, e.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    if (e.nbt != null) {
                        BlockEntity be = level.getBlockEntity(p);
                        if (be != null && !BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).getNamespace().equals(com.concertstage.ConcertStage.MOD_ID)) {
                            // a block entity of another mod (a stage light...): loaded first, then given the level, the way a chunk
                            // loads it, so what it registers when it enters the world (a light's DMX network) uses its saved data
                            CompoundTag full = e.nbt.copy();
                            full.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
                            BlockEntity loaded = BlockEntity.loadStatic(p, e.state, full);
                            if (loaded != null) {
                                level.setBlockEntity(loaded);
                                loaded.setChanged();
                                BlockState st = level.getBlockState(p);
                                level.sendBlockUpdated(p, st, st, Block.UPDATE_ALL);
                                continue;
                            }
                        }
                        if (be != null) {
                            be.load(e.nbt);
                            be.setChanged();
                            BlockState st = level.getBlockState(p);
                            level.sendBlockUpdated(p, st, st, Block.UPDATE_ALL);
                        }
                    }
                }
                // roof frames and circular trusses rebuild their invisible cells where they arrived (taken away at the start)
                for (Entry e : entries) {
                    BlockPos p = anchor.offset(e.rel).above(offset + d);
                    BlockEntity be = level.getBlockEntity(p);
                    if (be instanceof com.concertstage.block.roof.RoofTrussBlockEntity r) { r.rebuildCells(); r.setChanged(); level.sendBlockUpdated(p, e.state, e.state, Block.UPDATE_CLIENTS); }
                    else if (be instanceof com.concertstage.block.ring.RingTrussBlockEntity r) r.rebuildCells();
                    else if (be instanceof com.concertstage.audio.SpeakerBlockEntity r) r.rebuildCells();
                }
                return;
            }
        }
        // no room anywhere: give the pieces back as items
        for (Entry e : entries) {
            if (e.state.getBlock() instanceof MultiPartBlock mp && !mp.isMainPart(e.state)) continue;
            ItemStack stack = new ItemStack(e.state.getBlock());
            if (!stack.isEmpty()) Block.popResource(level, anchor.offset(e.rel).above(offset), stack);
        }
    }

    /** Killed or removed by something else (command, mod): do not lose the blocks. */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && !finished && !reason.shouldSave() && level() instanceof ServerLevel sl && !entries.isEmpty()) {
            finished = true;
            place(sl, Math.round((unitsAt(sl.getGameTime()) - baseUnits) / (float) UNITS));
            for (BlockPos t : towers) {
                if (sl.hasChunkAt(t) && sl.getBlockEntity(t) instanceof TowerBlockEntity tb && tb.isController() && tb.isMoving()) {
                    tb.setFault(TowerBlockEntity.FAULT_LOST);
                    tb.arrive(nearestUnits(sl.getGameTime()));
                }
            }
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------ carrying whoever stands on the structure
    private void carry(double oldY, double newY) {
        if (supports.isEmpty() || relBox == null) return;
        Level level = level();
        double dy = newY - oldY;
        AABB area = relBox.move(getX(), oldY, getZ()).inflate(0.5).expandTowards(0, 3.0, 0);
        List<Entity> list = level.getEntities(this, area, e -> !(e instanceof LiftStructureEntity) && !e.isSpectator() && !e.isPassenger());
        for (Entity e : list) {
            if (level.isClientSide && !(e instanceof Player p && p.isLocalPlayer())) continue;   // other entities follow the server
            AABB eb = e.getBoundingBox();
            double bestTop = Double.NEGATIVE_INFINITY;
            for (double[] s : supports) {
                if (eb.maxX <= getX() + s[0] + 1.0E-4 || eb.minX >= getX() + s[2] - 1.0E-4) continue;
                if (eb.maxZ <= getZ() + s[1] + 1.0E-4 || eb.minZ >= getZ() + s[3] - 1.0E-4) continue;
                double top = oldY + s[4];
                if (eb.minY >= top - 0.6 && eb.minY <= top + 0.12 && top > bestTop) bestTop = top;
            }
            if (bestTop == Double.NEGATIVE_INFINITY) continue;
            Vec3 m = e.getDeltaMovement();
            if (m.y > 0.1) continue;                                      // jumping
            double shift = (bestTop + dy) - eb.minY;
            e.setPos(e.getX(), e.getY() + shift, e.getZ());
            e.yo = e.getY() - dy;                                         // the camera only sees the platform move
            e.yOld = e.yo;
            e.setOnGround(true);
            e.fallDistance = 0.0F;
            if (m.y < 0) e.setDeltaMovement(m.x, 0.0, m.z);
        }
    }

    // ------------------------------------------------------------ saving
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // compact format (a roof can be thousands of blocks): a palette of block states + four ints per block (x, y, z, palette index);
        // only the blocks that have block entity data get an extra entry
        ListTag palette = new ListTag();
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        int[] data = new int[entries.size() * 4];
        ListTag bes = new ListTag();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            Integer pi = paletteIndex.get(e.state);
            if (pi == null) {
                pi = palette.size();
                paletteIndex.put(e.state, pi);
                palette.add(NbtUtils.writeBlockState(e.state));
            }
            data[4 * i] = e.rel.getX();
            data[4 * i + 1] = e.rel.getY();
            data[4 * i + 2] = e.rel.getZ();
            data[4 * i + 3] = pi;
            if (e.nbt != null) {
                CompoundTag c = new CompoundTag();
                c.putInt("I", i);
                c.put("Be", e.nbt);
                if (e.renderNbt != null) c.put("Render", e.renderNbt);
                bes.add(c);
            }
        }
        tag.put("Palette", palette);
        tag.putIntArray("Data", data);
        tag.put("Bes", bes);
        tag.put("Anchor", NbtUtils.writeBlockPos(anchor));
        tag.putInt("BaseUnits", baseUnits);
        tag.putLong("StartTick", startTick);
        tag.putInt("StartUnits", startUnits);
        tag.putInt("TargetUnits", targetUnits);
        ListTag tl = new ListTag();
        for (BlockPos t : towers) tl.add(NbtUtils.writeBlockPos(t));
        tag.put("Towers", tl);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entries.clear();
        payload = null;
        if (tag.contains("Data")) {
            ListTag palette = tag.getList("Palette", Tag.TAG_COMPOUND);
            List<BlockState> states = new ArrayList<>(palette.size());
            for (int i = 0; i < palette.size(); i++) {
                states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
            }
            int[] data = tag.getIntArray("Data");
            for (int i = 0; i + 3 < data.length; i += 4) {
                int pi = data[i + 3];
                if (pi < 0 || pi >= states.size()) continue;
                entries.add(new Entry(new BlockPos(data[i], data[i + 1], data[i + 2]), states.get(pi), null));
            }
            ListTag bes = tag.getList("Bes", Tag.TAG_COMPOUND);
            for (int i = 0; i < bes.size(); i++) {
                CompoundTag c = bes.getCompound(i);
                int at = c.getInt("I");
                if (at >= 0 && at < entries.size()) {
                    entries.get(at).nbt = c.getCompound("Be");
                    if (c.contains("Render")) entries.get(at).renderNbt = c.getCompound("Render").copy();
                }
            }
        } else {
            // saves from 1.2.8 and before: one compound per block
            ListTag list = tag.getList("Blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                BlockState st = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), c.getCompound("State"));
                entries.add(new Entry(new BlockPos(c.getInt("X"), c.getInt("Y"), c.getInt("Z")), st, c.contains("Be") ? c.getCompound("Be") : null));
            }
        }
        anchor = NbtUtils.readBlockPos(tag.getCompound("Anchor"));
        // Older saves only contain full block data. Recreate an unregistered instance
        // to obtain its normal client update tag, without spawning or ticking it.
        if (!level().isClientSide) {
            for (Entry e : entries) {
                if (e.renderNbt != null || e.nbt == null || !(e.state.getBlock() instanceof EntityBlock eb)) continue;
                BlockEntity be = eb.newBlockEntity(anchor.offset(e.rel), e.state);
                if (be != null) {
                    be.setLevel(level());
                    be.load(e.nbt);
                    e.captureRenderData(be);
                }
            }
        }
        baseUnits = tag.getInt("BaseUnits");
        startTick = tag.getLong("StartTick");
        startUnits = tag.getInt("StartUnits");
        targetUnits = tag.getInt("TargetUnits");
        towers.clear();
        ListTag tl = tag.getList("Towers", Tag.TAG_COMPOUND);
        for (int i = 0; i < tl.size(); i++) towers.add(NbtUtils.readBlockPos(tl.getCompound(i)));
        rebuildDerived();
        setPos(anchor.getX(), anchor.getY() + (unitsAt(level().getGameTime()) - baseUnits) / (double) UNITS, anchor.getZ());
        syncData();
    }

    /** Client: a travelling rig is drawn as far as the mod's blocks (render distance of the video settings), not only 64 blocks. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double d = com.concertstage.client.RenderQuality.distance();
        return distanceSqr < d * d;
    }

    // ------------------------------------------------------------ misc
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        // the position is computed from the game time: ignore position packets
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
