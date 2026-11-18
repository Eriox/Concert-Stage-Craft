// Concert Stage Craft 1.2.0 rev. 16 — CSC PROFESSIONAL LIGHTING RENDER ENGINE: the common optical mask.
//
// One function, csc_light, gives the light of a fixture at a point of a cross-section of its beam. It is used by the beam in the haze
// (concertstage_beam_volume) AND by the light on surfaces (concertstage_light_projector), with the same uniforms: the shape seen in the
// air and on the floor cannot differ. Java twin (tests, offline pictures): com.concertstage.light.core.OpticalProfile#light — keep both
// equal line for line.
//
// q: cross-section point divided by the half width hw of the beam there (|q| = 1: edge of the drawn cone), not rotated
// along: blocks from the lens, tanH: tangent of the half angle, rot: (cos, sin) of the gobo wheel, blur: offset of this tap (focus)
// Needs: uniform sampler2D PatternSampler (the gobo) declared before the import.
//
// OptKind:  x = family (0 spot / beam, 1 lens array, 2 panel), y = gobo in, z = 3-facet prism in, w = prism angle (rad)
// OptShape: spot x beamness, y edge softness, z skirt | lens array x lens radius, y skirt, z lens count, w rings | panel x, y half
//           extent of the face, z, w pitch of its cells (blocks)
// OptRing0..5 (lens array): x radius of the ring (blocks), y lens count, z angle of its first lens

uniform vec4 OptKind;
uniform vec4 OptShape;
uniform vec4 OptRing0;
uniform vec4 OptRing1;
uniform vec4 OptRing2;
uniform vec4 OptRing3;
uniform vec4 OptRing4;
uniform vec4 OptRing5;
// rev. 16b: colours of the cells (OpticalProfile#tints): x = on, y = group count; OptTint0..7 rgb = colour of each group (lens ring
// of a wash, column of the PULSESTRIKE face). Off: the light keeps the plain colour of the beam.
uniform vec4 OptTintInfo;
uniform vec4 OptTint0;
uniform vec4 OptTint1;
uniform vec4 OptTint2;
uniform vec4 OptTint3;
uniform vec4 OptTint4;
uniform vec4 OptTint5;
uniform vec4 OptTint6;
uniform vec4 OptTint7;

const float CSC_PI = 3.14159265;
const float CSC_PRISM_OFFSET = 0.55;
const float CSC_PRISM_SCALE = 0.45;

// rev. 16g: level of detail of the gobo picture, set by the shader before reading the mask (0 = full resolution). Far away a texel of
// the gobo is smaller than a pixel: read at full resolution its fine bars shimmered and broke up into noise (the gobos "badly
// drawn depending on the distance", 9 November 22 h 37 video); the pattern is now read at the size of the pixel (mipmaps).
float cscGoboLod = 0.0;

// New single-optic controls occupy ring uniforms unused by spot profiles.
float csc_secondary(vec2 q) {
    int slot=int(OptRing2.x+0.5);
    float c=cos(OptRing2.y),s=sin(OptRing2.y);
    vec2 r=vec2(q.x*c-q.y*s,q.x*s+q.y*c);
    float rad=length(r),theta=atan(r.y,r.x),mask=1.0;
    if(slot>0) {
        float d;
        if(slot==1) d=abs(sin(theta*3.0))*rad;
        else if(slot==2) d=abs(rad-0.55);
        else if(slot==3) d=min(abs(r.x),abs(r.y));
        else if(slot==4) d=abs(sin(r.x*12.0))/12.0;
        else if(slot==5) d=abs(sin(theta*5.0))*rad;
        else if(slot==6) d=abs(max(abs(r.x),abs(r.y))-0.5);
        else if(slot==7) d=abs(sin((r.x+r.y)*10.0))/10.0;
        else d=abs(sin(rad*15.0))/15.0;
        mask=1.0-smoothstep(0.04,0.09,d);
    }
    if(OptRing2.z>0.001) {
        float stripes=0.5+0.5*sin(q.y*18.0+OptRing2.w*4.0);
        mask*=1.0-OptRing2.z+OptRing2.z*smoothstep(0.3,0.7,stripes);
    }
    return mask;
}
float csc_framing(vec2 q) {
    if(dot(OptRing0,vec4(1.0)) <= 0.0) return 1.0;
    float c=cos(OptRing1.x), s=sin(OptRing1.x);
    vec2 r=vec2(q.x*c-q.y*s,q.x*s+q.y*c);
    float d=min(min(1.0-2.0*OptRing0.x-r.x,1.0-2.0*OptRing0.y+r.x),
                min(1.0-2.0*OptRing0.z-r.y,1.0-2.0*OptRing0.w+r.y));
    return smoothstep(-0.005,0.005,d);
}
float csc_laser(vec2 q) {
    float c=cos(OptRing0.z), s=sin(OptRing0.z);
    vec2 r=vec2(q.x*c-q.y*s,q.x*s+q.y*c);
    float size=max(0.1,OptRing0.y), d;
    int pattern=int(OptRing0.x+0.5);
    if(pattern==0) d=length(r);
    else if(pattern==1) {
        d=1.0;
        for(int i=0;i<5;i++) d=min(d,length(r-vec2(float(i-2)*0.4*size,0.0)));
    } else if(pattern==2) d=abs(length(r)-size);
    else if(pattern==3) d=min(abs(r.x),abs(r.y));
    else d=abs(max(abs(r.x),abs(r.y))-size*0.7);
    return 12.0*(1.0-smoothstep(0.006,0.018,d));
}

float csc_gobo(vec2 q, vec2 rot) {
    if (OptKind.y < 0.5) {
        return 1.0;
    }
    vec2 r = vec2(q.x * rot.x - q.y * rot.y, q.x * rot.y + q.y * rot.x);
    if (abs(r.x) > 1.0 || abs(r.y) > 1.0) {
        return 0.0;
    }
    return textureLod(PatternSampler, r * 0.5 + 0.5, cscGoboLod).r;
}

// one ring of lenses: angular mean of its gaussian spots (e^-z I0(z) ~ 1 / sqrt(1 + 2 pi z)) and the ripple of its lenses
float csc_ring(vec4 ring, float ra, float th, float sig2) {
    float cnt = ring.y;
    if (cnt <= 0.0) {
        return 0.0;
    }
    float R = ring.x;
    if (R < 1.0e-4) {
        return cnt * exp(-ra * ra / (2.0 * sig2));
    }
    float z = ra * R / sig2;
    float dr = ra - R;
    float g = exp(-dr * dr / (2.0 * sig2)) / sqrt(1.0 + 2.0 * CSC_PI * z);
    float arc = 2.0 * CSC_PI * R / cnt;
    float m = exp(-2.0 * CSC_PI * CSC_PI * sig2 / (arc * arc));
    g *= max(0.0, 1.0 + 2.0 * m * cos(cnt * (th - ring.z)));
    return cnt * g;
}

float csc_overlap(float a, float s, float b) {
    float lo = max(a - s, -b);
    float hi = min(a + s, b);
    return max(0.0, hi - lo) / (2.0 * s);
}

// the distribution of the family, before the gobo
float csc_base(vec2 q, float hw, float along, float tanH) {
    float rho = length(q);
    float cut = 1.0 - smoothstep(0.92, 1.08, rho);
    if (cut <= 0.0) {
        return 0.0;
    }
    if (OptKind.x < 0.5) {
        // VORTEX X1: spot (even disc, edge from the focus, gentle hot centre) to beam (the same flux in a concentrated core)
        float e = OptShape.y;
        float spot = 1.0 - smoothstep(1.0 - e, 1.0, rho);
        spot *= 1.0 + 0.18 * (1.0 - rho * rho);
        float core = exp(-rho * rho / (2.0 * 0.3 * 0.3));
        return mix(spot, 2.4 * core + 0.12 * spot, OptShape.x) * cut * csc_framing(q) * csc_secondary(q) * (OptShape.w > 0.0 ? OptShape.w : 1.0);
    }
    if (OptKind.x > 2.5) return csc_laser(q) * cut;
    if (OptKind.x < 1.5) {
        // LUMORA W7 / W19: the beams of every lens; each its own shaft near the face, an even wash further away
        float lensR = OptShape.x;
        float skirt = OptShape.y;
        float n = max(1.0, OptShape.z);
        vec2 a = q * hw;
        float ra = length(a);
        float th = atan(a.y, a.x);
        float s = lensR + max(along, 0.0) * tanH;
        float sig = max(0.5 * s, 1.0e-3);
        float sig2 = sig * sig;
        float sum = csc_ring(OptRing0, ra, th, sig2) + csc_ring(OptRing1, ra, th, sig2) + csc_ring(OptRing2, ra, th, sig2)
                  + csc_ring(OptRing3, ra, th, sig2) + csc_ring(OptRing4, ra, th, sig2) + csc_ring(OptRing5, ra, th, sig2);
        float dens = sum / (2.0 * CSC_PI * sig2);
        float lit = dens * CSC_PI * hw * hw / n;
        float halo = skirt * exp(-rho * rho * 1.6) * 1.4;
        // rev. 22 — the edge of a wash fades from 45 % of the cone to its border, as the field of a real wash (half the light at
        // about 70 % of the cone). Before, it stayed full up to 92 % and was cut at the cone: seen from the stage or the front rows
        // looking into wide washes (full zoom), the haze filled the cone evenly and its border was drawn as a hard white outline —
        // the "white bar" over the stage and the hard-edged fans of the 11 November picture and video. Same flux (x1.78).
        float field = (1.0 - smoothstep(0.45, 1.0, rho)) * 1.78;
        return (lit * (1.0 - skirt) + halo) * min(cut, field);
    }
    // PULSESTRIKE M: the rectangular face spread by the optic, with the grid of its cells near the face; a broad frontal flood far away
    float bx = OptShape.x;
    float by = OptShape.y;
    vec2 a = abs(q * hw);
    float s = max(max(along, 0.0) * tanH, 1.0e-3);
    float lit = csc_overlap(a.x, s, bx) * csc_overlap(a.y, s, by) * CSC_PI * hw * hw / max(4.0 * bx * by, 1.0e-5);
    float sig = 0.5 * s;
    if (OptShape.z > 1.0e-4) {
        lit *= 1.0 + 0.85 * exp(-2.0 * CSC_PI * CSC_PI * sig * sig / (OptShape.z * OptShape.z)) * cos(2.0 * CSC_PI * q.x * hw / OptShape.z);
    }
    if (OptShape.w > 1.0e-4) {
        lit *= 1.0 + 0.85 * exp(-2.0 * CSC_PI * CSC_PI * sig * sig / (OptShape.w * OptShape.w)) * cos(2.0 * CSC_PI * q.y * hw / OptShape.w);
    }
    // rev. 23 — away from the face the footprint becomes round and soft: the box kernel kept a rectangle with straight edges on the
    // floor at any distance (the "square" reflection of the PULSESTRIKE M / FLASHCORE FX, picture of 11 November, 22 h 19). Once the
    // spread is larger than the face, it is a soft disc of the same flux (fades from 30 % of the cone, x2.17)
    // 1.2.0 beta 2b — with a hot centre: the footprint of a strobe or a bar on the floor was a flat disc (an even pale patch, author's
    // picture of 13 November). Now a bell: brightest on the axis, falling all the way to the edge (x2.8 the light at half the
    // radius), same flux (x4.03); the flat plateau kept the whole patch at the ceiling of the light composite
    float round_ = exp(-rho * rho / (2.0 * 0.35 * 0.35)) * 4.03;
    // 1.2.0 beta 2b — near the face too the patch was a flat box at the ceiling of the light: brighter towards the axis
    // (x2.5 on the axis, x0.6 at 80 % of the cone; mean over the cone unchanged)
    float hot = 0.35 + 0.65 * exp(-rho * rho / (2.0 * 0.4 * 0.4)) / 0.306;
    lit = mix(max(0.0, lit) * hot, round_, smoothstep(0.5, 2.0, s / max(max(bx, by), 1.0e-3)));
    float halo = 0.10 * exp(-rho * rho * 1.2);
    return (lit * 0.9 + halo) * cut;
}

// the light at q: distribution x gobo (read with the focus offset of this tap), split into three smaller images by the prism
float csc_light(vec2 q, float hw, float along, float tanH, vec2 rot, vec2 blur) {
    if (OptKind.z < 0.5) {
        return csc_base(q, hw, along, tanH) * csc_gobo(q + blur, rot);
    }
    float sum = 0.0;
    int facets = OptRing1.y >= 3.0 ? min(8,int(OptRing1.y+0.5)) : 3;
    for (int k = 0; k < 8; k++) {
        if (k >= facets) break;
        float a = OptKind.w + float(k) * (2.0*CSC_PI/float(facets));
        vec2 qk = (q - CSC_PRISM_OFFSET * vec2(cos(a), sin(a))) / CSC_PRISM_SCALE;
        sum += csc_base(qk, hw * CSC_PRISM_SCALE, along, tanH) * csc_gobo(qk + blur, rot);
    }
    return sum / (float(facets) * CSC_PRISM_SCALE * CSC_PRISM_SCALE);
}

// focus blur of the projection: the gobo read over a small disc of 9 taps of radius rb (the distribution itself is not blurred)
float csc_light9(vec2 q, float hw, float along, float tanH, vec2 rot, float rb) {
    if (rb <= 0.0005) {
        return csc_light(q, hw, along, tanH, rot, vec2(0.0));
    }
    float rd = rb * 0.7071;
    if (OptKind.z < 0.5) {
        float base = csc_base(q, hw, along, tanH);
        if (base <= 0.0) {
            return 0.0;
        }
        float g = csc_gobo(q, rot) + csc_gobo(q + vec2(rb, 0.0), rot) + csc_gobo(q - vec2(rb, 0.0), rot)
                + csc_gobo(q + vec2(0.0, rb), rot) + csc_gobo(q - vec2(0.0, rb), rot) + csc_gobo(q + vec2(rd, rd), rot)
                + csc_gobo(q - vec2(rd, rd), rot) + csc_gobo(q + vec2(rd, -rd), rot) + csc_gobo(q + vec2(-rd, rd), rot);
        return base * g / 9.0;
    }
    return (csc_light(q, hw, along, tanH, rot, vec2(0.0)) + csc_light(q, hw, along, tanH, rot, vec2(rb, 0.0))
          + csc_light(q, hw, along, tanH, rot, vec2(-rb, 0.0)) + csc_light(q, hw, along, tanH, rot, vec2(0.0, rb))
          + csc_light(q, hw, along, tanH, rot, vec2(0.0, -rb)) + csc_light(q, hw, along, tanH, rot, vec2(rd, rd))
          + csc_light(q, hw, along, tanH, rot, vec2(-rd, -rd)) + csc_light(q, hw, along, tanH, rot, vec2(rd, -rd))
          + csc_light(q, hw, along, tanH, rot, vec2(-rd, rd))) / 9.0;
}

vec4 csc_tintOf(int i) {
    if (i == 0) return OptTint0;
    if (i == 1) return OptTint1;
    if (i == 2) return OptTint2;
    if (i == 3) return OptTint3;
    if (i == 4) return OptTint4;
    if (i == 5) return OptTint5;
    if (i == 6) return OptTint6;
    return OptTint7;
}

vec4 csc_ringOf(int i) {
    if (i == 0) return OptRing0;
    if (i == 1) return OptRing1;
    if (i == 2) return OptRing2;
    if (i == 3) return OptRing3;
    if (i == 4) return OptRing4;
    return OptRing5;
}

// rev. 16b: colour of the light at q (twin of OpticalProfile#tint): the colours of the cells weighted by what each group brings there
vec3 csc_tint(vec2 q, float hw, float along, float tanH) {
    if (OptTintInfo.x < 0.5) {
        return vec3(1.0);
    }
    int n = int(OptTintInfo.y + 0.5);
    vec3 acc = vec3(0.0);
    float ws = 0.0;
    if (OptKind.x > 0.5 && OptKind.x < 1.5) {
        vec2 a = q * hw;
        float ra = length(a);
        float th = atan(a.y, a.x);
        float s = OptShape.x + max(along, 0.0) * tanH;
        float sig = max(0.5 * s, 1.0e-3);
        float sig2 = sig * sig;
        for (int i = 0; i < 6; i++) {
            if (i >= n) {
                break;
            }
            float w = csc_ring(csc_ringOf(i), ra, th, sig2) + 1.0e-6;
            acc += w * csc_tintOf(i).rgb;
            ws += w;
        }
    } else {
        float bx = OptShape.x;
        float x = q.x * hw;
        float s = max(max(along, 0.0) * tanH, 1.0e-3);
        float cw = 2.0 * bx / float(max(1, n));
        // 1.2.0 beta 2b — far from the face the light of the cells mixes into one colour: the floor under a pixel bar was one even
        // patch whatever its cells showed. Part of the footprint now keeps the image of the bar (its cells side by side across it)
        float far = 0.7 * smoothstep(0.5, 2.0, s / max(bx, 1.0e-3));
        float xi = clamp(q.x, -1.0, 1.0) * bx;
        for (int c = 0; c < 8; c++) {
            if (c >= n) {
                break;
            }
            float xc = -bx + (float(c) + 0.5) * cw;
            float w = mix(csc_overlap(abs(x - xc), s, 0.5 * cw), csc_overlap(abs(xi - xc), 0.5 * cw, 0.5 * cw), far) + 1.0e-6;
            acc += w * csc_tintOf(c).rgb;
            ws += w;
        }
    }
    return acc / ws;
}
