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
    private final boolean abilityUnlocked, ultUnlocked, fireUltActive, laserUltActive;
    private final int laserUltTicks, laserUltMaxTicks;

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
        this.laserUltActive = data.isLaserUltActive();
        this.laserUltTicks = data.getLaserUltTicks();
        this.laserUltMaxTicks = data.getLaserUltMaxTicks();
    }

    private PacketSyncPowerData(String pt, int sc, int ac, int uc, int sm, int am, int um, boolean au, boolean uu,
            boolean fua, boolean lua, int lut, int lumt) {
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
        laserUltActive = lua;
        laserUltTicks = lut;
        laserUltMaxTicks = lumt;
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
        buf.writeBoolean(msg.laserUltActive);
        buf.writeInt(msg.laserUltTicks);
        buf.writeInt(msg.laserUltMaxTicks);
    }

    public static PacketSyncPowerData decode(FriendlyByteBuf buf) {
        return new PacketSyncPowerData(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readInt());
    }

    public static void handle(PacketSyncPowerData msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPowerData.update(
                PowerType.fromId(msg.powerType),
                msg.shiftCd, msg.abilityCd, msg.ultCd,
                msg.shiftMaxCd, msg.abilityMaxCd, msg.ultMaxCd,
                msg.abilityUnlocked, msg.ultUnlocked, msg.fireUltActive,
                msg.laserUltActive, msg.laserUltTicks, msg.laserUltMaxTicks));
        ctx.get().setPacketHandled(true);
    }
}
