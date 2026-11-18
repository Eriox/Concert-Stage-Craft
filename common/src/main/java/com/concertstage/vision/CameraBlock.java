package com.concertstage.vision;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The four CSC cameras (1.3.0): CAM PTZ P1, BROADCAST B1 (tripod), CRANE C1, REMOTE R1 (truss clamp). Right click: camera settings
 * (number, name, pan / tilt / zoom, boom, presets). The PTZ and the REMOTE R1 can hang under a block (ceiling plate, truss clamp).
 */
public class CameraBlock extends VisionBlock {
    public final CameraKind kind;

    public CameraBlock(Properties props, CameraKind kind, double[] box, double[] hangBox) {
        super(props, box, hangBox);
        this.kind = kind;
    }

    @Override
    protected boolean canHang() { return kind == CameraKind.PTZ || kind == CameraKind.REMOTE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CameraBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof CameraBlockEntity be) {
            be.number = CameraData.get(sl).nextNumber();
            be.changed();
            VisionAutoLink.cameraPlaced(sl, pos);            // rev. 2: shown on the nearest processor at once
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        boolean gone = !newState.is(state.getBlock());
        super.onRemove(state, level, pos, newState, moved);
        if (gone && level instanceof ServerLevel sl) CameraData.get(sl).remove(sl, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            VisionClientHooks.openCamera(pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }
}
