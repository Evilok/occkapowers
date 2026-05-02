package com.occka.occkapowers.network;

import com.occka.occkapowers.client.ClientFormData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Syncs the admin-assigned form (mob skin) for a player to all clients.
 * formId = "" means form removed.
 */
public class PacketSyncPlayerForm {
    private final UUID playerId;
    private final String formId;

    public PacketSyncPlayerForm(UUID playerId, String formId) {
        this.playerId = playerId;
        this.formId = formId;
    }

    public static void encode(PacketSyncPlayerForm msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeUtf(msg.formId);
    }

    public static PacketSyncPlayerForm decode(FriendlyByteBuf buf) {
        return new PacketSyncPlayerForm(buf.readUUID(), buf.readUtf());
    }

    public static void handle(PacketSyncPlayerForm msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientFormData.set(msg.playerId, msg.formId)));
        ctx.get().setPacketHandled(true);
    }
}
