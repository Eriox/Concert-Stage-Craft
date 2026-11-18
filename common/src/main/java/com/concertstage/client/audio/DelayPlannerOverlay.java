package com.concertstage.client.audio;

import com.concertstage.audio.SpeakerModels;
import com.concertstage.audio.core.DelayCoverage;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;

/** Translucent, non-interactive preview. Never creates world blocks or entities. */
public final class DelayPlannerOverlay {
  private static ClientLevel level;
  private static DelayCoverage.Result result;
  private static List<DelayCoverage.Tower> towers = List.of();
  private static int[] stage;
  private static double green, yellow, orange;
  private static boolean heatmap;

  public static void show(
      ClientLevel world,
      DelayCoverage.Result r,
      List<DelayCoverage.Tower> t,
      int[] pin,
      double g,
      double y,
      double o) {
    level = world;
    result = r;
    towers = List.copyOf(t);
    stage = pin.clone();
    green = g;
    yellow = y;
    orange = o;
  }

  public static void clear() {
    level = null;
    result = null;
    towers = List.of();
  }

  public static void heatmap(boolean on) {
    heatmap = on;
  }

  public static boolean heatmap() {
    return heatmap;
  }

  public static RenderType type() {
    return RenderType.entityTranslucent(SpeakerMeshes.ATLAS);
  }

  private static final class Tint implements VertexConsumer {
    final VertexConsumer out;
    final int r, g, b, a;

    Tint(VertexConsumer out, int r, int g, int b, int a) {
      this.out = out;
      this.r = r;
      this.g = g;
      this.b = b;
      this.a = a;
    }

    public VertexConsumer vertex(double x, double y, double z) {
      out.vertex(x, y, z);
      return this;
    }

    public VertexConsumer color(int r, int g, int b, int a) {
      out.color(this.r, this.g, this.b, this.a);
      return this;
    }

    public VertexConsumer uv(float u, float v) {
      out.uv(920 / 1024f, 328 / 1024f);
      return this;
    }

    public VertexConsumer overlayCoords(int u, int v) {
      out.overlayCoords(u, v);
      return this;
    }

    public VertexConsumer uv2(int u, int v) {
      out.uv2(u, v);
      return this;
    }

    public VertexConsumer normal(float x, float y, float z) {
      out.normal(x, y, z);
      return this;
    }

    public void endVertex() {
      out.endVertex();
    }

    public void defaultColor(int r, int g, int b, int a) {
      out.defaultColor(this.r, this.g, this.b, this.a);
    }

    public void unsetDefaultColor() {
      out.unsetDefaultColor();
    }
  }

  public static void render(PoseStack p, MultiBufferSource buffers, Vec3 camera) {
    var mc = Minecraft.getInstance();
    if (level == null || result == null) return;
    if (mc.level != level) {
      clear();
      return;
    }
    var raw = buffers.getBuffer(type());
    var blue = new Tint(raw, 90, 180, 255, 115);
    int light = LightTexture.FULL_BRIGHT;
    if (heatmap) {
      var grid = result.grid();
      var data = result.predicted();
      for (int i = 0; i < data.length; i++)
        if (Float.isFinite(data[i])
            && grid.known(i)
            && camera.distanceToSqr(grid.px(i), grid.ground()[i], grid.pz(i)) < 256 * 256) {
          float v = data[i];
          var colour =
              new Tint(
                  raw,
                  v >= green ? 45 : v >= yellow ? 215 : v >= orange ? 235 : 190,
                  v >= green ? 180 : v >= yellow ? 185 : v >= orange ? 115 : 45,
                  v >= green ? 95 : 45,
                  70);
          double x0 = Math.max(grid.x(), grid.px(i) - grid.step() * .5),
              z0 = Math.max(grid.z(), grid.pz(i) - grid.step() * .5),
              x1 =
                  Math.min(
                      grid.x() + (grid.columns() - 1) * grid.step(), grid.px(i) + grid.step() * .5),
              z1 =
                  Math.min(
                      grid.z() + (grid.rows() - 1) * grid.step(), grid.pz(i) + grid.step() * .5);
          AudioRenderGeometry.box(
              p, colour, x0, grid.ground()[i] + .03, z0, x1 - x0, .012, z1 - z0, 7, light);
        }
    }
    for (var t : towers)
      if (t.enabled()) {
        var model = SpeakerModels.get(t.model());
        if (model == null) continue;
        p.pushPose();
        p.translate(t.x(), t.ground(), t.z());
        p.mulPose(Axis.YP.rotationDegrees((float) -t.yaw()));
        double localTop = (t.modules() / 2 + .5) * model.height(),
            pitch = Math.toRadians(t.pitch()),
            frameY = t.height() + localTop * Math.cos(pitch),
            frameZ = localTop * Math.sin(pitch),
            back = -model.depth() / 2 - .55,
            mast = frameY + .5;
        for (double x : new double[] {-.3, .3})
          for (double z : new double[] {back - .3, back + .3}) {
            AudioRenderGeometry.box(p, blue, x - .025, 0, z - .025, .05, mast, .05, 3, light);
            AudioRenderGeometry.box(p, blue, x - .09, 0, z - .09, .18, .04, .18, 3, light);
          }
        for (int n = 0; n < mast; n++) {
          double hi = Math.min(mast, n + 1);
          for (double x : new double[] {-.3, .3})
            AudioRenderGeometry.link(
                p, blue, new Vec3(x, n, back - .3), new Vec3(x, hi, back + .3), light);
          for (double z : new double[] {back - .3, back + .3})
            AudioRenderGeometry.link(p, blue, new Vec3(-.3, n, z), new Vec3(.3, hi, z), light);
        }
        AudioRenderGeometry.box(
            p, blue, -.42, mast - .04, back - .36, .84, .08, frameZ - back + .85, 3, light);
        p.pushPose();
        p.translate(0, t.height(), 0);
        p.mulPose(Axis.XP.rotationDegrees((float) t.pitch()));
        for (int i = 0; i < t.modules(); i++) {
          p.pushPose();
          p.translate(0, localTop - (i + 1) * model.height(), model.depth() / 2);
          SpeakerRenderer.emit(p, blue, SpeakerMeshes.get(model.id()).far(), light);
          p.popPose();
        }
        var reference = SpeakerModels.get("csc_" + model.family() + "_a1");
        if (reference == null) reference = SpeakerModels.get("csc_orvane_lx12");
        p.pushPose();
        p.translate(0, localTop + .02, model.depth() / 2);
        p.scale(
            (float) (model.width() * SpeakerModels.scale(reference.id()) / reference.width()),
            1,
            (float) (model.depth() * SpeakerModels.scale(reference.id()) / reference.depth()));
        SpeakerRenderer.emit(
            p, blue, SpeakerMeshes.get("csc_" + model.family() + "_flying_frame").far(), light);
        p.popPose();
        p.popPose();
        AudioRenderGeometry.link(
            p, blue, new Vec3(0, frameY + .14, frameZ), new Vec3(0, mast, frameZ), light);
        p.popPose();
        AudioRenderGeometry.box(
            p, blue, t.refX() - .25, t.refY() - .02, t.refZ() - .25, .5, .04, .5, 7, light);
      }
    if (stage != null)
      AudioRenderGeometry.box(
          p, blue, stage[0] - .4, stage[1] + .03, stage[2] - .4, .8, .06, .8, 7, light);
  }
}
