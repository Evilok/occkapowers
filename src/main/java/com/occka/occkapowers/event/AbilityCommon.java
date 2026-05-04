package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class AbilityCommon {
    private AbilityCommon() {
    }

    public static MobEffectInstance fx(MobEffect eff, int dur, int amp) {
        return new MobEffectInstance(eff, dur, amp, false, false);
    }

    public static Component msg(String text, ChatFormatting... fmt) {
        Style style = Style.EMPTY;
        for (ChatFormatting f : fmt)
            style = style.applyFormat(f);
        return Component.literal(text).withStyle(style);
    }

    public static List<LivingEntity> getNearbyEnemies(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player);
    }

    public static boolean isGrounded(ServerPlayer player, Level level) {
        return player.onGround() || !level.noCollision(player, player.getBoundingBox().move(0.0, -0.08, 0.0));
    }
}
