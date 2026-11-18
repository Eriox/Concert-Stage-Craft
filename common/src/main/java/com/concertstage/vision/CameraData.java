package com.concertstage.vision;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** CSC 1.3.0 - the cameras of a dimension (position, model, number, name), for the regie lists, saved in the world. */
public class CameraData extends SavedData {
    public static final String NAME = "csc_livevision_cameras";

    public record Cam(long pos, int kind, int number, String name) {}

    public final Map<Long, Cam> cams = new LinkedHashMap<>();
    private ServerLevel level;

    public static CameraData get(ServerLevel level) {
        CameraData d = level.getDataStorage().computeIfAbsent(CameraData::load, CameraData::new, NAME);
        d.level = level;
        return d;
    }

    public static CameraData load(CompoundTag tag) {
        CameraData d = new CameraData();
        for (Tag t : tag.getList("Cams", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.cams.put(c.getLong("Pos"), new Cam(c.getLong("Pos"), c.getInt("Kind"), c.getInt("Number"), c.getString("Name")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Cam c : cams.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", c.pos); t.putInt("Kind", c.kind); t.putInt("Number", c.number); t.putString("Name", c.name);
            l.add(t);
        }
        tag.put("Cams", l);
        return tag;
    }

    public int nextNumber() {
        int n = 1;
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Cam c : cams.values()) used.add(c.number);
        while (used.contains(n)) n++;
        return n;
    }

    public void update(CameraBlockEntity be) {
        Cam c = new Cam(be.getBlockPos().asLong(), be.kind().ordinal(), be.number, be.name);
        if (c.equals(cams.get(c.pos))) return;
        cams.put(c.pos, c);
        setDirty();
        if (level != null) VisionNet.camerasChanged(level);
    }

    public void remove(ServerLevel level, BlockPos pos) {
        if (cams.remove(pos.asLong()) != null) {
            setDirty();
            VisionNet.camerasChanged(level);
        }
    }
}
