package com.occka.occkapowers.form;

import com.occka.occkapowers.network.NetworkHandler;
import com.occka.occkapowers.network.PacketSyncPlayerForm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class PlayerFormSync {

    private PlayerFormSync() {}

    public static void syncToTrackingAndSelf(ServerPlayer player) {
        syncToTrackingAndSelf(player, PlayerFormData.getForm(player));
    }

    public static void clearForTrackingAndSelf(ServerPlayer player) {
        syncToTrackingAndSelf(player, "");
    }

    public static void syncVisibleFormsTo(ServerPlayer receiver) {
        if (receiver.getServer() == null) {
            return;
        }

        for (ServerPlayer player : receiver.getServer().getPlayerList().getPlayers()) {
            String formId = PlayerFormData.getForm(player);
            if (!formId.isEmpty()) {
                syncTo(receiver, player, formId);
            }
        }
    }

    public static void syncTrackedPlayerTo(ServerPlayer receiver, ServerPlayer tracked) {
        String formId = PlayerFormData.getForm(tracked);
        if (!formId.isEmpty()) {
            syncTo(receiver, tracked, formId);
        }
    }

    private static void syncToTrackingAndSelf(ServerPlayer player, String formId) {
        NetworkHandler.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new PacketSyncPlayerForm(player.getUUID(), formId));
    }

    private static void syncTo(ServerPlayer receiver, ServerPlayer formOwner, String formId) {
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> receiver),
                new PacketSyncPlayerForm(formOwner.getUUID(), formId));
    }
}
