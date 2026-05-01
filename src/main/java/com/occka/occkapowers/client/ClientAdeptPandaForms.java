package com.occka.occkapowers.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ClientAdeptPandaForms {
    private static final Set<UUID> ACTIVE = new HashSet<>();

    private ClientAdeptPandaForms() {}

    public static void set(UUID playerId, boolean active) {
        if (active) {
            ACTIVE.add(playerId);
        } else {
            ACTIVE.remove(playerId);
        }
    }

    public static boolean isActive(UUID playerId) {
        return ACTIVE.contains(playerId);
    }
}
