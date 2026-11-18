package com.concertstage.fabric.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The block and item models of the mod are OBJ meshes declared with Forge's {@code "loader": "forge:obj"} JSON. Fabric has no OBJ loader:
 * this reads the same JSON, OBJ and MTL files (nothing is duplicated) and gives the game baked models ({@link ObjUnbakedModel}). Item
 * models that only take an OBJ block model as parent (with their own display) are handled too.
 */
public final class FabricObjModels {
    private FabricObjModels() {}

    /** One OBJ model: its JSON (textures, options, display) and the parsed mesh. */
    record Entry(JsonObject json, ObjMesh mesh) {}

    public static void register() {
        PreparableModelLoadingPlugin.register(FabricObjModels::load, (data, plugin) ->
            plugin.resolveModel().register(ctx -> {
                Entry e = data.get(ctx.id());
                return e == null ? null : new ObjUnbakedModel(e.json(), e.mesh());
            }));
    }

    private static CompletableFuture<Map<ResourceLocation, Entry>> load(ResourceManager manager, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            Map<ResourceLocation, JsonObject> jsons = new HashMap<>();
            for (Map.Entry<ResourceLocation, Resource> r : manager.listResources("models",
                id -> id.getNamespace().equals("concertstage") && id.getPath().endsWith(".json")).entrySet()) {
                try (Reader reader = r.getValue().openAsReader()) {
                    JsonElement el = JsonParser.parseReader(reader);
                    if (!el.isJsonObject()) continue;
                    String p = r.getKey().getPath();
                    jsons.put(new ResourceLocation(r.getKey().getNamespace(), p.substring("models/".length(), p.length() - ".json".length())), el.getAsJsonObject());
                } catch (IOException | RuntimeException ignored) {
                    // unreadable model: left to the vanilla loader (missing model)
                }
            }
            Map<ResourceLocation, Entry> out = new HashMap<>();
            Map<ResourceLocation, ObjMesh> meshes = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonObject> m : jsons.entrySet()) {
                JsonObject j = m.getValue();
                if (!j.has("loader") || !"forge:obj".equals(j.get("loader").getAsString()) || !j.has("model")) continue;
                ResourceLocation obj = new ResourceLocation(j.get("model").getAsString());
                ObjMesh mesh = meshes.computeIfAbsent(obj, k -> ObjMesh.read(manager, k));
                if (mesh != null) out.put(m.getKey(), new Entry(j, mesh));
            }
            // item models that only point at an OBJ block model, with their own display transforms
            for (Map.Entry<ResourceLocation, JsonObject> m : jsons.entrySet()) {
                JsonObject j = m.getValue();
                if (out.containsKey(m.getKey()) || !j.has("parent")) continue;
                Entry parent = out.get(new ResourceLocation(j.get("parent").getAsString()));
                if (parent == null) continue;
                JsonObject merged = parent.json().deepCopy();
                for (Map.Entry<String, JsonElement> kv : j.entrySet()) if (!kv.getKey().equals("parent")) merged.add(kv.getKey(), kv.getValue());
                out.put(m.getKey(), new Entry(merged, parent.mesh()));
            }
            return out;
        }, executor);
    }

    /** Faces of an OBJ file by material, with the texture key of each material (map_Kd #key of the MTL). */
    static final class ObjMesh {
        /** Per face: positions (x, y, z) then uvs (u, v) then the normal, 3 or 4 corners. */
        record Face(float[][] pos, float[][] uv, float[] normal, String texture) {}
        final List<Face> faces = new ArrayList<>();

        static ObjMesh read(ResourceManager manager, ResourceLocation id) {
            try {
                Resource res = manager.getResource(id).orElse(null);
                if (res == null) return null;
                List<float[]> v = new ArrayList<>(), vt = new ArrayList<>(), vn = new ArrayList<>();
                Map<String, String> materials = new HashMap<>();
                String material = null;
                ObjMesh out = new ObjMesh();
                String dir = id.getPath().contains("/") ? id.getPath().substring(0, id.getPath().lastIndexOf('/') + 1) : "";
                try (BufferedReader r = res.openAsReader()) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.charAt(0) == '#') continue;
                        String[] t = line.split("\\s+");
                        switch (t[0]) {
                            case "v" -> v.add(new float[] {f(t[1]), f(t[2]), f(t[3])});
                            case "vt" -> vt.add(new float[] {f(t[1]), t.length > 2 ? f(t[2]) : 0});
                            case "vn" -> vn.add(new float[] {f(t[1]), f(t[2]), f(t[3])});
                            case "mtllib" -> readMtl(manager, new ResourceLocation(id.getNamespace(), dir + t[1]), materials);
                            case "usemtl" -> material = t.length > 1 ? t[1] : null;
                            case "f" -> {
                                int n = t.length - 1;
                                int[][] idx = new int[n][3];
                                for (int i = 0; i < n; i++) {
                                    String[] p = t[i + 1].split("/", -1);
                                    idx[i][0] = index(p[0], v.size());
                                    idx[i][1] = p.length > 1 && !p[1].isEmpty() ? index(p[1], vt.size()) : -1;
                                    idx[i][2] = p.length > 2 && !p[2].isEmpty() ? index(p[2], vn.size()) : -1;
                                }
                                String tex = material == null ? null : materials.getOrDefault(material, null);
                                for (int k = 1; k + 1 < n; k += 2) {               // quads as they are, larger faces in quads/triangles
                                    int c = Math.min(3, n - k);
                                    int[] corners = c == 3 ? new int[] {0, k, k + 1, k + 2} : new int[] {0, k, k + 1};
                                    float[][] pos = new float[corners.length][], uv = new float[corners.length][];
                                    for (int i = 0; i < corners.length; i++) {
                                        int[] q = idx[corners[i]];
                                        pos[i] = v.get(q[0]);
                                        uv[i] = q[1] >= 0 ? vt.get(q[1]) : new float[] {0, 0};
                                    }
                                    float[] normal = idx[0][2] >= 0 ? vn.get(idx[0][2]) : null;
                                    out.faces.add(new Face(pos, uv, normal, tex));
                                    if (c == 2) break;
                                }
                            }
                            default -> { }
                        }
                    }
                }
                return out;
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }

        private static void readMtl(ResourceManager manager, ResourceLocation id, Map<String, String> out) {
            Resource res = manager.getResource(id).orElse(null);
            if (res == null) return;
            try (BufferedReader r = res.openAsReader()) {
                String line, current = null;
                while ((line = r.readLine()) != null) {
                    String[] t = line.trim().split("\\s+");
                    if (t[0].equals("newmtl") && t.length > 1) current = t[1];
                    else if (t[0].equals("map_Kd") && t.length > 1 && current != null) out.put(current, t[1]);
                }
            } catch (IOException ignored) {
                // no materials: the particle texture is used
            }
        }

        private static float f(String s) { return Float.parseFloat(s); }
        private static int index(String s, int size) { int i = Integer.parseInt(s); return i < 0 ? size + i : i - 1; }
    }
}
