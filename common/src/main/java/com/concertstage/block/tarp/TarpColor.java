package com.concertstage.block.tarp;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

/** The five PVC tarp colours (few block states: a tarp is big, every extra value multiplies the states of three blocks). */
public enum TarpColor implements StringRepresentable {
    BLACK("black"), WHITE("white"), GREY("grey"), BLUE("blue"), RED("red");

    private final String name;

    TarpColor(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Colour given by a dye, or null for a dye that has no tarp colour. */
    public static TarpColor fromDye(DyeColor dye) {
        switch (dye) {
            case BLACK: return BLACK;
            case WHITE: return WHITE;
            case GRAY: case LIGHT_GRAY: return GREY;
            case BLUE: case LIGHT_BLUE: case CYAN: return BLUE;
            case RED: case ORANGE: case MAGENTA: case PINK: return RED;
            default: return null;
        }
    }
}
