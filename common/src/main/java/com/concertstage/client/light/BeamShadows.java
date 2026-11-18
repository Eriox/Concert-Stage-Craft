package com.concertstage.client.light;

import com.concertstage.compat.LightCompat;
import com.concertstage.light.FixtureBlock;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 1.2.0 rev. 15: shadows of the structures in the light of the fixtures (a shadow map per beam).
 *
 * <p>What a fixture "sees" through its optic is drawn into a small depth texture, from the fixture: the real geometry of the blocks
 * around it — the baked models of the trusses (their chords, braces and diagonals as modelled), towers, scaffolding, platforms,
 * walls — and the outline of the blocks the game draws otherwise. The projected light ({@code concertstage_light_projector}) and the
 * volume of the beam in the haze ({@code concertstage_beam_volume}) compare the distance of each point to the fixture with that
 * texture: a point behind a tube, as seen from the fixture, gets no light. The gobo keeps its pattern and loses the parts the tubes
 * hide; behind each tube a dark shaft runs through the haze. It is a real depth test, recomputed when the head moves, the zoom or
 * the range change.
 *
 * <p>Cost: the geometry is gathered per 16³ section into a vertex buffer kept on the GPU (shared by every fixture, rebuilt every few
 * seconds, one section per frame at most); a shadow map is a few draw calls of those buffers into a 256² depth texture, only when
 * the beam of that fixture changed. Fixture bodies (CSC fixtures are drawn by their renderer) never cast shadows.
 */
public final class BeamShadows {
    /** Shadow maps per quality level (low, medium, high, ultra) and how many can be redrawn in one frame. */
    // rev. 21: one per projection (VolumeLights.MAX_PROJECTORS), no longer one per beam in the haze: a projection with no map of its
    // own lit everything in its cone, through every deck panel and roof (light under the stage, picture 2 of 11 November)
    // (and the beams in the haze that are not projected too: a map for every place of the frame, VolumeLights.MAX_PROJECTORS +
    // MAX_VOLUMES; with only one per projection, the beams in the haze took maps first and some projections went without — seen in
    // game at medium quality, the scaffolding under the stage front lit through the deck)
    // rev. 22: maps at low quality too (40: its 24 projections + 16 beams in the haze). Low had none: its light went through the
    // stage deck and the festival assemblies, and the author plays at low (picture 1 and video of 11 November, 21 h)
    private static final int[] MAX_SLOTS = {80, 112, 112, 160};       // rev. 23: low 40 -> 80, medium 56 -> 112
    // rev. 16g: maps redrawn per frame. A moving rig (AutoShow, 9 November 22 h 37 video: "15 maps (15 redrawn)", thousands of meshes,
    // GPU 100 %) redrew every map every frame; now the oldest ones first, a few per frame: each map follows its fixture within 2 to 4
    // frames (the shadow of a moving head lags by a few hundredths of a second, unseen in motion)
    private static final int[] MAX_RENDERS = {3, 4, 6, 8};
    /** How far the shadows reach from the fixture (blocks), per quality level. */
    // rev. 21: as far as the beams throw (VORTEX 72 blocks): the projected light stopped at 48 blocks, so the far truss of a large roof
    // or the far side of a stage stayed dark from one side (pictures 3 and 4 of 11 November)
    // and the same at every distance from the camera (the quality of a far fixture is lower, its light must not end sooner)
    private static final float[] FAR = {64f, 72f, 72f, 96f};
    /** Rev. 21: size of a map per quality level (320 at ultra: the longer reach keeps the shadows sharp; depth only, no colour buffer). */
    private static final int[] SIZES = {192, 256, 256, 320};
    public static final int SIZE = 256;
    // rev. 21: 36 sections at most per map dropped the far ones of a wide cone — the stage deck under a wash 20 blocks away was not in
    // its map and the light went through it
    private static final int MAX_SECTIONS_PER_MAP = 160, SECTION_CHECK_FRAMES = 160, SECTION_EVICT_FRAMES = 3600;   // rev. 25: fewer section rebuilds when moving heads sweep (churn)

    private static final class Section {
        VertexBuffer vbo;
        boolean empty;
        long builtFrame, usedFrame, generation;
        /** Rev. 21: what the blocks of the section were when it was built (rebuilt only when they change). */
        long contentHash;
    }

    private static final class Slot {
        DepthTarget target;
        Object owner;
        long usedFrame, renderedGen = -1, countedFrame = -1;
        boolean rendered;
        // the light (block-local frame of the owner) the map was drawn with
        final Matrix4f lightView = new Matrix4f(), lightProj = new Matrix4f();
        float near, far;
        // requested this frame
        final Matrix4f wantView = new Matrix4f(), wantProj = new Matrix4f();
        float wantNear, wantFar;
        // what the map depends on (apex, axis, up, field of view, near, far): redrawn when it changes
        final float[] key = new float[12], wantKey = new float[12];
        BlockPos origin;
        long[] sections = new long[0];
        final Matrix4f blockPose = new Matrix4f();
        long meshSig, renderedMeshSig;           // rev. 16c: the structure meshes in the cone (GpuMesh), redrawn when they change
        boolean requested;
        long drewFrame = -1, wantGen;                    // rev. 16g: last frame it was redrawn (oldest first)
    }

    // rev. 25 (memory): a primitive long map — HashMap<Long, …> boxed every key looked up (thousands of objects a frame with moving heads)
    private static final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Section> SECTIONS = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private static final Map<Object, Slot> BY_OWNER = new IdentityHashMap<>();
    private static final List<Slot> SLOTS = new ArrayList<>();
    private static long frame, generation;
    private static ShaderInstance shader;
    private static boolean failed;
    private static BufferBuilder builder;
    private static int statMaps, statRendered, statSections;
    static int lastMaps, lastRendered, lastSections;

    private BeamShadows() {}

    static boolean enabled(int quality) {
        return !failed && MAX_SLOTS[BeamOpticsQ.q(quality)] > 0 && VolumeLights.enabled();
    }

    /** Shadow range of a quality level. */
    static float far(int quality) {
        return FAR[BeamOpticsQ.q(quality)];
    }

    /**
     * A beam asks for its shadow map this frame (block-local frame: {@code origin} lens, {@code axis}, {@code side} first axis of the
     * pattern; {@code blockPose} the block entity pose). Returns a handle for {@link VolumeLights}, or -1 (no shadows: budget, feature
     * off). The map itself is drawn later in the frame, by {@link #render()}.
     */
    static int request(Object owner, Level level, BlockPos bp, Matrix4f blockPose, Vector3f origin, Vector3f axis, Vector3f side,
                       float r0, float tanHalf, float range, int quality) {
        if (!enabled(quality) || level == null) return -1;
        int q = BeamOpticsQ.q(quality);
        Slot slot = BY_OWNER.get(owner);
        if (slot == null) {
            for (Slot s : SLOTS) if (s.usedFrame < frame - 2) { BY_OWNER.remove(s.owner); slot = s; break; }
            if (slot == null && SLOTS.size() < LightClient.budget(LightClient.maxShadows, MAX_SLOTS[FixtureRenderer.Perf.quality()])) { slot = new Slot(); SLOTS.add(slot); }
            if (slot == null) return -1;
            slot.owner = owner;
            slot.rendered = false;
            BY_OWNER.put(owner, slot);
        }
        if (slot.requested) return -1;                              // a prism: only its first beam
        slot.usedFrame = frame;
        slot.requested = true;
        slot.origin = bp.immutable();
        slot.blockPose.set(blockPose);
        float tan = Math.max(0.004f, tanHalf);
        // the light comes from the apex of the cone (behind the lens by r0 / tan): the map has the same perspective as the pattern
        float back = Math.min(60f, r0 / tan);
        Vector3f apex = new Vector3f(axis).mul(-back).add(origin);
        Vector3f up = new Vector3f(axis).cross(side).normalize();
        float far = Math.min(range, FAR[q]);
        slot.wantNear = back + 0.3f;
        slot.wantFar = back + Math.max(1f, far);
        slot.wantView.identity().lookAt(apex.x, apex.y, apex.z, apex.x + axis.x, apex.y + axis.y, apex.z + axis.z, up.x, up.y, up.z);
        float fov = 2f * (float) Math.atan(tan * 1.04f + 0.01f);
        slot.wantProj.identity().perspective(fov, 1f, slot.wantNear, slot.wantFar);
        float[] k = slot.wantKey;
        k[0] = apex.x; k[1] = apex.y; k[2] = apex.z; k[3] = axis.x; k[4] = axis.y; k[5] = axis.z;
        k[6] = up.x; k[7] = up.y; k[8] = up.z; k[9] = fov; k[10] = slot.wantNear; k[11] = slot.wantFar;
        // sections the cone goes through (its bounding box), nearest first
        float rEnd = r0 + far * tan;
        Vector3f end = new Vector3f(axis).mul(far).add(origin);
        int x0 = bp.getX() + (int) Math.floor(Math.min(origin.x, end.x) - rEnd), x1 = bp.getX() + (int) Math.ceil(Math.max(origin.x, end.x) + rEnd);
        int y0 = bp.getY() + (int) Math.floor(Math.min(origin.y, end.y) - rEnd), y1 = bp.getY() + (int) Math.ceil(Math.max(origin.y, end.y) + rEnd);
        int z0 = bp.getZ() + (int) Math.floor(Math.min(origin.z, end.z) - rEnd), z1 = bp.getZ() + (int) Math.ceil(Math.max(origin.z, end.z) + rEnd);
        // rev. 25 (memory): reused primitive arrays instead of a list of long[2] per section and per fixture each frame (~500 KB a frame
        // with 62 moving heads, measured in game); sorted as (distance << 24 | index) longs, nearest first, no allocation
        int count = 0;
        for (int sx = x0 >> 4; sx <= x1 >> 4; sx++) for (int sy = y0 >> 4; sy <= y1 >> 4; sy++) for (int sz = z0 >> 4; sz <= z1 >> 4; sz++) {
            if (!sectionInCone(sx, sy, sz, bp, origin, axis, r0, tan, far)) continue;
            long d = (long) sq((sx << 4) + 8 - bp.getX()) + sq((sy << 4) + 8 - bp.getY()) + sq((sz << 4) + 8 - bp.getZ());
            if (count == FOUND_KEY.length || count >= (1 << 24)) {
                if (count >= (1 << 24)) break;
                FOUND_KEY = java.util.Arrays.copyOf(FOUND_KEY, count * 2);
                FOUND_ORDER = java.util.Arrays.copyOf(FOUND_ORDER, count * 2);
            }
            FOUND_KEY[count] = net.minecraft.core.SectionPos.asLong(sx, sy, sz);
            FOUND_ORDER[count] = (Math.min(d, (1L << 38)) << 24) | count;
            count++;
        }
        java.util.Arrays.sort(FOUND_ORDER, 0, count);
        int n = Math.min(MAX_SECTIONS_PER_MAP, count);
        if (slot.sections.length != n) slot.sections = new long[n];
        for (int i = 0; i < n; i++) slot.sections[i] = FOUND_KEY[(int) (FOUND_ORDER[i] & 0xFFFFFFL)];
        level(level);
        return SLOTS.indexOf(slot);
    }

    private static Level level;
    private static long[] FOUND_KEY = new long[512], FOUND_ORDER = new long[512];   // render thread only

    private static void level(Level l) {
        if (level != l) { level = l; clearSections(); }
    }

    /** Rough test: the sphere around a section meets the cone. */
    private static boolean sectionInCone(int sx, int sy, int sz, BlockPos bp, Vector3f o, Vector3f a, float r0, float tan, float far) {
        float cx = (sx << 4) + 8 - bp.getX() - o.x, cy = (sy << 4) + 8 - bp.getY() - o.y, cz = (sz << 4) + 8 - bp.getZ() - o.z;
        float along = cx * a.x + cy * a.y + cz * a.z;
        float rad = 13.9f;                                          // half diagonal of a section
        if (along < -rad || along > far + rad) return false;
        float al = Math.max(0f, Math.min(far, along));
        float px = cx - a.x * along, py = cy - a.y * along, pz = cz - a.z * along;
        float dist = (float) Math.sqrt(px * px + py * py + pz * pz);
        return dist <= r0 + al * tan + rad * (1f + tan);
    }

    private static int sq(int x) {
        return x * x;
    }

    /** Draws the maps asked for this frame that changed (render thread, before the projected light). Restores nothing: the caller binds its target. */
    static void render() {
        if (failed || SLOTS.isEmpty()) return;
        boolean any = false;
        for (Slot s : SLOTS) if (s.requested) { any = true; break; }
        if (!any) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (shader == null) shader = new ShaderInstance(mc.getResourceManager(), "concertstage_shadow", DefaultVertexFormat.POSITION_COLOR_TEX);
            int q = BeamOpticsQ.q(LightClient.beamQuality);
            // rev. 16d: once per frame. The renderer can run twice a frame (after the block entities and after the translucent blocks)
            // and the structure meshes listed in between differed: every map was redrawn twice a frame (31 redrawn for 15 maps,
            // 4805 meshes, 9 November) — the frame-rate drop
            if (renderedFrame == frame) return;
            renderedFrame = frame;
            int budget = MAX_RENDERS[q];
            // rev. 21: sections never built first (a few per frame: the occluders of a new view arrive within a second), then the
            // periodic check of the old ones, which only rebuilds a section whose blocks really changed (content hash)
            int builds = q >= 3 ? 4 : 2, checks = 2;
            // rev. 21: and never more than ~1.5 ms of building per frame (a new view asks for many sections at once: the frame-rate
            // dip of the first seconds made the automatic quality step down for 20 s)
            long buildStart = System.nanoTime();
            List<Slot> todo = new ArrayList<>();
            for (Slot s : SLOTS) {
                if (!s.requested) continue;
                if (s.countedFrame != frame) { s.countedFrame = frame; statMaps++; }     // render() can run several times a frame
                // sections: built when missing or old (a block placed or broken shows within a few seconds)
                long gen = 0;
                for (long key : s.sections) {
                    Section sec = SECTIONS.get(key);
                    boolean time = System.nanoTime() - buildStart < 1_500_000L;
                    if (sec == null) { if (builds > 0 && time) { sec = build(key, null, 0L); builds--; } }
                    else if (frame - sec.builtFrame > SECTION_CHECK_FRAMES && checks > 0) {
                        checks--;
                        long h = contentHash(key);
                        if (h != sec.contentHash && builds > 0 && time) { sec = build(key, sec, h); builds--; }
                        else if (h == sec.contentHash) sec.builtFrame = frame;
                    }
                    if (sec != null) { sec.usedFrame = frame; gen = Math.max(gen, sec.generation); }
                }
                s.wantGen = gen;
                // rev. 16c: the structure meshes (towers, angled / ring trusses, roofs...) are part of the map: redrawn when they move
                Matrix4f inv = new Matrix4f(s.blockPose).invert();
                s.meshSig = com.concertstage.client.GpuMesh.drawnSignature(inv, apexOf(s), reachOf(s));
                boolean moved = !s.rendered || differs(s.key, s.wantKey) || gen > s.renderedGen || s.meshSig != s.renderedMeshSig;
                if (moved) todo.add(s);
            }
            // never drawn first, then the oldest
            todo.sort((a, b) -> a.rendered != b.rendered ? (a.rendered ? 1 : -1) : Long.compare(a.drewFrame, b.drewFrame));
            for (Slot s : todo) {
                if (budget-- <= 0) break;
                s.drewFrame = frame;
                draw(s, s.wantGen);
            }
        } catch (Throwable t) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: beam shadows turned off (shader or driver error)", t);
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthFunc(515);
        }
        // forget sections nobody looked at for a while
        if (frame % 60 == 0) for (Iterator<Section> it = SECTIONS.values().iterator(); it.hasNext(); ) {   // once a second is enough
            Section sec = it.next();
            if (frame - sec.usedFrame > SECTION_EVICT_FRAMES) { if (sec.vbo != null) sec.vbo.close(); it.remove(); }
        }
        statSections = SECTIONS.size();
    }

    static int statMeshes, lastMeshes;
    private static long renderedFrame = -1;

    /** Apex of the light of a slot (block-local of its owner), from its key. */
    private static Vector3f apexOf(Slot s) {
        return new Vector3f(s.wantKey[0], s.wantKey[1], s.wantKey[2]);
    }

    /** How far from the apex a mesh origin can be and still matter (the far plane plus the size of a large structure piece). */
    private static float reachOf(Slot s) {
        return s.wantFar + 24f;
    }

    private static boolean differs(float[] a, float[] b) {
        for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - b[i]) > 1e-4f) return true;
        return false;
    }

    private static void draw(Slot s, long gen) {
        int size = SIZES[BeamOpticsQ.q(LightClient.beamQuality)];
        if (s.target != null && s.target.size != size) { s.target.destroy(); s.target = null; }
        if (s.target == null) s.target = new DepthTarget(size);
        s.target.bindAndClear();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();                                  // both sides of every face: thin tubes, open models
        Matrix4f mv = new Matrix4f();
        // rev. 16e: through RenderSystem — VertexBuffer.drawWithShader sets Sampler0..11 from RenderSystem's textures, so a sampler set
        // on the shader directly was replaced by whatever texture was bound last (the alpha test then read the wrong picture)
        RenderSystem.setShaderTexture(0, net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
        // rev. 21 (perf): only the sections inside the light's frustum (the list is the box around the cone, up to 160 sections)
        org.joml.FrustumIntersection fi = new org.joml.FrustumIntersection(new Matrix4f(s.wantProj).mul(s.wantView));
        // rev. 25 (perf): the shader is set up once per map, then each section only changes its model-view matrix and draws.
        // drawWithShader set up every uniform and sampler again for each section: ~1000 calls a frame with moving heads, 4 ms of
        // processor time (measured in game, 12 November)
        FastDraw.begin(shader, s.wantProj, net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
        try {
            for (long key : s.sections) {
                Section sec = SECTIONS.get(key);
                if (sec == null || sec.empty || sec.vbo == null) continue;
                int sx = net.minecraft.core.SectionPos.x(key) << 4, sy = net.minecraft.core.SectionPos.y(key) << 4, sz = net.minecraft.core.SectionPos.z(key) << 4;
                float rx = sx - s.origin.getX(), ry = sy - s.origin.getY(), rz = sz - s.origin.getZ();
                if (!fi.testAab(rx, ry, rz, rx + 16f, ry + 16f, rz + 16f)) continue;
                mv.set(s.wantView).translate(rx, ry, rz);
                FastDraw.draw(sec.vbo, mv);
            }
        } finally {
            FastDraw.end();
        }
        VertexBuffer.unbind();
        // rev. 16c: the meshes the mod really draws for its structures (instead of their outline boxes, which stopped the light on
        // invisible boxes: the beam in the haze ended on their faces and showed the gobo in the openings of the truss)
        statMeshes += com.concertstage.client.GpuMesh.drawShadowCasters(new Matrix4f(s.blockPose).invert(), s.wantView, s.wantProj, shader,
            apexOf(s), reachOf(s));
        drawSheets(s);
        s.renderedMeshSig = s.meshSig;
        s.lightView.set(s.wantView);
        s.lightProj.set(s.wantProj);
        System.arraycopy(s.wantKey, 0, s.key, 0, s.key.length);
        s.near = s.wantNear;
        s.far = s.wantFar;
        s.rendered = true;
        s.renderedGen = gen;
        statRendered++;
    }

    /**
     * Rev. 21: a hash of the block states of a section (identity of each state and its place). Reading 4096 states costs far less than
     * reading their models and uploading them: the sections are checked every few seconds and rebuilt only when this changes.
     */
    private static long contentHash(long key) {
        Level lvl = level;
        if (lvl == null) return 1L;
        int ox = net.minecraft.core.SectionPos.x(key) << 4, oy = net.minecraft.core.SectionPos.y(key) << 4, oz = net.minecraft.core.SectionPos.z(key) << 4;
        if (!lvl.hasChunk(ox >> 4, oz >> 4)) return 2L;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long h = 1469598103934665603L;
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            BlockState st = lvl.getBlockState(pos.set(ox + x, oy + y, oz + z));
            h = (h ^ System.identityHashCode(st)) * 1099511628211L;
        }
        return h == 0L ? 3L : h;
    }

    /** The geometry of one section that can stop light, in section-local coordinates, uploaded to the GPU. */
    private static Section build(long key, Section old, long hash) {
        Section sec = old != null ? old : new Section();
        sec.builtFrame = frame;
        sec.generation = ++generation;
        sec.contentHash = hash != 0L ? hash : contentHash(key);
        Level lvl = level;
        if (lvl == null) { sec.empty = true; SECTIONS.put(key, sec); return sec; }
        int ox = net.minecraft.core.SectionPos.x(key) << 4, oy = net.minecraft.core.SectionPos.y(key) << 4, oz = net.minecraft.core.SectionPos.z(key) << 4;
        if (builder == null) builder = new BufferBuilder(1 << 18);
        BufferBuilder bb = builder;
        // rev. 16c: with the texture coordinates, so that the holes of cut-out textures (scaffolding, bars, models of other mods) let
        // the light through instead of making the whole face opaque
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        int quads = 0;
        Minecraft mc = Minecraft.getInstance();
        RandomSource rand = RandomSource.create();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            pos.set(ox + x, oy + y, oz + z);
            BlockState st = lvl.getBlockState(pos);
            if (st.isAir() || st.getBlock() instanceof FixtureBlock || !st.getFluidState().isEmpty() && st.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) continue;
            if (quads > 60000) break;
            try {
                quads += emit(bb, lvl, pos, st, x, y, z, mc, rand);
            } catch (RuntimeException e) {
                // a model of another mod that cannot be read here: that block casts no shadow, the others still do
            }
        }
        BufferBuilder.RenderedBuffer rb = bb.end();
        if (quads == 0 || rb.isEmpty()) {
            rb.release();
            sec.empty = true;
            if (sec.vbo != null) { sec.vbo.close(); sec.vbo = null; }
        } else {
            if (sec.vbo == null) sec.vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            sec.vbo.bind();
            sec.vbo.upload(rb);
            VertexBuffer.unbind();
            sec.empty = false;
        }
        SECTIONS.put(key, sec);
        return sec;
    }

    /** Quads of one block that can stop light, written to {@code bb} at section-local {@code x y z}. */
    private static int emit(BufferBuilder bb, Level lvl, BlockPos pos, BlockState st, int x, int y, int z, Minecraft mc, RandomSource rand) {
        int n = 0;
        if (st.getRenderShape() == RenderShape.MODEL) {
            // light goes through glass, ice, stained blocks
            if (ItemBlockRenderTypes.getChunkRenderType(st) == RenderType.translucent()) return n;
            BakedModel model = mc.getBlockRenderer().getBlockModel(st);
            long seed = st.getSeed(pos);
            for (int d = -1; d < 6; d++) {
                Direction dir = d < 0 ? null : Direction.values()[d];
                if (dir != null && !Block.shouldRenderFace(st, lvl, pos, dir, pos.relative(dir))) continue;
                rand.setSeed(seed);
                List<BakedQuad> list = model.getQuads(st, dir, rand);
                for (BakedQuad bq : list) {
                    int[] v = bq.getVertices();
                    int stride = v.length / 4;
                    for (int i = 0; i < 4; i++) {
                        bb.vertex(x + Float.intBitsToFloat(v[i * stride]), y + Float.intBitsToFloat(v[i * stride + 1]),
                            z + Float.intBitsToFloat(v[i * stride + 2])).color(255, 255, 255, 255)
                            .uv(Float.intBitsToFloat(v[i * stride + 4]), Float.intBitsToFloat(v[i * stride + 5])).endVertex();
                    }
                    n++;
                }
            }
            // rev. 16d: a block of the mod whose model draws nothing (its look comes from a renderer, a tarp sheet): its light shape
            if (n == 0) {
                VoxelShape ls = lightShape(lvl, pos, st);
                if (ls != null) for (AABB b : ls.toAabbs()) {
                    box(bb, (float) (x + b.minX), (float) (y + b.minY), (float) (z + b.minZ), (float) (x + b.maxX), (float) (y + b.maxY), (float) (z + b.maxZ));
                    n += 6;
                }
            }
        } else {
            // drawn by a renderer (towers, tarps, roofs...): its truss lattice. Rev. 16c: no more outline box for the others — the box
            // is not what is drawn (an angled truss, a ring truss, a roof frame are open structures): their real meshes are drawn into
            // the map from GpuMesh (drawShadowCasters); a renderer of another mod casts no shadow rather than an invisible box
            VoxelShape sh = LightCompat.lattice(st.getBlock(), st);
            // rev. 16d: the structures of the mod drawn by a renderer outside the GPU meshes (tarps, FOH roof, curtains...) stop the
            // light with the shape they give to light rays (thin sheets, plates) — the beams crossed the tarps and the stage roofs
            if (sh == null) sh = lightShape(lvl, pos, st);
            if (sh == null || sh.isEmpty()) return n;
            for (AABB b : sh.toAabbs()) {
                box(bb, (float) (x + b.minX), (float) (y + b.minY), (float) (z + b.minZ), (float) (x + b.maxX), (float) (y + b.maxY), (float) (z + b.maxZ));
                n += 6;
            }
        }
        return n;
    }

    /** Rev. 20: the light shape of the sheets (tarp cloth and top, curtain cloth, FOH roof plate) for the rays of the fixtures; null otherwise. */
    static VoxelShape sheetShape(Level lvl, BlockPos pos, BlockState st) {
        var b = st.getBlock();
        if (!(b instanceof com.concertstage.block.tarp.StageTarpClothBlock || b instanceof com.concertstage.block.tarp.StageTarpTopBlock
            || b instanceof com.concertstage.block.CurtainClothBlock || b instanceof com.concertstage.block.regie.RegieRoofBlock)) return null;
        return lightShape(lvl, pos, st);
    }

    /** Rev. 16d: the shape a block of the mod gives to light rays (null for the blocks of other mods: their outline is not their look). */
    private static VoxelShape lightShape(Level lvl, BlockPos pos, BlockState st) {
        var b = st.getBlock();
        // only the blocks that define what stops light (their light-ray shape); any other outline would be an invisible box again
        boolean lightShaped = b instanceof com.concertstage.block.tarp.StageTarpClothBlock || b instanceof com.concertstage.block.tarp.StageTarpTopBlock
            || b instanceof com.concertstage.block.tarp.StageTarpRailBlock || b instanceof com.concertstage.block.CurtainClothBlock
            || b instanceof com.concertstage.block.CurtainRailBlock || b instanceof com.concertstage.block.ring.RingTrussCellBlock
            || b instanceof com.concertstage.block.regie.RegieRoofBlock;
        if (!lightShaped) return null;
        VoxelShape sh = st.getCollisionShape(lvl, pos, com.concertstage.compat.LightCollisionContext.INSTANCE);
        // a roof plate answers light rays with no collision: its outline is the plate itself
        if (sh.isEmpty() && b instanceof com.concertstage.block.regie.RegieRoofBlock) sh = st.getShape(lvl, pos);
        return sh;
    }

    /** Rev. 20: the roof sheets with no block of their own (LightBlockers) in the map, around the cone of the slot. */
    private static void drawSheets(Slot s) {
        Vector3f apex = apexOf(s);
        float r = reachOf(s);
        double cx = s.origin.getX() + apex.x, cy = s.origin.getY() + apex.y, cz = s.origin.getZ() + apex.z;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        int[] n = {0};
        double ox = s.origin.getX(), oy = s.origin.getY(), oz = s.origin.getZ();
        LightBlockers.forEach(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r, q -> {
            for (int k = 0; k < 4; k++) bb.vertex((float) (q[k * 3] - ox), (float) (q[k * 3 + 1] - oy), (float) (q[k * 3 + 2] - oz)).color(255, 255, 255, 0).uv(0f, 0f).endVertex();
            n[0]++;
        });
        BufferBuilder.RenderedBuffer rb = bb.end();
        if (n[0] == 0) { rb.release(); return; }
        RenderSystem.setShader(() -> shader);
        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().setIdentity();
        RenderSystem.applyModelViewMatrix();
        Matrix4f oldProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f(s.wantProj).mul(s.wantView), sorting);
        try {
            BufferUploader.drawWithShader(rb);
        } finally {
            RenderSystem.setProjectionMatrix(oldProj, sorting);
            RenderSystem.getModelViewStack().popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void box(BufferBuilder bb, float x0, float y0, float z0, float x1, float y1, float z1) {
        float[][] f = {
            {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
            {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
            {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        // alpha 0 in the colour: no texture test for these boxes (concertstage_shadow.fsh)
        for (float[] q : f) for (int i = 0; i < 4; i++) bb.vertex(q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).color(255, 255, 255, 0).uv(0f, 0f).endVertex();
    }

    // ---- what VolumeLights needs to read a map (handle = index of the slot)

    /** Depth texture of a map, or -1 when it has never been drawn. */
    static int texture(int h) {
        if (h < 0 || h >= SLOTS.size()) return -1;
        Slot s = SLOTS.get(h);
        return s.rendered && s.target != null ? s.target.texture : -1;
    }

    /** View space (camera at the origin, after {@code modelView}) to the clip space of the map, with the light it was drawn with. */
    static Matrix4f shadowMat(int h, Matrix4f modelView) {
        Slot s = SLOTS.get(h);
        Matrix4f viewToLocal = new Matrix4f(modelView).mul(s.blockPose).invert();
        return new Matrix4f(s.lightProj).mul(s.lightView).mul(viewToLocal);
    }

    static float nearOf(int h) { return SLOTS.get(h).near; }

    static float farOf(int h) { return SLOTS.get(h).far; }

    public static void beginFrame() {
        frame++;
        lastMaps = statMaps; lastRendered = statRendered; lastSections = statSections; lastMeshes = statMeshes; statMeshes = 0;
        statMaps = statRendered = 0;
        for (Slot s : SLOTS) s.requested = false;
    }

    /** World left (or changed): every cached buffer goes. */
    public static void clearSections() {
        for (Section sec : SECTIONS.values()) if (sec.vbo != null) sec.vbo.close();
        SECTIONS.clear();
        for (Slot s : SLOTS) s.rendered = false;
    }

    static String stats() {
        if (failed) return "shadows: turned off after a shader error";
        return "shadows " + lastMaps + " maps (" + lastRendered + " redrawn), " + lastSections + " sections, " + lastMeshes + " meshes";
    }

    /** Quality level clamp without depending on the common optics class for an int. */
    private static final class BeamOpticsQ {
        static int q(int quality) { return Math.max(0, Math.min(3, quality)); }
    }
}
