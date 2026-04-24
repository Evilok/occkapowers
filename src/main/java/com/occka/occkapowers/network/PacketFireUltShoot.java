package com.occka.occkapowers.network;

import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.event.AbilityActivator;
import com.occka.occkapowers.registry.ModCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Sent when player LMB clicks during fire ult
public class PacketFireUltShoot {
    public PacketFireUltShoot() {
    }

    public static void encode(PacketFireUltShoot msg, FriendlyByteBuf buf) {
    }

    public static PacketFireUltShoot decode(FriendlyByteBuf buf) {
        return new PacketFireUltShoot();
    }

    public static void handle(PacketFireUltShoot msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
                    AbilityActivator.fireUltShoot(player, data);
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
