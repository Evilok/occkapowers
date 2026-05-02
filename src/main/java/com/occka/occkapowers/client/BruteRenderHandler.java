package com.occka.occkapowers.client;

import com.occka.occkapowers.OcckaPowers;
import com.occka.occkapowers.ability.PowerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = OcckaPowers.MOD_ID, value = Dist.CLIENT)
public final class BruteRenderHandler {
    private static final float BRUTE_MODEL_SCALE = 1.5F;
    private static final Set<UUID> SCALED_PLAYERS = new HashSet<>();

    private BruteRenderHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player) || !isBrute(player)) {
            return;
        }

        event.getPoseStack().pushPose();
        event.getPoseStack().scale(BRUTE_MODEL_SCALE, BRUTE_MODEL_SCALE, BRUTE_MODEL_SCALE);
        SCALED_PLAYERS.add(player.getUUID());
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (SCALED_PLAYERS.remove(event.getEntity().getUUID())) {
            event.getPoseStack().popPose();
        }
    }

    @SubscribeEvent
    public static void onPlayerSize(EntityEvent.Size event) {
        if (event.getEntity() instanceof AbstractClientPlayer player && isBrute(player)) {
            event.setNewSize(EntityDimensions.scalable(0.6f, 2.1f), true);
        }
    }

    private static boolean isBrute(AbstractClientPlayer player) {
        if (player == Minecraft.getInstance().player) {
            return ClientPowerData.powerType == PowerType.BRUTE;
        }

        return ClientPlayerPowerData.get(player.getUUID()) == PowerType.BRUTE;
    }
}
