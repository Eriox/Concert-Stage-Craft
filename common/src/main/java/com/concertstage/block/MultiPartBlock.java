package com.concertstage.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block whose 3D model covers several cells (e.g. 3x3x2 scaffold, 2-block-long trusses).
 * One cell ("main") renders the whole model; the other cells are invisible parts that only provide collision.
 * Breaking any part removes the whole structure.
 *
 * The footprint is described in the "authored" orientation (FACING = north): local x grows east, local y up,
 * local z south. FACING rotates the whole structure around the main cell.
 */
public class MultiPartBlock extends Block {
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
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 26);

    /** How the FACING is chosen from the way the player looks when placing. */
    public enum Placement {
        /** FACING = opposite of the look direction (scaffold: the stage front faces the player). */
        FRONT,
        /** FACING chosen so that the authored +x extension runs along the look direction. */
        EXTEND,
        /** FACING = look direction. */
        LOOK,
        /**
         * 3-way corner (open flanges at +x, +z and down): when clicking the side of a truss, turned so that one
         * flange meets it; the other flange goes toward where you look (sneak to take the other one).
         */
        CORNER3
    }

    /** Collision shape of a cell, in the authored orientation. */
    public interface ShapeProvider {
        VoxelShape get(int lx, int ly, int lz);
    }

    private final int sx, sy, sz, mx, my, mz, ax, ay, az;
    private final Placement placement;
    /** Truss pieces: placement follows the clicked face and lines up with the truss that was clicked. */
    private final boolean chain;
    private final VoxelShape[][] shapes; // [part][turns]
    private final ShapeProvider shapeProvider;
    private final boolean deferredShapes;

    public MultiPartBlock(Properties properties, int sx, int sy, int sz, int mx, int my, int mz,
                          Placement placement, ShapeProvider provider) {
        this(properties, sx, sy, sz, mx, my, mz, mx, my, mz, placement, false, provider);
    }

    /** Truss constructor: the anchor is the main cell and chained placement is on. */
    public MultiPartBlock(Properties properties, int sx, int sy, int sz, int mx, int my, int mz,
                          Placement placement, boolean chain, ShapeProvider provider) {
        this(properties, sx, sy, sz, mx, my, mz, mx, my, mz, placement, chain, provider);
    }

    /** (ax, ay, az) is the "anchor" cell: the one that lands on the clicked position when the item is placed. */
    public MultiPartBlock(Properties properties, int sx, int sy, int sz, int mx, int my, int mz,
                          int ax, int ay, int az, Placement placement, ShapeProvider provider) {
        this(properties, sx, sy, sz, mx, my, mz, ax, ay, az, placement, false, provider);
    }

    public MultiPartBlock(Properties properties, int sx, int sy, int sz, int mx, int my, int mz,
                          int ax, int ay, int az, Placement placement, boolean chain, ShapeProvider provider) {
        this(properties,sx,sy,sz,mx,my,mz,ax,ay,az,placement,chain,provider,false);
    }

    /** Opt-in deferred collision for complex compatibility stages; ordinary rigging remains eager. */
    protected MultiPartBlock(Properties properties, int sx, int sy, int sz, int mx, int my, int mz,
                             int ax, int ay, int az, Placement placement, boolean chain, ShapeProvider provider,
                             boolean deferShapes) {
        super(properties);
        this.shapeProvider = provider;
        this.deferredShapes = deferShapes;
        this.sx = sx; this.sy = sy; this.sz = sz;
        this.mx = mx; this.my = my; this.mz = mz;
        this.ax = ax; this.ay = ay; this.az = az;
        this.placement = placement;
        this.chain = chain;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, index(mx, my, mz)));

        int count = sx * sy * sz;
        this.shapes = new VoxelShape[count][4];
        if (!deferShapes) for (int idx = 0; idx < count; idx++) {
            VoxelShape base = provider.get(idx % sx, idx / (sx * sz), (idx / sx) % sz);
            for (int k = 0; k < 4; k++) {
                shapes[idx][k] = rotate(base, k);
            }
        }
    }

    private VoxelShape partShape(int part, int turn) {
        if (!deferredShapes) return shapes[part][turn];
        // Synchronize publication as chunk queries can occur on different threads.
        synchronized (shapes) {
            VoxelShape result = shapes[part][turn];
            if (result == null) {
                VoxelShape base = shapes[part][0];
                if (base == null) shapes[part][0] = base = shapeProvider.get(part % sx, part / (sx * sz), (part / sx) % sz);
                shapes[part][turn] = result = turn == 0 ? base : rotate(base, turn);
            }
            return result;
        }
    }

    // ------------------------------------------------------------ geometry helpers
    private int index(int lx, int ly, int lz) {
        return lx + sx * (lz + sz * ly);
    }

    private int mainIndex() {
        return index(mx, my, mz);
    }

    private int anchorIndex() {
        return index(ax, ay, az);
    }

    /** A cell with no collision (e.g. the free space above a scaffold deck) is not occupied, so anything can be placed in it. */
    private boolean isVirtual(int idx) {
        return idx != mainIndex() && idx != anchorIndex() && partShape(idx, 0).isEmpty();
    }

    private int count() {
        return sx * sy * sz;
    }

    /** Local y of the cell this state is (0 = bottom row of the structure). */
    private int localY(BlockState state) {
        return state.getValue(PART) / (sx * sz);
    }

    /** Number of clockwise quarter turns between the authored orientation (north) and the given facing. */
    private static int turns(Direction facing) {
        return (facing.get2DDataValue() + 2) % 4;
    }

    /** World offset of a cell from the main cell. */
    private BlockPos offset(int idx, int turns) {
        int lx = idx % sx, ly = idx / (sx * sz), lz = (idx / sx) % sz;
        int dx = lx - mx, dy = ly - my, dz = lz - mz;
        for (int i = 0; i < turns; i++) {
            int ndx = -dz, ndz = dx;
            dx = ndx;
            dz = ndz;
        }
        return new BlockPos(dx, dy, dz);
    }

    protected static VoxelShape rotate(VoxelShape shape, int turns) {
        VoxelShape result = shape;
        for (int i = 0; i < turns; i++) {
            final VoxelShape[] out = {Shapes.empty()};
            result.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                out[0] = Shapes.or(out[0], Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2)));
            result = out[0];
        }
        return result;
    }

    /** Position of the main cell of the structure this cell belongs to. */
    public BlockPos mainPos(BlockState state, BlockPos pos) {
        return pos.subtract(offset(state.getValue(PART), turns(state.getValue(FACING))));
    }

    /** 1.2.0 rev. 18: world position of cell {@code idx} of the structure this cell belongs to (cell 0 carries the block model). */
    public BlockPos cellPos(BlockState state, BlockPos pos, int idx) {
        int t = turns(state.getValue(FACING));
        return mainPos(state, pos).offset(offset(Math.max(0, Math.min(count() - 1, idx)), t));
    }

    /** True for the cell that renders the model (and holds the block entity of machines). */
    public boolean isMainPart(BlockState state) {
        return state.getValue(PART) == mainIndex();
    }

    /** Number of cells of the structure. */
    public int cellCount() {
        return count();
    }

    /** Cells along the authored x axis (the length of a truss). */
    public int sizeX() {
        return sx;
    }

    /** Cells along the vertical axis. */
    public int sizeY() {
        return sy;
    }

    /** True for the truss pieces (placement follows the clicked face and lines up with the truss that was clicked). */
    public boolean isChain() {
        return chain;
    }

    /** Local row (0 = bottom row of the structure) of the cell this state is. */
    public int rowOf(BlockState state) {
        return localY(state);
    }

    // ------------------------------------------------------------ placement
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        Direction face = ctx.getClickedFace();
        Direction look = ctx.getHorizontalDirection();
        BlockPos place = ctx.getClickedPos();
        boolean replacing = ctx.replacingClickedOnBlock();
        BlockPos clicked = replacing ? place : place.relative(face.getOpposite());
        BlockState clickedState = level.getBlockState(clicked);
        boolean onTruss = chain && !replacing
            && clickedState.getBlock() instanceof MultiPartBlock other && other.chain;

        Direction facing = chooseFacing(ctx, face, look);
        int k = turns(facing);
        BlockPos anchorOff = offset(anchorIndex(), k);

        // Candidate positions of the main cell. The first one that fits wins.
        List<BlockPos> mains = new ArrayList<>();
        if (chain && face == Direction.DOWN) {
            // hanging under a beam: the top row of the structure goes right under the clicked block
            mains.add(new BlockPos(place.getX() - anchorOff.getX(), place.getY() - (sy - 1) + my, place.getZ() - anchorOff.getZ()));
        } else if (onTruss && face.getAxis().isHorizontal()) {
            // continuing sideways: same bottom row as the truss that was clicked, whichever of its rows you hit
            MultiPartBlock clickedBlock = (MultiPartBlock) clickedState.getBlock();
            int baseY = clicked.getY() - clickedBlock.localY(clickedState);
            mains.add(new BlockPos(place.getX() - anchorOff.getX(), baseY + my, place.getZ() - anchorOff.getZ()));
        }
        mains.add(place.subtract(anchorOff));

        for (BlockPos main : mains) {
            int idxAtPlace = -1;
            for (int idx = 0; idx < count(); idx++) {
                if (main.offset(offset(idx, k)).equals(place)) {
                    idxAtPlace = idx;
                    break;
                }
            }
            if (idxAtPlace < 0 || isVirtual(idxAtPlace)) continue;
            boolean free = true;
            for (int idx = 0; idx < count() && free; idx++) {
                if (idx == idxAtPlace || isVirtual(idx)) continue;
                BlockPos p = main.offset(offset(idx, k));
                free = level.isInWorldBounds(p) && level.getBlockState(p).canBeReplaced();
            }
            if (free) {
                return this.defaultBlockState().setValue(FACING, facing).setValue(PART, idxAtPlace);
            }
        }
        return null;
    }

    public Direction placementFacing(BlockPlaceContext ctx){return chooseFacing(ctx,ctx.getClickedFace(),ctx.getHorizontalDirection());}
    private Direction chooseFacing(BlockPlaceContext ctx, Direction face, Direction look) {
        boolean side = chain && face.getAxis().isHorizontal();
        switch (placement) {
            case FRONT:
                return look.getOpposite();
            case EXTEND:
                // clicking the side/end of something: the truss runs straight out of that face
                return side ? face.getCounterClockWise() : look.getCounterClockWise();
            case CORNER3: {
                if (!side) return look;
                Direction t = face.getOpposite();                 // direction of the clicked truss
                Direction f1 = t.getCounterClockWise();           // flange facing t, other flange to t.cw
                Direction f2 = t.getOpposite();                   // flange facing t, other flange to t.ccw
                Direction o1 = t.getClockWise(), o2 = t.getCounterClockWise();
                int d1 = look.getStepX() * o1.getStepX() + look.getStepZ() * o1.getStepZ();
                int d2 = look.getStepX() * o2.getStepX() + look.getStepZ() * o2.getStepZ();
                boolean first = d1 >= d2;
                if (ctx.isSecondaryUseActive()) first = !first;
                return first ? f1 : f2;
            }
            default:
                return look;
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (level.isClientSide) return;
        int k = turns(state.getValue(FACING));
        int placed = state.getValue(PART);
        BlockPos main = pos.subtract(offset(placed, k));
        for (int idx = 0; idx < count(); idx++) {
            if (idx == placed || isVirtual(idx)) continue;
            level.setBlock(main.offset(offset(idx, k)), state.setValue(PART, idx), Block.UPDATE_ALL);
        }
    }

    /** Places the whole structure (used by the stage builder). */
    public void placeAll(Level level, BlockPos main, Direction facing) {
        BlockState base = this.defaultBlockState().setValue(FACING, facing);
        int k = turns(facing);
        for (int idx = 0; idx < count(); idx++) {
            if (isVirtual(idx)) continue;
            level.setBlock(main.offset(offset(idx, k)), base.setValue(PART, idx), Block.UPDATE_ALL);
        }
    }

    private static boolean swapping;

    /**
     * Replaces every cell of the structure that contains {@code pos} by the same cell of {@code other} (a twin of the same size, e.g. a
     * fence with or without its screen), without breaking or dropping anything.
     */
    public void swapStructure(Level level, BlockPos pos, BlockState state, MultiPartBlock other) {
        Direction facing = state.getValue(FACING);
        int k = turns(facing);
        BlockPos main = pos.subtract(offset(state.getValue(PART), k));
        swapping = true;
        try {
            for (int idx = 0; idx < count(); idx++) {
                BlockPos p = main.offset(offset(idx, k));
                BlockState s = level.getBlockState(p);
                if (s.is(this) && s.getValue(PART) == idx && s.getValue(FACING) == facing)
                    level.setBlock(p, other.defaultBlockState().setValue(FACING, facing).setValue(PART, idx), Block.UPDATE_ALL);
            }
        } finally {
            swapping = false;
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!swapping && !state.is(newState.getBlock()) && !level.isClientSide) {
            Direction facing = state.getValue(FACING);
            int k = turns(facing);
            int part = state.getValue(PART);
            if (part < count()) {
                BlockPos main = pos.subtract(offset(part, k));
                for (int idx = 0; idx < count(); idx++) {
                    BlockPos p = main.offset(offset(idx, k));
                    if (p.equals(pos)) continue;
                    BlockState s = level.getBlockState(p);
                    if (s.is(this) && s.getValue(PART) == idx && s.getValue(FACING) == facing) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int part = state.getValue(PART);
        if (part >= shapes.length) return Shapes.block();
        return partShape(part, turns(state.getValue(FACING)));
    }

    /** Theatrical light beams go through the lattice of trusses and tower masts (see LightCompat). */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (com.concertstage.compat.LightCompat.isLightRay(ctx)) {
            VoxelShape lattice = com.concertstage.compat.LightCompat.lattice(this, state);
            if (lattice != null) return lattice;
        }
        return super.getCollisionShape(state, level, pos, ctx);
    }

    /** Theatrical / Extra Lights fixtures hang on trusses and FOH girders like on Theatrical's own truss. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                                                     net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                                     net.minecraft.world.phys.BlockHitResult hit) {
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this).getPath();
        if (path.startsWith("truss_") || path.startsWith("regie_poutre") || path.startsWith("regie_lisse")) {
            net.minecraft.world.InteractionResult hung = com.concertstage.compat.TheatricalCompat.tryHang(level, pos, player, hand, hit);
            if (hung != null) return hung;
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    /** Lets any block (torches, carpets, slabs, blocks from other mods...) rest on the top of a part that reaches the top of its cell. */
    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        int part = state.getValue(PART);
        if (part >= shapes.length) return Shapes.block();
        VoxelShape s = partShape(part, turns(state.getValue(FACING)));
        int ly = part / (sx * sz);
        if (s.isEmpty() || (ly == 0 && sy > 1)) return s;
        return s.max(net.minecraft.core.Direction.Axis.Y) >= 0.999 ? Shapes.block() : s;
    }
}
