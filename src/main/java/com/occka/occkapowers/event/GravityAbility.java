package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class GravityAbility {
    private GravityAbility() {}

    // SHIFT (held): левитация на месте + частицы
    public static void activateShift(ServerPlayer player, ServerLevel level) {
        player.addEffect(AbilityCommon.fx(MobEffects.LEVITATION, 25, 0));
        player.setDeltaMovement(player.getDeltaMovement().x, 0, player.getDeltaMovement().z);
        for (int i = 0; i < 15; i++) {
            double angle = Math.random() * Math.PI * 2;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    player.getX() + 1.5 * Math.cos(angle),
                    player.getY() + Math.random() * 2,
                    player.getZ() + 1.5 * Math.sin(angle),
                    1, 0, 0, 0, 0.02);
        }
    }

    // ABILITY: воронка гравитации — притягивает врагов к точке прицела
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(30));
        BlockHitResult hit = level.clip(
                new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 center = hit.getType() == HitResult.Type.MISS ? end : Vec3.atCenterOf(hit.getBlockPos());

        for (LivingEntity entity : AbilityCommon.getNearbyEnemies(player, 12)) {
            Vec3 pull = center.subtract(entity.position()).normalize().scale(1.8);
            entity.setDeltaMovement(entity.getDeltaMovement().add(pull.x * 1.5, pull.y * 0.5, pull.z * 1.5));
            entity.hurtMarked = true;
        }

        for (int i = 0; i < 80; i++) {
            double a = Math.random() * Math.PI * 2, r = Math.random() * 8;
            level.sendParticles(ParticleTypes.PORTAL,
                    center.x + r * Math.cos(a), center.y + Math.random() * 3, center.z + r * Math.sin(a),
                    1, 0, 0, 0, 0.2);
        }
        for (int i = 0; i < 20; i++)
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 1, center.z, 5, 1, 1, 1, 0.1);
        player.sendSystemMessage(AbilityCommon.msg("Gravity Vortex!", ChatFormatting.DARK_GRAY));
    }

    // ULT: инверсия гравитации — всех подбрасывает вверх, через 3с резко бьёт вниз
    // Таймер падения тикается в AbilityEventHandler.tickGravityUlt()
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(50);
        List<LivingEntity> entities = player.level().getEntitiesOfClass(
                LivingEntity.class, box, e -> e != player);

        for (LivingEntity entity : entities) {
            entity.addEffect(AbilityCommon.fx(MobEffects.LEVITATION, 60, 0));
            entity.setDeltaMovement(entity.getDeltaMovement().add(0, 2.0, 0));
            entity.hurtMarked = true;
        }

        // Таймер: через 60 тиков (3 сек) всех швырнёт вниз (tickGravityUlt в handler)
        player.getPersistentData().putInt("occka_gravity_ult_ticks", 60);
        player.getPersistentData().putDouble("occka_gravity_ult_radius", 50.0);

        for (int i = 0; i < 100; i++) {
            double a = Math.random() * Math.PI * 2,
                    p = (Math.random() - 0.5) * Math.PI,
                    r = Math.random() * 50;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    player.getX() + r * Math.cos(a) * Math.cos(p),
                    player.getY() + 2 + r * Math.abs(Math.sin(p)),
                    player.getZ() + r * Math.sin(a) * Math.cos(p),
                    1, 0, 0, 0, 0.1);
        }
        player.sendSystemMessage(
                AbilityCommon.msg("GRAVITY INVERSION!", ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
    }
}
