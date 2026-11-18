package com.concertstage.vision;

import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One LED panel: the wall it belongs to (id kept in the world registry {@link WallData}) and the accessories fitted on it. */
public class LedPanelBlockEntity extends BlockEntity {
    public static final int MOUNT_HANG = 1, MOUNT_GROUND = 2, MOUNT_WALL = 4;
    private long wallId;
    private int mounts;

    public LedPanelBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.LED_PANEL_ENTITY, pos, state);
    }

    public long wallId() { return wallId; }

    /** Rev. 1.3.0-3 (Forge reads it): a panel of a wall at an angle is drawn away from its block, up to the width of its wall. */
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(wallId == 0 ? 1.5 : 12.0, 1.5, wallId == 0 ? 1.5 : 12.0);
    }
    public int mounts() { return mounts; }

    void setWallId(long id) {
        if (id == wallId) return;
        wallId = id;
        changed();
    }

    /** Fits or removes an accessory; returns true when it is now fitted. */
    public boolean toggleMount(int bit) {
        mounts ^= bit;
        changed();
        return (mounts & bit) != 0;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState st = getBlockState();
            level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Wall", wallId);
        tag.putInt("Mounts", mounts);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        wallId = tag.getLong("Wall");
        mounts = tag.getInt("Mounts");
    }
}
