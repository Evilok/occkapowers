package com.occka.occkapowers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.occka.occkapowers.OcckaPowers;
import com.occka.occkapowers.form.FormRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = OcckaPowers.MOD_ID, value = Dist.CLIENT)
public final class FormRenderHandler {
    private static final Map<UUID, FormRenderState> FORM_ENTITIES = new HashMap<>();

    private FormRenderHandler() {
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }

        String form = ClientFormData.get(player.getUUID());
        if (!FormRegistry.isValid(form)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        EntityType<?> type = FormRegistry.get(form);
        if (type == null) {
            return;
        }

        Entity dummy = getOrCreateDummy(player, form, type, mc);
        if (dummy == null) {
            return;
        }

        float partial = event.getPartialTick();
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
        float pitch = Mth.lerp(partial, player.xRotO, player.getXRot());

        copyPlayerState(player, dummy, bodyYaw, headYaw, pitch);

        event.setCanceled(true);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        applyFormTransform(form, poseStack);
        mc.getEntityRenderDispatcher().render(dummy, 0.0, 0.0, 0.0, bodyYaw, partial,
                poseStack, event.getMultiBufferSource(), event.getPackedLight());
        poseStack.popPose();
    }

    private static Entity getOrCreateDummy(AbstractClientPlayer player, String form, EntityType<?> type, Minecraft mc) {
        FormRenderState state = FORM_ENTITIES.get(player.getUUID());
        if (state == null || !state.form.equals(form) || state.entity.getType() != type || state.entity.level() != mc.level) {
            Entity entity = type.create(mc.level);
            if (entity == null) {
                FORM_ENTITIES.remove(player.getUUID());
                return null;
            }
            state = new FormRenderState(form, entity);
            FORM_ENTITIES.put(player.getUUID(), state);
        }
        return state.entity;
    }

    private static void copyPlayerState(AbstractClientPlayer player, Entity dummy,
            float bodyYaw, float headYaw, float pitch) {
        FormRenderState state = FORM_ENTITIES.get(player.getUUID());
        boolean newGameTick = state == null || state.lastPlayerTick != player.tickCount;
        if (state != null) {
            state.lastPlayerTick = player.tickCount;
        }

        dummy.tickCount = player.tickCount;
        dummy.setPos(player.getX(), player.getY(), player.getZ());
        dummy.setYRot(bodyYaw);
        dummy.setXRot(pitch);
        dummy.yRotO = bodyYaw;
        dummy.xRotO = pitch;

        if (dummy instanceof LivingEntity living) {
            living.yBodyRot = bodyYaw;
            living.yBodyRotO = bodyYaw;
            living.yHeadRot = headYaw;
            living.yHeadRotO = headYaw;
            living.hurtTime = player.hurtTime;
            living.deathTime = player.deathTime;
            if (newGameTick) {
                living.walkAnimation.update(player.walkAnimation.speed(), 1.0f);
            }
        }

        dummy.setShiftKeyDown(player.isShiftKeyDown());
        dummy.setSprinting(player.isSprinting());
        dummy.setOnGround(player.onGround());

        if (dummy instanceof EnderDragon dragon) {
            dragon.posPointer = 0;
            for (int i = 0; i < dragon.positions.length; i++) {
                dragon.positions[i][0] = bodyYaw;
                dragon.positions[i][1] = player.getY();
                dragon.positions[i][2] = 0.0;
            }
        }
    }

    private static void applyFormTransform(String form, PoseStack poseStack) {
        switch (form) {
            case "dragon" -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
                poseStack.translate(0.0, 0.1, 0.0);
                poseStack.scale(0.3f, 0.3f, 0.3f);
            }
            case "enderman" -> {
                poseStack.translate(0.0, -0.05, 0.0);
                poseStack.scale(0.85f, 0.85f, 0.85f);
            }
            case "golem" -> poseStack.scale(0.8f, 0.8f, 0.8f);
            case "ravager" -> poseStack.scale(0.55f, 0.55f, 0.55f);
            case "horse" -> poseStack.scale(0.75f, 0.75f, 0.75f);
            case "spider" -> poseStack.scale(0.9f, 0.9f, 0.9f);
            case "parrot" -> poseStack.scale(1.15f, 1.15f, 1.15f);
            case "fish" -> {
                poseStack.translate(0.0, 0.15, 0.0);
                poseStack.scale(1.2f, 1.2f, 1.2f);
            }
            case "phantom" -> poseStack.scale(0.7f, 0.7f, 0.7f);
            default -> {
            }
        }
    }

    private static final class FormRenderState {
        private final String form;
        private final Entity entity;
        private int lastPlayerTick = -1;

        private FormRenderState(String form, Entity entity) {
            this.form = form;
            this.entity = entity;
        }
    }
}
