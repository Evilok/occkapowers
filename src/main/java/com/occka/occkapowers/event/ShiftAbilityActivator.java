package com.occka.occkapowers.event;

import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.ability.PowerType;
import com.occka.occkapowers.registry.ModCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ShiftAbilityActivator {
    private ShiftAbilityActivator() {
    }

    public static void activateShift(ServerPlayer player, PlayerPowerData data, PowerType type) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        switch (type) {
            case FIRE -> activateFireShift(player, level);
            case CHAOS -> ChaosAbility.activateShift(player, level);
            case SUPERFORCE -> SuperforceAbility.activateAbility(player, level);
            case ADEPT -> AdeptAbility.activateShift(player, level);
            case AIR -> activateAirShift(player, level);
            case WATER -> activateWaterShift(player, level);
            case ICE -> activateIceShift(player, level);
            case LIGHTNING -> activateLightningShift(player, level);
            case LASER -> activateLaserShift(player, level);
            case GEO -> activateGeoShift(player, level);
            case VOID -> activateVoidShift(player, level);
            case LIGHT -> activateLightShift(player, level);
            case GRAVITY -> activateGravityShift(player, level);
            case ECHO -> {
                if (data.getShiftCooldown() <= 0) {
                    spawnEchoClone(player, level, data);
                }
            }
        }
    }

    private static void activateFireShift(ServerPlayer player, ServerLevel level) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        double length = 10.0;

        for (LivingEntity entity : AbilityCommon.getNearbyEnemies(player, 12)) {
            Vec3 toEntity = entity.position().subtract(start);
            double dot = toEntity.dot(dir);
            if (dot > 0 && dot < length) {
                Vec3 proj = start.add(dir.scale(dot));
                if (proj.distanceTo(entity.position()) < 2.0) {
                    entity.hurt(player.damageSources().onFire(), 6);
                    entity.setSecondsOnFire(4);
                    level.sendParticles(ParticleTypes.FLAME, entity.getX(), entity.getY() + 1, entity.getZ(), 15, 0.3,
                            0.5, 0.3, 0.08);
                }
            }
        }

        for (double d = 0.3; d < length; d += 0.3) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
            if (d % 1.5 < 0.3) {
                level.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0);
            }
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, start.x + dir.x, start.y + dir.y, start.z + dir.z, 1, 0, 0, 0,
                0);
    }

    private static void activateAirShift(ServerPlayer player, ServerLevel level) {
        player.addEffect(AbilityCommon.fx(MobEffects.LEVITATION, 25, 3));
        player.addEffect(AbilityCommon.fx(MobEffects.SLOW_FALLING, 25, 0));
        for (int i = 0; i < 12; i++) {
            level.sendParticles(ParticleTypes.CLOUD, player.getX() + (Math.random() - 0.5) * 0.5, player.getY() - 0.5,
                    player.getZ() + (Math.random() - 0.5) * 0.5, 5, 0.5, 0.1, 0.5, 0.01);
        }
    }

    private static void activateWaterShift(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(3);
        player.level().getEntitiesOfClass(LivingEntity.class, box, e -> true)
                .forEach(e -> e.addEffect(AbilityCommon.fx(MobEffects.REGENERATION, 25, 2)));

        for (int i = 0; i < 12; i++) {
            level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX() + (Math.random() - 0.5) * 6,
                    player.getY() + Math.random() * 3, player.getZ() + (Math.random() - 0.5) * 6, 2, 0, 0.05, 0,
                    0.02);
        }
    }

    private static void activateIceShift(ServerPlayer player, ServerLevel level) {
        AbilityCommon.getNearbyEnemies(player, 5).forEach(e -> {
            e.hurt(player.damageSources().playerAttack(player), 0.5f);
            e.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
        });

        for (int i = 0; i < 25; i++) {
            double angle = Math.random() * Math.PI * 2;
            double r = Math.random() * 5;
            level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX() + r * Math.cos(angle),
                    player.getY() + Math.random() * 3, player.getZ() + r * Math.sin(angle), 1,
                    (Math.random() - 0.5) * 0.2, 0.03, (Math.random() - 0.5) * 0.2, 0);
        }
    }

    private static void activateLightningShift(ServerPlayer player, ServerLevel level) {
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SPEED, 250, 6));
        for (int i = 0; i < 20; i++) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX() + (Math.random() - 0.5) * 1.5,
                    player.getY() + Math.random() * 2, player.getZ() + (Math.random() - 0.5) * 1.5, 1, 0, 0, 0, 0.3);
        }
        level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(), 5, 0.3, 0.5, 0.3,
                0.2);
    }

    private static void activateLaserShift(ServerPlayer player, ServerLevel level) {
        player.addEffect(AbilityCommon.fx(MobEffects.NIGHT_VISION, 60, 0));
        AbilityCommon.getNearbyEnemies(player, 35).forEach(e -> e.addEffect(AbilityCommon.fx(MobEffects.GLOWING, 45, 0)));

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (int i = 1; i <= 24; i++) {
            Vec3 p = eye.add(look.scale(i));
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0.0);
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private static void activateGeoShift(ServerPlayer player, ServerLevel level) {
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 25, 2));
        player.addEffect(AbilityCommon.fx(MobEffects.DAMAGE_RESISTANCE, 25, 1));
        for (int i = 0; i < 12; i++) {
            double angle = (i / 12.0) * Math.PI * 2;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    player.getX() + 1.3 * Math.cos(angle), player.getY() + 0.5 + Math.random(),
                    player.getZ() + 1.3 * Math.sin(angle), 2, 0, 0, 0, 0);
        }
    }

    private static void activateVoidShift(ServerPlayer player, ServerLevel level) {
        player.addEffect(AbilityCommon.fx(MobEffects.NIGHT_VISION, 25, 0));
        player.addEffect(AbilityCommon.fx(MobEffects.INVISIBILITY, 25, 0));
        for (int i = 0; i < 15; i++) {
            level.sendParticles(ParticleTypes.PORTAL, player.getX() + (Math.random() - 0.5) * 2,
                    player.getY() + Math.random() * 2.5, player.getZ() + (Math.random() - 0.5) * 2, 1, 0, 0, 0,
                    0.05);
        }
    }

    private static void activateLightShift(ServerPlayer player, ServerLevel level) {
        BlockPos center = player.blockPosition();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock) {
                    boolean boneMeal = net.minecraft.world.item.BoneMealItem.applyBonemeal(
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL),
                            level, pos, player);
                    if (boneMeal) {
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1,
                                pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
                    }
                }
            }
        }

        AABB box = player.getBoundingBox().inflate(5);
        player.level().getEntitiesOfClass(Player.class, box, p -> true)
                .forEach(p -> p.addEffect(AbilityCommon.fx(MobEffects.SATURATION, 25, 1)));

        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 15, 1.5, 1.5, 1.5,
                0.05);
    }

    private static void activateGravityShift(ServerPlayer player, ServerLevel level) {
        player.addEffect(AbilityCommon.fx(MobEffects.LEVITATION, 25, 0));
        player.setDeltaMovement(player.getDeltaMovement().x, 0, player.getDeltaMovement().z);

        for (int i = 0; i < 15; i++) {
            double angle = Math.random() * Math.PI * 2;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX() + 1.5 * Math.cos(angle),
                    player.getY() + Math.random() * 2, player.getZ() + 1.5 * Math.sin(angle), 1, 0, 0, 0, 0.02);
        }
    }

    private static void spawnEchoClone(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        net.minecraft.world.entity.decoration.ArmorStand clone = new net.minecraft.world.entity.decoration.ArmorStand(
                net.minecraft.world.entity.EntityType.ARMOR_STAND, level);
        clone.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        clone.setCustomName(net.minecraft.network.chat.Component.literal(player.getName().getString())
                .withStyle(player.getCapability(ModCapabilities.PLAYER_POWER).map(d -> d.getPowerType().getColor())
                        .orElse(net.minecraft.ChatFormatting.WHITE)));
        clone.setCustomNameVisible(true);
        clone.setNoGravity(false);
        clone.getPersistentData().putString("occka_echo_clone", player.getUUID().toString());
        level.addFreshEntity(clone);

        player.addEffect(AbilityCommon.fx(MobEffects.INVISIBILITY, 400, 0));
        data.setShiftCooldown(600);

        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 1, 0.5,
                0.1);
        player.sendSystemMessage(AbilityCommon.msg("Echo Clone deployed!", net.minecraft.ChatFormatting.GREEN));
    }
}
