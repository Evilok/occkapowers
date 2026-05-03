package com.occka.occkapowers.network;

import com.occka.occkapowers.client.ClientAlignmentData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncAlignment {

    private final String alignment;

    public PacketSyncAlignment(String alignment) {
        this.alignment = alignment;
    }

    public static void encode(PacketSyncAlignment msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.alignment);
    }

    public static PacketSyncAlignment decode(FriendlyByteBuf buf) {
        return new PacketSyncAlignment(buf.readUtf());
    }

    public static void handle(PacketSyncAlignment msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientAlignmentData.set(msg.alignment))
        );
        ctx.get().setPacketHandled(true);
    }
}