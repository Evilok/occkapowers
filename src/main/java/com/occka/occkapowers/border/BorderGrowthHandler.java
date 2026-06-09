package com.occka.occkapowers.border;

import com.occka.occkapowers.OcckaPowers;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BorderGrowthHandler {

    @SubscribeEvent
    public void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        if (!level.getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) {
            return;
        }

        Advancement advancement = event.getAdvancement();
        DisplayInfo displayInfo = advancement.getDisplay();
        if (displayInfo == null || !displayInfo.shouldAnnounceChat()) {
            return;
        }

        int growthBlocks = BorderGrowthData.get(level).getBlocksPerAdvancement();
        if (growthBlocks <= 0) {
            return;
        }

        WorldBorder border = level.getWorldBorder();
        double oldSize = border.getSize();
        double newSize = oldSize + growthBlocks;
        border.setSize(newSize);

        OcckaPowers.LOGGER.debug("Increased {} world border from {} to {} after {} earned advancement {}",
                level.dimension().location(), oldSize, newSize, player.getGameProfile().getName(), advancement.getId());
    }
}
