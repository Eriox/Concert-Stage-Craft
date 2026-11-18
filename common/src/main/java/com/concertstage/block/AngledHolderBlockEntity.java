package com.concertstage.block;

import com.concertstage.StageContent;
import com.concertstage.angle.AngledPiece;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The trusses placed at an angle whose pivot is in this cell. */
public class AngledHolderBlockEntity extends BlockEntity {
    private final List<AngledPiece> pieces = new ArrayList<>();
    /** Set (not saved) just before the cell is removed on purpose, so the pieces are not dropped. */
    public boolean noDrops;

    public AngledHolderBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.ANGLED_HOLDER_ENTITY, pos, state);
    }

    public List<AngledPiece> pieces() {
        return Collections.unmodifiableList(pieces);
    }

    public void add(AngledPiece piece) {
        pieces.add(piece);
        changed();
    }

    private void changed() {
        setChanged();
        Level lvl = getLevel();
        if (lvl != null && !lvl.isClientSide) {
            BlockState st = getBlockState();
            lvl.sendBlockUpdated(worldPosition, st, st, 3);
        }
    }

    /** The pieces stick out of the cell: tell the renderer how far (used by Forge for culling, ignored by Fabric). */
    public AABB getRenderBoundingBox() {
        AABB box = new AABB(worldPosition);
        for (AngledPiece p : pieces) {
            double[] s = p.start(worldPosition);
            double[] e = p.end(worldPosition);
            double y = p.absY(worldPosition);
            box = box.minmax(new AABB(s[0], y, s[1], e[0], y + p.height(), e[1]));
        }
        return box.inflate(1.0);
    }

    // ------------------------------------------------------------ saving / syncing
    public static ListTag writePieces(List<AngledPiece> list) {
        ListTag out = new ListTag();
        for (AngledPiece p : list) out.add(p.save());
        return out;
    }

    public static List<AngledPiece> readPieces(CompoundTag tag) {
        List<AngledPiece> out = new ArrayList<>();
        ListTag list = tag.getList("Pieces", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) out.add(AngledPiece.load(list.getCompound(i)));
        return out;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Pieces", writePieces(pieces));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        pieces.clear();
        pieces.addAll(readPieces(tag));
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
