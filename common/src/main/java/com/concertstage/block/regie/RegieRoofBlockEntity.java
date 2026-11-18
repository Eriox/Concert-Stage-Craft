package com.concertstage.block.regie;

import com.concertstage.StageContent;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * FOH tarp roof: covers the whole FOH (régie) structure it was set on, from the top of its highest element, with a little overhang and a
 * valance all round. Its size follows the structure (checked every 5 s); without a FOH element under it any more, it comes down (item
 * given back). Coordinates are relative to this anchor cell.
 */
public class RegieRoofBlockEntity extends BlockEntity {
    public static final int GABLE = 0, FLAT = 1, MONO = 2;
    /** aurex.2: flag of the CSC RoofCommand message: take the roof back. */
    public static final int REMOVE = 1 << 10;

    /** Removes the roof and gives its tarp back (into the player's inventory when there is one, else dropped; nothing in creative). */
    public void takeBack(net.minecraft.world.entity.player.Player player) {
        if (level == null || level.isClientSide) return;
        BlockPos o = getBlockPos();
        level.removeBlock(o, false);
        level.playSound(null, o, net.minecraft.sounds.SoundEvents.WOOL_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 1, 0.9F);
        if (player != null && player.getAbilities().instabuild) return;
        ItemStack back = new ItemStack(StageContent.REGIE_ROOF_TARP);
        if (player == null || !player.addItem(back)) Block.popResource(level, o, back);
    }
    private static final int MAX_CELLS = 2048;

    private double x0, z0, x1, z1, eave;
    private int shape = GABLE;
    /** Set with the CSC RoofCommand: overhang (tenths of a metre), valance depth (5 cm steps), pitch (0..4), tarp colour. */
    private int overhang10 = 2, valance20 = 5, pitch = 2, color = 0;
    public static final String[] COLORS = {"black", "grey", "white", "blue", "red"};
    private static final double[] PITCH = {0.12, 0.2, 0.28, 0.38, 0.5};
    private BlockPos seed = BlockPos.ZERO;

    public RegieRoofBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.REGIE_ROOF_ENTITY, pos, state);
    }

    public double x0() { return x0; } public double z0() { return z0; } public double x1() { return x1; } public double z1() { return z1; }
    public double eave() { return eave; } public int shape() { return shape; }
    public int overhang10() { return overhang10; } public int valance20() { return valance20; } public int pitch() { return pitch; } public int color() { return color; }
    public double valance() { return valance20 * 0.05; }
    public double pitchFactor() { return PITCH[pitch]; }
    public String colorName() { return COLORS[color]; }

    /** From the CSC RoofCommand (checked): overhang 0..1 m, valance 0..60 cm, pitch, shape, colour; the roof is fitted again. */
    public void apply(int overhang10, int valance20, int flags) {
        if ((flags & REMOVE) != 0) { takeBack(null); return; }
        this.overhang10 = Math.max(0, Math.min(10, overhang10));
        this.valance20 = Math.max(0, Math.min(12, valance20));
        this.shape = Math.floorMod(flags & 3, 3);
        this.pitch = Math.max(0, Math.min(4, (flags >> 2) & 7));
        this.color = Math.max(0, Math.min(COLORS.length - 1, (flags >> 5) & 7));
        if (level == null || !fit(getBlockPos().offset(seed))) sync();
        else sync();
    }

    /** FOH kit piece (not its tarps, not this roof). */
    public static boolean isRegie(BlockState s) {
        String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return path.startsWith("regie_") && !path.startsWith("regie_tarp") && !path.startsWith("regie_roof");
    }

    /** The FOH structure around {@code start}: {minX, minZ, maxX, maxZ, top} in world units, or null. */
    public static double[] measure(Level level, BlockPos start) {
        if (!isRegie(level.getBlockState(start))) return null;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(start); seen.add(start);
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            BlockState s = level.getBlockState(p);
            VoxelShape shape = s.getShape(level, p);
            AABB box = shape.isEmpty() ? new AABB(0.4, 0, 0.4, 0.6, 1, 0.6) : shape.bounds();
            b[0] = Math.min(b[0], p.getX() + Math.max(0, box.minX)); b[1] = Math.min(b[1], p.getZ() + Math.max(0, box.minZ));
            b[2] = Math.max(b[2], p.getX() + Math.min(1, box.maxX)); b[3] = Math.max(b[3], p.getZ() + Math.min(1, box.maxZ));
            b[4] = Math.max(b[4], p.getY() + box.maxY);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos q = p.offset(dx, dy, dz);
                if (seen.size() >= MAX_CELLS || seen.contains(q) || !level.isLoaded(q)) continue;
                if (isRegie(level.getBlockState(q))) { seen.add(q); queue.add(q); }
            }
        }
        return b;
    }

    /** Fits the roof to the structure measured from {@code start}; false when there is none. */
    public boolean fit(BlockPos start) {
        double[] b = measure(level, start);
        if (b == null) return false;
        BlockPos o = getBlockPos();
        double ov = overhang10 / 10.0;
        double nx0 = b[0] - ov - o.getX(), nz0 = b[1] - ov - o.getZ(), nx1 = b[2] + ov - o.getX(), nz1 = b[3] + ov - o.getZ();
        double ne = b[4] - o.getY();
        BlockPos ns = start.subtract(o);
        boolean changed = Math.abs(nx0 - x0) > 1e-3 || Math.abs(nz0 - z0) > 1e-3 || Math.abs(nx1 - x1) > 1e-3 || Math.abs(nz1 - z1) > 1e-3
            || Math.abs(ne - eave) > 1e-3 || !ns.equals(seed);
        x0 = nx0; z0 = nz0; x1 = nx1; z1 = nz1; eave = ne; seed = ns;
        if (changed) sync();
        return true;
    }

    public void cycleShape() {
        shape = (shape + 1) % 3;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    public void serverTick() {
        if (level == null || (level.getGameTime() + getBlockPos().asLong()) % 100 != 0) return;
        BlockPos o = getBlockPos();
        BlockPos start = o.offset(seed);
        if (!isRegie(level.getBlockState(start))) {
            start = null;
            for (int y = -1; y >= -3 && start == null; y--)
                for (int x = (int) Math.floor(x0); x <= Math.floor(x1) && start == null; x++)
                    for (int z = (int) Math.floor(z0); z <= Math.floor(z1) && start == null; z++)
                        if (isRegie(level.getBlockState(o.offset(x, y, z)))) start = o.offset(x, y, z);
        }
        if (start == null || !fit(start)) {
            level.removeBlock(o, false);
            Block.popResource(level, o, new ItemStack(StageContent.REGIE_ROOF_TARP));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("X0", x0); tag.putDouble("Z0", z0); tag.putDouble("X1", x1); tag.putDouble("Z1", z1); tag.putDouble("Eave", eave);
        tag.putInt("Shape", shape); tag.putLong("Seed", seed.asLong());
        tag.putInt("Overhang", overhang10); tag.putInt("Valance", valance20); tag.putInt("Pitch", pitch); tag.putInt("Color", color);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        x0 = clamp(tag.getDouble("X0")); z0 = clamp(tag.getDouble("Z0")); x1 = clamp(tag.getDouble("X1")); z1 = clamp(tag.getDouble("Z1"));
        eave = Math.max(-4, Math.min(4, tag.getDouble("Eave")));
        shape = Math.floorMod(tag.getInt("Shape"), 3); seed = BlockPos.of(tag.getLong("Seed"));
        overhang10 = tag.contains("Overhang") ? Math.max(0, Math.min(10, tag.getInt("Overhang"))) : 2;
        valance20 = tag.contains("Valance") ? Math.max(0, Math.min(12, tag.getInt("Valance"))) : 5;
        pitch = tag.contains("Pitch") ? Math.max(0, Math.min(4, tag.getInt("Pitch"))) : 2;
        color = Math.max(0, Math.min(COLORS.length - 1, tag.getInt("Color")));
    }

    private static double clamp(double v) { return Double.isFinite(v) ? Math.max(-64, Math.min(64, v)) : 0; }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    /** Forge: the roof is drawn far outside its anchor cell. */
    public AABB getRenderBoundingBox() {
        BlockPos o = getBlockPos();
        return new AABB(o.getX() + x0, o.getY() + eave - valance() - 0.1, o.getZ() + z0, o.getX() + x1, o.getY() + eave + 3, o.getZ() + z1);
    }
}
