package com.concertstage.stage;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;

/**
 * Platforms placed at an angle with the platform protractor: the platform becomes a mounted piece (like a stair flight) turned by the angle
 * of the tool from the usual placement, centred on the cell where it would have gone. Broken like a flight (it gives its item back).
 */
public final class StagePlatformAngle {
    private StagePlatformAngle() {}

    private static final Set<String> KINDS = Set.of("platform", "riser", "extension");

    /** Platforms that can be turned (they have a mounted model); festival stages and the legacy DJ platform cannot. */
    public static boolean turnable(StageSpec spec) {
        // decks, risers and small platforms; the big assemblies (festival stages: thousands of parts) would be far too heavy to turn
        return (KINDS.contains(spec.kind) || spec.id.equals("csc_foh_platform_4x2_h040")) && !spec.internal && !spec.id.equals("dj_platform_2x2_h080");
    }

    /** A floor clicked between two block levels (a 40 cm deck, a riser...): the height of its top in its cell, else -1. */
    public static double floorBetweenLevels(UseOnContext ctx) {
        if (ctx.getClickedFace() != Direction.UP) return -1;
        net.minecraft.world.level.block.state.BlockState s = ctx.getLevel().getBlockState(ctx.getClickedPos());
        if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals(com.concertstage.ConcertStage.MOD_ID)) return -1;
        double t = ctx.getClickLocation().y - ctx.getClickedPos().getY();
        return t > 0.02 && t < 0.98 ? t : -1;
    }

    public static InteractionResult place(UseOnContext ctx, StageEquipmentBlock block, int bend) {
        Level l = ctx.getLevel();
        StageSpec spec = block.spec;
        BlockPlaceContext place = new BlockPlaceContext(ctx);
        BlockPos target = place.getClickedPos();
        Direction look = ctx.getHorizontalDirection();
        double angle = StageSpec.turns(look.getOpposite()) * 90.0 + bend;
        double y = ctx.getClickedFace() == Direction.UP ? ctx.getClickLocation().y : target.getY();
        double x, z;
        if (bend == 0) {                       // straight, on a floor between two block levels: same grid as a normal placement
            double[] v = StagePose.rotate(-.5, 0, -.5, angle);
            x = target.getX() + .5 + v[0];
            z = target.getZ() + .5 + v[2];
        } else {
            double[] b = spec.raw_bounds;
            double[] centre = StagePose.rotate((b[0] + b[3]) / 2, 0, (b[2] + b[5]) / 2, angle);
            x = target.getX() + 0.5 - centre[0];
            z = target.getZ() + 0.5 - centre[2];
        }
        return placeAt(ctx, block, x, y, z, angle);
    }

    /** Lays the platform as a mounted piece at (x, y, z) turned by {@code angle}; y is the level its feet stand on. */
    public static InteractionResult placeAt(UseOnContext ctx, StageEquipmentBlock block, double x, double y, double z, double angle) {
        Level l = ctx.getLevel();
        StageSpec spec = block.spec;
        if (!StageSurfaces.supported(l, spec, x, y, z, angle)) return StageModules.fail(ctx, "platform_no_ground");
        if (!StageModules.place(ctx, spec, x, y, z, angle, spec.id + "=1", "ground", null, Map.of())) return StageModules.fail(ctx, "space_blocked");
        if (!l.isClientSide) {
            SoundType sound = block.defaultBlockState().getSoundType();
            l.playSound(null, BlockPos.containing(x, y, z), sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
