package com.concertstage.light;

import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity of a fixture: the desk that drives it and its fixture number in that desk's show. On the client it also keeps the
 * physical state of the head (where the motors are now, moving towards the target at the speed of the profile) and the last beam
 * length measured against the blocks.
 */
public class FixtureBlockEntity extends BlockEntity {
    private BlockPos console;
    private int fid;
    /** Position last reported to the desk (a lift moves the fixture: the desk's patch is updated). */
    private BlockPos reported;
    public float manualPan, manualTilt;

    public void adjustManual(boolean pan) {
        if (pan) manualPan = (manualPan + 15f) % 360f;
        else manualTilt = manualTilt >= 90f ? -90f : manualTilt + 15f;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Rev. 24 (fixture angle tool): sets the tilt of a fixture without tilt motor, sent to the players around. */
    public void setManualTilt(float tilt) {
        manualTilt = Math.max(-90f, Math.min(90f, tilt));
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * 1.2.1, free placement: offset of the base from the centre of its block (blocks, up to 2 each way), a fine yaw added to the
     * facing, and a pitch / roll of the base (degrees). Set where the player aimed when placing (sneaking) and in the position screen
     * of the angle tool. All 0 = the placement of 1.2.0.
     */
    public float offX, offY, offZ, rotYaw, rotPitch, rotRoll;
    public static final float MAX_OFFSET = 2f;

    /** 1.2.1: gel in front of a conventional (RGB, 0xFFFFFF = open white). */
    public int gel = 0xFFFFFF;

    /** Sets the gel (server side), sent to the players around and to the desk. */
    public void setGel(int rgb) {
        gel = rgb & 0xFFFFFF;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            LightServer.fixtureMoved(level, worldPosition, this);
        }
    }

    /** Moved or tilted off the standard place (the truss hardware of a hung fixture is not drawn then). */
    public boolean posed() {
        return offX != 0 || offY != 0 || offZ != 0 || rotPitch != 0 || rotRoll != 0;
    }

    public boolean hasPose() {
        return posed() || rotYaw != 0;
    }

    /** Sets the pose (checked), sends it to the players around and to the desk. Server side. */
    public void setPose(float ox, float oy, float oz, float yaw, float pitch, float roll) {
        offX = clamp(ox, MAX_OFFSET); offY = clamp(oy, MAX_OFFSET); offZ = clamp(oz, MAX_OFFSET);
        rotYaw = clamp(yaw, 180f); rotPitch = clamp(pitch, 90f); rotRoll = clamp(roll, 180f);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            LightServer.fixtureMoved(level, worldPosition, this);
        }
    }

    private static float clamp(float v, float lim) {
        if (!Float.isFinite(v)) return 0f;
        return Math.round(Math.max(-lim, Math.min(lim, v)) * 1000f) / 1000f;
    }

    // client only: motors and beam
    public float curPan = Float.NaN, curTilt = Float.NaN, goboAngle, prismAngle;
    /** Rev. 1.3.0-2: angle of each gobo wheel (wheel 2: PHANTOM S1); {@link #goboAngle} is the one of the gobo in the light. */
    public float wheel1Angle, wheel2Angle;
    /** Rev. 1.3.0-2: speed of the pan and tilt motors (degrees / s, client). */
    public float panVel, tiltVel;
    public long lastFrameNanos;
    /**
     * Client, 1.2.2: the light of a tungsten lamp (a conventional or a blinder) right now, after its filament's inertia, and the
     * time it was worked out (System.nanoTime). NaN until the first frame of the fixture. Not saved, not synced: a look only.
     */
    public float lampLevel = Float.NaN;
    public long lampNanos;
    /**
     * 1.2.2 (RackWorks, Forge): whether the fixture has mains from the RackWorks power network, and the level of its feed. True
     * and 1 when RackWorks is not installed (or the fixture is not cabled): the fixture works as it always did. Synced to the
     * clients with the update tag, never saved (a fixture placed again must not remember being unplugged).
     */
    public boolean mainsPowered = true;
    public float mainsLevel = 1f;
    /** 1.2.2 (Forge): the loader's cable host (RackWorks) for this fixture, null until the loader makes it. */
    public Object rackworksHost;
    /** The loader's optional hooks (cables, mains); null in a build without them (Fabric). */
    public static FixtureHooks HOOKS;
    /** Whether {@link #HOOKS} has been told this fixture is in the world (setLevel or load). */
    private boolean hooksInWorld;
    /** Client, rev. 18: hanging hardware (client.light.MountGeometry.Layout), the block above it was planned for, and when. */
    public Object mount;
    public net.minecraft.world.level.block.state.BlockState mountSupport;
    public long mountTime = Long.MIN_VALUE / 2;
    public float beamLength = -1;
    public int beamCheckFrame = -1000;
    public net.minecraft.core.Direction beamHitFace;
    /** Client: key of the small Shimmer light in front of the lens (rev. 4), the beam's own light uses the block entity. */
    public final Object lensLight = new Object();
    /** Client (rev. 5): keys of the extra Shimmer lights spread over a wide footprint. */
    public final Object[] spreadLights = {new Object(), new Object(), new Object(), new Object()};
    /** Rev. 21 (client): the cells of a zone fixture at this instant, each zone with its own strobe (ZoneStrobe). */
    public float[] frameZones;
    /**
     * Client (rev. 5): surfaces under the beam, sampled by a few rays and kept for several frames. Per ray, {@link #HIT_STRIDE} floats:
     * hit point (block-local x, y, z), face normal (x, y, z), distance from the lens (negative = no hit), cosine of incidence, light
     * already on the surface (0..1).
     */
    public static final int HIT_STRIDE = 9;
    public final float[] surfaceHits = new float[13 * HIT_STRIDE];
    /**
     * Client (rev. 8): spill light — surfaces around the fixture (truss above, roof, tarps, platform, structure next to it) found by
     * rays from the fixture, same layout as {@link #surfaceHits}, rarely refreshed (they only change when the rig is rebuilt).
     */
    public final float[] spillHits = new float[14 * HIT_STRIDE];
    public int spillCount, spillFrame = Integer.MIN_VALUE / 2;
    /** Client (rev. 8): key of the Shimmer light put on the rig above / around the fixture. */
    public final Object rigLight = new Object();
    public int surfaceCount, surfaceFrame = Integer.MIN_VALUE / 2;
    /** Beam axis and cone tangent when the surfaces were sampled (resampled when the head has moved). */
    public float surfAx, surfAy, surfAz, surfTan = -1f;
    /** Rev. 22 (client): last centre ray of a far ribbon (frame, axis), so its length follows the head and stops on the deck. */
    /** Client (1.3.0 rev. 1): lengths of the lines of a laser figure (LaserBeams), measured at most once per game tick. */
    public final float[] laserLen = new float[24];
    public final boolean[] laserHit = new boolean[24];
    public long laserTick = Long.MIN_VALUE;
    public int laserCount;
    public int farRayFrame = -100000;
    public float farAx, farAy, farAz;
    /** Client: end of the beam (world) and its radius there, for the render box. NaN until the first frame. */
    private double beamEndX = Double.NaN, beamEndY, beamEndZ, beamEndR;

    public void setBeamEnd(double x, double y, double z, double r) {
        beamEndX = x; beamEndY = y; beamEndZ = z; beamEndR = r;
    }

    public FixtureBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.FIXTURE_ENTITY, pos, state);
    }

    public BlockPos console() {
        return console;
    }

    public int fid() {
        return fid;
    }

    /** Rev. 1.3.0-2: id of the desk that patched this fixture (0: patched before rev. 2, or not patched). */
    public long deskId() {
        return deskId;
    }

    private long deskId;

    public void patch(BlockPos desk, int fixtureId) {
        patch(desk, fixtureId, 0L);
    }

    public void patch(BlockPos desk, int fixtureId, long id) {
        console = desk == null ? null : desk.immutable();
        fid = desk == null ? 0 : fixtureId;
        deskId = desk == null ? 0L : id;
        reported = worldPosition;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState s = getBlockState();
            level.sendBlockUpdated(worldPosition, s, s, Block.UPDATE_CLIENTS);
        }
    }

    public FixtureBlock block() {
        return getBlockState().getBlock() instanceof FixtureBlock b ? b : null;
    }

    /**
     * 1.2.2 (RackWorks): the mains of the fixture from the power network. A change is synced to the clients, where the renderer
     * makes the fixture dark (and lets a halogen lamp cool out) while it has no supply.
     */
    public void setMains(boolean on, float fedLevel) {
        float lv = Math.max(0f, Math.min(1f, fedLevel));
        if (on == mainsPowered && Math.abs(lv - mainsLevel) < 0.01f) return;
        mainsPowered = on;
        mainsLevel = lv;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState s = getBlockState();
            level.sendBlockUpdated(worldPosition, s, s, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        hooksJoin();                                                // 1.2.2: a new fixture (and a loaded one) joins the loader's networks
    }

    @Override
    public void setRemoved() {
        if (hooksInWorld && HOOKS != null) {
            hooksInWorld = false;
            HOOKS.leave(this);
        }
        super.setRemoved();
    }

    /** The loader hears that the fixture is in the world, once (its grids, its render tracking). */
    private void hooksJoin() {
        if (!hooksInWorld && HOOKS != null) {
            hooksInWorld = true;
            HOOKS.join(this);
        }
    }

    /** Every 2 s on the server: a fixture carried by a lift tells its desk where it is now. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, FixtureBlockEntity be) {
        if (be.console == null || (level.getGameTime() + pos.asLong()) % 40 != 0) return;
        if (!pos.equals(be.reported)) {
            LightServer.fixtureMoved(level, pos, be);
            be.reported = pos.immutable();
            return;
        }
        // rev. 1.3.0-2: a link to a desk that is gone, was replaced, or no longer has this fixture is dropped, so the fixture is free
        // again for the auto patch and does not answer the fixture number the desk gives to another one
        LightServer.checkLink(level, pos, be);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("ManualPan", manualPan); tag.putFloat("ManualTilt", manualTilt);
        if (gel != 0xFFFFFF) tag.putInt("Gel", gel);                            // 1.2.1
        if (hasPose()) {                                                         // 1.2.1
            tag.putFloat("PoseX", offX); tag.putFloat("PoseY", offY); tag.putFloat("PoseZ", offZ);
            tag.putFloat("PoseYaw", rotYaw); tag.putFloat("PosePitch", rotPitch); tag.putFloat("PoseRoll", rotRoll);
        }
        if (console != null) {
            tag.putInt("ConsoleX", console.getX());
            tag.putInt("ConsoleY", console.getY());
            tag.putInt("ConsoleZ", console.getZ());
            tag.putInt("Fid", fid);
            if (deskId != 0) tag.putLong("DeskId", deskId);
        }
        if (reported != null) tag.putLong("Reported", reported.asLong());
        if (HOOKS != null) HOOKS.save(this, tag);                   // 1.2.2: RackWorks cables, saved with the fixture
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        manualPan = tag.getFloat("ManualPan"); manualTilt = tag.getFloat("ManualTilt");
        if (!Float.isFinite(manualPan)) manualPan = 0;
        if (!Float.isFinite(manualTilt)) manualTilt = 0;
        gel = tag.contains("Gel") ? tag.getInt("Gel") & 0xFFFFFF : 0xFFFFFF;
        offX = clamp(tag.getFloat("PoseX"), MAX_OFFSET); offY = clamp(tag.getFloat("PoseY"), MAX_OFFSET); offZ = clamp(tag.getFloat("PoseZ"), MAX_OFFSET);
        rotYaw = clamp(tag.getFloat("PoseYaw"), 180f); rotPitch = clamp(tag.getFloat("PosePitch"), 90f); rotRoll = clamp(tag.getFloat("PoseRoll"), 180f);
        console = tag.contains("ConsoleX") ? new BlockPos(tag.getInt("ConsoleX"), tag.getInt("ConsoleY"), tag.getInt("ConsoleZ")) : null;
        fid = tag.getInt("Fid");
        deskId = console == null ? 0L : tag.getLong("DeskId");
        reported = tag.contains("Reported") ? BlockPos.of(tag.getLong("Reported")) : null;
        lampLevel = Float.NaN;                                      // 1.2.2: the lamp starts cold (the first frame takes its level)
        // 1.2.2 (RackWorks): the mains is synced, never saved; a fixture placed again starts supplied until the network says otherwise
        mainsPowered = !tag.contains("Mains") || tag.getBoolean("Mains");
        mainsLevel = tag.contains("MainsLevel") ? Math.max(0f, Math.min(1f, tag.getFloat("MainsLevel"))) : 1f;
        if (HOOKS != null) {
            HOOKS.load(this, tag);
            hooksJoin();
            HOOKS.rebind(this);                                     // the links were read: a lift may have moved the fixture
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = saveWithoutMetadata();
        if (HOOKS != null) {
            tag.putBoolean("Mains", mainsPowered);
            tag.putFloat("MainsLevel", mainsLevel);
            tag.putByte("PowerSync", (byte) 1);                     // RackWorks: an empty cable update is still sent, so the client clears it
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Render box: the fixture and its beam (rev. 2), so a fixture whose beam is off screen is skipped (Forge culls with this box,
     * Fabric through ClientCulling). Before the first frame, a wide box.
     */
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(worldPosition).inflate(posed() ? 2.5 : 0.5);   // 1.2.1
        if (Double.isNaN(beamEndX)) box = box.inflate(48);
        else {
            double r = beamEndR + 1;
            box = box.minmax(new net.minecraft.world.phys.AABB(beamEndX - r, beamEndY - r, beamEndZ - r, beamEndX + r, beamEndY + r, beamEndZ + r));
        }
        // 1.2.2 (RackWorks): the cables run past the fixture, up to the far ends
        return HOOKS != null ? HOOKS.renderBox(this, box) : box;
    }
}
