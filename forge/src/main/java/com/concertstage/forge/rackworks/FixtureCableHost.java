package com.concertstage.forge.rackworks;

import com.concertstage.light.FixtureBlock;
import com.concertstage.light.FixtureBlockEntity;
import com.concertstage.light.core.FixtureProfile;
import com.rackworks.power.GearHost;
import com.rackworks.power.NodeKey;
import com.rackworks.power.Placement;
import com.rackworks.power.Port;
import com.rackworks.power.PortLinks;
import com.rackworks.power.PortType;
import com.rackworks.power.PowerGrid;
import com.rackworks.power.PowerModel;
import com.rackworks.power.PowerNode;
import com.rackworks.power.PowerWorld;
import com.rackworks.rigging.Affine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 1.2.2 — one CSC fixture as a RackWorks power node: a powerCON inlet on its base. The power network counts it as a load
 * ({@link FixtureProfile#watts}) and tells it when its supply is there; the fixture renders dark without it, and a halogen cools
 * out through the lamp model.
 *
 * <p>The cable links are saved with the fixture under RackWorks' "Power" name, so a hoist that moves the fixture keeps its
 * cables: RackWorks repairs the far ends when the block entity loads at its new position. The DMX sockets (5-pin in / out) come
 * with the wired-DMX step; the connectors are already on the model so the cable item has something to plug into later.
 */
final class FixtureCableHost implements GearHost, PowerNode {

    private final FixtureBlockEntity be;
    private final FixtureProfile profile;
    private final List<Port> powerPorts;
    private final PortLinks powerLinks = new PortLinks();

    FixtureCableHost(FixtureBlockEntity be) {
        this.be = be;
        this.profile = be.block() != null ? be.block().profile() : FixtureProfile.VORTEX_X1;
        // one inlet, 16 A single phase (powerCON): the same socket the fixture has on its base
        this.powerPorts = List.of(new Port("block.concertstage.connector.mains", -1, true, PortType.SINGLE, 16, 0, 0, 0,
            List.of("powercon0")));
    }

    // ------------------------------------------------------------------ host

    @Override public List<PowerNode> powerNodes() { return List.of(this); }

    // ------------------------------------------------------------------ power network

    @Override public NodeKey key() { return NodeKey.of(be.getBlockPos().asLong()); }
    @Override public String nodeName() { return "block.concertstage." + profile.id; }
    @Override public String gearId() { return RackworksCompat.gearId(profile); }
    @Override public List<Port> ports() { return powerPorts; }
    @Override public PortLinks links() { return powerLinks; }

    @Override
    public Placement placement() {
        BlockState state = be.getBlockState();
        BlockPos pos = be.getBlockPos();
        Direction facing = state.getValue(FixtureBlock.FACING);
        boolean hung = state.getValue(FixtureBlock.HUNG);
        // the pose of FixtureRenderer.basePose: fine yaw, pitch and roll about the model origin, the hang flip, then the base of
        // the cell (its top when hung); RackWorks turns the whole model to the block's facing itself
        Affine pose = Affine.rotY(be.rotYaw).mul(Affine.rotX(be.rotPitch)).mul(Affine.rotZ(be.rotRoll));
        if (hung) pose = pose.mul(Affine.rotZ(180));
        return new Placement(pos.getX(), pos.getY(), pos.getZ(), facing,
            0.5 + be.offX, (hung ? 1.0 : 0.0) + be.offY, 0.5 + be.offZ, pose);
    }

    @Override
    public void describe(PowerModel.Node node) {
        // one inlet, no switch of its own: what reaches the inlet reaches the fixture. The load is the nameplate power.
        node.through = true;
        node.load(profile.id, profile.watts, 0, false);
    }

    @Override
    public void apply(PowerModel.Result result) {
        // no mains, no light: the renderer makes the fixture dark, and a halogen cools out through its filament
        be.setMains(result.inletMask != 0, (float) result.level);
    }

    @Override
    public void linksChanged() {
        be.setChanged();
        Level level = be.getLevel();
        if (level == null || level.isClientSide) return;
        BlockState state = be.getBlockState();
        level.sendBlockUpdated(be.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
        PowerGrid power = PowerWorld.grid(level);
        if (power != null) power.changed();
    }

    // ------------------------------------------------------------------ saved with the fixture

    void save(CompoundTag tag) {
        powerLinks.save(tag, "Power", be.getBlockPos().asLong());
    }

    void load(CompoundTag tag) {
        powerLinks.load(tag, "Power", powerPorts.size(), be.getBlockPos().asLong());
    }
}
