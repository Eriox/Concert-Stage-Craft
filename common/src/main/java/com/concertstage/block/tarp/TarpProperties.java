package com.concertstage.block.tarp;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Block state properties shared by the three blocks of a stage tarp (rail, top row, cloth). */
public final class TarpProperties {
    public static final EnumProperty<TarpColor> COLOR = EnumProperty.create("color", TarpColor.class);
    /**
     * How the top row is held. 0 = hung under a truss: welded hem with ball bungees up to the bottom chord.
     * 1..16 = tied to a stage rim beside it: the top of the tarp is {@code 2 * n} px above the bottom of the cell
     * (17 to 32 px reach up into the cell above, for a deck surface just above the rail row) and the bungees go into the stage.
     */
    public static final IntegerProperty TOP = IntegerProperty.create("top", 0, 16);
    /** Bottom hem of the column: 0 = more tarp below, 1 = free hem, 2 = hem tied to what is under it (deck, railing, ground...). */
    public static final IntegerProperty BOTTOM = IntegerProperty.create("bottom", 0, 2);
    /** The hem goes down {@code 2 * n} px into the cell below (a deck surface, a ledger... lower than the top of its cell). */
    public static final IntegerProperty REACH = IntegerProperty.create("reach", 0, 8);
    /** Side hem on the end of the tarp towards the negative / positive end of the axis. */
    public static final BooleanProperty END_NEG = BooleanProperty.create("end_neg");
    public static final BooleanProperty END_POS = BooleanProperty.create("end_pos");

    private TarpProperties() {}

    /** END property for the end of a tarp on side {@code side} (a horizontal direction along the tarp). */
    public static BooleanProperty end(Direction side) {
        return side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? END_POS : END_NEG;
    }
}
