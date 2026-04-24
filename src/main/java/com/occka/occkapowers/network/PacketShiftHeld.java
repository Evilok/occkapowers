package com.occka.occkapowers.network;

import com.occka.occkapowers.event.AbilityActivator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Sent every 20 ticks while shift key is held
public class PacketShiftHeld {
    public PacketShiftHeld() {
    }

    public static void encode(PacketShiftHeld msg, FriendlyByteBuf buf) {
    }

    public static PacketShiftHeld decode(FriendlyByteBuf buf) {
        return new PacketShiftHeld();
    }

    public static void handle(PacketShiftHeld msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null)
                AbilityActivator.activateShiftHeld(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
