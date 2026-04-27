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
                case 0 -> activateShift(player, data, type);
                case 1 -> activateAbility(player, data, type);
                case 2 -> activateUlt(player, data, type);
            }
            syncToClient(player, data);
        });
    }

    /** Called every 20 ticks (1s) while shift key held */
    public static void activateShiftHeld(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
            activateShift(player, data, data.getPowerType());
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
            case FIRE     -> FireAbility.activateShift(player, level);
            case AIR      -> AirAbility.activateShift(player, level);
            case WATER    -> WaterAbility.activateShift(player, level);
            case ICE      -> IceAbility.activateShift(player, level);
            case LIGHTNING -> LightningAbility.activateShift(player, level);
            case LASER    -> LaserAbility.activateShift(player, level);
            case GEO      -> GeoAbility.activateShift(player, level);
            case VOID     -> VoidAbility.activateShift(player, level);
            case LIGHT    -> LightAbility.activateShift(player, level);
            case GRAVITY  -> GravityAbility.activateShift(player, level);
            case ECHO     -> EchoAbility.activateShift(player, level, data);
            case CHAOS    -> ChaosAbility.activateShift(player, level);
            case SUPERFORCE -> SuperforceAbility.activateAbility(player, level); // punch (no cd)
            case ADEPT    -> AdeptAbility.activateShift(player, level);
            default -> {}
        }
    }

    // ===== ABILITY =====
    private static void activateAbility(ServerPlayer player, PlayerPowerData data, PowerType type) {
        if (!data.isAbilityUnlocked()) {
            if (UnlockHelper.tryConsumeAbility(player, type)) {
                data.setAbilityUnlocked(true);
                player.sendSystemMessage(AbilityCommon.msg("Ability unlocked!", ChatFormatting.GREEN, ChatFormatting.BOLD));
            } else {
                player.sendSystemMessage(
                        AbilityCommon.msg("Need to unlock ability! Cost: " + type.getAbilityUnlockHint(), ChatFormatting.YELLOW));
            }
            syncToClient(player, data);
            return;
        }
        if (data.getAbilityCooldown() > 0) {
            player.sendSystemMessage(
                    AbilityCommon.msg("Ability on cooldown: " + String.format("%.1f", data.getAbilityCooldown() / 20f) + "s",
                            ChatFormatting.YELLOW));
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) return;

        switch (type) {
            case FIRE      -> FireAbility.activateAbility(player, level);
            case AIR       -> AirAbility.activateAbility(player, level);
            case WATER     -> WaterAbility.activateAbility(player, level);
            case ICE       -> IceAbility.activateAbility(player, level);
            case LIGHTNING -> LightningAbility.activateAbility(player, level);
            case LASER     -> LaserAbility.activateAbility(player, level);
            case GEO       -> GeoAbility.activateAbility(player, level);
            case VOID      -> VoidAbility.activateAbility(player, level);
            case LIGHT     -> LightAbility.activateAbility(player, level);
            case GRAVITY   -> GravityAbility.activateAbility(player, level);
            case ECHO      -> EchoAbility.activateAbility(player, level);
            case CHAOS     -> ChaosAbility.activateAbility(player, level);
            case SUPERFORCE -> SuperforceAbility.activateShift(player, level); // ground slam (cd)
            case ADEPT     -> AdeptAbility.activateAbility(player, level);
            default -> {}
        }

        data.setAbilityCooldown(type.getAbilityCooldown());
        syncToClient(player, data);
    }

    // ===== ULT =====
    private static void activateUlt(ServerPlayer player, PlayerPowerData data, PowerType type) {
        if (!data.isUltUnlocked()) {
            if (UnlockHelper.tryConsumeUlt(player, type)) {
                data.setUltUnlocked(true);
                player.sendSystemMessage(AbilityCommon.msg("Ultimate unlocked!", ChatFormatting.GOLD, ChatFormatting.BOLD));
            } else {
                player.sendSystemMessage(
                        AbilityCommon.msg("Need to unlock ultimate! Cost: " + type.getUltUnlockHint(), ChatFormatting.YELLOW));
            }
            syncToClient(player, data);
            return;
        }
        if (data.getUltCooldown() > 0) {
            player.sendSystemMessage(
                    AbilityCommon.msg("Ult on cooldown: " + String.format("%.1f", data.getUltCooldown() / 20f) + "s",
                            ChatFormatting.RED));
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) return;

        switch (type) {
            case FIRE      -> FireAbility.startUlt(player, level, data);
            case AIR       -> AirAbility.activateUlt(player, level);
            case WATER     -> WaterAbility.activateUlt(player, level);
            case ICE       -> IceAbility.activateUlt(player, level);
            case LIGHTNING -> LightningAbility.activateUlt(player, level, 40);
            case LASER     -> LaserAbility.activateUlt(player, level);
            case GEO       -> {
                GeoAbility.activateUlt(player, level);
                // КД ставим сразу — не ждём завершения орбиты
                data.setUltCooldown(type.getUltCooldown());
                syncToClient(player, data);
                return; // чтобы не дублировать setUltCooldown ниже
            }
            case VOID      -> VoidAbility.activateUlt(player, level);
            case LIGHT     -> LightAbility.activateUlt(player, level);
            case GRAVITY   -> GravityAbility.activateUlt(player, level);
            case ECHO      -> EchoAbility.activateUlt(player, level);
            case CHAOS     -> ChaosAbility.activateUlt(player, level);
            case SUPERFORCE -> SuperforceAbility.activateUlt(player, level);
            case ADEPT     -> AdeptAbility.activateUlt(player, level);
            default -> {}
        }

        data.setUltCooldown(type.getUltCooldown());
        syncToClient(player, data);
    }

    // ===== FIRE ULT HELPERS (вызываются из AbilityEventHandler) =====

    public static void fireUltShoot(ServerPlayer player, PlayerPowerData data) {
        FireAbility.ultShoot(player, data);
    }

    public static void endFireUlt(ServerPlayer player, PlayerPowerData data) {
        FireAbility.endUlt(player, data);
    }
}
