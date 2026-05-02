package com.occka.occkapowers.network;

import com.occka.occkapowers.ability.PowerType;
import com.occka.occkapowers.client.ClientPlayerPowerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class PacketSyncPlayerPowerType {
    private final UUID playerId;
    private final String powerId;

    public PacketSyncPlayerPowerType(UUID playerId, PowerType powerType) {
        this(playerId, powerType.getId());
    }

    public PacketSyncPlayerPowerType(UUID playerId, String powerId) {
        this.playerId = playerId;
        this.powerId = powerId;
    }

    public static void encode(PacketSyncPlayerPowerType msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeUtf(msg.powerId);
    }

    public static PacketSyncPlayerPowerType decode(FriendlyByteBuf buf) {
        return new PacketSyncPlayerPowerType(buf.readUUID(), buf.readUtf());
    }

    public static void handle(PacketSyncPlayerPowerType msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPlayerPowerData.set(msg.playerId, PowerType.fromId(msg.powerId))));
        ctx.get().setPacketHandled(true);
    }
}
