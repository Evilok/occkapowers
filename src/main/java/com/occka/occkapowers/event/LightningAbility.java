package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class LightningAbility {
    private LightningAbility() {}

    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(50));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 strikePos = hit.getType() == HitResult.Type.MISS ? end : Vec3.atCenterOf(hit.getBlockPos());

        net.minecraft.world.entity.LightningBolt bolt = new net.minecraft.world.entity.LightningBolt(EntityType.LIGHTNING_BOLT, level);
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
        for (var entity : AbilityCommon.getNearbyEnemies(player, radius)) {
            net.minecraft.world.entity.LightningBolt bolt = new net.minecraft.world.entity.LightningBolt(EntityType.LIGHTNING_BOLT, level);
            bolt.moveTo(entity.position());
            bolt.setVisualOnly(false);
            level.addFreshEntity(bolt);
        }
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
}
