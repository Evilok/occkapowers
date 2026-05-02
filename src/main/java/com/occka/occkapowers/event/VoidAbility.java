package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class VoidAbility {
    private VoidAbility() {}

    // SHIFT (held): инвис + ночное зрение + порталные частицы
    public static void activateShift(ServerPlayer player, ServerLevel level) {
        player.addEffect(AbilityCommon.fx(MobEffects.NIGHT_VISION, 25, 0));
        player.addEffect(AbilityCommon.fx(MobEffects.INVISIBILITY, 25, 0));
        for (int i = 0; i < 15; i++) {
            level.sendParticles(ParticleTypes.PORTAL,
                    player.getX() + (Math.random() - 0.5) * 2,
                    player.getY() + Math.random() * 2.5,
                    player.getZ() + (Math.random() - 0.5) * 2,
                    1, 0, 0, 0, 0.05);
        }
    }

    // ABILITY: слепота + яд всем врагам в радиусе
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        voidBlind(player, level, 10);
    }

    private static void voidBlind(ServerPlayer player, ServerLevel level, double radius) {
        for (LivingEntity entity : AbilityCommon.getNearbyEnemies(player, radius)) {
            entity.addEffect(AbilityCommon.fx(MobEffects.BLINDNESS, 200, 0));
            entity.addEffect(AbilityCommon.fx(MobEffects.POISON, 100, 0));
            level.sendParticles(ParticleTypes.PORTAL,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    25, 0.5, 1, 0.5, 0.1);
        }
        for (int i = 0; i < 70; i++) {
            double a = Math.random() * Math.PI * 2, r = Math.random() * radius;
            level.sendParticles(ParticleTypes.PORTAL,
                    player.getX() + r * Math.cos(a),
                    player.getY() + 1 + Math.random() * 3,
                    player.getZ() + r * Math.sin(a),
                    1, 0, 0, 0, 0.05);
        }
        player.sendSystemMessage(AbilityCommon.msg("Darkness!", ChatFormatting.DARK_PURPLE));
    }

    // ULT: бафф себе + withering + weakness врагам
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        double radius = 20;
        player.addEffect(AbilityCommon.fx(MobEffects.DAMAGE_BOOST, 600, 0));
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SPEED, 600, 3));

        for (LivingEntity entity : AbilityCommon.getNearbyEnemies(player, radius)) {
            entity.addEffect(AbilityCommon.fx(MobEffects.WITHER, 200, 0));
            entity.addEffect(AbilityCommon.fx(MobEffects.WEAKNESS, 200, 1));
            level.sendParticles(ParticleTypes.PORTAL,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    25, 0.5, 1, 0.5, 0.1);
        }

        for (int i = 0; i < 120; i++) {
            double a = Math.random() * Math.PI * 2,
                    p = (Math.random() - 0.5) * Math.PI,
                    r = Math.random() * radius;
            level.sendParticles(ParticleTypes.PORTAL,
                    player.getX() + r * Math.cos(a) * Math.cos(p),
                    player.getY() + 2 + r * Math.sin(p),
                    player.getZ() + r * Math.sin(a) * Math.cos(p),
                    1, 0, 0, 0, 0.03);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + 1, player.getZ(),
                40, 2, 2, 2, 0.1);
        player.sendSystemMessage(AbilityCommon.msg("VOID ULT!", ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
    }
}
