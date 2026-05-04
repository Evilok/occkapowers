package com.occka.occkapowers.alignment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class PlayerAlignment {
    public enum Type { NONE, HERO, VILLAIN }

    private static final String KEY = "occka_alignment";

    public static Type get(ServerPlayer player) {
        String val = player.getPersistentData().getString(KEY);
        return switch (val) {
            case "hero"    -> Type.HERO;
            case "villain" -> Type.VILLAIN;
            default        -> Type.NONE;
        };
    }

    public static void set(ServerPlayer player, Type type) {
        player.getPersistentData().putString(KEY, switch (type) {
            case HERO    -> "hero";
            case VILLAIN -> "villain";
            default      -> "";
        });
    }

    public static void clear(ServerPlayer player) {
        player.getPersistentData().remove(KEY);
    }
}