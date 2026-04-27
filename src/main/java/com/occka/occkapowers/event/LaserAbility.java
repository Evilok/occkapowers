package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.item.PrimedTnt;

public final class LaserAbility {
    private LaserAbility() {}

    // SHIFT (held): подсветка врагов + линия прицела
    public static void activateShift(ServerPlayer player, ServerLevel level) {
        AbilityCommon.getNearbyEnemies(player, 30)
                .forEach(e -> e.addEffect(AbilityCommon.fx(MobEffects.GLOWING, 25, 0)));

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (int i = 1; i <= 15; i++) {
            Vec3 p = eye.add(look.scale(i));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 2, 0.03, 0.03, 0.03, 0.01);
        }
    }

    // ABILITY: плотный лазерный луч, 18 урона магией
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        fireLaserBeam(player, level, 15);
    }

    private static void fireLaserBeam(ServerPlayer player, ServerLevel level, double length) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();

        for (LivingEntity entity : AbilityCommon.getNearbyEnemies(player, 12)) {
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

        for (double d = 0.3; d < length; d += 0.3) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            if (d % 1.5 < 0.3)
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        level.sendParticles(ParticleTypes.FLASH,
                start.x + dir.x, start.y + dir.y, start.z + dir.z, 1, 0, 0, 0, 0);
        player.sendSystemMessage(AbilityCommon.msg("Laser Beam!", ChatFormatting.RED));
    }

    // ULT: авиаудар ТНТ по точке прицела
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(60));
        BlockHitResult hit = level.clip(
                new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? end : Vec3.atCenterOf(hit.getBlockPos());

        for (int i = 0; i < 20; i++)
            level.sendParticles(ParticleTypes.CRIT,
                    target.x + (Math.random() - 0.5) * 2, target.y + i * 0.3,
                    target.z + (Math.random() - 0.5) * 2, 2, 0.2, 0.1, 0.2, 0.05);
        level.sendParticles(ParticleTypes.FLAME, target.x, target.y + 1, target.z, 20, 1, 2, 1, 0.1);

        int[][] pattern = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int[] offset : pattern) {
            PrimedTnt tnt = new PrimedTnt(
                    level, target.x + offset[0], target.y + 25, target.z + offset[1], player);
            tnt.setFuse(60 + level.random.nextInt(20));
            level.addFreshEntity(tnt);
        }
        player.sendSystemMessage(AbilityCommon.msg("AIRSTRIKE!", ChatFormatting.RED, ChatFormatting.BOLD));
    }
}
