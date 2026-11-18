package com.concertstage.block;

import com.concertstage.StageContent;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity of one cell of a motorised tower. The bottom cell is the controller: it holds the lift state.
 *
 * The lift is counted in units of 1/20 block (one unit per game tick = 1 block per second). While the tower moves, the lift is
 * a plain function of the game time ({@link #unitsAt}), so the server and every client compute the same value without any
 * per-tick packet: the state is only sent when it changes (start, stop, arrival).
 */
public class TowerBlockEntity extends BlockEntity {
    public static final int UNITS = 20;
    public static final int FAULT_NONE = 0;
    public static final int FAULT_BLOCKED = 1;
    public static final int FAULT_LOST = 2;

    private int units;            // lift at rest, in 1/20 block (0 = joint in its lowest position)
    private boolean moving;
    private long startTick;
    private int startUnits;
    private int targetUnits;
    private UUID session;         // the LiftStructureEntity that drives the current movement
    private float angle;          // tower: extra turn about its own axis (degrees, clockwise); angled hoist: compass angle of the beam it hangs under
    private float offX, offZ;     // angled hoist only: where it hangs, relative to the centre of its cell (blocks)
    private int fault;
    private int lostTicks;
    private float hookRise = Float.NaN;   // hoist only (1.1.0): how far the upper hook goes up into the truss above, px (NaN = not known yet)

    public TowerBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.TOWER_ENTITY, pos, state);
        if (state.getBlock() instanceof MotorTowerBlock t) this.units = t.initialUnits();   // a new hoist starts fully raised
    }

    // ------------------------------------------------------------ structure
    public MotorTowerBlock tower() {
        return (MotorTowerBlock) getBlockState().getBlock();
    }

    public boolean isController() {
        return tower().isMainPart(getBlockState());
    }

    public BlockPos mainPos() {
        return tower().mainPos(getBlockState(), worldPosition);
    }

    /** The controller block entity of this tower (this one if it is the bottom cell). Null if it is not loaded. */
    public TowerBlockEntity controller() {
        if (isController()) return this;
        Level lvl = getLevel();
        if (lvl == null) return null;
        BlockEntity be = lvl.getBlockEntity(mainPos());
        return be instanceof TowerBlockEntity t ? t : null;
    }

    // ------------------------------------------------------------ angle (placed with the angle tool / hoist under an angled beam)
    public float angle() { return angle; }

    public float offX() { return offX; }

    public float offZ() { return offZ; }

    /** Turns the tower about its own axis by {@code degrees} (every cell of a tower stores it, the renderer of each cell turns its slice). */
    public void setAngle(float degrees) {
        this.angle = degrees;
        changed();
    }

    /** A hoist hanging under an angled beam: compass angle of the beam and where the hoist sits relative to the centre of its cell. */
    public void setPlacement(float degrees, float offsetX, float offsetZ) {
        this.angle = degrees;
        this.offX = offsetX;
        this.offZ = offsetZ;
        changed();
    }

    /** Hoist: raise of the upper hook into the truss above (px), NaN if not known yet. Saved, so a hoist carried by a tower keeps it. */
    public float hookRise() { return hookRise; }

    /** Hoist, server: works the raise of the upper hook out again from the truss above; keeps the last value if there is no truss. */
    public void refreshHookRise() {
        if (level == null || level.isClientSide || moving || !(getBlockState().getBlock() instanceof HoistBlock)) return;
        float r = HoistBlock.hookRise(level, worldPosition, getBlockState());
        if (Float.isNaN(r) || Math.abs(r - hookRise) < 0.01f) return;
        hookRise = r;
        changed();
    }

    // ------------------------------------------------------------ lift state (controller)
    public boolean isMoving() { return moving; }

    /** A cell also draws the tilted bars that stick out of it a little, so give it some margin (used by Forge, ignored by Fabric). */
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(worldPosition).inflate(1.0);
        // a chain hoist draws its hook and chain down to the lowest position of the load
        return getBlockState().getBlock() instanceof HoistBlock ? box.expandTowards(0.0, -(HoistBlock.RANGE + 1), 0.0) : box;
    }
    public UUID session() { return session; }
    public int fault() { return fault; }
    public int targetUnits() { return targetUnits; }
    public int restUnits() { return units; }

    /** Lift in units at the given game time. */
    public int unitsAt(long now) {
        if (!moving) return units;
        long dt = Math.max(0L, now - startTick);
        int dir = Integer.signum(targetUnits - startUnits);
        long v = startUnits + dir * dt;
        return (int) (dir > 0 ? Math.min(v, targetUnits) : Math.max(v, targetUnits));
    }

    /** Lift in blocks, smooth between ticks (client rendering). */
    public double liftBlocks(long now, float partial) {
        if (!moving) return units / (double) UNITS;
        int dir = Integer.signum(targetUnits - startUnits);
        double dt = Math.max(0.0, now - startTick) + partial;
        double v = startUnits + dir * dt;
        v = dir > 0 ? Math.min(v, targetUnits) : Math.max(v, targetUnits);
        return v / UNITS;
    }

    public void startMove(long now, int target, UUID sessionId) {
        this.startTick = now;
        this.startUnits = units;
        this.targetUnits = target;
        this.session = sessionId;
        this.moving = true;
        this.fault = FAULT_NONE;
        this.lostTicks = 0;
        changed();
    }

    /** New target while moving (stop, obstacle). The movement continues from where it is. */
    public void retarget(long now, int newTarget) {
        int current = unitsAt(now);
        this.startTick = now;
        this.startUnits = current;
        this.targetUnits = newTarget;
        changed();
    }

    public void arrive(int finalUnits) {
        this.units = finalUnits;
        this.moving = false;
        this.session = null;
        this.lostTicks = 0;
        changed();
    }

    public void setFault(int code) {
        this.fault = code;
        changed();
    }

    private void changed() {
        setChanged();
        Level lvl = getLevel();
        if (lvl != null && !lvl.isClientSide) {
            BlockState st = getBlockState();
            lvl.sendBlockUpdated(worldPosition, st, st, 3);
        }
    }

    /** Safety net: if the entity that drives a movement has been gone for 5 seconds, the tower settles on the nearest block. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, TowerBlockEntity be) {
        // hoist: follow the truss above (placed, changed, turned to another model) about once a second
        if (state.getBlock() instanceof HoistBlock && Math.floorMod(level.getGameTime() + pos.asLong(), 20L) == 0) be.refreshHookRise();
        if (!be.moving || !(level instanceof ServerLevel server) || !be.isController()) return;
        if (be.session != null && server.getEntity(be.session) != null) {
            be.lostTicks = 0;
            return;
        }
        if (++be.lostTicks > 100) {
            int now = be.unitsAt(level.getGameTime());
            be.fault = FAULT_LOST;
            be.arrive(Math.round(now / (float) UNITS) * UNITS);
        }
    }

    // ------------------------------------------------------------ saving / syncing
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Units", units);
        tag.putBoolean("Moving", moving);
        tag.putLong("StartTick", startTick);
        tag.putInt("StartUnits", startUnits);
        tag.putInt("TargetUnits", targetUnits);
        tag.putInt("Fault", fault);
        tag.putFloat("Angle", angle);
        tag.putFloat("OffX", offX);
        tag.putFloat("OffZ", offZ);
        if (!Float.isNaN(hookRise)) tag.putFloat("HookRise", hookRise);
        if (session != null) tag.putUUID("Session", session);
        if (getBlockState().getBlock() instanceof HoistBlock) tag.putInt("HoistRange", HoistBlock.RANGE);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        units = tag.getInt("Units");
        moving = tag.getBoolean("Moving");
        startTick = tag.getLong("StartTick");
        startUnits = tag.getInt("StartUnits");
        targetUnits = tag.getInt("TargetUnits");
        fault = tag.getInt("Fault");
        angle = tag.getFloat("Angle");
        offX = tag.getFloat("OffX");
        offZ = tag.getFloat("OffZ");
        session = tag.hasUUID("Session") ? tag.getUUID("Session") : null;
        hookRise = tag.contains("HookRise") ? tag.getFloat("HookRise") : Float.NaN;
        // a hoist saved with a shorter hook travel: same hook height, counted from the new lowest position
        if (getBlockState().getBlock() instanceof HoistBlock) {
            int saved = tag.contains("HoistRange") ? tag.getInt("HoistRange") : 8;
            int shift = (HoistBlock.RANGE - saved) * UNITS;
            if (shift != 0) { units += shift; startUnits += shift; targetUnits += shift; }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
