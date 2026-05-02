package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.LivingEntity;
import com.occka.occkapowers.event.AbilityCommon;

public final class WaterAbility {
    private static final String NBT_RAIN_ABSORPTION = "occka_water_rain_absorption";
    private static final float MAX_RAIN_ABSORPTION = 40.0F;
    private static final float RAIN_GAIN_PER_TICK = MAX_RAIN_ABSORPTION / (7.0F * 20.0F);
    private static final float RAIN_LOSS_PER_TICK = MAX_RAIN_ABSORPTION / (15.0F * 20.0F);

    private WaterAbility() {
    }

    public static void activateShift(ServerPlayer player, ServerLevel level) {

        // Healing aura
        AABB box = player.getBoundingBox().inflate(3);
        player.level().getEntitiesOfClass(LivingEntity.class, box, e -> true)
                .forEach(e -> e.addEffect(AbilityCommon.fx(MobEffects.REGENERATION, 25, 2)));
        for (int i = 0; i < 12; i++) {
            level.sendParticles(ParticleTypes.BUBBLE_POP,
                    player.getX() + (Math.random() - 0.5) * 6,
                    player.getY() + Math.random() * 3,
                    player.getZ() + (Math.random() - 0.5) * 6,
                    2, 0, 0.05, 0, 0.02);

        }
    }

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
            if (mob == null)
                continue;

            double angle = (i / (double) count) * Math.PI * 2 + rng.nextDouble();
            double r = 1.5 + rng.nextDouble() * 2.5;
            mob.moveTo(player.getX() + r * Math.cos(angle), player.getY() + 0.5, player.getZ() + r * Math.sin(angle),
                    rng.nextFloat() * 360, 0);
            if (mob instanceof Mob m) {
                m.setPersistenceRequired();
                m.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED,
                        null, null);
            }
            level.addFreshEntity(mob);
            level.sendParticles(ParticleTypes.SPLASH, mob.getX(), mob.getY() + 0.5, mob.getZ(), 8, 0.3, 0.2, 0.3, 0.1);
        }

        level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1, player.getZ(), 40, 3, 1.5, 3, 0.15);
        level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY() + 1, player.getZ(), 20, 2, 1, 2,
                0.1);
        player.sendSystemMessage(AbilityCommon.msg("Ocean Summon! (" + count + " creatures)", ChatFormatting.AQUA));
    }

    public static void activateUlt(ServerPlayer player, ServerLevel level) {
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

    public static void tickRainUlt(ServerPlayer player, ServerLevel level, boolean enabled) {
        float current = player.getPersistentData().getFloat(NBT_RAIN_ABSORPTION);
        boolean gaining = enabled && level.getLevelData().isRaining();
        float next = gaining
                ? Math.min(MAX_RAIN_ABSORPTION, current + RAIN_GAIN_PER_TICK)
                : Math.max(0.0F, current - RAIN_LOSS_PER_TICK);

        if (Math.abs(next - current) > 0.001) {
            applyRainAbsorption(player, current, next);
        } else if (next <= 0.0) {
            clearRainAbsorption(player, current);
        }

        if (gaining && player.tickCount % 20 == 0) {
            level.sendParticles(ParticleTypes.DRIPPING_WATER,
                    player.getX(), player.getY() + 2.0, player.getZ(),
                    4, 0.35, 0.2, 0.35, 0.02);
        }

        if (gaining) {
            player.addEffect(AbilityCommon.fx(MobEffects.REGENERATION, 40, 0));
        } else {
            player.removeEffect(MobEffects.REGENERATION);
        }
    }

    private static void applyRainAbsorption(ServerPlayer player, float current, float next) {
        float delta = next - current;
        player.getPersistentData().putFloat(NBT_RAIN_ABSORPTION, next);
        player.setAbsorptionAmount(Math.max(0.0F, player.getAbsorptionAmount() + delta));
    }

    private static void clearRainAbsorption(ServerPlayer player, float current) {
        if (current > 0.0F) {
            player.setAbsorptionAmount(Math.max(0.0F, player.getAbsorptionAmount() - current));
        }
        player.getPersistentData().remove(NBT_RAIN_ABSORPTION);
        if (player.getAbsorptionAmount() <= 0.001F) {
            player.removeEffect(MobEffects.ABSORPTION);
        }
    }
}
