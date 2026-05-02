package com.occka.occkapowers.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from client to server while laser ult is held (every tick)
 * and once when released (held = false).
 */
public class PacketLaserUltChannel {

    /** true = key still held this tick, false = key just released */
    private final boolean held;

    public PacketLaserUltChannel(boolean held) {
        this.held = held;
    }

    public static void encode(PacketLaserUltChannel msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.held);
    }

    public static PacketLaserUltChannel decode(FriendlyByteBuf buf) {
        return new PacketLaserUltChannel(buf.readBoolean());
    }

    public static void handle(PacketLaserUltChannel msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                if (msg.held) {
                    com.occka.occkapowers.event.LaserAbility.tickUltChannel(player, level);
                } else {
                    // Player released the key — stop channel and apply cooldown if was active
                    boolean wasActive = com.occka.occkapowers.event.LaserAbility.stopUltChannel(player, level);
                    if (wasActive) {
                        player.getCapability(com.occka.occkapowers.registry.ModCapabilities.PLAYER_POWER)
                                .ifPresent(data -> {
                                    data.setUltCooldown(
                                            com.occka.occkapowers.ability.PowerType.LASER.getUltCooldown());
                                    com.occka.occkapowers.event.AbilityActivator.syncToClient(player, data);
                                });
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
