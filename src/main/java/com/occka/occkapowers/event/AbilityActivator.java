package com.occka.occkapowers.event;

import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.ability.PowerType;
import com.occka.occkapowers.network.NetworkHandler;
import com.occka.occkapowers.network.PacketSyncPowerData;
import com.occka.occkapowers.registry.ModCapabilities;
import com.occka.occkapowers.unlock.UnlockHelper;

import com.occka.occkapowers.event.GeoOrbitHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.common.ForgeMod;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.List;

public class AbilityActivator {

    // No-particle effect helper
    private static MobEffectInstance fx(net.minecraft.world.effect.MobEffect eff, int dur, int amp) {
        return new MobEffectInstance(eff, dur, amp, false, false);
    }

    // Chat message using ChatFormatting (avoids section encoding issues)
    private static Component msg(String text, ChatFormatting... fmt) {
        var style = net.minecraft.network.chat.Style.EMPTY;
        for (ChatFormatting f : fmt)
            style = style.applyFormat(f);
        return Component.literal(text).withStyle(style);
    }

    public static void activate(ServerPlayer player, int slot) {
        player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
            PowerType type = data.getPowerType();
            if (type == PowerType.NONE) {
                player.sendSystemMessage(msg("You have no class assigned!", ChatFormatting.RED));
                return;
            }
            switch (slot) {
                case 0 -> activateShift(player, data, type);
                case 1 -> activateAbility(player, data, type);
                case 2 -> activateUlt(player, data, type);
            }
            syncToClient(player, data);
        });
    }

    // Called every 20 ticks (1s) while shift key held
    public static void activateShiftHeld(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
            PowerType type = data.getPowerType();
            if (type == PowerType.NONE)
                return;
            activateShift(player, data, type);
        });
    }

    public static void syncToClient(ServerPlayer player, PlayerPowerData data) {
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketSyncPowerData(data));
    }

    // ===== SHIFT (continuous, called every 20t while held) =====
    private static void activateShift(ServerPlayer player, PlayerPowerData data, PowerType type) {
        if (!(player.level() instanceof ServerLevel level))
            return;

        switch (type) {
            case FIRE -> {
                // Levitation while shift held (level 1 = gentle lift)
                player.addEffect(fx(MobEffects.LEVITATION, 25, 1));
                player.resetFallDistance();
                // Fire particles around player
                for (int i = 0; i < 10; i++) {
                    double angle = (i / 10.0) * Math.PI * 2;
                    double r = 1.2 + Math.random() * 0.8;
                    level.sendParticles(ParticleTypes.FLAME,
                            player.getX() + r * Math.cos(angle),
                            player.getY() + 0.3 + Math.random(),
                            player.getZ() + r * Math.sin(angle),
                            1, 0.04, 0.08, 0.04, 0.015);
                }
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        player.getX(), player.getY(), player.getZ(),
                        2, 0.3, 0.2, 0.3, 0.005);
            }
            case CHAOS -> ChaosAbility.activateShift(player, level);
            case SUPERFORCE -> SuperforceAbility.activateAbility(player, level); // punch (no cd)
            case AIR -> {
                // Levitate + cloud particles under feet
                player.addEffect(fx(MobEffects.LEVITATION, 25, 3));
                player.addEffect(fx(MobEffects.SLOW_FALLING, 25, 0));
                for (int i = 0; i < 12; i++) {
                    level.sendParticles(ParticleTypes.CLOUD,
                            player.getX() + (Math.random() - 0.5) * 0.5,
                            player.getY() - 0.5,
                            player.getZ() + (Math.random() - 0.5) * 0.5,
                            5, 0.5, 0.1, 0.5, 0.01);
                }
            }
            case WATER -> {
                // Healing aura
                AABB box = player.getBoundingBox().inflate(3);
                player.level().getEntitiesOfClass(LivingEntity.class, box, e -> true)
                        .forEach(e -> e.addEffect(fx(MobEffects.REGENERATION, 25, 2)));
                for (int i = 0; i < 12; i++) {
                    level.sendParticles(ParticleTypes.BUBBLE_POP,
                            player.getX() + (Math.random() - 0.5) * 6,
                            player.getY() + Math.random() * 3,
                            player.getZ() + (Math.random() - 0.5) * 6,
                            2, 0, 0.05, 0, 0.02);
                }
            }
            case ICE -> {
                // Blizzard aura
                getNearbyEnemies(player, 5).forEach(e -> {
                    e.hurt(player.damageSources().playerAttack(player), 0.5f);
                    e.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
                });
                for (int i = 0; i < 25; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double r = Math.random() * 5;
                    level.sendParticles(ParticleTypes.SNOWFLAKE,
                            player.getX() + r * Math.cos(angle), player.getY() + Math.random() * 3,
                            player.getZ() + r * Math.sin(angle), 1, (Math.random() - 0.5) * 0.2, 0.03,
                            (Math.random() - 0.5) * 0.2, 0);
                }
            }
            case LIGHTNING -> {
                // Speed burst + sparks
                player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 250, 6));
                for (int i = 0; i < 20; i++) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            player.getX() + (Math.random() - 0.5) * 1.5, player.getY() + Math.random() * 2,
                            player.getZ() + (Math.random() - 0.5) * 1.5, 1, 0, 0, 0, 0.3);
                }
                level.sendParticles(ParticleTypes.CRIT,
                        player.getX(), player.getY() + 1, player.getZ(), 5, 0.3, 0.5, 0.3, 0.2);
            }
            case LASER -> {
                // Cyclops-like visor focus: highlights targets and chips them with a thin beam
                player.addEffect(fx(MobEffects.NIGHT_VISION, 40, 0));
                player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 25, 0));
                getNearbyEnemies(player, 35).forEach(e -> e.addEffect(fx(MobEffects.GLOWING, 35, 0)));
                fireLaserBeam(player, level, 22, 6f, 1.35, false);

                // Scanning laser line particles from eyes
                Vec3 eye = player.getEyePosition();
                Vec3 look = player.getLookAngle();
                for (int i = 1; i <= 22; i++) {
                    Vec3 p = eye.add(look.scale(i));
                    level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.03, 0.03, 0.03, 0.005);
                }
            }
            case GEO -> {
                // Stone armor effect
                player.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 25, 2));
                player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 25, 1));
                for (int i = 0; i < 12; i++) {
                    double angle = (i / 12.0) * Math.PI * 2;
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                            player.getX() + 1.3 * Math.cos(angle), player.getY() + 0.5 + Math.random(),
                            player.getZ() + 1.3 * Math.sin(angle), 2, 0, 0, 0, 0);
                }
            }
            case VOID -> {
                // Invis + night vision + void particles
                player.addEffect(fx(MobEffects.NIGHT_VISION, 25, 0));
                player.addEffect(fx(MobEffects.INVISIBILITY, 25, 0));
                for (int i = 0; i < 15; i++) {
                    level.sendParticles(ParticleTypes.PORTAL,
                            player.getX() + (Math.random() - 0.5) * 2, player.getY() + Math.random() * 2.5,
                            player.getZ() + (Math.random() - 0.5) * 2, 1, 0, 0, 0, 0.05);
                }
            }
            case LIGHT -> {
                // Bone meal nearby crops + saturation for players
                BlockPos center = player.blockPosition();
                for (int dx = -5; dx <= 5; dx++)
                    for (int dz = -5; dz <= 5; dz++) {
                        BlockPos pos = center.offset(dx, 0, dz);
                        var state = level.getBlockState(pos);
                        if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop) {
                            var boneMeal = net.minecraft.world.item.BoneMealItem.applyBonemeal(
                                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL),
                                    level, pos, player);
                            if (boneMeal)
                                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1,
                                        pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
                        }
                    }
                AABB box2 = player.getBoundingBox().inflate(5);
                player.level().getEntitiesOfClass(Player.class, box2, p -> true)
                        .forEach(p -> p.addEffect(fx(MobEffects.SATURATION, 25, 1)));
                level.sendParticles(ParticleTypes.END_ROD,
                        player.getX(), player.getY() + 1, player.getZ(), 15, 1.5, 1.5, 1.5, 0.05);
            }
            case GRAVITY -> {
                // Hover in place when held
                player.addEffect(fx(MobEffects.LEVITATION, 25, 0));
                player.setDeltaMovement(player.getDeltaMovement().x, 0, player.getDeltaMovement().z);
                for (int i = 0; i < 15; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                            player.getX() + 1.5 * Math.cos(angle), player.getY() + Math.random() * 2,
                            player.getZ() + 1.5 * Math.sin(angle), 1, 0, 0, 0, 0.02);
                }
            }
            case ECHO -> {
                // One-time use with cooldown (30s) - handled in activateShift
                if (data.getShiftCooldown() > 0)
                    return;
                spawnEchoClone(player, level, data);
            }
        }
    }

    // ===== ABILITY =====
    private static void activateAbility(ServerPlayer player, PlayerPowerData data, PowerType type) {
        if (!data.isAbilityUnlocked()) {
            // Try to unlock
            if (UnlockHelper.tryConsumeAbility(player, type)) {
                data.setAbilityUnlocked(true);
                player.sendSystemMessage(msg("Ability unlocked!", ChatFormatting.GREEN, ChatFormatting.BOLD));
            } else {
                player.sendSystemMessage(
                        msg("Need to unlock ability! Cost: " + type.getAbilityUnlockHint(), ChatFormatting.YELLOW));
            }
            syncToClient(player, data);
            return;
        }
        if (data.getAbilityCooldown() > 0) {
            player.sendSystemMessage(
                    msg("Ability on cooldown: " + String.format("%.1f", data.getAbilityCooldown() / 20f) + "s",
                            ChatFormatting.YELLOW));
            return;
        }
        if (!(player.level() instanceof ServerLevel level))
            return;

        switch (type) {
            case FIRE -> {
                // Knockback + ignite all in radius 10
                Vec3 playerPos = player.position();
                for (LivingEntity entity : getNearbyEnemies(player, 10)) {
                    Vec3 dir = entity.position().subtract(playerPos).normalize();
                    double dist = entity.distanceTo(player);
                    double force = 1.8 * (1.0 - dist / 10.0) + 0.4; // stronger near center
                    entity.setDeltaMovement(
                            dir.x * force,
                            0.45 + (force * 0.3),
                            dir.z * force);
                    entity.hurtMarked = true;
                    entity.setSecondsOnFire(8);
                    entity.hurt(player.damageSources().onFire(), 4);
                    // Spark on each enemy
                    level.sendParticles(ParticleTypes.FLAME,
                            entity.getX(), entity.getY() + 1, entity.getZ(),
                            12, 0.3, 0.5, 0.3, 0.08);
                }
                // Central blast - ring of fire particles expanding outward
                for (int deg = 0; deg < 360; deg += 6) {
                    for (double r = 0.5; r <= 10; r += 1.5) {
                        double x = player.getX() + r * Math.cos(Math.toRadians(deg));
                        double z = player.getZ() + r * Math.sin(Math.toRadians(deg));
                        level.sendParticles(ParticleTypes.FLAME,
                                x, player.getY() + 0.3, z, 1, 0, 0.1, 0, 0.04);
                    }
                }
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        player.getX(), player.getY(), player.getZ(), 2, 0.5, 0, 0.5, 0.05);
                level.sendParticles(ParticleTypes.LAVA,
                        player.getX(), player.getY() + 0.5, player.getZ(),
                        20, 1.5, 0.5, 1.5, 0.2);
                player.sendSystemMessage(msg("Firestorm!", ChatFormatting.RED));
            }
            case AIR -> dashForward(player, level, 15);
            case SUPERFORCE -> SuperforceAbility.activateShift(player, level); // ground slam (cd)
            case WATER -> spawnAquaticMobs(player, level);
            case ICE -> cageNearestEnemy(player, level);
            case LIGHTNING -> strikeLightningAtLookBlock(player, level);
            case CHAOS -> ChaosAbility.activateAbility(player, level);
            case LASER -> fireLaserBeam(player, level, 28, 22f, 1.9, true);
            case GEO -> geoShockwave(player, level, 10);
            case VOID -> voidBlind(player, level, 10);
            case LIGHT -> {
                // Glow + haste for self and nearby
                AABB box = player.getBoundingBox().inflate(15);
                player.level().getEntitiesOfClass(LivingEntity.class, box, e -> true).forEach(e -> {
                    e.addEffect(fx(MobEffects.GLOWING, 200, 0));
                    if (e instanceof Player)
                        e.addEffect(fx(MobEffects.DIG_SPEED, 200, 2));
                });
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 60, 7, 3, 7,
                        0.15);
                level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0,
                        0);
            }
            case GRAVITY -> gravityVortex(player, level);
            case ECHO -> echoSwap(player, level);
        }
        data.setAbilityCooldown(type.getAbilityCooldown());
        syncToClient(player, data);
    }

    // ===== ULT =====
    private static void activateUlt(ServerPlayer player, PlayerPowerData data, PowerType type) {
        if (!data.isUltUnlocked()) {
            if (UnlockHelper.tryConsumeUlt(player, type)) {
                data.setUltUnlocked(true);
                player.sendSystemMessage(msg("Ultimate unlocked!", ChatFormatting.GOLD, ChatFormatting.BOLD));
            } else {
                player.sendSystemMessage(
                        msg("Need to unlock ultimate! Cost: " + type.getUltUnlockHint(), ChatFormatting.YELLOW));
            }
            syncToClient(player, data);
            return;
        }
        if (data.getUltCooldown() > 0) {
            player.sendSystemMessage(msg("Ult on cooldown: " + String.format("%.1f", data.getUltCooldown() / 20f) + "s",
                    ChatFormatting.RED));
            return;
        }
        if (!(player.level() instanceof ServerLevel level))
            return;

        switch (type) {
            case FIRE -> startFireUlt(player, level, data);
            case AIR -> {
                levitateEnemies(player, 15, 21, 40);
                for (int i = 0; i < 80; i++) {
                    double a = Math.random() * Math.PI * 2, p = (Math.random() - 0.5) * Math.PI, r = Math.random() * 20;
                    level.sendParticles(ParticleTypes.CLOUD, player.getX() + r * Math.cos(a) * Math.cos(p),
                            player.getY() + 2 + r * Math.sin(p), player.getZ() + r * Math.sin(a) * Math.cos(p), 1, 0, 0,
                            0, 0.05);
                }
                // Give slow_falling after use
                player.addEffect(fx(MobEffects.SLOW_FALLING, 100, 0));
                player.sendSystemMessage(msg("AIR BLAST!", ChatFormatting.AQUA, ChatFormatting.BOLD));
            }
            case WATER -> {
                player.addEffect(fx(MobEffects.ABSORPTION, 1200, 17));
                level.setWeatherParameters(0, 6000, true, false);
                for (int i = 0; i < 60; i++)
                    level.sendParticles(ParticleTypes.DRIPPING_WATER,
                            player.getX() + (Math.random() - 0.5) * 20, player.getY() + 10 + Math.random() * 5,
                            player.getZ() + (Math.random() - 0.5) * 20, 1, 0, -0.3, 0, 0.5);
                player.sendSystemMessage(msg("Tide of Power!", ChatFormatting.AQUA));
            }
            case ICE -> iceUltFreeze(player, level);
            case LIGHTNING -> lightningStrikeAll(player, level, 40);
            case LASER -> startLaserUlt(player, level, data);
            case SUPERFORCE -> SuperforceAbility.activateUlt(player, level);
            case GEO -> {
                // Спавним орбиту и сразу запускаем таймер — кд ставится здесь же
                GeoOrbitHandler.startOrbit(player, level);
                // Кд ставится сразу (не ждём запуска всех свиней)
                data.setUltCooldown(type.getUltCooldown());
                syncToClient(player, data);
                return; // return чтобы не дублировать setUltCooldown в конце метода
            }
            case CHAOS -> ChaosAbility.activateUlt(player, level);
            case VOID -> voidUlt(player, level, 20);
            case LIGHT -> lightUlt(player, level);
            case GRAVITY -> gravityUlt(player, level);
            case ECHO -> echoUlt(player, level);
        }
        data.setUltCooldown(type.getUltCooldown());
        syncToClient(player, data);
    }

    // ===== IMPLEMENTATIONS =====

    private static void createFireRing(ServerPlayer player, ServerLevel level, int radius) {
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
        player.sendSystemMessage(msg("Fire Ring!", ChatFormatting.RED));
    }

    // Physics dash - velocity based, not teleport
    private static void dashForward(ServerPlayer player, ServerLevel level, double distance) {
        Vec3 look = player.getLookAngle();
        Vec3 vel = new Vec3(look.x * 2.8, 0.35, look.z * 2.8);
        player.setDeltaMovement(vel);
        player.hurtMarked = true;
        // Slow falling after dash (air passive)
        player.addEffect(fx(MobEffects.SLOW_FALLING, 100, 0));

        Vec3 start = player.position();
        for (int i = 0; i < 25; i++) {
            Vec3 behind = start.subtract(look.scale(i * 0.35));
            level.sendParticles(ParticleTypes.CLOUD, behind.x + (Math.random() - 0.5) * 0.6,
                    behind.y + 0.5 + Math.random() * 1.5, behind.z + (Math.random() - 0.5) * 0.6, 2, 0.1, 0.1, 0.1,
                    0.03);
        }
        level.sendParticles(ParticleTypes.POOF, start.x, start.y + 1, start.z, 30, 0.6, 0.6, 0.6, 0.15);
        level.sendParticles(ParticleTypes.CLOUD, start.x, start.y + 1, start.z, 20, 0.5, 0.5, 0.5, 0.08);
        player.sendSystemMessage(msg("Dash!", ChatFormatting.AQUA));
    }

    // Lightning strikes exactly the targeted block via raycast
    private static void strikeLightningAtLookBlock(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(50));
        BlockHitResult hit = level
                .clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        Vec3 strikePos = hit.getType() == HitResult.Type.MISS ? end : Vec3.atCenterOf(hit.getBlockPos());

        net.minecraft.world.entity.LightningBolt bolt = new net.minecraft.world.entity.LightningBolt(
                EntityType.LIGHTNING_BOLT, level);
        bolt.moveTo(strikePos);
        bolt.setVisualOnly(false);
        level.addFreshEntity(bolt);

        // Pre-strike tracer
        for (int i = 0; i < 12; i++) {
            Vec3 p = eye.lerp(strikePos, (double) i / 11);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.2);
        }
        level.sendParticles(ParticleTypes.FLASH, strikePos.x, strikePos.y, strikePos.z, 1, 0, 0, 0, 0);
        player.sendSystemMessage(msg("Lightning Strike!", ChatFormatting.YELLOW));
    }

    // Cyclops-like optic beam with configurable damage/radius and optional block melting
    private static void fireLaserBeam(ServerPlayer player, ServerLevel level, double length, float damage, double hitRadius, boolean meltBlocks) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        for (LivingEntity entity : getNearbyEnemies(player, 30)) {
            Vec3 toE = entity.position().subtract(start);
            double dot = toE.dot(dir);
            if (dot > 0 && dot < length) {
                Vec3 proj = start.add(dir.scale(dot));
                if (proj.distanceTo(entity.position()) < hitRadius) {
                    entity.hurt(player.damageSources().magic(), damage);
                    entity.setDeltaMovement(dir.x * 1.2, 0.3, dir.z * 1.2);
                    entity.hurtMarked = true;
                    level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1, entity.getZ(), 25, 0.5,
                            0.5, 0.5, 0.3);
                }
            }
        }

        // Beam particles
        for (double d = 0.3; d < length; d += 0.3) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0.005);
            if (d % 1.5 < 0.3)
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);

            if (meltBlocks && d % 1.0 < 0.3) {
                BlockPos pos = BlockPos.containing(p);
                var state = level.getBlockState(pos);
                if (!state.isAir() && (state.is(Blocks.GLASS) || state.is(Blocks.TORCH) || state.is(Blocks.TALL_GRASS)
                        || state.is(Blocks.GRASS) || state.is(Blocks.SNOW) || state.is(Blocks.ICE))) {
                    level.destroyBlock(pos, false);
                    level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 4, 0.1, 0.1, 0.1, 0.01);
                }
            }
        }
        level.sendParticles(ParticleTypes.FLASH, start.x + dir.x, start.y + dir.y, start.z + dir.z, 1, 0, 0, 0, 0);
        player.sendSystemMessage(msg("Optic Blast!", ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private static void startLaserUlt(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        data.setLaserUltMaxTicks(80); // 4s
        data.setLaserUltTicks(80);
        data.setLaserUltActive(true);
        player.sendSystemMessage(msg("LASER ULT: channeling for 4s!", ChatFormatting.RED, ChatFormatting.BOLD));
    }

    // Called each server tick while laser ult is active
    public static void tickLaserUlt(ServerPlayer player, PlayerPowerData data, ServerLevel level) {
        if (!data.isLaserUltActive())
            return;

        // Stop movement while channeling
        player.setDeltaMovement(0, 0, 0);
        player.hurtMarked = true;
        player.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 6, 10));
        player.addEffect(fx(MobEffects.JUMP, 6, 128));

        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        double length = 42;

        // Instant mining/cutting along beam path
        for (double d = 0.4; d <= length; d += 0.4) {
            Vec3 p = start.add(dir.scale(d));
            BlockPos pos = BlockPos.containing(p);
            var state = level.getBlockState(pos);
            if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0) {
                level.destroyBlock(pos, true, player);
            }

            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.02, 0.02, 0.02, 0.01);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0);
        }

        // Damage entities in beam corridor
        for (LivingEntity entity : getNearbyEnemies(player, 45)) {
            Vec3 toE = entity.position().subtract(start);
            double dot = toE.dot(dir);
            if (dot > 0 && dot < length) {
                Vec3 proj = start.add(dir.scale(dot));
                if (proj.distanceTo(entity.position()) < 1.75) {
                    entity.hurt(player.damageSources().magic(), 6.5f);
                    entity.setSecondsOnFire(2);
                }
            }
        }
    }

    private static void geoShockwave(ServerPlayer player, ServerLevel level, double radius) {
        for (LivingEntity entity : getNearbyEnemies(player, radius)) {
            entity.hurt(player.damageSources().playerAttack(player), 12);
            entity.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
        }
        for (int deg = 0; deg < 360; deg += 5) {
            for (double r = 0.5; r <= radius; r += 1.2) {
                double x = player.getX() + r * Math.cos(Math.toRadians(deg)),
                        z = player.getZ() + r * Math.sin(Math.toRadians(deg));
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        x, player.getY() + 0.1, z, 2, 0, 0.2, 0, 0.1);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 4, 1, 0.5, 1, 0.1);
        player.sendSystemMessage(msg("Shockwave!", ChatFormatting.GOLD));
    }

    private static void voidBlind(ServerPlayer player, ServerLevel level, double radius) {
        for (LivingEntity entity : getNearbyEnemies(player, radius)) {
            entity.addEffect(fx(MobEffects.BLINDNESS, 200, 0));
            entity.addEffect(fx(MobEffects.POISON, 100, 0));
            level.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 1, entity.getZ(), 25, 0.5, 1, 0.5,
                    0.1);
        }
        for (int i = 0; i < 70; i++) {
            double a = Math.random() * Math.PI * 2, r = Math.random() * radius;
            level.sendParticles(ParticleTypes.PORTAL, player.getX() + r * Math.cos(a),
                    player.getY() + 1 + Math.random() * 3, player.getZ() + r * Math.sin(a), 1, 0, 0, 0, 0.05);
        }
        player.sendSystemMessage(msg("Darkness!", ChatFormatting.DARK_PURPLE));
    }

    private static void cageNearestEnemy(ServerPlayer player, ServerLevel level) {
        LivingEntity target = null;
        double minD = Double.MAX_VALUE;
        for (LivingEntity e : getNearbyEnemies(player, 12)) {
            double d = e.distanceTo(player);
            if (d < minD) {
                minD = d;
                target = e;
            }
        }
        if (target == null) {
            player.sendSystemMessage(msg("No targets!", ChatFormatting.RED));
            return;
        }
        BlockPos center = target.blockPosition();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = 0; dy <= 2; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) == 1 || dy == 0 || dy == 2 || Math.abs(dz) == 1) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (level.getBlockState(pos).isAir())
                            level.setBlock(pos, Blocks.BLUE_ICE.defaultBlockState(), 3);
                    }
                }
        level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1, target.getZ(), 60, 1, 1.5, 1,
                0.15);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getY() + 1, target.getZ(), 25, 0.5, 0.5,
                0.5, 0.2);
        player.sendSystemMessage(msg("Ice Cage!", ChatFormatting.AQUA));
    }

    // Fire ult: rise 20 blocks, shoot fireballs on LMB for 15s, return to origin
    private static void startFireUlt(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        data.setFireUltOrigin(player.getX(), player.getY(), player.getZ());
        player.teleportTo(player.getX(), player.getY() + 14, player.getZ());
        AttributeInstance gravity = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity != null) {
            data.setFireUltOldGravity(gravity.getBaseValue());
            gravity.setBaseValue(0.0);
        }
        data.setFireUltActive(true);
        data.setFireUltTicks(300); // 15s
        data.setFireUltFireballCooldown(0);
        // Rise particles
        for (int i = 0; i < 30; i++)
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() - i * 0.5, player.getZ(), 5, 1, 0.2,
                    1, 0.05);
        player.sendSystemMessage(
                msg("FIRE ULT! Shoot fireballs with LMB for 15s!", ChatFormatting.RED, ChatFormatting.BOLD));
    }

    // Called from event handler when fire ult active + LMB click
    public static void fireUltShoot(ServerPlayer player, PlayerPowerData data) {
        if (!data.isFireUltActive())
            return;

        ServerLevel level = (ServerLevel) player.level();

        Vec3 dir = player.getLookAngle().normalize();

        LargeFireball fb = new LargeFireball(EntityType.FIREBALL, level);
        fb.setOwner(player);
        fb.setPos(player.getEyePosition());
        fb.setDeltaMovement(dir.scale(1.5));

        level.addFreshEntity(fb);

        level.sendParticles(ParticleTypes.LAVA,
                player.getX(), player.getY(), player.getZ(),
                8, 0.3, 0.3, 0.3, 0.1);
    }

    // Called when fire ult expires
    public static void endFireUlt(ServerPlayer player, PlayerPowerData data) {
        player.teleportTo(
                data.getFireUltOriginX(),
                data.getFireUltOriginY(),
                data.getFireUltOriginZ());

        AttributeInstance gravity = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity != null) {
            gravity.setBaseValue(data.getFireUltOldGravity());

        }

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    player.getX(), player.getY() + 1, player.getZ(),
                    20, 1, 1, 1, 0.05);
        }
    }

    private static void lightningStrikeAll(ServerPlayer player, ServerLevel level, double radius) {
        for (LivingEntity entity : getNearbyEnemies(player, radius)) {
            net.minecraft.world.entity.LightningBolt bolt = new net.minecraft.world.entity.LightningBolt(
                    EntityType.LIGHTNING_BOLT, level);
            bolt.moveTo(entity.position());
            bolt.setVisualOnly(false);
            level.addFreshEntity(bolt);
        }
        for (int i = 0; i < 60; i++) {
            double a = Math.random() * Math.PI * 2, r = Math.random() * radius;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX() + r * Math.cos(a),
                    player.getY() + 20 + Math.random() * 5, player.getZ() + r * Math.sin(a), 1, 0, 0, 0, 0.5);
        }
        player.sendSystemMessage(msg("LIGHTNING STORM!", ChatFormatting.YELLOW, ChatFormatting.BOLD));
    }

    // Laser ult: mark target with red smoke, after 2s drop 4-6 TNT in diamond
    // pattern
    private static void tntAirstrike(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(60));
        BlockHitResult hit = level
                .clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? end : Vec3.atCenterOf(hit.getBlockPos());

        // Signal smoke at target
        for (int i = 0; i < 20; i++)
            level.sendParticles(ParticleTypes.CRIT, target.x + (Math.random() - 0.5) * 2, target.y + i * 0.3,
                    target.z + (Math.random() - 0.5) * 2, 2, 0.2, 0.1, 0.2, 0.05);
        level.sendParticles(ParticleTypes.FLAME, target.x, target.y + 1, target.z, 20, 1, 2, 1, 0.1);

        // Spawn TNT with varied fuses in diamond pattern
        int[][] pattern = { { 0, 0 }, { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 } };
        for (int[] offset : pattern) {
            net.minecraft.world.entity.item.PrimedTnt tnt = new net.minecraft.world.entity.item.PrimedTnt(
                    level, target.x + offset[0], target.y + 25, target.z + offset[1], player);
            tnt.setFuse(60 + level.random.nextInt(20)); // varied delay
            level.addFreshEntity(tnt);
        }
        player.sendSystemMessage(msg("AIRSTRIKE!", ChatFormatting.RED, ChatFormatting.BOLD));
    }

    // Geo ult: earthquake - throw all in radius, slowness, camera shake via potion
    private static void geoUlt(ServerPlayer player, ServerLevel level) {
        for (LivingEntity entity : getNearbyEnemies(player, 12)) {
            // Launch upward
            entity.setDeltaMovement(entity.getDeltaMovement().add(
                    (Math.random() - 0.5) * 0.5, 0.9 + Math.random() * 0.3, (Math.random() - 0.5) * 0.5));
            entity.hurtMarked = true;
            entity.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 200, 3));
            // Simulate camera shake via nausea effect
            entity.addEffect(fx(MobEffects.CONFUSION, 40, 10));
        }
        // Massive ground crack particles
        for (int deg = 0; deg < 360; deg += 3) {
            for (double r = 0.5; r <= 20; r += 2) {
                double x = player.getX() + r * Math.cos(Math.toRadians(deg)),
                        z = player.getZ() + r * Math.sin(Math.toRadians(deg));
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        x, player.getY() + 0.1, z, 1, 0, 0.3, 0, 0.15);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 3, 2, 0, 2,
                0.1);
        player.sendSystemMessage(msg("EARTHQUAKE!", ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private static void spawnIceMinions(ServerPlayer player, ServerLevel level) {
        for (int i = 0; i < 2; i++) {
            WitherSkeleton minion = new WitherSkeleton(EntityType.WITHER_SKELETON, level);
            minion.moveTo(player.getX() + (i == 0 ? 3 : -3), player.getY(), player.getZ());
            minion.setCustomName(Component.literal("Ice Guardian").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            minion.setCustomNameVisible(true);
            minion.setPersistenceRequired();
            minion.addEffect(fx(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 1));
            minion.addEffect(fx(MobEffects.ABSORPTION, Integer.MAX_VALUE, 4));
            minion.addEffect(fx(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 0));
            minion.getPersistentData().putString("occka_owner", player.getUUID().toString());
            level.addFreshEntity(minion);
            level.sendParticles(ParticleTypes.SNOWFLAKE, minion.getX(), minion.getY() + 1, minion.getZ(), 50, 0.5, 1,
                    0.5, 0.15);
        }
        player.sendSystemMessage(msg("Ice Guardians summoned!", ChatFormatting.AQUA));
    }

    private static void voidUlt(ServerPlayer player, ServerLevel level, double radius) {
        player.addEffect(fx(MobEffects.DAMAGE_BOOST, 600, 2));
        player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 600, 3));
        for (LivingEntity entity : getNearbyEnemies(player, radius)) {
            entity.addEffect(fx(MobEffects.WITHER, 200, 0));
            entity.addEffect(fx(MobEffects.WEAKNESS, 200, 1));
            level.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 1, entity.getZ(), 25, 0.5, 1, 0.5,
                    0.1);
        }
        for (int i = 0; i < 120; i++) {
            double a = Math.random() * Math.PI * 2, p = (Math.random() - 0.5) * Math.PI, r = Math.random() * radius;
            level.sendParticles(ParticleTypes.PORTAL, player.getX() + r * Math.cos(a) * Math.cos(p),
                    player.getY() + 2 + r * Math.sin(p), player.getZ() + r * Math.sin(a) * Math.cos(p), 1, 0, 0, 0,
                    0.03);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 2, 2, 2,
                0.1);
        player.sendSystemMessage(msg("VOID ULT!", ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
    }

    // Light ult: totem of undying for all nearby players + heavy self debuffs
    private static void lightUlt(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(12);
        List<Player> nearbyPlayers = player.level().getEntitiesOfClass(Player.class, box, p -> true);

        // Give totem effect to all nearby players (simulate with absorption + regen)
        for (Player p : nearbyPlayers) {
            p.addEffect(fx(MobEffects.ABSORPTION, 400, 4));
            p.addEffect(fx(MobEffects.REGENERATION, 200, 2));
            p.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 200, 1));
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 1, 0.5, 0.3);
        }

        // Heavy cost on self
        player.addEffect(fx(MobEffects.BLINDNESS, 200, 0));
        player.addEffect(fx(MobEffects.CONFUSION, 200, 0));
        player.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 200, 3));
        player.addEffect(fx(MobEffects.WEAKNESS, 200, 3));
        player.addEffect(fx(MobEffects.POISON, 200, 1));
        player.addEffect(fx(MobEffects.DIG_SLOWDOWN, 200, 3));

        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 100, 1, 2,
                1, 0.5);
        player.sendSystemMessage(msg("LIGHT SACRIFICE! Allies protected!", ChatFormatting.YELLOW, ChatFormatting.BOLD));
    }

    // Gravity ability: vortex that pulls enemies toward a point
    private static void gravityVortex(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(30));
        BlockHitResult hit = level
                .clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 center = hit.getType() == HitResult.Type.MISS ? end : Vec3.atCenterOf(hit.getBlockPos());

        // Store vortex in level data - simplified: just pull immediately
        for (LivingEntity entity : getNearbyEnemies(player, 12)) {
            Vec3 pull = center.subtract(entity.position()).normalize().scale(1.8);
            entity.setDeltaMovement(entity.getDeltaMovement().add(pull.x * 1.5, pull.y * 0.5, pull.z * 1.5));
            entity.hurtMarked = true;
        }

        // Black hole particles
        for (int i = 0; i < 80; i++) {
            double a = Math.random() * Math.PI * 2, r = Math.random() * 8;
            level.sendParticles(ParticleTypes.PORTAL, center.x + r * Math.cos(a), center.y + Math.random() * 3,
                    center.z + r * Math.sin(a), 1, 0, 0, 0, 0.2);
        }
        for (int i = 0; i < 20; i++)
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 1, center.z, 5, 1, 1, 1, 0.1);
        player.sendSystemMessage(msg("Gravity Vortex!", ChatFormatting.DARK_GRAY));
    }

    // Gravity ult: reverse gravity for all in radius
    private static void gravityUlt(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(50);
        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player);
        for (LivingEntity entity : entities) {
            entity.addEffect(fx(MobEffects.LEVITATION, 300, 0)); // 10s floating
            entity.setDeltaMovement(entity.getDeltaMovement().add(0, 2.0, 0));
            entity.hurtMarked = true;
        }
        for (int i = 0; i < 100; i++) {
            double a = Math.random() * Math.PI * 2, p = (Math.random() - 0.5) * Math.PI, r = Math.random() * 50;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    player.getX() + r * Math.cos(a) * Math.cos(p), player.getY() + 2 + r * Math.abs(Math.sin(p)),
                    player.getZ() + r * Math.sin(a) * Math.cos(p), 1, 0, 0, 0, 0.1);
        }
        player.sendSystemMessage(msg("GRAVITY INVERSION!", ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
    }

    // Echo shift: clone + invisibility
    private static void spawnEchoClone(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        // Spawn armor stand with player's name as clone visual
        net.minecraft.world.entity.decoration.ArmorStand clone = new net.minecraft.world.entity.decoration.ArmorStand(
                EntityType.ARMOR_STAND, level);
        clone.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        clone.setCustomName(Component.literal(player.getName().getString())
                .withStyle(player.getCapability(ModCapabilities.PLAYER_POWER).map(d -> d.getPowerType().getColor())
                        .orElse(ChatFormatting.WHITE)));
        clone.setCustomNameVisible(true);
        clone.setNoGravity(false);
        clone.getPersistentData().putString("occka_echo_clone", player.getUUID().toString());
        level.addFreshEntity(clone);

        player.addEffect(fx(MobEffects.INVISIBILITY, 400, 0)); // 20s
        data.setShiftCooldown(600); // 30s

        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 1, 0.5,
                0.1);
        player.sendSystemMessage(msg("Echo Clone deployed!", ChatFormatting.GREEN));
    }

    // Echo ability: swap with nearest enemy
    private static void echoSwap(ServerPlayer player, ServerLevel level) {
        LivingEntity target = null;
        double minD = Double.MAX_VALUE;
        // Prioritize players
        AABB box = player.getBoundingBox().inflate(30);
        for (Player p : player.level().getEntitiesOfClass(Player.class, box, p -> p != player)) {
            double d = p.distanceTo(player);
            if (d < minD) {
                minD = d;
                target = p;
            }
        }
        if (target == null) {
            for (LivingEntity e : getNearbyEnemies(player, 12)) {
                double d = e.distanceTo(player);
                if (d < minD) {
                    minD = d;
                    target = e;
                }
            }
        }
        if (target == null) {
            player.sendSystemMessage(msg("No targets!", ChatFormatting.RED));
            return;
        }

        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position();

        // Particles at both locations
        level.sendParticles(ParticleTypes.PORTAL, playerPos.x, playerPos.y + 1, playerPos.z, 30, 0.5, 1, 0.5, 0.15);
        level.sendParticles(ParticleTypes.PORTAL, targetPos.x, targetPos.y + 1, targetPos.z, 30, 0.5, 1, 0.5, 0.15);

        player.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        target.teleportTo(playerPos.x, playerPos.y, playerPos.z);
        player.sendSystemMessage(msg("Position Swap!", ChatFormatting.GREEN));
    }

    // Echo ult: blind all in radius + observer mode for 20s
    private static void echoUlt(ServerPlayer player, ServerLevel level) {
        for (LivingEntity entity : getNearbyEnemies(player, 12)) {
            entity.addEffect(fx(MobEffects.BLINDNESS, 100, 0));
            level.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 1, entity.getZ(), 20, 0.5, 1, 0.5,
                    0.1);
        }
        // Spectator for 20s - handled via gamemode change temporarily
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        // Schedule return to survival after 20s via tag
        player.getPersistentData().putInt("occka_echo_ult_ticks", 400);
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 60, 3, 3, 3, 0.1);
        player.sendSystemMessage(msg("Echo Phase: Spectator mode for 20s!", ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    // === UTILS ===
    // Water ability: summon 2-8 random aquatic mobs nearby
    private static void spawnAquaticMobs(ServerPlayer player, ServerLevel level) {
        java.util.Random rng = new java.util.Random();
        int count = 2 + rng.nextInt(7); // 2 to 8

        net.minecraft.world.entity.EntityType<?>[] aquaticTypes = {
                net.minecraft.world.entity.EntityType.COD,
                net.minecraft.world.entity.EntityType.SALMON,
                net.minecraft.world.entity.EntityType.TROPICAL_FISH,
                net.minecraft.world.entity.EntityType.SQUID,
                net.minecraft.world.entity.EntityType.GLOW_SQUID,
                net.minecraft.world.entity.EntityType.TURTLE,
                net.minecraft.world.entity.EntityType.DOLPHIN,
        };

        for (int i = 0; i < count; i++) {
            net.minecraft.world.entity.EntityType<?> type = aquaticTypes[rng.nextInt(aquaticTypes.length)];
            net.minecraft.world.entity.Entity mob = type.create(level);
            if (mob == null)
                continue;

            double angle = (i / (double) count) * Math.PI * 2 + rng.nextDouble();
            double r = 1.5 + rng.nextDouble() * 2.5;
            mob.moveTo(
                    player.getX() + r * Math.cos(angle),
                    player.getY() + 0.5,
                    player.getZ() + r * Math.sin(angle),
                    rng.nextFloat() * 360, 0);
            if (mob instanceof net.minecraft.world.entity.Mob m) {
                m.setPersistenceRequired();
                m.finalizeSpawn(level,
                        level.getCurrentDifficultyAt(mob.blockPosition()),
                        net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null, null);
            }
            level.addFreshEntity(mob);

            // Splash particles at spawn
            level.sendParticles(ParticleTypes.SPLASH,
                    mob.getX(), mob.getY() + 0.5, mob.getZ(),
                    8, 0.3, 0.2, 0.3, 0.1);
        }

        // Water burst
        level.sendParticles(ParticleTypes.SPLASH,
                player.getX(), player.getY() + 1, player.getZ(),
                40, 3, 1.5, 3, 0.15);
        level.sendParticles(ParticleTypes.BUBBLE_POP,
                player.getX(), player.getY() + 1, player.getZ(),
                20, 2, 1, 2, 0.1);
        player.sendSystemMessage(msg("Ocean Summon! (" + count + " creatures)", ChatFormatting.AQUA));
    }

    public static List<LivingEntity> getNearbyEnemies(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(LivingEntity.class, box,
                // Исключаем только самого себя
                e -> e != player);
    }

    private static void levitateEnemies(ServerPlayer player, double radius, int amp, int dur) {
        for (LivingEntity e : getNearbyEnemies(player, radius))
            e.addEffect(fx(MobEffects.LEVITATION, dur, amp));
    }

    // Ice ult: flash-freeze - stops all enemies in radius, encases them in ice
    private static void iceUltFreeze(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> enemies = getNearbyEnemies(player, 15);

        for (LivingEntity entity : enemies) {
            // 15 seconds of freeze
            entity.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 300, 10));
            entity.addEffect(fx(MobEffects.JUMP, 300, 128));
            entity.addEffect(fx(MobEffects.DIG_SLOWDOWN, 300, 10));
            entity.setDeltaMovement(0, entity.getDeltaMovement().y, 0);
            entity.hurtMarked = true;

            // Freeze burst on each enemy - NO blocks, only particles
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    80, 0.8, 1.5, 0.8, 0.25);
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                    ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    50, 0.8, 0.8, 0.8, 0.35);
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    20, 0.4, 0.4, 0.4, 0.2);
        }

        // Self buffs for 15s
        player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 300, 4));
        player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 300, 2));

        // Start 15s snowstorm via tag
        player.getPersistentData().putInt("occka_ice_snowstorm_ticks", 300);

        // Massive shockwave of snowflakes
        for (int deg = 0; deg < 360; deg += 3) {
            for (double r = 1; r <= 25; r += 2) {
                double x = player.getX() + r * Math.cos(Math.toRadians(deg));
                double z = player.getZ() + r * Math.sin(Math.toRadians(deg));
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        x, player.getY() + 0.2, z, 1, 0, 0.1, 0, 0.03);
            }
        }
        level.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);

        for (Player p : level.getEntitiesOfClass(Player.class,
                player.getBoundingBox().inflate(30), x -> true)) {
            ((ServerPlayer) p).sendSystemMessage(
                    Component.literal(player.getName().getString() + " unleashed a BLIZZARD!")
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
    }
}
