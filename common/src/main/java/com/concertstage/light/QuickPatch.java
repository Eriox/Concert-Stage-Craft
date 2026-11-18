package com.concertstage.light;

import com.concertstage.StageContent;
import com.concertstage.light.core.Bytes;
import com.concertstage.light.core.Commands;
import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.core.Show;
import com.concertstage.light.dmx.ArtNetSettings;
import com.concertstage.light.dmx.DmxMode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Rev. 1.3.0-2 (asked 12 November, 22 h 38) — patching without opening the desk:
 * <ul>
 *   <li>the <b>configuration card</b> ({@link ConfigCardItem}): linked to a console (CSC ART-NET NODE or LUMICORE X1), it holds a
 *   universe, a start address, a DMX mode and "auto increment"; a right click on a fixture patches it there and moves the address on
 *   by the footprint of the fixture in that mode (next universe at 1 when it would pass 512);</li>
 *   <li>the <b>patch of a fixture from its own screen</b> (right click on the fixture, empty hand): universe, address, mode, sent here;
 *   a fixture not patched yet goes to the nearest console within {@value #REACH} blocks.</li>
 * </ul>
 * Everything goes through the desk commands (PATCH_ADD via {@link LightServer#patch}, then PATCH_DMX), so the show, the undo history and
 * the clients stay as with the desk itself. Server side only.
 */
public final class QuickPatch {
    public static final int REACH = 64;
    /** Card mode: the mode the fixture already has. */
    public static final int MODE_KEEP = -1;

    private QuickPatch() {}

    // ------------------------------------------------------------ card data
    public record Card(BlockPos console, int universe, int address, int mode, boolean autoInc) {}

    public static Card card(ItemStack stack) {
        CompoundTag t = stack.getTag();
        BlockPos c = t != null && t.contains("ConsoleX") ? new BlockPos(t.getInt("ConsoleX"), t.getInt("ConsoleY"), t.getInt("ConsoleZ")) : null;
        int u = t != null && t.contains("Universe") ? t.getInt("Universe") : 0;
        int a = t != null && t.contains("Address") ? t.getInt("Address") : 1;
        int m = t != null && t.contains("Mode") ? t.getInt("Mode") : MODE_KEEP;
        boolean inc = t == null || !t.contains("AutoInc") || t.getBoolean("AutoInc");
        return new Card(c, clampU(u), Math.max(1, Math.min(512, a)), Math.max(MODE_KEEP, Math.min(DmxMode.ULTRA, m)), inc);
    }

    public static void write(ItemStack stack, int universe, int address, int mode, boolean autoInc) {
        CompoundTag t = stack.getOrCreateTag();
        t.putInt("Universe", clampU(universe));
        t.putInt("Address", Math.max(1, Math.min(512, address)));
        t.putInt("Mode", Math.max(MODE_KEEP, Math.min(DmxMode.ULTRA, mode)));
        t.putBoolean("AutoInc", autoInc);
    }

    public static void link(ItemStack stack, BlockPos console) {
        CompoundTag t = stack.getOrCreateTag();
        t.putInt("ConsoleX", console.getX()); t.putInt("ConsoleY", console.getY()); t.putInt("ConsoleZ", console.getZ());
    }

    private static int clampU(int u) {
        return Math.max(ArtNetSettings.MIN_UNIVERSE, Math.min(ArtNetSettings.MAX_UNIVERSE, u));
    }

    /** C_CARD: hand, universe, address, mode, auto increment — saved on the card the player holds in that hand. */
    static void saveCard(ServerPlayer p, Bytes.Reader r) {
        int hand = r.varInt(), u = r.varInt(), a = r.varInt(), m = r.sInt();
        boolean inc = r.bool();
        if (!r.ok()) return;
        ItemStack stack = p.getItemInHand(hand == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!stack.is(StageContent.CONFIG_CARD)) return;
        if (!ArtNetSettings.validUniverse(u) || a < 1 || a > 512 || m < MODE_KEEP || m > DmxMode.ULTRA) {
            p.displayClientMessage(Component.translatable("message.concertstage.card_invalid"), true);
            return;
        }
        write(stack, u, a, m, inc);
        p.displayClientMessage(Component.translatable("message.concertstage.card_saved", u, a, modeName(m)), true);
    }

    // ------------------------------------------------------------ card on a fixture
    /** Right click of a fixture with the card: patch it on the linked console at the address of the card. */
    public static void cardOnFixture(ServerLevel level, ServerPlayer p, ItemStack stack, FixtureBlockEntity fe) {
        Card c = card(stack);
        if (c.console() == null || !level.isLoaded(c.console()) || !(level.getBlockEntity(c.console()) instanceof LumicoreBlockEntity desk)) {
            p.displayClientMessage(Component.translatable("message.concertstage.card_no_console"), true);
            return;
        }
        if (!desk.mayOperate(p)) { p.displayClientMessage(Component.translatable("message.concertstage.light_no_permission"), true); return; }
        Show.Patch patch = ensurePatched(level, desk, fe);
        if (patch == null) { p.displayClientMessage(Component.translatable("message.concertstage.card_failed"), true); return; }
        int mode = c.mode() == MODE_KEEP ? patch.dmxMode : c.mode();
        int foot = DmxMode.of(patch.profile(), mode).footprint();
        int u = c.universe(), a = c.address();
        if (a + foot - 1 > 512) {                                      // does not fit in what is left of the universe: next universe
            if (u >= ArtNetSettings.MAX_UNIVERSE) { p.displayClientMessage(Component.translatable("message.concertstage.card_full"), true); return; }
            u++; a = 1;
        }
        if (!setDmx(level, desk, patch, u, a, mode)) { p.displayClientMessage(Component.translatable("message.concertstage.card_failed"), true); return; }
        int nu = u, na = a;
        if (c.autoInc()) {
            na = a + foot;
            if (na > 512) { nu = Math.min(ArtNetSettings.MAX_UNIVERSE, u + 1); na = 1; }
        }
        write(stack, nu, na, c.mode(), c.autoInc());
        Component msg = Component.translatable("message.concertstage.card_patched", patch.profile().name, patch.fid, u, a, DmxMode.NAMES[mode], foot, nu, na);
        String clash = overlap(desk, patch);
        p.displayClientMessage(clash == null ? msg : msg.copy().append(Component.translatable("message.concertstage.patch_overlap", clash)), true);
    }

    // ------------------------------------------------------------ patch from the fixture screen
    /** C_FIXTURE_PATCH at the fixture position: universe, address, mode. */
    static void fixturePatch(ServerPlayer p, ServerLevel level, BlockPos pos, Bytes.Reader r) {
        int u = r.varInt(), a = r.varInt(), mode = r.varInt();
        if (!r.ok() || !level.isLoaded(pos) || p.distanceToSqr(Vec3.atCenterOf(pos)) > 32 * 32) return;
        if (!(level.getBlockEntity(pos) instanceof FixtureBlockEntity fe)) return;
        if (!ArtNetSettings.validUniverse(u) || a < 1 || a > 512 || mode < 0 || mode > DmxMode.ULTRA) {
            p.displayClientMessage(Component.translatable("message.concertstage.card_invalid"), true);
            return;
        }
        LumicoreBlockEntity desk = null;
        LumicoreBlockEntity linked = LightServer.linkedDesk(level, fe);
        if (linked != null) desk = linked;
        if (desk == null) desk = nearestDesk(level, pos, REACH);
        if (desk == null) { p.displayClientMessage(Component.translatable("message.concertstage.patch_no_console_near", REACH), true); return; }
        if (!desk.mayOperate(p)) { p.displayClientMessage(Component.translatable("message.concertstage.light_no_permission"), true); return; }
        Show.Patch patch = ensurePatched(level, desk, fe);
        if (patch == null) { p.displayClientMessage(Component.translatable("message.concertstage.card_failed"), true); return; }
        int foot = DmxMode.of(patch.profile(), mode).footprint();
        if (a + foot - 1 > 512) {
            p.displayClientMessage(Component.translatable("message.concertstage.patch_past_512", foot, 513 - foot), true);
            return;
        }
        if (!setDmx(level, desk, patch, u, a, mode)) { p.displayClientMessage(Component.translatable("message.concertstage.card_failed"), true); return; }
        Component msg = Component.translatable("message.concertstage.fixture_patched", patch.profile().name, patch.fid, u, a, DmxMode.NAMES[mode], foot);
        String clash = overlap(desk, patch);
        p.displayClientMessage(clash == null ? msg : msg.copy().append(Component.translatable("message.concertstage.patch_overlap", clash)), true);
    }

    // ------------------------------------------------------------ helpers
    /** The patch of {@code fe} in {@code desk}, patching it first if needed (null when the desk refused). */
    static Show.Patch ensurePatched(ServerLevel level, LumicoreBlockEntity desk, FixtureBlockEntity fe) {
        if (!desk.patches(fe) && !LightServer.patch(level, desk, fe)) return null;
        return desk.show.fixture(fe.fid());
    }

    static boolean setDmx(ServerLevel level, LumicoreBlockEntity desk, Show.Patch p, int u, int a, int mode) {
        return LightServer.apply(level, desk, Commands.op(Commands.PATCH_DMX).varInt(p.fid).varInt(u).varInt(a).varInt(mode)
            .bool(p.invertPan).bool(p.invertTilt).f32(p.panOffset).f32(p.tiltOffset).toBytes());
    }

    /** "#fid" of the first other fixture of the desk whose channels overlap those of {@code p}, or null. */
    static String overlap(LumicoreBlockEntity desk, Show.Patch p) {
        if (p.dmxMode == DmxMode.MATRIX) return null;
        int a0 = p.address, a1 = p.address + DmxMode.of(p.profile(), p.dmxMode).footprint() - 1;
        for (Show.Patch o : desk.show.patch) {
            if (o == p || o.fid == p.fid || o.universe != p.universe || o.dmxMode == DmxMode.MATRIX) continue;
            FixtureProfile pr = o.profile();
            if (pr == null) continue;
            int b0 = o.address, b1 = o.address + DmxMode.of(pr, o.dmxMode).footprint() - 1;
            if (a0 <= b1 && b0 <= a1) return "#" + o.fid;
        }
        return null;
    }

    /** Nearest console (LUMICORE X1 or ART-NET NODE) within {@code reach} blocks, in the loaded chunks. */
    public static LumicoreBlockEntity nearestDesk(ServerLevel level, BlockPos from, int reach) {
        LumicoreBlockEntity best = null;
        double bestD = (double) reach * reach;
        int c0x = (from.getX() - reach) >> 4, c1x = (from.getX() + reach) >> 4, c0z = (from.getZ() - reach) >> 4, c1z = (from.getZ() + reach) >> 4;
        for (int cx = c0x; cx <= c1x; cx++)
            for (int cz = c0z; cz <= c1z; cz++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof LumicoreBlockEntity d) || be.isRemoved()) continue;
                    double dd = be.getBlockPos().distSqr(from);
                    if (dd <= bestD) { bestD = dd; best = d; }
                }
            }
        return best;
    }

    public static String modeName(int mode) {
        return mode == MODE_KEEP ? "-" : DmxMode.NAMES[mode];
    }
}
