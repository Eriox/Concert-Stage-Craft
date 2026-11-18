package com.concertstage.angle;

import com.concertstage.StageContent;
import com.concertstage.block.AngledHolderBlock;
import com.concertstage.block.AngledHolderBlockEntity;
import com.concertstage.block.MotorTowerBlock;
import com.concertstage.block.MultiPartBlock;
import com.concertstage.block.TowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.concertstage.item.AngleToolItem;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server side of placing a truss at an angle, and of turning a tower. The geometry is in {@link AngleMath}. */
public final class AnglePlacement {
    private AnglePlacement() {}

    /**
     * Where the last angled truss placed by a player ends: the next truss carries on the line from there. {@code holder} is the cell that
     * holds that last piece (the line is forgotten when the piece is broken) and {@code auto} is true when the piece went the way of a
     * turned tower or of the line before it, not because of the tool.
     */
    private record ChainEnd(ResourceKey<Level> dimension, double x, double y, double z, double deg, BlockPos holder, boolean auto, boolean barrier) {}

    private static final Map<UUID, ChainEnd> CHAINS = new ConcurrentHashMap<>();

    /** The line still ends where it was remembered: the piece is there. If its chunk is not loaded we cannot tell, so it is kept. */
    private static boolean stillThere(Level level, ChainEnd e) {
        if (!level.hasChunkAt(e.holder())) return true;
        if (!(level.getBlockEntity(e.holder()) instanceof AngledHolderBlockEntity holder)) return false;
        for (AngledPiece p : holder.pieces()) {
            double[] end = p.end(holder.getBlockPos());
            if (AngleMath.dist2(end[0], end[1], e.x(), e.z()) < 1e-4 && AngleMath.angleDiff(p.angle, e.deg()) < 0.01) return true;
        }
        return false;
    }

    /**
     * The end of the player's last line if a click at {@code place} carries it on (see {@link AngleMath#chainApplies}), else null. The line
     * has no length limit and is not forgotten with time: it lasts until its last piece is broken or another line is started.
     */
    private static ChainEnd chainNear(Level level, Player player, BlockPos place, boolean barrier) {
        if (player == null) return null;
        ChainEnd e = CHAINS.get(player.getUUID());
        // 1.2.0 beta 2b: a line of barriers is carried on by barriers, a line of trusses by trusses
        if (e == null || e.dimension() != level.dimension() || e.barrier() != barrier) return null;
        if (!stillThere(level, e)) {
            CHAINS.remove(player.getUUID());
            return null;
        }
        double d2 = AngleMath.dist2(e.x(), e.z(), place.getX() + 0.5, place.getZ() + 0.5);
        return AngleMath.chainApplies(AngleToolItem.holding(player), e.auto(), d2, e.y() - place.getY()) ? e : null;
    }

    /**
     * 1.2.0 beta 2b (14 November: "every barrier should go at an angle: front of stage, the barriers with the tarp..."): the crowd barriers
     * (CrowdGuard 1 m / gate / end, CityLine 2 m, AluGuard 1 m and its corner) and the site fences (with or without their black screen)
     * are placed at an angle like the trusses: angle tool, one line after the other, end to end.
     */
    public static boolean barrier(net.minecraft.world.level.block.Block b) {
        return b == StageContent.BARRIER_CROWDGUARD_1M || b == StageContent.BARRIER_CROWDGUARD_GATE || b == StageContent.BARRIER_CROWDGUARD_END
            || b == StageContent.BARRIER_CITYLINE_2M || b == StageContent.BARRIER_ALUGUARD_1M || b == StageContent.BARRIER_ALUGUARD_CORNER
            || b == StageContent.FENCE_SITE || b == StageContent.FENCE_SITE_SCREEN;
    }

    /** A piece that can be placed at an angle: a truss (and the FOH pieces of the same kind) or a barrier. */
    public static boolean angleable(net.minecraft.world.level.block.Block b) {
        return b instanceof MultiPartBlock mp && (mp.isChain() || barrier(b));
    }

    /** True for the blocks a truss attaches to (a click on them is an attachment, never the continuation of a remembered line). */
    private static boolean attachesTo(BlockState state) {
        return angleable(state.getBlock()) || state.getBlock() instanceof MotorTowerBlock || state.getBlock() instanceof AngledHolderBlock;
    }

    /** The block whose side was clicked (the placed position itself when the click replaces something). */
    private static BlockPos clickedBlock(BlockPlaceContext ctx) {
        BlockPos place = ctx.getClickedPos();
        return ctx.replacingClickedOnBlock() ? place : place.relative(ctx.getClickedFace().getOpposite());
    }

    /** The angled piece whose end face is closest to the click, if the click is near enough to an end. */
    private static AngledPiece pieceEndNear(AngledHolderBlockEntity holder, Vec3 hit) {
        AngledPiece best = null;
        double bestD = 1.2 * 1.2;
        BlockPos cell = holder.getBlockPos();
        for (AngledPiece p : holder.pieces()) {
            double[] e = p.end(cell);
            double d = AngleMath.dist2(e[0], e[1], hit.x, hit.z);
            double y = p.absY(cell);
            boolean sameHeight = hit.y >= y - 0.25 && hit.y <= y + p.height() + 0.25;
            if (sameHeight && d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** The block entity of the tower at {@code pos} if it is a tower (not a hoist) that has been turned by the angle tool, else null. */
    private static TowerBlockEntity turnedTower(Level level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof MotorTowerBlock tower) || tower.isHoist()) return null;
        return level.getBlockEntity(pos) instanceof TowerBlockEntity be && be.angle() != 0.0F ? be : null;
    }

    /**
     * True if the click is on the end of an angled truss, or on the side of a turned tower: a truss placed there goes the way the piece or the
     * flange points, whatever the tool says (the tool then adds a bend).
     */
    public static boolean continuesPiece(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = clickedBlock(ctx);
        if (!ctx.replacingClickedOnBlock() && ctx.getClickedFace().getAxis().isHorizontal() && turnedTower(level, clicked) != null) return true;
        if (level.getBlockState(clicked).getBlock() instanceof AngledHolderBlock
            && level.getBlockEntity(clicked) instanceof AngledHolderBlockEntity holder
            && pieceEndNear(holder, ctx.getClickLocation()) != null) return true;
        boolean barrier = ctx.getItemInHand().getItem() instanceof net.minecraft.world.item.BlockItem bi && barrier(bi.getBlock());
        return !attachesTo(level.getBlockState(clicked)) && chainNear(level, ctx.getPlayer(), ctx.getClickedPos(), barrier) != null;
    }

    /**
     * Places {@code truss} as an angled piece. Where it starts, and the direction it is turned away from:
     * <ul>
     * <li>on the end of an angled truss: from that end, away from its direction;</li>
     * <li>on the side of a straight truss or corner: from the centre of the clicked face, away from that face;</li>
     * <li>anywhere else: centred on the cell, away from the direction you look at.</li>
     * </ul>
     * The angle of the tool is added to that direction. The cell that contains the pivot of the piece holds it.
     */
    public static InteractionResult place(BlockPlaceContext ctx, MultiPartBlock truss, int bend) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.sidedSuccess(true);
        Player player = ctx.getPlayer();
        Direction face = ctx.getClickedFace();
        Vec3 hit = ctx.getClickLocation();
        BlockPos place = ctx.getClickedPos();
        BlockPos clicked = clickedBlock(ctx);
        BlockState clickedState = level.getBlockState(clicked);

        double baseDeg = 0.0;
        double px = 0.0, pz = 0.0;
        int baseY = place.getY();
        boolean fromStart = false;
        boolean continued = false;         // goes straight on from a piece, from the remembered end or from the flank of a turned tower: the angle of the tool is a bend only if sneaking
        double startX = 0.0, startZ = 0.0;
        int pushX = 0, pushZ = 0;          // outward normal of the face the piece is attached to (see AngleMath.holderCell)

        if (clickedState.getBlock() instanceof AngledHolderBlock && level.getBlockEntity(clicked) instanceof AngledHolderBlockEntity holder) {
            AngledPiece prev = pieceEndNear(holder, hit);
            if (prev != null) {
                double[] e = prev.end(holder.getBlockPos());
                baseDeg = prev.angle;
                startX = e[0];
                startZ = e[1];
                baseY = (int) Math.round(prev.absY(holder.getBlockPos()));
                fromStart = true;
                continued = true;
            }
        }
        if (!fromStart && !ctx.replacingClickedOnBlock() && face.getAxis().isHorizontal() && turnedTower(level, clicked) != null) {
            // the side of a tower turned by the angle tool: the truss leaves from the flange of the joint, which points in the turned direction
            float towerAngle = turnedTower(level, clicked).angle();
            continued = true;       // the truss takes the angle of the tower by itself: the tool is not needed (sneak to add its angle as a bend)
            baseDeg = AngleMath.towerFlankAngle(face.getStepX(), face.getStepZ(), towerAngle);
            double[] u = AngleMath.dir(baseDeg);
            startX = clicked.getX() + 0.5 + 0.5 * u[0];
            startZ = clicked.getZ() + 0.5 + 0.5 * u[1];
            baseY = clicked.getY();
            pushX = face.getStepX();
            pushZ = face.getStepZ();
            fromStart = true;
        }
        if (!fromStart && !ctx.replacingClickedOnBlock() && face.getAxis().isHorizontal()
            && clickedState.getBlock() instanceof MultiPartBlock other && (other.isChain() && truss.isChain() || barrier(other) && barrier(truss))) {
            // the side of a straight truss (a straight barrier for a barrier): the piece leaves from the middle of that face
            baseDeg = AngleMath.compassOfStep(face.getStepX(), face.getStepZ());
            startX = clicked.getX() + 0.5 + 0.5 * face.getStepX();
            startZ = clicked.getZ() + 0.5 + 0.5 * face.getStepZ();
            baseY = clicked.getY() - other.rowOf(clickedState);
            pushX = face.getStepX();
            pushZ = face.getStepZ();
            fromStart = true;
        }

        if (!fromStart && !attachesTo(clickedState)) {
            // not on a truss: if the click is near the end of the line just placed, carry the line on, whatever the aim
            ChainEnd end = chainNear(level, player, place, barrier(truss));
            if (end != null) {
                baseDeg = end.deg();
                startX = end.x();
                startZ = end.z();
                baseY = (int) Math.round(end.y());
                fromStart = true;
                continued = true;
            }
        }
        if (continued && (player == null || !player.isShiftKeyDown())) bend = 0;     // straight on; sneak to add the angle of the tool as a bend

        double deg;
        if (fromStart) {
            deg = AngleMath.norm360(baseDeg + bend);
            double[] p = AngleMath.pivotFromStart(startX, startZ, deg);
            px = p[0];
            pz = p[1];
        } else {
            Direction look = ctx.getHorizontalDirection();
            deg = AngleMath.norm360(AngleMath.compassOfStep(look.getStepX(), look.getStepZ()) + bend);
            px = place.getX() + 0.5;
            pz = place.getZ() + 0.5;
        }

        int[] cell = AngleMath.holderCell(px, pz, pushX, pushZ);
        BlockPos holderPos = new BlockPos(cell[0], baseY, cell[1]);
        if (!level.isInWorldBounds(holderPos) || !level.isInWorldBounds(holderPos.above(truss.sizeY() - 1))) return fail(player, "message.concertstage.angle_blocked");
        BlockState atHolder = level.getBlockState(holderPos);
        boolean existing = atHolder.getBlock() instanceof AngledHolderBlock;
        if (!existing && !atHolder.canBeReplaced()) return fail(player, "message.concertstage.angle_blocked");

        if (!existing) level.setBlock(holderPos, StageContent.ANGLED_HOLDER.defaultBlockState(), 3);
        if (!(level.getBlockEntity(holderPos) instanceof AngledHolderBlockEntity be)) return fail(player, "message.concertstage.angle_blocked");
        String name = BuiltInRegistries.BLOCK.getKey(truss).getPath();
        be.add(AngledPiece.at(name, holderPos, px, baseY, pz, (float) deg));
        if (player != null) {
            double[] end = AngleMath.endPoint(px, pz, deg, truss.sizeX());
            CHAINS.put(player.getUUID(), new ChainEnd(level.dimension(), end[0], baseY, end[1], deg, holderPos, continued, barrier(truss)));
            player.displayClientMessage(Component.translatable("message.concertstage.angle_placed", Math.round(deg)), true);
        }

        level.playSound(null, holderPos, truss.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
        if (player != null && !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.sidedSuccess(false);
    }

    private static InteractionResult fail(Player player, String key) {
        if (player != null) player.displayClientMessage(Component.translatable(key), true);
        return InteractionResult.FAIL;
    }

    /** Turns a tower that has just been placed by {@code bend} degrees about its own axis (every cell of the tower stores it). */
    public static void angleTower(BlockPlaceContext ctx, MotorTowerBlock tower, int bend) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MotorTowerBlock)) return;
        BlockPos main = tower.mainPos(state, pos);
        for (BlockPos cell : tower.cells(main)) {
            if (level.getBlockEntity(cell) instanceof TowerBlockEntity be) be.setAngle(bend);
        }
    }
}
