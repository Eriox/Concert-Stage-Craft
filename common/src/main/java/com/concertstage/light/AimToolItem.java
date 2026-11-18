package com.concertstage.light;

import com.concertstage.light.core.Commands;
import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.core.Live;
import com.concertstage.light.core.Movement;
import com.concertstage.light.core.Show;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Rev. 1.3.0-3 — CSC FOCUS TOOL (asked 13 November: "un outil qui permet de positionner une ou différentes lumières à l'endroit que
 * l'on veut, et cela doit se voir sur les consoles externes"):
 * <ul>
 *   <li>right click a fixture (rev. 4c: no shift needed): adds it to the tool's selection (or takes it out); shift + right click in the
 *   air: empties it (and gives the fixtures held over an external desk back to it);</li>
 *   <li>right click a block: every selected fixture points at the spot clicked (pan / tilt from the geometry of its patch).</li>
 * </ul>
 * A fixture on a desk driven by the desk (internal control) moves at once (programmer, like the AIM of the desk). A fixture driven by
 * an external console (Art-Net node, External mode): rev. 4c, it moves too, held over the console's DMX (Live.focus) until released, and
 * the tool gives in the chat the DMX values of its pan and tilt (coarse / fine, inversions and offsets taken back) to store as a position.
 */
public class AimToolItem extends Item {
    public static final int MAX = 64;

    public AimToolItem(Properties p) {
        super(p);
    }

    static List<BlockPos> selection(ItemStack s) {
        List<BlockPos> out = new ArrayList<>();
        CompoundTag t = s.getTag();
        if (t == null) return out;
        for (Tag e : t.getList("Fixtures", Tag.TAG_LONG)) out.add(BlockPos.of(((LongTag) e).getAsLong()));
        return out;
    }

    static void store(ItemStack s, List<BlockPos> sel) {
        ListTag l = new ListTag();
        for (BlockPos p : sel) l.add(LongTag.valueOf(p.asLong()));
        s.getOrCreateTag().put("Fixtures", l);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();
        List<BlockPos> sel = selection(stack);
        // rev. 4c (asked 13 November): a plain right click on a fixture selects it (shift is no longer needed)
        if (level.getBlockEntity(pos) instanceof FixtureBlockEntity) {
            if (!sel.remove(pos)) {
                if (sel.size() >= MAX) { player.displayClientMessage(Component.translatable("message.concertstage.aim_full", MAX), true); return InteractionResult.FAIL; }
                sel.add(pos.immutable());
            }
            store(stack, sel);
            player.displayClientMessage(Component.translatable("message.concertstage.aim_selection", sel.size()), true);
            return InteractionResult.SUCCESS;
        }
        if (sel.isEmpty()) { player.displayClientMessage(Component.translatable("message.concertstage.aim_empty"), true); return InteractionResult.FAIL; }
        Vec3 target = ctx.getClickLocation();
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) aim(sl, sp, sel, target);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player.isShiftKeyDown()) {
            // rev. 4c: the fixtures held over an external desk are given back to it
            if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) release(sl, sp, selection(stack));
            store(stack, new ArrayList<>());
            player.displayClientMessage(Component.translatable("message.concertstage.aim_cleared"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Points the fixtures at {@code target}: by desk, in its programmer, or gives the DMX values for an external console. */
    public static void aim(ServerLevel level, ServerPlayer p, List<BlockPos> sel, Vec3 target) {
        Map<LumicoreBlockEntity, List<Integer>> byDesk = new LinkedHashMap<>();
        int missing = 0;
        for (BlockPos b : sel) {
            if (!level.isLoaded(b) || !(level.getBlockEntity(b) instanceof FixtureBlockEntity fe)) { missing++; continue; }
            LumicoreBlockEntity d = LightServer.linkedDesk(level, fe);
            if (d == null) { missing++; continue; }
            byDesk.computeIfAbsent(d, k -> new ArrayList<>()).add(fe.fid());
        }
        int moved = 0;
        List<Component> dmx = new ArrayList<>();
        for (var e : byDesk.entrySet()) {
            LumicoreBlockEntity d = e.getKey();
            if (!d.mayOperate(p)) { p.displayClientMessage(Component.translatable("message.concertstage.light_no_permission"), true); continue; }
            int[] fids = e.getValue().stream().mapToInt(Integer::intValue).toArray();
            if (d.live.control == Live.EXTERNAL) {
                // rev. 4c: the fixtures move now (held over the console's DMX until shift + right click in the air), and the values to
                // program on the console are still given
                com.concertstage.light.core.Bytes.Writer fw = Commands.op(Commands.P_FOCUS);
                Commands.fids(fw, fids);
                if (LightServer.apply(level, d, fw.f32((float) target.x).f32((float) target.y).f32((float) target.z).toBytes())) moved += fids.length;
                for (int fid : fids) {
                    Show.Patch pa = d.show.fixture(fid);
                    float[] pt = pa == null ? null : Movement.aim(pa, target.x, target.y, target.z);
                    if (pt == null) continue;
                    dmx.add(dmxLine(pa, pt));
                }
            } else {
                com.concertstage.light.core.Bytes.Writer w = Commands.op(Commands.P_AIM);
                Commands.fids(w, fids);
                if (LightServer.apply(level, d, w.f32((float) target.x).f32((float) target.y).f32((float) target.z).toBytes())) moved += fids.length;
            }
        }
        if (moved > 0) p.displayClientMessage(Component.translatable("message.concertstage.aim_done", moved,
            String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", target.x, target.y, target.z)), true);
        if (!dmx.isEmpty()) {
            p.sendSystemMessage(Component.translatable("message.concertstage.aim_external",
                String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", target.x, target.y, target.z)).withStyle(ChatFormatting.AQUA));
            for (Component c : dmx) p.sendSystemMessage(c);
        }
        if (missing > 0) p.displayClientMessage(Component.translatable("message.concertstage.aim_missing", missing), false);
    }

    /** Rev. 4c: gives the fixtures held by the focus tool back to their external desk. */
    static void release(ServerLevel level, ServerPlayer p, List<BlockPos> sel) {
        Map<LumicoreBlockEntity, List<Integer>> byDesk = new LinkedHashMap<>();
        for (BlockPos b : sel) {
            if (!level.isLoaded(b) || !(level.getBlockEntity(b) instanceof FixtureBlockEntity fe)) continue;
            LumicoreBlockEntity d = LightServer.linkedDesk(level, fe);
            if (d != null && d.live.focus.containsKey(fe.fid())) byDesk.computeIfAbsent(d, k -> new ArrayList<>()).add(fe.fid());
        }
        for (var e : byDesk.entrySet()) {
            if (!e.getKey().mayOperate(p)) continue;
            com.concertstage.light.core.Bytes.Writer w = Commands.op(Commands.P_FOCUS_CLEAR);
            Commands.fids(w, e.getValue().stream().mapToInt(Integer::intValue).toArray());
            LightServer.apply(level, e.getKey(), w.toBytes());
        }
    }

    /** "#4 CSC VORTEX X1 (1.28): Pan 128 / 0 (50.0 %) · Tilt 85 / 85 (33.3 %)": the DMX the console must send for this aim. */
    public static Component dmxLine(Show.Patch pa, float[] pt) {
        FixtureProfile pr = pa.profile();
        StringBuilder sb = new StringBuilder();
        sb.append('#').append(pa.fid).append(' ').append(pr.name).append(" (").append(pa.universe).append('.').append(pa.address).append("): ");
        // rev. 3: the channels of the fixture's own DMX mode (Compact has no fine channel): the value and its DMX address
        com.concertstage.light.dmx.DmxMode m = com.concertstage.light.dmx.DmxMode.of(pr, pa.dmxMode);
        if (pr.hasPan()) sb.append(value(m, pa, com.concertstage.light.core.Attr.PAN, "Pan", raw(pt[0], pa.invertPan, pr.panRange > 0 ? pa.panOffset / pr.panRange : 0f))).append(" · ");
        sb.append(value(m, pa, com.concertstage.light.core.Attr.TILT, "Tilt", raw(pt[1], pa.invertTilt, pr.tiltRange > 0 ? pa.tiltOffset / pr.tiltRange : 0f)));
        return Component.literal(sb.toString());
    }

    /** The DMX value (0..1) that DmxDecode turns into {@code v} with these inversion and offset. */
    static float raw(float v, boolean invert, float offset) {
        float y = v - offset;
        if (invert) y = 1f - y;
        return Math.max(0f, Math.min(1f, y));
    }

    static String value(com.concertstage.light.dmx.DmxMode m, Show.Patch pa, com.concertstage.light.core.Attr a, String name, float v) {
        int coarse = -1, fine = -1;
        for (int i = 0; i < m.channels.length; i++) {
            var c = m.channels[i];
            if (c.attr() != a) continue;
            if (c.kind() == com.concertstage.light.dmx.DmxMode.Kind.ATTR && coarse < 0) coarse = i;
            else if (c.kind() == com.concertstage.light.dmx.DmxMode.Kind.FINE && fine < 0) fine = i;
        }
        if (coarse < 0) return value(name, v);
        if (fine < 0) return String.format(java.util.Locale.ROOT, "%s ch %d = %d (%.1f %%)", name, pa.address + coarse, Math.round(v * 255f), v * 100f);
        int w16 = Math.round(v * 65535f);
        return String.format(java.util.Locale.ROOT, "%s ch %d = %d, ch %d = %d (%.1f %%)", name, pa.address + coarse, w16 >> 8, pa.address + fine, w16 & 255, v * 100f);
    }

    static String value(String name, float v) {
        int w16 = Math.round(v * 65535f);
        return String.format(java.util.Locale.ROOT, "%s %d / %d (%.1f %%)", name, w16 >> 8, w16 & 255, v * 100f);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.concertstage.aim_selected", selection(stack).size()).withStyle(ChatFormatting.AQUA));
        for (int i = 1; i <= 3; i++) lines.add(Component.translatable("tooltip.concertstage.aim_" + i).withStyle(ChatFormatting.DARK_GRAY));
    }
}
