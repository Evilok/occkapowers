package com.occka.occkapowers.event;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.ChatFormatting;
import java.util.*;

public class GeoOrbitHandler {

    public static class OrbitData {
        public final UUID playerUUID;
        public final List<UUID> orbitingPigs = new ArrayList<>();
        public final List<Double> angles = new ArrayList<>();
        public int orbitTick = 0;
        public int launchTimer = 0;

        public static final double ORBIT_RADIUS = 2.5;
        public static final double ORBIT_SPEED = 0.1; // Чуть быстрее вращение для драйва

        public OrbitData(UUID playerUUID) {
            this.playerUUID = playerUUID;
        }
    }

    private static final Map<UUID, OrbitData> ORBIT_MAP = new HashMap<>();
    private static final List<UUID> ACTIVE_MISSILES = new ArrayList<>();

    public static void startOrbit(ServerPlayer player, ServerLevel level) {
        UUID pid = player.getUUID();
        clearPlayer(pid, level);

        OrbitData data = new OrbitData(pid);

        for (int i = 0; i < 3; i++) {
            double angle = (i / 3.0) * Math.PI * 2;
            Pig pig = new Pig(EntityType.PIG, level);

            pig.setNoAi(true);
            pig.setNoGravity(true);
            pig.setInvisible(true);
            pig.setSilent(true);
            pig.setInvulnerable(true);
            pig.addTag("geo_orbiting");

            pig.getPersistentData().putString("owner_uuid", pid.toString());

            level.addFreshEntity(pig);
            data.orbitingPigs.add(pig.getUUID());
            data.angles.add(angle);
        }

        data.launchTimer = 30;
        ORBIT_MAP.put(pid, data);
        player.sendSystemMessage(
                Component.literal("GEO: Свиньи на орбите. Запуск пошел!").withStyle(ChatFormatting.GREEN));
    }

    public static void tick(ServerLevel level) {
        // 1. Сначала двигаем ракеты (тут проверка на нужный уровень уже встроена)
        tickMissiles(level);

        // 2. Орбиты
        Iterator<Map.Entry<UUID, OrbitData>> orbitIterator = ORBIT_MAP.entrySet().iterator();
        while (orbitIterator.hasNext()) {
            Map.Entry<UUID, OrbitData> entry = orbitIterator.next();
            OrbitData data = entry.getValue();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(data.playerUUID);

            if (player == null || !player.isAlive()) {
                orbitIterator.remove();
                continue;
            }

            // КРИТИЧЕСКИЙ ФИКС: Если игрок в Оверворлде, а мы сейчас тикаем Незер —
            // игнорим.
            // Это предотвратит кражу свиней "в никуда".
            if (player.level() != level)
                continue;

            data.orbitTick++;

            for (int i = 0; i < data.orbitingPigs.size(); i++) {
                Entity ent = level.getEntity(data.orbitingPigs.get(i));
                if (ent instanceof Pig pig) {
                    pig.setInvisible(true);
                    if (pig.getTags().contains("geo_missile"))
                        continue;

                    double angle = data.angles.get(i) + OrbitData.ORBIT_SPEED;
                    data.angles.set(i, angle);

                    double tx = player.getX() + OrbitData.ORBIT_RADIUS * Math.cos(angle);
                    double ty = player.getY() + 1.2;
                    double tz = player.getZ() + OrbitData.ORBIT_RADIUS * Math.sin(angle);
                    pig.teleportTo(tx, ty, tz);

                }
            }

            if (!data.orbitingPigs.isEmpty()) {
                data.launchTimer--;
                if (data.launchTimer <= 0) {
                    // Теперь запуск произойдет только в "родном" мире свиньи
                    launchNextPig(data, player, level);
                    data.launchTimer = 20;
                }
            } else {
                orbitIterator.remove();
            }
        }
    }

    private static void launchNextPig(OrbitData data, ServerPlayer player, ServerLevel level) {
        if (data.orbitingPigs.isEmpty())
            return;

        // Извлекаем первую свинью из списка орбиты
        UUID pigUUID = data.orbitingPigs.remove(0);
        data.angles.remove(0);

        Entity ent = level.getEntity(pigUUID);
        if (ent instanceof Pig pig) {
            System.out.println("DEBUG: Свинья " + pigUUID + " ОТОРВАЛАСЬ ОТ ОРБИТЫ!");

            // Чистим старые теги, ставим новые
            pig.getTags().remove("geo_orbiting");
            pig.addTag("geo_missile");

            pig.setInvulnerable(false);
            pig.setNoAi(true); // Еще раз на всякий случай

            // Расчет вектора строго в момент клика
            Vec3 look = player.getLookAngle().normalize();
            double speed = 1.2; // Скорость за тик

            pig.getPersistentData().putDouble("vx", look.x * speed);
            pig.getPersistentData().putDouble("vy", look.y * speed);
            pig.getPersistentData().putDouble("vz", look.z * speed);
            // Точка старта для контроля дистанции (15 блоков)
            pig.getPersistentData().putDouble("startX", pig.getX());
            pig.getPersistentData().putDouble("startY", pig.getY());
            pig.getPersistentData().putDouble("startZ", pig.getZ());

            ACTIVE_MISSILES.add(pigUUID);

            level.sendParticles(ParticleTypes.LARGE_SMOKE, pig.getX(), pig.getY(), pig.getZ(), 5, 0.1, 0.1, 0.1, 0.05);
        }
    }

    private static void tickMissiles(ServerLevel level) {
        if (ACTIVE_MISSILES.isEmpty())
            return;

        Iterator<UUID> iterator = ACTIVE_MISSILES.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Entity ent = level.getEntity(id); // Ищем свинью в ТЕКУЩЕМ уровне

            // КРИТИЧЕСКИЙ ФИКС: Если свиньи нет в ЭТОМ мире, просто идем дальше, НЕ
            // удаляем!
            if (ent == null)
                continue;

            if (!(ent instanceof Pig pig) || !pig.isAlive()) {
                iterator.remove();
                continue;
            }

            // Читаем вектор
            double vx = pig.getPersistentData().getDouble("vx");
            double vy = pig.getPersistentData().getDouble("vy");
            double vz = pig.getPersistentData().getDouble("vz");

            // Если вектор нулевой (ошибка записи) - даем пинок вперед
            if (vx == 0 && vy == 0 && vz == 0) {
                vx = pig.getLookAngle().x;
                vy = pig.getLookAngle().y;
                vz = pig.getLookAngle().z;
            }

            // Жёсткое перемещение
            Vec3 nextPos = pig.position().add(vx, vy, vz);
            pig.teleportTo(nextPos.x, nextPos.y, nextPos.z);

            // Визуал
            level.sendParticles(ParticleTypes.FLAME, pig.getX(), pig.getY() + 0.5, pig.getZ(), 2, 0.02, 0.02, 0.02,
                    0.01);

            // Проверка на взрыв (дистанция 25 блоков)
            double startX = pig.getPersistentData().getDouble("startX");
            double startY = pig.getPersistentData().getDouble("startY");
            double startZ = pig.getPersistentData().getDouble("startZ");
            if (pig.position().distanceToSqr(startX, startY, startZ) > 625) { // 25*25
                explodePig(pig, level);
                iterator.remove();
                continue;
            }

            // Взрыв при контакте с кем-то кроме хозяина
            AABB hitBox = pig.getBoundingBox().inflate(0.6);
            String ownerStr = pig.getPersistentData().getString("owner_uuid");
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, hitBox,
                    e -> e != pig && !e.getUUID().toString().equals(ownerStr));

            if (!targets.isEmpty() || pig.horizontalCollision || pig.verticalCollision) {
                explodePig(pig, level);
                iterator.remove();
            }
        }
    }

    private static void explodePig(Pig pig, ServerLevel level) {
        Vec3 pos = pig.position();

        // Визуальный и звуковой бабах
        level.explode(null, pos.x, pos.y, pos.z, 0.0f, false, ServerLevel.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);

        // Урон в радиусе 3 блоков (как ты и просил)
        float damage = 22.0f; // 10 сердечек
        double radius = 6.0;

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, pig.getBoundingBox().inflate(radius));
        String ownerUUIDStr = pig.getPersistentData().getString("owner_uuid");

        for (LivingEntity target : targets) {
            if (target == pig)
                continue;
            if (target.getUUID().toString().equals(ownerUUIDStr))
                continue;

            target.hurt(level.damageSources().explosion(null, null), damage);

            // Откидывание (мадж-стайл)
            Vec3 kb = target.position().subtract(pos).normalize().scale(1.5);
            target.setDeltaMovement(kb.x, 0.5, kb.z);
            target.hurtMarked = true;
        }
        pig.discard();
    }

    public static void clearPlayer(UUID playerUUID, ServerLevel level) {
        OrbitData data = ORBIT_MAP.remove(playerUUID);
        if (data != null) {
            data.orbitingPigs.forEach(id -> {
                Entity e = level.getEntity(id);
                if (e != null)
                    e.discard();
            });
        }
    }
}