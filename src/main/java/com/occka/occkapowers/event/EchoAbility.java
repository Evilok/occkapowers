package com.occka.occkapowers.event;

import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.registry.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class EchoAbility {
    private EchoAbility() {
    }

    // ===== SHIFT: свап с ближайшим + урон + инвиз =====
    public static void activateShift(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        if (data.getShiftCooldown() > 0)
            return;
        // Ищем ближайшего — сначала игроков, потом всех остальных
        LivingEntity target = null;
        double minD = Double.MAX_VALUE;

        AABB box = player.getBoundingBox().inflate(30);

        // Приоритет — игроки
        for (Player p : level.getEntitiesOfClass(Player.class, box, p -> p != player)) {
            double d = p.distanceTo(player);
            if (d < minD) {
                minD = d;
                target = p;
            }
        }
        // Если игроков нет — любая живая сущность
        if (target == null) {
            for (LivingEntity e : AbilityCommon.getNearbyEnemies(player, 20)) {
                double d = e.distanceTo(player);
                if (d < minD) {
                    minD = d;
                    target = e;
                }
            }
        }
        if (target == null) {
            player.sendSystemMessage(AbilityCommon.msg("No targets nearby!", ChatFormatting.RED));
            return;
        }

        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position();

        // Частицы на обоих позициях
        level.sendParticles(ParticleTypes.PORTAL,
                playerPos.x, playerPos.y + 1, playerPos.z, 25, 0.5, 1, 0.5, 0.15);
        level.sendParticles(ParticleTypes.PORTAL,
                targetPos.x, targetPos.y + 1, targetPos.z, 25, 0.5, 1, 0.5, 0.15);

        // Свап позиций
        player.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        target.teleportTo(playerPos.x, playerPos.y, playerPos.z);

        // Небольшой урон цели (6 магического)
        target.hurt(player.damageSources().magic(), 6.0f);
        level.sendParticles(ParticleTypes.CRIT,
                targetPos.x, targetPos.y + 1, targetPos.z,
                15, 0.4, 0.4, 0.4, 0.15);

        // Инвиз себе на 10 секунд
        player.addEffect(AbilityCommon.fx(MobEffects.INVISIBILITY, 200, 0));

        level.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);

        String targetName = target instanceof Player p
                ? p.getName().getString()
                : target.getType().getDescription().getString();
        player.sendSystemMessage(AbilityCommon.msg(
                "Swapped with " + targetName + "! Invisible 10s.", ChatFormatting.GREEN));
        data.setShiftCooldown(300);
    }

    // ===== ABILITY: луч-метка → повторное нажатие = свап =====
    // Логика: если метки нет — пускаем луч и вешаем метку (без КД).
    // если метка есть — телепортируемся к цели и ставим КД.
    public static void activateAbility(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        // Если метка уже стоит — выполняем свап
        if (player.getPersistentData().hasUUID("occka_echo_mark_target")) {
            executeMarkSwap(player, level, data);
            return;
        }

        if (data.getAbilityCooldown() > 0)
            return;
        // Иначе — пускаем луч и вешаем метку (КД не ставим)
        fireMark(player, level);
    }

    private static void fireMark(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        double range = 50.0;
        // Толщина хитбокса луча — достаточно широкая чтобы попасть
        double thickness = 1.8;

        LivingEntity target = findEntityInBeam(player, level, eye, dir, range, thickness);

        if (target == null) {
            // Промах — рисуем луч но НЕ ставим КД
            drawBeam(level, eye, dir, range, false);
            player.sendSystemMessage(AbilityCommon.msg(
                    "Miss! No cooldown.", ChatFormatting.GRAY));
            return;
        }

        // Попали — вешаем метку
        player.getPersistentData().putUUID("occka_echo_mark_target", target.getUUID());

        // Рисуем луч до цели
        double hitDist = target.getEyePosition().subtract(eye).dot(dir);
        drawBeam(level, eye, dir, hitDist, true);

        // Вспышка на цели
        level.sendParticles(ParticleTypes.FLASH,
                target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                target.getX(), target.getY() + 1, target.getZ(),
                20, 0.4, 0.8, 0.4, 0.1);

        String targetName = target instanceof Player p
                ? p.getName().getString()
                : target.getType().getDescription().getString();
        player.sendSystemMessage(AbilityCommon.msg(
                "Mark set on: " + targetName + " | Press [F] again to swap!", ChatFormatting.GREEN));
    }

    private static void executeMarkSwap(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        UUID targetUUID = player.getPersistentData().getUUID("occka_echo_mark_target");
        // Снимаем метку в любом случае
        player.getPersistentData().remove("occka_echo_mark_target");

        // Ищем цель в широком радиусе (любое расстояние в пределах загруженных чанков)
        LivingEntity target = null;
        // Ищем среди всех загруженных сущностей уровня
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(500), // широкий поиск
                e -> e.getUUID().equals(targetUUID) && e.isAlive())) {
            target = e;
            break;
        }

        if (target == null) {
            player.sendSystemMessage(AbilityCommon.msg(
                    "Mark target lost! (dead or unloaded)", ChatFormatting.RED));
            // КД НЕ ставим — цель пропала
            return;
        }

        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position();

        // Частицы отправки и прибытия
        level.sendParticles(ParticleTypes.PORTAL,
                playerPos.x, playerPos.y + 1, playerPos.z, 30, 0.5, 1, 0.5, 0.15);
        level.sendParticles(ParticleTypes.PORTAL,
                targetPos.x, targetPos.y + 1, targetPos.z, 30, 0.5, 1, 0.5, 0.15);

        // Свап
        player.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        target.teleportTo(playerPos.x, playerPos.y, playerPos.z);

        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + 1, player.getZ(), 25, 0.5, 1, 0.5, 0.15);
        level.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);

        // КД только после успешного свапа
        data.setAbilityCooldown(data.getPowerType().getAbilityCooldown());
        AbilityActivator.syncToClient(player, data);

        String targetName = target instanceof Player p
                ? p.getName().getString()
                : target.getType().getDescription().getString();
        player.sendSystemMessage(AbilityCommon.msg(
                "Swapped with marked target: " + targetName + "!", ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    // ===== ULT: оригинальный — ослепление + спектатор 8с =====
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        // Ослепление всем в радиусе 15 (кроме самого игрока)
        for (LivingEntity entity : AbilityCommon.getNearbyEnemies(player, 15)) {
            entity.addEffect(AbilityCommon.fx(MobEffects.BLINDNESS, 100, 0)); // 5с
            level.sendParticles(ParticleTypes.PORTAL,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    20, 0.5, 1, 0.5, 0.1);
        }

        // Себя — спектатор на 8 секунд
        player.setGameMode(GameType.SPECTATOR);
        player.getPersistentData().putInt("occka_echo_ult_ticks", 160); // 8s = 160 тиков

        level.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1, player.getZ(),
                60, 3, 3, 3, 0.1);
        player.sendSystemMessage(AbilityCommon.msg(
                "Echo Phase: Spectator for 8s! Enemies blinded 5s.",
                ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    // ===== HELPERS =====

    // Луч с хитбоксом — ищет первую сущность на пути
    private static LivingEntity findEntityInBeam(ServerPlayer player, ServerLevel level,
            Vec3 eye, Vec3 dir, double range, double thickness) {
        AABB searchBox = player.getBoundingBox().inflate(range + 2);
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class, searchBox, e -> e != player && e.isAlive());

        LivingEntity closest = null;
        double closestDot = Double.MAX_VALUE;

        for (LivingEntity entity : candidates) {
            // Проверяем несколько точек хитбокса сущности для надёжного попадания
            Vec3[] checkPoints = {
                    entity.getEyePosition(),
                    entity.position().add(0, entity.getBbHeight() * 0.5, 0), // центр
                    entity.position().add(0, entity.getBbHeight() * 0.1, 0), // низ
            };

            for (Vec3 checkPoint : checkPoints) {
                Vec3 toE = checkPoint.subtract(eye);
                double dot = toE.dot(dir);
                if (dot > 0.5 && dot < range) {
                    Vec3 proj = eye.add(dir.scale(dot));
                    // Проверяем с учётом ширины хитбокса сущности
                    double hitRadius = thickness + entity.getBbWidth() * 0.5;
                    if (proj.distanceTo(checkPoint) < hitRadius && dot < closestDot) {
                        closestDot = dot;
                        closest = entity;
                        break; // нашли попадание для этой сущности
                    }
                }
            }
        }
        return closest;
    }

    // Рисуем луч частицами
    private static void drawBeam(ServerLevel level, Vec3 start, Vec3 dir,
            double length, boolean hit) {
        for (double d = 0.5; d <= length; d += 0.4) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.PORTAL,
                    p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            // Акцент через каждые 2 блока
            if (d % 2.0 < 0.4) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
        // Вспышка в конце луча
        Vec3 end = start.add(dir.scale(length));
        level.sendParticles(hit ? ParticleTypes.FLASH : ParticleTypes.POOF,
                end.x, end.y, end.z, 1, 0, 0, 0, 0);
    }
}