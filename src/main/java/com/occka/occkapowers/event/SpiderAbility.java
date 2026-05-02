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
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import com.occka.occkapowers.ability.PlayerPowerData;

import net.minecraftforge.event.entity.living.LivingFallEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class SpiderAbility {
    private SpiderAbility() {
    }

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

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        Entity target = findTarget(player, level, 30.0);
        if (target == null) {
            player.sendSystemMessage(AbilityCommon.msg("Miss!", ChatFormatting.GRAY));
            return;
        }
        // направление к игроку
        Vec3 dir = player.position().add(0, 1.0, 0)
                .subtract(target.position());

        double dist = dir.length();
        if (dist < 0.001)
            return;

        dir = dir.normalize();

        // скорость зависит от дистанции (чем дальше — тем сильнее рывок)
        double speed = Math.min(3.2, 0.25 + dist * 0.18);
        // double speed = 4.5;

        // ПРЯМАЯ УСТАНОВКА СКОРОСТИ (без "add", это ключ)
        Vec3 motion = dir.scale(speed);

        // небольшой контроль Y чтобы не ломалось об землю
        motion = new Vec3(
                motion.x,
                Math.max(motion.y, 0.25),
                motion.z);

        target.setDeltaMovement(motion);
        target.hurtMarked = true;

        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().normalize().scale(30));

        for (int i = 0; i <= 60; i++) { // больше точек = плотнее линия
            double t = i / 60.0;

            Vec3 pos = start.lerp(end, t);

            level.sendParticles(
                    ParticleTypes.CLOUD, // стабильный партикл
                    pos.x, pos.y, pos.z,
                    1, 0, 0, 0, 0);
        }

        if (target instanceof LivingEntity) {
            CompoundTag tag = player.getPersistentData();
            tag.putUUID("occka_spider_pull_target", target.getUUID());
            tag.putInt("occka_spider_pull_ticks", 40);
        }
    }

    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        double radius = 15.0;
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SPEED, 100, 2));

        for (LivingEntity enemy : AbilityCommon.getNearbyEnemies(player, radius)) {
            // СБРОС ВНИЗ
            Vec3 cur = enemy.getDeltaMovement();

            // жёсткий удар вниз
            enemy.setDeltaMovement(
                    cur.x * 0.3,
                    -2.2, // сила падения (можно крутить)
                    cur.z * 0.3);

            enemy.hurtMarked = true;

            // убираем полётные эффекты
            enemy.removeEffect(MobEffects.LEVITATION);
            enemy.removeEffect(MobEffects.SLOW_FALLING);
            enemy.setNoGravity(false);
            enemy.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 100, 127));
            enemy.addEffect(AbilityCommon.fx(MobEffects.JUMP, 100, 127));

            placeTimedWeb(level, enemy.blockPosition(), level.getGameTime() + 100, player);
            placeTimedWeb(level, enemy.blockPosition().north(), level.getGameTime() + 100, player);
            placeTimedWeb(level, enemy.blockPosition().south(), level.getGameTime() + 100, player);
            placeTimedWeb(level, enemy.blockPosition().east(), level.getGameTime() + 100, player);
            placeTimedWeb(level, enemy.blockPosition().west(), level.getGameTime() + 100, player);
            placeTimedWeb(level, enemy.blockPosition().above(), level.getGameTime() + 100, player);
        }

        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 1, player.getZ(),
                80, radius * 0.4, 1.2, radius * 0.4, 0.05);
    }

    private static boolean isInWeb(ServerPlayer player) {
        AABB box = player.getBoundingBox();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
            for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
                for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {

                    pos.set(x, y, z);

                    if (player.level().getBlockState(pos).is(Blocks.COBWEB)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void tick(ServerPlayer player, ServerLevel level) {
        player.resetFallDistance();
        player.fallDistance = 0f;

        player.setNoGravity(false);

        tickPullDamage(player, level);
        tickWebCleanup(player, level);

        boolean touchingWall = isTouchingWall(player, level);

        if (isInWeb(player)) {

            Vec3 m = player.getDeltaMovement();

            // полностью игнорируем “stuck in web”
            player.setDeltaMovement(
                    m.x * 3.0,
                    Math.max(m.y, 0.15),
                    m.z * 3.0);

            // КЛЮЧ: сброс ванильного замедления
            player.fallDistance = 0;
            player.resetFallDistance();

            player.hurtMarked = true;
            player.hasImpulse = true;

            // ВАЖНО: принудительно сбрасываем stuck эффект
            player.makeStuckInBlock(Blocks.AIR.defaultBlockState(), new Vec3(1, 1, 1));
        }

        // ===== ЛАЗАНИЕ =====
        if (touchingWall && !player.onGround()) {
            Vec3 m = player.getDeltaMovement();

            player.setDeltaMovement(
                    m.x * 0.95,
                    Math.max(m.y, 0.1),
                    m.z * 0.95);

            player.fallDistance = 0;
            player.hurtMarked = true;
        }

        // ===== ПРИЛИПАНИЕ =====
        if (touchingWall && player.getDeltaMovement().y < 0) {
            player.setDeltaMovement(player.getDeltaMovement().x, 0, player.getDeltaMovement().z);
            player.fallDistance = 0;
        }
    }

    private static void tickPullDamage(ServerPlayer player, ServerLevel level) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.hasUUID("occka_spider_pull_target")) {
            return;
        }

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

        boolean landedNearPlayer = living.onGround() && living.distanceTo(player) <= 2.7f;
        if (landedNearPlayer) {
            living.hurt(player.damageSources().playerAttack(player), 8.0f);
            tag.remove("occka_spider_pull_target");
            tag.remove("occka_spider_pull_ticks");
        }
    }

    private static void placeTimedWeb(ServerLevel level, BlockPos pos, long expiresAt, ServerPlayer owner) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) {
            return;
        }
        level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);

        ListTag list = owner.getPersistentData().getList("occka_spider_webs", Tag.TAG_COMPOUND);
        CompoundTag entry = new CompoundTag();
        entry.putLong("p", pos.asLong());
        entry.putLong("e", expiresAt);
        list.add(entry);
        owner.getPersistentData().put("occka_spider_webs", list);
    }

    private static void tickWebCleanup(ServerPlayer player, ServerLevel level) {
        ListTag list = player.getPersistentData().getList("occka_spider_webs", Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            return;
        }

        long now = level.getGameTime();
        List<CompoundTag> keep = new ArrayList<>();
        for (Tag t : list) {
            if (!(t instanceof CompoundTag entry)) {
                continue;
            }
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

        // Широкий AABB для первичного отбора кандидатов
        AABB searchBox = player.getBoundingBox()
                .expandTowards(look.scale(range))
                .inflate(3.0); // увеличен с 1.6 до 3.0

        // Собираем всех кандидатов
        List<Entity> candidates = level.getEntities(
                player,
                searchBox,
                e -> e != player && e.isPickable() && e.isAlive());

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : candidates) {
            // Проверяем несколько точек хитбокса — глаза, центр, низ
            // Это даёт "толстый" луч без явного цилиндра
            Vec3[] checkPoints = {
                    entity.getEyePosition(),
                    entity.position().add(0, entity.getBbHeight() * 0.5, 0),
                    entity.position().add(0, entity.getBbHeight() * 0.15, 0),
            };

            // Радиус попадания: фиксированный хитбокс + половина ширины сущности
            // 1.4 — достаточно широко чтобы попадать не целясь идеально
            double hitRadius = 1.4 + entity.getBbWidth() * 0.5;

            for (Vec3 point : checkPoints) {
                Vec3 toPoint = point.subtract(eye);
                double dot = toPoint.dot(look);

                // Точка должна быть впереди и в пределах дальности
                if (dot < 0.5 || dot > range)
                    continue;

                // Ближайшая точка луча к checkPoint
                Vec3 proj = eye.add(look.scale(dot));
                double lateralDist = proj.distanceTo(point);

                if (lateralDist < hitRadius && dot < closestDist) {
                    closestDist = dot;
                    closest = entity;
                    break; // нашли попадание для этой сущности
                }
            }
        }

        // Проверяем что между игроком и целью нет блоков
        if (closest != null) {
            Vec3 end = eye.add(look.scale(closestDist));
            BlockHitResult blockHit = level.clip(
                    new ClipContext(eye, end,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, player));
            // Если блок ближе цели — цель за стеной, не считаем
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                double blockDist = blockHit.getLocation().distanceToSqr(eye);
                double entityDist = closestDist * closestDist;
                if (blockDist < entityDist) {
                    return null;
                }
            }
        }

        return closest;
    }

    private static boolean isTouchingWall(ServerPlayer player, ServerLevel level) {

        Vec3 pos = player.position();

        double r = 0.35; // радиус проверки

        return level.getBlockState(BlockPos.containing(pos.x + r, pos.y, pos.z)).isSolid()
                || level.getBlockState(BlockPos.containing(pos.x - r, pos.y, pos.z)).isSolid()
                || level.getBlockState(BlockPos.containing(pos.x, pos.y, pos.z + r)).isSolid()
                || level.getBlockState(BlockPos.containing(pos.x, pos.y, pos.z - r)).isSolid();
    }
}