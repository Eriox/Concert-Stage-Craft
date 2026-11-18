package com.concertstage.client;

import com.concertstage.lift.LiftPayload;
import com.concertstage.lift.LiftStructureEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client side of {@link LiftPayload} (1.1.0): the pieces of the blocks of a travelling structure are put together, unpacked and given to
 * the entity. If the entity is not there yet, the blocks wait a few seconds for it.
 */
public final class LiftClient {
    private static final class Assembly {
        final int id;
        final byte[] data;
        int received;
        Assembly(int id, int length) { this.id = id; this.data = new byte[length]; }
    }

    private record Ready(List<LiftStructureEntity.Entry> entries, long since) {}

    private static final Map<Integer, Assembly> PARTS = new HashMap<>();
    private static final Map<Integer, Ready> READY = new HashMap<>();
    /** Largest structure accepted (compressed): a safety against a broken server, far above any real stage. */
    private static final int MAX_BYTES = 256 * 1024 * 1024;

    private LiftClient() {}

    /** One piece (message AudioNet.S_LIFT), on the client thread. */
    public static void receive(FriendlyByteBuf b) {
        int entity = b.readVarInt();
        int payload = b.readInt();
        int length = b.readVarInt();
        int offset = b.readVarInt();
        byte[] part = b.readByteArray(LiftPayload.CHUNK + 16);
        if (length < 0 || length > MAX_BYTES || offset < 0 || offset + part.length > length) return;
        Assembly a = PARTS.get(entity);
        if (a == null || a.id != payload || a.data.length != length) {
            a = new Assembly(payload, length);
            PARTS.put(entity, a);
        }
        System.arraycopy(part, 0, a.data, offset, part.length);
        a.received += part.length;
        if (a.received < length) return;
        PARTS.remove(entity);
        List<LiftStructureEntity.Entry> entries = LiftPayload.decode(a.data);
        if (entries == null) return;
        if (!give(entity, entries)) READY.put(entity, new Ready(entries, System.currentTimeMillis()));
    }

    private static boolean give(int entityId, List<LiftStructureEntity.Entry> entries) {
        var level = Minecraft.getInstance().level;
        if (level == null) return false;
        if (level.getEntity(entityId) instanceof LiftStructureEntity lift) {
            lift.applyBlocks(entries);
            return true;
        }
        return false;
    }

    /** Every client tick: blocks that arrived before their entity. */
    public static void tick() {
        if (READY.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Integer, Ready>> it = READY.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Ready> e = it.next();
            if (give(e.getKey(), e.getValue().entries) || now - e.getValue().since > 10_000) it.remove();
        }
    }

    public static void reset() {
        PARTS.clear();
        READY.clear();
    }
}
