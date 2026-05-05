package com.occka.occkapowers;

import com.occka.occkapowers.command.OcckaCommand;
import com.occka.occkapowers.event.AbilityEventHandler;
//import com.occka.occkapowers.event.ItemEventHandler;
import com.occka.occkapowers.network.NetworkHandler;
import com.occka.occkapowers.registry.ModCapabilities;
import com.occka.occkapowers.registry.ModEntities;
import com.occka.occkapowers.taczlock.TaczRecipeLock;
//import com.occka.occkapowers.registry.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(OcckaPowers.MOD_ID)
public class OcckaPowers {
    public static final String MOD_ID = "occkapowers";
    public static final Logger LOGGER = LogManager.getLogger();

    public OcckaPowers() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);

        ModEntities.ENTITY_TYPES.register(modBus);
        // ModItems.ITEMS.register(modBus);
        ModCapabilities.register(modBus);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new AbilityEventHandler());
        // MinecraftForge.EVENT_BUS.register(new ItemEventHandler());
        TaczRecipeLock.register();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        NetworkHandler.register();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        OcckaCommand.register(event.getDispatcher());
    }
}
