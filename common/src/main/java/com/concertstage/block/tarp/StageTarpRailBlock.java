package com.concertstage.block.tarp;

import com.concertstage.block.StageProperties;
import com.concertstage.block.TrussGap;
import com.concertstage.stage.StageEquipmentBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Stage tarp (PVC, welded hems, eyelets every 50 cm, ball bungees). The item is the first top piece of the tarp; the tarp
 * runs to the RIGHT of where you look and hangs down.
 * <ul>
 * <li>Under a truss: hung from the bottom chord on the side you stand on (like the curtain rail).</li>
 * <li>Against the side of a stage (festival assembly, CSC decks, any stage equipment): tied to the stage rim, the top of the
 *     tarp is level with the deck surface of the stage, the tarp lies against the stage face (stage skirt).</li>
 * <li>Every column hangs down to the first thing under it (deck, railing, ledger, ground) and its hem is tied to it, down to
 *     the real surface of a deck lower than the top of its cell (a 40 cm deck, a riser). The height set with the Tarp
 *     Tool is the most it may hang.</li>
 * <li>Tarp Tool: length, maximum height, position on the truss. Dye (black, white, grey, blue, red): recolour.</li>
 * </ul>
 */
public class StageTarpRailBlock extends Block implements EntityBlock {
    @Override
    public void playerWillDestroy(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                                  net.minecraft.world.entity.player.Player player) {
        com.concertstage.block.Drops.breaking(level, player);
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Broken by a player: the item goes straight into the inventory. */
    @Override
    public void playerDestroy(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos,
                              net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.entity.BlockEntity be,
                              net.minecraft.world.item.ItemStack tool) {
        com.concertstage.block.Drops.playerDestroy(this, level, player, pos, state, be, tool);
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** True for the compact FOH (régie) tarp: always on the tube line of the cell, tied to every FOH element. */
    private final boolean regie;

    public StageTarpRailBlock(Properties properties) {
        this(properties, false);
    }

    public StageTarpRailBlock(Properties properties, boolean regie) {
        super(properties);
        this.regie = regie;
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.EAST)
            .setValue(StageProperties.SHIFT, 0)
            .setValue(TarpProperties.COLOR, TarpColor.BLACK)
            .setValue(TarpProperties.TOP, 0)
            .setValue(TarpProperties.END_NEG, true)
            .setValue(TarpProperties.END_POS, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, StageProperties.SHIFT, TarpProperties.COLOR, TarpProperties.TOP, TarpProperties.END_NEG, TarpProperties.END_POS);
    }

    public boolean isRegie() {
        return regie;
    }

    /** Pieces placed by this tarp. */
    public Block topBlock() {
        return regie ? com.concertstage.StageContent.REGIE_TARP_TOP : com.concertstage.StageContent.STAGE_TARP_TOP;
    }

    public Block clothBlock() {
        return regie ? com.concertstage.StageContent.REGIE_TARP_CLOTH : com.concertstage.StageContent.STAGE_TARP_CLOTH;
    }

    /**
     * FOH tarp tied from the side to the FOH element beside {@code pos} (direction {@code side}): TOP 9..16 = top of the cloth at
     * 2 * (TOP - 8) px, just above the tube it is tied to (ledger / node: middle of the cell; rail, panel: top ledger; girder: top chord;
     * post: top of the cell). 0 when there is no FOH element there.
     */
    static int regieSideTop(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos q = pos.relative(side);
        BlockState s = level.getBlockState(q);
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        if (!path.startsWith("regie_") || path.startsWith("regie_tarp") || path.equals("regie_roof")) return 0;
        int drop = s.getBlock() instanceof com.concertstage.block.RegieBlock ? s.getValue(com.concertstage.block.RegieBlock.DROP) : 0;
        double tubePx;
        if (path.startsWith("regie_lisse") || path.startsWith("regie_noeud")) tubePx = 8 - drop;
        else {
            VoxelShape shape = s.getCollisionShape(level, q);
            tubePx = shape.isEmpty() ? 15 : shape.max(Direction.Axis.Y) * 16 - 1;
        }
        int topPx = (int) Math.round(tubePx + 1.5);
        return 8 + Math.max(1, Math.min(8, (int) Math.ceil(topPx / 2.0)));
    }

    /**
     * FOH tarp: TOP = how far up (2 px steps) its ties reach to the element above: a ledger / node tube is in the middle of its
     * cell (8 px up), a lattice girder chord 4 px up, a truss chord 1.5 px; nothing above: 0.
     */
    static int regieHang(BlockGetter level, BlockPos pos) {
        BlockPos up = pos.above();
        BlockState s = level.getBlockState(up);
        VoxelShape shape = s.isAir() ? Shapes.empty() : s.getCollisionShape(level, up);
        if (shape.isEmpty()) {
            // an element lowered onto a festival deck (DROP) can hang its tube into the cell under it: the tarp is then one cell lower
            // and its ties reach up into that cell, to the tube
            BlockPos up2 = pos.above(2);
            BlockState s2 = level.getBlockState(up2);
            if (s2.isAir()) return 0;
            VoxelShape shape2 = s2.getCollisionShape(level, up2);
            if (shape2.isEmpty() || shape2.min(Direction.Axis.Y) >= 0) return 0;
            return Math.max(0, Math.min(8, (int) Math.round((1 + shape2.min(Direction.Axis.Y)) * 8 + 0.5)));
        }
        return Math.max(0, Math.min(8, (int) Math.round(shape.min(Direction.Axis.Y) * 8 + 0.5)));
    }

    /** Where a FOH tarp goes under the FOH element at {@code element}: the cell under it, or two cells under when its tube hangs into the cell below (DROP). */
    public static BlockPos regieHangPos(BlockGetter level, BlockPos element) {
        BlockState s = level.getBlockState(element);
        VoxelShape shape = s.getCollisionShape(level, element);
        return !shape.isEmpty() && shape.min(Direction.Axis.Y) < 0 ? element.below(2) : element.below();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (regie) {
            // FOH tarp: always hung UNDER the element above it (the item puts it under the FOH element whatever face was clicked)
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getClockWise())
                .setValue(TarpProperties.TOP, regieHang(ctx.getLevel(), ctx.getClickedPos()));
        }
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction look = ctx.getHorizontalDirection();
        BlockState state = defaultBlockState().setValue(FACING, look.getClockWise());
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isHorizontal()) {
            // clicked on the side of something: a stage rim beside the tarp?
            Direction toStage = face.getOpposite();
            int top = rimTop(level, pos, toStage);
            if (top > 0) {
                Direction along = toStage.getClockWise();
                return state.setValue(FACING, look.getClockWise().getAxis() == along.getAxis() ? look.getClockWise() : along)
                    .setValue(StageProperties.SHIFT, StageProperties.shiftToward(toStage))
                    .setValue(TarpProperties.TOP, top);
            }
        }
        if (face.getAxis().isHorizontal()) {
            // clicked on the side of a truss: the tarp hangs along the face of the truss, tied to its top chord or its bottom chord
            // (a 2-high truss: the top chord from its upper cell, the bottom chord from its lower cell; a 1-high truss: the half clicked)
            Direction toTruss = face.getOpposite();
            BlockState t = level.getBlockState(pos.relative(toTruss));
            if (t.getBlock() instanceof com.concertstage.block.MultiPartBlock mp && TrussGap.isTruss(t)) {
                int rows = mp.sizeY(), row = mp.rowOf(t);
                boolean upper = rows > 1 ? row == rows - 1 : ctx.getClickLocation().y - pos.getY() >= 0.5;
                Direction along = toTruss.getClockWise();
                return state.setValue(FACING, look.getClockWise().getAxis() == along.getAxis() ? look.getClockWise() : along)
                    .setValue(StageProperties.SHIFT, StageProperties.shiftToward(toTruss))
                    .setValue(TarpProperties.TOP, upper ? 8 : 2);
            }
        }
        BlockState above = level.getBlockState(pos.above());
        return state.setValue(StageProperties.SHIFT, TrussGap.curtainShift(above, look.getOpposite()));
    }

    /**
     * Top of the stage beside {@code pos} (in direction {@code side}) as a TOP value (1..16 = 2 px steps above the bottom of the
     * cell of {@code pos}), or 0 when there is no stage equipment there. The deck surface is the highest collision of the stage
     * column, in this cell and the one above.
     */
    static int rimTop(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos n = pos.relative(side);
        double best = -1;
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos q = n.above(dy);
            BlockState s = level.getBlockState(q);
            if (!(s.getBlock() instanceof StageEquipmentBlock)) break;
            VoxelShape shape = s.getCollisionShape(level, q);
            if (!shape.isEmpty()) best = dy + shape.max(Direction.Axis.Y);
        }
        if (best <= 0) return 0;
        return Math.max(1, Math.min(16, (int) Math.round(best * 8)));
    }

    /** No collision for players, but a Theatrical light beam stops on the cloth (it casts a shadow). */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return com.concertstage.compat.LightCompat.isLightRay(ctx) ? getShape(state, level, pos, ctx) : super.getCollisionShape(state, level, pos, ctx);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return TarpShapes.get(state.getValue(FACING).getAxis(), state.getValue(StageProperties.SHIFT));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StageTarpRailBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StageTarpRailBlockEntity be) be.rebuild();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof StageTarpRailBlockEntity be) {
            be.clearAll();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        if (level.isClientSide) return;
        if (level.getBlockEntity(pos) instanceof StageTarpRailBlockEntity be) be.setPowered(level.hasNeighborSignal(pos));   // redstone: gathered / spread like the curtain
        level.scheduleTick(pos, this, 2);
    }

    /** Put back by a lift (or placed): fit again a moment later, the tied columns reach their support again. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(this)) level.scheduleTick(pos, this, 3);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof StageTarpRailBlockEntity be) be.rebuild();
    }

    /** Dye: recolour. Empty hand: gather the tarp against its first column / spread it again (like the curtain rail). */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult dyed = StageTarpClothBlock.dye(level, pos, player, hand);
        if (dyed != InteractionResult.PASS) return dyed;
        if (player.getItemInHand(hand).isEmpty()) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof StageTarpRailBlockEntity be) be.toggleOpen();
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** Asks the tarp owning the piece at {@code pos} to fit itself again a moment later (never inside a neighbour update). */
    static void scheduleRefit(Level level, BlockPos pos) {
        BlockPos rail = findRail(level, pos);
        if (rail != null) level.scheduleTick(rail, level.getBlockState(rail).getBlock(), 2);
    }

    /** Position of the tarp rail owning the tarp piece at {@code pos}, or null. */
    public static BlockPos findRail(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof StageTarpRailBlock) return pos;
        Direction.Axis axis;
        if (state.getBlock() instanceof StageTarpClothBlock) axis = state.getValue(StageTarpClothBlock.AXIS);
        else if (state.getBlock() instanceof StageTarpTopBlock) axis = state.getValue(StageTarpTopBlock.AXIS);
        else return null;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (dir.getAxis() != axis) continue;
            for (int j = 0; j < StageTarpRailBlockEntity.MAX; j++) {
                for (int k = 0; k < StageTarpRailBlockEntity.MAX; k++) {
                    BlockPos q = pos.relative(dir.getOpposite(), k).above(j);
                    BlockState st = level.getBlockState(q);
                    if (st.getBlock() instanceof StageTarpRailBlock && st.getValue(FACING) == dir
                        && level.getBlockEntity(q) instanceof StageTarpRailBlockEntity rail && rail.getLength() > k && rail.getHeight() > j) return q;
                    if (!(st.getBlock() instanceof StageTarpTopBlock || st.getBlock() instanceof StageTarpClothBlock || st.getBlock() instanceof StageTarpRailBlock)) break;
                }
            }
        }
        return null;
    }
}
