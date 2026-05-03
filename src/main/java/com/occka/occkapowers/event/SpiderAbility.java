package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import com.occka.occkapowers.ability.PlayerPowerData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class SpiderAbility {
    private SpiderAbility() {
    }

    // Список UUID врагов ожидающих паутину {uuid_most, uuid_least, deadline}
    private static final String NBT_WEB_TARGETS = "occka_spider_web_targets";
    // Тики запрета полёта — хранится у самой цели
    private static final String NBT_NO_FLIGHT_TICKS = "occka_spider_no_flight_ticks";

    public static void activateShift(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        if (data.getShiftCooldown() > 0)
            return;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().normalize().scale(30.0));
        BlockHitResult hit = level
                .clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        Vec3 anchor = hit.getLocation();
        Vec3 toAnchor = anchor.subtract(player.position());
        Vec3 current = player.getDeltaMovement();
        Vec3 boost = toAnchor.normalize().scale(1.8);
        Vec3 swing = current.scale(0.6).add(boost);

        player.setDeltaMovement(swing);
        player.hurtMarked = true;
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SPEED, 30, 1));

        for (int i = 0; i <= 20; i++) {
            double t = i / 20.0;
            Vec3 point = eye.lerp(anchor, t);
            level.sendParticles(new DustParticleOptions(new Vector3f(1f, 1f, 1f), 0.8f),
                    point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        Entity target = findTarget(player, level, 30.0);
        if (target == null) {
            player.sendSystemMessage(AbilityCommon.msg("Miss!", ChatFormatting.GRAY));
            return;
        }
        Vec3 dir = player.position().add(0, 1.0, 0).subtract(target.position());
        double dist = dir.length();
        if (dist < 0.001) return;

        dir = dir.normalize();
        double speed = Math.min(3.2, 0.25 + dist * 0.18);
        Vec3 motion = new Vec3(dir.x * speed, Math.max(dir.y * speed, 0.25), dir.z * speed);

        target.setDeltaMovement(motion);
        target.hurtMarked = true;

        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().normalize().scale(30));
        for (int i = 0; i <= 60; i++) {
            double t = i / 60.0;
            Vec3 pos = start.lerp(end, t);
            level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }

        if (target instanceof LivingEntity) {
            player.getPersistentData().putUUID("occka_spider_pull_target", target.getUUID());
            player.getPersistentData().putInt("occka_spider_pull_ticks", 40);
        }
    }

    /**
     * ULT: сбрасывает всех вниз, выключает полёт/левитацию.
     * Паутина ставится под каждым врагом когда тот приземляется (или через 5с).
     */
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        double radius = 15.0;
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SPEED, 100, 2));

        // Дедлайн 5 секунд (100 тиков) — последний шанс поставить паутину
        long deadline = level.getGameTime() + 100;

        ListTag webTargets = player.getPersistentData().getList(NBT_WEB_TARGETS, Tag.TAG_COMPOUND);

        for (LivingEntity enemy : AbilityCommon.getNearbyEnemies(player, radius)) {
            // Выключаем полёт и левитацию
            stripFlight(enemy);
            enemy.removeEffect(MobEffects.LEVITATION);
            enemy.removeEffect(MobEffects.SLOW_FALLING);
            enemy.setNoGravity(false);

            // Принудительный рывок вниз
            Vec3 cur = enemy.getDeltaMovement();
            enemy.setDeltaMovement(cur.x * 0.3, -2.2, cur.z * 0.3);
            enemy.hurtMarked = true;

            // Замедление
            enemy.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 100, 127));
            enemy.addEffect(AbilityCommon.fx(MobEffects.JUMP, 100, 127));

            // Запрет полёта у самой цели
            enemy.getPersistentData().putInt(NBT_NO_FLIGHT_TICKS, 100);

            // Сохраняем UUID — паутину поставим когда цель приземлится
            CompoundTag entry = new CompoundTag();
            entry.putLong("uuid_most", enemy.getUUID().getMostSignificantBits());
            entry.putLong("uuid_least", enemy.getUUID().getLeastSignificantBits());
            entry.putLong("deadline", deadline);
            webTargets.add(entry);

            level.sendParticles(ParticleTypes.CLOUD,
                    enemy.getX(), enemy.getY() + 1, enemy.getZ(),
                    8, 0.5, 0.5, 0.5, 0.04);
        }

        player.getPersistentData().put(NBT_WEB_TARGETS, webTargets);

        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 1, player.getZ(),
                80, radius * 0.4, 1.2, radius * 0.4, 0.05);

        player.sendSystemMessage(AbilityCommon.msg(
                "Web Trap! Flight disabled, webs on landing...",
                ChatFormatting.WHITE, ChatFormatting.BOLD));
    }

    // ===== TICK =====

    public static void tick(ServerPlayer player, ServerLevel level) {
        player.resetFallDistance();
        player.fallDistance = 0f;
        player.setNoGravity(false);

        tickPullDamage(player, level);
        tickWebCleanup(player, level);
        tickWebTargets(player, level);
        tickNoFlightEntities(player, level);

        boolean touchingWall = isTouchingWall(player, level);

        if (isInWeb(player)) {
            Vec3 m = player.getDeltaMovement();
            player.setDeltaMovement(m.x * 3.0, Math.max(m.y, 0.15), m.z * 3.0);
            player.fallDistance = 0;
            player.resetFallDistance();
            player.hurtMarked = true;
            player.hasImpulse = true;
            player.makeStuckInBlock(Blocks.AIR.defaultBlockState(), new Vec3(1, 1, 1));
        }

        if (touchingWall && !player.onGround()) {
            Vec3 m = player.getDeltaMovement();
            player.setDeltaMovement(m.x * 0.95, Math.max(m.y, 0.1), m.z * 0.95);
            player.fallDistance = 0;
            player.hurtMarked = true;
        }

        if (touchingWall && player.getDeltaMovement().y < 0) {
            player.setDeltaMovement(player.getDeltaMovement().x, 0, player.getDeltaMovement().z);
            player.fallDistance = 0;
        }
    }

    /**
     * Следит за целями ульты по UUID.
     * Когда цель onGround() ИЛИ вышел дедлайн — ставит паутину под её ТЕКУЩЕЙ позицией.
     */
    private static void tickWebTargets(ServerPlayer player, ServerLevel level) {
        ListTag list = player.getPersistentData().getList(NBT_WEB_TARGETS, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return;

        long now = level.getGameTime();
        List<CompoundTag> keep = new ArrayList<>();

        for (Tag t : list) {
            if (!(t instanceof CompoundTag entry)) continue;

            UUID targetUUID = new UUID(entry.getLong("uuid_most"), entry.getLong("uuid_least"));
            long deadline = entry.getLong("deadline");

            // Ищем сущность в широкой зоне
            LivingEntity target = null;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(50),
                    e -> e.getUUID().equals(targetUUID) && e.isAlive())) {
                target = e;
                break;
            }

            // Цель мертва / не найдена — убираем
            if (target == null) continue;

            boolean landed = target.onGround();
            boolean timedOut = now >= deadline;

            if (landed || timedOut) {
                // Ставим паутину под ТЕКУЩЕЙ позицией цели
                spawnWebsUnderEntity(target, level, now, player);
                // Не добавляем в keep — удалено
            } else {
                keep.add(entry);
            }
        }

        ListTag rebuilt = new ListTag();
        keep.forEach(rebuilt::add);
        player.getPersistentData().put(NBT_WEB_TARGETS, rebuilt);
    }

    /** Ставит паутину 3x3 под текущей позицией сущности. */
    private static void spawnWebsUnderEntity(LivingEntity entity, ServerLevel level, long now, ServerPlayer owner) {
        BlockPos center = entity.blockPosition();

        // Находим реальный пол под ногами (на случай если чуть в воздухе)
        BlockPos floor = center;
        for (int i = 0; i <= 4; i++) {
            BlockPos check = center.below(i);
            if (!level.getBlockState(check).isAir()) {
                floor = check.above();
                break;
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = floor.offset(dx, 0, dz);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
                    placeTimedWeb(level, pos, now + 100, owner); // убрать через 5с
                    level.sendParticles(new DustParticleOptions(new Vector3f(1f, 1f, 1f), 0.7f),
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            3, 0.2, 0.2, 0.2, 0);
                }
            }
        }
    }

    /** Тикает запрет полёта у всех помеченных сущностей (100 тиков = 5с). */
    private static void tickNoFlightEntities(ServerPlayer player, ServerLevel level) {
        level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(50),
                e -> e.getPersistentData().getInt(NBT_NO_FLIGHT_TICKS) > 0)
        .forEach(e -> {
            int ticks = e.getPersistentData().getInt(NBT_NO_FLIGHT_TICKS) - 1;
            e.getPersistentData().putInt(NBT_NO_FLIGHT_TICKS, ticks);
            stripFlight(e);
            e.removeEffect(MobEffects.LEVITATION);
            e.setNoGravity(false);
            if (ticks <= 0) {
                e.getPersistentData().remove(NBT_NO_FLIGHT_TICKS);
            }
        });
    }

    private static void stripFlight(LivingEntity entity) {
        if (entity instanceof ServerPlayer sp) {
            if (sp.getAbilities().flying) {
                sp.getAbilities().flying = false;
                sp.onUpdateAbilities();
            }
            if (sp.getAbilities().mayfly && !sp.isCreative() && !sp.isSpectator()) {
                sp.getAbilities().mayfly = false;
                sp.onUpdateAbilities();
            }
        }
        entity.removeEffect(MobEffects.LEVITATION);
        entity.setNoGravity(false);
    }

    private static boolean isInWeb(ServerPlayer player) {
        AABB box = player.getBoundingBox();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
            for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
                for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxX); z++) {
                    pos.set(x, y, z);
                    if (player.level().getBlockState(pos).is(Blocks.COBWEB)) return true;
                }
            }
        }
        return false;
    }

    private static void tickPullDamage(ServerPlayer player, ServerLevel level) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.hasUUID("occka_spider_pull_target")) return;

        int ticks = tag.getInt("occka_spider_pull_ticks");
        if (ticks <= 0) {
            tag.remove("occka_spider_pull_target");
            tag.remove("occka_spider_pull_ticks");
            return;
        }
        tag.putInt("occka_spider_pull_ticks", ticks - 1);

        UUID id = tag.getUUID("occka_spider_pull_target");
        Entity entity = level.getEntity(id);
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            tag.remove("occka_spider_pull_target");
            tag.remove("occka_spider_pull_ticks");
            return;
        }

        if (living.onGround() && living.distanceTo(player) <= 2.7f) {
            living.hurt(player.damageSources().playerAttack(player), 8.0f);
            tag.remove("occka_spider_pull_target");
            tag.remove("occka_spider_pull_ticks");
        }
    }

    private static void placeTimedWeb(ServerLevel level, BlockPos pos, long expiresAt, ServerPlayer owner) {
        ListTag list = owner.getPersistentData().getList("occka_spider_webs", Tag.TAG_COMPOUND);
        CompoundTag entry = new CompoundTag();
        entry.putLong("p", pos.asLong());
        entry.putLong("e", expiresAt);
        list.add(entry);
        owner.getPersistentData().put("occka_spider_webs", list);
    }

    private static void tickWebCleanup(ServerPlayer player, ServerLevel level) {
        ListTag list = player.getPersistentData().getList("occka_spider_webs", Tag.TAG_COMPOUND);
        if (list.isEmpty()) return;

        long now = level.getGameTime();
        List<CompoundTag> keep = new ArrayList<>();
        for (Tag t : list) {
            if (!(t instanceof CompoundTag entry)) continue;
            long expires = entry.getLong("e");
            BlockPos pos = BlockPos.of(entry.getLong("p"));
            if (expires <= now) {
                if (level.getBlockState(pos).is(Blocks.COBWEB)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            } else {
                keep.add(entry);
            }
        }

        keep.sort(Comparator.comparingLong(c -> c.getLong("e")));
        ListTag rebuilt = new ListTag();
        keep.forEach(rebuilt::add);
        player.getPersistentData().put("occka_spider_webs", rebuilt);
    }

    private static Entity findTarget(ServerPlayer player, ServerLevel level, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(3.0);
        List<Entity> candidates = level.getEntities(player, searchBox,
                e -> e != player && e.isPickable() && e.isAlive());

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : candidates) {
            Vec3[] checkPoints = {
                    entity.getEyePosition(),
                    entity.position().add(0, entity.getBbHeight() * 0.5, 0),
                    entity.position().add(0, entity.getBbHeight() * 0.15, 0),
            };
            double hitRadius = 1.4 + entity.getBbWidth() * 0.5;

            for (Vec3 point : checkPoints) {
                Vec3 toPoint = point.subtract(eye);
                double dot = toPoint.dot(look);
                if (dot < 0.5 || dot > range) continue;
                Vec3 proj = eye.add(look.scale(dot));
                if (proj.distanceTo(point) < hitRadius && dot < closestDist) {
                    closestDist = dot;
                    closest = entity;
                    break;
                }
            }
        }

        if (closest != null) {
            Vec3 end = eye.add(look.scale(closestDist));
            BlockHitResult blockHit = level.clip(new ClipContext(eye, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (blockHit.getType() == HitResult.Type.BLOCK
                    && blockHit.getLocation().distanceToSqr(eye) < closestDist * closestDist) {
                return null;
            }
        }
        return closest;
    }

    private static boolean isTouchingWall(ServerPlayer player, ServerLevel level) {
        Vec3 pos = player.position();
        double r = 0.35;
        return level.getBlockState(BlockPos.containing(pos.x + r, pos.y, pos.z)).isSolid()
                || level.getBlockState(BlockPos.containing(pos.x - r, pos.y, pos.z)).isSolid()
                || level.getBlockState(BlockPos.containing(pos.x, pos.y, pos.z + r)).isSolid()
                || level.getBlockState(BlockPos.containing(pos.x, pos.y, pos.z - r)).isSolid();
    }
}