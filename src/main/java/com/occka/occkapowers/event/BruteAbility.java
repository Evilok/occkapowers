package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

public final class BruteAbility {
    private BruteAbility() {
    }

    private static final String NBT_ZONE_ACTIVE = "occka_brute_zone_active";
    private static final String NBT_ZONE_X = "occka_brute_zone_x";
    private static final String NBT_ZONE_Y = "occka_brute_zone_y";
    private static final String NBT_ZONE_Z = "occka_brute_zone_z";
    private static final String NBT_BULLDOZE = "occka_brute_bulldoze_active";
    private static final String NBT_BULLDOZE_TICK = "occka_brute_bulldoze_ticks";
    private static final String NBT_CHARGE_TICK = "occka_brute_charge_ticks";
    private static final String NBT_CHARGING = "occka_brute_charging";

    private static final double ZONE_HALF = 2.0;

    public static void activateShift(ServerPlayer player, ServerLevel level) { /* same */
        Vec3 pos = player.position();
        breakNearbyBlocks(player, level, pos, 2);
        double radius = 4.0;
        AABB box = new AABB(pos.x - radius, pos.y - 1, pos.z - radius, pos.x + radius, pos.y + 3, pos.z + radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive());
        for (LivingEntity entity : targets) {
            double dist = Math.max(0.01, entity.distanceTo(player));
            float damage = (float) (10.0 - (10.0 - 5.0) * (dist / radius));
            damage = Math.max(5.0f, Math.min(10.0f, damage));
            Vec3 kb = entity.position().subtract(pos).normalize();
            entity.setDeltaMovement(kb.x * 1.8, 0.7, kb.z * 1.8);
            entity.hurtMarked = true;
            entity.hurt(player.damageSources().playerAttack(player), damage);
        }
        for (int deg = 0; deg < 360; deg += 10) {
            for (double r = 0.5; r <= radius; r += 1.0) {
                double x = pos.x + r * Math.cos(Math.toRadians(deg));
                double z = pos.z + r * Math.sin(Math.toRadians(deg));
                BlockPos bp = BlockPos.containing(x, pos.y - 0.5, z);
                BlockState under = level.getBlockState(bp).isAir() ? level.getBlockState(bp.below())
                        : level.getBlockState(bp);
                if (!under.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), x, pos.y + 0.2, z, 1, 0,
                            0.3, 0, 0.15);
                }
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 2, 0.3, 0, 0.3, 0.04);
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 6, 1.5, 0.1, 1.5, 0.05);
        player.sendSystemMessage(AbilityCommon.msg("GROUND SLAM!", ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        if (player.getPersistentData().getBoolean(NBT_ZONE_ACTIVE)) {
            player.sendSystemMessage(
                    AbilityCommon.msg("Zone already active! Leave it to cancel.", ChatFormatting.YELLOW));
            return;
        }
        Vec3 pos = player.position();
        player.getPersistentData().putBoolean(NBT_ZONE_ACTIVE, true);
        player.getPersistentData().putDouble(NBT_ZONE_X, pos.x);
        player.getPersistentData().putDouble(NBT_ZONE_Y, pos.y);
        player.getPersistentData().putDouble(NBT_ZONE_Z, pos.z);
        level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);
        for (int i = 0; i < 40; i++) {
            double a = Math.random() * Math.PI * 2;
            double r = Math.random() * ZONE_HALF;
            level.sendParticles(new DustParticleOptions(new Vector3f(0.8f, 0.1f, 0.1f), 1.2f), pos.x + r * Math.cos(a),
                    pos.y + Math.random() * ZONE_HALF * 2, pos.z + r * Math.sin(a), 1, 0, 0, 0, 0);
        }
        player.sendSystemMessage(AbilityCommon.msg("FORTIFIED ZONE active! Resistance V inside.",
                ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    public static void tickZone(ServerPlayer player, ServerLevel level,
            com.occka.occkapowers.ability.PlayerPowerData data) {
        if (!player.getPersistentData().getBoolean(NBT_ZONE_ACTIVE))
            return;
        double cx = player.getPersistentData().getDouble(NBT_ZONE_X);
        double cy = player.getPersistentData().getDouble(NBT_ZONE_Y);
        double cz = player.getPersistentData().getDouble(NBT_ZONE_Z);
        boolean inside = Math.abs(player.getX() - cx) <= ZONE_HALF && player.getY() >= cy - 0.5
                && player.getY() <= cy + ZONE_HALF * 2 + 0.5 && Math.abs(player.getZ() - cz) <= ZONE_HALF;
        if (inside)
            player.addEffect(AbilityCommon.fx(MobEffects.DAMAGE_RESISTANCE, 5, 4));
        else {
            deactivateZone(player, level, data);
            return;
        }
        if (player.tickCount % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double px = cx + (Math.random() * 2 - 1) * ZONE_HALF;
                double py = cy + Math.random() * ZONE_HALF * 2;
                double pz = cz + (Math.random() * 2 - 1) * ZONE_HALF;
                level.sendParticles(new DustParticleOptions(new Vector3f(0.9f, 0.15f, 0.15f), 0.8f), px, py, pz, 1, 0,
                        0, 0, 0);
            }
        }
        if (player.tickCount % 20 == 0)
            spawnZoneBorderParticles(level, cx, cy, cz);
    }

    private static void deactivateZone(ServerPlayer player, ServerLevel level,
            com.occka.occkapowers.ability.PlayerPowerData data) {
        player.getPersistentData().putBoolean(NBT_ZONE_ACTIVE, false);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        double cx = player.getPersistentData().getDouble(NBT_ZONE_X);
        double cy = player.getPersistentData().getDouble(NBT_ZONE_Y);
        double cz = player.getPersistentData().getDouble(NBT_ZONE_Z);
        level.sendParticles(ParticleTypes.POOF, cx, cy + ZONE_HALF, cz, 20, ZONE_HALF * 0.8, ZONE_HALF * 0.8,
                ZONE_HALF * 0.8, 0.05);
        data.setAbilityCooldown(800);
        AbilityActivator.syncToClient(player, data);
        player.sendSystemMessage(AbilityCommon.msg("Zone left — Cooldown 40s.", ChatFormatting.GRAY));
    }

    private static void spawnZoneBorderParticles(ServerLevel level, double cx, double cy, double cz) {
        double h = ZONE_HALF, top = cy + h * 2;
        for (double t = 0; t <= 1; t += 0.25) {
            double y = cy + t * h * 2;
            for (int sx : new int[] { -1, 1 })
                for (int sz : new int[] { -1, 1 })
                    level.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.3f, 0.3f), 0.6f), cx + sx * h, y,
                            cz + sz * h, 1, 0, 0, 0, 0);
        }
        for (double t = -1; t <= 1; t += 0.5) {
            for (double[] ry : new double[][] { { cy }, { top } }) {
                level.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.3f, 0.3f), 0.6f), cx + t * h, ry[0],
                        cz - h, 1, 0, 0, 0, 0);
                level.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.3f, 0.3f), 0.6f), cx + t * h, ry[0],
                        cz + h, 1, 0, 0, 0, 0);
                level.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.3f, 0.3f), 0.6f), cx - h, ry[0],
                        cz + t * h, 1, 0, 0, 0, 0);
                level.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.3f, 0.3f), 0.6f), cx + h, ry[0],
                        cz + t * h, 1, 0, 0, 0, 0);
            }
        }
    }

    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        player.getPersistentData().putBoolean(NBT_CHARGING, true);
        player.getPersistentData().putInt(NBT_CHARGE_TICK, 60);
        player.getPersistentData().putBoolean(NBT_BULLDOZE, false);
        player.getPersistentData().putInt(NBT_BULLDOZE_TICK, 0);
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 70, 127));
        player.addEffect(AbilityCommon.fx(MobEffects.WEAKNESS, 70, 10));
        Vec3 look = player.getLookAngle();
        player.getPersistentData().putDouble("occka_brute_run_dx", look.x);
        player.getPersistentData().putDouble("occka_brute_run_dz", look.z);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 10, 0.4, 0.4,
                0.4, 0.03);
        player.sendSystemMessage(
                AbilityCommon.msg("BULLDOZER CHARGING... 3s!", ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    public static void tickUlt(ServerPlayer player, ServerLevel level,
            com.occka.occkapowers.ability.PlayerPowerData data) {
        if (player.getPersistentData().getBoolean(NBT_CHARGING)) {
            int chargeTicks = player.getPersistentData().getInt(NBT_CHARGE_TICK) - 1;
            player.getPersistentData().putInt(NBT_CHARGE_TICK, chargeTicks);
            player.setDeltaMovement(0, Math.max(player.getDeltaMovement().y, -0.15), 0);
            player.hurtMarked = true;

            float progress = (60 - chargeTicks) / 60f;
            int count = 2 + (int) (progress * 8);
            for (int i = 0; i < count; i++) {
                double a = Math.random() * Math.PI * 2;
                double r = Math.random() * 0.8;
                level.sendParticles(new DustParticleOptions(new Vector3f(0.9f, 0.2f, 0.05f), 1.0f + progress),
                        player.getX() + r * Math.cos(a),
                        player.getY() + Math.random() * 2.5,
                        player.getZ() + r * Math.sin(a), 1, 0, 0, 0, 0);
            }

            if (chargeTicks <= 0) {
                player.getPersistentData().putBoolean(NBT_CHARGING, false);
                player.getPersistentData().putBoolean(NBT_BULLDOZE, true);
                player.getPersistentData().putInt(NBT_BULLDOZE_TICK, 80);

                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                player.removeEffect(MobEffects.WEAKNESS);
                player.addEffect(AbilityCommon.fx(MobEffects.DAMAGE_RESISTANCE, 180, 3));

                double dx = player.getPersistentData().getDouble("occka_brute_run_dx");
                double dz = player.getPersistentData().getDouble("occka_brute_run_dz");
                Vec3 dir = new Vec3(dx, 0, dz).normalize();
                breakBlocksInFront(player, level, dir);
                player.setDeltaMovement(dir.x * 1.6, 0.15, dir.z * 1.6);
                player.hurtMarked = true;

                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        player.getX(), player.getY(), player.getZ(), 2, 0.2, 0, 0.2, 0.05);
                level.sendParticles(ParticleTypes.FLASH,
                        player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);

                player.sendSystemMessage(AbilityCommon.msg("BULLDOZER! 8s!", ChatFormatting.RED, ChatFormatting.BOLD));
            }
            return;
        }
        if (!player.getPersistentData().getBoolean(NBT_BULLDOZE))
            return;
        int runTicks = player.getPersistentData().getInt(NBT_BULLDOZE_TICK);
        if (runTicks <= 0) {
            endBulldozer(player, level, data);
            return;
        }
        player.getPersistentData().putInt(NBT_BULLDOZE_TICK, --runTicks);
        double dx = player.getPersistentData().getDouble("occka_brute_run_dx");
        double dz = player.getPersistentData().getDouble("occka_brute_run_dz");
        Vec3 dir = new Vec3(dx, 0, dz).normalize();
        double currentVY = player.getDeltaMovement().y;
        breakBlocksInFront(player, level, dir);
        player.setDeltaMovement(dir.x * 1.45, Math.max(currentVY, -0.35), dir.z * 1.45);
        player.hurtMarked = true;
        player.resetFallDistance();
        Vec3 front = player.position().add(dir.scale(1.5));
        AABB hitBox = new AABB(front.x - 1.5, player.getY() - 0.5, front.z - 1.5, front.x + 1.5, player.getY() + 3.5,
                front.z + 1.5);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, hitBox,
                e -> e != player && e.isAlive())) {
            int cd = entity.getPersistentData().getInt("brute_bulldoze_hit_cd");
            if (cd > 0) {
                entity.getPersistentData().putInt("brute_bulldoze_hit_cd", cd - 1);
                continue;
            }
            entity.hurt(player.damageSources().playerAttack(player), 8.0f);
            entity.setDeltaMovement(dir.x * 2.0, 0.5, dir.z * 2.0);
            entity.hurtMarked = true;
            entity.getPersistentData().putInt("brute_bulldoze_hit_cd", 10);
            level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1, entity.getZ(), 8, 0.3, 0.3, 0.3,
                    0.2);
        }
    }

    private static void endBulldozer(ServerPlayer player, ServerLevel level,
            com.occka.occkapowers.ability.PlayerPowerData data) {
        player.getPersistentData().putBoolean(NBT_BULLDOZE, false);
        player.setDeltaMovement(player.getDeltaMovement().x * 0.2, player.getDeltaMovement().y,
                player.getDeltaMovement().z * 0.2);
        player.hurtMarked = true;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 2, 0.3, 0,
                0.3, 0.04);
        data.setUltCooldown(data.getPowerType().getUltCooldown());
        AbilityActivator.syncToClient(player, data);
        player.sendSystemMessage(AbilityCommon.msg("Bulldozer stopped.", ChatFormatting.GRAY));
    }

    private static void breakBlocksInFront(ServerPlayer player, ServerLevel level, Vec3 dir) {
        Vec3 perp = new Vec3(-dir.z, 0, dir.x).normalize();
        for (double forward = 0.15; forward <= 1.8; forward += 0.35) {
            for (int side = -1; side <= 1; side++) {
                for (int up = 0; up <= 2; up++) {
                    Vec3 checkPos = player.position()
                            .add(dir.scale(forward))
                            .add(perp.scale(side * 0.95))
                            .add(0, up, 0);
                    BlockPos bp = BlockPos.containing(checkPos);
                    BlockState state = level.getBlockState(bp);
                    if (state.isAir() || state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)
                            || state.getDestroySpeed(level, bp) < 0) {
                        continue;
                    }

                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5,
                            4, 0.2, 0.2, 0.2, 0.1);
                    level.removeBlock(bp, false);
                }
            }
        }
    }

    private static void breakNearbyBlocks(ServerPlayer player, ServerLevel level, Vec3 pos, int radius) {
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius)
                        continue;
                    BlockPos bp = BlockPos.containing(pos.x + dx, pos.y + dy, pos.z + dz);
                    BlockState state = level.getBlockState(bp);
                    if (state.isAir() || state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER))
                        continue;
                    float hardness = state.getDestroySpeed(level, bp);
                    if (hardness < 0 || hardness > 1.5f)
                        continue;
                    level.removeBlock(bp, false);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), bp.getX() + 0.5,
                            bp.getY() + 0.5, bp.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.1);
                }
    }
    //
}