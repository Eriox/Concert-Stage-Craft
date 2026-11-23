package com.rackworks.power;

/** Compile-only shape of RackWorks' NodeKey (the real class is in the RackWorks jar at runtime). */
public record NodeKey(long pos, int sub) {
    public static NodeKey of(long pos) { return new NodeKey(pos, 0); }
}