package com.concertstage.vision;

import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A CSC camera (1.3.0): number and name shown in the regie, the position of its axes (pan, tilt, zoom, crane boom, head pan) and
 * eight memorised positions. A move is a timed interpolation (start, from, to, length from the largest angle and the speed of the
 * model, eased in and out), so every player sees the same smooth motion and the camera image follows it, without sending anything
 * per tick.
 */
public class CameraBlockEntity extends BlockEntity {
    public static final int AXES = 5, PRESETS = 8;
    public int number = 1;
    public String name = "";
    /**
     * Rev. 4c (asked 13 November: "an option on the camera to choose the quality and the images per second I want"): height of the
     * camera's image (0 = what the screens ask, or 360 / 480 / 720 / 1080) and images per second (0 = the screens, or 15 / 24 / 30 / 60).
     * When set, the camera gets them on every screen even if the game loses some frames for it.
     */
    public int videoHeight, videoFps;
    public static final int[] HEIGHTS = {0, 360, 480, 720, 1080}, FPS = {0, 15, 24, 30, 60};
    private final float[] from = new float[AXES], to = new float[AXES];
    private long moveStart;
    private int moveTicks = 1;
    public final float[][] presets = new float[PRESETS][];

    public CameraBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.CAMERA_ENTITY, pos, state);
        to[2] = from[2] = 0.3f;
    }

    public CameraKind kind() {
        return getBlockState().getBlock() instanceof CameraBlock c ? c.kind : CameraKind.PTZ;
    }

    public boolean hanging() {
        BlockState s = getBlockState();
        return s.hasProperty(VisionBlock.HANGING) && s.getValue(VisionBlock.HANGING);
    }

    /** Value of axis {@code a} at this moment (eased). */
    public float axis(int a, double gameTime) {
        double t = (gameTime - moveStart) / Math.max(1, moveTicks);
        if (t >= 1) return to[a];
        if (t <= 0) return from[a];
        double e = t * t * (3 - 2 * t);
        return (float) (from[a] + (to[a] - from[a]) * e);
    }

    public float target(int a) { return to[a]; }

    /** Moves to {@code values} (null entries: unchanged), clamped to the mechanics, at the speed of the model. */
    public void moveTo(Float[] values, long now) {
        CameraKind k = kind();
        float max = 0;
        for (int a = 0; a < AXES; a++) {
            float cur = axis(a, now);
            from[a] = cur;
            if (a < values.length && values[a] != null) to[a] = k.clamp(a, values[a]);
            max = Math.max(max, a == 2 ? Math.abs(to[a] - cur) * 60 : Math.abs(to[a] - cur));
        }
        moveStart = now;
        moveTicks = Math.max(2, Math.round(max / k.speed * 20));
        changed();
    }

    public void store(int i) {
        if (i < 0 || i >= PRESETS) return;
        presets[i] = to.clone();
        changed();
    }

    public void recall(int i, long now) {
        if (i < 0 || i >= PRESETS || presets[i] == null) return;
        Float[] v = new Float[AXES];
        for (int a = 0; a < AXES; a++) v[a] = presets[i][a];
        moveTo(v, now);
    }

    /** Forge: the crane reaches 4.7 m in front of its block. */
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(kind() == CameraKind.CRANE ? 6 : 1.5);
    }

    public String label() { return "CAM " + number + (name.isEmpty() ? "" : " - " + name); }

    public void changed() {
        setChanged();
        if (level instanceof ServerLevel sl) {
            BlockState st = getBlockState();
            level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
            CameraData.get(sl).update(this);
        }
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Number", number);
        tag.putString("Name", name);
        for (int a = 0; a < AXES; a++) { tag.putFloat("From" + a, from[a]); tag.putFloat("To" + a, to[a]); }
        tag.putLong("MoveStart", moveStart);
        tag.putInt("VideoHeight", videoHeight);
        tag.putInt("VideoFps", videoFps);
        tag.putInt("MoveTicks", moveTicks);
        for (int i = 0; i < PRESETS; i++) {
            if (presets[i] == null) continue;
            int[] bits = new int[AXES];
            for (int a = 0; a < AXES; a++) bits[a] = Float.floatToIntBits(presets[i][a]);
            tag.putIntArray("Preset" + i, bits);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Number")) number = tag.getInt("Number");
        name = tag.getString("Name");
        for (int a = 0; a < AXES; a++) {
            if (tag.contains("To" + a)) { from[a] = tag.getFloat("From" + a); to[a] = tag.getFloat("To" + a); }
        }
        moveStart = tag.getLong("MoveStart");
        videoHeight = tag.getInt("VideoHeight");
        videoFps = tag.getInt("VideoFps");
        moveTicks = Math.max(1, tag.getInt("MoveTicks"));
        for (int i = 0; i < PRESETS; i++) {
            int[] bits = tag.getIntArray("Preset" + i);
            if (bits.length < AXES) { presets[i] = null; continue; }
            presets[i] = new float[AXES];
            for (int a = 0; a < AXES; a++) presets[i][a] = Float.intBitsToFloat(bits[a]);
        }
    }
}
