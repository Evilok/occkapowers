package com.occka.occkapowers.network;

import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.ability.PowerType;
import com.occka.occkapowers.client.ClientPowerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncPowerData {
    private final String powerType;
    private final int shiftCd, abilityCd, ultCd, shiftMaxCd, abilityMaxCd, ultMaxCd;
    private final boolean abilityUnlocked, ultUnlocked, fireUltActive;

    public PacketSyncPowerData(PlayerPowerData data) {
        this.powerType = data.getPowerType().getId();
        this.shiftCd = data.getShiftCooldown();
        this.abilityCd = data.getAbilityCooldown();
        this.ultCd = data.getUltCooldown();
        this.shiftMaxCd = data.getPowerType().getShiftCooldown();
        this.abilityMaxCd = data.getPowerType().getAbilityCooldown();
        this.ultMaxCd = data.getPowerType().getUltCooldown();
        this.abilityUnlocked = data.isAbilityUnlocked();
        this.ultUnlocked = data.isUltUnlocked();
        this.fireUltActive = data.isFireUltActive();
    }

    private PacketSyncPowerData(String pt, int sc, int ac, int uc, int sm, int am, int um, boolean au, boolean uu,
            boolean fua) {
        powerType = pt;
        shiftCd = sc;
        abilityCd = ac;
        ultCd = uc;
        shiftMaxCd = sm;
        abilityMaxCd = am;
        ultMaxCd = um;
        abilityUnlocked = au;
        ultUnlocked = uu;
        fireUltActive = fua;
    }

    public static void encode(PacketSyncPowerData msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.powerType);
        buf.writeInt(msg.shiftCd);
        buf.writeInt(msg.abilityCd);
        buf.writeInt(msg.ultCd);
        buf.writeInt(msg.shiftMaxCd);
        buf.writeInt(msg.abilityMaxCd);
        buf.writeInt(msg.ultMaxCd);
        buf.writeBoolean(msg.abilityUnlocked);
        buf.writeBoolean(msg.ultUnlocked);
        buf.writeBoolean(msg.fireUltActive);
    }

    public static PacketSyncPowerData decode(FriendlyByteBuf buf) {
        return new PacketSyncPowerData(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(PacketSyncPowerData msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPowerData.update(
                PowerType.fromId(msg.powerType),
                msg.shiftCd, msg.abilityCd, msg.ultCd,
                msg.shiftMaxCd, msg.abilityMaxCd, msg.ultMaxCd,
                msg.abilityUnlocked, msg.ultUnlocked, msg.fireUltActive));
        ctx.get().setPacketHandled(true);
    }
}
