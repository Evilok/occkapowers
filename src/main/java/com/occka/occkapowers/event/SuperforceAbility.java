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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SuperforceAbility {

    private static MobEffectInstance fx(net.minecraft.world.effect.MobEffect eff, int dur, int amp) {
        return new MobEffectInstance(eff, dur, amp, false, false);
    }

    // ===== PASSIVE: mayfly всегда для SUPERFORCE =====

    public static void applyElytraFlight(ServerPlayer player, ServerLevel level) {
        if (!player.isFallFlying())
            return;
        Vec3 look = player.getLookAngle().normalize();
        double speed = 1.35;
        player.setDeltaMovement(look.x * speed, look.y * speed, look.z * speed);
        player.hurtMarked = true;
        player.resetFallDistance();
        player.fallDistance = 0;
        if (player.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY(), player.getZ(),
                    2, 0.2, 0.1, 0.2, 0.03);
        }
    }

    // ===== SHIFT: Super Punch (без изменений) =====

    public static void activateShift(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();

        LivingEntity target = null;
        double minDist = Double.MAX_VALUE;
        AABB box = player.getBoundingBox().inflate(6);

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player)) {
            Vec3 toE = e.position().subtract(eye).normalize();
            double dot = toE.dot(dir);
            double dist = e.distanceTo(player);
            if (dot > 0.82 && dist < minDist) {
                minDist = dist;
                target = e;
            }
        }

        if (target != null) {
            target.setDeltaMovement(dir.x * 3.2, 0.75, dir.z * 3.2);
            target.hurtMarked = true;
            target.hurt(player.damageSources().playerAttack(player), 14);
            if (target instanceof ServerPlayer tp) {
                tp.addEffect(fx(MobEffects.CONFUSION, 50, 6));
            }
            for (int i = 0; i < 30; i++) {
                double a = Math.random() * Math.PI * 2;
                level.sendParticles(ParticleTypes.CRIT,
                        target.getX() + Math.cos(a) * 0.4, target.getY() + 1 + Math.random(),
                        target.getZ() + Math.sin(a) * 0.4, 1, 0, 0, 0, 0.3);
            }
            level.sendParticles(ParticleTypes.EXPLOSION,
                    target.getX(), target.getY() + 1, target.getZ(),
                    3, 0.3, 0.3, 0.3, 0.1);
            for (double d = 0.5; d < minDist; d += 0.6) {
                Vec3 p = eye.add(dir.scale(d));
                level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
            player.sendSystemMessage(
                    Component.literal("SUPER PUNCH!")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    eye.x + dir.x * 3, eye.y + dir.y * 3, eye.z + dir.z * 3,
                    4, 0.3, 0.3, 0.3, 0.1);
            player.sendSystemMessage(Component.literal("Miss!").withStyle(ChatFormatting.GRAY));
        }
    }

    // ===== ABILITY: взлёт + elytra-полёт (бывший ULT) =====

    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        // Подбрасываем игрока вверх
        player.setDeltaMovement(0, 2.8, 0);
        player.hurtMarked = true;
        player.resetFallDistance();

        // Помечаем: через несколько тиков включить elytra-полёт
        player.getPersistentData().putBoolean("occka_sf_fly_active", true);
        player.getPersistentData().putInt("occka_sf_fly_ticks", 0);

        for (int i = 0; i < 20; i++) {
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + (Math.random() - 0.5) * 0.8, player.getY(),
                    player.getZ() + (Math.random() - 0.5) * 0.8,
                    1, 0.2, -0.05, 0.2, 0.04);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                player.getX(), player.getY(), player.getZ(), 1, 0, 0, 0, 0.05);
        player.sendSystemMessage(Component.literal("SUPERFORCE FLIGHT!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    // ===== ABILITY TICK: поддержка elytra-полёта из Ability =====

    public static void tickFlyAbility(ServerPlayer player, ServerLevel level) {
        var nbt = player.getPersistentData();
        if (!nbt.getBoolean("occka_sf_fly_active"))
            return;

        // Плавное падение только пока активен полёт
        player.addEffect(fx(MobEffects.SLOW_FALLING, 5, 0));

        int ticks = nbt.getInt("occka_sf_fly_ticks");
        nbt.putInt("occka_sf_fly_ticks", ticks + 1);

        // Даём игроку немного взлететь прежде чем включить elytra (5 тиков)
        if (ticks >= 5) {
            if (!player.isFallFlying()) {
                player.startFallFlying();
            }

            if (player.isFallFlying()) {
                Vec3 look = player.getLookAngle().normalize();
                double speed = 1.35;
                player.setDeltaMovement(look.x * speed, look.y * speed, look.z * speed);
                player.hurtMarked = true;
                player.resetFallDistance();
                player.fallDistance = 0;

                if (ticks % 3 == 0) {
                    level.sendParticles(ParticleTypes.CLOUD,
                            player.getX(), player.getY(), player.getZ(),
                            2, 0.2, 0.1, 0.2, 0.03);
                }

                // Приземлился — завершаем полёт
                if (player.onGround() && ticks > 20) {
                    nbt.putBoolean("occka_sf_fly_active", false);
                    player.stopFallFlying();
                    player.sendSystemMessage(
                            Component.literal("Flight ended.").withStyle(ChatFormatting.GRAY));
                }
            } else {
                // Fallback: elytra не включилась (нет крыльев) — симулируем движение
                Vec3 look = player.getLookAngle();
                player.setDeltaMovement(
                        look.x * 0.9,
                        Math.max(look.y * 0.9, -0.1),
                        look.z * 0.9);
                player.hurtMarked = true;
                player.resetFallDistance();
                if (player.onGround() && ticks > 20) {
                    nbt.putBoolean("occka_sf_fly_active", false);
                }
            }
        }

        // Таймаут 30 секунд (600 тиков)
        if (ticks > 600) {
            nbt.putBoolean("occka_sf_fly_active", false);
            if (player.isFallFlying())
                player.stopFallFlying();
        }
    }

    // ===== ULT: METEOR — с земли прыгает→летит→падает, с воздуха сразу падает
    // =====

    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        boolean inAir = !player.onGround();

        player.getPersistentData().putBoolean("occka_sf_ult_flying", true);
        player.getPersistentData().putInt("occka_sf_ult_ticks", 0);
        player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 100, 3));

        // Ищем ближайший пол под игроком
        int distToGround = 0;
        for (int i = 1; i <= 50; i++) {
            BlockPos below = BlockPos.containing(player.getX(), player.getY() - i, player.getZ());
            if (level.getBlockState(below).isSolid()) {
                distToGround = i;
                break;
            }
        }

        boolean shouldLaunchUp = inAir && distToGround > 0 && distToGround < 5;
        boolean shouldDive = inAir && (distToGround == 0 || distToGround >= 5);

        player.getPersistentData().putBoolean("occka_sf_ult_from_air", shouldDive);

        if (shouldDive) {
            player.setDeltaMovement(
                    player.getDeltaMovement().x * 0.2,
                    -3.5,
                    player.getDeltaMovement().z * 0.2);
            player.hurtMarked = true;
            player.resetFallDistance();
            player.fallDistance = 0;

            for (int i = 0; i < 20; i++) {
                level.sendParticles(ParticleTypes.FLAME,
                        player.getX() + (Math.random() - 0.5) * 0.5,
                        player.getY() + i * 0.25,
                        player.getZ() + (Math.random() - 0.5) * 0.5,
                        1, 0.1, 0.05, 0.1, 0.03);
            }
            level.sendParticles(ParticleTypes.CRIT,
                    player.getX(), player.getY(), player.getZ(),
                    10, 0.5, 0.5, 0.5, 0.1);
            player.sendSystemMessage(Component.literal("METEOR DIVE! Incoming!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        } else {
            // На земле или низко в воздухе — взлетаем
            player.setDeltaMovement(0, 2.8, 0);
            player.hurtMarked = true;
            player.resetFallDistance();

            for (int i = 0; i < 25; i++) {
                level.sendParticles(ParticleTypes.CLOUD,
                        player.getX() + (Math.random() - 0.5) * 0.8, player.getY(),
                        player.getZ() + (Math.random() - 0.5) * 0.8,
                        1, 0.2, -0.05, 0.2, 0.04);
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    player.getX(), player.getY(), player.getZ(), 2, 0, 0, 0, 0.05);
            player.sendSystemMessage(Component.literal("METEOR DIVE! Look where you want to crash!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    // ===== ULT TICK =====

    public static void tickUlt(ServerPlayer player, ServerLevel level) {
        var nbt = player.getPersistentData();

        if (nbt.getBoolean("occka_sf_ult_flying")) {
            int ticks = nbt.getInt("occka_sf_ult_ticks");
            nbt.putInt("occka_sf_ult_ticks", ticks + 1);

            boolean fromAir = nbt.getBoolean("occka_sf_ult_from_air");

            if (fromAir) {
                // === РЕЖИМ: с воздуха → принудительное падение ===
                Vec3 vel = player.getDeltaMovement();

                // Ускоряем падение, ограничиваем максимальную скорость
                double newVy = Math.max(vel.y - 0.35, -4.0);
                player.setDeltaMovement(vel.x * 0.92, newVy, vel.z * 0.92);
                player.hurtMarked = true;
                player.resetFallDistance();
                player.fallDistance = 0;

                // Частицы хвоста кометы
                if (ticks % 2 == 0) {
                    level.sendParticles(ParticleTypes.FLAME,
                            player.getX(), player.getY() + 1, player.getZ(),
                            4, 0.3, 0.3, 0.3, 0.08);
                    level.sendParticles(ParticleTypes.CRIT,
                            player.getX(), player.getY() + 1, player.getZ(),
                            2, 0.2, 0.2, 0.2, 0.05);
                }

                // Приземление — краш!
                if (player.onGround() && ticks > 5) {
                    nbt.putBoolean("occka_sf_ult_flying", false);
                    executeMeteorCrash(player, level);
                    return;
                }

            } else {
                // === РЕЖИМ: с земли → взлёт + elytra с принудительным наклоном вниз ===
                if (ticks >= 5) {
                    if (!player.isFallFlying()) {
                        player.startFallFlying();
                    }

                    if (player.isFallFlying()) {
                        Vec3 look = player.getLookAngle().normalize();

                        // Постепенно тянем нос вниз: look.y уменьшается со временем
                        double vy = look.y - ticks * 0.008;
                        vy = Math.max(vy, -1.5); // ограничиваем
                        double speed = 1.22;

                        player.setDeltaMovement(look.x * speed, vy * speed, look.z * speed);
                        player.hurtMarked = true;
                        player.resetFallDistance();
                        player.fallDistance = 0;

                        if (ticks % 2 == 0) {
                            level.sendParticles(ParticleTypes.CRIT,
                                    player.getX(), player.getY(), player.getZ(),
                                    3, 0.3, 0.3, 0.3, 0.1);
                            level.sendParticles(ParticleTypes.FLAME,
                                    player.getX(), player.getY(), player.getZ(),
                                    2, 0.2, 0.2, 0.2, 0.05);
                        }

                        // Приземление — краш!
                        if (player.onGround() && ticks > 15) {
                            nbt.putBoolean("occka_sf_ult_flying", false);
                            player.stopFallFlying();
                            executeMeteorCrash(player, level);
                            return;
                        }

                    } else {
                        // Fallback без elytra
                        Vec3 look = player.getLookAngle();
                        double vy = Math.max(look.y * 0.95 - ticks * 0.01, -1.2);
                        player.setDeltaMovement(look.x * 0.95, vy, look.z * 0.95);
                        player.hurtMarked = true;
                        player.resetFallDistance();
                        if (player.onGround() && ticks > 15) {
                            nbt.putBoolean("occka_sf_ult_flying", false);
                            executeMeteorCrash(player, level);
                        }
                    }
                }

                // Таймаут 1000 тиков (~50 сек) — принудительный краш
                if (ticks > 1000) {
                    nbt.putBoolean("occka_sf_ult_flying", false);
                    executeMeteorCrash(player, level);
                }
            }
        }

        // Camera shake тикер
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

    // ===== METEOR CRASH =====

    public static void executeMeteorCrash(ServerPlayer player, ServerLevel level) {
        Vec3 pos = player.position();

        // Ломаем блоки в радиусе 2 от точки приземления
        breakBlocksOnImpact(player, level, pos, 2);

        // Частицы кратера
        for (int deg = 0; deg < 360; deg += 5) {
            for (double r = 0.5; r <= 8; r += 1.5) {
                double x = pos.x + r * Math.cos(Math.toRadians(deg));
                double z = pos.z + r * Math.sin(Math.toRadians(deg));
                BlockPos bp = BlockPos.containing(x, pos.y - 0.3, z);
                var state = level.getBlockState(bp).isAir()
                        ? level.getBlockState(bp.below())
                        : level.getBlockState(bp);
                if (!state.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            x, pos.y + 0.2, z, 2, 0, 0.4, 0, 0.2);
                }
                if (r < 4) {
                    level.sendParticles(ParticleTypes.EXPLOSION, x, pos.y + 0.1, z, 1, 0, 0, 0, 0);
                }
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z, 5, 1.5, 0.3, 1.5, 0.08);
        level.sendParticles(ParticleTypes.FLASH,
                pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);

        // Урон и отбрасывание в радиусе 10
        for (LivingEntity entity : getNearby(player, 10)) {
            Vec3 dir = entity.position().subtract(pos);
            double dist = Math.max(0.1, dir.length());
            dir = dir.normalize();
            double force = 1.5 * (1.0 - dist / 10.0) + 0.4;
            entity.setDeltaMovement(dir.x * force, 1.5 + (1.0 - dist / 10.0) * 0.6, dir.z * force);
            entity.hurtMarked = true;
            entity.hurt(player.damageSources().playerAttack(player),
                    (float) (12 * (1 - dist / 10.0)));

            if (entity instanceof ServerPlayer tp) {
                tp.getPersistentData().putInt("occka_shake_ticks", 10);
                tp.addEffect(fx(MobEffects.CONFUSION, 50, 7));
            }
            level.sendParticles(ParticleTypes.CRIT,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    12, 0.4, 0.4, 0.4, 0.2);
        }

        // Встряска камеры самого игрока
        player.addEffect(fx(MobEffects.CONFUSION, 15, 3));
        player.getPersistentData().putInt("occka_shake_ticks", 6);

        for (Player p : level.getEntitiesOfClass(Player.class,
                player.getBoundingBox().inflate(15), x -> true)) {
            ((ServerPlayer) p).sendSystemMessage(
                    Component.literal("METEOR CRASH!")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    /**
     * Ломает блоки в радиусе вокруг точки удара.
     * Пропускает bedrock, barrier и indestructible блоки. Без дропа лута.
     */
    private static void breakBlocksOnImpact(ServerPlayer player, ServerLevel level,
            Vec3 pos, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius)
                        continue;
                    BlockPos bp = BlockPos.containing(pos.x + dx, pos.y + dy, pos.z + dz);
                    BlockState state = level.getBlockState(bp);
                    if (state.isAir())
                        continue;
                    if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER))
                        continue;
                    if (state.getDestroySpeed(level, bp) < 0)
                        continue;
                    level.removeBlock(bp, false);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5,
                            5, 0.3, 0.3, 0.3, 0.1);
                }
            }
        }
    }

    private static List<LivingEntity> getNearby(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && !(e instanceof Player p && p.isAlliedTo(player)));
    }
}
