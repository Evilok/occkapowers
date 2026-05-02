package com.occka.occkapowers.form;

import net.minecraft.world.entity.EntityType;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FormRegistry {

    private FormRegistry() {}

    /** All supported form IDs (lowercase). */
    public static final Map<String, EntityType<?>> FORMS = new LinkedHashMap<>();

    static {
        FORMS.put("zombie",     EntityType.ZOMBIE);
        FORMS.put("parrot",     EntityType.PARROT);
        FORMS.put("fish",       EntityType.COD);
        FORMS.put("turtle",     EntityType.TURTLE);
        FORMS.put("blaze",      EntityType.BLAZE);
        FORMS.put("enderman",   EntityType.ENDERMAN);
        FORMS.put("phantom",    EntityType.PHANTOM);
        FORMS.put("dragon",     EntityType.ENDER_DRAGON);
        FORMS.put("horse",      EntityType.HORSE);
        FORMS.put("villager",   EntityType.VILLAGER);
        FORMS.put("pig",        EntityType.PIG);
        FORMS.put("spider",     EntityType.SPIDER);
        FORMS.put("squid",      EntityType.SQUID);
        FORMS.put("golem",      EntityType.IRON_GOLEM);
        FORMS.put("ravager",    EntityType.RAVAGER);
    }

    public static boolean isValid(String id) {
        return id != null && FORMS.containsKey(PlayerFormData.normalize(id));
    }

    /** Returns null if not found. */
    public static EntityType<?> get(String id) {
        return FORMS.get(PlayerFormData.normalize(id));
    }
}
