package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

public final class WaterAbility {
    private WaterAbility() {}

    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        spawnAquaticMobs(player, level);
    }

    private static void spawnAquaticMobs(ServerPlayer player, ServerLevel level) {
        java.util.Random rng = new java.util.Random();
        int count = 2 + rng.nextInt(7);

        net.minecraft.world.entity.EntityType<?>[] aquaticTypes = {
                net.minecraft.world.entity.EntityType.COD,
                net.minecraft.world.entity.EntityType.SALMON,
                net.minecraft.world.entity.EntityType.TROPICAL_FISH,
                net.minecraft.world.entity.EntityType.SQUID,
                net.minecraft.world.entity.EntityType.GLOW_SQUID,
                net.minecraft.world.entity.EntityType.TURTLE,
                net.minecraft.world.entity.EntityType.DOLPHIN,
        };

        for (int i = 0; i < count; i++) {
            net.minecraft.world.entity.EntityType<?> type = aquaticTypes[rng.nextInt(aquaticTypes.length)];
            net.minecraft.world.entity.Entity mob = type.create(level);
            if (mob == null) continue;

            double angle = (i / (double) count) * Math.PI * 2 + rng.nextDouble();
            double r = 1.5 + rng.nextDouble() * 2.5;
            mob.moveTo(player.getX() + r * Math.cos(angle), player.getY() + 0.5, player.getZ() + r * Math.sin(angle),
                    rng.nextFloat() * 360, 0);
            if (mob instanceof Mob m) {
                m.setPersistenceRequired();
                m.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            }
            level.addFreshEntity(mob);
            level.sendParticles(ParticleTypes.SPLASH, mob.getX(), mob.getY() + 0.5, mob.getZ(), 8, 0.3, 0.2, 0.3, 0.1);
        }

        level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1, player.getZ(), 40, 3, 1.5, 3, 0.15);
        level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY() + 1, player.getZ(), 20, 2, 1, 2, 0.1);
        player.sendSystemMessage(AbilityCommon.msg("Ocean Summon! (" + count + " creatures)", ChatFormatting.AQUA));
    }

    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        player.addEffect(AbilityCommon.fx(MobEffects.ABSORPTION, 1200, 17));
        level.setWeatherParameters(0, 6000, true, false);
        for (int i = 0; i < 60; i++) {
            level.sendParticles(ParticleTypes.DRIPPING_WATER,
                    player.getX() + (Math.random() - 0.5) * 20,
                    player.getY() + 10 + Math.random() * 5,
                    player.getZ() + (Math.random() - 0.5) * 20,
                    1, 0, -0.3, 0, 0.5);
        }
        player.sendSystemMessage(AbilityCommon.msg("Tide of Power!", ChatFormatting.AQUA));
    }
}
