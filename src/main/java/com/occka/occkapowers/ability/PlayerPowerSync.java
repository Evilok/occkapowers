package com.occka.occkapowers.ability;

import com.occka.occkapowers.network.NetworkHandler;
import com.occka.occkapowers.network.PacketSyncPlayerPowerType;
import com.occka.occkapowers.registry.ModCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class PlayerPowerSync {
    private PlayerPowerSync() {
    }

    public static void syncToTrackingAndSelf(ServerPlayer player, PlayerPowerData data) {
        NetworkHandler.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new PacketSyncPlayerPowerType(player.getUUID(), data.getPowerType()));
    }

    public static void syncVisiblePowersTo(ServerPlayer receiver) {
        if (receiver.getServer() == null) {
            return;
        }

        for (ServerPlayer player : receiver.getServer().getPlayerList().getPlayers()) {
            player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> syncTo(receiver, player, data));
        }
    }

    public static void syncTrackedPlayerTo(ServerPlayer receiver, ServerPlayer tracked) {
        tracked.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> syncTo(receiver, tracked, data));
    }

    private static void syncTo(ServerPlayer receiver, ServerPlayer powerOwner, PlayerPowerData data) {
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> receiver),
                new PacketSyncPlayerPowerType(powerOwner.getUUID(), data.getPowerType()));
    }
}
