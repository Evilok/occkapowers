package com.occka.occkapowers.form;

import net.minecraft.world.entity.player.Player;

public final class PlayerFormData {

    public static final String NBT_KEY = "occka_admin_form";

    private PlayerFormData() {}

    public static void setForm(Player player, String formId) {
        player.getPersistentData().putString(NBT_KEY, normalize(formId));
    }

    public static void clearForm(Player player) {
        player.getPersistentData().remove(NBT_KEY);
    }

    /** Returns form id or empty string if none. */
    public static String getForm(Player player) {
        return player.getPersistentData().getString(NBT_KEY);
    }

    public static boolean hasForm(Player player) {
        return !getForm(player).isEmpty();
    }

    public static void copyForm(Player from, Player to) {
        String formId = getForm(from);
        if (formId.isEmpty()) {
            clearForm(to);
        } else {
            setForm(to, formId);
        }
    }

    public static String normalize(String formId) {
        return formId == null ? "" : formId.trim().toLowerCase();
    }
}
