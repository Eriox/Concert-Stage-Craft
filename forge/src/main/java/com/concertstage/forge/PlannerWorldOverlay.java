package com.concertstage.forge;

import com.concertstage.ConcertStage;
import com.concertstage.client.audio.DelayPlannerOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ConcertStage.MOD_ID, value = Dist.CLIENT)
public final class PlannerWorldOverlay {
  @SubscribeEvent
  public static void render(RenderLevelStageEvent e) {
    if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || com.concertstage.client.vision.CameraFeeds.rendering) return;
    var mc = net.minecraft.client.Minecraft.getInstance();
    var p = e.getPoseStack();
    var camera = e.getCamera().getPosition();
    p.pushPose();
    p.translate(-camera.x, -camera.y, -camera.z);
    var buffers = mc.renderBuffers().bufferSource();
    DelayPlannerOverlay.render(p, buffers, camera);
    buffers.endBatch(DelayPlannerOverlay.type());
    p.popPose();
  }
}
