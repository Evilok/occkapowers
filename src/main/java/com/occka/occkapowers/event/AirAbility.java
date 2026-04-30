package com.occka.occkapowers.event;

import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.ability.PowerType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import com.occka.occkapowers.event.AbilityCommon;

public final class AirAbility {

    private AirAbility() {
    }


    public static void activateShift(ServerPlayer player, ServerLevel level) {

        // Levitate + cloud particles under feet
        player.addEffect(AbilityCommon.fx(MobEffects.LEVITATION, 25, 3));
        player.addEffect(AbilityCommon.fx(MobEffects.SLOW_FALLING, 25, 0));
        for (int i = 0; i < 12; i++) {
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + (Math.random() - 0.5) * 0.5,
                    player.getY() - 0.5,
                    player.getZ() + (Math.random() - 0.5) * 0.5,
                    5, 0.5, 0.1, 0.5, 0.01);
        }
    }

    public static void activateAbility(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        dashForward(player, level);
    }


    private static void dashForward(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        Vec3 vel = look.scale(2.8);
        player.setDeltaMovement(vel);
        player.hurtMarked = true;
        player.addEffect(AbilityCommon.fx(MobEffects.SLOW_FALLING, 100, 0));

        Vec3 start = player.position();
        for (int i = 0; i < 25; i++) {
            Vec3 behind = start.subtract(look.scale(i * 0.35));
            level.sendParticles(ParticleTypes.CLOUD,
                    behind.x + (Math.random() - 0.5) * 0.6,
                    behind.y + 0.5 + Math.random() * 1.5,
                    behind.z + (Math.random() - 0.5) * 0.6,
                    2, 0.1, 0.1, 0.1, 0.03);
        }
        level.sendParticles(ParticleTypes.POOF, start.x, start.y + 1, start.z, 30, 0.6, 0.6, 0.6, 0.15);
        level.sendParticles(ParticleTypes.CLOUD, start.x, start.y + 1, start.z, 20, 0.5, 0.5, 0.5, 0.08);
        player.sendSystemMessage(AbilityCommon.msg("Dash!", ChatFormatting.AQUA));
    }

    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        levitateEnemies(player, 15, 21, 20);
        for (int i = 0; i < 80; i++) {
            double a = Math.random() * Math.PI * 2;
            double p = (Math.random() - 0.5) * Math.PI;
            double r = Math.random() * 20;
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + r * Math.cos(a) * Math.cos(p),
                    player.getY() + 2 + r * Math.sin(p),
                    player.getZ() + r * Math.sin(a) * Math.cos(p),
                    1, 0, 0, 0, 0.05);
        }
        player.addEffect(AbilityCommon.fx(MobEffects.SLOW_FALLING, 100, 0));
        player.sendSystemMessage(AbilityCommon.msg("AIR BLAST!", ChatFormatting.AQUA, ChatFormatting.BOLD));
    }

    private static void levitateEnemies(ServerPlayer player, double radius, int amp, int dur) {
        for (var e : AbilityCommon.getNearbyEnemies(player, radius))
            e.addEffect(AbilityCommon.fx(MobEffects.LEVITATION, dur, amp));
    }
}
