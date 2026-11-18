package com.concertstage.lift;

import com.concertstage.ConcertStage;
import com.concertstage.StageContent;
import com.concertstage.angle.AngleMath;
import com.concertstage.angle.AngledPiece;
import com.concertstage.block.AngledHolderBlockEntity;
import com.concertstage.block.MotorTowerBlock;
import com.concertstage.block.TowerBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server side of the lift: what gets lifted, whether it can move, and the start / stop of a movement. */
public final class LiftControl {
    /**
     * 1.1.0: no limit on the number of blocks lifted in one go (trusses + everything attached, roof included). The blocks no longer
     * travel in the entity data (one packet of 2 MB at most, hence the old limit of 24 000) but packed and in pieces (LiftPayload).
     */
    public static final int MAX_BLOCKS = Integer.MAX_VALUE;
    /**
     * Most blocks looked at while searching for what is attached to the trusses. A roof is a big flat mass of ordinary blocks
     * (black concrete, wool, planks...), so attached blocks are followed from block to block at any distance from the trusses;
     * this only bounds the work when the roof touches something huge (the ground-resting parts are dropped afterwards).
     */
    public static final int MAX_SCAN = 1_000_000;
    /** How far (blocks) past the trusses, sideways, attached blocks are looked for (overhang of a roof made of blocks). */
    public static final int ATTACH_MARGIN = 12;
    /** Blocks that are never taken along as "attached to a truss" (add any block to this tag in a data pack). */
    public static final TagKey<Block> NEVER_LIFT = TagKey.create(Registries.BLOCK, new ResourceLocation(ConcertStage.MOD_ID, "never_lift"));
    /** Blocks of other mods a chain hoist picks up under its hook (Stage Equipment Furnitures is recognised by itself; add more in a data pack). */
    public static final TagKey<Block> HOIST_LOAD = TagKey.create(Registries.BLOCK, new ResourceLocation(ConcertStage.MOD_ID, "hoist_load"));

    /**
     * A block of Stage Equipment Furnitures (S.E.F), recognised by its namespace since the mod has no API, or of the hoist_load tag:
     * a chain hoist lifts it with what is fixed on it.
     */
    public static boolean isSefBlock(BlockState s) {
        if (s.isAir()) return false;
        if (s.is(HOIST_LOAD)) return true;
        String ns = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace();
        return ns.equals("sef") || ns.contains("furnit") || ns.contains("stageequip") || ns.contains("stage_equip")
            || ns.startsWith("sef_") || ns.endsWith("_sef")
            // Rackworks (racks, flight cases) and NDI Display (LED screens, monitors): hung under trusses and hoists like S.E.F
            || ns.contains("rackwork") || ns.startsWith("ndi") || ns.contains("_ndi") || ns.contains("ndidisplay") || ns.contains("ndi_display");
    }

    private LiftControl() {}

    /**
     * What the lower hook of a chain hoist takes when it comes down onto it: the hoist loads, a S.E.F block, or a S.E.F block lowered onto
     * a deck as a decoration. The hook goes into that cell (it does not stop above it), so raising the hoist picks it up.
     */
    public static boolean hookCanTake(ServerLevel level, BlockPos pos, BlockState s) {
        if (isHoistLoad(level, pos, s) || isSefBlock(s)) return true;
        return s.getBlock() == StageContent.OFFSET_BLOCK && level.getBlockEntity(pos) instanceof com.concertstage.block.offset.OffsetBlockEntity o
            && o.stored() != null && isSefBlock(o.stored());
    }

    /** Blocks that travel with the joints: trusses, and what hangs on them. The scaffold, stairs, bases and towers never do. */
    /** 1.2.0: true while a lift takes its blocks out of the world (the lighting fixtures stay patched in their desk). */
    public static boolean capturing;

    public static boolean isLiftable(BlockState s) {
        Block b = s.getBlock();
        return isTruss(b) || b == StageContent.ANGLED_HOLDER                    // trusses placed at an angle travel with the cell that holds them
            || b instanceof com.concertstage.block.HoistBlock    // a hoist hanging on a lifted truss goes with it
            || b == StageContent.CURTAIN_RAIL || b == StageContent.CURTAIN_CLOTH
            || b == StageContent.STAGE_TARP_RAIL || b == StageContent.STAGE_TARP_TOP || b == StageContent.STAGE_TARP_CLOTH
            || b == StageContent.REGIE_TARP_RAIL || b == StageContent.REGIE_TARP_TOP || b == StageContent.REGIE_TARP_CLOTH
            || b == StageContent.REGIE_POUTRE_TREILLIS_L1 || b == StageContent.REGIE_POUTRE_TREILLIS_L2
            || b == StageContent.RING_TRUSS || b == StageContent.RING_TRUSS_CELL || b instanceof com.concertstage.block.roof.RoofTrussBlock
            || b == StageContent.SPEAKER && s.getValue(com.concertstage.audio.SpeakerBlock.HANGING)    // CSC line arrays flown under a truss (1.1.0)
            || b instanceof com.concertstage.light.FixtureBlock && s.getValue(com.concertstage.light.FixtureBlock.HUNG);   // 1.2.0: lights hung on a truss
    }

    /** Every truss block, aluminium or black (corners included, base excluded: it stands on the ground). */
    private static boolean isTruss(Block b) {
        return b == StageContent.TRUSS_H1_L1 || b == StageContent.TRUSS_H1_L2 || b == StageContent.TRUSS_H2_L2 || b == StageContent.TRUSS_V2_H2
            || b == StageContent.TRUSS_H2_L1 || b == StageContent.TRUSS_H2_L1_NOIR
            || b == StageContent.TRUSS_CORNER_3 || b == StageContent.TRUSS_CORNER_3_H2
            || b == StageContent.TRUSS_CORNER_6 || b == StageContent.TRUSS_CORNER_6_H2
            || b == StageContent.TRUSS_H1_L1_NOIR || b == StageContent.TRUSS_H1_L2_NOIR || b == StageContent.TRUSS_H2_L2_NOIR
            || b == StageContent.TRUSS_V2_H2_NOIR || b == StageContent.TRUSS_CORNER_3_NOIR || b == StageContent.TRUSS_CORNER_3_H2_NOIR
            || b == StageContent.TRUSS_CORNER_6_NOIR || b == StageContent.TRUSS_CORNER_6_H2_NOIR
            || isSmallTruss(b)
            || b == StageContent.TRUSS_LADDER_L1
            || b == StageContent.TRUSS_LADDER_L1_NOIR
            || b == StageContent.TRUSS_LADDER_L2
            || b == StageContent.TRUSS_LADDER_L2_NOIR
            || b == StageContent.TRUSS_LADDER_V2
            || b == StageContent.TRUSS_LADDER_V2_NOIR
            || b == StageContent.TRUSS_JUNCTION_90
            || b == StageContent.TRUSS_JUNCTION_90_NOIR
            || b == StageContent.TRUSS_JUNCTION_T
            || b == StageContent.TRUSS_JUNCTION_T_NOIR
            || b == StageContent.TRUSS_JUNCTION_CROSS
            || b == StageContent.TRUSS_JUNCTION_CROSS_NOIR
            || b == StageContent.TRUSS_X30D_L1 || b == StageContent.TRUSS_X30D_L1_NOIR
            || b == StageContent.TRUSS_X30D_L2 || b == StageContent.TRUSS_X30D_L2_NOIR
            || b == StageContent.TRUSS_H2_L2_X30D || b == StageContent.TRUSS_H2_L2_X30D_NOIR;
    }

    /** The small 1 x 2 truss (10 px), aluminium or black: the only trusses a chain hoist carries. */
    private static boolean isSmallTruss(Block b) {
        return b == StageContent.TRUSS_H1_L2_PETIT || b == StageContent.TRUSS_H1_L2_PETIT_NOIR
            || b == StageContent.TRUSS_H1_L1_PETIT || b == StageContent.TRUSS_H1_L1_PETIT_NOIR
            || b == StageContent.TRUSS_LADDER_L1 || b == StageContent.TRUSS_LADDER_L2
            || b == StageContent.TRUSS_LADDER_L1_NOIR || b == StageContent.TRUSS_LADDER_L2_NOIR;
    }

    /** Same as {@link #isHoistLoad(BlockState)}, and a cell of angled trusses counts when every piece it holds is a small or X30D truss. */
    private static boolean isHoistLoad(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.getBlock() == StageContent.ANGLED_HOLDER) {
            if (!(level.getBlockEntity(pos) instanceof AngledHolderBlockEntity holder) || holder.pieces().isEmpty()) return false;
            for (AngledPiece p : holder.pieces()) {
                String name = p.block.endsWith("_noir") ? p.block.substring(0, p.block.length() - 5) : p.block;
                if (!name.equals("truss_h1_l2_petit") && !name.equals("truss_h1_l1_petit") && !name.equals("truss_x30d_l1") && !name.equals("truss_x30d_l2") && !name.equals("truss_ladder_l1") && !name.equals("truss_ladder_l2")) return false;
            }
            return true;
        }
        return isHoistLoad(s);
    }

    /** What a chain hoist takes along: the small trusses, the X30D triangular trusses and the curtains that hang on them (the attachments are added afterwards). */
    private static boolean isHoistLoad(BlockState s) {
        Block b = s.getBlock();
        return isSmallTruss(b) || b == StageContent.TRUSS_X30D_L1 || b == StageContent.TRUSS_X30D_L1_NOIR || b == StageContent.TRUSS_X30D_L2 || b == StageContent.TRUSS_X30D_L2_NOIR
            || b == StageContent.CURTAIN_RAIL || b == StageContent.CURTAIN_CLOTH
            || b == StageContent.RING_TRUSS || b == StageContent.RING_TRUSS_CELL || b instanceof com.concertstage.block.roof.RoofTrussBlock   // a circular truss hung on the hook
            || b == StageContent.SPEAKER && s.getValue(com.concertstage.audio.SpeakerBlock.HANGING)   // a line array hung on the hook
            || b == StageContent.STAGE_TARP_RAIL || b == StageContent.STAGE_TARP_TOP || b == StageContent.STAGE_TARP_CLOTH
            || b == StageContent.REGIE_TARP_RAIL || b == StageContent.REGIE_TARP_TOP || b == StageContent.REGIE_TARP_CLOTH;
    }

    /** The truss blocks: only what touches these is taken along as an attachment. */
    private static boolean isCarrier(BlockState s) {
        Block b = s.getBlock();
        return isTruss(b) || isSefBlock(s) || b == StageContent.ANGLED_HOLDER || b == StageContent.RING_TRUSS || b == StageContent.RING_TRUSS_CELL || b instanceof com.concertstage.block.roof.RoofTrussBlock;   // lights hanging on a ring travel too
    }

    /**
     * A block that may hang on a truss (lights, clamps, speakers... from this mod or any other): not air or fluid, not unbreakable,
     * not a replaceable plant, not terrain, not something of this mod that stays on the ground (scaffold, deck, stairs, towers, bases)
     * and not tagged {@code concertstage:never_lift}.
     */
    private static boolean isAttachable(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced()) return false;
        if (s.getDestroySpeed(level, pos) < 0.0F) return false;
        Block b = s.getBlock();
        if (BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals(ConcertStage.MOD_ID)) return false;   // ours: only isLiftable() ones travel
        if (s.is(NEVER_LIFT) || s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER)
            || s.is(BlockTags.SAND) || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.ICE) || s.is(BlockTags.SNOW)) return false;
        return true;
    }

    /**
     * Adds to the capture the blocks attached to the trusses: a light hanging under a truss, a clamp, a speaker, and also a roof
     * made of ordinary blocks (black concrete or anything else) resting on the trusses. They are found from the trusses outwards,
     * block to block, at any distance (up to {@link #MAX_SCAN} blocks looked at). Then every block that stands on something that
     * does not travel (a block that rests on the ground, on the deck, on a wall) is dropped again, together with what was only
     * connected through it, so a house or a tree next to the stage stays where it is, and so does the part of a roof that rests on
     * a wall. What is left must still be connected to a truss.
     *
     * @return false if the connected mass is too big to be scanned (the lift is refused instead of silently leaving the roof behind)
     */
    private static boolean addAttachments(ServerLevel level, Capture cap) {
        Set<BlockPos> found = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        // 1.1.0: what hangs on or rests on the rig is above or under it: the search stays over the footprint of the trusses (plus
        // ATTACH_MARGIN blocks for an overhanging roof). Before, a roof touching a paved square or a building followed it block after
        // block until the search limit, and the lift was refused ("too big").
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockState> m : cap.blocks.entrySet()) {
            if (!isCarrier(m.getValue())) continue;
            x0 = Math.min(x0, m.getKey().getX()); x1 = Math.max(x1, m.getKey().getX());
            z0 = Math.min(z0, m.getKey().getZ()); z1 = Math.max(z1, m.getKey().getZ());
        }
        final int bx0 = x0 - ATTACH_MARGIN, bx1 = x1 + ATTACH_MARGIN, bz0 = z0 - ATTACH_MARGIN, bz1 = z1 + ATTACH_MARGIN;
        for (Map.Entry<BlockPos, BlockState> m : cap.blocks.entrySet()) {
            if (!isCarrier(m.getValue())) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = m.getKey().relative(d);
                if (n.getY() < cap.floorY || cap.blocks.containsKey(n) || found.contains(n) || !level.hasChunkAt(n)) continue;
                if (isAttachable(level, n, level.getBlockState(n))) {
                    found.add(n);
                    queue.add(n);
                }
            }
        }
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = cur.relative(d);
                if (n.getX() < bx0 || n.getX() > bx1 || n.getZ() < bz0 || n.getZ() > bz1 || n.getY() < cap.floorY) continue;
                if (cap.blocks.containsKey(n) || found.contains(n) || !level.hasChunkAt(n)) continue;
                if (isAttachable(level, n, level.getBlockState(n))) {
                    found.add(n);
                    if (found.size() > MAX_SCAN) return false;
                    queue.add(n);
                }
            }
        }

        // S.E.F / Rackworks / NDI Display blocks: a screen or a rack hung on the rig goes with it as a whole, even when its bottom row
        // touches the ground or the deck; the whole connected group of such blocks counts when one of them touches what travels
        cap.hung.clear();
        for (BlockPos p : found) {
            if (cap.hung.contains(p) || !isSefBlock(level.getBlockState(p)) || !touchesCarrier(cap, p)) continue;
            Deque<BlockPos> g = new ArrayDeque<>();
            g.add(p);
            cap.hung.add(p);
            while (!g.isEmpty()) {
                BlockPos c = g.poll();
                for (Direction d : Direction.values()) {
                    BlockPos n = c.relative(d);
                    if (found.contains(n) && !cap.hung.contains(n) && isSefBlock(level.getBlockState(n))) { cap.hung.add(n); g.add(n); }
                }
            }
        }
        // drop what stands on something that stays (and, step by step, what stood on the blocks that were just dropped)
        Set<BlockPos> keep = new LinkedHashSet<>(found);
        Deque<BlockPos> dropped = new ArrayDeque<>();
        for (BlockPos p : found) {
            // a light hung on a truss travels with it even when the rig is built low and the light touches the deck or the ground
            if (hangsOnRig(level, cap, p)) continue;
            if (standsOnSomethingThatStays(level, cap, keep, p)) dropped.add(p);
        }
        while (!dropped.isEmpty()) {
            BlockPos p = dropped.poll();
            if (hangsOnRig(level, cap, p) || !keep.remove(p)) continue;
            BlockPos above = p.above();
            if (keep.contains(above) && standsOnSomethingThatStays(level, cap, keep, above)) dropped.add(above);
        }

        // keep only what is still connected to a truss
        Set<BlockPos> linked = new LinkedHashSet<>();
        Deque<BlockPos> q2 = new ArrayDeque<>();
        for (Map.Entry<BlockPos, BlockState> m : cap.blocks.entrySet()) {
            if (!isCarrier(m.getValue())) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = m.getKey().relative(d);
                if (keep.contains(n) && linked.add(n)) q2.add(n);
            }
        }
        while (!q2.isEmpty()) {
            BlockPos cur = q2.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = cur.relative(d);
                if (keep.contains(n) && linked.add(n)) q2.add(n);
            }
        }
        for (BlockPos p : linked) {
            cap.blocks.put(p, level.getBlockState(p));
            cap.attached.add(p);
        }
        return true;
    }

    /**
     * A stage light (Theatrical, Theatrical: Extra Lights, or any block with a true "hanging" state) touching a travelling truss: it hangs on
     * the rig, so it goes with it whatever is under it.
     */
    private static boolean hangsOnRig(ServerLevel level, Capture cap, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (cap.hung.contains(p) || isSefBlock(s) && sefHangs(level, cap, p)) return true;
        if (!isStageLight(s)) return false;
        for (Direction d : Direction.values()) {
            BlockState n = cap.blocks.get(p.relative(d));
            if (n != null && isCarrier(n)) return true;
        }
        return false;
    }

    private static boolean touchesCarrier(Capture cap, BlockPos p) {
        for (Direction d : Direction.values()) {
            BlockState n = cap.blocks.get(p.relative(d));
            if (n != null && isCarrier(n)) return true;
        }
        return false;
    }

    /**
     * A S.E.F block hung under what travels (a line array under its bumper, the bumper on the hoist hook or a truss): going up from it through
     * S.E.F blocks only, one of them touches a travelling carrier. The whole column goes, even when its bottom box rests on the deck.
     */
    private static boolean sefHangs(ServerLevel level, Capture cap, BlockPos p) {
        BlockPos q = p;
        for (int i = 0; i < 64; i++) {
            BlockState own = cap.blocks.get(q);
            if (own != null) return isCarrier(own);
            if (!isSefBlock(level.getBlockState(q))) return false;
            for (Direction d : Direction.values()) {
                BlockState n = cap.blocks.get(q.relative(d));
                if (n != null && isCarrier(n)) return true;
            }
            q = q.above();
        }
        return false;
    }

    static boolean isStageLight(BlockState s) {
        if (com.concertstage.compat.TheatricalCompat.isFixture(s.getBlock())) return true;
        String ns = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace();
        if (ns.contains("theatrical") || ns.contains("extralight") || ns.contains("extra_light")) return true;
        for (net.minecraft.world.level.block.state.properties.Property<?> prop : s.getProperties())
            if (prop.getName().equals("hanging") && Boolean.TRUE.equals(s.getValue(prop))) return true;
        return false;
    }

    /**
     * Is the block below {@code p} solid, and neither part of the structure nor still planned to travel? Then {@code p} rests on
     * something that stays. (A block below that is a torch, grass... with no collision does not count.)
     */
    private static boolean standsOnSomethingThatStays(ServerLevel level, Capture cap, Set<BlockPos> keep, BlockPos p) {
        BlockPos below = p.below();
        if (keep.contains(below) || cap.blocks.containsKey(below)) return false;
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    /** Can every block of the structure stand {@code offset} blocks above where it was taken (the blocks are out of the world)? */
    public static boolean canOccupy(ServerLevel level, BlockPos anchor, List<LiftStructureEntity.Entry> entries, int offset) {
        return obstacle(level, anchor, entries, offset) == null;
    }

    /** 1.1.0: the first block in the way at {@code offset} (null: free), so the player is told where it is. */
    public static BlockPos obstacle(ServerLevel level, BlockPos anchor, List<LiftStructureEntity.Entry> entries, int offset) {
        int i = obstacleIndex(level, anchor, entries, offset);
        return i < 0 ? null : anchor.offset(entries.get(i).rel).above(offset);
    }

    /** Index of the first block of the structure that cannot go {@code offset} blocks above where it was taken, or -1. */
    public static int obstacleIndex(ServerLevel level, BlockPos anchor, List<LiftStructureEntity.Entry> entries, int offset) {
        for (int i = 0; i < entries.size(); i++) {
            BlockPos p = anchor.offset(entries.get(i).rel).above(offset);
            if (p.getY() < level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight() || !level.hasChunkAt(p)
                || !passable(level, p, level.getBlockState(p))) return i;
        }
        return -1;
    }

    /** "Obstacle at x, y, z (block): the structure stops", and a mark on the block for a few seconds. */
    public static void tellObstacle(ServerLevel level, BlockPos p, java.util.Collection<? extends net.minecraft.server.level.ServerPlayer> players) {
        tellObstacle(level, p, null, null, players);
    }

    /**
     * Same, also naming the block of the structure that runs into it and where it was taken from ({@code source} may be null): when a
     * block was taken along by mistake (a truss lying on the deck, a block of the ground), this says which one.
     */
    public static void tellObstacle(ServerLevel level, BlockPos p, BlockState sourceState, BlockPos sourcePos,
                                    java.util.Collection<? extends net.minecraft.server.level.ServerPlayer> players) {
        BlockState s = level.getBlockState(p);
        Component name = p.getY() < level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight()
            ? Component.translatable("message.concertstage.lift_world_limit") : s.getBlock().getName();
        // an invisible cell: say whose it is (the roof frame, circular truss or speaker array that stays where it is)
        if (s.getBlock() == StageContent.RING_TRUSS_CELL && level.getBlockEntity(p) instanceof com.concertstage.block.ring.RingTrussCellBlockEntity c
            && c.anchor() != null && level.hasChunkAt(c.anchor()))
            name = Component.translatable("message.concertstage.lift_cell_of", level.getBlockState(c.anchor()).getBlock().getName(), c.anchor().toShortString());
        Component msg = sourceState == null ? Component.translatable("message.concertstage.lift_blocked_at", p.toShortString(), name)
            : Component.translatable("message.concertstage.lift_blocked_by", p.toShortString(), name, sourceState.getBlock().getName(), sourcePos.toShortString());
        for (var pl : players) pl.displayClientMessage(msg, true);
        com.mojang.logging.LogUtils.getLogger().info("Concert Stage: lift stopped by {} at {} (moving block {} from {})", s, p.toShortString(),
            sourceState, sourcePos == null ? "?" : sourcePos.toShortString());
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
    }

    // ------------------------------------------------------------ result of a request
    public enum Result { STARTED, ALREADY, UNLOADED, MOVING, NOT_ALIGNED, GROUNDED, TOO_BIG, BLOCKED, BLOCKED_TOLD }

    /** What is hanging on the joints, or why it cannot be lifted. */
    private static final class Capture {
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        /** The blocks of {@link #blocks} that are attachments (foreign blocks hanging on the trusses). */
        final Set<BlockPos> attached = new HashSet<>();
        /** Roof frames and circular trusses taken along: their cells are removed at the start and rebuilt on arrival. */
        final List<BlockPos> cellOwners = new ArrayList<>();
        /** S.E.F / Rackworks / NDI Display blocks hung on the rig (whole connected groups): never left behind. */
        final Set<BlockPos> hung = new HashSet<>();
        Result error;
        BlockPos errorPos;
        /** 1.1.0: level of the feet of the towers: blocks under it (the ground, a paved square) are never taken along. */
        int floorY = Integer.MIN_VALUE;
    }

    private static Capture capture(ServerLevel level, List<TowerBlockEntity> towers, int liftBlocks) {
        Capture cap = new Capture();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        boolean hoist = towers.get(0).tower().isHoist();       // hoists only take small trusses (and curtains), towers take every truss
        if (!hoist) {
            cap.floorY = Integer.MAX_VALUE;
            for (TowerBlockEntity t : towers) cap.floorY = Math.min(cap.floorY, t.mainPos().getY());
        }
        for (TowerBlockEntity t : towers) {
            MotorTowerBlock block = t.tower();
            for (BlockPos start : block.attachCells(t, liftBlocks)) {
                BlockState st = level.getBlockState(start);
                // a chain hoist also picks up a S.E.F block under its hook (what is fixed on it comes as attachments)
                if (seen.add(start) && (hoist ? hookCanTake(level, start, st) : isLiftable(st))) {
                    cap.blocks.put(start, st);
                    queue.add(start);
                    if (!hoist) addHoistLoad(level, start, st, cap, seen, queue);
                }
            }
        }
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (BlockPos n : linkedNeighbours(level, cur)) {
                if (!seen.add(n)) continue;
                if (!level.hasChunkAt(n)) continue;
                BlockState s = level.getBlockState(n);
                if (s.getBlock() == StageContent.TRUSS_BASE_V2 || s.getBlock() == StageContent.TRUSS_BASE_V2_NOIR) {          // standing on the ground: it cannot go up
                    cap.error = Result.GROUNDED;
                    cap.errorPos = n;
                    return cap;
                }
                if (!(hoist ? isHoistLoad(level, n, s) : isLiftable(s))) continue;
                cap.blocks.put(n, s);
                if (!hoist) addHoistLoad(level, n, s, cap, seen, queue);         // a chain hoist on a lifted truss takes its load along
                // 1.1.0: an invisible cell touching the rig: its owner (roof frame, circular truss, flown speaker array) goes along too,
                // else its cells stay in the way of the lift
                if (!hoist && s.getBlock() == StageContent.RING_TRUSS_CELL
                    && level.getBlockEntity(n) instanceof com.concertstage.block.ring.RingTrussCellBlockEntity cell && cell.anchor() != null) {
                    BlockPos a = cell.anchor();
                    if (level.hasChunkAt(a) && seen.add(a)) {
                        BlockState as = level.getBlockState(a);
                        if (isLiftable(as) && com.concertstage.block.ring.CellOwners.owns(level, a, n)) {
                            cap.blocks.put(a, as);
                            queue.add(a);
                        }
                    }
                }
                queue.add(n);
            }
        }
        if (!addAttachments(level, cap)) cap.error = Result.TOO_BIG;
        // the invisible cells of roof frames and circular trusses do not travel: their owner takes them away and rebuilds them on arrival
        for (BlockPos p : new ArrayList<>(cap.blocks.keySet())) {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof com.concertstage.block.roof.RoofTrussBlockEntity || be instanceof com.concertstage.block.ring.RingTrussBlockEntity || be instanceof com.concertstage.audio.SpeakerBlockEntity) cap.cellOwners.add(p);
        }
        cap.blocks.keySet().removeIf(p -> cap.blocks.get(p).getBlock() == StageContent.RING_TRUSS_CELL);
        return cap;
    }

    /**
     * Tower lift: a chain hoist hanging on a lifted truss goes up with it, and so does what hangs on its hook ({@code RANGE - lift} blocks
     * under it): the small truss, curtains, the S.E.F bumper and its speakers (these come as attachments).
     */
    private static void addHoistLoad(ServerLevel level, BlockPos p, BlockState s, Capture cap, Set<BlockPos> seen, Deque<BlockPos> queue) {
        if (!(s.getBlock() instanceof com.concertstage.block.HoistBlock hoistBlock)) return;
        if (!(level.getBlockEntity(p) instanceof TowerBlockEntity part)) return;
        TowerBlockEntity tb = level.getBlockEntity(part.mainPos()) instanceof TowerBlockEntity m ? m : part;
        if (tb.isMoving()) return;
        for (BlockPos h : hoistBlock.attachCells(tb, tb.restUnits() / TowerBlockEntity.UNITS)) {
            if (!level.hasChunkAt(h) || !seen.add(h)) continue;
            BlockState hs = level.getBlockState(h);
            if (!hookCanTake(level, h, hs)) continue;
            cap.blocks.put(h, hs);
            queue.add(h);
        }
    }

    /** No obstacle for a lift: replaceable, or a cell of a roof sheet that only stops light beams. */
    static boolean passable(ServerLevel level, BlockPos p, BlockState s) {
        if (s.canBeReplaced()) return true;
        if (s.getBlock() != StageContent.RING_TRUSS_CELL) return false;
        if (level.getBlockEntity(p) instanceof com.concertstage.block.ring.RingTrussCellBlockEntity c && c.lightOnly()) return true;
        // 1.1.0: a cell whose owner is gone (it travels with this lift, or the cell is a left-over) is not in the way
        if (com.concertstage.block.ring.RingTrussCellBlock.orphan(level, p)) return true;
        // nor the cells of a speaker array that stays (a stack on the deck or the ground next to a truss): they only give the cabinets
        // something to stand on and bump into; the rig goes past them, and if it stops there its block takes the place of the cell
        return level.getBlockEntity(p) instanceof com.concertstage.block.ring.RingTrussCellBlockEntity c && c.anchor() != null
            && level.getBlockEntity(c.anchor()) instanceof com.concertstage.audio.SpeakerBlockEntity;
    }

    /**
     * The blocks a block is linked to: the six that touch it. A cell of trusses placed at an angle is also linked to the cells that touch it by an
     * edge or a corner (an angled truss crosses the grid diagonally), and any cell touching such a cell by an edge or a corner is linked to it.
     */
    private static List<BlockPos> linkedNeighbours(ServerLevel level, BlockPos cur) {
        List<BlockPos> out = new ArrayList<>(26);
        boolean holder = level.getBlockState(cur).getBlock() == StageContent.ANGLED_HOLDER;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos n = cur.offset(dx, dy, dz);
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1) {
                        out.add(n);
                    } else if (level.hasChunkAt(n) && (holder || level.getBlockState(n).getBlock() == StageContent.ANGLED_HOLDER)) {
                        out.add(n);
                    }
                }
            }
        }
        if (holder && level.getBlockEntity(cur) instanceof AngledHolderBlockEntity cell) {
            // the pieces are longer than a cell: two angled trusses that touch can be held two cells apart, so look at the real ends of each
            // piece (the cell of the next piece, or of a corner, is within a block of the end), and at what hangs under it (a chain hoist)
            for (AngledPiece piece : cell.pieces()) {
                double[] start = piece.start(cur), end = piece.end(cur);
                int y0 = (int) Math.round(piece.absY(cur));
                for (double[] q : new double[][] {start, end}) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            for (int row = 0; row < piece.height(); row++) {
                                out.add(new BlockPos(AngleMath.cellOf(q[0]) + dx, y0 + row, AngleMath.cellOf(q[1]) + dz));
                            }
                        }
                    }
                }
                double[] u = AngleMath.dir(piece.angle);
                for (int i = 0; i <= 4 * piece.length(); i++) {
                    double t = i / 4.0;
                    out.add(new BlockPos(AngleMath.cellOf(start[0] + t * u[0]), y0 - 1, AngleMath.cellOf(start[1] + t * u[1])));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------ start
    /** Starts a lift of all the given towers to {@code targetBlocks}. All of them must be idle and at the same height. */
    public static Result start(ServerLevel level, List<TowerBlockEntity> towers, int targetBlocks, ServerPlayer player) {
        if (towers.isEmpty()) return Result.UNLOADED;
        int u0 = towers.get(0).restUnits();
        int max = Integer.MAX_VALUE;
        boolean hoists = towers.get(0).tower().isHoist();
        for (TowerBlockEntity t : towers) {
            if (t.tower().isHoist() != hoists) return Result.NOT_ALIGNED;      // hoists and towers are not lifted together
            if (t.isMoving()) return Result.MOVING;
            if (t.restUnits() != u0) return Result.NOT_ALIGNED;
            max = Math.min(max, t.tower().maxLiftBlocks());
        }
        int target = Math.max(0, Math.min(max, targetBlocks)) * TowerBlockEntity.UNITS;
        if (target == u0) return Result.ALREADY;
        int dir = Integer.signum(target - u0);
        int liftBlocks = u0 / TowerBlockEntity.UNITS;

        Capture cap = capture(level, towers, liftBlocks);
        if (cap.error != null) {
            if (player != null) {
                if (cap.error == Result.GROUNDED) {
                    player.displayClientMessage(Component.translatable("message.concertstage.lift_grounded",
                        cap.errorPos.toShortString()), true);
                }
            }
            return cap.error;
        }

        // tied tarps: their bottom stays tied, the lifted top row draws the cloth as a stretching sheet while it moves
        for (BlockPos p : new ArrayList<>(cap.blocks.keySet())) {
            if (level.getBlockEntity(p) instanceof com.concertstage.block.tarp.StageTarpRailBlockEntity tarp)
                for (BlockPos q : tarp.prepareLift()) cap.blocks.remove(q);
        }

        BlockPos anchor = towers.get(0).mainPos();
        // the first block of travel must be free (the blocks are still in the world: they do not count as obstacles, nor do the cells of
        // the roof frames taken along)
        Set<BlockPos> own = new HashSet<>(cap.blocks.keySet());
        for (BlockPos o : cap.cellOwners) {
            if (level.getBlockEntity(o) instanceof com.concertstage.block.roof.RoofTrussBlockEntity r) own.addAll(r.cellPositions());
            else if (level.getBlockEntity(o) instanceof com.concertstage.block.ring.RingTrussBlockEntity r) own.addAll(r.cellPositions());
            else if (level.getBlockEntity(o) instanceof com.concertstage.audio.SpeakerBlockEntity r) own.addAll(r.cellPositions());
        }
        if (hoists && dir < 0) {                      // a hoist lowering its hook: the hook stops on the first obstacle under it
            for (TowerBlockEntity t : towers) {
                BlockPos hook = t.mainPos().below(2 + com.concertstage.block.HoistBlock.RANGE - liftBlocks);
                BlockState hs = level.getBlockState(hook);
                // a load the hook can take (a S.E.F block...): the hook goes down into it and stops there (LiftStructureEntity)
                if (!own.contains(hook) && !passable(level, hook, hs) && !hookCanTake(level, hook, hs)) return Result.BLOCKED;
            }
        }
        for (BlockPos q : cap.blocks.keySet()) {
            BlockPos p = q.above(dir);
            if (p.getY() < level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight()
                || !own.contains(p) && !passable(level, p, level.getBlockState(p))) {
                if (player != null) tellObstacle(level, p, cap.blocks.get(q), q, java.util.List.of(player));
                return Result.BLOCKED_TOLD;
            }
        }
        // roof frames and circular trusses: their invisible cells go now (saved without them), rebuilt where they arrive
        for (BlockPos o : cap.cellOwners) {
            if (level.getBlockEntity(o) instanceof com.concertstage.block.roof.RoofTrussBlockEntity r) r.clearCells();
            else if (level.getBlockEntity(o) instanceof com.concertstage.block.ring.RingTrussBlockEntity r) r.clearCells();
            else if (level.getBlockEntity(o) instanceof com.concertstage.audio.SpeakerBlockEntity r) r.clearCells();
        }
        List<LiftStructureEntity.Entry> entries = new ArrayList<>(cap.blocks.size());
        for (Map.Entry<BlockPos, BlockState> m : cap.blocks.entrySet()) {
            BlockEntity be = level.getBlockEntity(m.getKey());
            CompoundTag nbt = be != null ? be.saveWithoutMetadata() : null;
            LiftStructureEntity.Entry entry = new LiftStructureEntity.Entry(m.getKey().subtract(anchor), m.getValue(), nbt);
            entry.captureRenderData(be);
            entries.add(entry);
        }

        LiftStructureEntity entity = new LiftStructureEntity(StageContent.LIFT_STRUCTURE, level);
        List<BlockPos> towerPositions = new ArrayList<>();
        for (TowerBlockEntity t : towers) towerPositions.add(t.mainPos());
        long now = level.getGameTime();
        entity.setPos(anchor.getX(), anchor.getY() + 0.0, anchor.getZ());
        entity.setup(level, anchor, entries, u0, now, u0, target, towerPositions);

        // take the blocks out of the world (no drops, no neighbour updates: the states are kept in the entity)
        capturing = true;
        try {
        for (BlockPos p : cap.blocks.keySet()) {
            // an attached block of another mod (a chest, a light with a screen...): detach its block entity first so nothing
            // is dropped; its data is kept in the entity and put back on arrival
            if (cap.attached.contains(p)) level.removeBlockEntity(p);
            if (level.getBlockEntity(p) instanceof AngledHolderBlockEntity holder) holder.noDrops = true;     // the pieces travel with the cell
            level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        }
        } finally {
            capturing = false;
        }
        level.addFreshEntity(entity);
        UUID id = entity.getUUID();
        for (TowerBlockEntity t : towers) t.startMove(now, target, id);
        return Result.STARTED;
    }

    // ------------------------------------------------------------ stop
    /** @return true if something was moving */
    public static boolean stop(ServerLevel level, List<TowerBlockEntity> towers) {
        boolean any = false;
        Set<UUID> done = new HashSet<>();
        for (TowerBlockEntity t : towers) {
            if (!t.isMoving() || t.session() == null || !done.add(t.session())) continue;
            any = true;
            if (level.getEntity(t.session()) instanceof LiftStructureEntity e) e.requestStop(level);
        }
        return any;
    }
}
