package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class LaserAbility {
    private LaserAbility() {}

    // Длина луча ульты
    public static final double ULT_LASER_LENGTH = 40.0;
    // Максимум тиков канала (7 секунд = 140 тиков)
    public static final int ULT_LASER_MAX_TICKS = 140;

    // SHIFT (held): подсветка врагов + линия прицела
    public static void activateShift(ServerPlayer player, ServerLevel level) {
        AbilityCommon.getNearbyEnemies(player, 30)
                .forEach(e -> e.addEffect(AbilityCommon.fx(MobEffects.GLOWING, 25, 0)));

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (int i = 1; i <= 15; i++) {
            Vec3 p = eye.add(look.scale(i));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 2, 0.03, 0.03, 0.03, 0.01);
        }
    }

    // ABILITY: плотный лазерный луч, 18 урона магией
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        fireLaserBeam(player, level, 15);
    }

    private static void fireLaserBeam(ServerPlayer player, ServerLevel level, double length) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();

        for (LivingEntity entity : AbilityCommon.getNearbyEnemies(player, 12)) {
            Vec3 toE = entity.position().subtract(start);
            double dot = toE.dot(dir);
            if (dot > 0 && dot < length) {
                Vec3 proj = start.add(dir.scale(dot));
                if (proj.distanceTo(entity.position()) < 2.5) {
                    entity.hurt(player.damageSources().magic(), 18);
                    level.sendParticles(ParticleTypes.CRIT,
                            entity.getX(), entity.getY() + 1, entity.getZ(),
                            25, 0.5, 0.5, 0.5, 0.3);
                }
            }
        }

        for (double d = 0.3; d < length; d += 0.3) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            if (d % 1.5 < 0.3)
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        level.sendParticles(ParticleTypes.FLASH,
                start.x + dir.x, start.y + dir.y, start.z + dir.z, 1, 0, 0, 0, 0);
        player.sendSystemMessage(AbilityCommon.msg("Laser Beam!", ChatFormatting.RED));
    }

    // ===== ULT: CHANNEL LASER (Cyclops beam) =====

    /**
     * Вызывается при первом нажатии ульты — запускает канал.
     */
    public static void startUlt(ServerPlayer player) {
        player.getPersistentData().putBoolean("occka_laser_ult_active", true);
        player.getPersistentData().putInt("occka_laser_ult_ticks", ULT_LASER_MAX_TICKS);
        player.sendSystemMessage(AbilityCommon.msg(
                "CYCLOPS LASER! Hold [G] to sustain. 7s max.",
                ChatFormatting.RED, ChatFormatting.BOLD));
    }

    /**
     * Вызывается каждый тик пока кнопка зажата (из AbilityEventHandler).
     * Возвращает true если канал завершился (ставить КД).
     */
    public static boolean tickUltChannel(ServerPlayer player, ServerLevel level) {
        var nbt = player.getPersistentData();
        if (!nbt.getBoolean("occka_laser_ult_active")) return false;

        int ticks = nbt.getInt("occka_laser_ult_ticks");
        if (ticks <= 0) {
            endUltChannel(player, level, true);
            return true;
        }

        nbt.putInt("occka_laser_ult_ticks", ticks - 1);

        fireChannelLaser(player, level);
        return false;
    }

    /**
     * Вызывается когда игрок отпустил кнопку ульты.
     */
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

        // Дебаффы: слепота 10с + слабость 10с
        player.addEffect(AbilityCommon.fx(MobEffects.BLINDNESS, 200, 0));
        player.addEffect(AbilityCommon.fx(MobEffects.WEAKNESS, 200, 1));

        String msg = timedOut
                ? "Laser drained! You are temporarily blinded."
                : "Laser released. Eyes burning...";
        player.sendSystemMessage(AbilityCommon.msg(msg, ChatFormatting.DARK_RED));

        // Яркая вспышка в конце
        level.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1.5, player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0.01);
    }

    /**
     * Основная логика луча: частицы, урон по сущностям, разрушение блоков.
     */
    private static void fireChannelLaser(ServerPlayer player, ServerLevel level) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();

        // --- Урон по сущностям вдоль луча ---
        AABB searchBox = player.getBoundingBox().inflate(ULT_LASER_LENGTH + 2);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player);

        // Попадаем только тех кто реально в луче
        for (LivingEntity entity : candidates) {
            Vec3 toE = entity.getEyePosition().subtract(start);
            double dot = toE.dot(dir);
            if (dot > 0 && dot < ULT_LASER_LENGTH) {
                Vec3 proj = start.add(dir.scale(dot));
                // Толщина луча ~1.2 блока
                if (proj.distanceTo(entity.getEyePosition()) < 1.2) {
                    // 3 урона магией в тик (~60 в секунду — достаточно)
                    entity.hurt(player.damageSources().magic(), 3.0f);
                    // Дополнительный knockback от луча
                    Vec3 kb = dir.scale(0.15);
                    entity.setDeltaMovement(entity.getDeltaMovement().add(kb));
                    entity.hurtMarked = true;

                    // Спарклы на попадание
                    level.sendParticles(ParticleTypes.CRIT,
                            entity.getX(), entity.getY() + 1, entity.getZ(),
                            4, 0.3, 0.3, 0.3, 0.2);
                    level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                            entity.getX(), entity.getY() + 1, entity.getZ(),
                            2, 0.1, 0.1, 0.1, 0.1);
                }
            }
        }

        // --- Луч вперёд: частицы + разрушение блоков ---
        // Стреляем по шагам 0.5 блока
        double hitDist = ULT_LASER_LENGTH; // по умолчанию — полная длина
        Vec3 hitPos = null;

        for (double d = 0.5; d <= ULT_LASER_LENGTH; d += 0.5) {
            Vec3 p = start.add(dir.scale(d));
            BlockPos bp = BlockPos.containing(p);
            BlockState state = level.getBlockState(bp);

            if (!state.isAir() && !state.getBlock().equals(Blocks.BEDROCK)
                    && !state.getBlock().equals(Blocks.BARRIER)
                    && state.getDestroySpeed(level, bp) >= 0) {

                // Разрушаем блок лазером
                level.removeBlock(bp, false);

                // Частицы разрушения блока
                level.sendParticles(ParticleTypes.EXPLOSION,
                        p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.1);
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        p.x, p.y, p.z, 1, 0, 0, 0, 0.02);

                hitDist = d;
                hitPos = p;
                break; // луч остановился
            }

            // Рисуем луч (красные + белые частицы)
            level.sendParticles(ParticleTypes.CRIT,
                    p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0);
            if (d % 1.0 < 0.5) {
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0);
            }
            // Каждые 3 блока — более яркая точка
            if (d % 3.0 < 0.5) {
                level.sendParticles(ParticleTypes.FLASH,
                        p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }

        // Вспышка в конце луча
        Vec3 endPoint = hitPos != null ? hitPos : start.add(dir.scale(hitDist));
        level.sendParticles(ParticleTypes.EXPLOSION,
                endPoint.x, endPoint.y, endPoint.z, 3, 0.2, 0.2, 0.2, 0.05);

        // Лёгкое свечение вокруг игрока пока он стреляет
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.5, player.getZ(),
                1, 0.15, 0.1, 0.15, 0.01);
    }
}
