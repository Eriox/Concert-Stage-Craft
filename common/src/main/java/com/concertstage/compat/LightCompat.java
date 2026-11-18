package com.concertstage.compat;

import com.concertstage.block.MotorTowerBlock;
import com.concertstage.block.MultiPartBlock;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Light beams of Theatrical / Theatrical: Extra Lights. Their fixtures find where a beam stops with a collision ray-trace that
 * carries their own {@code LightCollisionContext} (no compile dependency: recognised by its class name). For that ray only:
 * <ul>
 * <li>trusses and tower masts answer with their four chords instead of the full cell: the beam goes through the lattice and
 *     stops (lighting it, and Shimmer's coloured light lands there) only when it really hits a tube;</li>
 * <li>curtains and tarps, which have no collision for players, stop the beam: they cast a shadow.</li>
 * </ul>
 */
public final class LightCompat {
    private static final double LO = 0.25 / 16, HI = 2.75 / 16, LO2 = 13.25 / 16, HI2 = 15.75 / 16;
    private static final VoxelShape X = chords(Direction.Axis.X), Y = chords(Direction.Axis.Y), Z = chords(Direction.Axis.Z);
    private static final VoxelShape EDGES = Shapes.join(Shapes.join(X, Y, BooleanOp.OR), Z, BooleanOp.OR).optimize();

    private LightCompat() {}

    /** True for the ray-trace of a Theatrical light beam. */
    public static boolean isLightRay(CollisionContext ctx) {
        return ctx != null && LIGHT_RAY.get(ctx.getClass());
    }

    /** aurex.2: answer kept per context class (this test runs for every collision query of the world, and for every cell a beam crosses). */
    private static final ClassValue<Boolean> LIGHT_RAY = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) { return type.getName().endsWith(".LightCollisionContext"); }
    };
    /** aurex.2: lattice kind per block, worked out once (a registry look-up per beam step before). 0 none, 1 X/Z chords, 2 Y, 3 edges. */
    private static final java.util.Map<Block, Integer> KIND = new java.util.concurrent.ConcurrentHashMap<>();

    private static VoxelShape chords(Direction.Axis axis) {
        VoxelShape s = Shapes.empty();
        for (double[] a : new double[][] {{LO, HI}, {LO2, HI2}}) {
            for (double[] b : new double[][] {{LO, HI}, {LO2, HI2}}) {
                VoxelShape box;
                if (axis == Direction.Axis.X) box = Shapes.box(0, a[0], b[0], 1, a[1], b[1]);
                else if (axis == Direction.Axis.Y) box = Shapes.box(a[0], 0, b[0], a[1], 1, b[1]);
                else box = Shapes.box(a[0], b[0], 0, a[1], b[1], 1);
                s = Shapes.or(s, box);
            }
        }
        return s.optimize();
    }

    /** Shape a beam meets in a truss / tower cell, or null to keep the normal collision. */
    public static VoxelShape lattice(Block block, BlockState state) {
        int kind = KIND.computeIfAbsent(block, LightCompat::kind);
        if (kind == 0) return null;
        if (kind == 2) return Y;
        if (kind == 3) return EDGES;
        Direction facing = state.getValue(MultiPartBlock.FACING);
        return facing.getAxis() == Direction.Axis.X ? X : Z;
    }

    /**
     * 1.2.0 rev. 15: axis of the chords of a straight truss / mast cell (null for anything else, corners and junctions included),
     * so CSC's beam shadows can add the diagonal braces between the chords.
     */
    public static Direction.Axis latticeAxis(Block block, BlockState state) {
        int kind = KIND.computeIfAbsent(block, LightCompat::kind);
        if (kind == 2) return Direction.Axis.Y;
        if (kind != 1) return null;
        return state.getValue(MultiPartBlock.FACING).getAxis() == Direction.Axis.X ? Direction.Axis.X : Direction.Axis.Z;
    }

    /** Inner and outer limits of the chord tubes (1/16 of a block) for the braces. */
    public static final double CHORD_LO = LO, CHORD_HI = HI, CHORD_LO2 = LO2, CHORD_HI2 = HI2;

    private static int kind(Block block) {
        if (block instanceof MotorTowerBlock tower) return tower.isHoist() ? 0 : 2;
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (!path.startsWith("truss_")) return 0;
        if (path.contains("petit") || path.contains("x30d") || path.contains("ladder")) return 0;   // already slim shapes
        if (path.contains("corner") || path.contains("junction")) return 3;
        if (path.contains("v2") || path.contains("base")) return 2;
        return 1;
    }
}
