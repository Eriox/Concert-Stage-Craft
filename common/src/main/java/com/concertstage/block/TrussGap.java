package com.concertstage.block;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gap in pixels (0-5) between the cell boundary and the truss tubes, used so curtains and tarps touch the truss.
 * Since 1.2.9 the truss section is one full block (16 px, tubes 3 px at the cell edge): every truss reaches the cell
 * boundary, so {@link #TUBE_GAP} is 0. (1.2.5 - 1.2.8 had a 10 px section centred in the cell: TUBE_GAP was 3.)
 */
public final class TrussGap {
    /** (16 - section) / 2 with a 16 px section; must match FULL_SIZE (or SPACING + TUBE) in tools/shrink_trusses.py. */
    public static final int TUBE_GAP = 0;

    private TrussGap() {}

    /** Gap under the block {@code above} (a curtain rail hangs under it). */
    public static int gapUnder(BlockState above) {
        return gap(above, Direction.DOWN);
    }

    /** Gap over the block {@code below} (a tarp lies on top of it). */
    public static int gapOver(BlockState below) {
        return gap(below, Direction.UP);
    }

    /** Gap of the truss face that looks towards the cloth. {@code face} is DOWN (cloth under the truss) or UP (cloth on top). */
    public static int gap(BlockState state, Direction face) {
        if (!isTruss(state)) return 0;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.endsWith("_noir")) path = path.substring(0, path.length() - 5);   // black versions behave like the aluminium ones
        switch (path) {
            case "truss_h1_l1":
            case "truss_h1_l2":
            case "truss_h2_l2":
            case "truss_h2_l1":
            case "truss_h2_l2_x30d":
                return TUBE_GAP;                                   // horizontal beams: tubes inside the cell on both sides
            case "regie_poutre_treillis_l1":
            case "regie_poutre_treillis_l2":
            case "truss_ladder_l1":
            case "truss_ladder_l2":
            case "truss_h1_l2_petit":
            case "truss_h1_l1_petit":
            case "truss_x30d_l1":
            case "truss_x30d_l2":
                return 3;                                          // small beam: tubes 2 px wide, outer face 3 px from the cell boundary
            case "truss_corner_3":
            case "truss_corner_3_h2":
                return face == Direction.UP ? TUBE_GAP : 0;        // open face down (flange on the boundary), closed top
            default:
                return 0;                                          // columns, 6-way joints, base: flange on the boundary
        }
    }

    /** True for every truss block (and its invisible parts), not for the scaffold. */
    public static boolean isTruss(BlockState state) {
        return state.getBlock() instanceof MultiPartBlock
            && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("truss");
    }

    /**
     * Shift of a curtain hanging under {@code above}: on a truss the cloth goes on the line of the tubes, on the side
     * {@code toward} (the side the player is standing on); otherwise it stays centred.
     */
    public static int curtainShift(BlockState above, Direction toward) {
        return isTruss(above) ? StageProperties.shiftToward(toward) : 0;
    }
}
