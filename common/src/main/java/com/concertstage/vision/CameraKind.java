package com.concertstage.vision;

/**
 * The four CSC cameras (1.3.0) and their mechanical limits (degrees): pan, tilt, crane boom, head pan. Speeds in degrees per second
 * (motorised heads move smoothly, eased at both ends).
 */
public enum CameraKind {
    /** Motorised PTZ: base, fork, spherical head, optical block that moves with the zoom. */
    PTZ("cam_ptz_p1", 170, -30, 90, 0, 0, 0, 60),
    /** Shoulder / studio camera on a fluid-head tripod. */
    BROADCAST("broadcast_b1", 180, -45, 45, 0, 0, 0, 45),
    /** Camera crane: base turn, 5.8 m boom (4.5 m in front), levelled 2-axis motorised head. */
    CRANE("crane_c1", 180, -60, 60, -25, 45, 90, 25),
    /** Compact remote camera clamped on a Ø 119 mm truss chord. */
    REMOTE("remote_r1", 175, -60, 60, 0, 0, 0, 70);

    public final String model;
    public final float panMax, tiltMin, tiltMax, boomMin, boomMax, headPanMax, speed;

    CameraKind(String model, float panMax, float tiltMin, float tiltMax, float boomMin, float boomMax, float headPanMax, float speed) {
        this.model = model; this.panMax = panMax; this.tiltMin = tiltMin; this.tiltMax = tiltMax;
        this.boomMin = boomMin; this.boomMax = boomMax; this.headPanMax = headPanMax; this.speed = speed;
    }

    /** Clamps axis {@code a} (0 pan, 1 tilt, 2 zoom 0..1, 3 boom, 4 head pan) to the mechanics. */
    public float clamp(int a, float v) {
        return switch (a) {
            case 0 -> Math.max(-panMax, Math.min(panMax, v));
            case 1 -> Math.max(tiltMin, Math.min(tiltMax, v));
            case 2 -> Math.max(0f, Math.min(1f, v));
            case 3 -> Math.max(boomMin, Math.min(boomMax, v));
            case 4 -> Math.max(-headPanMax, Math.min(headPanMax, v));
            default -> v;
        };
    }
}
