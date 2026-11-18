package com.concertstage.light.core;

/** Shared CPU/GLSL optical controls. Uses unused ring uniforms of single-optic fixtures;
 * lens-array and panel uniforms keep their existing meanings. */
public final class ProfessionalOptics {
    private ProfessionalOptics() {}

    public static void write(FixtureProfile p, float[] v, double seconds, float[] u, int at) {
        if (p == FixtureProfile.PROFILE_P1) {
            for (int i = 0; i < 4; i++) u[at + 8 + i] = v[Attr.BLADE_1.ordinal() + i];
            u[at + 12] = (v[Attr.FRAME_ROT.ordinal()] - 0.5f) * (float) Math.PI;
        }
        if (p.has(Attr.PRISM)) u[at + 13] = p.prismFacets;
        if (p == FixtureProfile.PHANTOM_S1) {
            u[at + 16] = Math.round(v[Attr.GOBO_2.ordinal()] * p.gobos);
            u[at + 17] = (float) ((v[Attr.GOBO_2_ROT.ordinal()] - .5f) * 2 * Math.toRadians(220) * seconds % (2*Math.PI));
            u[at + 18] = v[Attr.ANIMATION.ordinal()];
            u[at + 19] = (float) (seconds % (2*Math.PI));
            if (v[Attr.PRISM_2.ordinal()] > .5f) {
                u[at + 2] = 1f;
                // Prism 1: three facets, Prism 2: six facets; both inserted: eight combined images within the engine budget.
                u[at + 13] = v[Attr.PRISM.ordinal()] > .5f ? 8 : 6;
                u[at + 3] = (float) ((v[Attr.PRISM_2_ROT.ordinal()] - .5f) * 2 * Math.toRadians(160) * seconds % (2*Math.PI));
            }
        }
        if (p == FixtureProfile.LASERCORE_L1) {
            u[at] = 3f;
            u[at + 8] = Math.min(4, (int) (v[Attr.LASER_PATTERN.ordinal()] * 5));
            u[at + 9] = 0.1f + 0.8f * v[Attr.LASER_SIZE.ordinal()];
            u[at + 10] = (float) (seconds * (0.1 + 2.9 * v[Attr.LASER_SPEED.ordinal()]) % (2 * Math.PI));
        }
    }

    /** Second rotating gobo wheel and moving animation wheel, multiplied into the common beam mask. */
    public static float secondary(float[] u, float x, float y) {
        int slot = Math.round(u[16]);
        float c=(float)Math.cos(u[17]),s=(float)Math.sin(u[17]),rx=x*c-y*s,ry=x*s+y*c;
        float rad=(float)Math.hypot(rx,ry),theta=(float)Math.atan2(ry,rx),mask=1f;
        if(slot>0) {
            float d;
            switch(slot) {
                case 1 -> d=Math.abs((float)Math.sin(theta*3))*rad;
                case 2 -> d=Math.abs(rad-.55f);
                case 3 -> d=Math.min(Math.abs(rx),Math.abs(ry));
                case 4 -> d=Math.abs((float)Math.sin(rx*12))/12;
                case 5 -> d=Math.abs((float)Math.sin(theta*5))*rad;
                case 6 -> d=Math.abs(Math.max(Math.abs(rx),Math.abs(ry))-.5f);
                case 7 -> d=Math.abs((float)Math.sin((rx+ry)*10))/10;
                default -> d=Math.abs((float)Math.sin(rad*15))/15;
            }
            mask=1f-OpticalProfile.smoothstep(.04f,.09f,d);
        }
        if(u[18]>.001f) {
            float stripes=.5f+.5f*(float)Math.sin(y*18+u[19]*4);
            mask*=1f-u[18]+u[18]*OpticalProfile.smoothstep(.3f,.7f,stripes);
        }
        return mask;
    }

    /** Four shutters in the rotated framing module. Zero insertion leaves the beam open. */
    public static float framing(float[] u, float x, float y) {
        if (u[8]+u[9]+u[10]+u[11] <= 0f) return 1f;
        float c = (float) Math.cos(u[12]), s = (float) Math.sin(u[12]);
        float rx = x*c - y*s, ry = x*s + y*c;
        float d = Math.min(Math.min(1 - 2*u[8] - rx, 1 - 2*u[9] + rx),
                           Math.min(1 - 2*u[10] - ry, 1 - 2*u[11] + ry));
        return OpticalProfile.smoothstep(-0.005f, 0.005f, d);
    }

    /** Graphical scanner patterns: dot, fan, circle, cross and rotating square. */
    public static float laser(float[] u, float x, float y) {
        float c = (float) Math.cos(u[10]), s = (float) Math.sin(u[10]);
        float rx=x*c-y*s, ry=x*s+y*c, size=Math.max(.1f,u[9]), d;
        int pattern=Math.round(u[8]);
        if (pattern==0) d=(float)Math.hypot(rx,ry);
        else if (pattern==1) {
            d=1f;
            for(int i=0;i<5;i++) d=Math.min(d,(float)Math.hypot(rx-((i-2)*.4f*size),ry));
        } else if(pattern==2) d=Math.abs((float)Math.hypot(rx,ry)-size);
        else if(pattern==3) d=Math.min(Math.abs(rx),Math.abs(ry));
        else d=Math.abs(Math.max(Math.abs(rx),Math.abs(ry))-size*.7f);
        return 12f*(1f-OpticalProfile.smoothstep(.006f,.018f,d));
    }
}
