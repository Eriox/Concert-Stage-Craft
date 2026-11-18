package com.concertstage.block;

import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stores the size and colour of a curtain and keeps the cloth blocks in sync.
 * Row 0 (the rail row) holds the rail bar with the cloth hanging from it, then {@code height - 1} rows of cloth below.
 * The curtain is {@code length} blocks long along the rail direction. Length and height go from 1 to {@link #MAX}.
 */
public class CurtainRailBlockEntity extends BlockEntity implements ResizableCloth {
    public static final int MAX = 50;

    private int length = 4;
    private int height = 4;
    private DyeColor color = DyeColor.BLACK;

    public CurtainRailBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.CURTAIN_RAIL_ENTITY, pos, state);
    }

    @Override public int getLength() { return length; }
    @Override public int getHeight() { return height; }

    /** Position of the cloth across its thickness (see {@link StageProperties#SHIFT}). */
    public int getShift() {
        return getBlockState().getValue(StageProperties.SHIFT);
    }

    public void configure(int length, int height, DyeColor color) {
        configure(length, height, color, getShift());
    }

    public void configure(int length, int height, DyeColor color, int shift) {
        this.length = clamp(length);
        this.height = clamp(height);
        this.color = color;
        setChanged();
        syncRailState(shift);
        rebuild();
        recolor();
        syncToClient();
    }

    /** Called by the Tarp Tool screen (through the network). */
    public void applySettings(int length, int height, int shift) {
        this.length = clamp(length);
        this.height = clamp(height);
        setChanged();
        syncRailState(Math.max(0, Math.min(2, shift)));
        rebuild();
        syncToClient();
    }

    @Override
    public void adjust(int mode, int delta) {
        if (mode == 0) length = clamp(length + delta);
        else height = clamp(height + delta);
        setChanged();
        rebuild();
        syncToClient();
    }

    public void setColor(DyeColor color) {
        this.color = color;
        setChanged();
        syncRailState(getShift());
        recolor();
        syncToClient();
    }

    private static int clamp(int v) {
        return Math.max(1, Math.min(MAX, v));
    }

    /** Keeps the colour and the shift of the rail block itself in line with the curtain. */
    private void syncRailState(int shift) {
        Level lvl = getLevel();
        if (lvl == null || lvl.isClientSide) return;
        BlockState st = lvl.getBlockState(worldPosition);
        if (!(st.getBlock() instanceof CurtainRailBlock)) return;
        BlockState next = st.setValue(CurtainRailBlock.COLOR, color).setValue(StageProperties.SHIFT, shift);
        if (next != st) lvl.setBlock(worldPosition, next, Block.UPDATE_ALL);
    }

    private void syncToClient() {
        Level lvl = getLevel();
        if (lvl == null || lvl.isClientSide) return;
        BlockState st = lvl.getBlockState(worldPosition);
        lvl.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_ALL);
    }

    /** Places / removes cloth so that it matches length, height, shift and the open (gathered) state. */
    public void rebuild() {
        Level lvl = getLevel();
        if (lvl == null || lvl.isClientSide) return;
        BlockState rail = lvl.getBlockState(worldPosition);
        if (!(rail.getBlock() instanceof CurtainRailBlock)) return;
        Direction dir = rail.getValue(CurtainRailBlock.FACING);
        int shift = rail.getValue(StageProperties.SHIFT);
        int effLength = rail.getValue(CurtainRailBlock.OPEN) ? 1 : length;

        for (int k = 0; k < MAX; k++) {
            for (int j = 0; j < MAX; j++) {
                if (k == 0 && j == 0) continue; // the rail block itself
                BlockPos p = worldPosition.relative(dir, k).below(j);
                if (!lvl.hasChunkAt(p)) continue;
                BlockState cur = lvl.getBlockState(p);
                boolean wanted = k < effLength && j < height;
                if (wanted) {
                    if (cur.isAir()) {
                        boolean top = j == 0;
                        int drop = top ? TrussGap.gapUnder(lvl.getBlockState(p.above())) : 0;
                        lvl.setBlock(p, StageContent.CURTAIN_CLOTH.defaultBlockState()
                            .setValue(CurtainClothBlock.AXIS, dir.getAxis())
                            .setValue(CurtainClothBlock.COLOR, color)
                            .setValue(CurtainClothBlock.TOP, top)
                            .setValue(StageProperties.DROP, drop)
                            .setValue(StageProperties.SHIFT, shift), Block.UPDATE_CLIENTS);
                    } else if (cur.getBlock() instanceof CurtainClothBlock
                        && cur.getValue(StageProperties.SHIFT) != shift) {
                        // keep the colour of a piece that was dyed on its own, only move it
                        lvl.setBlock(p, cur.setValue(StageProperties.SHIFT, shift), Block.UPDATE_CLIENTS);
                    }
                } else if (cur.getBlock() instanceof CurtainClothBlock) {
                    lvl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private void recolor() {
        Level lvl = getLevel();
        if (lvl == null || lvl.isClientSide) return;
        BlockState rail = lvl.getBlockState(worldPosition);
        if (!(rail.getBlock() instanceof CurtainRailBlock)) return;
        Direction dir = rail.getValue(CurtainRailBlock.FACING);
        for (int k = 0; k < length; k++) {
            for (int j = 0; j < height; j++) {
                BlockPos p = worldPosition.relative(dir, k).below(j);
                if (!lvl.hasChunkAt(p)) continue;
                BlockState cur = lvl.getBlockState(p);
                if (cur.getBlock() instanceof CurtainClothBlock) {
                    lvl.setBlock(p, cur.setValue(CurtainClothBlock.COLOR, color), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Removes every cloth block of this curtain (called when the rail is broken). */
    public void clearAll() {
        Level lvl = getLevel();
        if (lvl == null || lvl.isClientSide) return;
        BlockState rail = lvl.getBlockState(worldPosition);
        if (!(rail.getBlock() instanceof CurtainRailBlock)) return;
        Direction dir = rail.getValue(CurtainRailBlock.FACING);
        for (int k = 0; k < length; k++) {
            for (int j = 0; j < height; j++) {
                BlockPos p = worldPosition.relative(dir, k).below(j);
                if (!lvl.hasChunkAt(p)) continue;
                if (lvl.getBlockState(p).getBlock() instanceof CurtainClothBlock) {
                    lvl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
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

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Length", length);
        tag.putInt("Height", height);
        tag.putInt("Color", color.getId());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Length")) length = clamp(tag.getInt("Length"));
        if (tag.contains("Height")) height = clamp(tag.getInt("Height"));
        if (tag.contains("Color")) color = DyeColor.byId(tag.getInt("Color"));
    }
}
