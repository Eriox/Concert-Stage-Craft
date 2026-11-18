package com.concertstage.audio.core;

import java.util.*;

/** Relative full-range coverage estimate, not measured SPL. Inputs contain no world objects. */
public final class DelayCoverage {
  public static final double SPEED = 343;

  public record Source(
      double x,
      double y,
      double z,
      double yaw,
      double top,
      double bottom,
      double horizontal,
      double vertical,
      double rear,
      DistanceCurve curve,
      double level,
      boolean main,
      boolean attenuate) {
    public double gain(double px, double py, double pz) {
      double dx = px - x,
          dy = py - y,
          dz = pz - z,
          flat = Math.hypot(dx, dz),
          r = Math.toRadians(yaw);
      double cosine = flat < 1e-6 ? 1 : (-Math.sin(r) * dx + Math.cos(r) * dz) / flat;
      return (attenuate ? curve.gain(Math.sqrt(dx * dx + dy * dy + dz * dz)) : curve.output())
          * Acoustics.directivity(
              false,
              Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosine)))),
              Math.toDegrees(Math.atan2(-dy, Math.max(.5, flat))),
              horizontal,
              vertical,
              top,
              bottom,
              rear)
          * level;
    }
  }

  public record Grid(
      int x,
      int z,
      int step,
      int columns,
      int rows,
      double[] ground,
      float[][] transmission,
      int budget) {
    public Grid {
      if (columns < 1
          || rows < 1
          || columns * rows > 4096
          || step < 1
          || ground.length != columns * rows) throw new IllegalArgumentException("grid");
      ground = ground.clone();
      float[][] copy = new float[transmission.length][];
      for (int i = 0; i < copy.length; i++) {
        if (transmission[i].length != ground.length)
          throw new IllegalArgumentException("transmission");
        copy[i] = transmission[i].clone();
      }
      transmission = copy;
      budget = Math.max(1, budget);
    }

    public int index(double px, double pz) {
      int c = (int) Math.round((px - x) / step), r = (int) Math.round((pz - z) / step);
      return c < 0 || r < 0 || c >= columns || r >= rows ? -1 : r * columns + c;
    }

    public double px(int i) {
      return x + (i % columns) * step;
    }

    public double pz(int i) {
      return z + (i / columns) * step;
    }

    public boolean known(int i) {
      return i >= 0 && i < ground.length && Double.isFinite(ground[i]);
    }
  }

  public record Tower(
      double x,
      double ground,
      double z,
      double height,
      double yaw,
      int modules,
      String model,
      int group,
      double refX,
      double refY,
      double refZ,
      double delayMs,
      boolean enabled,
      Source template,
      double cabinetHeight) {
    public double pitch() {
      return Math.max(
          -15,
          Math.min(
              15,
              Math.toDegrees(Math.atan2(ground + height - refY, Math.hypot(refX - x, refZ - z)))));
    }

    public Tower move(Grid g, List<Source> sources, double nx, double nz, double nh, int count) {
      nh = Math.max(nh, (count - count / 2 - .5) * cabinetHeight + 1);
      int cell = g.index(nx, nz);
      double floor = g.known(cell) ? g.ground[cell] : ground;
      return new Tower(
          nx,
          floor,
          nz,
          nh,
          Math.toDegrees(Math.atan2(-(refX - nx), refZ - nz)),
          count,
          model,
          group,
          refX,
          refY,
          refZ,
          alignment(sources, nx, floor + nh, nz, refX, refY, refZ),
          enabled,
          template,
          cabinetHeight);
    }

    public Tower reference(List<Source> sources, double x, double y, double z) {
      return new Tower(
          this.x,
          ground,
          this.z,
          height,
          Math.toDegrees(Math.atan2(-(x - this.x), z - this.z)),
          modules,
          model,
          group,
          x,
          y,
          z,
          alignment(sources, this.x, ground + height, this.z, x, y, z),
          enabled,
          template,
          cabinetHeight);
    }
  }

  public record Result(
      Grid grid, List<Source> sources, float[] baseline, float[] predicted, List<Tower> towers) {
    public Result {
      sources = List.copyOf(sources);
      towers = List.copyOf(towers);
    }
  }

  private DelayCoverage() {}

  private static double sq(double d) {
    return d * d;
  }

  public static double alignment(
      List<Source> sources, double x, double y, double z, double refX, double refY, double refZ) {
    Source chosen = null;
    double best = -1;
    for (var s : sources)
      if (s.main) {
        double gain = s.gain(refX, refY, refZ);
        if (gain > best) {
          best = gain;
          chosen = s;
        }
      }
    if (chosen == null) return Double.NaN;
    return (Math.sqrt(sq(refX - chosen.x) + sq(refY - chosen.y) + sq(refZ - chosen.z))
            - Math.sqrt(sq(refX - x) + sq(refY - y) + sq(refZ - z)))
        * 1000
        / SPEED;
  }

  public static Result analyze(
      Grid grid,
      List<Source> sources,
      double weak,
      DistanceCurve delayCurve,
      String model,
      Source template,
      double cabinetHeight) {
    return analyze(
        grid,
        sources,
        weak,
        delayCurve,
        model,
        List.of(template, template, template),
        cabinetHeight);
  }

  public static Result analyze(
      Grid grid,
      List<Source> sources,
      double weak,
      DistanceCurve delayCurve,
      String model,
      List<Source> templates,
      double cabinetHeight) {
    if (templates.size() != 3) throw new IllegalArgumentException("three proposal bus templates");
    if (sources.isEmpty() || grid.transmission.length != sources.size())
      throw new IllegalArgumentException("sources");
    float[] base = coverage(grid, sources, List.of(), delayCurve);
    List<Tower> towers = new ArrayList<>();
    for (int n = 0; n < 3; n++) {
      float[] current = coverage(grid, sources, towers, delayCurve);
      int candidate = -1;
      double best = 0;
      for (int i = 0; i < current.length; i++)
        if (grid.known(i) && Float.isFinite(current[i]) && current[i] < weak) {
          double separation = Double.POSITIVE_INFINITY;
          for (var t : towers)
            separation = Math.min(separation, Math.hypot(grid.px(i) - t.x, grid.pz(i) - t.z));
          if (separation < 24) continue;
          double score = weak - current[i];
          int known = 0;
          for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) {
              int j = grid.index(grid.px(i) + dx * grid.step, grid.pz(i) + dz * grid.step);
              if (grid.known(j)) {
                known++;
                score += Math.max(0, weak - current[j]);
              }
            }
          if (known < 4) continue;
          double distance = Double.POSITIVE_INFINITY;
          for (var s : sources)
            if (s.main)
              distance = Math.min(distance, Math.hypot(grid.px(i) - s.x, grid.pz(i) - s.z));
          if (distance < 20) continue;
          score *= Math.min(1, distance / 60);
          if (score > best) {
            best = score;
            candidate = i;
          }
        }
      if (candidate < 0) break;
      double rx = grid.px(candidate), rz = grid.pz(candidate), ry = grid.ground[candidate] + 1.6;
      Source main =
          sources.stream()
              .filter(Source::main)
              .max(Comparator.comparingDouble(s -> s.gain(rx, ry, rz)))
              .orElse(null);
      if (main == null) break;
      double dx = rx - main.x, dz = rz - main.z, len = Math.max(1, Math.hypot(dx, dz));
      double
          x =
              Math.max(
                  grid.x, Math.min(grid.x + (grid.columns - 1) * grid.step, rx - dx / len * 12)),
          z = Math.max(grid.z, Math.min(grid.z + (grid.rows - 1) * grid.step, rz - dz / len * 12));
      int idx = grid.index(x, z);
      if (!grid.known(idx)) break;
      towers.add(
          new Tower(
              x,
              grid.ground[idx],
              z,
              6,
              Math.toDegrees(Math.atan2(-(rx - x), rz - z)),
              4,
              model,
              3 + n,
              rx,
              ry,
              rz,
              alignment(sources, x, grid.ground[idx] + 6, z, rx, ry, rz),
              true,
              templates.get(n),
              cabinetHeight));
    }
    return new Result(grid, sources, base, coverage(grid, sources, towers, delayCurve), towers);
  }

  public static Result update(Result before, List<Tower> towers, DistanceCurve curve) {
    return new Result(
        before.grid,
        before.sources,
        before.baseline,
        coverage(before.grid, before.sources, towers, curve),
        towers);
  }

  private static float[] coverage(
      Grid g, List<Source> sources, List<Tower> towers, DistanceCurve curve) {
    float[] values = new float[g.ground.length];
    int active = (int) towers.stream().filter(Tower::enabled).count();
    for (int i = 0; i < values.length; i++) {
      if (Thread.currentThread().isInterrupted())
        throw new java.util.concurrent.CancellationException();
      if (!g.known(i)) {
        values[i] = Float.NaN;
        continue;
      }
      double x = g.px(i), z = g.pz(i), y = g.ground[i] + 1.6, sum = 0;
      for (int s = 0; s < sources.size(); s++)
        sum += sources.get(s).gain(x, y, z) * g.transmission[s][i];
      for (var t : towers)
        if (t.enabled) {
          var a = t.template;
          double range =
              curve.maximum() * Math.min(192, 112 + t.modules * t.cabinetHeight * 12) / 192;
          var s =
              new Source(
                  t.x,
                  t.ground + t.height,
                  t.z,
                  t.yaw,
                  t.pitch(),
                  t.pitch(),
                  a.horizontal,
                  a.vertical,
                  a.rear,
                  new DistanceCurve(curve.reference(), range, curve.rolloff(), curve.output()),
                  a.level,
                  false,
                  a.attenuate);
          sum += s.gain(x, y, z) * terrainTransmission(g, s.x, s.y, s.z, x, y, z);
        }
      values[i] = (float) (sum / (g.budget + active));
    }
    return values;
  }

  private static double terrainTransmission(
      Grid g, double x, double y, double z, double toX, double toY, double toZ) {
    int count = Math.max(1, (int) (Math.hypot(toX - x, toZ - z) / g.step));
    for (int i = 1; i < count; i++) {
      double f = i / (double) count;
      int cell = g.index(x + (toX - x) * f, z + (toZ - z) * f);
      if (g.known(cell) && g.ground[cell] > y + (toY - y) * f - .2) return .2;
    }
    return 1;
  }
}
