package com.concertstage.light;

import com.concertstage.StageContent;
import com.concertstage.light.core.Live;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rev. 1.3.0-2 — CSC ART-NET NODE (asked 12 November, 22 h 38: "une console Art-Net : on pourra rentrer manuellement l'adresse IP qui
 * connectera la console à l'application externe"). A small rack node, one block: the fixtures patched on it (configuration card, or the
 * patch screen of a fixture) are driven by the Art-Net the external application sends. It is a desk of its own ({@link LumicoreBlockEntity},
 * same block entity type as the LUMICORE X1) that starts in External mode; its screen ({@code ArtNetConsoleScreen}) sets the IP of the
 * application (source filter), the local interface, the UDP port and the Art-Net universes, and shows what arrives. The advanced
 * functions (recorder, DMX monitor, patch list) stay on the full desk screen, opened from the node.
 *
 * <p>The Art-Net receiver of CSC is one per world (one UDP port): the IP set on a node applies to the whole world, as in
 * Settings → Network → Art-Net of the LUMICORE X1.
 */
public class ArtNetConsoleBlock extends LumicoreBlock {
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // a 19-inch node lying on a stand: 14 x 6 x 10 pixels, front face to the operator
            boolean x = d.getAxis() == Direction.Axis.X;
            SHAPES[d.get2DDataValue()] = x ? Shapes.box(3 / 16.0, 0, 1 / 16.0, 13 / 16.0, 7 / 16.0, 15 / 16.0)
                : Shapes.box(1 / 16.0, 0, 3 / 16.0, 15 / 16.0, 7 / 16.0, 13 / 16.0);
        }
    }

    public ArtNetConsoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof LumicoreBlockEntity be) {
            be.show.name = "Art-Net node";
            ExternalControl.mode(sl, be, Live.EXTERNAL);         // driven by the external application from the start
            be.changed();
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(StageContent.LIGHT_PATCH_TOOL) || held.is(StageContent.CONFIG_CARD)) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) com.concertstage.client.ClientHooks.openArtNetConsole(pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Whether the desk at this block entity is an Art-Net node (not a LUMICORE X1). */
    public static boolean isNode(LumicoreBlockEntity be) {
        return be.getBlockState().getBlock() instanceof ArtNetConsoleBlock;
    }
}
