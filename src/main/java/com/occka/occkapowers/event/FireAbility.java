package com.occka.occkapowers.event;

import org.jetbrains.annotations.NotNull;

import com.occka.occkapowers.ability.PlayerPowerData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;

public final class FireAbility {

    private FireAbility() {
    }

    // ===== SHIFT: toggle огненной формы =====

    /**
     * Первое нажатие — включает форму.
     * Второе нажатие — выключает.
     */
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        boolean active = player.getPersistentData().getBoolean("occka_fire_form_active");
        if (!active) {
            enableFireForm(player, level);
        } else {
            disableFireForm(player, level);
        }

    }

    public static void meltIceAndSnow(ServerPlayer player, ServerLevel level) {
        BlockPos center = player.blockPosition();
        int radius = 8; // чуть больше радиус для абилки
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 5; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius)
                        continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.PACKED_ICE)) {
                        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                        level.sendParticles(ParticleTypes.SMOKE,
                                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                                2, 0.2, 0.1, 0.2, 0.02);
                    } else if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }
        level.sendParticles(ParticleTypes.LAVA,
                player.getX(), player.getY() + 1, player.getZ(),
                15, 1.5, 0.5, 1.5, 0.2);
        player.sendSystemMessage(AbilityCommon.msg("Ice Melt!", ChatFormatting.RED));
    }

    private static void enableFireForm(ServerPlayer player, ServerLevel level) {
        player.getPersistentData().putBoolean("occka_fire_form_active", true);

        // Визуал включения: кольцо огня вокруг игрока
        for (int i = 0; i < 20; i++) {
            double angle = (i / 20.0) * Math.PI * 2;
            level.sendParticles(ParticleTypes.FLAME,
                    player.getX() + 1.2 * Math.cos(angle),
                    player.getY() + 1.0,
                    player.getZ() + 1.2 * Math.sin(angle),
                    1, 0.05, 0.1, 0.05, 0.02);
        }
        level.sendParticles(ParticleTypes.LAVA,
                player.getX(), player.getY() + 1, player.getZ(),
                10, 0.5, 0.5, 0.5, 0.1);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                player.getX(), player.getY() + 0.5, player.getZ(),
                5, 0.3, 0.3, 0.3, 0.02);

        player.sendSystemMessage(AbilityCommon.msg(
                "Fire Form: ON — Press [R] again to deactivate",
                ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private static void disableFireForm(ServerPlayer player, ServerLevel level) {
        player.getPersistentData().putBoolean("occka_fire_form_active", false);

        // Снимаем полёт
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        // Гасим визуальный огонь
        player.clearFire();

        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                player.getX(), player.getY() + 1, player.getZ(),
                15, 0.5, 0.5, 0.5, 0.05);

        player.sendSystemMessage(AbilityCommon.msg(
                "Fire Form: OFF", ChatFormatting.GRAY));
    }

    /**
     * Вызывается каждый тик из AbilityEventHandler пока игрок имеет класс FIRE.
     * Поддерживает визуальный огонь и плавит лёд/снег вокруг.
     */
    public static void tickFireForm(ServerPlayer player, ServerLevel level) {
        if (!player.getPersistentData().getBoolean("occka_fire_form_active"))
            return;
        // Плавное падение всегда в форме
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 15, 0, false, false));

        // Визуальный огонь на игроке (FIRE_RESISTANCE из пассивки — урона нет)
        player.setRemainingFireTicks(40);

    }

    /**
     * Сброс формы при смерти/смене класса/выходе из игры.
     */
    public static void clearFireForm(Player player) {
        if (!player.getPersistentData().getBoolean("occka_fire_form_active"))
            return;

        player.getPersistentData().putBoolean("occka_fire_form_active", false);

        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        player.clearFire();
    }

    // ===== ABILITY: Firestorm =====

    public static void activateShift(ServerPlayer player, ServerLevel level, PlayerPowerData data) {

        // КД проверка
        if (data.getShiftCooldown() > 0)
            return;

        Vec3 playerPos = player.position();

        for (LivingEntity entity : AbilityCommon.getNearbyEnemies(player, 10)) {

            Vec3 dir = entity.position().subtract(playerPos).normalize();
            double dist = entity.distanceTo(player);

            double force = 1.8 * (1.0 - dist / 10.0) + 0.4;

            entity.setDeltaMovement(
                    dir.x * force,
                    0.45 + (force * 0.3),
                    dir.z * force);

            entity.hurtMarked = true;
            entity.setSecondsOnFire(8);
            entity.hurt(player.damageSources().onFire(), 4);

            level.sendParticles(
                    ParticleTypes.FLAME,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    12, 0.3, 0.5, 0.3, 0.08);
        }
        meltIceAndSnow(player, level);
        // кольцо огня
        for (int deg = 0; deg < 360; deg += 6) {
            for (double r = 0.5; r <= 10; r += 1.5) {
                double x = player.getX() + r * Math.cos(Math.toRadians(deg));
                double z = player.getZ() + r * Math.sin(Math.toRadians(deg));

                level.sendParticles(
                        ParticleTypes.FLAME,
                        x, player.getY() + 0.3, z,
                        1, 0, 0.1, 0, 0.04);
            }
        }

        level.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                player.getX(), player.getY(), player.getZ(),
                2, 0.5, 0, 0.5, 0.05);

        level.sendParticles(
                ParticleTypes.LAVA,
                player.getX(), player.getY() + 0.5, player.getZ(),
                20, 1.5, 0.5, 1.5, 0.2);

        player.sendSystemMessage(
                AbilityCommon.msg("Firestorm!", ChatFormatting.RED));

        data.setShiftCooldown(400); // 20 секунд
    }
    // ===== ULT: Fire Ult (без изменений) =====

    public static void createFireRing(ServerPlayer player, ServerLevel level, int radius) {
        double cx = player.getX(), cy = player.getY(), cz = player.getZ();
        for (int deg = 0; deg < 360; deg += 8) {
            double rad = Math.toRadians(deg);
            double x = cx + radius * Math.cos(rad), z = cz + radius * Math.sin(rad);
            BlockPos pos = new BlockPos((int) x, (int) cy, (int) z);
            while (pos.getY() > level.getMinBuildHeight() && level.getBlockState(pos).isAir())
                pos = pos.below();
            pos = pos.above();
            if (level.getBlockState(pos).isAir())
                level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
            level.sendParticles(ParticleTypes.FLAME, x, cy + 0.5, z, 4, 0.1, 0.3, 0.1, 0.03);
            level.sendParticles(ParticleTypes.LAVA, x, cy + 0.2, z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, cy + 1, z, 2, 0.1, 0.3, 0.1, 0.01);
        }
        player.sendSystemMessage(AbilityCommon.msg("Fire Ring!", ChatFormatting.RED));
    }

    public static void startUlt(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        if (!player.onGround() || player.isFallFlying() || player.getAbilities().flying) {
            player.sendSystemMessage(AbilityCommon.msg(
                    "Can't use ability while flying!", ChatFormatting.RED));
            return;
        }
        
        data.setFireUltOrigin(player.getX(), player.getY(), player.getZ());
        player.teleportTo(player.getX(), player.getY() + 14, player.getZ());
        AttributeInstance gravity = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity != null) {
            data.setFireUltOldGravity(gravity.getBaseValue());
            gravity.setBaseValue(0.0);
        }
        data.setFireUltActive(true);
        data.setFireUltTicks(300);
        data.setFireUltFireballCooldown(0);
        for (int i = 0; i < 30; i++)
            level.sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() - i * 0.5, player.getZ(),
                    5, 1, 0.2, 1, 0.05);
        player.sendSystemMessage(AbilityCommon.msg(
                "FIRE ULT! Shoot fireballs with LMB for 15s!",
                ChatFormatting.RED, ChatFormatting.BOLD));
    }

    public static void ultShoot(ServerPlayer player, PlayerPowerData data) {
        if (!data.isFireUltActive())
            return;
        ServerLevel level = (ServerLevel) player.level();
        Vec3 dir = player.getLookAngle().normalize();
        LargeFireball fb = new LargeFireball(EntityType.FIREBALL, level);
        fb.setOwner(player);
        fb.setPos(player.getEyePosition());
        fb.setDeltaMovement(dir.scale(2.5));
        fb.addTag("ult_fireball_" + player.getUUID().toString());
        level.addFreshEntity(fb);
        level.sendParticles(ParticleTypes.LAVA,
                player.getX(), player.getY(), player.getZ(),
                8, 0.3, 0.3, 0.3, 0.1);
    }

    public static void endUlt(ServerPlayer player, PlayerPowerData data) {
        player.teleportTo(
                data.getFireUltOriginX(), data.getFireUltOriginY(), data.getFireUltOriginZ());
        AttributeInstance gravity = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity != null) {
            gravity.setBaseValue(data.getFireUltOldGravity());
        }
        if (player.level() instanceof ServerLevel level) {
            String tag = "ult_fireball_" + player.getUUID().toString();
            level.getAllEntities().forEach(entity -> {
                if (entity.getTags().contains(tag))
                    entity.discard();
            });
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    player.getX(), player.getY() + 1, player.getZ(),
                    20, 1, 1, 1, 0.05);
        }
    }

}
