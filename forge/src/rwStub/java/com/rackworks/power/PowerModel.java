package com.rackworks.power;

/** Compile-only shape of RackWorks' PowerModel (the parts a node uses). */
public final class PowerModel {
    private PowerModel() {}
    public static final class Node {
        public boolean through = true;
        public Load load(Object tag, int watts, int phase, boolean threePhase) { return null; }
    }
    public static final class Load {}
    public static final class Result {
        public int inletMask;
        public double level = 1.0;
        public boolean supplied() { return inletMask != 0; }
    }
}