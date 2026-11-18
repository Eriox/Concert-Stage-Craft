package com.concertstage.stage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Platforms joined edge to edge, at any angle: clicking the side of a platform laid at an angle (protractor, or an earlier join) or of a
 * triangle with another platform lays the new one against the side clicked, in line with it, standing on the same ground. The side is the
 * nearest edge of the clicked platform's outline (a triangle's long side is at 45°, so a straight stage, a triangle and a 45° platform
 * close up without a gap). Along the side the new platform is centred on the click, by 25 cm steps, and flush with a corner when close.
 */
public final class StagePlatformJoin {
    private StagePlatformJoin() {}

    /** A platform in the world: origin and angle of its own frame, y = the level its feet stand on. */
    public record Host(double x, double y, double z, double angle, StageSpec spec, boolean angled) {}

    public static Host host(Level l, BlockPos p, Vec3 hit) {
        if (l.getBlockEntity(p) instanceof StageAttachmentBlockEntity be) {
            StageAttachmentBlockEntity.Entry best = null; double dist = 0.09;
            for (var e : be.entries()) {
                StageSpec s = StageSpec.find(e.key());
                if (s == null || !StagePlatformAngle.turnable(s) || !"ground".equals(e.support())) continue;
                for (double[] b : e.boxes()) {
                    double dx = Math.max(p.getX() + b[0] - hit.x, Math.max(0, hit.x - p.getX() - b[3]));
                    double dy = Math.max(p.getY() + b[1] - hit.y, Math.max(0, hit.y - p.getY() - b[4]));
                    double dz = Math.max(p.getZ() + b[2] - hit.z, Math.max(0, hit.z - p.getZ() - b[5]));
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < dist) { dist = d; best = e; }
                }
            }
            if (best != null) {
                double a = best.degrees();
                return new Host(p.getX() + best.x(), p.getY() + best.y(), p.getZ() + best.z(), a, StageSpec.get(best.key()), Math.abs(a / 90.0 - Math.rint(a / 90.0)) > 1e-6);
            }
        }
        BlockState s = l.getBlockState(p);
        if (s.getBlock() instanceof StageEquipmentBlock b && StagePlatformAngle.turnable(b.spec)) {
            StageSurfaces.Surface f = StageSurfaces.at(l, p);
            if (f != null) return new Host(f.x(), f.y() - b.spec.walk, f.z(), f.turn() * 90.0, b.spec, false);
        }
        return null;
    }

    /** True when a click on this host should join rather than use the block grid: an angled platform, or a triangle. */
    public static boolean joins(Host h) {
        return h != null && (h.angled() || h.spec().outline().length != 4);
    }

    public static InteractionResult place(UseOnContext ctx, StageEquipmentBlock item, Host h) {
        double[][] poly = h.spec().outline();
        Vec3 hit = ctx.getClickLocation();
        double[] q = StagePose.rotate(hit.x - h.x(), 0, hit.z - h.z(), -h.angle());
        int n = poly.length, edge = 0; double best = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double d = segment(poly[i], poly[(i + 1) % n], q[0], q[2]);
            if (d < best) { best = d; edge = i; }
        }
        double[] a = poly[edge], b = poly[(edge + 1) % n];
        double cx = 0, cz = 0; for (double[] v : poly) { cx += v[0] / n; cz += v[1] / n; }
        double dx = b[0] - a[0], dz = b[1] - a[1], len = Math.hypot(dx, dz); dx /= len; dz /= len;
        if (-dz * ((a[0] + b[0]) / 2 - cx) + dx * ((a[1] + b[1]) / 2 - cz) < 0) {        // outward normal is (-dz, dx): walk the side the other way
            double[] t = a; a = b; b = t; dx = -dx; dz = -dz;
        }
        StageSpec s = item.spec;
        double[] r = s.raw_bounds; double w = r[3] - r[0];
        double t = (q[0] - a[0]) * dx + (q[2] - a[1]) * dz;
        double start = Math.rint((t - w / 2) / 0.25) * 0.25;
        if (Math.abs(start) < 0.3) start = 0;
        if (Math.abs(start + w - len) < 0.3) start = len - w;
        double gap = 0.004;
        double hx = a[0] + dx * start - dz * gap, hz = a[1] + dz * start + dx * gap;
        double[] corner = StagePose.point(new double[]{hx, 0, hz}, h.x(), h.y(), h.z(), h.angle());
        double angle = h.angle() + Math.toDegrees(Math.atan2(dz, dx));
        angle = ((angle % 360) + 360) % 360;
        if (Math.abs(angle - Math.rint(angle)) < 1e-6) angle = Math.rint(angle);
        double[] c = StagePose.rotate(r[0], 0, r[2], angle);
        return StagePlatformAngle.placeAt(ctx, item, corner[0] - c[0], h.y(), corner[2] - c[2], angle);
    }

    private static double segment(double[] a, double[] b, double x, double z) {
        double dx = b[0] - a[0], dz = b[1] - a[1], l2 = dx * dx + dz * dz;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[1]) * dz) / l2));
        double px = a[0] + t * dx - x, pz = a[1] + t * dz - z;
        return Math.sqrt(px * px + pz * pz);
    }

    /** The click is on a side (not the top) of the platform. */
    public static boolean side(UseOnContext ctx) {
        return ctx.getClickedFace() != Direction.UP && ctx.getClickedFace() != Direction.DOWN;
    }
}
