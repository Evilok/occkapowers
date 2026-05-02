package com.occka.occkapowers.network;

import com.occka.occkapowers.client.ClientAdeptPandaForms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class PacketSyncAdeptPandaForm {
    private final UUID playerId;
    private final boolean active;

    public PacketSyncAdeptPandaForm(UUID playerId, boolean active) {
        this.playerId = playerId;
        this.active = active;
    }

    public static void encode(PacketSyncAdeptPandaForm msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeBoolean(msg.active);
    }

    public static PacketSyncAdeptPandaForm decode(FriendlyByteBuf buf) {
        return new PacketSyncAdeptPandaForm(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(PacketSyncAdeptPandaForm msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientAdeptPandaForms.set(msg.playerId, msg.active)));
        ctx.get().setPacketHandled(true);
    }
}
