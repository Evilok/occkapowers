package com.occka.occkapowers.registry;

import com.occka.occkapowers.OcckaPowers;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModEntities {
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPES = DeferredRegister
            .create(ForgeRegistries.ENTITY_TYPES, OcckaPowers.MOD_ID);

    // Custom entities can be added here in the future
}
