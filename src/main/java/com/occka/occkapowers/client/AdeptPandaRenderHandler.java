package com.occka.occkapowers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.occka.occkapowers.OcckaPowers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PandaModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Panda;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.math.Axis;

@Mod.EventBusSubscriber(modid = OcckaPowers.MOD_ID, value = Dist.CLIENT)
public final class AdeptPandaRenderHandler {
    private static final ResourceLocation PANDA_TEXTURE = new ResourceLocation("textures/entity/panda/panda.png");
    private static PandaModel<Panda> pandaModel;

    private AdeptPandaRenderHandler() {}

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        if (!ClientAdeptPandaForms.isActive(player.getUUID())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        event.setCanceled(true);
        if (pandaModel == null) {
            pandaModel = new PandaModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PANDA));
        }

        Panda panda = new Panda(EntityType.PANDA, mc.level);
        panda.tickCount = player.tickCount;

        float partialTick = event.getPartialTick();
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot) - bodyYaw;
        float headPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        float limbSwing = player.walkAnimation.position(partialTick);
        float limbSwingAmount = Math.min(player.walkAnimation.speed(partialTick), 1.0F);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        pandaModel.prepareMobModel(panda, limbSwing, limbSwingAmount, partialTick);
        pandaModel.setupAnim(panda, limbSwing, limbSwingAmount, player.tickCount + partialTick, headYaw, headPitch);
        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(PANDA_TEXTURE));
        pandaModel.renderToBuffer(poseStack, buffer, event.getPackedLight(), OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
    }
}
