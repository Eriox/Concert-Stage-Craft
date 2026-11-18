package com.concertstage.client.light;

import com.concertstage.ConcertStage;
import com.concertstage.block.MultiPartBlock;
import com.concertstage.light.FixtureBlock;
import com.concertstage.light.FixtureBlockEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rev. 18 — how a hung fixture is fixed to what is above it (client, drawing only; the block and its hitbox do not change).
 *
 * <p>For the 42 straight trusses of the mod (box, triangle, ladder, H1/H2/petit, corners), the real tubes of the model drawn in the
 * world are known ({@code lightshow/truss_tubes.json}, written by {@code tools/rev18/truss_tubes.py} from the OBJ of each facing, and
 * {@code lightshow/truss_blocks.json}: block → model of its part-0 cell per facing). For every other support (motor towers, roof and
 * ring trusses, stage towers, FOH lattice, any block of another mod), the tubes are read from the outline shape of the block above.
 * {@link MountGeometry#plan} then picks the hardware: a hanging bar with two half couplers across two chords, an omega bracket with two
 * hook clamps on a single tube, or a bracket plate under a flat face.
 *
 * <p>Cost: the plan is made once per fixture and checked again every second (or when the block above changes); the hardware mesh is
 * shared by every fixture with the same layout and kept in the graphics card ({@link FixtureMeshes#drawMount}).
 */
public final class FixtureMount {
    private static Map<String, List<MountGeometry.Tube>> tubes;
    private static Map<String, String[]> blocks;
    private static final Map<String, MountGeometry.Layout> SHARED = new HashMap<>();
    private static final MountGeometry.Layout NONE = new MountGeometry.Layout(MountGeometry.NONE, 1f, 0, 0, List.of(), List.of(), "none");

    private FixtureMount() {}

    /** Resource reload (with {@link FixtureMeshes#clear}). */
    public static void clear() {
        tubes = null;
        blocks = null;
        SHARED.clear();
    }

    /** The hardware of a fixture, from its cache (checked again every 20 ticks or when the block above is replaced). */
    public static MountGeometry.Layout of(FixtureBlockEntity be, Level level, float scale) {
        BlockState fixture = be.getBlockState();
        if (!fixture.getValue(FixtureBlock.HUNG)) return NONE;
        BlockPos above = be.getBlockPos().above();
        BlockState support = level.getBlockState(above);
        long now = level.getGameTime();
        if (be.mount instanceof MountGeometry.Layout cached && be.mountSupport == support && now - be.mountTime < 20 && now >= be.mountTime) return cached;
        MountGeometry.Layout l;
        try {
            l = plan(level, above, support, scale);
        } catch (RuntimeException e) {
            l = NONE;                                   // a support of another mod that cannot answer: the fixture as before
        }
        be.mount = l;
        be.mountSupport = support;
        be.mountTime = now;
        return l;
    }

    private static MountGeometry.Layout plan(Level level, BlockPos above, BlockState support, float scale) {
        if (support.isAir()) return NONE;
        List<MountGeometry.Tube> list = new ArrayList<>();
        float plate = Float.NaN;
        String known = knownModel(support);
        if (known != null && support.getBlock() instanceof MultiPartBlock mp) {
            // the tubes of the truss model, from its part-0 cell to the fixture's cell (the fixture is one block below `above`)
            BlockPos p0 = mp.cellPos(support, above, 0);
            float dx = p0.getX() - above.getX(), dy = p0.getY() - above.getY() + 1, dz = p0.getZ() - above.getZ();
            for (MountGeometry.Tube t : tubes().getOrDefault(known, List.of())) {
                float dc = t.axis() == 0 ? dz : dx, da = t.axis() == 0 ? dx : dz;
                list.add(new MountGeometry.Tube(t.axis(), t.yMin() + dy, t.yMax() + dy, t.cMin() + dc, t.cMax() + dc, t.aMin() + da, t.aMax() + da));
            }
        } else {
            // any other support: its outline, box by box (tubes are the long thin boxes), and its lowest face over the fixture
            VoxelShape shape = support.getShape(level, above);
            if (shape.isEmpty()) return NONE;
            for (AABB b : shape.toAabbs()) {
                float x0 = (float) b.minX, x1 = (float) b.maxX, y0 = (float) b.minY + 1, y1 = (float) b.maxY + 1, z0 = (float) b.minZ, z1 = (float) b.maxZ;
                float ex = x1 - x0, ey = y1 - y0, ez = z1 - z0;
                if (ey <= 0.3f && ex >= 0.35f && ez <= 0.3f) list.add(new MountGeometry.Tube(0, y0, y1, z0, z1, x0, x1));
                else if (ey <= 0.3f && ez >= 0.35f && ex <= 0.3f) list.add(new MountGeometry.Tube(2, y0, y1, x0, x1, z0, z1));
                if (x0 <= 0.5f && x1 >= 0.5f && z0 <= 0.5f && z1 >= 0.5f && (Float.isNaN(plate) || y0 < plate)) plate = y0;
            }
        }
        MountGeometry.Layout l = MountGeometry.plan(list, plate, scale);
        if (l.kind() == MountGeometry.NONE) return NONE;
        // one instance per layout (the key holds every number of its geometry): the GPU mesh is shared
        MountGeometry.Layout shared = SHARED.get(l.key());
        if (shared != null) return shared;
        if (SHARED.size() > 512) SHARED.clear();
        SHARED.put(l.key(), l);
        return l;
    }

    private static String knownModel(BlockState s) {
        if (!(s.getBlock() instanceof MultiPartBlock) || !s.hasProperty(MultiPartBlock.FACING)) return null;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
        if (id == null || !ConcertStage.MOD_ID.equals(id.getNamespace())) return null;
        String[] byFacing = blocks().get(id.getPath());
        if (byFacing == null) return null;
        int i = switch (s.getValue(MultiPartBlock.FACING)) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        return byFacing[i];
    }

    private static Map<String, List<MountGeometry.Tube>> tubes() {
        if (tubes == null) {
            Map<String, List<MountGeometry.Tube>> m = new HashMap<>();
            JsonObject root = read("truss_tubes");
            if (root != null) {
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    List<MountGeometry.Tube> l = new ArrayList<>();
                    for (JsonElement t : e.getValue().getAsJsonArray()) {
                        JsonArray a = t.getAsJsonArray();
                        l.add(new MountGeometry.Tube(a.get(0).getAsInt(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat(),
                            a.get(4).getAsFloat(), a.get(5).getAsFloat(), a.get(6).getAsFloat()));
                    }
                    m.put(e.getKey(), l);
                }
            }
            tubes = m;
        }
        return tubes;
    }

    private static Map<String, String[]> blocks() {
        if (blocks == null) {
            Map<String, String[]> m = new HashMap<>();
            JsonObject root = read("truss_blocks");
            if (root != null) {
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    JsonArray a = e.getValue().getAsJsonArray();
                    String[] f = new String[4];
                    for (int i = 0; i < 4 && i < a.size(); i++) f[i] = a.get(i).isJsonNull() ? null : a.get(i).getAsString();
                    m.put(e.getKey(), f);
                }
            }
            blocks = m;
        }
        return blocks;
    }

    private static JsonObject read(String name) {
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(ConcertStage.MOD_ID, "lightshow/" + name + ".json"));
            if (res.isEmpty()) return null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: {} could not be read, fixtures hang as before", name, e);
            return null;
        }
    }
}
