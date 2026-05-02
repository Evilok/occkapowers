package com.occka.occkapowers.network;

import com.occka.occkapowers.OcckaPowers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
        private static final String PROTOCOL_VERSION = "6";
        public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
                        new ResourceLocation(OcckaPowers.MOD_ID, "main"),
                        () -> PROTOCOL_VERSION,
                        PROTOCOL_VERSION::equals,
                        PROTOCOL_VERSION::equals);
        private static int id = 0;

        public static void register() {
                CHANNEL.registerMessage(id++, PacketActivateAbility.class, PacketActivateAbility::encode,
                                PacketActivateAbility::decode, PacketActivateAbility::handle);
                CHANNEL.registerMessage(id++, PacketSyncPowerData.class, PacketSyncPowerData::encode,
                                PacketSyncPowerData::decode, PacketSyncPowerData::handle);
                CHANNEL.registerMessage(id++, PacketShiftHeld.class, PacketShiftHeld::encode, PacketShiftHeld::decode,
                                PacketShiftHeld::handle);
                CHANNEL.registerMessage(id++, PacketFireUltShoot.class, PacketFireUltShoot::encode,
                                PacketFireUltShoot::decode,
                                PacketFireUltShoot::handle);
                // Laser ult channel: sent every tick while [G] held, and once on release
                CHANNEL.registerMessage(id++, PacketLaserUltChannel.class, PacketLaserUltChannel::encode,
                                PacketLaserUltChannel::decode, PacketLaserUltChannel::handle);
                CHANNEL.registerMessage(id++, PacketSyncAdeptPandaForm.class, PacketSyncAdeptPandaForm::encode,
                                PacketSyncAdeptPandaForm::decode, PacketSyncAdeptPandaForm::handle);
                // Admin form (mob skin override)
                CHANNEL.registerMessage(id++, PacketSyncPlayerForm.class, PacketSyncPlayerForm::encode,
                                PacketSyncPlayerForm::decode, PacketSyncPlayerForm::handle);
                CHANNEL.registerMessage(id++, PacketSyncPlayerPowerType.class, PacketSyncPlayerPowerType::encode,
                                PacketSyncPlayerPowerType::decode, PacketSyncPlayerPowerType::handle);
        }
}
