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
        // Храним dimension key чтобы искать свиней в правильном мире
        public String levelKey;
        public int orbitTick = 0;

        public static final double ORBIT_RADIUS = 2.5;
        public static final double ORBIT_SPEED = 0.1;

        public OrbitData(UUID playerUUID) {
            this.playerUUID = playerUUID;
        }
    }

    private static final Map<UUID, OrbitData> ORBIT_MAP = new HashMap<>();

    // Ракеты теперь хранят и UUID уровня чтобы искать в правильном мире
    private static final Map<UUID, String> MISSILE_LEVEL_MAP = new HashMap<>();

    public static void startOrbit(ServerPlayer player, ServerLevel level) {
        UUID pid = player.getUUID();
        clearPlayer(pid, level);

        OrbitData data = new OrbitData(pid);
        data.levelKey = level.dimension().location().toString();

        for (int i = 0; i < 3; i++) {
            double angle = (i / 3.0) * Math.PI * 2;

            Pig pig = EntityType.PIG.create(level);
            if (pig == null) continue; // <-- был баг: new Pig(...) может не финализировать спавн

            pig.setNoAi(true);
            pig.setNoGravity(true);
            pig.setSilent(true);
            pig.setInvulnerable(true);
            pig.addTag("geo_orbiting");
            pig.getPersistentData().putString("owner_uuid", pid.toString());

            // Спавним рядом с игроком сразу, а не в 0,0,0
            double tx = player.getX() + OrbitData.ORBIT_RADIUS * Math.cos(angle);
            double ty = player.getY() + 1.2;
            double tz = player.getZ() + OrbitData.ORBIT_RADIUS * Math.sin(angle);
            pig.moveTo(tx, ty, tz, 0f, 0f);

            level.addFreshEntity(pig);
            data.orbitingPigs.add(pig.getUUID());
            data.angles.add(angle);
        }

        if (data.orbitingPigs.isEmpty()) {
            player.sendSystemMessage(
                Component.literal("GEO: Ошибка спавна свиней!").withStyle(ChatFormatting.RED));
            return;
        }

        ORBIT_MAP.put(pid, data);
        player.sendSystemMessage(
                Component.literal("GEO: Свиньи на орбите. Нажми ульту ещё раз для выстрела.")
                        .withStyle(ChatFormatting.GREEN));
    }

    public static void tick(ServerLevel level) {
        // 1. Ракеты
        tickMissiles(level);

        // 2. Орбиты
        Iterator<Map.Entry<UUID, OrbitData>> orbitIterator = ORBIT_MAP.entrySet().iterator();
        while (orbitIterator.hasNext()) {
            Map.Entry<UUID, OrbitData> entry = orbitIterator.next();
            OrbitData data = entry.getValue();

            // Проверяем что тикаем правильный уровень для этой орбиты
            String thisLevelKey = level.dimension().location().toString();
            if (!thisLevelKey.equals(data.levelKey)) continue;

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(data.playerUUID);

            if (player == null || !player.isAlive()) {
                // Чистим свиней перед удалением
                data.orbitingPigs.forEach(id -> {
                    Entity e = level.getEntity(id);
                    if (e != null) e.discard();
                });
                orbitIterator.remove();
                continue;
            }

            if (player.level() != level) continue;

            data.orbitTick++;

            // Двигаем орбитальных свиней
            for (int i = 0; i < data.orbitingPigs.size(); i++) {
                Entity ent = level.getEntity(data.orbitingPigs.get(i));
                if (ent instanceof Pig pig) {
                    if (pig.getTags().contains("geo_missile")) continue;

                    double angle = data.angles.get(i) + OrbitData.ORBIT_SPEED;
                    data.angles.set(i, angle);

                    double tx = player.getX() + OrbitData.ORBIT_RADIUS * Math.cos(angle);
                    double ty = player.getY() + 1.2;
                    double tz = player.getZ() + OrbitData.ORBIT_RADIUS * Math.sin(angle);
                    pig.teleportTo(tx, ty, tz);
                } else if (ent == null) {
                    // Свинья исчезла (убита игроком и т.п.) — убираем из списка
                    data.orbitingPigs.remove(i);
                    data.angles.remove(i);
                    i--;
                }
            }

            if (data.orbitingPigs.isEmpty()) {
                orbitIterator.remove();
            }
        }
    }

    private static void launchNextPig(OrbitData data, ServerPlayer player, ServerLevel level) {
        if (data.orbitingPigs.isEmpty()) return;

        UUID pigUUID = data.orbitingPigs.remove(0);
        data.angles.remove(0);

        Entity ent = level.getEntity(pigUUID);
        if (!(ent instanceof Pig pig)) {
            // Свинья не нашлась — пробуем следующую на следующем тике
            return;
        }

        pig.getTags().remove("geo_orbiting");
        pig.addTag("geo_missile");
        pig.setInvulnerable(false);
        pig.setNoAi(true);

        Vec3 look = player.getLookAngle().normalize();
        double speed = 1.2;

        pig.getPersistentData().putDouble("vx", look.x * speed);
        pig.getPersistentData().putDouble("vy", look.y * speed);
        pig.getPersistentData().putDouble("vz", look.z * speed);
        pig.getPersistentData().putDouble("startX", pig.getX());
        pig.getPersistentData().putDouble("startY", pig.getY());
        pig.getPersistentData().putDouble("startZ", pig.getZ());

        // Сохраняем в каком уровне летит ракета
        MISSILE_LEVEL_MAP.put(pigUUID, level.dimension().location().toString());

        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                pig.getX(), pig.getY(), pig.getZ(), 5, 0.1, 0.1, 0.1, 0.05);
    }

    public static boolean hasOrbit(ServerPlayer player) {
        OrbitData data = ORBIT_MAP.get(player.getUUID());
        return data != null && !data.orbitingPigs.isEmpty();
    }

    /**
     * Запускает одну орбитальную свинью при нажатии ульты.
     *
     * @return true если после запуска орбита полностью закончилась (можно ставить КД)
     */
    public static boolean launchFromUltPress(ServerPlayer player, ServerLevel level) {
        OrbitData data = ORBIT_MAP.get(player.getUUID());
        if (data == null || data.orbitingPigs.isEmpty()) {
            player.sendSystemMessage(
                    Component.literal("GEO: Нет свиней на орбите. Нажми ульту для призыва.")
                            .withStyle(ChatFormatting.YELLOW));
            return false;
        }

        launchNextPig(data, player, level);

        if (data.orbitingPigs.isEmpty()) {
            ORBIT_MAP.remove(player.getUUID());
            player.sendSystemMessage(
                    Component.literal("GEO: Все свиньи выпущены. Ульта ушла в КД.")
                            .withStyle(ChatFormatting.GOLD));
            return true;
        }

        player.sendSystemMessage(
                Component.literal("GEO: Свинья запущена! Осталось: " + data.orbitingPigs.size())
                        .withStyle(ChatFormatting.AQUA));
        return false;
    }

    private static void tickMissiles(ServerLevel level) {
        if (MISSILE_LEVEL_MAP.isEmpty()) return;

        String thisLevelKey = level.dimension().location().toString();

        Iterator<Map.Entry<UUID, String>> iterator = MISSILE_LEVEL_MAP.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, String> entry = iterator.next();
            UUID id = entry.getKey();
            String missileLevel = entry.getValue();

            // Ракета в другом мире — не трогаем
            if (!thisLevelKey.equals(missileLevel)) continue;

            Entity ent = level.getEntity(id);

            if (ent == null || !(ent instanceof Pig pig) || !pig.isAlive()) {
                iterator.remove();
                if (ent != null) ent.discard();
                continue;
            }

            double vx = pig.getPersistentData().getDouble("vx");
            double vy = pig.getPersistentData().getDouble("vy");
            double vz = pig.getPersistentData().getDouble("vz");

            if (vx == 0 && vy == 0 && vz == 0) {
                vx = pig.getLookAngle().x;
                vy = pig.getLookAngle().y;
                vz = pig.getLookAngle().z;
            }

            Vec3 nextPos = pig.position().add(vx, vy, vz);
            pig.teleportTo(nextPos.x, nextPos.y, nextPos.z);

            level.sendParticles(ParticleTypes.FLAME,
                    pig.getX(), pig.getY() + 0.5, pig.getZ(), 2, 0.02, 0.02, 0.02, 0.01);

            double startX = pig.getPersistentData().getDouble("startX");
            double startY = pig.getPersistentData().getDouble("startY");
            double startZ = pig.getPersistentData().getDouble("startZ");

            if (pig.position().distanceToSqr(startX, startY, startZ) > 625) {
                explodePig(pig, level);
                iterator.remove();
                continue;
            }

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

        level.explode(null, pos.x, pos.y, pos.z, 0.0f, false, ServerLevel.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);

        float damage = 22.0f;
        double radius = 6.0;
        String ownerUUIDStr = pig.getPersistentData().getString("owner_uuid");

        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class, pig.getBoundingBox().inflate(radius));

        for (LivingEntity target : targets) {
            if (target == pig) continue;
            if (target.getUUID().toString().equals(ownerUUIDStr)) continue;

            target.hurt(level.damageSources().explosion(null, null), damage);
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
                if (e != null) e.discard();
            });
        }
        // Чистим ракеты этого игрока
        MISSILE_LEVEL_MAP.entrySet().removeIf(entry -> {
            String levelKey = level.dimension().location().toString();
            if (!entry.getValue().equals(levelKey)) return false;
            Entity e = level.getEntity(entry.getKey());
            if (e != null && e.getPersistentData().getString("owner_uuid").equals(playerUUID.toString())) {
                e.discard();
                return true;
            }
            return false;
        });
    }
}
