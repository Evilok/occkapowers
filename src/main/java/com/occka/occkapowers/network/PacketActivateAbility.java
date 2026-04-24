package com.occka.occkapowers.network;

import com.occka.occkapowers.ability.PowerType;
import com.occka.occkapowers.event.AbilityActivator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketActivateAbility {
    // type: 0 = shift, 1 = ability, 2 = ult
    private final int abilitySlot;

    public PacketActivateAbility(int slot) {
        this.abilitySlot = slot;
    }

    public static void encode(PacketActivateAbility msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.abilitySlot);
    }

    public static PacketActivateAbility decode(FriendlyByteBuf buf) {
        return new PacketActivateAbility(buf.readInt());
    }

    public static void handle(PacketActivateAbility msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                AbilityActivator.activate(player, msg.abilitySlot);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
