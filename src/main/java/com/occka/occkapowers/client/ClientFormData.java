package com.occka.occkapowers.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientFormData {

    private static final Map<UUID, String> FORMS = new HashMap<>();

    private ClientFormData() {}

    public static void set(UUID playerId, String formId) {
        if (formId == null || formId.isEmpty()) {
            FORMS.remove(playerId);
        } else {
            FORMS.put(playerId, formId.toLowerCase());
        }
    }

    /** Returns form id or "" if none. */
    public static String get(UUID playerId) {
        return FORMS.getOrDefault(playerId, "");
    }

    public static boolean has(UUID playerId) {
        return FORMS.containsKey(playerId);
    }
}