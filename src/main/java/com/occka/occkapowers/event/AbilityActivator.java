package com.occka.occkapowers.event;

import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.ability.PowerType;
import com.occka.occkapowers.network.NetworkHandler;
import com.occka.occkapowers.network.PacketSyncPowerData;
import com.occka.occkapowers.registry.ModCapabilities;
import com.occka.occkapowers.unlock.UnlockHelper;
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
import java.util.Random;

public class AbilityActivator {

    private static final Random RNG = new Random();

    private static MobEffectInstance fx(net.minecraft.world.effect.MobEffect eff, int dur, int amp) {
        return new MobEffectInstance(eff, dur, amp, false, false);
    }

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

    public static void activateShiftHeld(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
            PowerType type = data.getPowerType();
            if (type == PowerType.NONE) return;
            activateShift(player, data, type);
        });
    }

    public static void syncToClient(ServerPlayer player, PlayerPowerData data) {
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketSyncPowerData(data));
    }

    // ===== SHIFT =====
    private static void activateShift(ServerPlayer player, PlayerPowerData data, PowerType type) {
        if (!(player.level() instanceof ServerLevel level)) return;

        switch (type) {
            case FIRE -> {
                player.addEffect(fx(MobEffects.LEVITATION, 40, 2));
                level.sendParticles(ParticleTypes.FLAME,
                        player.getX(), player.getY() + 0.8, player.getZ(),
                        14, 1.5, 0.6, 1.5, 0.02);
                level.sendParticles(ParticleTypes.LAVA,
                        player.getX(), player.getY() + 0.1, player.getZ(),
                        7, 1.0, 0.1, 1.0, 0.0);
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        player.getX(), player.getY(), player.getZ(),
                        4, 0.2, 0.2, 0.2, 0.01);
            }
            case CHAOS -> ChaosAbility.activateShift(player, level);
            case SUPERFORCE -> SuperforceAbility.activateAbility(player, level);
            case AIR -> {
                player.addEffect(fx(MobEffects.LEVITATION, 25, 3));
                player.addEffect(fx(MobEffects.SLOW_FALLING, 25, 0));
                level.sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() - 0.5, player.getZ(),
                        20, 0.5, 0.1, 0.5, 0.01);
            }
            case WATER -> {
                AABB box = player.getBoundingBox().inflate(3);
                player.level().getEntitiesOfClass(LivingEntity.class, box, e -> true)
                        .forEach(e -> e.addEffect(fx(MobEffects.REGENERATION, 25, 2)));
                level.sendParticles(ParticleTypes.BUBBLE_POP,
                        player.getX(), player.getY() + 1.5, player.getZ(),
                        24, 3.0, 1.5, 3.0, 0.02);
            }
            case ICE -> {
                List<LivingEntity> enemies = getNearbyEnemies(player, 5);
                for (LivingEntity e : enemies) {
                    e.hurt(player.damageSources().playerAttack(player), 0.5f);
                    e.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        player.getX(), player.getY() + 1.5, player.getZ(),
                        25, 2.5, 1.5, 2.5, 0.03);
            }
            case LIGHTNING -> {
                player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 250, 6));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        player.getX(), player.getY() + 1, player.getZ(),
                        20, 0.75, 1.0, 0.75, 0.3);
                level.sendParticles(ParticleTypes.CRIT,
                        player.getX(), player.getY() + 1, player.getZ(),
                        5, 0.3, 0.5, 0.3, 0.2);
            }
            case LASER -> {
                getNearbyEnemies(player, 50).forEach(e -> e.addEffect(fx(MobEffects.GLOWING, 25, 0)));
                Vec3 eye = player.getEyePosition();
                Vec3 look = player.getLookAngle();
                for (int i = 3; i <= 15; i += 5) {
                    Vec3 p = eye.add(look.scale(i));
                    level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 6, 0.1, 0.1, 0.1, 0.01);
                }
            }
            case GEO -> {
                player.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 25, 2));
                player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 25, 1));
                level.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        player.getX(), player.getY() + 0.5, player.getZ(),
                        24, 1.3, 0.5, 1.3, 0.0);
            }
            case VOID -> {
                player.addEffect(fx(MobEffects.NIGHT_VISION, 25, 0));
                player.addEffect(fx(MobEffects.INVISIBILITY, 25, 0));
                level.sendParticles(ParticleTypes.PORTAL,
                        player.getX(), player.getY() + 1.25, player.getZ(),
                        15, 1.0, 1.25, 1.0, 0.05);
            }
            case LIGHT -> {
                BlockPos center = player.blockPosition();
                for (int dx = -5; dx <= 5; dx++)
                    for (int dz = -5; dz <= 5; dz++) {
                        BlockPos pos = center.offset(dx, 0, dz);
                        var state = level.getBlockState(pos);
                        if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock) {
                            boolean boneMeal = net.minecraft.world.item.BoneMealItem.applyBonemeal(
                                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL),
                                    level, pos, player);
                            if (boneMeal)
                                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                        pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                                        3, 0.3, 0.3, 0.3, 0);
                        }
                    }
                AABB box2 = player.getBoundingBox().inflate(5);
                player.level().getEntitiesOfClass(Player.class, box2, p -> true)
                        .forEach(p -> p.addEffect(fx(MobEffects.SATURATION, 25, 1)));
                level.sendParticles(ParticleTypes.END_ROD,
                        player.getX(), player.getY() + 1, player.getZ(),
                        15, 1.5, 1.5, 1.5, 0.05);
            }
            case GRAVITY -> {
                player.addEffect(fx(MobEffects.LEVITATION, 25, 0));
                player.setDeltaMovement(player.getDeltaMovement().x, 0, player.getDeltaMovement().z);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        player.getX(), player.getY() + 1, player.getZ(),
                        15, 1.5, 1.0, 1.5, 0.02);
            }
            case ECHO -> {
                if (data.getShiftCooldown() > 0) return;
                spawnEchoClone(player, level, data);
            }
        }
    }

    // ===== ABILITY =====
    private static void activateAbility(ServerPlayer player, PlayerPowerData data, PowerType type) {
        if (!data.isAbilityUnlocked()) {
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
        if (!(player.level() instanceof ServerLevel level)) return;

        switch (type) {
            case FIRE -> {
                Vec3 playerPos = player.position();
                List<LivingEntity> enemies = getNearbyEnemies(player, 10); // один вызов
                for (LivingEntity entity : enemies) {
                    Vec3 dir = entity.position().subtract(playerPos).normalize();
                    double dist = entity.distanceTo(player);
                    double force = 1.8 * (1.0 - dist / 10.0) + 0.4;
                    entity.setDeltaMovement(dir.x * force, 0.45 + (force * 0.3), dir.z * force);
                    entity.hurtMarked = true;
                    entity.setSecondsOnFire(8);
                    entity.hurt(player.damageSources().onFire(), 4);
                    level.sendParticles(ParticleTypes.FLAME,
                            entity.getX(), entity.getY() + 1, entity.getZ(),
                            12, 0.3, 0.5, 0.3, 0.08);
                }
                level.sendParticles(ParticleTypes.FLAME,
                        player.getX(), player.getY() + 0.3, player.getZ(),
                        80, 5.0, 0.2, 5.0, 0.06);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        player.getX(), player.getY(), player.getZ(),
                        2, 0.5, 0, 0.5, 0.05);
                level.sendParticles(ParticleTypes.LAVA,
                        player.getX(), player.getY() + 0.5, player.getZ(),
                        20, 1.5, 0.5, 1.5, 0.2);
                player.sendSystemMessage(msg("Firestorm!", ChatFormatting.RED));
            }
            case AIR -> dashForward(player, level, 15);
            case SUPERFORCE -> SuperforceAbility.activateShift(player, level);
            case WATER -> {
                levitateEnemies(player, 15, 0, 200);
                level.sendParticles(ParticleTypes.SPLASH,
                        player.getX(), player.getY() + 1, player.getZ(),
                        60, 7, 2, 7, 0.1);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        player.getX(), player.getY() + 2, player.getZ(),
                        40, 7.5, 1.5, 7.5, 0.05);
            }
            case ICE -> cageNearestEnemy(player, level);
            case LIGHTNING -> strikeLightningAtLookBlock(player, level);
            case CHAOS -> ChaosAbility.activateAbility(player, level);
            case LASER -> fireLaserBeam(player, level, 15);
            case GEO -> geoShockwave(player, level, 10);
            case VOID -> voidBlind(player, level, 15);
            case LIGHT -> {
                AABB box = player.getBoundingBox().inflate(15);
                player.level().getEntitiesOfClass(LivingEntity.class, box, e -> true).forEach(e -> {
                    e.addEffect(fx(MobEffects.GLOWING, 200, 0));
                    if (e instanceof Player)
                        e.addEffect(fx(MobEffects.DIG_SPEED, 200, 2));
                });
                level.sendParticles(ParticleTypes.END_ROD,
                        player.getX(), player.getY() + 1, player.getZ(),
                        60, 7, 3, 7, 0.15);
                level.sendParticles(ParticleTypes.FLASH,
                        player.getX(), player.getY() + 1, player.getZ(),
                        1, 0, 0, 0, 0);
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
            player.sendSystemMessage(
                    msg("Ult on cooldown: " + String.format("%.1f", data.getUltCooldown() / 20f) + "s",
                            ChatFormatting.RED));
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) return;

        switch (type) {
            case FIRE -> startFireUlt(player, level, data);
            case AIR -> {
                levitateEnemies(player, 20, 21, 40);
                level.sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 2, player.getZ(),
                        80, 10, 10, 10, 0.05);
                player.addEffect(fx(MobEffects.SLOW_FALLING, 100, 0));
                player.sendSystemMessage(msg("AIR BLAST!", ChatFormatting.AQUA, ChatFormatting.BOLD));
            }
            case WATER -> {
                player.addEffect(fx(MobEffects.ABSORPTION, 1200, 17));
                level.setWeatherParameters(0, 6000, true, false);
                level.sendParticles(ParticleTypes.DRIPPING_WATER,
                        player.getX(), player.getY() + 12, player.getZ(),
                        60, 10, 2.5, 10, 0.5);
                player.sendSystemMessage(msg("Tide of Power!", ChatFormatting.AQUA));
            }
            case ICE -> iceUltFreeze(player, level);
            case LIGHTNING -> lightningStrikeAll(player, level, 40);
            case LASER -> tntAirstrike(player, level);
            case SUPERFORCE -> SuperforceAbility.activateUlt(player, level);
            case GEO -> geoUlt(player, level);
            case CHAOS -> ChaosAbility.activateUlt(player, level);
            case VOID -> voidUlt(player, level, 30);
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
            level.sendParticles(ParticleTypes.FLAME, x, cy + 0.5, z, 2, 0.1, 0.3, 0.1, 0.03);
        }
        level.sendParticles(ParticleTypes.LAVA, cx, cy + 0.2, cz, radius * 2, radius, 0.1, radius, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy + 1, cz, radius * 3, radius, 0.5, radius, 0.01);
        player.sendSystemMessage(msg("Fire Ring!", ChatFormatting.RED));
    }

    private static void dashForward(ServerPlayer player, ServerLevel level, double distance) {
        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(new Vec3(look.x * 2.8, 0.35, look.z * 2.8));
        player.hurtMarked = true;
        player.addEffect(fx(MobEffects.SLOW_FALLING, 100, 0));

        Vec3 start = player.position();
        level.sendParticles(ParticleTypes.CLOUD,
                start.x, start.y + 1, start.z,
                50, 4.0, 0.8, 4.0, 0.03);
        level.sendParticles(ParticleTypes.POOF, start.x, start.y + 1, start.z, 30, 0.6, 0.6, 0.6, 0.15);
        level.sendParticles(ParticleTypes.CLOUD, start.x, start.y + 1, start.z, 20, 0.5, 0.5, 0.5, 0.08);
        player.sendSystemMessage(msg("Dash!", ChatFormatting.AQUA));
    }

    private static void strikeLightningAtLookBlock(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(50));
        BlockHitResult hit = level.clip(
                new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 strikePos = hit.getType() == HitResult.Type.MISS ? end : Vec3.atCenterOf(hit.getBlockPos());

        net.minecraft.world.entity.LightningBolt bolt = new net.minecraft.world.entity.LightningBolt(
                EntityType.LIGHTNING_BOLT, level);
        bolt.moveTo(strikePos);
        bolt.setVisualOnly(false);
        level.addFreshEntity(bolt);

        for (int i = 0; i <= 2; i++) {
            Vec3 p = eye.lerp(strikePos, i / 2.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 8, 0.2, 0.2, 0.2, 0.2);
        }
        level.sendParticles(ParticleTypes.FLASH, strikePos.x, strikePos.y, strikePos.z, 1, 0, 0, 0, 0);
        player.sendSystemMessage(msg("Lightning Strike!", ChatFormatting.YELLOW));
    }

    private static void fireLaserBeam(ServerPlayer player, ServerLevel level, double length) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        List<LivingEntity> enemies = getNearbyEnemies(player, 20); // один вызов
        for (LivingEntity entity : enemies) {
            Vec3 toE = entity.position().subtract(start);
            double dot = toE.dot(dir);
            if (dot > 0 && dot < length) {
                Vec3 proj = start.add(dir.scale(dot));
                if (proj.distanceTo(entity.position()) < 2.5) {
                    entity.hurt(player.damageSources().magic(), 18);
                    level.sendParticles(ParticleTypes.CRIT,
                            entity.getX(), entity.getY() + 1, entity.getZ(),
                            25, 0.5, 0.5, 0.5, 0.3);
                }
            }
        }
        for (double d = 0; d < length; d += 3.0) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 3, 0.05, 0.05, 0.05, 0);
        }
        level.sendParticles(ParticleTypes.FLASH,
                start.x + dir.x, start.y + dir.y, start.z + dir.z, 1, 0, 0, 0, 0);
        player.sendSystemMessage(msg("Laser Beam!", ChatFormatting.RED));
    }

    private static void geoShockwave(ServerPlayer player, ServerLevel level, double radius) {
        List<LivingEntity> enemies = getNearbyEnemies(player, radius); // один вызов
        for (LivingEntity entity : enemies) {
            entity.hurt(player.damageSources().playerAttack(player), 12);
            entity.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
        }
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                player.getX(), player.getY() + 0.1, player.getZ(),
                200, radius * 0.5, 0.3, radius * 0.5, 0.1);
        level.sendParticles(ParticleTypes.EXPLOSION,
                player.getX(), player.getY(), player.getZ(),
                4, 1, 0.5, 1, 0.1);
        player.sendSystemMessage(msg("Shockwave!", ChatFormatting.GOLD));
    }

    private static void voidBlind(ServerPlayer player, ServerLevel level, double radius) {
        List<LivingEntity> enemies = getNearbyEnemies(player, radius); // один вызов
        for (LivingEntity entity : enemies) {
            entity.addEffect(fx(MobEffects.BLINDNESS, 200, 0));
            entity.addEffect(fx(MobEffects.POISON, 100, 0));
            level.sendParticles(ParticleTypes.PORTAL,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    25, 0.5, 1, 0.5, 0.1);
        }
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 2, player.getZ(),
                70, radius * 0.5, 1.5, radius * 0.5, 0.05);
        player.sendSystemMessage(msg("Darkness!", ChatFormatting.DARK_PURPLE));
    }

    private static void cageNearestEnemy(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> nearby = getNearbyEnemies(player, 20); // один вызов
        LivingEntity target = null;
        double minD = Double.MAX_VALUE;
        for (LivingEntity e : nearby) {
            double d = e.distanceTo(player);
            if (d < minD) { minD = d; target = e; }
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
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                target.getX(), target.getY() + 1, target.getZ(), 60, 1, 1.5, 1, 0.15);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                target.getX(), target.getY() + 1, target.getZ(), 25, 0.5, 0.5, 0.5, 0.2);
        player.sendSystemMessage(msg("Ice Cage!", ChatFormatting.AQUA));
    }

    private static void startFireUlt(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
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
        level.sendParticles(ParticleTypes.FLAME,
                player.getX(), player.getY() - 7, player.getZ(),
                30, 0.5, 7, 0.5, 0.05);
        player.sendSystemMessage(
                msg("FIRE ULT! Shoot fireballs with LMB for 15s!", ChatFormatting.RED, ChatFormatting.BOLD));
    }

    public static void fireUltShoot(ServerPlayer player, PlayerPowerData data) {
        if (!data.isFireUltActive()) return;
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

    public static void endFireUlt(ServerPlayer player, PlayerPowerData data) {
        player.teleportTo(data.getFireUltOriginX(), data.getFireUltOriginY(), data.getFireUltOriginZ());
        AttributeInstance gravity = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity != null) gravity.setBaseValue(data.getFireUltOldGravity());
        if (player.level() instanceof ServerLevel level)
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    player.getX(), player.getY() + 1, player.getZ(),
                    20, 1, 1, 1, 0.05);
    }

    private static void lightningStrikeAll(ServerPlayer player, ServerLevel level, double radius) {
        for (LivingEntity entity : getNearbyEnemies(player, radius)) {
            net.minecraft.world.entity.LightningBolt bolt = new net.minecraft.world.entity.LightningBolt(
                    EntityType.LIGHTNING_BOLT, level);
            bolt.moveTo(entity.position());
            bolt.setVisualOnly(false);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 22, player.getZ(),
                60, radius * 0.5, 2.5, radius * 0.5, 0.5);
        player.sendSystemMessage(msg("LIGHTNING STORM!", ChatFormatting.YELLOW, ChatFormatting.BOLD));
    }

    private static void tntAirstrike(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(60));
        BlockHitResult hit = level.clip(
                new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? end : Vec3.atCenterOf(hit.getBlockPos());

        level.sendParticles(ParticleTypes.CRIT,
                target.x, target.y + 3, target.z, 40, 1.0, 3.0, 1.0, 0.05);
        level.sendParticles(ParticleTypes.FLAME,
                target.x, target.y + 1, target.z, 20, 1, 2, 1, 0.1);

        int[][] pattern = { { 0, 0 }, { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 } };
        for (int[] offset : pattern) {
            net.minecraft.world.entity.item.PrimedTnt tnt = new net.minecraft.world.entity.item.PrimedTnt(
                    level, target.x + offset[0], target.y + 25, target.z + offset[1], player);
            tnt.setFuse(60 + level.random.nextInt(20));
            level.addFreshEntity(tnt);
        }
        player.sendSystemMessage(msg("AIRSTRIKE!", ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private static void geoUlt(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> enemies = getNearbyEnemies(player, 20); // один вызов
        for (LivingEntity entity : enemies) {
            entity.setDeltaMovement(entity.getDeltaMovement().add(
                    (RNG.nextDouble() - 0.5) * 0.5, 0.9 + RNG.nextDouble() * 0.3,
                    (RNG.nextDouble() - 0.5) * 0.5));
            entity.hurtMarked = true;
            entity.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 200, 3));
            entity.addEffect(fx(MobEffects.CONFUSION, 40, 10));
        }
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                player.getX(), player.getY() + 0.1, player.getZ(),
                500, 10, 0.5, 10, 0.15);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                player.getX(), player.getY(), player.getZ(),
                3, 2, 0, 2, 0.1);
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
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    minion.getX(), minion.getY() + 1, minion.getZ(), 50, 0.5, 1, 0.5, 0.15);
        }
        player.sendSystemMessage(msg("Ice Guardians summoned!", ChatFormatting.AQUA));
    }

    private static void voidUlt(ServerPlayer player, ServerLevel level, double radius) {
        player.addEffect(fx(MobEffects.DAMAGE_BOOST, 600, 2));
        player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 600, 3));
        List<LivingEntity> enemies = getNearbyEnemies(player, radius); // один вызов
        for (LivingEntity entity : enemies) {
            entity.addEffect(fx(MobEffects.WITHER, 200, 0));
            entity.addEffect(fx(MobEffects.WEAKNESS, 200, 1));
            level.sendParticles(ParticleTypes.PORTAL,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    25, 0.5, 1, 0.5, 0.1);
        }
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 2, player.getZ(),
                120, radius * 0.5, radius * 0.5, radius * 0.5, 0.03);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + 1, player.getZ(),
                40, 2, 2, 2, 0.1);
        player.sendSystemMessage(msg("VOID ULT!", ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
    }

    private static void lightUlt(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(20);
        List<Player> nearbyPlayers = player.level().getEntitiesOfClass(Player.class, box, p -> true);
        for (Player p : nearbyPlayers) {
            p.addEffect(fx(MobEffects.ABSORPTION, 400, 4));
            p.addEffect(fx(MobEffects.REGENERATION, 200, 2));
            p.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 200, 1));
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 1, 0.5, 0.3);
        }
        player.addEffect(fx(MobEffects.BLINDNESS, 200, 0));
        player.addEffect(fx(MobEffects.CONFUSION, 200, 0));
        player.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 200, 3));
        player.addEffect(fx(MobEffects.WEAKNESS, 200, 3));
        player.addEffect(fx(MobEffects.POISON, 200, 1));
        player.addEffect(fx(MobEffects.DIG_SLOWDOWN, 200, 3));
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1, player.getZ(),
                100, 1, 2, 1, 0.5);
        player.sendSystemMessage(
                msg("LIGHT SACRIFICE! Allies protected!", ChatFormatting.YELLOW, ChatFormatting.BOLD));
    }

    private static void gravityVortex(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(30));
        BlockHitResult hit = level.clip(
                new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 center = hit.getType() == HitResult.Type.MISS ? end : Vec3.atCenterOf(hit.getBlockPos());

        for (LivingEntity entity : getNearbyEnemies(player, 20)) {
            Vec3 pull = center.subtract(entity.position()).normalize().scale(1.8);
            entity.setDeltaMovement(entity.getDeltaMovement().add(pull.x * 1.5, pull.y * 0.5, pull.z * 1.5));
            entity.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.PORTAL,
                center.x, center.y + 1.5, center.z,
                80, 4, 1.5, 4, 0.2);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                center.x, center.y + 1, center.z,
                20, 1, 1, 1, 0.1);
        player.sendSystemMessage(msg("Gravity Vortex!", ChatFormatting.DARK_GRAY));
    }

    private static void gravityUlt(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(50);
        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player);
        for (LivingEntity entity : entities) {
            entity.addEffect(fx(MobEffects.LEVITATION, 300, 0));
            entity.setDeltaMovement(entity.getDeltaMovement().add(0, 2.0, 0));
            entity.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + 2, player.getZ(),
                100, 25, 25, 25, 0.1);
        player.sendSystemMessage(msg("GRAVITY INVERSION!", ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
    }

    private static void spawnEchoClone(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        net.minecraft.world.entity.decoration.ArmorStand clone =
                new net.minecraft.world.entity.decoration.ArmorStand(EntityType.ARMOR_STAND, level);
        clone.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        clone.setCustomName(Component.literal(player.getName().getString())
                .withStyle(player.getCapability(ModCapabilities.PLAYER_POWER)
                        .map(d -> d.getPowerType().getColor()).orElse(ChatFormatting.WHITE)));
        clone.setCustomNameVisible(true);
        clone.setNoGravity(false);
        clone.getPersistentData().putString("occka_echo_clone", player.getUUID().toString());
        level.addFreshEntity(clone);
        player.addEffect(fx(MobEffects.INVISIBILITY, 400, 0));
        data.setShiftCooldown(600);
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1, player.getZ(),
                30, 0.5, 1, 0.5, 0.1);
        player.sendSystemMessage(msg("Echo Clone deployed!", ChatFormatting.GREEN));
    }

    private static void echoSwap(ServerPlayer player, ServerLevel level) {
        LivingEntity target = null;
        double minD = Double.MAX_VALUE;
        AABB box = player.getBoundingBox().inflate(30);
        for (Player p : player.level().getEntitiesOfClass(Player.class, box, p -> p != player)) {
            double d = p.distanceTo(player);
            if (d < minD) { minD = d; target = p; }
        }
        if (target == null) {
            for (LivingEntity e : getNearbyEnemies(player, 30)) {
                double d = e.distanceTo(player);
                if (d < minD) { minD = d; target = e; }
            }
        }
        if (target == null) {
            player.sendSystemMessage(msg("No targets!", ChatFormatting.RED));
            return;
        }
        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position();
        level.sendParticles(ParticleTypes.PORTAL, playerPos.x, playerPos.y + 1, playerPos.z, 30, 0.5, 1, 0.5, 0.15);
        level.sendParticles(ParticleTypes.PORTAL, targetPos.x, targetPos.y + 1, targetPos.z, 30, 0.5, 1, 0.5, 0.15);
        player.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        target.teleportTo(playerPos.x, playerPos.y, playerPos.z);
        player.sendSystemMessage(msg("Position Swap!", ChatFormatting.GREEN));
    }

    private static void echoUlt(ServerPlayer player, ServerLevel level) {
        for (LivingEntity entity : getNearbyEnemies(player, 30)) {
            entity.addEffect(fx(MobEffects.BLINDNESS, 100, 0));
            level.sendParticles(ParticleTypes.PORTAL,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    20, 0.5, 1, 0.5, 0.1);
        }
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        player.getPersistentData().putInt("occka_echo_ult_ticks", 400);
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1, player.getZ(),
                60, 3, 3, 3, 0.1);
        player.sendSystemMessage(
                msg("Echo Phase: Spectator mode for 20s!", ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    // === UTILS ===
    public static List<LivingEntity> getNearbyEnemies(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && !(e instanceof Player p && p.isAlliedTo(player)));
    }

    private static void levitateEnemies(ServerPlayer player, double radius, int amp, int dur) {
        for (LivingEntity e : getNearbyEnemies(player, radius))
            e.addEffect(fx(MobEffects.LEVITATION, dur, amp));
    }

    private static void iceUltFreeze(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> enemies = getNearbyEnemies(player, 25); // один вызов
        for (LivingEntity entity : enemies) {
            entity.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 300, 10));
            entity.addEffect(fx(MobEffects.JUMP, 300, 128));
            entity.addEffect(fx(MobEffects.DIG_SLOWDOWN, 300, 10));
            entity.setDeltaMovement(0, entity.getDeltaMovement().y, 0);
            entity.hurtMarked = true;
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    entity.getX(), entity.getY() + 1, entity.getZ(), 80, 0.8, 1.5, 0.8, 0.25);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    entity.getX(), entity.getY() + 1, entity.getZ(), 50, 0.8, 0.8, 0.8, 0.35);
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    entity.getX(), entity.getY() + 1, entity.getZ(), 20, 0.4, 0.4, 0.4, 0.2);
        }
        player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 300, 4));
        player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 300, 2));
        player.getPersistentData().putInt("occka_ice_snowstorm_ticks", 300);

        level.sendParticles(ParticleTypes.SNOWFLAKE,
                player.getX(), player.getY() + 0.2, player.getZ(),
                300, 12.5, 0.2, 12.5, 0.03);
        level.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);

        for (Player p : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(30), x -> true)) {
            ((ServerPlayer) p).sendSystemMessage(
                    Component.literal(player.getName().getString() + " unleashed a BLIZZARD!")
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
    }
}
