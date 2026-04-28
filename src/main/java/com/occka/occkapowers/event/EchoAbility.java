package com.occka.occkapowers.event;

import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.registry.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class EchoAbility {
    private EchoAbility() {
    }

    // SHIFT (one-time, 30s cd): клон из стойки для брони + инвизибилити
    public static void activateShift(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        if (data.getShiftCooldown() > 0)
            return;

        ArmorStand clone = new ArmorStand(EntityType.ARMOR_STAND, level);
        clone.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        clone.setCustomName(
                net.minecraft.network.chat.Component.literal(player.getName().getString())
                        .withStyle(
                                player.getCapability(ModCapabilities.PLAYER_POWER)
                                        .map(d -> d.getPowerType().getColor())
                                        .orElse(ChatFormatting.WHITE)));
        clone.setCustomNameVisible(true);
        clone.setNoGravity(false);
        clone.getPersistentData().putString("occka_echo_clone", player.getUUID().toString());
        level.addFreshEntity(clone);

        player.addEffect(AbilityCommon.fx(MobEffects.INVISIBILITY, 400, 0)); // 20s
        data.setShiftCooldown(600); // 30s

        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 1, 0.5, 0.1);
        player.sendSystemMessage(AbilityCommon.msg("Echo Clone deployed!", ChatFormatting.GREEN));
    }

    // ABILITY: телепортация — меняемся позицией с ближайшей целью
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        LivingEntity target = null;
        double minD = Double.MAX_VALUE;

        // Сначала ищем ближайшего игрока
        AABB box = player.getBoundingBox().inflate(30);
        for (Player p : player.level().getEntitiesOfClass(Player.class, box, p -> p != player)) {
            double d = p.distanceTo(player);
            if (d < minD) {
                minD = d;
                target = p;
            }
        }
        // Если игроков нет — ищем любую сущность
        if (target == null) {
            for (LivingEntity e : AbilityCommon.getNearbyEnemies(player, 12)) {
                double d = e.distanceTo(player);
                if (d < minD) {
                    minD = d;
                    target = e;
                }
            }
        }
        if (target == null) {
            player.sendSystemMessage(AbilityCommon.msg("No targets!", ChatFormatting.RED));
            return;
        }

        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position();

        level.sendParticles(ParticleTypes.PORTAL,
                playerPos.x, playerPos.y + 1, playerPos.z, 30, 0.5, 1, 0.5, 0.15);
        level.sendParticles(ParticleTypes.PORTAL,
                targetPos.x, targetPos.y + 1, targetPos.z, 30, 0.5, 1, 0.5, 0.15);

        player.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        target.teleportTo(playerPos.x, playerPos.y, playerPos.z);

        player.sendSystemMessage(AbilityCommon.msg("Position Swap!", ChatFormatting.GREEN));
    }

    // ULT: слепим всех врагов + сам уходим в режим наблюдателя на 8 секунд
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        for (LivingEntity entity : AbilityCommon.getNearbyEnemies(player, 12)) {
            entity.addEffect(AbilityCommon.fx(MobEffects.BLINDNESS, 100, 0));
            level.sendParticles(ParticleTypes.PORTAL,
                    entity.getX(), entity.getY() + 1, entity.getZ(), 20, 0.5, 1, 0.5, 0.1);
        }

        player.setGameMode(GameType.SPECTATOR);
        player.getPersistentData().putInt("occka_echo_ult_ticks", 160); // 8s

        level.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1, player.getZ(), 60, 3, 3, 3, 0.1);
        player.sendSystemMessage(
                AbilityCommon.msg("Echo Phase: Spectator mode for 8s!", ChatFormatting.GREEN, ChatFormatting.BOLD));
    }
}
