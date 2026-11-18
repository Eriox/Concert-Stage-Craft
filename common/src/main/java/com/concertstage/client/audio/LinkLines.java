package com.concertstage.client.audio;

import com.concertstage.StageContent;
import com.concertstage.audio.AudioConsoleBlockEntity;
import com.concertstage.audio.AudioGroups;
import com.concertstage.audio.LinkToolItem;
import com.concertstage.audio.SpeakerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** While the Audio Link Tool is held: a line of coloured dust (the group colour) from the selected console to each linked array. */
final class LinkLines {
    private static int tick;

    private LinkLines() {}

    static void tick(Minecraft mc) {
        if (++tick % 6 != 0 || mc.player == null || mc.level == null) return;
        ItemStack s = mc.player.getMainHandItem().is(StageContent.LINK_TOOL) ? mc.player.getMainHandItem()
            : mc.player.getOffhandItem().is(StageContent.LINK_TOOL) ? mc.player.getOffhandItem() : ItemStack.EMPTY;
        if (s.isEmpty()) return;
        BlockPos console = LinkToolItem.console(s);
        if (console == null || !(mc.level.getBlockEntity(console) instanceof AudioConsoleBlockEntity c)) return;
        Vec3 from = Vec3.atCenterOf(console).add(0, 0.5, 0);
        for (BlockPos p : c.speakers()) {
            if (!(mc.level.getBlockEntity(p) instanceof SpeakerBlockEntity sp)) continue;
            Vec3 to = sp.acousticAxis()[0];
            int col = AudioGroups.COLORS[sp.group()];
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(((col >> 16) & 255) / 255f, ((col >> 8) & 255) / 255f, (col & 255) / 255f), 0.8f);
            double len = to.distanceTo(from);
            int n = (int) Math.min(36, Math.max(2, len / 0.8));
            for (int i = 0; i <= n; i++) {
                Vec3 q = from.lerp(to, i / (double) n);
                mc.level.addParticle(dust, q.x, q.y, q.z, 0, 0, 0);
            }
        }
    }
}
