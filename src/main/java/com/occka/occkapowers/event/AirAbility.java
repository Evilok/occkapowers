package com.occka.occkapowers.event;

import com.occka.occkapowers.ability.PlayerPowerData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class AirAbility {

    private AirAbility() {}

    // Максимальная высота подъёма сущностей в торнадо
    private static final double TORNADO_MAX_HEIGHT = 8.0;
    // Радиус торнадо у основания
    private static final double TORNADO_BASE_RADIUS = 10.0;
    // Орбитальный радиус кружения у земли
    private static final double ORBIT_RADIUS = 5.0;

    // ===== SHIFT =====

    public static void activateShift(ServerPlayer player, ServerLevel level) {
        player.addEffect(AbilityCommon.fx(MobEffects.SLOW_FALLING, 40, 0));
        player.addEffect(AbilityCommon.fx(MobEffects.JUMP, 40, 1));

        Vec3 vel = player.getDeltaMovement();

        // лёгкое удержание в воздухе + скольжение вперёд
        Vec3 look = player.getLookAngle();
        Vec3 newVel = new Vec3(
                look.x * 0.6,
                Math.max(vel.y, 0.15),
                look.z * 0.6);

        player.setDeltaMovement(newVel);
        player.hurtMarked = true;

        for (int i = 0; i < 6; i++) {
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() - 0.2, player.getZ(),
                    1, 0.3, 0.1, 0.3, 0.02);
        }

    }

    // ===== ABILITY: дэш в направлении взгляда (с вертикалью) =====

    public static void activateAbility(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        dashForward(player, level);
    }

    private static void dashForward(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();

        // Горизонталь + вертикаль по взгляду — позволяет подниматься
        Vec3 vel = new Vec3(
                look.x * 2.8,
                look.y * 1.4 + 0.2, // +0.2 минимальный подъём даже при горизонтальном взгляде
                look.z * 2.8);

        player.setDeltaMovement(vel);
        player.hurtMarked = true;
        player.addEffect(AbilityCommon.fx(MobEffects.SLOW_FALLING, 100, 0));

        Vec3 start = player.position();
        for (int i = 0; i < 25; i++) {
            Vec3 behind = start.subtract(look.scale(i * 0.35));
            level.sendParticles(ParticleTypes.CLOUD,
                    behind.x + (Math.random() - 0.5) * 0.6,
                    behind.y + 0.5 + Math.random() * 1.5,
                    behind.z + (Math.random() - 0.5) * 0.6,
                    2, 0.1, 0.1, 0.1, 0.03);
        }
        level.sendParticles(ParticleTypes.POOF,
                start.x, start.y + 1, start.z, 30, 0.6, 0.6, 0.6, 0.15);
        level.sendParticles(ParticleTypes.CLOUD,
                start.x, start.y + 1, start.z, 20, 0.5, 0.5, 0.5, 0.08);

        player.sendSystemMessage(AbilityCommon.msg("Dash!", ChatFormatting.AQUA));
    }

    // ===== ULT: запуск торнадо =====

    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        player.getPersistentData().putDouble("occka_air_tornado_x", player.getX());
        player.getPersistentData().putDouble("occka_air_tornado_y", player.getY());
        player.getPersistentData().putDouble("occka_air_tornado_z", player.getZ());
        player.getPersistentData().putInt("occka_air_tornado_ticks", 200); // 10 сек

        for (int i = 0; i < 80; i++) {
            double a = Math.random() * Math.PI * 2;
            double r = Math.random() * TORNADO_BASE_RADIUS;
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + r * Math.cos(a),
                    player.getY() + 1 + Math.random() * 4,
                    player.getZ() + r * Math.sin(a),
                    1, 0, 0, 0, 0.06);
        }

        player.addEffect(AbilityCommon.fx(MobEffects.SLOW_FALLING, 220, 0));
        player.sendSystemMessage(AbilityCommon.msg(
                "TORNADO! 10s — everything in range gets swept up!",
                ChatFormatting.AQUA, ChatFormatting.BOLD));
    }

    // ===== TICK торнадо — вызывается из AbilityEventHandler каждый тик =====

    public static void tickTornado(ServerPlayer player, ServerLevel level) {
        int ticks = player.getPersistentData().getInt("occka_air_tornado_ticks");
        if (ticks <= 0) return;

        ticks--;
        player.getPersistentData().putInt("occka_air_tornado_ticks", ticks);

        double cx = player.getPersistentData().getDouble("occka_air_tornado_x");
        double cy = player.getPersistentData().getDouble("occka_air_tornado_y");
        double cz = player.getPersistentData().getDouble("occka_air_tornado_z");

        // --- Визуал: конус широкий внизу, узкий вверху ---
        spawnTornadoParticles(level, cx, cy, cz, ticks);

        // --- Захват новых целей в основании торнадо ---
        AABB captureBox = new AABB(
                cx - TORNADO_BASE_RADIUS, cy - 1, cz - TORNADO_BASE_RADIUS,
                cx + TORNADO_BASE_RADIUS, cy + TORNADO_MAX_HEIGHT + 3, cz + TORNADO_BASE_RADIUS);

        List<LivingEntity> all = level.getEntitiesOfClass(
                LivingEntity.class, captureBox,
                e -> e != player && e.isAlive());

        // Разделяем игроков и мобов
        List<LivingEntity> players = new ArrayList<>();
        List<LivingEntity> mobs    = new ArrayList<>();
        for (LivingEntity e : all) {
            if (e instanceof Player) players.add(e);
            else mobs.add(e);
        }

        // Все игроки попадают, мобов — максимум 10 ближайших к центру
        List<LivingEntity> targets = new ArrayList<>(players);
        if (mobs.size() <= 10) {
            targets.addAll(mobs);
        } else {
            mobs.sort((a, b) -> Double.compare(distSqFlat(a, cx, cz), distSqFlat(b, cx, cz)));
            targets.addAll(mobs.subList(0, 10));
        }

        // Помечаем активные цели NBT-тегом (для снятия гравитации по окончании)
        for (LivingEntity e : targets) {
            e.getPersistentData().putBoolean("occka_tornado_gripped", true);
        }

        // --- Применяем физику ---
        for (LivingEntity entity : targets) {
            applyTornadoForce(entity, cx, cy, cz);

            // Урон 0.5–1.0 каждые 10 тиков
            if (ticks % 10 == 0) {
                entity.hurt(player.damageSources().magic(),
                        0.5f + (float)(Math.random() * 0.5f));
            }
        }

        // --- Конец торнадо ---
        if (ticks == 0) {
            releaseAllGripped(player, level);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    cx, cy + 5, cz, 2, 1.5, 1.5, 1.5, 0.05);
            player.sendSystemMessage(AbilityCommon.msg(
                    "Tornado dissipated.", ChatFormatting.AQUA));
        }
    }

    // Восстанавливаем гравитацию всем помеченным сущностям
    private static void releaseAllGripped(ServerPlayer player, ServerLevel level) {
        // Ищем в большой области — сущности могли разлететься по зоне
        AABB wideBox = player.getBoundingBox().inflate(TORNADO_BASE_RADIUS + 10);
        List<LivingEntity> gripped = level.getEntitiesOfClass(
                LivingEntity.class, wideBox,
                e -> e.getPersistentData().getBoolean("occka_tornado_gripped"));

        for (LivingEntity e : gripped) {
            // Для мобов — снимаем NoGravity и бросаем вниз
            e.setNoGravity(false);
            e.getPersistentData().remove("occka_tornado_gripped");

            // Сбрасываем горизонтальную скорость и даём импульс вниз
            Vec3 cur = e.getDeltaMovement();
            double downForce = -2.5;

            if (e instanceof ServerPlayer sp) {
                // Для игроков setNoGravity не работает так же как для мобов,
                // поэтому просто принудительно задаём скорость падения
                sp.setDeltaMovement(cur.x * 0.2, downForce, cur.z * 0.2);
                sp.hurtMarked = true;
                // Убираем slow_falling если он был от торнадо
                sp.removeEffect(MobEffects.SLOW_FALLING);
                sp.removeEffect(MobEffects.LEVITATION);
                // Добавляем небольшой debuff чтобы гарантировать падение
                sp.addEffect(AbilityCommon.fx(MobEffects.SLOW_FALLING, 1, 0)); // 1 тик — сброс
                sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
            } else {
                e.setDeltaMovement(cur.x * 0.2, downForce, cur.z * 0.2);
                e.hurtMarked = true;
            }
        }
    }

    private static double distSqFlat(LivingEntity e, double cx, double cz) {
        double dx = e.getX() - cx;
        double dz = e.getZ() - cz;
        return dx * dx + dz * dz;
    }

    // Визуал торнадо: конус ШИРОКИЙ СНИЗУ, узкий сверху
    private static void spawnTornadoParticles(ServerLevel level,
            double cx, double cy, double cz, int ticks) {

        double phase = ticks * 0.28; // угол вращения меняется каждый тик
        int layers = 14;

        for (int i = 0; i < layers; i++) {
            double t      = i / (double) layers; // 0 = низ, 1 = верх
            double height = t * 13.0;

            // ШИРОКИЙ СНИЗУ (t=0 → r=BASE_RADIUS), УЗКИЙ СВЕРХУ (t=1 → r=BASE_RADIUS*0.1)
            double r = TORNADO_BASE_RADIUS * (0.1 + t * 0.9);
            r = Math.max(r, 0.4);

            // Два полных витка спирали по высоте
            double angle = phase + t * Math.PI * 4;

            double px = cx + r * Math.cos(angle);
            double pz = cz + r * Math.sin(angle);

            level.sendParticles(ParticleTypes.CLOUD,
                    px, cy + height, pz,
                    1, 0.12, 0.04, 0.12, 0.01);

            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.POOF,
                        px + (Math.random() - 0.5) * 0.8,
                        cy + height + Math.random() * 0.5,
                        pz + (Math.random() - 0.5) * 0.8,
                        1, 0.08, 0.04, 0.08, 0.02);
            }
        }
    }

    // Орбитальная физика с ограничением высоты
    private static void applyTornadoForce(LivingEntity entity,
            double cx, double cy, double cz) {

        double ex = entity.getX() - cx;
        double ey = entity.getY() - cy; // высота над основанием торнадо
        double ez = entity.getZ() - cz;
        double distHoriz = Math.sqrt(ex * ex + ez * ez);

        // Нормаль от центра
        double nx = distHoriz > 0.01 ? ex / distHoriz : 1.0;
        double nz = distHoriz > 0.01 ? ez / distHoriz : 0.0;

        // Целевой орбитальный радиус зависит от высоты:
        // у земли = ORBIT_RADIUS, у потолка сужается (как стенки конуса)
        double heightFraction = Math.max(0, Math.min(ey / TORNADO_MAX_HEIGHT, 1.0));
        double targetOrbit = ORBIT_RADIUS * (1.0 - heightFraction * 0.75);
        targetOrbit = Math.max(targetOrbit, 0.5);

        // Радиальная тяга к орбите
        double pullStrength;
        if (distHoriz < targetOrbit - 0.5) {
            pullStrength = 0.16;  // слишком близко к оси — выталкиваем
        } else if (distHoriz > targetOrbit + 1.5) {
            pullStrength = -0.13; // слишком далеко — притягиваем
        } else {
            pullStrength = 0.0;   // на орбите
        }

        // Тангенциальная скорость — закрутка против часовой стрелки
        double tangX = -nz * 0.40;
        double tangZ =  nx * 0.40;

        // Вертикальная скорость с жёстким потолком
        double vy;
        if (ey < TORNADO_MAX_HEIGHT - 0.3) {
            vy = 0.22; // поднимаем
        } else if (ey > TORNADO_MAX_HEIGHT + 0.5) {
            vy = -0.20; // выше потолка — тянем вниз
        } else {
            vy = 0.0; // на потолке — держим
        }

        Vec3 cur = entity.getDeltaMovement();
        entity.setDeltaMovement(
                cur.x * 0.40 + tangX + nx * pullStrength,
                cur.y * 0.15 + vy, // сильное гашение вертикальной инерции
                cur.z * 0.40 + tangZ + nz * pullStrength);

        // Для игроков не используем setNoGravity — управляем движением напрямую
        if (!(entity instanceof Player)) {
            entity.setNoGravity(true);
        } else {
            // Для игроков добавляем levitation чтобы противодействовать гравитации
            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    MobEffects.LEVITATION, 3, 0, false, false));
        }

        entity.hurtMarked = true;
        entity.fallDistance = 0;
    }
}