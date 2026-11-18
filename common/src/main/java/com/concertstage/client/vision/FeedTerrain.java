package com.concertstage.client.vision;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * CSC 1.3.0 rev. 2 — the blocks of the camera images when the game's own chunk meshes cannot be used.
 *
 * <p>Embeddium / Sodium (and other renderers that replace the game's chunk rendering) leave the vanilla chunk sections empty: the
 * camera images showed the sky and nothing else (author's CraftCity profile, 12 November, Embeddium 0.3.31). Here the sections around
 * the cameras are built by CSC itself — the same block models, fluids and light as the game, through the game's block renderer —
 * into its own vertex buffers, a few per image within a time budget, nearest first, and rebuilt every 30 s (changes made in the
 * world appear with that delay in the camera images). At most {@link #MAX_SECTIONS} are kept; the oldest used are freed.
 */
public final class FeedTerrain {
    /** Rev. 2: how a block is drawn into the layers (Forge sets one that reads the render types and model data of each model). */
    public interface BlockDrawer {
        void draw(BlockState state, BlockPos pos, ClientLevel level, PoseStack pose, java.util.function.Function<RenderType, com.mojang.blaze3d.vertex.VertexConsumer> layer, RandomSource random);
    }

    public static BlockDrawer drawer;

    static final RenderType[] LAYERS = {RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout(), RenderType.translucent()};
    private static final int MAX_SECTIONS = 1500;
    private static final long REBUILD_MS = 30_000;

    static final class Section {
        final long key;
        final BlockPos origin;
        final VertexBuffer[] buffers = new VertexBuffer[LAYERS.length];
        long builtAt, used;
        boolean empty;

        Section(long key) {
            this.key = key;
            this.origin = SectionPos.of(key).origin();
        }

        void free() {
            for (int i = 0; i < buffers.length; i++) {
                if (buffers[i] != null) { try { buffers[i].close(); } catch (RuntimeException ignored) { } buffers[i] = null; }
            }
        }
    }

    private static final Map<Long, Section> SECTIONS = new HashMap<>();
    private static BufferBuilder[] builders;
    private static ClientLevel level;
    private static final RandomSource RANDOM = RandomSource.create();
    static int built;
    private static long calls;

    private FeedTerrain() {}

    static void clear() {
        for (Section s : SECTIONS.values()) s.free();
        SECTIONS.clear();
        level = null;
    }

    /**
     * The sections to draw for this image (built ones only), building the missing or old ones within {@code budgetNanos}, nearest
     * first.
     */
    static List<Section> sections(ClientLevel lv, Vec3 cam, Frustum frustum, double range, long budgetNanos) {
        if (lv != level) { clear(); level = lv; }
        long now = System.currentTimeMillis();
        int r = (int) Math.ceil(range / 16.0);
        int cx = BlockPos.containing(cam).getX() >> 4, cy = BlockPos.containing(cam).getY() >> 4, cz = BlockPos.containing(cam).getZ() >> 4;
        int minY = lv.getMinSection(), maxY = lv.getMaxSection() - 1;
        List<long[]> todo = new ArrayList<>();
        List<Section> out = new ArrayList<>();
        double r2 = (range + 16) * (range + 16);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                LevelChunk ch = lv.getChunkSource().getChunk(x, z, false);
                if (ch == null) continue;
                for (int y = Math.max(minY, cy - r); y <= Math.min(maxY, cy + r); y++) {
                    double dx = x * 16 + 8 - cam.x, dy = y * 16 + 8 - cam.y, dz = z * 16 + 8 - cam.z;
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > r2) continue;
                    LevelChunkSection sec = ch.getSection(lv.getSectionIndexFromSectionY(y));
                    if (sec == null || sec.hasOnlyAir()) continue;
                    if (!frustum.isVisible(new AABB(x * 16, y * 16, z * 16, x * 16 + 16, y * 16 + 16, z * 16 + 16))) continue;
                    long key = SectionPos.asLong(x, y, z);
                    Section s = SECTIONS.get(key);
                    if (s == null || now - s.builtAt > REBUILD_MS) todo.add(new long[] {key, (long) d2, s == null ? 0 : 1});
                    if (s != null) { s.used = now; if (!s.empty) out.add(s); }
                }
            }
        }
        // missing sections first, then the old ones; nearest first in each group
        todo.sort((a, b) -> a[2] != b[2] ? Long.compare(a[2], b[2]) : Long.compare(a[1], b[1]));
        long t0 = System.nanoTime();
        for (long[] t : todo) {
            if (System.nanoTime() - t0 > budgetNanos && built > 0) break;
            Section s = SECTIONS.get(t[0]);
            boolean fresh = s == null;
            if (fresh) { s = new Section(t[0]); SECTIONS.put(t[0], s); }
            build(lv, s);
            s.used = now;
            built++;
            if (fresh && !s.empty) out.add(s);
        }
        built = 0;
        if (SECTIONS.size() > MAX_SECTIONS) {
            List<Section> all = new ArrayList<>(SECTIONS.values());
            all.sort((a, b) -> Long.compare(a.used, b.used));
            for (int i = 0; i < all.size() - MAX_SECTIONS; i++) { all.get(i).free(); SECTIONS.remove(all.get(i).key); }
        }
        // forget the sections of chunks no longer loaded
        if (++calls % 100 == 0) {
            for (Iterator<Section> it = SECTIONS.values().iterator(); it.hasNext(); ) {
                Section s = it.next();
                if (lv.getChunkSource().getChunk(s.origin.getX() >> 4, s.origin.getZ() >> 4, false) == null) { s.free(); it.remove(); }
            }
        }
        return out;
    }

    private static void build(ClientLevel lv, Section s) {
        if (builders == null) {
            builders = new BufferBuilder[LAYERS.length];
            for (int i = 0; i < builders.length; i++) builders[i] = new BufferBuilder(1 << 16);
        }
        for (BufferBuilder b : builders) b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        BlockRenderDispatcher br = Minecraft.getInstance().getBlockRenderer();
        PoseStack pose = new PoseStack();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos o = s.origin;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    p.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                    BlockState state = lv.getBlockState(p);
                    if (state.isAir()) continue;
                    try {
                        FluidState fluid = state.getFluidState();
                        if (!fluid.isEmpty()) {
                            RenderType ft = ItemBlockRenderTypes.getRenderLayer(fluid);
                            br.renderLiquid(p, lv, builders[index(ft)], state, fluid);
                        }
                        if (state.getRenderShape() != RenderShape.MODEL) continue;
                        pose.pushPose();
                        pose.translate(x, y, z);
                        RANDOM.setSeed(state.getSeed(p));
                        if (drawer != null) drawer.draw(state, p.immutable(), lv, pose, rt -> builders[index(rt)], RANDOM);
                        else br.renderBatched(state, p, lv, pose, builders[index(ItemBlockRenderTypes.getChunkRenderType(state))], true, RANDOM);
                        pose.popPose();
                    } catch (RuntimeException e) {
                        // a block of another mod that cannot be drawn this way: left out of the camera image
                        pose = new PoseStack();
                    }
                }
            }
        }
        s.free();
        boolean any = false;
        for (int i = 0; i < builders.length; i++) {
            BufferBuilder.RenderedBuffer rb = builders[i].endOrDiscardIfEmpty();
            if (rb == null) continue;
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(rb);
            VertexBuffer.unbind();
            s.buffers[i] = vb;
            any = true;
        }
        s.empty = !any;
        s.builtAt = System.currentTimeMillis();
    }

    private static int index(RenderType t) {
        for (int i = 0; i < LAYERS.length; i++) if (LAYERS[i] == t) return i;
        return t == RenderType.tripwire() ? 3 : 0;
    }

    /** One layer of the built sections (the game's chunk shader of that layer, the offset of each section). */
    static void draw(int layer, List<Section> list, Matrix4f view, Matrix4f proj, Vec3 cam) {
        if (list.isEmpty()) return;
        RenderType type = LAYERS[layer];
        type.setupRenderState();
        ShaderInstance sh = RenderSystem.getShader();
        if (sh == null) { type.clearRenderState(); return; }
        for (int i = 0; i < 12; i++) sh.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        if (sh.MODEL_VIEW_MATRIX != null) sh.MODEL_VIEW_MATRIX.set(view);
        if (sh.PROJECTION_MATRIX != null) sh.PROJECTION_MATRIX.set(proj);
        if (sh.COLOR_MODULATOR != null) sh.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        if (sh.FOG_START != null) sh.FOG_START.set(RenderSystem.getShaderFogStart());
        if (sh.FOG_END != null) sh.FOG_END.set(RenderSystem.getShaderFogEnd());
        if (sh.FOG_COLOR != null) sh.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        if (sh.FOG_SHAPE != null) sh.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        if (sh.TEXTURE_MATRIX != null) sh.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        if (sh.GAME_TIME != null) sh.GAME_TIME.set(RenderSystem.getShaderGameTime());
        RenderSystem.setupShaderLights(sh);
        sh.apply();
        for (Section s : list) {
            VertexBuffer vb = s.buffers[layer];
            if (vb == null) continue;
            if (sh.CHUNK_OFFSET != null) {
                sh.CHUNK_OFFSET.set((float) (s.origin.getX() - cam.x), (float) (s.origin.getY() - cam.y), (float) (s.origin.getZ() - cam.z));
                sh.CHUNK_OFFSET.upload();
            }
            vb.bind();
            vb.draw();
        }
        if (sh.CHUNK_OFFSET != null) sh.CHUNK_OFFSET.set(0f, 0f, 0f);
        sh.clear();
        VertexBuffer.unbind();
        type.clearRenderState();
    }

    static int count() { return SECTIONS.size(); }
}
