package com.concertstage.vision;

import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CSC VISION PROCESSOR VP1 (1.3.0): four video outputs ({@link OutputState}), each feeding any number of LED walls. The server owns
 * the state (TAKE, transitions, automatic switcher) and sends it to the players of the dimension when it changes ({@link VisionNet}):
 * nothing is sent per frame, every client mixes the images itself from the same timing.
 */
public class VisionProcessorBlockEntity extends BlockEntity {
    public static final int OUTPUTS = 4;
    public final OutputState[] outputs = new OutputState[OUTPUTS];
    public String name = "VP1";

    public VisionProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.VISION_PROCESSOR_ENTITY, pos, state);
        for (int i = 0; i < OUTPUTS; i++) outputs[i] = new OutputState();
        outputs[1].program = OutputState.BLACK;
        outputs[2].program = OutputState.BLACK;
        outputs[3].program = OutputState.BLACK;
    }

    /** The state that output {@code i} shows (following a mirror). */
    public OutputState effective(int i) {
        OutputState o = outputs[Math.max(0, Math.min(OUTPUTS - 1, i))];
        int guard = 0;
        while (o.mirrorOf >= 0 && o.mirrorOf < OUTPUTS && guard++ < OUTPUTS) {
            OutputState m = outputs[o.mirrorOf];
            if (m == o) break;
            o = m;
        }
        return o;
    }

    /** TAKE: preview goes to program with the output's transition. */
    public void take(int out, long now) {
        OutputState o = outputs[out];
        String next = o.preview;
        o.preview = o.program;
        cut(out, next, now);
    }

    /** Puts {@code src} on program of {@code out} with its transition; restarts the countdown of the automatic switcher. */
    public void cut(int out, String src, long now) {
        OutputState o = outputs[out];
        if (src.equals(o.program) && o.progress(now) >= 1) return;
        o.from = o.program;
        o.program = src;
        o.transStart = now;
        if (src.startsWith("media:")) { o.mediaStart = now; o.pausedAt = -1; }
        if (o.auto) o.nextSwitch = now + o.interval * 20L;
        changed();
    }

    /** Server tick: the automatic switchers. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, VisionProcessorBlockEntity be) {
        if (level.isClientSide || (level.getGameTime() & 3) != 0) return;
        long now = level.getGameTime();
        for (int i = 0; i < OUTPUTS; i++) {
            OutputState o = be.outputs[i];
            if (!o.auto || o.autoPaused || o.autoCams.isEmpty() || now < o.nextSwitch) continue;
            String next = be.nextAuto(o);
            if (next == null) { o.nextSwitch = now + o.interval * 20L; continue; }
            o.lastAuto = next;
            be.cut(i, next, now);
        }
    }

    /** Next camera of the automatic switcher: in order, or at random without showing the same camera twice in a row. */
    private String nextAuto(OutputState o) {
        java.util.List<String> cams = new java.util.ArrayList<>();
        for (long c : o.autoCams) cams.add(OutputState.cam(BlockPos.of(c)));
        if (cams.isEmpty()) return null;
        if (cams.size() == 1) return cams.get(0);
        if (o.random) {
            java.util.List<String> pool = new java.util.ArrayList<>(cams);
            pool.remove(o.program);
            return pool.get(level.random.nextInt(pool.size()));
        }
        int i = cams.indexOf(o.program);
        return cams.get((i + 1) % cams.size());
    }

    public void changed() {
        setChanged();
        if (level instanceof ServerLevel sl) {
            BlockState st = getBlockState();
            level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
            VisionNet.outputsChanged(sl, this);
        }
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag l = new ListTag();
        for (OutputState o : outputs) l.add(o.save());
        tag.put("Outputs", l);
        tag.putString("Name", name);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ListTag l = tag.getList("Outputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < OUTPUTS && i < l.size(); i++) outputs[i] = OutputState.load(l.getCompound(i));
        if (tag.contains("Name")) name = tag.getString("Name");
    }
}
