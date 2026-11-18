package com.concertstage.block.offset;

import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The block set on a stage deck whose floor is not at a block boundary, and how far down it goes so it rests on the floor. */
public class OffsetBlockEntity extends BlockEntity {
    private BlockState stored = Blocks.AIR.defaultBlockState();
    private double drop;
    /** Saved data of the held block's own block entity (another mod's speaker, light...), drawn and dropped with it; null if none. */
    private CompoundTag heldData;
    private BlockEntity heldView;
    private VoxelShape shape, collision;

    public OffsetBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.OFFSET_BLOCK_ENTITY, pos, state);
    }

    public BlockState stored() { return stored; }
    public double drop() { return drop; }

    public CompoundTag heldData() { return heldData; }

    /** A detached copy of the held block's block entity (for its renderer and its drops), or null. */
    public BlockEntity heldView() {
        if (heldData == null) return null;
        if (heldView == null) {
            try {
                heldView = BlockEntity.loadStatic(worldPosition, stored, heldData.copy());
                if (heldView != null && level != null) heldView.setLevel(level);
            } catch (RuntimeException e) {
                heldData = null;
            }
        }
        return heldView;
    }

    public void set(BlockState state, double drop, CompoundTag data) {
        this.heldData = data;
        this.heldView = null;
        set(state, drop);
    }

    public void set(BlockState state, double drop) {
        this.stored = state;
        this.drop = drop;
        this.shape = this.collision = null;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    public VoxelShape shape(BlockGetter level, BlockPos pos) {
        if (shape == null) shape = stored.isAir() ? Shapes.block() : stored.getShape(level, pos).move(0, -drop, 0);
        return shape;
    }

    public VoxelShape collision(BlockGetter level, BlockPos pos) {
        if (collision == null) collision = stored.isAir() ? Shapes.block() : stored.getCollisionShape(level, pos).move(0, -drop, 0);
        return collision;
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
        tag.put("Stored", NbtUtils.writeBlockState(stored));
        tag.putDouble("Drop", drop);
        if (heldData != null) tag.put("Held", heldData);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("Stored"));
        drop = tag.getDouble("Drop");
        heldData = tag.contains("Held") ? tag.getCompound("Held") : null;
        heldView = null;
        shape = collision = null;
        if (level != null && level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }
}
