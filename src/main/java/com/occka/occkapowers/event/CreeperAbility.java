package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

public final class CreeperAbility {
    private CreeperAbility() {
    }

    // ===== CHARGE LEVELS (тики) =====
    public static final int CHARGE_TICKS_MAX = 320; // 16 сек = макс заряд (уровень 4)
    public static final int CHARGE_TICKS_LEVEL3 = 240; // 12 сек
    public static final int CHARGE_TICKS_LEVEL2 = 160; // 8 сек
    public static final int CHARGE_TICKS_LEVEL1 = 80; // 4 сек

    public static final String NBT_CHARGE = "occka_creeper_charge";
    public static final String NBT_CHARGING = "occka_creeper_charging";
    public static final String NBT_CHARGE_EXPIRE = "occka_creeper_charge_expire";
    public static final String NBT_LAST_TICK = "occka_creeper_last_tick";
    public static final String NBT_ULT_TICKS = "occka_creeper_ult_ticks";
    public static final String NBT_POWERED = "occka_creeper_powered";
    public static final String NBT_LAST_STAGE_MSG = "occka_creeper_last_stage_msg";
    public static final String NBT_LAST_PCT_MSG = "occka_creeper_last_pct_msg";
    public static final String NBT_CATAPULT_ACTIVE = "occka_creeper_catapult_active";

    // ===================================================================
    // PASSIVE — снимаем агро злых мобов с игрока
    // ===================================================================
    public static void tickPassive(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(20);
        List<net.minecraft.world.entity.Mob> mobs = level.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class, box,
                mob -> mob.getTarget() instanceof ServerPlayer sp
                        && sp.getUUID().equals(player.getUUID()));
        for (net.minecraft.world.entity.Mob mob : mobs) {
            mob.setTarget(null);
        }
        player.setSilent(true);
    }

    // ===================================================================
    // SHIFT — зарядка (вызывается каждый тик пока шифт зажат)
    // ===================================================================
    public static void tickCharging(ServerPlayer player, ServerLevel level) {
        int charge = player.getPersistentData().getInt(NBT_CHARGE);

        // Если заряд был сохранён после предыдущего отпускания — сначала сброс
        // (перезаряжаться с нуля, если заряд уже истёк — просто накапливаем)
        if (charge < CHARGE_TICKS_MAX) {
            charge++;
            player.getPersistentData().putInt(NBT_CHARGE, charge);
        }

        player.getPersistentData().putBoolean(NBT_CHARGING, true);
        player.getPersistentData().putInt(NBT_LAST_TICK, player.tickCount);
        // Сбрасываем expire пока заряжаемся
        player.getPersistentData().remove(NBT_CHARGE_EXPIRE);

        // Замедление
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 1, false, false));

        // Эффекты по уровню заряда
        applyChargeEffects(player, charge, true);
        sendChargeProgressMessage(player, charge);

        // Аура
        spawnChargeAura(player, level, charge);
    }

    // Вызывается из tickChargeDecay когда обнаружено что шифт отпущен
    private static void onShiftRelease(ServerPlayer player, ServerLevel level) {
        player.getPersistentData().putBoolean(NBT_CHARGING, false);
        int charge = player.getPersistentData().getInt(NBT_CHARGE);

        if (charge > 0) {
            // Сохраняем заряд на 400 тиков (20 сек)
            player.getPersistentData().putLong(NBT_CHARGE_EXPIRE, level.getGameTime() + 400);
        }
        player.getPersistentData().putInt(NBT_LAST_PCT_MSG, -1);
        player.getPersistentData().putInt(NBT_LAST_STAGE_MSG, -1);
    }

    // ===================================================================
    // TICK — вызывается каждый тик из AbilityEventHandler
    // ===================================================================
    public static void tickChargeDecay(ServerPlayer player, ServerLevel level) {
        boolean wasCharging = player.getPersistentData().getBoolean(NBT_CHARGING);

        // Детектируем отпускание шифта: если 2+ тика не было tickCharging
        if (wasCharging) {
            int lastTick = player.getPersistentData().getInt(NBT_LAST_TICK);
            if (player.tickCount - lastTick > 2) {
                onShiftRelease(player, level);
                wasCharging = false;
            }
        }

        if (wasCharging)
            return; // пока заряжаемся — не тикаем истечение

        int charge = player.getPersistentData().getInt(NBT_CHARGE);
        if (charge <= 0)
            return;

        long expireAt = player.getPersistentData().getLong(NBT_CHARGE_EXPIRE);
        if (expireAt > 0 && level.getGameTime() >= expireAt) {
            // Заряд истёк
            player.getPersistentData().putInt(NBT_CHARGE, 0);
            player.getPersistentData().remove(NBT_CHARGE_EXPIRE);
            player.getPersistentData().putBoolean(NBT_POWERED, false);
            player.removeEffect(MobEffects.JUMP);
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
            player.removeEffect(MobEffects.REGENERATION);
            player.sendSystemMessage(AbilityCommon.msg("Charge faded.", ChatFormatting.DARK_GREEN));
            return;
        }

        // Поддерживаем эффекты
        applyChargeEffects(player, charge, false);

        // Аура раз в 8 тиков
        if (player.tickCount % 8 == 0) {
            spawnChargeAura(player, level, charge);
        }
    }

    // ===================================================================
    // CHARGE EFFECTS
    // ===================================================================
    private static void applyChargeEffects(ServerPlayer player, int charge, boolean isCharging) {
        int dur = isCharging ? 5 : 30;
        int regenDur = 25;
        if (charge >= CHARGE_TICKS_LEVEL1 && charge < CHARGE_TICKS_LEVEL2) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, dur, 0, false, false));
        } else if (charge >= CHARGE_TICKS_LEVEL2 && charge < CHARGE_TICKS_LEVEL3) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, dur, 1, false, false));
        } else if (charge >= CHARGE_TICKS_LEVEL3 && charge < CHARGE_TICKS_MAX) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, dur, 1, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1, false, false));
        } else if (charge >= CHARGE_TICKS_MAX) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, dur, 2, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenDur, 0, false, false));
            player.getPersistentData().putBoolean(NBT_POWERED, true);
        }
    }

    private static void spawnChargeAura(ServerPlayer player, ServerLevel level, int charge) {
        float t = Math.min(1f, (float) charge / CHARGE_TICKS_MAX);
        // Зелёный (0,1,0) → синий (0,0.3,1)
        float r = 0f;
        float g = 1f - t * 0.7f;
        float b = t;

        int count = 2 + (int) (t * 5);
        double radius = 0.7 + t * 1.0;

        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2;
            level.sendParticles(
                    new DustParticleOptions(new Vector3f(r, g, b), 0.9f),
                    player.getX() + radius * Math.cos(angle),
                    player.getY() + Math.random() * 2.2,
                    player.getZ() + radius * Math.sin(angle),
                    1, 0, 0, 0, 0);
        }

        if (charge >= CHARGE_TICKS_MAX && player.tickCount % 6 == 0) {
            level.sendParticles(new DustParticleOptions(new Vector3f(0f, 0.2f, 1f), 1.5f),
                    player.getX(), player.getY() + 1, player.getZ(),
                    4, 1.2, 1.2, 1.2, 0.08);
        }
    }

    private static int getChargeStage(int charge) {
        if (charge >= CHARGE_TICKS_MAX)
            return 4;
        if (charge >= CHARGE_TICKS_LEVEL3)
            return 3;
        if (charge >= CHARGE_TICKS_LEVEL2)
            return 2;
        if (charge >= CHARGE_TICKS_LEVEL1)
            return 1;
        return 0;
    }

    private static void sendChargeProgressMessage(ServerPlayer player, int charge) {
        int stage = getChargeStage(charge);
        int percent = (int) Math.floor(getChargeProgress(charge) * 100.0f);
        int percentStep = (percent / 10) * 10;

        int lastStage = player.getPersistentData().getInt(NBT_LAST_STAGE_MSG);
        int lastPercentStep = player.getPersistentData().getInt(NBT_LAST_PCT_MSG);

        boolean stageChanged = stage != lastStage;
        boolean pctChanged = percentStep != lastPercentStep;
        if (!stageChanged && !pctChanged)
            return;

        player.getPersistentData().putInt(NBT_LAST_STAGE_MSG, stage);
        player.getPersistentData().putInt(NBT_LAST_PCT_MSG, percentStep);

        ChatFormatting color = stage >= 4 ? ChatFormatting.BLUE : ChatFormatting.GREEN;
        player.sendSystemMessage(AbilityCommon.msg(
                "Charge: " + percent + "% (Stage " + stage + "/4)",
                color));
    }

    /**
     * Прогресс для HUD (0..1). Используется как shiftProgress.
     */
    public static float getChargeProgress(int charge) {
        return Math.min(1f, (float) charge / CHARGE_TICKS_MAX);
    }

    // ===================================================================
    // ABILITY — Catapult (рывок)
    // ===================================================================
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        boolean powered = player.getPersistentData().getBoolean(NBT_POWERED);
        Vec3 look = player.getLookAngle().normalize();

        double distance = powered ? 45.0 : 25.0;
        double speed = distance / 8.0;

        Vec3 vel = new Vec3(look.x * speed, Math.max(look.y * speed, 0.4), look.z * speed);
        player.setDeltaMovement(vel);
        player.hurtMarked = true;
        player.resetFallDistance();
        player.fallDistance = 0;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 2, false, false));
        player.getPersistentData().putBoolean(NBT_CATAPULT_ACTIVE, true);

        Vec3 startPos = player.position();

        // Эффект взрыва на месте старта
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                startPos.x, startPos.y, startPos.z, 2, 0.2, 0.1, 0.2, 0.05);
        level.sendParticles(ParticleTypes.EXPLOSION,
                startPos.x, startPos.y, startPos.z, 6, 0.4, 0.2, 0.4, 0.1);
        level.sendParticles(ParticleTypes.FLASH,
                startPos.x, startPos.y + 0.5, startPos.z, 1, 0, 0, 0, 0);
        for (int i = 0; i < 15; i++) {
            double a = Math.random() * Math.PI * 2;
            double rr = Math.random() * 2.5;
            level.sendParticles(ParticleTypes.SMOKE,
                    startPos.x + rr * Math.cos(a),
                    startPos.y + Math.random() * 1.5,
                    startPos.z + rr * Math.sin(a),
                    1, 0, 0.03, 0, 0.02);
        }

        if (powered) {
            double aoeRadius = 15.0;
            AABB box = new AABB(
                    startPos.x - aoeRadius, startPos.y - 1, startPos.z - aoeRadius,
                    startPos.x + aoeRadius, startPos.y + 4, startPos.z + aoeRadius);
            List<LivingEntity> nearby = level.getEntitiesOfClass(
                    LivingEntity.class, box, e -> e != player);
            for (LivingEntity entity : nearby) {
                entity.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 100, 2, false, false));
                Vec3 cur = entity.getDeltaMovement();
                entity.setDeltaMovement(cur.x * 0.4, -2.5, cur.z * 0.4);
                entity.hurtMarked = true;
            }

            // Кольцо частиц
            for (int deg = 0; deg < 360; deg += 12) {
                for (double rr = 2; rr <= aoeRadius; rr += 3.5) {
                    double x = startPos.x + rr * Math.cos(Math.toRadians(deg));
                    double z = startPos.z + rr * Math.sin(Math.toRadians(deg));
                    level.sendParticles(
                            new DustParticleOptions(new Vector3f(0f, 0.3f, 1f), 1f),
                            x, startPos.y + 0.15, z, 1, 0, 0, 0, 0);
                }
            }

            player.sendSystemMessage(AbilityCommon.msg(
                    "POWERED CATAPULT!", ChatFormatting.BLUE, ChatFormatting.BOLD));
        } else {
            player.sendSystemMessage(AbilityCommon.msg("Catapult!", ChatFormatting.GREEN));
        }
    }

    public static void tickCatapultLanding(ServerPlayer player, ServerLevel level) {
        if (!player.getPersistentData().getBoolean(NBT_CATAPULT_ACTIVE))
            return;
        if (player.getPersistentData().getInt(NBT_ULT_TICKS) > 0)
            return;
        if (!player.onGround())
            return;

        player.getPersistentData().putBoolean(NBT_CATAPULT_ACTIVE, false);
        Vec3 pos = player.position();

        // Снижаем self-damage через сопротивление
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1, false, false));

        level.explode(player, pos.x, pos.y, pos.z,
                2.5f, true, ServerLevel.ExplosionInteraction.TNT);
        level.sendParticles(ParticleTypes.EXPLOSION,
                pos.x, pos.y + 0.2, pos.z, 8, 0.8, 0.2, 0.8, 0.03);

        // Урон самому игроку
        player.hurt(player.damageSources().explosion(player, player), 10.0f);
    }

    // ===================================================================
    // ULT — Self Destruct
    // ===================================================================
    public static void startUlt(ServerPlayer player, ServerLevel level) {
        boolean powered = player.getPersistentData().getBoolean(NBT_POWERED);
        player.getPersistentData().putInt(NBT_ULT_TICKS, 100);
        player.getPersistentData().putBoolean("occka_creeper_ult_powered", powered);
        player.getPersistentData().putBoolean(NBT_CATAPULT_ACTIVE, false);

        // Замораживаем игрока (замедление 128)
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 115, 127, false, false));

        player.sendSystemMessage(AbilityCommon.msg(
                powered ? "⚡ CHARGED SELF DESTRUCT! 5s..." : "💥 SELF DESTRUCT! 5s...",
                ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    public static void tickUlt(ServerPlayer player, ServerLevel level) {
        int ticks = player.getPersistentData().getInt(NBT_ULT_TICKS);
        if (ticks <= 0)
            return;

        ticks--;
        player.getPersistentData().putInt(NBT_ULT_TICKS, ticks);

        boolean powered = player.getPersistentData().getBoolean("occka_creeper_ult_powered");
        Vec3 pos = player.position();

        // Нарастающие частицы огня
        float progress = (100f - ticks) / 100f;
        int particleCount = 3 + (int) (progress * 15);
        double spread = 0.4 + progress * 1.8;

        for (int i = 0; i < particleCount; i++) {
            double angle = Math.random() * Math.PI * 2;
            double rr = Math.random() * spread;
            level.sendParticles(ParticleTypes.FLAME,
                    pos.x + rr * Math.cos(angle),
                    pos.y + Math.random() * 2.5,
                    pos.z + rr * Math.sin(angle),
                    1, 0, 0.02, 0, 0.02);
        }

        if (ticks % 8 == 0 && ticks > 0) {
            level.sendParticles(ParticleTypes.EXPLOSION,
                    pos.x, pos.y + 1, pos.z, 1, 0.3, 0.2, 0.3, 0.05);
        }

        // Мигающий звук (через частицы flash каждые 20-10-5 тиков по убыванию)
        int flashInterval = Math.max(4, 20 - (int) (progress * 16));
        if (ticks % flashInterval == 0 && ticks > 0) {
            level.sendParticles(new DustParticleOptions(
                    powered ? new Vector3f(0.3f, 0.5f, 1f) : new Vector3f(0.2f, 0.9f, 0.2f), 1.2f),
                    pos.x, pos.y + 1.2, pos.z, 3, 0.3, 0.3, 0.3, 0);
        }

        if (ticks == 0) {
            executeExplosion(player, level, powered);
        }
    }

    private static void executeExplosion(ServerPlayer player, ServerLevel level, boolean powered) {
        Vec3 pos = player.position();
        double radius = powered ? 25.0 : 15.0;
        float centerDmg = powered ? 100f : 80f;
        float edgeDmg = powered ? 30f : 20f;

        // Взрыв блоков
        float blastStrength = powered ? 10.0f : 6.0f;
        level.explode(null, pos.x, pos.y + 0.5, pos.z, blastStrength,
                powered,
                powered ? ServerLevel.ExplosionInteraction.TNT : ServerLevel.ExplosionInteraction.BLOCK);

        // Урон сущностям
        AABB box = new AABB(
                pos.x - radius, pos.y - radius * 0.5, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class, box, e -> e != player);

        for (LivingEntity entity : targets) {
            double dist = entity.position().distanceTo(pos);
            if (dist > radius)
                continue;
            float t = (float) (1.0 - dist / radius);
            float dmg = edgeDmg + (centerDmg - edgeDmg) * t;
            entity.hurt(player.damageSources().explosion(null, null), dmg);
            Vec3 kb = entity.position().subtract(pos).normalize().scale(2.5 + t * 2.5);
            entity.setDeltaMovement(kb.x, 0.8 + t * 0.8, kb.z);
            entity.hurtMarked = true;
        }

        // ===== ВИЗУАЛ ВЗРЫВА =====
        // Центральный взрыв
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y + 1, pos.z, powered ? 10 : 5, 1.5, 0.5, 1.5, 0.06);
        level.sendParticles(ParticleTypes.FLASH,
                pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);

        // Концентрические кольца взрыва
        int rings = powered ? 4 : 3;
        for (int ring = 1; ring <= rings; ring++) {
            double rr = radius * ring / (double) rings;
            for (int deg = 0; deg < 360; deg += powered ? 6 : 10) {
                double x = pos.x + rr * Math.cos(Math.toRadians(deg));
                double z = pos.z + rr * Math.sin(Math.toRadians(deg));
                level.sendParticles(ParticleTypes.EXPLOSION, x, pos.y + 0.3, z, 1, 0, 0, 0, 0);
                if (deg % 20 == 0) {
                    level.sendParticles(ParticleTypes.FLAME, x, pos.y + 0.5, z, 2, 0.15, 0.4, 0.15, 0.04);
                }
            }
        }

        // Дым
        for (int i = 0; i < 50; i++) {
            double a = Math.random() * Math.PI * 2;
            double rr = Math.random() * radius;
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.x + rr * Math.cos(a),
                    pos.y + Math.random() * 6,
                    pos.z + rr * Math.sin(a),
                    1, 0, 0, 0, 0.01);
        }

        if (powered) {
            // Заряженный: синие частицы
            for (int i = 0; i < 100; i++) {
                double a = Math.random() * Math.PI * 2;
                double p = (Math.random() - 0.5) * Math.PI;
                double rr = Math.random() * radius;
                level.sendParticles(
                        new DustParticleOptions(new Vector3f(0.2f, 0.5f, 1f), 1.5f),
                        pos.x + rr * Math.cos(a) * Math.cos(p),
                        pos.y + 2 + rr * Math.abs(Math.sin(p)),
                        pos.z + rr * Math.sin(a) * Math.cos(p),
                        1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.x, pos.y + 1, pos.z, 60, 4, 4, 4, 0.4);
        }

        // Сброс заряда
        player.getPersistentData().putInt(NBT_CHARGE, 0);
        player.getPersistentData().putBoolean(NBT_POWERED, false);
        player.getPersistentData().remove(NBT_CHARGE_EXPIRE);

        player.sendSystemMessage(AbilityCommon.msg(
                powered ? "⚡ CHARGED CREEPER! KABOOM!" : "💥 CREEPER EXPLOSION!",
                ChatFormatting.GREEN, ChatFormatting.BOLD));
    }
}
