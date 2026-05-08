package com.occka.occkapowers.cuffslock;

import com.occka.occkapowers.OcckaPowers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class CuffsRecipeLock {
    private static final String CUFFED_MOD_ID = "cuffed";
    private static final Set<ResourceLocation> BLOCKED_RECIPE_IDS = Set.of(
            new ResourceLocation(CUFFED_MOD_ID, "bundle"),
            new ResourceLocation(CUFFED_MOD_ID, "fuzzy_handcuffs"),
            new ResourceLocation(CUFFED_MOD_ID, "handcuffs"),
            new ResourceLocation(CUFFED_MOD_ID, "leg_shackles"),
            new ResourceLocation(CUFFED_MOD_ID, "legcuffs"),
            new ResourceLocation(CUFFED_MOD_ID, "shackles"),
            new ResourceLocation(CUFFED_MOD_ID, "duck_tape"),
            new ResourceLocation(CUFFED_MOD_ID, "weighted_anchor"),
            new ResourceLocation(CUFFED_MOD_ID, "padlock")
    );
    private static final Set<ResourceLocation> BLOCKED_ITEM_IDS = BLOCKED_RECIPE_IDS;

    private CuffsRecipeLock() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(CuffsRecipeLock.class);
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        filterCraftingRecipes(event.getServer().getRecipeManager(), "server about to start");
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        filterCraftingRecipes(event.getServer().getRecipeManager(), "server started");
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                filterCraftingRecipes(server.getRecipeManager(), "tags updated");
            }
        }
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        filterCraftingRecipes(event.getRecipeManager(), "client recipes updated");
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack craftedStack = event.getCrafting();
        if (!(event.getEntity() instanceof ServerPlayer player) || !isBlockedCuffedItem(craftedStack)) {
            return;
        }

        Item craftedItem = craftedStack.getItem();
        int craftedCount = craftedStack.getCount();
        craftedStack.setCount(0);

        player.getServer().execute(() -> {
            int removedCount = removeCraftedItems(player, craftedItem, craftedCount);
            player.containerMenu.broadcastChanges();

            OcckaPowers.LOGGER.info(
                    "Removed blocked Cuffed crafted item {} x{} from {}",
                    ForgeRegistries.ITEMS.getKey(craftedItem),
                    removedCount,
                    player.getGameProfile().getName()
            );
        });
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }

        if (event.player instanceof ServerPlayer player) {
            int removedCount = removeBlockedCuffedItems(player);
            if (removedCount > 0) {
                player.containerMenu.broadcastChanges();
                OcckaPowers.LOGGER.info(
                        "Removed {} blocked Cuffed item(s) from {}",
                        removedCount,
                        player.getGameProfile().getName()
                );
            }
        }
    }

    private static void filterCraftingRecipes(RecipeManager recipeManager, String reason) {
        try {
            Field recipesField = RecipeManager.class.getDeclaredField("recipes");
            Field byNameField = RecipeManager.class.getDeclaredField("byName");
            recipesField.setAccessible(true);
            byNameField.setAccessible(true);

            Map<?, ?> originalRecipesByType = (Map<?, ?>) recipesField.get(recipeManager);
            Map<?, ?> originalRecipesByName = (Map<?, ?>) byNameField.get(recipeManager);
            Object originalCraftingRecipes = originalRecipesByType.get(RecipeType.CRAFTING);

            if (!(originalCraftingRecipes instanceof Map<?, ?> craftingRecipesById)) {
                OcckaPowers.LOGGER.info("No crafting recipes found while filtering Cuffs recipes for {}", reason);
                return;
            }

            Map<Object, Object> filteredCraftingRecipes = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : craftingRecipesById.entrySet()) {
                if (!isCuffsRecipe(entry.getKey(), entry.getValue())) {
                    filteredCraftingRecipes.put(entry.getKey(), entry.getValue());
                }
            }

            Map<Object, Object> filteredRecipesByType = new HashMap<>(originalRecipesByType);
            filteredRecipesByType.put(RecipeType.CRAFTING, Map.copyOf(filteredCraftingRecipes));

            Map<Object, Object> filteredRecipesByName = new HashMap<>();
            for (Map.Entry<?, ?> entry : originalRecipesByName.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Recipe<?> recipe && recipe.getType() == RecipeType.CRAFTING) {
                    if (!isCuffsRecipe(entry.getKey(), value)) {
                        filteredRecipesByName.put(entry.getKey(), value);
                    }
                } else {
                    filteredRecipesByName.put(entry.getKey(), value);
                }
            }

            recipesField.set(recipeManager, Map.copyOf(filteredRecipesByType));
            byNameField.set(recipeManager, Map.copyOf(filteredRecipesByName));

            OcckaPowers.LOGGER.info(
                    "Locked Cuffs crafting recipes for {}: {} -> {}",
                    reason,
                    craftingRecipesById.size(),
                    filteredCraftingRecipes.size()
            );
        } catch (ReflectiveOperationException | ClassCastException exception) {
            OcckaPowers.LOGGER.error("Failed to filter Cuffs crafting recipes for {}", reason, exception);
        }
    }

    private static boolean isCuffsRecipe(Object recipeId, Object recipe) {
        if (recipeId instanceof ResourceLocation resourceLocation && BLOCKED_RECIPE_IDS.contains(resourceLocation)) {
            return true;
        }

        return recipe instanceof Recipe<?> typedRecipe
                && BLOCKED_RECIPE_IDS.contains(typedRecipe.getId());
    }

    private static boolean isBlockedCuffedItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId != null && BLOCKED_ITEM_IDS.contains(itemId);
    }

    private static int removeCraftedItems(ServerPlayer player, Item craftedItem, int craftedCount) {
        int remaining = craftedCount;
        ItemStack carriedStack = player.containerMenu.getCarried();

        if (carriedStack.is(craftedItem)) {
            int removedFromCarried = Math.min(remaining, carriedStack.getCount());
            carriedStack.shrink(removedFromCarried);
            remaining -= removedFromCarried;
            if (carriedStack.isEmpty()) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
            }
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack inventoryStack = player.getInventory().getItem(slot);
            if (!inventoryStack.is(craftedItem)) {
                continue;
            }

            int removedFromSlot = Math.min(remaining, inventoryStack.getCount());
            inventoryStack.shrink(removedFromSlot);
            remaining -= removedFromSlot;
            if (inventoryStack.isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }

        return craftedCount - remaining;
    }

    private static int removeBlockedCuffedItems(ServerPlayer player) {
        int removedCount = 0;

        ItemStack carriedStack = player.containerMenu.getCarried();
        if (isBlockedCuffedItem(carriedStack)) {
            removedCount += carriedStack.getCount();
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }

        for (int slotIndex = 0; slotIndex < player.containerMenu.slots.size(); slotIndex++) {
            ItemStack stack = player.containerMenu.slots.get(slotIndex).getItem();
            if (!isBlockedCuffedItem(stack)) {
                continue;
            }

            removedCount += stack.getCount();
            player.containerMenu.slots.get(slotIndex).set(ItemStack.EMPTY);
        }

        for (int slotIndex = 0; slotIndex < player.getInventory().getContainerSize(); slotIndex++) {
            ItemStack stack = player.getInventory().getItem(slotIndex);
            if (!isBlockedCuffedItem(stack)) {
                continue;
            }

            removedCount += stack.getCount();
            player.getInventory().setItem(slotIndex, ItemStack.EMPTY);
        }

        return removedCount;
    }
}
