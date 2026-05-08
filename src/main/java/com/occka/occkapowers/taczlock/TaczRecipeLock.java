package com.occka.occkapowers.taczlock;

import com.occka.occkapowers.OcckaPowers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

public final class TaczRecipeLock {
    private static final ResourceLocation TACZ_RECIPE_TYPE_ID = new ResourceLocation("tacz", "gun_smith_table_crafting");

    private static final Set<ResourceLocation> ALLOWED_GUN_RECIPE_IDS = Set.of(
            new ResourceLocation("tacz", "gun/glock_17"),
            new ResourceLocation("tacz", "gun/m700"),
            new ResourceLocation("tacz", "gun/m870")
    );

    private static final Set<String> ALLOWED_GUN_RECIPE_FILES = Set.of(
            "glock_17.json",
            "m700.json",
            "m870.json"
    );

    private static final String GUNPACK_SECTION = "[gunpack]";
    private static final String DEFAULT_PACK_DEBUG_KEY = "DefaultPackDebug";

    private static final String FILTER_JSON = """
            {
              "whitelist": [
                "^.*$"
              ],
              "blacklist": [
                "^tacz:gun/(?!(glock_17|m700|m870)$).*$"
              ]
            }
            """;

    private static final String GUN_SMITH_TABLE_DATA_JSON = """
            {
              "filter": "tacz:default",
              "tabs": [
                {
                  "id": "tacz:pistol",
                  "name": "tacz.type.pistol.name",
                  "icon": {
                    "item": "tacz:modern_kinetic_gun",
                    "nbt": {
                      "GunId": "tacz:glock_17"
                    }
                  }
                },
                {
                  "id": "tacz:sniper",
                  "name": "tacz.type.sniper.name",
                  "icon": {
                    "item": "tacz:modern_kinetic_gun",
                    "nbt": {
                      "GunId": "tacz:m700"
                    }
                  }
                },
                {
                  "id": "tacz:shotgun",
                  "name": "tacz.type.shotgun.name",
                  "icon": {
                    "item": "tacz:modern_kinetic_gun",
                    "nbt": {
                      "GunId": "tacz:m870"
                    }
                  }
                }
              ]
            }
            """;

    private static final Map<String, String> ALLOWED_GUN_RECIPE_JSON_BY_FILE = Map.of(
            "glock_17.json", """
                    {
                      "materials": [
                        {
                          "item": {
                            "tag": "forge:ingots/iron"
                          },
                          "count": 48
                        },
                        {
                          "item": {
                            "item": "minecraft:gunpowder"
                          },
                          "count": 12
                        }
                      ],
                      "result": {
                        "type": "gun",
                        "id": "tacz:glock_17"
                      },
                      "type": "tacz:gun_smith_table_crafting"
                    }
                    """,
            "m870.json", """
                    {
                      "materials": [
                        {
                          "item": {
                            "tag": "forge:ingots/iron"
                          },
                          "count": 64
                        },
                        {
                          "item": {
                            "tag": "minecraft:logs"
                          },
                          "count": 12
                        },
                        {
                          "item": {
                            "item": "minecraft:gunpowder"
                          },
                          "count": 12
                        }
                      ],
                      "result": {
                        "type": "gun",
                        "id": "tacz:m870"
                      },
                      "type": "tacz:gun_smith_table_crafting"
                    }
                    """,
            "m700.json", """
                    {
                      "materials": [
                        {
                          "item": {
                            "tag": "forge:gems/diamond"
                          },
                          "count": 12
                        },
                        {
                          "item": {
                            "tag": "forge:ingots/gold"
                          },
                          "count": 30
                        },
                        {
                          "item": {
                            "tag": "forge:ingots/iron"
                          },
                          "count": 64
                        }
                      ],
                      "result": {
                        "type": "gun",
                        "id": "tacz:m700",
                        "attachments": {
                          "scope": "tacz:scope_contender"
                        }
                      },
                      "type": "tacz:gun_smith_table_crafting"
                    }
                    """
    );

    private TaczRecipeLock() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(TaczRecipeLock.class);
        apply();
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        apply();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        filterTaczRecipeManager(event.getServer().getRecipeManager(), "server started");
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                filterTaczRecipeManager(server.getRecipeManager(), "tags updated");
            }
        }
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        filterTaczRecipeManager(event.getRecipeManager(), "client recipes updated");
    }

    private static void apply() {
        Path taczRoot = FMLPaths.GAMEDIR.get().resolve("tacz");
        Path taczPackRoot = taczRoot.resolve("tacz_default_gun");
        Path taczPackDataRoot = taczPackRoot.resolve("data").resolve("tacz");

        enableDefaultPackDebug(taczRoot.resolve("tacz-pre.toml"));
        writeRecipeFilter(taczPackDataRoot);
        writeGunSmithTableData(taczPackDataRoot);
        disableBlockedGunRecipes(taczPackDataRoot, taczPackRoot);
    }

    private static void enableDefaultPackDebug(Path preConfigFile) {
        try {
            Files.createDirectories(preConfigFile.getParent());

            String originalConfig = Files.exists(preConfigFile)
                    ? Files.readString(preConfigFile, StandardCharsets.UTF_8)
                    : "";
            String updatedConfig = withDefaultPackDebugEnabled(originalConfig);

            if (!updatedConfig.equals(originalConfig)) {
                Files.writeString(
                        preConfigFile,
                        updatedConfig,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
                OcckaPowers.LOGGER.info("TaCZ DefaultPackDebug enabled in {}", preConfigFile);
            }
        } catch (IOException exception) {
            OcckaPowers.LOGGER.error("Failed to enable TaCZ DefaultPackDebug in {}", preConfigFile, exception);
        }
    }

    private static String withDefaultPackDebugEnabled(String config) {
        if (config.isBlank()) {
            return GUNPACK_SECTION
                    + System.lineSeparator()
                    + DEFAULT_PACK_DEBUG_KEY
                    + " = true"
                    + System.lineSeparator();
        }

        String normalizedConfig = config.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalizedConfig.split("\n", -1);
        List<String> updatedLines = new ArrayList<>();
        boolean inGunpackSection = false;
        boolean sawGunpackSection = false;
        boolean wroteDefaultPackDebug = false;

        for (String line : lines) {
            String trimmedLine = line.trim();
            boolean startsSection = trimmedLine.startsWith("[") && trimmedLine.endsWith("]");

            if (startsSection) {
                if (inGunpackSection && !wroteDefaultPackDebug) {
                    updatedLines.add(DEFAULT_PACK_DEBUG_KEY + " = true");
                    wroteDefaultPackDebug = true;
                }

                inGunpackSection = GUNPACK_SECTION.equals(trimmedLine);
                sawGunpackSection |= inGunpackSection;
            }

            if (inGunpackSection && isDefaultPackDebugLine(trimmedLine)) {
                updatedLines.add(DEFAULT_PACK_DEBUG_KEY + " = true");
                wroteDefaultPackDebug = true;
            } else {
                updatedLines.add(line);
            }
        }

        if (inGunpackSection && !wroteDefaultPackDebug) {
            updatedLines.add(DEFAULT_PACK_DEBUG_KEY + " = true");
        }

        if (!sawGunpackSection) {
            if (!updatedLines.isEmpty() && !updatedLines.get(updatedLines.size() - 1).isBlank()) {
                updatedLines.add("");
            }
            updatedLines.add(GUNPACK_SECTION);
            updatedLines.add(DEFAULT_PACK_DEBUG_KEY + " = true");
        }

        String updatedConfig = String.join(System.lineSeparator(), updatedLines);
        if (!updatedConfig.endsWith(System.lineSeparator())) {
            updatedConfig += System.lineSeparator();
        }
        return updatedConfig;
    }

    private static boolean isDefaultPackDebugLine(String trimmedLine) {
        if (!trimmedLine.startsWith(DEFAULT_PACK_DEBUG_KEY)) {
            return false;
        }

        if (trimmedLine.length() == DEFAULT_PACK_DEBUG_KEY.length()) {
            return true;
        }

        char nextCharacter = trimmedLine.charAt(DEFAULT_PACK_DEBUG_KEY.length());
        return Character.isWhitespace(nextCharacter) || nextCharacter == '=';
    }

    private static void writeRecipeFilter(Path taczPackRoot) {
        Path filterFile = taczPackRoot
                .resolve("recipe_filters")
                .resolve("default.json");

        try {
            Files.createDirectories(filterFile.getParent());
            Files.writeString(
                    filterFile,
                    FILTER_JSON,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            OcckaPowers.LOGGER.info("TaCZ recipe filter written to {}", filterFile);
        } catch (IOException exception) {
            OcckaPowers.LOGGER.error("Failed to write TaCZ recipe filter to {}", filterFile, exception);
        }
    }

    private static void writeGunSmithTableData(Path taczPackRoot) {
        Path tableDataFile = taczPackRoot
                .resolve("data")
                .resolve("blocks")
                .resolve("gun_smith_table.json");

        try {
            Files.createDirectories(tableDataFile.getParent());
            Files.writeString(
                    tableDataFile,
                    GUN_SMITH_TABLE_DATA_JSON,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            OcckaPowers.LOGGER.info("TaCZ gun smith table data written to {}", tableDataFile);
        } catch (IOException exception) {
            OcckaPowers.LOGGER.error("Failed to write TaCZ gun smith table data to {}", tableDataFile, exception);
        }
    }

    private static void filterTaczRecipeManager(RecipeManager recipeManager, String reason) {
        RecipeType<?> recipeType = ForgeRegistries.RECIPE_TYPES.getValue(TACZ_RECIPE_TYPE_ID);
        if (recipeType == null) {
            OcckaPowers.LOGGER.info("TaCZ recipe type was not registered yet while filtering recipes for {}", reason);
            return;
        }

        try {
            Field recipesField = RecipeManager.class.getDeclaredField("recipes");
            Field byNameField = RecipeManager.class.getDeclaredField("byName");
            recipesField.setAccessible(true);
            byNameField.setAccessible(true);

            Map<?, ?> originalRecipesByType = (Map<?, ?>) recipesField.get(recipeManager);
            Map<?, ?> originalRecipesByName = (Map<?, ?>) byNameField.get(recipeManager);
            Object originalTaczRecipes = originalRecipesByType.get(recipeType);

            if (!(originalTaczRecipes instanceof Map<?, ?> taczRecipesById)) {
                OcckaPowers.LOGGER.info("No TaCZ gun smith table recipes found while filtering recipes for {}", reason);
                return;
            }

            Map<Object, Object> filteredTaczRecipes = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : taczRecipesById.entrySet()) {
                if (entry.getKey() instanceof ResourceLocation recipeId && ALLOWED_GUN_RECIPE_IDS.contains(recipeId)) {
                    filteredTaczRecipes.put(entry.getKey(), entry.getValue());
                }
            }

            Map<Object, Object> filteredRecipesByType = new HashMap<>(originalRecipesByType);
            filteredRecipesByType.put(recipeType, Map.copyOf(filteredTaczRecipes));

            Map<Object, Object> filteredRecipesByName = new HashMap<>();
            for (Map.Entry<?, ?> entry : originalRecipesByName.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Recipe<?> recipe && recipe.getType() == recipeType) {
                    if (entry.getKey() instanceof ResourceLocation recipeId && ALLOWED_GUN_RECIPE_IDS.contains(recipeId)) {
                        filteredRecipesByName.put(entry.getKey(), value);
                    }
                } else {
                    filteredRecipesByName.put(entry.getKey(), value);
                }
            }

            recipesField.set(recipeManager, Map.copyOf(filteredRecipesByType));
            byNameField.set(recipeManager, Map.copyOf(filteredRecipesByName));

            OcckaPowers.LOGGER.info(
                    "Locked TaCZ gun smith recipes for {}: {} -> {}",
                    reason,
                    taczRecipesById.size(),
                    filteredTaczRecipes.size()
            );
        } catch (ReflectiveOperationException | ClassCastException exception) {
            OcckaPowers.LOGGER.error("Failed to filter TaCZ gun smith recipes for {}", reason, exception);
        }
    }

    private static void disableBlockedGunRecipes(Path taczPackDataRoot, Path taczPackRoot) {
        Path gunRecipesDir = taczPackDataRoot.resolve("recipes").resolve("gun");
        Path disabledRecipesDir = taczPackRoot
                .resolve("disabled_by_codex")
                .resolve("recipes")
                .resolve("gun");

        if (!Files.isDirectory(gunRecipesDir)) {
            OcckaPowers.LOGGER.info("TaCZ gun recipe directory was not found: {}", gunRecipesDir);
            return;
        }

        try {
            Files.createDirectories(disabledRecipesDir);
            migrateLegacyDisabledRecipes(taczPackDataRoot, disabledRecipesDir);
            restoreAllowedRecipes(disabledRecipesDir, gunRecipesDir);
            writeAllowedGunRecipes(gunRecipesDir);
            moveBlockedRecipes(gunRecipesDir, disabledRecipesDir);
        } catch (IOException exception) {
            OcckaPowers.LOGGER.error("Failed to lock TaCZ gun recipes in {}", gunRecipesDir, exception);
        }
    }

    private static void migrateLegacyDisabledRecipes(Path taczPackDataRoot, Path disabledRecipesDir) throws IOException {
        Path legacyDisabledRecipesDir = taczPackDataRoot
                .resolve("occkapowers_disabled_recipes")
                .resolve("gun");

        if (!Files.isDirectory(legacyDisabledRecipesDir)) {
            return;
        }

        try (var recipes = Files.list(legacyDisabledRecipesDir)) {
            recipes
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> moveBlockedRecipe(path, disabledRecipesDir.resolve(path.getFileName())));
        }
    }

    private static void restoreAllowedRecipes(Path disabledRecipesDir, Path gunRecipesDir) throws IOException {
        for (String allowedFileName : ALLOWED_GUN_RECIPE_FILES) {
            Path disabledRecipe = disabledRecipesDir.resolve(allowedFileName);
            Path activeRecipe = gunRecipesDir.resolve(allowedFileName);

            if (Files.exists(disabledRecipe) && !Files.exists(activeRecipe)) {
                Files.move(disabledRecipe, activeRecipe, StandardCopyOption.REPLACE_EXISTING);
                OcckaPowers.LOGGER.info("Restored allowed TaCZ gun recipe {}", allowedFileName);
            }
        }
    }

    private static void writeAllowedGunRecipes(Path gunRecipesDir) throws IOException {
        Files.createDirectories(gunRecipesDir);

        for (Map.Entry<String, String> entry : ALLOWED_GUN_RECIPE_JSON_BY_FILE.entrySet()) {
            Path activeRecipe = gunRecipesDir.resolve(entry.getKey());
            Files.writeString(
                    activeRecipe,
                    entry.getValue(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            OcckaPowers.LOGGER.info("TaCZ gun recipe cost written for {}", entry.getKey());
        }
    }

    private static void moveBlockedRecipes(Path gunRecipesDir, Path disabledRecipesDir) throws IOException {
        try (var recipes = Files.list(gunRecipesDir)) {
            recipes
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !ALLOWED_GUN_RECIPE_FILES.contains(path.getFileName().toString()))
                    .forEach(path -> moveBlockedRecipe(path, disabledRecipesDir.resolve(path.getFileName())));
        }
    }

    private static void moveBlockedRecipe(Path activeRecipe, Path disabledRecipe) {
        try {
            Files.move(activeRecipe, disabledRecipe, StandardCopyOption.REPLACE_EXISTING);
            OcckaPowers.LOGGER.info("Disabled blocked TaCZ gun recipe {}", activeRecipe.getFileName());
        } catch (IOException exception) {
            OcckaPowers.LOGGER.error("Failed to disable blocked TaCZ gun recipe {}", activeRecipe, exception);
        }
    }
}
