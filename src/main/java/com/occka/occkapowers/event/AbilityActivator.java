package com.occka.occkapowers.event;

import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.ability.PowerType;
import com.occka.occkapowers.network.NetworkHandler;
import com.occka.occkapowers.network.PacketSyncPowerData;
import com.occka.occkapowers.registry.ModCapabilities;
import com.occka.occkapowers.unlock.UnlockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public class AbilityActivator {

    public static void activate(ServerPlayer player, int slot) {
        player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
            PowerType type = data.getPowerType();
            if (type == PowerType.NONE) {
                player.sendSystemMessage(AbilityCommon.msg("You have no class assigned!", ChatFormatting.RED));
                return;
            }

            switch (slot) {
                case 0 -> ShiftAbilityActivator.activateShift(player, data, type);
                case 1 -> activateAbility(player, data, type);
                case 2 -> activateUlt(player, data, type);
                default -> {
                }
            }
            syncToClient(player, data);
        });
    }

    public static void activateShiftHeld(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
            PowerType type = data.getPowerType();
            if (type != PowerType.NONE) {
                ShiftAbilityActivator.activateShift(player, data, type);
            }
        });
    }

    public static void syncToClient(ServerPlayer player, PlayerPowerData data) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncPowerData(data));
    }

    private static void activateAbility(ServerPlayer player, PlayerPowerData data, PowerType type) {
        if (!data.isAbilityUnlocked()) {
            if (UnlockHelper.tryConsumeAbility(player, type)) {
                data.setAbilityUnlocked(true);
                player.sendSystemMessage(AbilityCommon.msg("Ability unlocked!", ChatFormatting.GREEN, ChatFormatting.BOLD));
            } else {
                player.sendSystemMessage(AbilityCommon.msg("Need to unlock ability! Cost: " + type.getAbilityUnlockHint(), ChatFormatting.YELLOW));
            }
            syncToClient(player, data);
            return;
        }

        if (data.getAbilityCooldown() > 0) {
            player.sendSystemMessage(AbilityCommon.msg("Ability on cooldown: " + String.format("%.1f", data.getAbilityCooldown() / 20f) + "s", ChatFormatting.YELLOW));
            return;
        }

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        switch (type) {
            case FIRE -> AbilityActions.activateFireAbility(player, level);
            case AIR -> AbilityActions.dashForward(player, level, 15);
            case SUPERFORCE -> SuperforceAbility.activateShift(player, level);
            case WATER -> AbilityActions.spawnAquaticMobs(player, level);
            case ICE -> AbilityActions.cageNearestEnemy(player, level);
            case LIGHTNING -> AbilityActions.strikeLightningAtLookBlock(player, level);
            case ADEPT -> AdeptAbility.activateAbility(player, level);
            case CHAOS -> ChaosAbility.activateAbility(player, level);
            case LASER -> AbilityActions.fireLaserAbility(player, level);
            case GEO -> AbilityActions.geoShockwave(player, level, 10);
            case VOID -> AbilityActions.voidBlind(player, level, 10);
            case LIGHT -> AbilityActions.activateLightAbility(player, level);
            case GRAVITY -> AbilityActions.gravityVortex(player, level);
            case ECHO -> AbilityActions.echoSwap(player, level);
        }

        data.setAbilityCooldown(type.getAbilityCooldown());
        syncToClient(player, data);
    }

    private static void activateUlt(ServerPlayer player, PlayerPowerData data, PowerType type) {
        if (!data.isUltUnlocked()) {
            if (UnlockHelper.tryConsumeUlt(player, type)) {
                data.setUltUnlocked(true);
                player.sendSystemMessage(AbilityCommon.msg("Ultimate unlocked!", ChatFormatting.GOLD, ChatFormatting.BOLD));
            } else {
                player.sendSystemMessage(AbilityCommon.msg("Need to unlock ultimate! Cost: " + type.getUltUnlockHint(), ChatFormatting.YELLOW));
            }
            syncToClient(player, data);
            return;
        }

        if (data.getUltCooldown() > 0) {
            player.sendSystemMessage(AbilityCommon.msg("Ult on cooldown: " + String.format("%.1f", data.getUltCooldown() / 20f) + "s", ChatFormatting.RED));
            return;
        }

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        switch (type) {
            case FIRE -> AbilityActions.startFireUlt(player, level, data);
            case AIR -> AbilityActions.activateAirUlt(player, level);
            case WATER -> AbilityActions.activateWaterUlt(player, level);
            case ICE -> AbilityActions.iceUltFreeze(player, level);
            case LIGHTNING -> AbilityActions.lightningStrikeAll(player, level, 40);
            case ADEPT -> AdeptAbility.activateUlt(player, level);
            case LASER -> AbilityActions.tntAirstrike(player, level);
            case SUPERFORCE -> SuperforceAbility.activateUlt(player, level);
            case GEO -> {
                GeoOrbitHandler.startOrbit(player, level);
                data.setUltCooldown(type.getUltCooldown());
                syncToClient(player, data);
                return;
            }
            case CHAOS -> ChaosAbility.activateUlt(player, level);
            case VOID -> AbilityActions.voidUlt(player, level, 20);
            case LIGHT -> AbilityActions.lightUlt(player, level);
            case GRAVITY -> AbilityActions.gravityUlt(player, level);
            case ECHO -> AbilityActions.echoUlt(player, level);
        }

        data.setUltCooldown(type.getUltCooldown());
        syncToClient(player, data);
    }

    public static void fireUltShoot(ServerPlayer player, PlayerPowerData data) {
        AbilityActions.fireUltShoot(player, data);
    }

    public static void endFireUlt(ServerPlayer player, PlayerPowerData data) {
        AbilityActions.endFireUlt(player, data);
    }

    public static void tickLaserUlt(ServerPlayer player, PlayerPowerData data, ServerLevel level) {
        AbilityActions.tickLaserUlt(player, data, level);
    }

    public static java.util.List<net.minecraft.world.entity.LivingEntity> getNearbyEnemies(ServerPlayer player, double radius) {
        return AbilityActions.getNearbyEnemies(player, radius);
    }
}
