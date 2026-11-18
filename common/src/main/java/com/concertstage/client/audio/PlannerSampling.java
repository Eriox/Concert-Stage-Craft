package com.concertstage.client.audio;

import com.concertstage.audio.*;
import com.concertstage.audio.core.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Budgeted world reads on the game thread. Only numeric arrays go to the background solver. */
final class PlannerSampling {
  final DelayCoverage.Grid grid;
  final List<DelayCoverage.Source> sources;
  int done, loaded;
  private int sourceCursor;
  private final int baseY;

  PlannerSampling(
      int[] zone, int requestedStep, AudioConsoleBlockEntity console, Set<BlockPos> mains) {
    var mc = Minecraft.getInstance();
    int dx = zone[2] - zone[0], dz = zone[3] - zone[1];
    if (dx < 1 || dz < 1 || dx > 512 || dz > 512)
      throw new IllegalArgumentException("Audience edges: 1..512 blocks");
    int step = Math.max(requestedStep, (int) Math.ceil(Math.max(dx, dz) / 39.0)),
        cols = dx / step + 1,
        rows = dz / step + 1;
    baseY = zone[4];
    List<DelayCoverage.Source> data = new ArrayList<>();
    for (BlockPos p : console.speakers()) {
      if (!(mc.level.getBlockEntity(p) instanceof SpeakerBlockEntity s)
          || s.firstModel() == null
          || s.cabinets().isEmpty()) continue;
      loaded++;
      var m = s.firstModel();
      if (m.isSub()) continue;
      var axis = s.acousticAxis();
      var poses = s.poses();
      double length = poses.stream().mapToDouble(v -> v.model().height()).sum();
      String key =
          console.groupDelay(s.group()) > 0
              ? "delay"
              : s.group() == 3 ? "front_fill" : s.group() == 4 ? "delay" : m.family();
      var c = AudioSettings.distanceCurve;
      var curve =
          new DistanceCurve(
              c.reference(),
              c.maximum()
                  * Acoustics.range(false, length)
                  / 192
                  * AudioSettings.rangeFactors.getOrDefault(key, 1.0),
              c.rolloff(),
              c.output());
      double level =
          console.muted(s.group()) ? 0 : console.master() * console.fader(s.group()) * s.volume();
      data.add(
          new DelayCoverage.Source(
              axis[0].x,
              axis[0].y,
              axis[0].z,
              s.yaw(),
              poses.get(0).pitch(),
              poses.get(poses.size() - 1).pitch(),
              AudioSettings.directionality ? m.hDispersion() : 360,
              AudioSettings.directionality ? m.vDispersion() : 360,
              AudioSettings.directionality ? m.rear() : 1,
              curve,
              level,
              mains.contains(p),
              AudioSettings.distanceAttenuation));
    }
    if (data.stream().noneMatch(DelayCoverage.Source::main))
      throw new IllegalArgumentException("Select a loaded main array");
    sources = List.copyOf(data);
    double[] ground = new double[cols * rows];
    Arrays.fill(ground, Double.NaN);
    grid =
        new DelayCoverage.Grid(
            zone[0],
            zone[1],
            step,
            cols,
            rows,
            ground,
            new float[data.size()][ground.length],
            console.speakers().size());
  }

  boolean tick() {
    long stop = System.nanoTime() + 2_000_000;
    int count = 0;
    while (done < grid.ground().length && count++ < 64) {
      sample(done);
      if (++sourceCursor >= sources.size() || !grid.known(done)) {
        done++;
        sourceCursor = 0;
      }
      if (System.nanoTime() > stop) break;
    }
    return done == grid.ground().length;
  }

  private void sample(int i) {
    var mc = Minecraft.getInstance();
    var level = mc.level;
    if (level == null) return;
    int x = (int) grid.px(i), z = (int) grid.pz(i);
    if (!level.hasChunkAt(new BlockPos(x, baseY, z))) return;
    double y = grid.ground()[i];
    if (sourceCursor == 0) {
      y = Double.NaN;
      for (int by = Math.min(level.getMaxBuildHeight() - 1, baseY + 12);
          by >= Math.max(level.getMinBuildHeight(), baseY - 48);
          by--) {
        var p = new BlockPos(x, by, z);
        var state = level.getBlockState(p);
        var shape = state.getCollisionShape(level, p);
        if (!shape.isEmpty() && state.isFaceSturdy(level, p, Direction.UP)) {
          y = by + shape.max(Direction.Axis.Y);
          break;
        }
      }
      if (!Double.isFinite(y)) return;
      grid.ground()[i] = y;
    }
    Vec3 ear = new Vec3(x, y + 1.6, z);
    var s = sources.get(sourceCursor);
    Vec3 from = new Vec3(s.x(), s.y(), s.z());
    if (from.distanceTo(ear) < 3) {
      grid.transmission()[sourceCursor][i] = 1;
      return;
    }
    Vec3 dir = ear.subtract(from).normalize(), start = from.add(dir.scale(2.5));
    for (double d = 0; d < start.distanceTo(ear); d += 16)
      if (!level.hasChunkAt(BlockPos.containing(start.add(dir.scale(d))))) {
        grid.ground()[i] = Double.NaN;
        return;
      }
    float transmission = 1;
    for (int k = 0; k < 12; k++) {
      var hit =
          level.clip(
              new ClipContext(
                  start, ear, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
      if (hit.getType() == HitResult.Type.MISS) break;
      if (level.getBlockState(hit.getBlockPos()).isSolidRender(level, hit.getBlockPos())) {
        transmission = .2f;
        break;
      }
      start = hit.getLocation().add(dir.scale(.55));
      if (start.distanceTo(ear) < .6) break;
    }
    grid.transmission()[sourceCursor][i] = transmission;
  }
}
