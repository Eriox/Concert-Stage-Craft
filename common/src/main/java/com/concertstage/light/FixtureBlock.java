package com.concertstage.light;

import com.concertstage.StageContent;
import com.concertstage.light.core.FixtureProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A CSC 1.2.0 moving light (VORTEX X1, LUMORA W7, LUMORA W19, PULSESTRIKE M). Placed on the floor it stands; placed against the
 * underside of a block (a truss) it hangs upside down. FACING is where its front looks (the player's view when placing). The block itself
 * is invisible: its renderer draws the exported model, turns the yoke and the head, lights the emitters and draws the beam.
 */
public class FixtureBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty HUNG = BooleanProperty.create("hung");
    public final String profileId;
    private final VoxelShape standing, hung;

    public FixtureBlock(Properties properties, String profileId, double halfWidth, double height) {
        super(properties);
        this.profileId = profileId;
        FixtureProfile pr = FixtureProfile.get(profileId);
        double scale = pr == null ? 1 : pr.renderScale;          // hit box at the size drawn in the world
        double w = Math.min(8, halfWidth * scale * 16), h = Math.min(16, height * scale * 16);
        standing = Block.box(8 - w, 0, 8 - w, 8 + w, h, 8 + w);
        hung = Block.box(8 - w, 16 - h, 8 - w, 8 + w, 16, 8 + w);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HUNG, false));
    }

    public FixtureProfile profile() {
        FixtureProfile p = FixtureProfile.get(profileId);
        return p != null ? p : FixtureProfile.VORTEX_X1;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, HUNG);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        boolean underside = ctx.getClickedFace() == Direction.DOWN;
        // rev. 1.3.0-2 (reported 13 November: moving lights placed against a truss stayed standing): placed against the side of a
        // block with something above it and nothing under it, the fixture hangs from what is above (a truss, a pipe, a ceiling)
        boolean sneakSide = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown() && ctx.getClickedFace().getAxis().isHorizontal();
        if (!underside && !sneakSide && ctx.getClickedFace().getAxis().isHorizontal()) {
            BlockPos at = ctx.getClickedPos();
            var level = ctx.getLevel();
            boolean above = !level.getBlockState(at.above()).isAir(), below = level.getBlockState(at.below()).isAir()
                || level.getBlockState(at.below()).canBeReplaced();
            if (above && below) underside = true;
        }
        // 1.2.1, free placement: sneaking, the fixture goes exactly where the player aims (on a tube, an edge, a wall: against the side
        // of a block it is mounted on that side); not sneaking, a standing fixture still sits on the real top of what is under it (a
        // slab, a truss lower than a block)
        Direction face = ctx.getClickedFace();
        boolean sneak = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown();
        boolean side = sneak && face.getAxis().isHorizontal() && !underside;
        Direction facing = side ? face : ctx.getHorizontalDirection();
        float[] pose = aimPose(ctx, underside, side, sneak, facing);
        if (ctx.getPlayer() != null && !ctx.getLevel().isClientSide) {
            if (pose != null) PENDING.put(ctx.getPlayer().getUUID(), new Object[] {ctx.getClickedPos().immutable(), pose});
            else PENDING.remove(ctx.getPlayer().getUUID());
        }
        return defaultBlockState().setValue(FACING, facing).setValue(HUNG, underside);
    }

    /** 1.2.1: pose waiting for the block entity of a fixture being placed (player → {pos, pose}). Server side. */
    private static final java.util.Map<java.util.UUID, Object[]> PENDING = new java.util.concurrent.ConcurrentHashMap<>();

    /** {offX, offY, offZ, yaw, pitch, roll} of a fixture placed from {@code ctx}, or null for the standard place. */
    static float[] aimPose(BlockPlaceContext ctx, boolean hung, boolean side, boolean sneak, Direction facing) {
        BlockPos pos = ctx.getClickedPos();
        net.minecraft.world.phys.Vec3 hit = ctx.getClickLocation();
        float ox = snap(hit.x - (pos.getX() + 0.5)), oz = snap(hit.z - (pos.getZ() + 0.5));
        if (side) {
            // base against the side of the block, the beam straight out of the wall
            float oy = snap(hit.y - pos.getY());
            return new float[] {clampOff(ox), clampOff(oy), clampOff(oz), 0f, -90f, 0f};
        }
        if (hung) {
            if (!sneak) return null;                                             // under a truss: its hardware places it
            float oy = snap(hit.y - (pos.getY() + 1.0));
            return new float[] {clampOff(ox), clampOff(oy), clampOff(oz), aimYaw(ctx, facing), 0f, 0f};
        }
        float oy = snap(hit.y - pos.getY());
        if (ctx.getClickedFace() != Direction.UP) oy = 0f;
        if (!sneak) return Math.abs(oy) > 0.01f && oy < 0 ? new float[] {0f, clampOff(oy), 0f, 0f, 0f, 0f} : null;
        return new float[] {clampOff(ox), clampOff(oy), clampOff(oz), aimYaw(ctx, facing), 0f, 0f};
    }

    /** Fine yaw that turns the fixture from its facing to the player's exact view (5° steps). */
    private static float aimYaw(BlockPlaceContext ctx, Direction facing) {
        if (ctx.getPlayer() == null) return 0f;
        float d = net.minecraft.util.Mth.wrapDegrees(facing.toYRot() - ctx.getPlayer().getYRot());
        return Math.round(d / 5f) * 5f;
    }

    private static float snap(double v) {
        return Math.round(v * 16.0) / 16f;                                       // a sixteenth of a block
    }

    private static float clampOff(float v) {
        return Math.max(-0.5f, Math.min(0.5f, v));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(placer instanceof Player pl)) return;
        Object[] p = PENDING.remove(pl.getUUID());
        if (p == null || !pos.equals(p[0]) || !(level.getBlockEntity(pos) instanceof FixtureBlockEntity be)) return;
        float[] v = (float[]) p[1];
        be.setPose(v[0], v[1], v[2], v[3], v[4], v[5]);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape base = state.getValue(HUNG) ? hung : standing;
        // 1.2.1: a fixture moved off the centre of its block (free pose) is selected where it is drawn
        if (level != null && level.getBlockEntity(pos) instanceof FixtureBlockEntity be && be.posed()) {
            if (Math.abs(be.rotPitch) > 45f || Math.abs(be.rotRoll) > 45f) base = Block.box(3, 3, 3, 13, 13, 13);
            double dx = Math.max(-0.5, Math.min(0.5, be.offX)), dy = Math.max(-0.5, Math.min(0.5, be.offY)), dz = Math.max(-0.5, Math.min(0.5, be.offZ));
            if (Math.abs(be.rotPitch) > 45f) {                                   // mounted on a side: the body stands out of the wall
                dy = Math.max(-0.5, Math.min(0.5, be.offY - 0.5));
                if (state.getValue(FACING).getAxis() == Direction.Axis.X) dx = 0; else dz = 0;
            }
            return base.move(dx, dy, dz);
        }
        return base;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FixtureBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != StageContent.FIXTURE_ENTITY) return null;
        return (l, p, s, be) -> FixtureBlockEntity.serverTick(l, p, s, (FixtureBlockEntity) be);
    }

    /** Right click (empty hand): which desk drives it and its number. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.getItemInHand(hand).is(StageContent.LIGHT_PATCH_TOOL)
            || player.getItemInHand(hand).is(StageContent.FIXTURE_ANGLE_TOOL) || player.getItemInHand(hand).is(StageContent.CONFIG_CARD)
            || player.getItemInHand(hand).is(StageContent.AIM_TOOL)) return InteractionResult.PASS;
        // rev. 1.3.0-2: empty hand → the patch screen of the fixture (universe, address, DMX mode), asked 12 November, 22 h 38
        if (player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()) {
            if (level.isClientSide) com.concertstage.client.ClientHooks.openFixturePatch(pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FixtureBlockEntity be) {
            if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty() && !profile().has(com.concertstage.light.core.Attr.TILT)) {
                boolean pan = profile() == FixtureProfile.FOLLOWSPOT_F1 && hit.getLocation().y - pos.getY() < 0.8;
                be.adjustManual(pan);
                player.displayClientMessage(Component.literal(profile().name + " · " + (pan ? "Pan " + be.manualPan : "Tilt " + be.manualTilt) + "°"), true);
                return InteractionResult.CONSUME;
            }
            if (be.console() == null) player.displayClientMessage(Component.translatable("message.concertstage.fixture_unpatched", profile().name), true);
            else player.displayClientMessage(Component.translatable("message.concertstage.fixture_info", profile().name, be.fid(),
                be.console().getX(), be.console().getY(), be.console().getZ()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        com.concertstage.block.Drops.breaking(level, player);
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof FixtureBlockEntity be)
            LightServer.fixtureRemoved(level, pos, be);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, BlockGetter level, java.util.List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.concertstage.fixture." + profileId).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.concertstage.fixture_place").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
