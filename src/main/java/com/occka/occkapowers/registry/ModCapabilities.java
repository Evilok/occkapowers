package com.occka.occkapowers.registry;

import com.occka.occkapowers.OcckaPowers;
import com.occka.occkapowers.ability.PlayerPowerData;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = OcckaPowers.MOD_ID)
public class ModCapabilities {
    public static final Capability<PlayerPowerData> PLAYER_POWER = CapabilityManager.get(new CapabilityToken<>() {
    });

    public static final ResourceLocation POWER_CAP_KEY = new ResourceLocation(OcckaPowers.MOD_ID, "player_power");

    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        bus.addListener(ModCapabilities::registerCaps);
    }

    private static void registerCaps(RegisterCapabilitiesEvent event) {
        event.register(PlayerPowerData.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(POWER_CAP_KEY, new PowerCapabilityProvider());
        }
    }

    public static class PowerCapabilityProvider
            implements ICapabilityProvider, net.minecraftforge.common.util.INBTSerializable<CompoundTag> {

        private final PlayerPowerData data = new PlayerPowerData();
        private final LazyOptional<PlayerPowerData> optional = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return PLAYER_POWER.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            data.deserializeNBT(nbt);
        }
    }
}
