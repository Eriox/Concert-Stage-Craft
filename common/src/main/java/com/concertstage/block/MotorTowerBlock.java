package com.concertstage.block;

import com.concertstage.StageContent;
import com.concertstage.angle.AngleMath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Motorised truss tower: a 1 x height x 1 multi-block (10, 14, 18 or 22 blocks) with a truss joint that slides along the mast.
 * Every cell has a block entity (the renderer draws each cell's slice of the tower, so the tower never disappears when only
 * part of it is on screen); the bottom cell is the controller that holds the lift state. The block itself is invisible: the
 * tower is drawn from {@code assets/concertstage/towers/*.json} by TowerRenderer.
 *
 * The joint rests in cell 1 (cell 0 holds the motor box) and moves in whole blocks: the trusses attached to it are re-placed
 * as ordinary blocks every time the lift stops on a block boundary.
 */
public class MotorTowerBlock extends MultiPartBlock implements EntityBlock {
    /** The four joints a tower can carry. {@code cells} = height of the joint in blocks, {@code sixWay} = flanges on the four sides. */
    public enum Carriage {
        CORNER3("corner3", 1, false),
        CORNER3_H2("corner3_h2", 2, false),
        CORNER6("corner6", 1, true),
        CORNER6_H2("corner6_h2", 2, true);

        public final String id;
        public final int cells;
        public final boolean sixWay;

        Carriage(String id, int cells, boolean sixWay) {
            this.id = id;
            this.cells = cells;
            this.sixWay = sixWay;
        }
    }

    /** The mast is one block wide (16 px) like every truss since 1.2.9: outline and collision are the whole cell. */
    private static final VoxelShape MAST = Shapes.block();
    private static final VoxelShape FOOT = Shapes.block();

    private final int height;
    private final Carriage carriage;
    private final boolean black;

    public MotorTowerBlock(Properties properties, int height, Carriage carriage) {
        this(properties, height, carriage, false);
    }

    /** {@code black}: same tower drawn with the black texture (textures/block/motor_tower_noir.png). */
    public MotorTowerBlock(Properties properties, int height, Carriage carriage, boolean black) {
        this(properties, height, carriage, black, (lx, ly, lz) -> ly == 0 ? FOOT : MAST);
    }

    /** For subclasses (the chain hoist) that have their own outline. */
    protected MotorTowerBlock(Properties properties, int height, Carriage carriage, boolean black, ShapeProvider shape) {
        super(properties, 1, height, 1, 0, 0, 0, Placement.LOOK, shape);
        this.height = height;
        this.carriage = carriage;
        this.black = black;
    }

    /** True for the chain hoist: no mast, the model is a normal block model, and it carries small trusses hanging under it. */
    public boolean isHoist() {
        return false;
    }

    /** Lift (in 1/20 block) of a block entity that has just been created. */
    public int initialUnits() {
        return 0;
    }

    /**
     * The blocks where the search for what this motor lifts starts, when it is {@code liftBlocks} blocks above its lowest position.
     * Tower: the neighbours of the joint cells on the sides that carry trusses.
     */
    public List<BlockPos> attachCells(BlockPos main, BlockState state, int liftBlocks) {
        List<BlockPos> out = new ArrayList<>();
        for (int c = 0; c < carriage.cells; c++) {
            BlockPos cell = main.above(1 + liftBlocks + c);
            for (Direction d : attachSides(state)) out.add(cell.relative(d));
        }
        return out;
    }

    /**
     * Same as {@link #attachCells(BlockPos, BlockState, int)} for a tower that may be turned by the angle tool: the trusses on the flanges of a
     * turned joint are angled pieces, so the search starts at the cells just outside each flange, along the turned directions.
     */
    public List<BlockPos> attachCells(TowerBlockEntity be, int liftBlocks) {
        float angle = be.angle();
        if (angle == 0.0F) return attachCells(be.mainPos(), be.getBlockState(), liftBlocks);
        List<BlockPos> out = new ArrayList<>();
        BlockPos main = be.mainPos();
        double cx = main.getX() + 0.5, cz = main.getZ() + 0.5;
        for (int c = 0; c < carriage.cells; c++) {
            int y = main.getY() + 1 + liftBlocks + c;
            for (Direction d : attachSides(be.getBlockState())) {
                double[] u = AngleMath.dir(AngleMath.norm360(AngleMath.compassOfStep(d.getStepX(), d.getStepZ()) + angle));
                for (double r : new double[] {0.6, 1.0, 1.5}) {
                    out.add(new BlockPos(AngleMath.cellOf(cx + r * u[0]), y, AngleMath.cellOf(cz + r * u[1])));
                }
            }
        }
        return out;
    }

    public boolean isBlack() {
        return black;
    }

    public int towerHeight() {
        return height;
    }

    public Carriage carriage() {
        return carriage;
    }

    /** Highest lift in whole blocks: the joint (and what hangs on it) stays under the head of the tower. */
    public int maxLiftBlocks() {
        return height - 2 - carriage.cells;
    }

    /** World directions of the flanges of the joint that carry trusses (the model is authored with flanges at +x and +z). */
    public List<Direction> attachSides(BlockState state) {
        List<Direction> sides = new ArrayList<>(4);
        if (carriage.sixWay) {
            sides.add(Direction.NORTH);
            sides.add(Direction.EAST);
            sides.add(Direction.SOUTH);
            sides.add(Direction.WEST);
            return sides;
        }
        int turns = (state.getValue(FACING).get2DDataValue() + 2) % 4;
        Direction east = Direction.EAST;
        Direction south = Direction.SOUTH;
        for (int i = 0; i < turns; i++) {
            east = east.getClockWise();
            south = south.getClockWise();
        }
        sides.add(east);
        sides.add(south);
        return sides;
    }

    /** Every cell of the tower whose main (bottom) cell is {@code main}. */
    public List<BlockPos> cells(BlockPos main) {
        List<BlockPos> list = new ArrayList<>(height);
        for (int i = 0; i < height; i++) list.add(main.above(i));
        return list;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TowerBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != StageContent.TOWER_ENTITY) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<TowerBlockEntity>) TowerBlockEntity::serverTick;
    }
}
