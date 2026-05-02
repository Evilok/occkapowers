package com.occka.occkapowers.network;

import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.ability.PowerType;
import com.occka.occkapowers.client.ClientPowerData;
import com.occka.occkapowers.event.CreeperAbility;
import com.occka.occkapowers.event.SoulReaperAbility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncPowerData {
        private final String powerType;
        private final int shiftCd, abilityCd, ultCd, shiftMaxCd, abilityMaxCd, ultMaxCd;
        private final boolean abilityUnlocked, ultUnlocked, fireUltActive;
        private final int abilityCharges, abilityMaxCharges, abilityChargeCd, abilityChargeCdMax;
        private final int ultCharges, ultMaxCharges, ultChargeCd, ultChargeCdMax;
        private final int shiftCharges, shiftMaxCharges, shiftChargeCd, shiftChargeCdMax;
        private final int madness;
        private final int soulCharge;
        private final int creeperCharge;

        public PacketSyncPowerData(PlayerPowerData data) {
                this(data, 0, 0, 0);
        }

        public PacketSyncPowerData(ServerPlayer player, PlayerPowerData data) {
                this(data,
                                data.getPowerType() == PowerType.MERC
                                                ? player.getPersistentData().getInt("occka_merc_madness")
                                                : 0,
                                data.getPowerType() == PowerType.SOUL_REAPER
                                                ? player.getPersistentData().getInt(SoulReaperAbility.NBT_SOUL_CHARGE)
                                                : 0,
                                data.getPowerType() == PowerType.CREEPER
                                                ? Math.round(player.getPersistentData()
                                                                .getInt(CreeperAbility.NBT_CHARGE)
                                                                * 100.0f / CreeperAbility.CHARGE_TICKS_MAX)
                                                : 0);
        }

        private PacketSyncPowerData(PlayerPowerData data, int madness, int soulCharge, int creeperCharge) {
                this.powerType = data.getPowerType().getId();
                this.shiftCd = data.getShiftCooldown();
                this.abilityCd = data.getAbilityCooldown();
                this.ultCd = data.getUltCooldown();
                this.shiftMaxCd = data.getPowerType().getShiftCooldown();
                this.abilityMaxCd = data.getAbilityMaxCdOverride() > 0
                                ? data.getAbilityMaxCdOverride()
                                : data.getPowerType().getAbilityCooldown();
                this.ultMaxCd = data.getPowerType().getUltCooldown();
                this.abilityUnlocked = data.isAbilityUnlocked();
                this.ultUnlocked = data.isUltUnlocked();
                this.fireUltActive = data.isFireUltActive();
                this.abilityCharges = data.getAbilityCharges();
                this.abilityMaxCharges = data.getAbilityMaxCharges();
                this.abilityChargeCd = data.getAbilityChargeCd();
                this.abilityChargeCdMax = data.getAbilityChargeCdMax();
                this.ultCharges = data.getUltCharges();
                this.ultMaxCharges = data.getUltMaxCharges();
                this.ultChargeCd = data.getUltChargeCd();
                this.ultChargeCdMax = data.getUltChargeCdMax();
                this.shiftCharges = data.getShiftCharges();
                this.shiftMaxCharges = data.getShiftMaxCharges();
                this.shiftChargeCd = data.getShiftChargeCd();
                this.shiftChargeCdMax = data.getShiftChargeCdMax();
                this.madness = Math.max(0, Math.min(100, madness));
                this.soulCharge = Math.max(0, Math.min(SoulReaperAbility.SOUL_MAX, soulCharge));
                this.creeperCharge = Math.max(0, Math.min(100, creeperCharge));
        }

        private PacketSyncPowerData(String pt, int sc, int ac, int uc, int sm, int am, int um,
                        boolean au, boolean uu, boolean fua,
                        int abilityCharges, int abilityMaxCharges, int abilityChargeCd, int abilityChargeCdMax,
                        int ultCharges, int ultMaxCharges, int ultChargeCd, int ultChargeCdMax,
                        int shiftCharges, int shiftMaxCharges, int shiftChargeCd, int shiftChargeCdMax,
                        int madness, int soulCharge, int creeperCharge) {
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
                this.abilityCharges = abilityCharges;
                this.abilityMaxCharges = abilityMaxCharges;
                this.abilityChargeCd = abilityChargeCd;
                this.abilityChargeCdMax = abilityChargeCdMax;
                this.ultCharges = ultCharges;
                this.ultMaxCharges = ultMaxCharges;
                this.ultChargeCd = ultChargeCd;
                this.ultChargeCdMax = ultChargeCdMax;
                this.shiftCharges = shiftCharges;
                this.shiftMaxCharges = shiftMaxCharges;
                this.shiftChargeCd = shiftChargeCd;
                this.shiftChargeCdMax = shiftChargeCdMax;
                this.madness = madness;
                this.soulCharge = soulCharge;
                this.creeperCharge = creeperCharge;
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
                buf.writeInt(msg.abilityCharges);
                buf.writeInt(msg.abilityMaxCharges);
                buf.writeInt(msg.abilityChargeCd);
                buf.writeInt(msg.abilityChargeCdMax);
                buf.writeInt(msg.ultCharges);
                buf.writeInt(msg.ultMaxCharges);
                buf.writeInt(msg.ultChargeCd);
                buf.writeInt(msg.ultChargeCdMax);
                buf.writeInt(msg.shiftCharges);
                buf.writeInt(msg.shiftMaxCharges);
                buf.writeInt(msg.shiftChargeCd);
                buf.writeInt(msg.shiftChargeCdMax);
                buf.writeInt(msg.madness);
                buf.writeInt(msg.soulCharge);
                buf.writeInt(msg.creeperCharge);
        }

        public static PacketSyncPowerData decode(FriendlyByteBuf buf) {
                return new PacketSyncPowerData(
                                buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(),
                                buf.readInt(), buf.readInt(), buf.readInt(),
                                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                                buf.readInt(), buf.readInt(), buf.readInt()); // три readInt() в кінці
        }

        public static void handle(PacketSyncPowerData msg, Supplier<NetworkEvent.Context> ctx) {
                ctx.get().enqueueWork(() -> ClientPowerData.update(
                                PowerType.fromId(msg.powerType),
                                msg.shiftCd, msg.abilityCd, msg.ultCd,
                                msg.shiftMaxCd, msg.abilityMaxCd, msg.ultMaxCd,
                                msg.abilityUnlocked, msg.ultUnlocked, msg.fireUltActive,
                                msg.abilityCharges, msg.abilityMaxCharges, msg.abilityChargeCd, msg.abilityChargeCdMax,
                                msg.ultCharges, msg.ultMaxCharges, msg.ultChargeCd, msg.ultChargeCdMax,
                                msg.shiftCharges, msg.shiftMaxCharges, msg.shiftChargeCd, msg.shiftChargeCdMax,
                                msg.madness, msg.soulCharge, msg.creeperCharge));
                ctx.get().setPacketHandled(true);
        }
}
