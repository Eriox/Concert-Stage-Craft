package com.concertstage.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class StageProperties {
    /** Gap in pixels (0-5) between a hanging/lying cloth and the truss it is attached to. */
    public static final IntegerProperty DROP = IntegerProperty.create("drop", 0, 5);

    /**
     * Position of a curtain across its thickness: 0 = centred in the block, 1 = against the +Z side (curtain along X)
     * or the +X side (curtain along Z), 2 = against the opposite side. Side 1 / 2 puts the cloth on the line of the truss tubes.
     */
    public static final IntegerProperty SHIFT = IntegerProperty.create("shift", 0, 2);

    private StageProperties() {}

    /** Shift value that puts the cloth on the given side of the block. */
    public static int shiftToward(Direction side) {
        return side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 2;
    }

    /** Side of the block (in the direction normal to a curtain running along {@code curtainAxis}) selected by a shift value. */
    public static Direction sideOf(Direction.Axis curtainAxis, int shift) {
        Direction.Axis normal = curtainAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        return Direction.fromAxisAndDirection(normal,
            shift == 2 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
    }
}
