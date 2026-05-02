package com.occka.occkapowers.client;

import com.occka.occkapowers.ability.PowerType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientPlayerPowerData {
    private static final Map<UUID, PowerType> POWERS = new HashMap<>();

    private ClientPlayerPowerData() {
    }

    public static void set(UUID playerId, PowerType powerType) {
        if (powerType == null || powerType == PowerType.NONE) {
            POWERS.remove(playerId);
        } else {
            POWERS.put(playerId, powerType);
        }
    }

    public static PowerType get(UUID playerId) {
        return POWERS.getOrDefault(playerId, PowerType.NONE);
    }
}
