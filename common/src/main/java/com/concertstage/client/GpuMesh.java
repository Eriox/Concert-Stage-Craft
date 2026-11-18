package com.concertstage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Performance (1.1.0): geometry of the mod kept in the graphics card. The big pieces drawn by block entity renderers (roof frames,
 * circular trusses, angled trusses, mounted stage pieces, tower masts, a structure travelling with a lift) used to send every vertex to
 * the graphics card again at every frame — tens of thousands of quads per frame for a festival stage, the main cost on the processor.
 * Now a piece is built once into a vertex buffer (rebuilt only when it changes: shape, level of detail, light) and every frame only a
 * draw call is made, like the chunks of the world.
 *
 * <p>The vertices use the block format and the shader of the cut-out terrain; the shading of entity-lit meshes (the two fixed lights of
 * the world, 40 % ambient) is baked in the colour, so the pieces look as before. Draws are queued while the block entities render and
 * all sent after them ({@link #flush()}, called by the loaders), grouped by texture. With a shader pack (Iris / Oculus), when turned
 * off ({@code gpu_meshes=false} in {@code config/concertstage-client.properties}) or after an error, everything is drawn the old way.
 */
public final class GpuMesh {
    /** The world's two diffuse lights (as in Minecraft's level lighting), in world space. */
    private static final float L0X, L0Y, L0Z, L1X, L1Y, L1Z;
    static {
        float a = (float) Math.sqrt(0.2 * 0.2 + 1 + 0.7 * 0.7);
        L0X = 0.2f / a; L0Y = 1f / a; L0Z = -0.7f / a;
        L1X = -0.2f / a; L1Y = 1f / a; L1Z = 0.7f / a;
    }

    private record Bucket(ResourceLocation texture, boolean cull) {}

    private record Kind(ResourceLocation texture, boolean cull, boolean entityShade) {}

    private record Queued(GpuMesh mesh, Matrix4f pose) {}
    private static final Matrix4f MV = new Matrix4f(), SIG_REL = new Matrix4f();
    private static final org.joml.Vector3f SIG_T = new org.joml.Vector3f();

    /** Render types the meshes can take the place of (anything else is drawn the old way). */
    private static final Map<RenderType, Kind> KNOWN = new IdentityHashMap<>();
    private static final List<GpuMesh> ALIVE = new ArrayList<>();
    private static final List<Queued> QUEUE = new ArrayList<>();
    /**
     * Rev. 14b: meshes drawn after CSC's stage lights (the light fixtures' own bodies): the lights of a fixture and of its neighbours
     * are around them, and lighting the bodies with them turned every fixture near the camera into the colour of the show.
     */
    private static final List<Queued> LATE = new ArrayList<>();
    /**
     * 1.2.0 rev. 16c: the structure meshes sent this frame (not the LATE fixture bodies), for the shadow maps of the beams: towers,
     * angled trusses, ring trusses, roofs, stage pieces cast the shadow of the geometry really drawn, not of their outline box.
     */
    private static final List<Queued> DRAWN = new ArrayList<>();
    private static BufferBuilder shared;
    private static long frame;
    private static boolean failed;
    private static Boolean configured;
    private static boolean shaderPack;
    private static long shaderCheck = Long.MIN_VALUE;
    /** Between the start of the world rendering and its end (GUI, item icons: never queued, drawn the old way). */
    private static boolean inWorld;

    private final Map<Bucket, VertexBuffer> parts = new LinkedHashMap<>();
    private long lastUsed;
    private boolean closed;

    private GpuMesh() {}

    // ------------------------------------------------------------ state
    static boolean enabledForStats() {
        return !failed && configured != Boolean.FALSE && !shaderPack;
    }

    static String offReason() {
        return failed ? "error, see the log" : configured == Boolean.FALSE ? "gpu_meshes=false" : shaderPack ? "shader pack" : "?";
    }

    /** Whether meshes are used this frame (otherwise the renderers draw the old way). */
    public static boolean enabled() {
        if (failed || !inWorld) return false;
        if (configured == null) configured = readSetting();
        if (!configured) return false;
        if (frame - shaderCheck > 100 || shaderCheck == Long.MIN_VALUE) {
            shaderCheck = frame;
            shaderPack = shaderPackInUse();
        }
        return !shaderPack;
    }

    private static boolean readSetting() {
        try {
            java.nio.file.Path f = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("concertstage-client.properties");
            if (!java.nio.file.Files.isRegularFile(f)) return true;
            java.util.Properties p = new java.util.Properties();
            try (var r = java.nio.file.Files.newBufferedReader(f)) { p.load(r); }
            return !"false".equalsIgnoreCase(p.getProperty("gpu_meshes", "true").trim());
        } catch (java.io.IOException | RuntimeException e) {
            return true;
        }
    }

    /** Iris / Oculus with a shader pack: their pipeline does not know these draws, the old way is used. */
    public static boolean shaderPackInUse() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            return Boolean.TRUE.equals(inst.getClass().getMethod("isShaderPackInUse").invoke(inst));
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable e) {
            return true;                                                     // unknown version: be safe
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    /** A mesh that may still be drawn (built, not freed). */
    public static boolean usable(GpuMesh m) {
        return m != null && !m.closed;
    }

    private static void known() {
        if (!KNOWN.isEmpty()) return;
        ResourceLocation blocks = TextureAtlas.LOCATION_BLOCKS;
        KNOWN.put(Sheets.cutoutBlockSheet(), new Kind(blocks, true, true));
        KNOWN.put(Sheets.solidBlockSheet(), new Kind(blocks, true, true));
        KNOWN.put(RenderType.entityCutout(blocks), new Kind(blocks, true, true));
        KNOWN.put(RenderType.entitySolid(blocks), new Kind(blocks, true, true));
        KNOWN.put(RenderType.entityCutoutNoCull(blocks), new Kind(blocks, false, true));
        KNOWN.put(RenderType.cutout(), new Kind(blocks, true, false));
        KNOWN.put(RenderType.cutoutMipped(), new Kind(blocks, true, false));
        KNOWN.put(RenderType.solid(), new Kind(blocks, true, false));
    }

    /** {@code RenderType.entityCutoutNoCull(texture)}, also known to the meshes. */
    public static RenderType noCull(ResourceLocation texture) {
        RenderType rt = RenderType.entityCutoutNoCull(texture);
        known();
        KNOWN.putIfAbsent(rt, new Kind(texture, false, true));
        return rt;
    }

    /**
     * Meshes by key (least recently used freed beyond {@code max}). A piece that cannot be cached is remembered as such (null) and
     * drawn the old way, without trying again every frame.
     */
    public static final class Cache {
        private final int max;
        private final LinkedHashMap<String, GpuMesh> map;

        public Cache(int max) {
            this.max = max;
            this.map = new LinkedHashMap<>(64, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, GpuMesh> e) {
                    if (size() <= Cache.this.max) return false;
                    if (e.getValue() != null) e.getValue().free();
                    return true;
                }
            };
        }

        /** The mesh for {@code key}, built with {@code fill} when missing or freed; null: draw the old way. */
        public GpuMesh get(String key, java.util.function.Consumer<Builder> fill) {
            GpuMesh m = map.get(key);
            if (usable(m)) return m;
            if (m == null && map.containsKey(key)) return null;
            Builder b = builder();
            fill.accept(b);
            m = b.build();
            map.put(key, m);
            return m;
        }

        public void clear() {
            for (GpuMesh m : map.values()) if (m != null) m.free();
            map.clear();
        }
    }

    // ------------------------------------------------------------ building
    public static Builder builder() {
        known();
        return new Builder();
    }

    /** Collects the vertices of a piece (positions relative to the piece, as given to the renderer's pose). */
    public static final class Builder {
        private final Map<Bucket, int[]> data = new LinkedHashMap<>();
        private final Map<Bucket, Integer> sizes = new HashMap<>();
        private boolean failed;

        /** Something asked for a render type the meshes cannot reproduce: the piece must be drawn the old way. */
        public boolean failed() {
            return failed;
        }

        /** Vertices for a texture; {@code entityShade}: lit like an entity (the shading is added), else the colour is final. */
        public VertexConsumer consumer(ResourceLocation texture, boolean cull, boolean entityShade) {
            return new Capture(new Bucket(texture, cull), entityShade);
        }

        /** Buffers for code that draws through a MultiBufferSource (block models, angled trusses). */
        public MultiBufferSource source() {
            return rt -> {
                Kind k = KNOWN.get(rt);
                if (k == null) {
                    failed = true;
                    return NOWHERE;
                }
                return consumer(k.texture, k.cull, k.entityShade);
            };
        }

        /** The mesh, or null when the piece cannot be cached (then draw it the old way). */
        public GpuMesh build() {
            if (failed) return null;
            long t0 = System.nanoTime();
            RenderStats.meshesBuilt++;
            GpuMesh mesh = new GpuMesh();
            try {
                for (Map.Entry<Bucket, int[]> e : data.entrySet()) {
                    int n = sizes.getOrDefault(e.getKey(), 0) / 8;
                    if (n < 4) continue;
                    mesh.parts.put(e.getKey(), upload(e.getValue(), n - n % 4));
                }
            } catch (Throwable t) {
                mesh.free();
                GpuMesh.failed = true;
                com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: GPU meshes turned off", t);
                return null;
            }
            mesh.lastUsed = frame;
            ALIVE.add(mesh);
            RenderStats.buildNanos += System.nanoTime() - t0;
            return mesh;
        }

        private void put(Bucket b, int x, int y, int z, int u, int v, int color, int light, int normal) {
            int[] a = data.get(b);
            int size = sizes.getOrDefault(b, 0);
            if (a == null) { a = new int[8 * 256]; data.put(b, a); }
            if (size + 8 > a.length) { a = java.util.Arrays.copyOf(a, a.length * 2); data.put(b, a); }
            a[size] = x; a[size + 1] = y; a[size + 2] = z; a[size + 3] = u; a[size + 4] = v; a[size + 5] = color; a[size + 6] = light; a[size + 7] = normal;
            sizes.put(b, size + 8);
        }

        private final class Capture implements VertexConsumer {
            private final Bucket bucket;
            private final boolean shade;
            private float x, y, z, u, v, nx, ny = 1, nz;
            private int r = 255, g = 255, b = 255, a = 255, light;
            private boolean fixed;
            private int fr, fg, fb, fa;

            Capture(Bucket bucket, boolean shade) {
                this.bucket = bucket;
                this.shade = shade;
            }

            @Override public VertexConsumer vertex(double px, double py, double pz) { x = (float) px; y = (float) py; z = (float) pz; return this; }
            @Override public VertexConsumer color(int cr, int cg, int cb, int ca) { r = cr; g = cg; b = cb; a = ca; return this; }
            @Override public VertexConsumer uv(float pu, float pv) { u = pu; v = pv; return this; }
            @Override public VertexConsumer overlayCoords(int pu, int pv) { return this; }
            @Override public VertexConsumer uv2(int pu, int pv) { light = (pu & 0xFFFF) | (pv & 0xFFFF) << 16; return this; }
            @Override public VertexConsumer normal(float px, float py, float pz) { nx = px; ny = py; nz = pz; return this; }
            @Override public void defaultColor(int cr, int cg, int cb, int ca) { fixed = true; fr = cr; fg = cg; fb = cb; fa = ca; }
            @Override public void unsetDefaultColor() { fixed = false; }

            @Override
            public void endVertex() {
                int cr = fixed ? fr : r, cg = fixed ? fg : g, cb = fixed ? fb : b, ca = fixed ? fa : a;
                if (shade) {
                    float l = Math.min(1f, (Math.max(0f, L0X * nx + L0Y * ny + L0Z * nz) + Math.max(0f, L1X * nx + L1Y * ny + L1Z * nz)) * 0.6f + 0.4f);
                    cr = Math.round(cr * l); cg = Math.round(cg * l); cb = Math.round(cb * l);
                }
                int color = (cr & 255) | (cg & 255) << 8 | (cb & 255) << 16 | (ca & 255) << 24;
                int normal = (packNormal(nx) & 255) | (packNormal(ny) & 255) << 8 | (packNormal(nz) & 255) << 16;
                put(bucket, Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), Float.floatToRawIntBits(z), Float.floatToRawIntBits(u),
                    Float.floatToRawIntBits(v), color, light, normal);
                r = g = b = a = 255;
            }
        }
    }

    private static int packNormal(float f) {
        return (byte) ((int) (Math.max(-1f, Math.min(1f, f)) * 127.0f));
    }

    private static final VertexConsumer NOWHERE = new VertexConsumer() {
        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    };

    private static VertexBuffer upload(int[] d, int vertices) {
        if (shared == null) shared = new BufferBuilder(1 << 18);
        BufferBuilder bb = shared;
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        for (int i = 0; i < vertices; i++) {
            int o = i * 8;
            int color = d[o + 5], light = d[o + 6], n = d[o + 7];
            bb.vertex(Float.intBitsToFloat(d[o]), Float.intBitsToFloat(d[o + 1]), Float.intBitsToFloat(d[o + 2]),
                (color & 255) / 255f, (color >>> 8 & 255) / 255f, (color >>> 16 & 255) / 255f, (color >>> 24 & 255) / 255f,
                Float.intBitsToFloat(d[o + 3]), Float.intBitsToFloat(d[o + 4]), 0, light,
                (byte) (n & 255) / 127f, (byte) (n >>> 8 & 255) / 127f, (byte) (n >>> 16 & 255) / 127f);
        }
        BufferBuilder.RenderedBuffer rendered = bb.end();
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(rendered);
        VertexBuffer.unbind();
        return vb;
    }

    // ------------------------------------------------------------ drawing
    /** Queues the mesh at the renderer's current pose (sent by {@link #flush()} after the block entities). */
    public void draw(PoseStack pose) {
        if (closed || parts.isEmpty()) return;
        lastUsed = frame;
        QUEUE.add(new Queued(this, new Matrix4f(pose.last().pose())));
    }

    /** Like {@link #draw}, but sent after CSC's stage lights, so they do not light it (the bodies of the light fixtures). */
    public void drawLate(PoseStack pose) {
        if (closed || parts.isEmpty()) return;
        lastUsed = frame;
        LATE.add(new Queued(this, new Matrix4f(pose.last().pose())));
    }

    /**
     * 1.3.0 rev. 1: a LiveVision camera image is drawn in the client tick, between two frames, where {@link #enabled()} said no
     * (outside the world rendering): every renderer then sent all its triangles the old way, each image — 60 ms per image in the
     * author's world (22 speakers 36 ms, 158 tower pieces 9 ms, 62 fixture bodies 17 ms, measured 12 November). The image now uses
     * the same cached meshes as the main view. Returns the previous state, given back to {@link #endFeed}.
     */
    public static boolean beginFeed() {
        boolean was = inWorld;
        inWorld = true;
        return was;
    }

    /** End of a camera image: back to the state before it (what it queued and did not send is dropped). */
    public static void endFeed(boolean was) {
        if (!was) {
            QUEUE.clear();
            LATE.clear();
            if (sheets != null && sheets.building()) sheets.end().release();
        }
        inWorld = was;
    }

    /** 1.3.0: inside a LiveVision camera image: the meshes queued by the block entities, nothing of the stage lights. */
    public static void flushFeed() {
        flushSheets();
        List<Queued> q = new ArrayList<>(QUEUE);
        QUEUE.clear();
        flushMeshes(q);
        flushMeshes(LATE);
    }

    // rev. 3b: the cameras' own depth copies (LightTargets)
    private static com.mojang.blaze3d.pipeline.TextureTarget fOpaqueDepth, fFullDepth;
    private static boolean fBeamsDeferred;

    public static void swapFeed() {
        var t = opaqueDepth; opaqueDepth = fOpaqueDepth; fOpaqueDepth = t;
        t = fullDepth; fullDepth = fFullDepth; fFullDepth = t;
        boolean b = beamsDeferred; beamsDeferred = fBeamsDeferred; fBeamsDeferred = b;
    }

    /**
     * Rev. 3b: inside a LiveVision camera image drawn with the full stage lights (LightTargets#beginFeed): the queued meshes, the light
     * projected on them, the fixture bodies. The beams follow in {@link #feedBeams()}, after the translucent terrain.
     */
    public static void flushFeedLit() {
        flushSheets();
        List<Queued> q = new ArrayList<>(QUEUE);
        QUEUE.clear();
        flushMeshes(q);
        com.concertstage.client.light.VolumeLights.flush();
        com.concertstage.client.light.VolumeLights.resetBlend();
        flushMeshes(LATE);
    }

    /** Rev. 3b: the beams in the haze, the light of the fixtures and the bloom of a camera image (see {@link #flushFeedLit()}). */
    public static void feedBeams() {
        drawBeams();
    }

    /** Sends every queued mesh, grouped by texture. Called after the block entities (and again, harmlessly, later in the frame). */
    public static void flush() {
        flushSheets();
        flushMeshes(QUEUE);
        long t0 = System.nanoTime();
        com.concertstage.client.light.VolumeLights.flush();        // 1.2.0 rev. 9: coloured stage lights on what is drawn so far
        com.concertstage.client.light.VolumeLights.resetBlend();   // rev. 15b: see resetBlend (grey screen)
        RenderStats.flushNanos += System.nanoTime() - t0;
        flushMeshes(LATE);                                         // rev. 14b: the fixture bodies, not lit by the stage lights
        com.concertstage.client.vision.LedWalls.drawMain();       // 1.3.0: LED walls (once per frame), before the beams
        t0 = System.nanoTime();
        deferBeams();
        RenderStats.flushNanos += System.nanoTime() - t0;
    }

    /*
     * Rev. 20 — the beams are drawn at the very end of the world (after the clouds, the weather and the translucent blocks), still
     * hidden by the opaque scene only. Seen in game (11 November, CSC_Visual test): the game draws the clouds after the block
     * entities; the beams, which do not write depth, were drawn before them, so every cloud behind a beam was painted over it — dark
     * blocks cutting the beams of a night show. Now the depth of the opaque scene (fixture bodies included) is kept when the beams are
     * queued; at the end of the frame it is put back for the time of the beams (the veil, the beams in the haze, the grouped light),
     * then the full depth (clouds, water, glass) is restored for what the game draws next (hand, outline).
     */
    private static com.mojang.blaze3d.pipeline.TextureTarget opaqueDepth, fullDepth;
    private static boolean beamsDeferred, deferFailed;
    private static final org.joml.Matrix4f deferredModelView = new org.joml.Matrix4f(), deferredProjection = new org.joml.Matrix4f();
    private static com.mojang.blaze3d.vertex.VertexSorting deferredSorting;

    private static void deferBeams() {
        if (beamsDeferred) return;                    // the opaque depth of this frame is kept: later light joins the same pass
        if (!com.concertstage.client.light.LightBatch.pending() && !com.concertstage.client.light.VolumeLights.beamsPending()) return;
        if (!deferFailed) {
            try {
                var main = com.concertstage.client.light.LightTargets.main();
                opaqueDepth = depthTarget(opaqueDepth, main);
                opaqueDepth.copyDepthFrom(main);
                main.bindWrite(false);
                // the matrices of this moment: the light is recorded in the pose of the block entities (camera rotation included)
                // and the game draws the clouds and the weather with the rotation in its model-view matrix (seen in game: replayed
                // with it, every beam was turned twice and drawn off screen)
                deferredModelView.set(RenderSystem.getModelViewMatrix());
                deferredProjection.set(RenderSystem.getProjectionMatrix());
                deferredSorting = RenderSystem.getVertexSorting();
                beamsDeferred = true;
                return;
            } catch (Throwable t) {
                deferFailed = true;
                com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: beams drawn before the clouds again (depth copy failed)", t);
                try { com.concertstage.client.light.LightTargets.main().bindWrite(false); } catch (Throwable ignored) { }
            }
        }
        drawBeams();
    }

    private static com.mojang.blaze3d.pipeline.TextureTarget depthTarget(com.mojang.blaze3d.pipeline.TextureTarget t, com.mojang.blaze3d.pipeline.RenderTarget main) {
        if (t == null) return new com.mojang.blaze3d.pipeline.TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        if (t.width != main.width || t.height != main.height) t.resize(main.width, main.height, Minecraft.ON_OSX);
        return t;
    }

    private static void drawBeams() {
        // rev. 25: cost of each pass (GpuTimers: F3, in-game measurements)
        com.concertstage.client.light.GpuTimers.begin(com.concertstage.client.light.GpuTimers.BLOOM);
        boolean bloom = com.concertstage.client.light.Bloom.begin();   // rev. 21: the frame before the light of the fixtures
        com.concertstage.client.light.GpuTimers.end(com.concertstage.client.light.GpuTimers.BLOOM);
        com.concertstage.client.light.GpuTimers.begin(com.concertstage.client.light.GpuTimers.HAZE);
        com.concertstage.client.light.VolumeLights.flushBeams();   // rev. 15: beams in the haze, cut by the structures (shadow maps)
        com.concertstage.client.light.VolumeLights.resetBlend();
        com.concertstage.client.light.GpuTimers.end(com.concertstage.client.light.GpuTimers.HAZE);
        com.concertstage.client.light.GpuTimers.begin(com.concertstage.client.light.GpuTimers.BEAMS);
        com.concertstage.client.light.LightBatch.flush();          // 1.2.0 rev. 5: the light, after the opaque meshes it lights
        com.concertstage.client.light.GpuTimers.end(com.concertstage.client.light.GpuTimers.BEAMS);
        if (bloom) {
            com.concertstage.client.light.GpuTimers.begin(com.concertstage.client.light.GpuTimers.BLOOM);
            com.concertstage.client.light.Bloom.apply();           // rev. 21: what they added glows
            com.concertstage.client.light.GpuTimers.end(com.concertstage.client.light.GpuTimers.BLOOM);
        }
    }

    private static void drawDeferredBeams() {
        if (!beamsDeferred) return;
        beamsDeferred = false;
        long t0 = System.nanoTime();
        var main = com.concertstage.client.light.LightTargets.main();
        boolean swapped = false;
        try {
            fullDepth = depthTarget(fullDepth, main);
            fullDepth.copyDepthFrom(main);
            main.copyDepthFrom(opaqueDepth);
            swapped = true;
        } catch (Throwable t) {
            deferFailed = true;
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: beams drawn before the clouds again (depth copy failed)", t);
        }
        main.bindWrite(false);
        var mvStack = RenderSystem.getModelViewStack();
        org.joml.Matrix4f proj = new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        mvStack.pushPose();
        mvStack.setIdentity();
        mvStack.mulPoseMatrix(deferredModelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(deferredProjection, deferredSorting == null ? sorting : deferredSorting);
        try {
            drawBeams();
        } finally {
            mvStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(proj, sorting);
            if (swapped) {
                try { main.copyDepthFrom(fullDepth); } catch (Throwable t) { deferFailed = true; }
                main.bindWrite(false);
            }
            RenderStats.flushNanos += System.nanoTime() - t0;
        }
    }

    /**
     * 1.2.0 rev. 16e: the cut-out sheets that CSC renderers draw themselves (tarps, FOH / regie roof, roof sheets, rigging) must be in
     * the depth buffer before the light passes, so that the light lands on them and the beams stop on them. Rev. 16d did this by
     * sending the game's whole "solid" / "cutout" layers early; that also drew everything other code had put in those shared layers,
     * at a moment the game does not draw them, and is the most likely cause of the black areas of the 9 November (21 h) captures.
     * Now the game's layers are left alone: only CSC's own sheets are recorded here (when they would go to the level's buffers) and
     * drawn with the same explicit state as the GPU meshes, which render correctly.
     */
    public static VertexConsumer cutout(MultiBufferSource buffers) {
        if (!enabled() || buffers != Minecraft.getInstance().renderBuffers().bufferSource()) return buffers.getBuffer(RenderType.cutout());
        if (sheets == null) sheets = new BufferBuilder(1 << 16);
        if (!sheets.building()) sheets.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        return sheets;
    }

    private static BufferBuilder sheets;
    private static VertexBuffer sheetsVbo;

    private static void flushSheets() {
        if (sheets == null || !sheets.building()) return;
        BufferBuilder.RenderedBuffer rb = sheets.end();
        if (rb.isEmpty() || failed) { rb.release(); return; }
        try {
            var mc = Minecraft.getInstance();
            if (sheetsVbo == null) sheetsVbo = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            sheetsVbo.bind();
            sheetsVbo.upload(rb);
            com.concertstage.client.light.VolumeLights.resetBlend();
            mc.gameRenderer.lightTexture().turnOnLightLayer();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShader(GameRenderer::getRendertypeCutoutShader);
            ResourceLocation atlas = TextureAtlas.LOCATION_BLOCKS;
            mc.getTextureManager().getTexture(atlas).setFilter(false, false);
            RenderSystem.setShaderTexture(0, atlas);
            ShaderInstance sh = RenderSystem.getShader();
            if (sh != null && sh.CHUNK_OFFSET != null) sh.CHUNK_OFFSET.set(0f, 0f, 0f);
            if (sh != null) sheetsVbo.drawWithShader(new Matrix4f(RenderSystem.getModelViewMatrix()), RenderSystem.getProjectionMatrix(), sh);
            VertexBuffer.unbind();
            mc.gameRenderer.lightTexture().turnOffLightLayer();
            RenderStats.meshesDrawn++;
        } catch (Throwable t) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: GPU meshes turned off after a drawing error (sheets)", t);
        }
    }

    private static void flushMeshes(List<Queued> queue) {
        if (queue.isEmpty()) return;
        long t0 = System.nanoTime();
        RenderStats.meshesDrawn += queue.size();
        List<Queued> todo = new ArrayList<>(queue);
        if (queue == QUEUE) DRAWN.addAll(todo);
        queue.clear();
        if (failed) return;
        try {
            Map<Bucket, List<Queued>> by = new LinkedHashMap<>();
            for (Queued q : todo)
                for (Bucket b : q.mesh.parts.keySet()) by.computeIfAbsent(b, k -> new ArrayList<>()).add(q);
            Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
            var mc = Minecraft.getInstance();
            mc.gameRenderer.lightTexture().turnOnLightLayer();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            for (Map.Entry<Bucket, List<Queued>> e : by.entrySet()) {
                Bucket b = e.getKey();
                RenderSystem.setShader(GameRenderer::getRendertypeCutoutShader);
                mc.getTextureManager().getTexture(b.texture).setFilter(false, false);
                RenderSystem.setShaderTexture(0, b.texture);
                if (b.cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
                ShaderInstance sh = RenderSystem.getShader();
                if (sh == null) continue;
                for (int i = 0; i < 12; i++) sh.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
                if (sh.PROJECTION_MATRIX != null) sh.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
                if (sh.COLOR_MODULATOR != null) sh.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
                if (sh.FOG_START != null) sh.FOG_START.set(RenderSystem.getShaderFogStart());
                if (sh.FOG_END != null) sh.FOG_END.set(RenderSystem.getShaderFogEnd());
                if (sh.FOG_COLOR != null) sh.FOG_COLOR.set(RenderSystem.getShaderFogColor());
                if (sh.FOG_SHAPE != null) sh.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
                if (sh.TEXTURE_MATRIX != null) sh.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
                if (sh.GAME_TIME != null) sh.GAME_TIME.set(RenderSystem.getShaderGameTime());
                if (sh.CHUNK_OFFSET != null) sh.CHUNK_OFFSET.set(0f, 0f, 0f);
                if (sh.MODEL_VIEW_MATRIX != null) sh.MODEL_VIEW_MATRIX.set(view);
                RenderSystem.setupShaderLights(sh);
                sh.apply();
                for (Queued q : e.getValue()) {
                    VertexBuffer vb = q.mesh.parts.get(b);
                    if (vb == null || q.mesh.closed) continue;
                    if (sh.MODEL_VIEW_MATRIX != null) {
                        sh.MODEL_VIEW_MATRIX.set(lightSpace(MV.set(view).mul(q.pose), sh));   // 1.2.0: one matrix, not one per mesh
                        sh.MODEL_VIEW_MATRIX.upload();
                    }
                    vb.bind();
                    vb.draw();
                }
                clearLightSpace(sh);
                sh.clear();
            }
            VertexBuffer.unbind();
            RenderSystem.enableCull();
            mc.gameRenderer.lightTexture().turnOffLightLayer();
            RenderStats.flushNanos += System.nanoTime() - t0;
        } catch (Throwable t) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: GPU meshes turned off after a drawing error", t);
        }
    }

    /** Keep terrain Position + ChunkOffset in camera-relative light space, without rebuilding cached vertices. */
    private static Matrix4f lightSpace(Matrix4f modelView, ShaderInstance shader) {
        if (shader.CHUNK_OFFSET == null) return modelView;
        org.joml.Vector3f offset = new org.joml.Vector3f();
        GpuLightSpace.split(modelView, offset);
        shader.CHUNK_OFFSET.set(offset.x, offset.y, offset.z);
        shader.CHUNK_OFFSET.upload();
        return modelView;
    }

    /** ChunkOffset is shared shader state: leave the vanilla zero offset for subsequent renderers. */
    private static void clearLightSpace(ShaderInstance shader) {
        if (shader.CHUNK_OFFSET == null) return;
        shader.CHUNK_OFFSET.set(0f, 0f, 0f);
        shader.CHUNK_OFFSET.upload();
    }

    /**
     * Rev. 16c: draws the structure meshes of this frame into a shadow map. {@code invOwnerPose}: inverse of the pose of the block entity
     * that owns the map (so a mesh lands in the owner's block-local frame, whatever the camera), {@code lightView} / {@code proj}: the
     * light of the map, {@code apex} (block-local) and {@code reach}: meshes whose origin is further are skipped. Returns the count drawn.
     */
    public static int drawShadowCasters(Matrix4f invOwnerPose, Matrix4f lightView, Matrix4f proj, ShaderInstance shader,
                                        org.joml.Vector3f apex, float reach) {
        int n = 0;
        boolean started = false;
        Matrix4f rel = new Matrix4f(), mv = new Matrix4f();
        float r2 = reach * reach;
        for (Queued q : DRAWN) {
            if (q.mesh.closed) continue;
            rel.set(invOwnerPose).mul(q.pose);
            float dx = rel.m30() - apex.x, dy = rel.m31() - apex.y, dz = rel.m32() - apex.z;
            if (dx * dx + dy * dy + dz * dz > r2) continue;
            mv.set(lightView).mul(rel);
            // rev. 16g: only meshes in the cone of the light (their origin within ~6 blocks of the frustum): a map of a beam aimed at
            // the audience no longer draws every tower and truss of the stage behind it
            if (!inLightFrustum(mv, proj, 6f)) continue;
            for (Map.Entry<Bucket, VertexBuffer> e : q.mesh.parts.entrySet()) {
                // the texture of the part: its transparent texels let the light through (concertstage_shadow.fsh)
                // rev. 25 (perf): the shader set up once per map (FastDraw), only the texture and the matrix change per part
                if (!started) { com.concertstage.client.light.FastDraw.begin(shader, proj, e.getKey().texture()); started = true; }
                else com.concertstage.client.light.FastDraw.texture(e.getKey().texture());
                com.concertstage.client.light.FastDraw.draw(e.getValue(), mv);
            }
            n++;
        }
        if (started) com.concertstage.client.light.FastDraw.end();
        VertexBuffer.unbind();
        return n;
    }

    /** Rev. 16g: whether the origin of a mesh (translation of {@code mv}, light view space) is within {@code margin} blocks of the frustum. */
    private static boolean inLightFrustum(Matrix4f mv, Matrix4f proj, float margin) {
        float x = mv.m30(), y = mv.m31(), z = mv.m32();
        float depth = -z;
        if (depth < -margin) return false;                                 // behind the light
        float d = Math.max(depth, 0.5f);
        float sx = proj.m00(), sy = proj.m11();
        // half extent of the frustum at that depth, plus the margin
        return Math.abs(x) <= d / sx + margin && Math.abs(y) <= d / sy + margin;
    }

    /**
     * Rev. 16c: a value that changes when the structure meshes near a map change (a tower lifted, a truss placed or moved), computed in
     * the owner's block-local frame so that it does not change when only the camera moves.
     */
    public static long drawnSignature(Matrix4f invOwnerPose, org.joml.Vector3f apex, float reach) {
        long h = 1;
        Matrix4f rel = SIG_REL;
        org.joml.Vector3f t = SIG_T;
        float r2 = reach * reach;
        for (Queued q : DRAWN) {
            // 1.2.0: the position first (one point transformed), the whole matrix only for the meshes within reach — this ran a
            // matrix product for every mesh drawn and every shadow map (288 fixtures: 64 maps x ~900 meshes each frame)
            invOwnerPose.transformPosition(t.set(q.pose.m30(), q.pose.m31(), q.pose.m32()));
            float dx = t.x - apex.x, dy = t.y - apex.y, dz = t.z - apex.z;
            if (dx * dx + dy * dy + dz * dz > r2) continue;
            rel.set(invOwnerPose).mul(q.pose);
            h = h * 31 + System.identityHashCode(q.mesh);
            h = h * 31 + Math.round(rel.m30() * 64f);
            h = h * 31 + Math.round(rel.m31() * 64f);
            h = h * 31 + Math.round(rel.m32() * 64f);
            h = h * 31 + Math.round(rel.m00() * 256f) + 7L * Math.round(rel.m02() * 256f) + 13L * Math.round(rel.m11() * 256f);
        }
        return h;
    }

    /** Start of the world rendering (called by the loaders). */
    /** Rev. 22: frames drawn (counted at the start of each world frame), the frame key of the lighting budgets. */
    public static long frameSerial;

    public static void beginFrame() {
        frameSerial++;
        if (sheets != null && sheets.building()) sheets.end().release();   // a frame that was not drawn to the end
        QUEUE.clear();
        LATE.clear();
        DRAWN.clear();
        com.concertstage.client.light.LightBatch.clear();
        com.concertstage.client.light.VolumeLights.clear();
        beamsDeferred = false;
        inWorld = true;
    }

    /** End of the world rendering: anything still queued is sent, and meshes not drawn for about 20 s are freed. */
    public static void endFrame() {
        flush();
        drawDeferredBeams();
        com.concertstage.client.vision.VisionStats.endFrame();
        inWorld = false;
        RenderStats.frame();
        frame++;
        if (frame % 200 != 0) return;
        for (Iterator<GpuMesh> it = ALIVE.iterator(); it.hasNext(); ) {
            GpuMesh m = it.next();
            if (m.closed) { it.remove(); continue; }
            if (frame - m.lastUsed > 1200) { m.free(); it.remove(); }
        }
    }

    /** Frees a mesh now (its owner rebuilds it if it is needed again). */
    public void free() {
        if (closed) return;
        closed = true;
        for (VertexBuffer vb : parts.values()) {
            try { vb.close(); } catch (RuntimeException ignored) {}
        }
        parts.clear();
    }

    /** Leaving a world, resource reload, quality change: every mesh is freed. */
    public static void clearAll() {
        com.concertstage.client.vision.VisionModel.clear();
        QUEUE.clear();
        LATE.clear();
        for (GpuMesh m : ALIVE) m.free();
        ALIVE.clear();
    }
}
