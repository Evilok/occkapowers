package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SuperforceAbility {

    private static MobEffectInstance fx(net.minecraft.world.effect.MobEffect eff, int dur, int amp) {
        return new MobEffectInstance(eff, dur, amp, false, false);
    }

    // Called every tick from AbilityEventHandler for superforce players
    public static void tickUlt(ServerPlayer player, ServerLevel level) {
        var data = player.getPersistentData();

        // Ult active: keep elytra flight going
        if (data.getBoolean("occka_sf_ult_active")) {
            int ticks = data.getInt("occka_sf_ult_ticks");
            data.putInt("occka_sf_ult_ticks", ticks + 1);

            // Start elytra flight on tick 2 (gives time to gain height)
            if (ticks == 2) {
                player.startFallFlying();
            }
            if (player.isFallFlying()) {
                Vec3 look = player.getLookAngle();
                Vec3 vel = player.getDeltaMovement();
                player.setDeltaMovement(
                        vel.x * 0.85 + look.x * 0.07,
                        vel.y * 0.85 + look.y * 0.07,
                        vel.z * 0.85 + look.z * 0.07);
                player.hurtMarked = true;
                player.resetFallDistance();
            }
        }

        // Camera shake ticks
        int shakeTicks = data.getInt("occka_shake_ticks");
        if (shakeTicks > 0) {
            data.putInt("occka_shake_ticks", shakeTicks - 1);
            // Rapidly rotate player view to simulate shake
            if (shakeTicks % 2 == 0) {
                float shakeAmt = 15f * (shakeTicks / 10f);
                player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * shakeAmt);
                player.setXRot(player.getXRot() + (player.getRandom().nextFloat() - 0.5f) * shakeAmt * 0.5f);
                player.teleportTo(player.getX(), player.getY(), player.getZ()); // force update
            }
        }
    }

    // ===== PASSIVE: creative flight always on =====
    public static void applyPassive(ServerPlayer player) {
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = true;
            player.getAbilities().setFlyingSpeed(0.1f);
            player.onUpdateAbilities();
        }
    }

    // ===== SHIFT: shockwave punch - shake camera + launch enemies =====
    public static void activateShift(ServerPlayer player, ServerLevel level) {
        Vec3 pos = player.position();

        // Camera shake for the player himself (nausea = screen wobble)
        player.addEffect(fx(MobEffects.CONFUSION, 30, 5)); // short intense nausea

        // Punch the ground - crater particles
        BlockPos ground = player.blockPosition().below();
        for (int deg = 0; deg < 360; deg += 8) {
            for (double r = 0.3; r <= 5; r += 0.8) {
                double x = pos.x + r * Math.cos(Math.toRadians(deg));
                double z = pos.z + r * Math.sin(Math.toRadians(deg));
                BlockPos bp = BlockPos.containing(x, pos.y - 0.5, z);
                var state = level.getBlockState(bp).isAir()
                        ? level.getBlockState(bp.below())
                        : level.getBlockState(bp);
                if (!state.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            x, pos.y + 0.1, z, 3, 0, 0.3, 0, 0.15);
                }
            }
        }

        // Launch + shake all enemies in radius 12
        List<LivingEntity> enemies = getNearby(player, 12);
        for (LivingEntity entity : enemies) {
            Vec3 dir = entity.position().subtract(pos).normalize();
            double dist = entity.distanceTo(player);
            double force = 1.2 * (1.0 - dist / 12.0) + 0.3;

            entity.setDeltaMovement(dir.x * force, 0.7 + force * 0.4, dir.z * force);
            entity.hurtMarked = true;
            entity.hurt(player.damageSources().playerAttack(player), 6);

            // Camera shake via nausea on hit entities
            if (entity instanceof ServerPlayer target) {
                target.addEffect(fx(MobEffects.CONFUSION, 40, 5));
            }

            level.sendParticles(ParticleTypes.CRIT,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    10, 0.3, 0.3, 0.3, 0.2);
        }

        // Impact shockwave ring
        for (int deg = 0; deg < 360; deg += 5) {
            double r = 5;
            level.sendParticles(ParticleTypes.EXPLOSION,
                    pos.x + r * Math.cos(Math.toRadians(deg)), pos.y + 0.1,
                    pos.z + r * Math.sin(Math.toRadians(deg)),
                    1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z, 2, 0.3, 0, 0.3, 0.05);

        player.sendSystemMessage(Component.literal("GROUND SLAM!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    // ===== ABILITY: superhero punch - single target, massive knockback =====
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        // Find entity in crosshair up to 6 blocks
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();

        LivingEntity target = null;
        double minDist = Double.MAX_VALUE;
        AABB searchBox = player.getBoundingBox().inflate(6);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player)) {
            // Check if entity is roughly in look direction
            Vec3 toEntity = entity.position().subtract(eye);
            double dot = toEntity.normalize().dot(dir);
            double dist = entity.distanceTo(player);
            if (dot > 0.7 && dist < minDist) { // ~45 degree cone
                minDist = dist;
                target = entity;
            }
        }

        if (target != null) {
            // Mega punch
            Vec3 punchDir = dir.normalize();
            target.setDeltaMovement(
                    punchDir.x * 3.5,
                    0.8,
                    punchDir.z * 3.5);
            target.hurtMarked = true;
            target.hurt(player.damageSources().playerAttack(player), 20);

            // Camera shake on target
            if (target instanceof ServerPlayer tp) {
                tp.addEffect(fx(MobEffects.CONFUSION, 60, 8));
            }

            // Impact particles on target
            for (int i = 0; i < 40; i++) {
                double angle = Math.random() * Math.PI * 2;
                level.sendParticles(ParticleTypes.CRIT,
                        target.getX() + Math.cos(angle) * 0.5,
                        target.getY() + 1 + Math.random(),
                        target.getZ() + Math.sin(angle) * 0.5,
                        1, 0, 0, 0, 0.3);
            }
            level.sendParticles(ParticleTypes.EXPLOSION,
                    target.getX(), target.getY() + 1, target.getZ(),
                    5, 0.5, 0.5, 0.5, 0.1);
            // Speed lines from player to target
            for (double d = 0.5; d < minDist; d += 0.5) {
                Vec3 p = eye.add(dir.scale(d));
                level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
            player.sendSystemMessage(Component.literal("SUPER PUNCH!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            // No target - just a haymaker into the air
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    eye.x + dir.x * 3, eye.y + dir.y * 3, eye.z + dir.z * 3,
                    5, 0.3, 0.3, 0.3, 0.1);
            player.sendSystemMessage(Component.literal("Miss!")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // ===== ULT: METEOR CRASH - fly up then crash down =====
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        // Launch upward and start elytra flight
        player.setDeltaMovement(0, 2.5, 0);
        player.hurtMarked = true;
        player.resetFallDistance();

        // Start elytra flight after a tiny delay (1 tick) via flag
        player.getPersistentData().putBoolean("occka_sf_ult_active", true);
        player.getPersistentData().putInt("occka_sf_ult_ticks", 0);
        // Mark as "waiting to land" for crash execution
        player.getPersistentData().putInt("occka_sf_crash_ticks", -1); // -1 = waiting for land

        player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 300, 4));

        // Launch particles
        for (int i = 0; i < 30; i++) {
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + (Math.random() - 0.5),
                    player.getY(),
                    player.getZ() + (Math.random() - 0.5),
                    1, 0.3, -0.05, 0.3, 0.05);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                player.getX(), player.getY(), player.getZ(), 2, 0, 0, 0, 0.05);

        player.sendSystemMessage(Component.literal("METEOR CRASH - FLY AND DIVE!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    // Called from tick handler when crash timer fires
    public static void executeMeteorCrash(ServerPlayer player, ServerLevel level) {
        Vec3 pos = player.position();

        // Crater particles - ground impact
        for (int deg = 0; deg < 360; deg += 3) {
            for (double r = 0.5; r <= 15; r += 1.8) {
                double x = pos.x + r * Math.cos(Math.toRadians(deg));
                double z = pos.z + r * Math.sin(Math.toRadians(deg));
                BlockPos bp = BlockPos.containing(x, pos.y - 0.5, z);
                var state = level.getBlockState(bp).isAir()
                        ? level.getBlockState(bp.below())
                        : level.getBlockState(bp);
                if (!state.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            x, pos.y + 0.2, z, 3, 0, 0.5, 0, 0.25);
                }
                if (r < 6) {
                    level.sendParticles(ParticleTypes.EXPLOSION,
                            x, pos.y + 0.1, z, 1, 0, 0, 0, 0);
                }
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z, 8, 2, 0.5, 2, 0.1);
        level.sendParticles(ParticleTypes.FLASH,
                pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);

        // Launch + shake all enemies in radius 15
        List<LivingEntity> enemies = getNearby(player, 15);
        for (LivingEntity entity : enemies) {
            Vec3 dir = entity.position().subtract(pos);
            double dist = dir.length();
            dir = dir.normalize();

            // Launch upward ~10 blocks
            entity.setDeltaMovement(
                    dir.x * 1.2,
                    1.8 + (1.0 - dist / 15.0) * 0.8, // ~10 blocks up
                    dir.z * 1.2);
            entity.hurtMarked = true;
            entity.hurt(player.damageSources().playerAttack(player),
                    (float) (15 * (1 - dist / 15.0)));

            // Camera shake: apply nausea + rapid teleport trick
            if (entity instanceof ServerPlayer target) {
                shakeCameraPlayer(target, level);
            }

            level.sendParticles(ParticleTypes.CRIT,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    15, 0.4, 0.4, 0.4, 0.25);
        }

        // Shake player's own camera
        player.addEffect(fx(MobEffects.CONFUSION, 30, 5));

        // Announce
        for (Player p : level.getEntitiesOfClass(Player.class,
                player.getBoundingBox().inflate(20), x -> true)) {
            ((ServerPlayer) p).sendSystemMessage(
                    Component.literal("METEOR CRASH!")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    // Camera shake via micro-teleport + rotation change
    private static void shakeCameraPlayer(ServerPlayer target, ServerLevel level) {
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 8, false, false));
        // Additional shake: move player view suddenly via setYRot
        // Schedule 5 rapid tiny teleports over next 10 ticks via nbt flag
        target.getPersistentData().putInt("occka_shake_ticks", 10);
    }

    private static List<LivingEntity> getNearby(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && !(e instanceof Player p && p.isAlliedTo(player)));
    }
}