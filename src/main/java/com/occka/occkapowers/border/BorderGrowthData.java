package com.occka.occkapowers.border;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class BorderGrowthData extends SavedData {
    private static final String DATA_NAME = "occkapowers_border_growth";
    private static final String BLOCKS_KEY = "blocks_per_advancement";
    public static final int DEFAULT_BLOCKS_PER_ADVANCEMENT = 1;

    private int blocksPerAdvancement = DEFAULT_BLOCKS_PER_ADVANCEMENT;

    public static BorderGrowthData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                BorderGrowthData::load,
                BorderGrowthData::new,
                DATA_NAME);
    }

    public static BorderGrowthData get(ServerLevel level) {
        return get(level.getServer());
    }

    public static BorderGrowthData load(CompoundTag tag) {
        BorderGrowthData data = new BorderGrowthData();
        if (tag.contains(BLOCKS_KEY)) {
            data.blocksPerAdvancement = Math.max(0, tag.getInt(BLOCKS_KEY));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(BLOCKS_KEY, blocksPerAdvancement);
        return tag;
    }

    public int getBlocksPerAdvancement() {
        return blocksPerAdvancement;
    }

    public void setBlocksPerAdvancement(int blocksPerAdvancement) {
        this.blocksPerAdvancement = Math.max(0, blocksPerAdvancement);
        setDirty();
    }

    public void resetBlocksPerAdvancement() {
        setBlocksPerAdvancement(DEFAULT_BLOCKS_PER_ADVANCEMENT);
    }
}
