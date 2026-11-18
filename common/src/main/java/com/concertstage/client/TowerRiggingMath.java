package com.concertstage.client;

/** Pure chain path geometry; 16 modelling units equal one Minecraft block. */
public final class TowerRiggingMath {
    public static final double PITCH=.75, LINK_HALF_HEIGHT=.44, DRIVE_X=6.5, LOAD_X=9.5, CHAIN_Z=-1.5, DRIVE_EXIT=11.5, HEAD_RADIUS=1.5;
    private TowerRiggingMath() {}
    public static double head(int height) { return height*16.0-5; }
    public static double eye(int cells,double lift) { return 16+cells*16.0-1.8+lift*16; }
    public static double phase(double lift) { return lift*16%(2*PITCH); }
    public static double wheelDegrees(double lift,double radius) { return lift*16/radius*180/Math.PI; }
    public static double storedChain(double lift) { return 48+lift*16; }
    public static double visibleChain(int height,int cells,double lift) { return head(height)-DRIVE_EXIT+head(height)-eye(cells,lift)+Math.PI*HEAD_RADIUS; }
    public static int firstLink(int cell,double head,double phase) { return (int)Math.floor((head+phase-(cell+1)*16)/PITCH)+1; }
    public static int lastLink(int cell,double head,double phase) { return (int)Math.floor((head+phase-cell*16)/PITCH); }
}
