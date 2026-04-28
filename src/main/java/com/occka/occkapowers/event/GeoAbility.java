package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

public final class GeoAbility {
    private GeoAbility() {
    }

    // SHIFT (held): каменная броня — замедление + сопротивление + частицы камня
    public static void activateShift(ServerPlayer player, ServerLevel level) {
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 25, 2));
        player.addEffect(AbilityCommon.fx(MobEffects.DAMAGE_RESISTANCE, 25, 1));
        for (int i = 0; i < 12; i++) {
            double angle = (i / 12.0) * Math.PI * 2;
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    player.getX() + 1.3 * Math.cos(angle),
                    player.getY() + 0.5 + Math.random(),
                    player.getZ() + 1.3 * Math.sin(angle),
                    2, 0, 0, 0, 0);
        }
    }

    // ABILITY: ударная волна — урон + замедление всем в радиусе
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        geoShockwave(player, level, 10);
    }

    private static void geoShockwave(ServerPlayer player, ServerLevel level, double radius) {
        for (LivingEntity entity : AbilityCommon.getNearbyEnemies(player, radius)) {
            entity.hurt(player.damageSources().playerAttack(player), 12);
            entity.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
        }
        for (int deg = 0; deg < 360; deg += 5) {
            for (double r = 0.5; r <= radius; r += 1.2) {
                double x = player.getX() + r * Math.cos(Math.toRadians(deg));
                double z = player.getZ() + r * Math.sin(Math.toRadians(deg));
                level.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        x, player.getY() + 0.1, z, 2, 0, 0.2, 0, 0.1);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION,
                player.getX(), player.getY(), player.getZ(), 4, 1, 0.5, 1, 0.1);
        player.sendSystemMessage(AbilityCommon.msg("Shockwave!", ChatFormatting.GOLD));
    }

    // ULT: запуск орбитальных свиней-снарядов (см. GeoOrbitHandler)
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        GeoOrbitHandler.startOrbit(player, level);
    }
}
