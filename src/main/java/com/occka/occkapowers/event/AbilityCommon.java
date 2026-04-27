package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class AbilityCommon {
    private AbilityCommon() {
    }

    public static MobEffectInstance fx(MobEffect effect, int duration, int amplifier) {
        return new MobEffectInstance(effect, duration, amplifier, false, false);
    }

    public static Component msg(String text, ChatFormatting... formatting) {
        var style = net.minecraft.network.chat.Style.EMPTY;
        for (ChatFormatting chatFormatting : formatting) {
            style = style.applyFormat(chatFormatting);
        }
        return Component.literal(text).withStyle(style);
    }

    public static List<LivingEntity> getNearbyEnemies(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player);
    }
}
