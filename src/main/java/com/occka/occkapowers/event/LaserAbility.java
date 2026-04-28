package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;

import java.util.List;

public final class LaserAbility {
    private LaserAbility() {}

    public static final double ULT_LASER_LENGTH = 40.0;
    public static final int ULT_LASER_MAX_TICKS = 140;

    private static final DustParticleOptions LASER_DUST_CORE =
            new DustParticleOptions(new Vector3f(1.0f, 0.05f, 0.0f), 0.6f);
    private static final DustParticleOptions LASER_DUST_GLOW =
            new DustParticleOptions(new Vector3f(1.0f, 0.3f, 0.1f), 1.0f);

    // SHIFT (held): подсветка врагов + линия прицела
    public static void activateShift(ServerPlayer player, ServerLevel level) {
        AbilityCommon.getNearbyEnemies(player, 30)
                .forEach(e -> e.addEffect(AbilityCommon.fx(MobEffects.GLOWING, 25, 0)));

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (int i = 1; i <= 15; i++) {
            Vec3 p = eye.add(look.scale(i));
            level.sendParticles(LASER_DUST_CORE, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
        }
    }

    // ABILITY: лазерный луч 18 урона + разрушение блоков если нет моба
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        fireLaserBeam(player, level, 15);
    }

    private static void fireLaserBeam(ServerPlayer player, ServerLevel level, double length) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();

        level.playSound(null, player.blockPosition(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.8f);

        // Сначала ищем моба на пути
        LivingEntity hitEntity = findFirstEntityInBeam(player, level, start, dir, length, 2.5);

        if (hitEntity != null) {
            // Есть моб — бьём, блоки не трогаем
            hitEntity.hurt(player.damageSources().magic(), 18);
            level.sendParticles(LASER_DUST_GLOW,
                    hitEntity.getX(), hitEntity.getY() + 1, hitEntity.getZ(),
                    20, 0.4, 0.4, 0.4, 0);
            level.sendParticles(ParticleTypes.EXPLOSION,
                    hitEntity.getX(), hitEntity.getY() + 1, hitEntity.getZ(),
                    3, 0.2, 0.2, 0.2, 0.05);

            double hitDot = hitEntity.position().subtract(start).dot(dir);
            drawBeam(level, start, dir, hitDot);

            Vec3 endPoint = start.add(dir.scale(hitDot));
            level.sendParticles(LASER_DUST_GLOW, endPoint.x, endPoint.y, endPoint.z, 8, 0.3, 0.3, 0.3, 0);

        } else {
            // Нет моба — 3 блока подряд, 30% шанс дропа
            drawBeamAndBreakBlock(player, level, start, dir, length, false);
        }

        level.sendParticles(ParticleTypes.FLASH,
                start.x + dir.x, start.y + dir.y, start.z + dir.z, 1, 0, 0, 0, 0);
        player.sendSystemMessage(AbilityCommon.msg("Laser Beam!", ChatFormatting.RED));
    }

    // ===== ULT =====

    public static void startUlt(ServerPlayer player) {
        player.getPersistentData().putBoolean("occka_laser_ult_active", true);
        player.getPersistentData().putInt("occka_laser_ult_ticks", ULT_LASER_MAX_TICKS);

        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 2.0f);
            level.playSound(null, player.blockPosition(),
                    SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.5f);
        }

        player.sendSystemMessage(AbilityCommon.msg(
                "CYCLOPS LASER! Hold [G] to sustain. 7s max.",
                ChatFormatting.RED, ChatFormatting.BOLD));
    }

    public static boolean tickUltChannel(ServerPlayer player, ServerLevel level) {
        var nbt = player.getPersistentData();
        if (!nbt.getBoolean("occka_laser_ult_active")) return false;

        int ticks = nbt.getInt("occka_laser_ult_ticks");
        if (ticks <= 0) {
            endUltChannel(player, level, true);
            // КД при таймауте
            player.getCapability(com.occka.occkapowers.registry.ModCapabilities.PLAYER_POWER)
                    .ifPresent(data -> {
                        data.setUltCooldown(com.occka.occkapowers.ability.PowerType.LASER.getUltCooldown());
                        AbilityActivator.syncToClient(player, data);
                    });
            return true;
        }

        nbt.putInt("occka_laser_ult_ticks", ticks - 1);

        if (ticks % 8 == 0) {
            level.playSound(null, player.blockPosition(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.4f, 2.0f);
        }

        fireChannelLaser(player, level);
        return false;
    }

    public static boolean stopUltChannel(ServerPlayer player, ServerLevel level) {
        var nbt = player.getPersistentData();
        if (!nbt.getBoolean("occka_laser_ult_active")) return false;
        endUltChannel(player, level, false);
        return true;
    }

    public static boolean isUltChanneling(ServerPlayer player) {
        return player.getPersistentData().getBoolean("occka_laser_ult_active");
    }

    private static void endUltChannel(ServerPlayer player, ServerLevel level, boolean timedOut) {
        player.getPersistentData().putBoolean("occka_laser_ult_active", false);
        player.getPersistentData().putInt("occka_laser_ult_ticks", 0);

        // Без CONFUSION — убрана белая пелена
        player.addEffect(AbilityCommon.fx(MobEffects.BLINDNESS, 200, 0));
        player.addEffect(AbilityCommon.fx(MobEffects.WEAKNESS, 200, 1));

        level.playSound(null, player.blockPosition(),
                SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 1.0f, 0.8f);

        String msg = timedOut ? "Laser drained! Eyes burning..." : "Laser released. Eyes burning...";
        player.sendSystemMessage(AbilityCommon.msg(msg, ChatFormatting.DARK_RED));

        level.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1.5, player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0.01);
    }

    private static void fireChannelLaser(ServerPlayer player, ServerLevel level) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();

        // Сначала ищем моба на пути
        LivingEntity hitEntity = findFirstEntityInBeam(player, level, start, dir, ULT_LASER_LENGTH, 1.2);

        if (hitEntity != null) {
            // Есть моб — бьём, блоки не трогаем
            hitEntity.hurt(player.damageSources().magic(), 3.0f);
            Vec3 kb = dir.scale(0.15);
            hitEntity.setDeltaMovement(hitEntity.getDeltaMovement().add(kb));
            hitEntity.hurtMarked = true;

            level.sendParticles(LASER_DUST_GLOW,
                    hitEntity.getX(), hitEntity.getY() + 1, hitEntity.getZ(),
                    6, 0.3, 0.3, 0.3, 0);
            level.sendParticles(ParticleTypes.EXPLOSION,
                    hitEntity.getX(), hitEntity.getY() + 1, hitEntity.getZ(),
                    1, 0.1, 0.1, 0.1, 0.05);

            double hitDot = hitEntity.getEyePosition().subtract(start).dot(dir);
            drawBeam(level, start, dir, hitDot);

            Vec3 endPoint = start.add(dir.scale(hitDot));
            level.sendParticles(LASER_DUST_GLOW, endPoint.x, endPoint.y, endPoint.z, 8, 0.3, 0.3, 0.3, 0);

        } else {
            // Нет моба — 1 блок за тик, 30% шанс дропа
            drawBeamAndBreakBlock(player, level, start, dir, ULT_LASER_LENGTH, true);
        }

        // Свечение у глаз игрока
        level.sendParticles(LASER_DUST_GLOW, start.x, start.y, start.z, 3, 0.1, 0.1, 0.1, 0);
    }

    // ===== HELPERS =====

    /**
     * Ищет ближайшую сущность в луче.
     * thickness — толщина луча для хитбокса.
     */
    private static LivingEntity findFirstEntityInBeam(ServerPlayer player, ServerLevel level,
            Vec3 start, Vec3 dir, double length, double thickness) {

        AABB searchBox = player.getBoundingBox().inflate(length + 2);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player);

        LivingEntity closest = null;
        double closestDot = Double.MAX_VALUE;

        for (LivingEntity entity : candidates) {
            Vec3 toE = entity.getEyePosition().subtract(start);
            double dot = toE.dot(dir);
            if (dot > 0 && dot < length) {
                Vec3 proj = start.add(dir.scale(dot));
                if (proj.distanceTo(entity.getEyePosition()) < thickness) {
                    if (dot < closestDot) {
                        closestDot = dot;
                        closest = entity;
                    }
                }
            }
        }
        return closest;
    }

    /**
     * Просто рисует луч из частиц до указанной дистанции.
     */
    private static void drawBeam(ServerLevel level, Vec3 start, Vec3 dir, double distance) {
        for (double d = 0.3; d <= distance; d += 0.3) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(LASER_DUST_CORE, p.x, p.y, p.z, 2, 0.01, 0.01, 0.01, 0);
            if (d % 0.6 < 0.3) {
                level.sendParticles(LASER_DUST_GLOW,
                        p.x + (Math.random() - 0.5) * 0.15,
                        p.y + (Math.random() - 0.5) * 0.15,
                        p.z + (Math.random() - 0.5) * 0.15,
                        1, 0, 0, 0, 0);
            }
            if (d % 2.0 < 0.3) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.05);
            }
        }
    }

    /**
     * Рисует луч и разрушает первый встреченный блок.
     * Вызывается только когда на пути луча нет мобов.
     * При абилке ломает 3 блока подряд, при ульте — 1, но каждый тик.
     */
    private static void drawBeamAndBreakBlock(ServerPlayer player, ServerLevel level,
            Vec3 start, Vec3 dir, double length) {
        drawBeamAndBreakBlock(player, level, start, dir, length, false);
    }

    private static void drawBeamAndBreakBlock(ServerPlayer player, ServerLevel level,
            Vec3 start, Vec3 dir, double length, boolean isUlt) {

        // Шанс выпадения лута при разрушении блока (30%)
        final double DROP_CHANCE = 0.30;
        // Абилка ломает 3 блока подряд, ульт — 1 блок за тик
        final int MAX_BLOCKS = isUlt ? 1 : 3;

        double hitDist = length;
        Vec3 hitPos = null;
        int blocksDestroyed = 0;

        for (double d = 0.3; d <= length; d += 0.3) {
            Vec3 p = start.add(dir.scale(d));
            BlockPos bp = BlockPos.containing(p);
            BlockState state = level.getBlockState(bp);

            if (!state.isAir()
                    && !state.getBlock().equals(Blocks.BEDROCK)
                    && !state.getBlock().equals(Blocks.BARRIER)
                    && state.getDestroySpeed(level, bp) >= 0) {

                // Выпадение лута с шансом DROP_CHANCE
                if (Math.random() < DROP_CHANCE) {
                    // dropResources — стандартный дроп блока как при разрушении
                    state.getBlock().playerDestroy(level, player, bp, state,
                            null, player.getMainHandItem());
                    level.removeBlock(bp, false);
                } else {
                    // Без дропа — просто убираем блок
                    level.removeBlock(bp, false);
                }

                level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.1);
                level.sendParticles(LASER_DUST_GLOW, p.x, p.y, p.z, 5, 0.2, 0.2, 0.2, 0);

                if (hitPos == null) {
                    hitDist = d;
                    hitPos = p;
                }

                blocksDestroyed++;
                if (blocksDestroyed >= MAX_BLOCKS) {
                    break; // достигли лимита блоков
                }
                // Продолжаем луч сквозь следующий шаг
                continue;
            }

            // Рисуем луч только до первого разрушенного блока
            if (blocksDestroyed == 0) {
                level.sendParticles(LASER_DUST_CORE, p.x, p.y, p.z, 2, 0.01, 0.01, 0.01, 0);
                if (d % 0.6 < 0.3) {
                    level.sendParticles(LASER_DUST_GLOW,
                            p.x + (Math.random() - 0.5) * 0.15,
                            p.y + (Math.random() - 0.5) * 0.15,
                            p.z + (Math.random() - 0.5) * 0.15,
                            1, 0, 0, 0, 0);
                }
                if (d % 2.0 < 0.3) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.05);
                }
            }
        }

        Vec3 endPoint = hitPos != null ? hitPos : start.add(dir.scale(hitDist));
        level.sendParticles(LASER_DUST_GLOW, endPoint.x, endPoint.y, endPoint.z, 8, 0.3, 0.3, 0.3, 0);
        level.sendParticles(ParticleTypes.FLASH, endPoint.x, endPoint.y, endPoint.z, 1, 0, 0, 0, 0);
    }
}
