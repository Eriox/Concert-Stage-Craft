package com.concertstage.lift;

import com.concertstage.StageContent;
import com.concertstage.audio.net.AudioNet;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks of a travelling structure, sent to the players who see it (1.1.0). Until 1.1.0 they went in the entity data, a single packet
 * limited to 2 MB, hence the old limit of 24 000 blocks per lift. Now they are packed (palette of block states, positions as small
 * differences, then the data of the angled trusses and of the blocks with their own renderer), compressed, and sent in pieces of
 * {@link #CHUNK} bytes on the mod's channel: there is no size limit any more. The entity data only carries the movement.
 */
public final class LiftPayload {
    public static final int CHUNK = 512 * 1024;

    private LiftPayload() {}

    /** Packed and compressed blocks. */
    public static byte[] encode(List<LiftStructureEntity.Entry> entries) {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer(Math.max(256, entries.size() * 4)));
        try {
            Map<BlockState, Integer> index = new HashMap<>();
            List<BlockState> palette = new ArrayList<>();
            int[] pi = new int[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                BlockState s = entries.get(i).state;
                Integer at = index.get(s);
                if (at == null) { at = palette.size(); index.put(s, at); palette.add(s); }
                pi[i] = at;
            }
            b.writeVarInt(entries.size());
            b.writeVarInt(palette.size());
            for (BlockState s : palette) b.writeVarInt(Block.BLOCK_STATE_REGISTRY.getId(s));
            int px = 0, py = 0, pz = 0;
            ListTag angled = new ListTag(), rendered = new ListTag();
            for (int i = 0; i < entries.size(); i++) {
                LiftStructureEntity.Entry e = entries.get(i);
                b.writeVarInt(pi[i]);
                b.writeVarInt(zig(e.rel.getX() - px));
                b.writeVarInt(zig(e.rel.getY() - py));
                b.writeVarInt(zig(e.rel.getZ() - pz));
                px = e.rel.getX(); py = e.rel.getY(); pz = e.rel.getZ();
                // the holder's block model is empty: its pieces are what is drawn
                if (e.state.getBlock() == StageContent.ANGLED_HOLDER && e.nbt != null) {
                    CompoundTag c = new CompoundTag();
                    c.putInt("I", i);
                    c.put("Pieces", e.nbt.getList("Pieces", Tag.TAG_COMPOUND).copy());
                    angled.add(c);
                }
                if (e.renderNbt != null) {
                    CompoundTag c = new CompoundTag();
                    c.putInt("I", i);
                    c.put("Data", e.renderNbt);
                    rendered.add(c);
                }
            }
            CompoundTag extra = new CompoundTag();
            extra.put("P", angled);
            extra.put("BE", rendered);
            b.writeNbt(extra);
            byte[] raw = new byte[b.readableBytes()];
            b.getBytes(b.readerIndex(), raw);
            Deflater d = new Deflater(Deflater.BEST_SPEED);
            try {
                d.setInput(raw);
                d.finish();
                ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 3 + 64);
                byte[] buf = new byte[64 * 1024];
                while (!d.finished()) out.write(buf, 0, d.deflate(buf));
                return out.toByteArray();
            } finally {
                d.end();
            }
        } finally {
            b.release();
        }
    }

    /** Client: the blocks back from {@link #encode}, or null if the data is damaged. */
    public static List<LiftStructureEntity.Entry> decode(byte[] packed) {
        Inflater inf = new Inflater();
        FriendlyByteBuf b = null;
        try {
            inf.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length * 4);
            byte[] buf = new byte[64 * 1024];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                out.write(buf, 0, n);
            }
            b = new FriendlyByteBuf(Unpooled.wrappedBuffer(out.toByteArray()));
            int n = b.readVarInt();
            int ps = b.readVarInt();
            BlockState[] palette = new BlockState[ps];
            for (int i = 0; i < ps; i++) palette[i] = Block.BLOCK_STATE_REGISTRY.byId(b.readVarInt());
            List<LiftStructureEntity.Entry> entries = new ArrayList<>(n);
            int x = 0, y = 0, z = 0;
            int[] map = new int[n];                                   // index sent -> index kept (unknown states are dropped)
            for (int i = 0; i < n; i++) {
                int p = b.readVarInt();
                x += unzig(b.readVarInt()); y += unzig(b.readVarInt()); z += unzig(b.readVarInt());
                BlockState s = p >= 0 && p < ps ? palette[p] : null;
                map[i] = s == null ? -1 : entries.size();
                if (s != null) entries.add(new LiftStructureEntity.Entry(new BlockPos(x, y, z), s, null));
            }
            CompoundTag extra = b.readAnySizeNbt();
            if (extra != null) {
                ListTag angled = extra.getList("P", Tag.TAG_COMPOUND);
                for (int i = 0; i < angled.size(); i++) {
                    CompoundTag c = angled.getCompound(i);
                    int at = c.getInt("I");
                    if (at < 0 || at >= n || map[at] < 0) continue;
                    LiftStructureEntity.Entry e = entries.get(map[at]);
                    if (e.state.getBlock() == StageContent.ANGLED_HOLDER) e.nbt = c;
                }
                ListTag rendered = extra.getList("BE", Tag.TAG_COMPOUND);
                for (int i = 0; i < rendered.size(); i++) {
                    CompoundTag c = rendered.getCompound(i);
                    int at = c.getInt("I");
                    if (at < 0 || at >= n || map[at] < 0) continue;
                    LiftStructureEntity.Entry e = entries.get(map[at]);
                    if (e.state.hasBlockEntity() && e.state.getBlock() != StageContent.ANGLED_HOLDER) e.renderNbt = c.getCompound("Data");
                }
            }
            return entries;
        } catch (DataFormatException | RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: damaged lift data", e);
            return null;
        } finally {
            inf.end();
            if (b != null) b.release();
        }
    }

    /** Sends the packed blocks of entity {@code entityId} to one player, in pieces. */
    public static void send(ServerPlayer player, int entityId, int payloadId, byte[] packed) {
        for (int off = 0; off < packed.length || off == 0; off += CHUNK) {
            int len = Math.min(CHUNK, packed.length - off);
            FriendlyByteBuf m = AudioNet.message(AudioNet.S_LIFT);
            m.writeVarInt(entityId);
            m.writeInt(payloadId);
            m.writeVarInt(packed.length);
            m.writeVarInt(off);
            m.writeByteArray(java.util.Arrays.copyOfRange(packed, off, off + Math.max(0, len)));
            AudioNet.toClient(player, m);
            if (packed.length == 0) break;
        }
    }

    private static int zig(int v) {
        return (v << 1) ^ (v >> 31);
    }

    private static int unzig(int v) {
        return (v >>> 1) ^ -(v & 1);
    }
}
