package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class VaderAbility {
    private VaderAbility() {
    }

    // ===== SHIFT: переключение ауры страха =====
    public static void activateShift(ServerPlayer player, ServerLevel level) {
        boolean active = player.getPersistentData().getBoolean("occka_vader_aura_active");
        if (!active) {
            enableAura(player, level);
        } else {
            disableAura(player, level);
        }
    }

    private static void enableAura(ServerPlayer player, ServerLevel level) {
        player.getPersistentData().putBoolean("occka_vader_aura_active", true);

        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 1, player.getZ(),
                20, 0.8, 0.8, 0.8, 0.05);
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1, player.getZ(),
                15, 0.5, 0.5, 0.5, 0.1);

        player.sendSystemMessage(AbilityCommon.msg(
                "Fear Aura: ACTIVE", ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    private static void disableAura(ServerPlayer player, ServerLevel level) {
        player.getPersistentData().putBoolean("occka_vader_aura_active", false);

        level.sendParticles(ParticleTypes.POOF,
                player.getX(), player.getY() + 1, player.getZ(),
                10, 0.5, 0.5, 0.5, 0.05);

        player.sendSystemMessage(AbilityCommon.msg(
                "Fear Aura: OFF", ChatFormatting.GRAY));
    }

    // Тик ауры — вызывается каждый тик из AbilityEventHandler
    public static void tickAura(ServerPlayer player, ServerLevel level) {
        if (!player.getPersistentData().getBoolean("occka_vader_aura_active"))
            return;

        // Дебаффы на себя (цена ауры)
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 25, 1)); // замедление II
        player.addEffect(AbilityCommon.fx(MobEffects.HUNGER, 25, 0)); // голод I
        player.addEffect(AbilityCommon.fx(MobEffects.DARKNESS, 25, 0));

        // Эффекты на всех в радиусе 5 блоков каждые 20 тиков
        if (player.tickCount % 20 == 0) {
            AABB box = player.getBoundingBox().inflate(5);
            List<LivingEntity> nearby = level.getEntitiesOfClass(
                    LivingEntity.class, box, e -> e != player && e.isAlive());

            for (LivingEntity entity : nearby) {
                entity.addEffect(AbilityCommon.fx(MobEffects.WEAKNESS, 25, 0));
                entity.addEffect(AbilityCommon.fx(MobEffects.DIG_SLOWDOWN, 25, 2));
                entity.addEffect(AbilityCommon.fx(MobEffects.CONFUSION, 100, 0));
                entity.addEffect(AbilityCommon.fx(MobEffects.DARKNESS, 25, 0));
            }

            // Частицы ауры каждую секунду
            for (int i = 0; i < 20; i++) {
                double angle = Math.random() * Math.PI * 2;
                double r = 1.5 + Math.random() * 3.5;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        player.getX() + r * Math.cos(angle),
                        player.getY() + Math.random() * 2,
                        player.getZ() + r * Math.sin(angle),
                        1, 0, 0, 0, 0.01);
            }
        }
    }

    // ===== ABILITY: удушающий захват =====
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        // Если уже держим кого-то — отпускаем
        if (player.getPersistentData().contains("occka_vader_grip_target")) {
            releaseGrip(player, level, true);
            return;
        }

        // Ищем цель в луче 15 блоков
        LivingEntity target = findTargetInBeam(player, level, 15.0);
        if (target == null) {
            player.sendSystemMessage(AbilityCommon.msg(
                    "No target in sight!", ChatFormatting.DARK_RED));
            return;
        }

        // Сохраняем UUID цели и дистанцию захвата
        double captureDistance = target.distanceTo(player);
        player.getPersistentData().putUUID("occka_vader_grip_target", target.getUUID());
        player.getPersistentData().putDouble("occka_vader_grip_distance", captureDistance);
        player.getPersistentData().putInt("occka_vader_grip_ticks", 100); // 5 сек

        // Поднимаем цель на 2 блока вверх и отключаем гравитацию
        target.teleportTo(target.getX(), target.getY() + 2, target.getZ());
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);

        // Частицы захвата
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(), target.getY() + 1, target.getZ(),
                25, 0.4, 0.6, 0.4, 0.05);
        level.sendParticles(ParticleTypes.PORTAL,
                target.getX(), target.getY() + 1, target.getZ(),
                20, 0.4, 0.6, 0.4, 0.1);

        String name = target instanceof Player p
                ? p.getName().getString()
                : target.getType().getDescription().getString();
        player.sendSystemMessage(AbilityCommon.msg(
                "Choking: " + name + "! (5s)", ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    // Тик захвата — каждый тик из AbilityEventHandler
    public static void tickGrip(ServerPlayer player, ServerLevel level) {
        if (!player.getPersistentData().contains("occka_vader_grip_target"))
            return;

        int ticks = player.getPersistentData().getInt("occka_vader_grip_ticks");
        if (ticks <= 0) {
            releaseGrip(player, level, false);
            return;
        }

        UUID targetUUID = player.getPersistentData().getUUID("occka_vader_grip_target");
        LivingEntity target = findByUUID(level, player, targetUUID);

        if (target == null || !target.isAlive()) {
            player.getPersistentData().remove("occka_vader_grip_target");
            player.getPersistentData().remove("occka_vader_grip_distance");
            player.getPersistentData().remove("occka_vader_grip_ticks");
            return;
        }

        ticks--;
        player.getPersistentData().putInt("occka_vader_grip_ticks", ticks);

        double distance = player.getPersistentData().getDouble("occka_vader_grip_distance");

        // Вычисляем целевую позицию цели:
        // она должна быть на расстоянии distance от игрока по направлению взгляда
        // (только горизонталь+Y)
        Vec3 look = player.getLookAngle().normalize();

        // Горизонтальное направление без вертикальной составляющей для стабильности
        Vec3 horizontal = new Vec3(look.x, 0, look.z).normalize();
        // Если смотришь прямо вверх/вниз — используем прошлое горизонтальное
        // направление
        if (horizontal.lengthSqr() < 0.01)
            horizontal = new Vec3(1, 0, 0);

        // Цель крутится по радиусу distance от игрока в направлении взгляда
        double targetX = player.getX() + horizontal.x * distance;
        double targetY = player.getY() + 1.5; // держим на уровне груди игрока
        double targetZ = player.getZ() + horizontal.z * distance;

        // Плавно тянем цель к целевой позиции (чтоб не было резких рывков)
        Vec3 currentPos = target.position();
        Vec3 toTarget = new Vec3(targetX, targetY, targetZ).subtract(currentPos);

        // Ограничиваем скорость перемещения
        double speed = Math.min(toTarget.length(), 0.8);
        Vec3 moveVel = toTarget.normalize().scale(speed);

        target.setDeltaMovement(moveVel.x, moveVel.y, moveVel.z);
        target.setNoGravity(true);
        target.hurtMarked = true;
        target.fallDistance = 0;

        // Урон от удушения каждые 15 тиков (~0.75 сек)
        if (ticks % 15 == 0) {
            target.hurt(player.damageSources().magic(), 1.5f);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + 1, target.getZ(),
                    5, 0.2, 0.2, 0.2, 0.1);
        }

        // Частицы удушения вокруг цели
        if (ticks % 3 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    target.getX(), target.getY() + target.getBbHeight() * 0.7,
                    target.getZ(), 2, 0.2, 0.1, 0.2, 0.02);
        }

        // Линия-захват от руки игрока до цели (частицы)
        if (ticks % 2 == 0) {
            Vec3 handPos = player.getEyePosition().add(look.scale(0.8));
            Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
            int steps = 10;
            for (int i = 0; i <= steps; i++) {
                Vec3 p = handPos.lerp(targetCenter, i / (double) steps);
                level.sendParticles(new DustParticleOptions(
                        new Vector3f(0.6f, 0.0f, 0.8f), 0.5f),
                        p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    // Отпускаем цель
    private static void releaseGrip(ServerPlayer player, ServerLevel level, boolean early) {
        if (!player.getPersistentData().contains("occka_vader_grip_target"))
            return;

        UUID targetUUID = player.getPersistentData().getUUID("occka_vader_grip_target");
        player.getPersistentData().remove("occka_vader_grip_target");
        player.getPersistentData().remove("occka_vader_grip_distance");
        player.getPersistentData().remove("occka_vader_grip_ticks");

        LivingEntity target = findByUUID(level, player, targetUUID);
        if (target == null)
            return;

        // Восстанавливаем гравитацию
        target.setNoGravity(false);

        // Бросок: кидаем цель в направлении взгляда игрока
        Vec3 throwDir = player.getLookAngle().normalize();
        target.setDeltaMovement(throwDir.x * 1.5, 0.6, throwDir.z * 1.5);
        target.hurtMarked = true;

        // Замедление после броска
        target.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
        target.addEffect(AbilityCommon.fx(MobEffects.WEAKNESS, 100, 1));

        // Частицы броска
        level.sendParticles(ParticleTypes.EXPLOSION,
                target.getX(), target.getY() + 1, target.getZ(),
                3, 0.3, 0.3, 0.3, 0.1);

        player.sendSystemMessage(AbilityCommon.msg(
                early ? "Released!" : "Choke ended — target thrown!",
                ChatFormatting.DARK_RED));
    }

    // ===== ULT: Death Star Laser =====
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        // Определяем точку прицела (до 100 блоков)
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 end = eye.add(dir.scale(100));

        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        Vec3 target;
        if (blockHit.getType() != HitResult.Type.MISS) {
            target = Vec3.atCenterOf(blockHit.getBlockPos());
        } else {
            target = end;
        }

        // Сохраняем точку удара и таймер
        player.getPersistentData().putDouble("occka_vader_ult_x", target.x);
        player.getPersistentData().putDouble("occka_vader_ult_y", target.y);
        player.getPersistentData().putDouble("occka_vader_ult_z", target.z);
        player.getPersistentData().putInt("occka_vader_ult_ticks", 200); // 10 сек предупреждение

        // Начальный луч-предупреждение (тонкий визуал)
        drawWarningBeam(level, eye, target);

        player.sendSystemMessage(AbilityCommon.msg(
                "MAXIMUM PULSE CHARGING... 10s!", ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    // Тик ульты — расширяющийся круг предупреждения → удар
    public static void tickUlt(ServerPlayer player, ServerLevel level) {
        int ticks = player.getPersistentData().getInt("occka_vader_ult_ticks");
        if (ticks <= 0)
            return;

        ticks--;
        player.getPersistentData().putInt("occka_vader_ult_ticks", ticks);

        double cx = player.getPersistentData().getDouble("occka_vader_ult_x");
        double cy = player.getPersistentData().getDouble("occka_vader_ult_y");
        double cz = player.getPersistentData().getDouble("occka_vader_ult_z");

        // Прогресс 0..1 (0 = только начали, 1 = удар)
        float progress = 1.0f - ticks / 200.0f;

        // Расширяющийся круг предупреждения
        double currentRadius = progress * 10.0; // радиус растёт от 0 до 10
        spawnWarningCircle(level, cx, cy, cz, currentRadius, ticks);

        // Луч-предупреждение каждые 5 тиков
        if (ticks % 5 == 0) {
            Vec3 fromPlayer = player.getEyePosition();
            Vec3 toTarget = new Vec3(cx, cy, cz);
            drawWarningBeam(level, fromPlayer, toTarget);
        }

        // Удар на последнем тике
        if (ticks == 0) {
            executeLaser(player, level, cx, cy, cz);
        }
    }

    // Исполнение лазера
    private static void executeLaser(ServerPlayer player, ServerLevel level,
            double cx, double cy, double cz) {

        final double RADIUS = 10.0;

        // Луч от игрока до точки — мощный визуал
        Vec3 from = player.getEyePosition();
        Vec3 to = new Vec3(cx, cy, cz);
        Vec3 dir = to.subtract(from).normalize();
        double length = from.distanceTo(to);

        // Главный луч
        for (double d = 0; d <= length; d += 0.3) {
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(new DustParticleOptions(
                    new Vector3f(1.0f, 0.2f, 0.0f), 1.5f),
                    p.x, p.y, p.z, 3, 0.15, 0.15, 0.15, 0);
            level.sendParticles(ParticleTypes.FLAME,
                    p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.05);
        }

        // Взрыв в точке удара
        level.explode(null, cx, cy, cz, 6.0f, true, 
            ServerLevel.ExplosionInteraction.BLOCK);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                cx, cy, cz, 5, 1, 1, 1, 0.05);
        level.sendParticles(ParticleTypes.FLASH,
                cx, cy, cz, 1, 0, 0, 0, 0);

        // Урон + поджигание в радиусе 10
        AABB box = new AABB(cx - RADIUS, cy - RADIUS, cz - RADIUS,
                cx + RADIUS, cy + RADIUS, cz + RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class, box,
                e -> e != player && e.isAlive());

        for (LivingEntity entity : targets) {
            double dist = entity.position().distanceTo(new Vec3(cx, cy, cz));
            if (dist > RADIUS)
                continue;

            // Урон уменьшается с расстоянием
            float damage = (float)(40.0 * (1.0 - dist / RADIUS)) + 20.0f;
            entity.hurt(player.damageSources().magic(), damage);
            entity.setSecondsOnFire(8);

            // Отброс от центра
            Vec3 kb = entity.position().subtract(cx, cy, cz).normalize().scale(1.5);
            entity.setDeltaMovement(kb.x, 0.8, kb.z);
            entity.hurtMarked = true;

            level.sendParticles(ParticleTypes.FLAME,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    15, 0.5, 0.5, 0.5, 0.1);
        }

        // Уведомление всем в радиусе
        for (Player p : level.getEntitiesOfClass(Player.class,
                new AABB(cx - 50, cy - 20, cz - 50, cx + 50, cy + 20, cz + 50), x -> true)) {
            ((ServerPlayer) p).sendSystemMessage(Component.literal(
                    player.getName().getString() + "'s MAXIMUM PULSE fired!")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
    }

    // Расширяющийся круг предупреждения
    private static void spawnWarningCircle(ServerLevel level,
            double cx, double cy, double cz,
            double radius, int ticks) {
        if (radius < 0.3)
            return;

        // Пульсация цвета: от жёлтого к красному по мере приближения удара
        float progress = 1.0f - ticks / 200.0f;
        float r = 1.0f;
        float g = 1.0f - progress; // жёлтый → красный
        float b = 0.0f;

        int points = Math.max(12, (int) (radius * 6));
        for (int i = 0; i < points; i++) {
            double angle = (i / (double) points) * Math.PI * 2;
            double px = cx + radius * Math.cos(angle);
            double pz = cz + radius * Math.sin(angle);

            // Кружим по земле
            level.sendParticles(new DustParticleOptions(new Vector3f(r, g, b), 1.0f),
                    px, cy + 0.1, pz, 1, 0, 0, 0, 0);

            // Пульсирующие столбы каждые 5 точек
            if (i % 5 == 0) {
                for (double h = 0; h < 3; h += 0.5) {
                    level.sendParticles(new DustParticleOptions(new Vector3f(r, g * 0.5f, b), 0.6f),
                            px, cy + h, pz, 1, 0, 0, 0, 0);
                }
            }
        }

        // Крест в центре
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                cx, cy + 0.2, cz, 2, 0.1, 0, 0.1, 0.01);
    }

    // Тонкий луч предупреждения
    private static void drawWarningBeam(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from).normalize();
        double len = from.distanceTo(to);
        for (double d = 0; d <= len; d += 1.0) {
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(new DustParticleOptions(
                    new Vector3f(1.0f, 0.3f, 0.0f), 0.4f),
                    p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    // ===== HELPERS =====

    // Поиск цели в луче с широким хитбоксом (приоритет — игроки)
    private static LivingEntity findTargetInBeam(ServerPlayer player,
            ServerLevel level, double range) {

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        AABB searchBox = player.getBoundingBox()
                .expandTowards(look.scale(range))
                .inflate(3.0);

        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e != player && e.isAlive());

        // Сортируем: сначала игроки, потом по дистанции
        candidates.sort(Comparator
                .<LivingEntity, Integer>comparing(e -> e instanceof Player ? 0 : 1)
                .thenComparingDouble(e -> e.distanceTo(player)));

        for (LivingEntity entity : candidates) {
            Vec3[] checkPoints = {
                    entity.getEyePosition(),
                    entity.position().add(0, entity.getBbHeight() * 0.5, 0),
                    entity.position().add(0, entity.getBbHeight() * 0.15, 0),
            };

            double hitRadius = 1.4 + entity.getBbWidth() * 0.5;

            for (Vec3 point : checkPoints) {
                Vec3 toPoint = point.subtract(eye);
                double dot = toPoint.dot(look);
                if (dot < 0.5 || dot > range)
                    continue;

                Vec3 proj = eye.add(look.scale(dot));
                if (proj.distanceTo(point) < hitRadius) {
                    // Проверяем нет ли блока между игроком и целью
                    BlockHitResult blockHit = level.clip(new ClipContext(
                            eye, point,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, player));
                    if (blockHit.getType() == HitResult.Type.MISS) {
                        return entity; // цель найдена, путь чист
                    }
                    break;
                }
            }
        }
        return null;
    }

    // Поиск сущности по UUID в загруженных чанках
    private static LivingEntity findByUUID(ServerLevel level,
            ServerPlayer player, UUID uuid) {
        AABB wide = player.getBoundingBox().inflate(200);
        for (LivingEntity e : level.getEntitiesOfClass(
                LivingEntity.class, wide,
                e -> e.getUUID().equals(uuid))) {
            return e;
        }
        return null;
    }
}