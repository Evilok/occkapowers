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

    // NBT-ключ: тики запрета полёта
    private static final String NBT_NO_FLIGHT_TICKS = "occka_gravity_no_flight_ticks";

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

    /**
     * ULT: инверсия гравитации.
     * Выключает полёт/левитацию у всех, подбрасывает вверх,
     * через 3с резко бьёт вниз + накладывает запрет полёта на 5с.
     */
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(50);
        List<LivingEntity> entities = player.level().getEntitiesOfClass(
                LivingEntity.class, box, e -> e != player);

        for (LivingEntity entity : entities) {
            // --- Выключаем полёт и левитацию ДО броска ---
            stripFlight(entity);

            // Бросаем вверх
            entity.addEffect(AbilityCommon.fx(MobEffects.LEVITATION, 60, 0));
            entity.setDeltaMovement(entity.getDeltaMovement().add(0, 2.0, 0));
            entity.hurtMarked = true;

            // Помечаем: после приземления (через 3с) выключить полёт на 5с
            entity.getPersistentData().putInt(NBT_NO_FLIGHT_TICKS, 0); // будет выставлен в tickGravityUlt
        }

        // Таймер: через 60 тиков (3 сек) всех швырнёт вниз
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

    /**
     * Вызывается из AbilityEventHandler.tickGravityUlt() когда таймер = 0.
     * Применяется к каждой цели: убираем levitation, бросаем вниз,
     * накладываем запрет полёта на 5 секунд (100 тиков), замедление.
     */
    public static void onGravityUltCrash(LivingEntity entity, ServerLevel level) {
        // Снимаем levitation и slow_falling
        entity.removeEffect(MobEffects.LEVITATION);
        entity.removeEffect(MobEffects.SLOW_FALLING);
        entity.setNoGravity(false);

        // Выключаем полёт
        stripFlight(entity);

        // Резкий бросок вниз
        entity.setDeltaMovement(
                entity.getDeltaMovement().x * 0.3,
                -3.5,
                entity.getDeltaMovement().z * 0.3);
        entity.hurtMarked = true;

        // Замедление
        entity.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));

        // Запрет полёта на 5 секунд (100 тиков) — тикается отдельно
        entity.getPersistentData().putInt(NBT_NO_FLIGHT_TICKS, 100);
    }

    /**
     * Тикает запрет полёта у всех помеченных сущностей в радиусе.
     * Вызывается каждый тик из AbilityEventHandler.
     */
    public static void tickNoFlightDebuff(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(60);
        level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.getPersistentData().getInt(NBT_NO_FLIGHT_TICKS) > 0)
        .forEach(e -> {
            int ticks = e.getPersistentData().getInt(NBT_NO_FLIGHT_TICKS) - 1;
            e.getPersistentData().putInt(NBT_NO_FLIGHT_TICKS, ticks);

            // Каждый тик принудительно выключаем полёт
            stripFlight(e);
            e.removeEffect(MobEffects.LEVITATION);
            e.setNoGravity(false);

            if (ticks <= 0) {
                e.getPersistentData().remove(NBT_NO_FLIGHT_TICKS);
            }
        });
    }

    // Вспомогательный метод: выключает полёт у игрока или снимает NoGravity у моба
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
}