package com.concertstage.audio;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;

/** Whitelist persisted planner metadata. This class never loads chunks or places blocks. */
public final class PlannerState {
  private PlannerState() {}

  public static CompoundTag clean(
      CompoundTag input, BlockPos console, Level level, List<BlockPos> linked) {
    int[] z = input.getIntArray("Zone");
    if (z.length != 5
        || z[0] > z[2]
        || z[1] > z[3]
        || (long) z[2] - z[0] > 512
        || (long) z[3] - z[1] > 512) return null;
    if (Math.abs((long) z[0] - console.getX()) > 512
        || Math.abs((long) z[2] - console.getX()) > 512
        || Math.abs((long) z[1] - console.getZ()) > 512
        || Math.abs((long) z[3] - console.getZ()) > 512) return null;
    if (level != null && (z[4] < level.getMinBuildHeight() || z[4] >= level.getMaxBuildHeight()))
      return null;
    CompoundTag out = new CompoundTag();
    out.putIntArray("Zone", z);
    int[] stage = input.getIntArray("Stage");
    if (stage.length == 3
        && Math.abs((long) stage[0] - console.getX()) <= 512
        && Math.abs((long) stage[2] - console.getZ()) <= 512
        && (level == null
            || (stage[1] >= level.getMinBuildHeight() && stage[1] < level.getMaxBuildHeight())))
      out.putIntArray("Stage", stage);
    long[] ids = input.getLongArray("Mains");
    if (ids.length > 64) return null;
    List<Long> mains = new ArrayList<>();
    for (long id : ids) if (mains.size() < 64 && linked.contains(BlockPos.of(id))) mains.add(id);
    out.putLongArray("Mains", mains);
    ListTag towers = new ListTag(), list = input.getList("Towers", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(3, list.size()); i++) {
      var t = list.getCompound(i);
      var m = SpeakerModels.get(t.getString("Model"));
      if (m == null || m.isSub()) continue;
      double x = t.getDouble("X"),
          pz = t.getDouble("Z"),
          h = t.getDouble("Height"),
          yaw = t.getDouble("Yaw"),
          rx = t.getDouble("RefX"),
          rz = t.getDouble("RefZ");
      if (!Double.isFinite(x + pz + h + yaw + rx + rz)
          || x < z[0]
          || x > z[2]
          || pz < z[1]
          || pz > z[3]) continue;
      int modules = Math.max(1, Math.min(12, t.getInt("Modules")));
      double minimumHeight = Math.max(3, (modules - modules / 2 - .5) * m.height() + 1);
      CompoundTag c = new CompoundTag();
      c.putDouble("X", x);
      c.putDouble("Z", pz);
      c.putDouble("Height", Math.max(minimumHeight, Math.min(24, h)));
      c.putDouble("Yaw", Math.IEEEremainder(yaw, 360));
      c.putDouble("RefX", Math.max(z[0], Math.min(z[2], rx)));
      c.putDouble("RefZ", Math.max(z[1], Math.min(z[3], rz)));
      c.putString("Model", m.id());
      c.putInt("Modules", modules);
      c.putInt("Group", Math.max(0, Math.min(5, t.getInt("Group"))));
      c.putBoolean("Enabled", !t.contains("Enabled") || t.getBoolean("Enabled"));
      String name = t.getString("Name");
      StringBuilder safe = new StringBuilder();
      for (int j = 0; j < name.length() && safe.length() < 32; j++)
        if (!Character.isISOControl(name.charAt(j))) safe.append(name.charAt(j));
      c.putString("Name", safe.toString());
      double delay = t.getDouble("Delay");
      if (t.contains("Delay") && Double.isFinite(delay) && delay >= 0 && delay <= 1500)
        c.putDouble("Delay", delay);
      towers.add(c);
    }
    out.put("Towers", towers);
    return out;
  }
}
