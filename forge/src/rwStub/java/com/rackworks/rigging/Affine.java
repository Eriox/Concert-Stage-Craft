package com.rackworks.rigging;

/** Compile-only shape of RackWorks' Affine (a rigid move of a model). */
public final class Affine {
    public static final Affine IDENTITY = new Affine();
    private Affine() {}
    public static Affine translation(double x, double y, double z) { return IDENTITY; }
    public static Affine rotX(double degrees) { return IDENTITY; }
    public static Affine rotY(double degrees) { return IDENTITY; }
    public static Affine rotZ(double degrees) { return IDENTITY; }
    public Affine mul(Affine other) { return this; }
}