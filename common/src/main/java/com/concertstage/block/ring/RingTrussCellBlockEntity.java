package com.concertstage.block.ring;

import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shape of one cell of a circular truss and the offset to its rigging point (relative, so it survives a lift). */
public class RingTrussCellBlockEntity extends BlockEntity {
    private float[] box = {0, 0, 0, 1, 1, 1};
    private int ax, ay, az;
    private VoxelShape shape;
    /** A cell of a roof sheet: it only stops light beams (players, mobs and clicks go through). */
    private boolean lightOnly;

    public boolean lightOnly() { return lightOnly; }

    public void set(float[] box, BlockPos anchor, boolean lightOnly) {
        this.lightOnly = lightOnly;
        set(box, anchor);
    }

    public RingTrussCellBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.RING_TRUSS_CELL_ENTITY, pos, state);
    }

    public void set(float[] box, BlockPos anchor) {
        this.box = box.clone();
        this.ax = anchor.getX() - worldPosition.getX();
        this.ay = anchor.getY() - worldPosition.getY();
        this.az = anchor.getZ() - worldPosition.getZ();
        this.shape = null;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public BlockPos anchor() {
        return worldPosition.offset(ax, ay, az);
    }

    public VoxelShape shape() {
        if (shape == null) shape = Shapes.box(box[0], box[1], box[2], Math.max(box[0] + 0.01, box[3]), Math.max(box[1] + 0.01, box[4]), Math.max(box[2] + 0.01, box[5]));
        return shape;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        for (int i = 0; i < 6; i++) tag.putFloat("B" + i, box[i]);
        tag.putInt("AX", ax);
        tag.putInt("AY", ay);
        tag.putInt("AZ", az);
        if (lightOnly) tag.putBoolean("L", true);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("B0")) for (int i = 0; i < 6; i++) box[i] = tag.getFloat("B" + i);
        ax = tag.getInt("AX");
        ay = tag.getInt("AY");
        az = tag.getInt("AZ");
        lightOnly = tag.getBoolean("L");
        shape = null;
    }
}
