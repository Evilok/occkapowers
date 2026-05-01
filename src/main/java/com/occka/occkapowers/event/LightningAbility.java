package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.ability.PowerType;
import com.occka.occkapowers.event.AbilityCommon;
import net.minecraft.world.entity.LivingEntity;

public final class LightningAbility {
    private LightningAbility() {
    }

    public static void activateShift(ServerPlayer player, ServerLevel level) {

        // Storm glide (лёгкий полёт на "грозовой тучке")
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

    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        // Ищем моба в конусе прицела — до 20 блоков, угол 30 градусов
        LivingEntity target = null;
        double minDist = Double.MAX_VALUE;

        for (LivingEntity entity : AbilityCommon.getNearbyEnemies(player, 20)) {
            Vec3 toEntity = entity.getEyePosition().subtract(eye).normalize();
            double dot = toEntity.dot(look); // 1.0 = прямо в прицеле, 0.0 = сбоку
            if (dot < 0.85)
                continue; // ~30 градусов от центра прицела
            double dist = entity.distanceTo(player);
            if (dist < minDist) {
                minDist = dist;
                target = entity;
            }
        }

        Vec3 strikePos;
        if (target != null) {
            strikePos = target.position();
        } else {
            // Нет мобов в прицеле — стреляем по прицелу как раньше
            Vec3 end = eye.add(look.scale(50));
            BlockHitResult hit = level.clip(
                    new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            strikePos = hit.getType() == HitResult.Type.MISS ? end : Vec3.atCenterOf(hit.getBlockPos());
        }

        net.minecraft.world.entity.LightningBolt bolt = new net.minecraft.world.entity.LightningBolt(
                EntityType.LIGHTNING_BOLT, level);
        bolt.moveTo(strikePos);
        bolt.setVisualOnly(false);
        level.addFreshEntity(bolt);

        for (int i = 0; i < 12; i++) {
            Vec3 p = eye.lerp(strikePos, (double) i / 11);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.2);
        }
        level.sendParticles(ParticleTypes.FLASH, strikePos.x, strikePos.y, strikePos.z, 1, 0, 0, 0, 0);
        player.sendSystemMessage(AbilityCommon.msg("Lightning Strike!", ChatFormatting.YELLOW));
    }

    public static void activateUlt(ServerPlayer player, ServerLevel level, double radius) {
        player.getPersistentData().putInt("occka_lightning_ult_ticks", 900); // 45 сек
        player.getPersistentData().putDouble("occka_lightning_ult_radius", radius);
        player.getPersistentData().putInt("occka_lightning_ult_count", 0);
        player.getPersistentData().putInt("occka_lightning_ult_max", 10 + new java.util.Random().nextInt(3)); // 10-12

        level.setWeatherParameters(0, 18000, true, true); // гроза на 15 минут

        for (int i = 0; i < 60; i++) {
            double a = Math.random() * Math.PI * 2, r = Math.random() * radius;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX() + r * Math.cos(a),
                    player.getY() + 20 + Math.random() * 5,
                    player.getZ() + r * Math.sin(a),
                    1, 0, 0, 0, 0.5);
        }
        player.sendSystemMessage(AbilityCommon.msg("LIGHTNING STORM!", ChatFormatting.YELLOW, ChatFormatting.BOLD));
    }

    public static void tickUlt(ServerPlayer player, ServerLevel level) {
        int ticks = player.getPersistentData().getInt("occka_lightning_ult_ticks");
        if (ticks <= 0)
            return;

        ticks--;
        player.getPersistentData().putInt("occka_lightning_ult_ticks", ticks);

        int count = player.getPersistentData().getInt("occka_lightning_ult_count");
        int max = player.getPersistentData().getInt("occka_lightning_ult_max");
        double radius = player.getPersistentData().getDouble("occka_lightning_ult_radius");

        int interval = 900 / max;
        if (count < max && ticks % interval == 0) {
            LivingEntity target = null;
            double minDist = Double.MAX_VALUE;
            for (LivingEntity e : AbilityCommon.getNearbyEnemies(player, radius)) {
                double d = e.distanceTo(player);
                if (d < minDist) {
                    minDist = d;
                    target = e;
                }
            }

            if (target != null) {
                net.minecraft.world.entity.LightningBolt bolt = new net.minecraft.world.entity.LightningBolt(
                        EntityType.LIGHTNING_BOLT, level);
                bolt.moveTo(target.position());
                bolt.setVisualOnly(false);
                level.addFreshEntity(bolt);
                player.getPersistentData().putInt("occka_lightning_ult_count", count + 1);
            }
        }

        if (ticks == 0) {
            level.setWeatherParameters(6000, 0, false, false);
            player.sendSystemMessage(AbilityCommon.msg("Storm ended.", ChatFormatting.YELLOW));
        }
    }
}
