package com.occka.occkapowers.event;

import com.occka.occkapowers.ability.PlayerPowerData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

public final class FireAbility {
    
    private FireAbility() {}

    public static void activateShift(ServerPlayer player, ServerLevel level) {
        Vec3 start = player.getEyePosition();
                Vec3 dir = player.getLookAngle().normalize();
                double length = 10.0;

                for (LivingEntity entity : getNearbyEnemies(player, 12)) {
                    Vec3 toE = entity.position().subtract(start);
                    double dot = toE.dot(dir);
                    if (dot > 0 && dot < length) {
                        Vec3 proj = start.add(dir.scale(dot));
                        if (proj.distanceTo(entity.position()) < 2.0) {
                            entity.hurt(player.damageSources().onFire(), 6); // меньше чем лазер (18)
                            entity.setSecondsOnFire(4);
                            level.sendParticles(ParticleTypes.FLAME,
                                    entity.getX(), entity.getY() + 1, entity.getZ(),
                                    15, 0.3, 0.5, 0.3, 0.08);
                        }
                    }
                }
                // Визуал луча — огненные частицы по линии
                for (double d = 0.3; d < length; d += 0.3) {
                    Vec3 p = start.add(dir.scale(d));
                    level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z,
                            1, 0.02, 0.02, 0.02, 0.01);
                    if (d % 1.5 < 0.3)
                        level.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z,
                                1, 0.01, 0.01, 0.01, 0);
                }
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        start.x + dir.x, start.y + dir.y, start.z + dir.z,
                        1, 0, 0, 0, 0);
    }

    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        Vec3 playerPos = player.position();
        for (LivingEntity entity : AbilityCommon.getNearbyEnemies(player, 10)) {
            Vec3 dir = entity.position().subtract(playerPos).normalize();
            double dist = entity.distanceTo(player);
            double force = 1.8 * (1.0 - dist / 10.0) + 0.4;
            entity.setDeltaMovement(dir.x * force, 0.45 + (force * 0.3), dir.z * force);
            entity.hurtMarked = true;
            entity.setSecondsOnFire(8);
            entity.hurt(player.damageSources().onFire(), 4);
            level.sendParticles(ParticleTypes.FLAME, entity.getX(), entity.getY() + 1, entity.getZ(), 12, 0.3, 0.5, 0.3, 0.08);
        }

        for (int deg = 0; deg < 360; deg += 6) {
            for (double r = 0.5; r <= 10; r += 1.5) {
                double x = player.getX() + r * Math.cos(Math.toRadians(deg));
                double z = player.getZ() + r * Math.sin(Math.toRadians(deg));
                level.sendParticles(ParticleTypes.FLAME, x, player.getY() + 0.3, z, 1, 0, 0.1, 0, 0.04);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 2, 0.5, 0, 0.5, 0.05);
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.5, player.getZ(), 20, 1.5, 0.5, 1.5, 0.2);
        player.sendSystemMessage(AbilityCommon.msg("Firestorm!", ChatFormatting.RED));
    }

    public static void createFireRing(ServerPlayer player, ServerLevel level, int radius) {
        double cx = player.getX(), cy = player.getY(), cz = player.getZ();
        for (int deg = 0; deg < 360; deg += 8) {
            double rad = Math.toRadians(deg);
            double x = cx + radius * Math.cos(rad), z = cz + radius * Math.sin(rad);
            BlockPos pos = new BlockPos((int) x, (int) cy, (int) z);
            while (pos.getY() > level.getMinBuildHeight() && level.getBlockState(pos).isAir())
                pos = pos.below();
            pos = pos.above();
            if (level.getBlockState(pos).isAir())
                level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
            level.sendParticles(ParticleTypes.FLAME, x, cy + 0.5, z, 4, 0.1, 0.3, 0.1, 0.03);
            level.sendParticles(ParticleTypes.LAVA, x, cy + 0.2, z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, cy + 1, z, 2, 0.1, 0.3, 0.1, 0.01);
        }
        player.sendSystemMessage(AbilityCommon.msg("Fire Ring!", ChatFormatting.RED));
    }

    public static void startUlt(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        data.setFireUltOrigin(player.getX(), player.getY(), player.getZ());
        player.teleportTo(player.getX(), player.getY() + 14, player.getZ());
        AttributeInstance gravity = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity != null) {
            data.setFireUltOldGravity(gravity.getBaseValue());
            gravity.setBaseValue(0.0);
        }
        data.setFireUltActive(true);
        data.setFireUltTicks(300);
        data.setFireUltFireballCooldown(0);
        for (int i = 0; i < 30; i++)
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() - i * 0.5, player.getZ(), 5, 1, 0.2, 1, 0.05);
        player.sendSystemMessage(AbilityCommon.msg("FIRE ULT! Shoot fireballs with LMB for 15s!", ChatFormatting.RED, ChatFormatting.BOLD));
    }

    public static void ultShoot(ServerPlayer player, PlayerPowerData data) {
        if (!data.isFireUltActive()) return;
        ServerLevel level = (ServerLevel) player.level();
        Vec3 dir = player.getLookAngle().normalize();
        LargeFireball fb = new LargeFireball(EntityType.FIREBALL, level);
        fb.setOwner(player);
        fb.setPos(player.getEyePosition());
        fb.setDeltaMovement(dir.scale(2.5));
        fb.addTag("ult_fireball_" + player.getUUID().toString());
        level.addFreshEntity(fb);
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY(), player.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
    }

    public static void endUlt(ServerPlayer player, PlayerPowerData data) {
        player.teleportTo(data.getFireUltOriginX(), data.getFireUltOriginY(), data.getFireUltOriginZ());
        AttributeInstance gravity = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity != null) {
            gravity.setBaseValue(data.getFireUltOldGravity());
        }
        if (player.level() instanceof ServerLevel level) {
            String tag = "ult_fireball_" + player.getUUID().toString();
            level.getAllEntities().forEach(entity -> {
                if (entity.getTags().contains(tag)) entity.discard();
            });
            level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 20, 1, 1, 1, 0.05);
        }
    }
}
