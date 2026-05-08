package com.occka.occkapowers.cuffslock;

import com.occka.occkapowers.OcckaPowers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.PathPackResources;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public final class CuffedRecipePack {
    private static final String PACK_ID = OcckaPowers.MOD_ID + ":cuffed_recipe_lock";
    private static final String PACK_ROOT = "resourcepacks/cuffed_recipe_lock";

    private CuffedRecipePack() {
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }

        event.addRepositorySource(output -> {
            Pack pack = Pack.readMetaAndCreate(
                    PACK_ID,
                    Component.literal("OcckaPowers Cuffed Recipe Lock"),
                    true,
                    id -> new PathPackResources(id, true, getPackRoot()) {
                        @NotNull
                        @Override
                        protected Path resolve(@NotNull String... paths) {
                            String[] resourcePath = new String[paths.length + 1];
                            resourcePath[0] = PACK_ROOT;
                            System.arraycopy(paths, 0, resourcePath, 1, paths.length);
                            return ModList.get()
                                    .getModFileById(OcckaPowers.MOD_ID)
                                    .getFile()
                                    .findResource(resourcePath);
                        }
                    },
                    PackType.SERVER_DATA,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN
            );

            if (pack != null) {
                output.accept(pack);
            }
        });
    }

    private static Path getPackRoot() {
        return ModList.get()
                .getModFileById(OcckaPowers.MOD_ID)
                .getFile()
                .findResource(PACK_ROOT);
    }
}
