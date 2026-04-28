package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SuperforceAbility {

    private static MobEffectInstance fx(net.minecraft.world.effect.MobEffect eff, int dur, int amp) {
        return new MobEffectInstance(eff, dur, amp, false, false);
    }

    public static void applyElytraFlight(ServerPlayer player, ServerLevel level) {

        // Не включаем принудительно — только если игрок УЖЕ летит на элитре
        if (!player.isFallFlying())
            return;

        Vec3 look = player.getLookAngle().normalize();
        double speed = 1.15;

        player.setDeltaMovement(
                look.x * speed,
                look.y * speed,
                look.z * speed);

        player.hurtMarked = true;
        player.resetFallDistance();
        player.fallDistance = 0;

        if (player.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY(), player.getZ(),
                    2, 0.2, 0.1, 0.2, 0.03);
        }
    }

    // ===== ULT TICK: runs every tick for SUPERFORCE players =====
    // В методе tickUlt замени блок с elytra:
    public static void tickUlt(ServerPlayer player, ServerLevel level) {
        var nbt = player.getPersistentData();

        if (nbt.getBoolean("occka_sf_ult_flying")) {
            int ticks = nbt.getInt("occka_sf_ult_ticks");
            nbt.putInt("occka_sf_ult_ticks", ticks + 1);

            // Начинаем elytra только когда игрок поднялся достаточно (тик 5+)
            if (ticks >= 5) {
                if (!player.isFallFlying()) {
                    // Принудительно включаем elytra
                    player.startFallFlying();
                }

                if (player.isFallFlying()) {
                    Vec3 look = player.getLookAngle().normalize();
                    double speed = 1.22; // регулируешь тут

                    player.setDeltaMovement(
                            look.x * speed,
                            look.y * speed,
                            look.z * speed);

                    player.hurtMarked = true;
                    player.resetFallDistance();
                    player.fallDistance = 0;

                    if (ticks % 2 == 0) {
                        level.sendParticles(ParticleTypes.CRIT,
                                player.getX(), player.getY(), player.getZ(),
                                3, 0.3, 0.3, 0.3, 0.1);
                        level.sendParticles(ParticleTypes.CLOUD,
                                player.getX(), player.getY(), player.getZ(),
                                2, 0.2, 0.1, 0.2, 0.03);
                    }

                    // Детект приземления: на земле после 15+ тиков
                    if (player.onGround() && ticks > 15) {
                        nbt.putBoolean("occka_sf_ult_flying", false);
                        player.stopFallFlying();
                        executeMeteorCrash(player, level);
                        return;
                    }
                } else {
                    // Если elytra не включилась (нет крыльев) — симулируем полёт вручную
                    Vec3 look = player.getLookAngle();
                    player.setDeltaMovement(
                            look.x * 0.95,
                            Math.max(look.y * 0.95, -0.1),
                            look.z * 0.95);
                    player.hurtMarked = true;
                    player.resetFallDistance();

                    if (player.onGround() && ticks > 15) {
                        nbt.putBoolean("occka_sf_ult_flying", false);
                        executeMeteorCrash(player, level);
                    }
                }
            }

            // Таймаут 5 секунд (100 тиков) — принудительный краш
            if (ticks > 1000) {
                nbt.putBoolean("occka_sf_ult_flying", false);
                executeMeteorCrash(player, level);
            }
        }

        // Camera shake тикер (остаётся как был)
        int shakeTicks = nbt.getInt("occka_shake_ticks");
        if (shakeTicks > 0) {
            nbt.putInt("occka_shake_ticks", shakeTicks - 1);
            if (shakeTicks % 2 == 0) {
                float amt = 12f * (shakeTicks / 10f);
                player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * amt);
                player.setXRot(Math.max(-89, Math.min(89,
                        player.getXRot() + (player.getRandom().nextFloat() - 0.5f) * amt * 0.4f)));
                player.teleportTo(player.getX(), player.getY(), player.getZ());
            }
        }
    }

    // ===== SHIFT (mapped to ability slot - no cd): SUPER PUNCH =====
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();

        LivingEntity target = null;
        double minDist = Double.MAX_VALUE;
        AABB box = player.getBoundingBox().inflate(6);

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player)) {
            Vec3 toE = e.position().subtract(eye).normalize();
            double dot = toE.dot(dir);
            double dist = e.distanceTo(player);
            if (dot > 0.65 && dist < minDist) {
                minDist = dist;
                target = e;
            }
        }

        if (target != null) {
            target.setDeltaMovement(dir.x * 3.2, 0.75, dir.z * 3.2);
            target.hurtMarked = true;
            target.hurt(player.damageSources().playerAttack(player), 18);
            if (target instanceof ServerPlayer tp) {
                tp.addEffect(fx(MobEffects.CONFUSION, 50, 6));
            }
            // Impact particles
            for (int i = 0; i < 30; i++) {
                double a = Math.random() * Math.PI * 2;
                level.sendParticles(ParticleTypes.CRIT,
                        target.getX() + Math.cos(a) * 0.4, target.getY() + 1 + Math.random(),
                        target.getZ() + Math.sin(a) * 0.4, 1, 0, 0, 0, 0.3);
            }
            level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1, target.getZ(), 3, 0.3, 0.3,
                    0.3, 0.1);
            // Speed lines
            for (double d = 0.5; d < minDist; d += 0.6) {
                Vec3 p = eye.add(dir.scale(d));
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
            player.sendSystemMessage(
                    Component.literal("SUPER PUNCH!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, eye.x + dir.x * 3, eye.y + dir.y * 3, eye.z + dir.z * 3, 4,
                    0.3, 0.3, 0.3, 0.1);
            player.sendSystemMessage(Component.literal("Miss!").withStyle(ChatFormatting.GRAY));
        }
    }

    // ===== ABILITY (cd 10s): GROUND SLAM =====
    public static void activateShift(ServerPlayer player, ServerLevel level) {
        Vec3 pos = player.position();

        // Self nausea shake
        player.addEffect(fx(MobEffects.CONFUSION, 25, 4));

        // Ground crack particles
        for (int deg = 0; deg < 360; deg += 10) {
            for (double r = 0.5; r <= 7; r += 1.0) {
                double x = pos.x + r * Math.cos(Math.toRadians(deg));
                double z = pos.z + r * Math.sin(Math.toRadians(deg));
                BlockPos bp = BlockPos.containing(x, pos.y - 0.3, z);
                var state = level.getBlockState(bp).isAir() ? level.getBlockState(bp.below()) : level.getBlockState(bp);
                if (!state.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            x, pos.y + 0.1, z, 2, 0, 0.25, 0, 0.12);
                }
            }
        }

        // Launch + shake enemies in radius 7 (reduced from 12)
        for (LivingEntity entity : getNearby(player, 7)) {
            Vec3 dir = entity.position().subtract(pos).normalize();
            double dist = entity.distanceTo(player);
            double force = 1.0 * (1.0 - dist / 7.0) + 0.3;
            entity.setDeltaMovement(dir.x * force, 0.6 + force * 0.3, dir.z * force);
            entity.hurtMarked = true;
            entity.hurt(player.damageSources().playerAttack(player), 5);
            if (entity instanceof ServerPlayer tp) {
                tp.addEffect(fx(MobEffects.CONFUSION, 35, 4));
            }
            level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1, entity.getZ(), 8, 0.3, 0.3, 0.3,
                    0.2);
        }

        // Shockwave ring
        for (int deg = 0; deg < 360; deg += 8) {
            level.sendParticles(ParticleTypes.EXPLOSION,
                    pos.x + 7 * Math.cos(Math.toRadians(deg)), pos.y + 0.1, pos.z + 7 * Math.sin(Math.toRadians(deg)),
                    1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0.02);
        player.sendSystemMessage(Component.literal("GROUND SLAM!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    // ===== ULT: jump + elytra + crash on landing =====
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        // Strong upward launch
        player.setDeltaMovement(0, 2.8, 0);
        player.hurtMarked = true;
        player.resetFallDistance();

        // Flag: start elytra after gaining height
        player.getPersistentData().putBoolean("occka_sf_ult_flying", true);
        player.getPersistentData().putInt("occka_sf_ult_ticks", 0);

        player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 400, 4));

        // Launch particles
        for (int i = 0; i < 25; i++) {
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + (Math.random() - 0.5) * 0.8, player.getY(),
                    player.getZ() + (Math.random() - 0.5) * 0.8, 1, 0.2, -0.05, 0.2, 0.04);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 2, 0, 0, 0,
                0.05);
        player.sendSystemMessage(Component.literal("METEOR DIVE! Look where you want to crash!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    // ===== IMPACT on landing =====
    public static void executeMeteorCrash(ServerPlayer player, ServerLevel level) {
        Vec3 pos = player.position();

        // Crater particles (radius 8, down from 15)
        for (int deg = 0; deg < 360; deg += 5) {
            for (double r = 0.5; r <= 8; r += 1.5) {
                double x = pos.x + r * Math.cos(Math.toRadians(deg));
                double z = pos.z + r * Math.sin(Math.toRadians(deg));
                BlockPos bp = BlockPos.containing(x, pos.y - 0.3, z);
                var state = level.getBlockState(bp).isAir() ? level.getBlockState(bp.below()) : level.getBlockState(bp);
                if (!state.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            x, pos.y + 0.2, z, 2, 0, 0.4, 0, 0.2);
                }
                if (r < 4) {
                    level.sendParticles(ParticleTypes.EXPLOSION, x, pos.y + 0.1, z, 1, 0, 0, 0, 0);
                }
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 5, 1.5, 0.3, 1.5, 0.08);
        level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);

        // Launch enemies in radius 10 (down from 15)
        for (LivingEntity entity : getNearby(player, 10)) {
            Vec3 dir = entity.position().subtract(pos);
            double dist = Math.max(0.1, dir.length());
            dir = dir.normalize();
            double force = 1.5 * (1.0 - dist / 10.0) + 0.4;

            entity.setDeltaMovement(dir.x * force, 1.5 + (1.0 - dist / 10.0) * 0.6, dir.z * force);
            entity.hurtMarked = true;
            entity.hurt(player.damageSources().playerAttack(player), (float) (12 * (1 - dist / 10.0)));

            if (entity instanceof ServerPlayer tp) {
                tp.getPersistentData().putInt("occka_shake_ticks", 10);
                tp.addEffect(fx(MobEffects.CONFUSION, 50, 7));
            }
            level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1, entity.getZ(), 12, 0.4, 0.4, 0.4,
                    0.2);
        }

        // Shake own camera
        player.addEffect(fx(MobEffects.CONFUSION, 15, 3));
        player.getPersistentData().putInt("occka_shake_ticks", 6);

        // Announce to nearby
        for (Player p : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(15), x -> true)) {
            ((ServerPlayer) p).sendSystemMessage(
                    Component.literal("METEOR CRASH!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    private static List<LivingEntity> getNearby(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && !(e instanceof Player p && p.isAlliedTo(player)));
    }
                
}
