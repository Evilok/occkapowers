package com.occka.occkapowers.client;

import com.occka.occkapowers.ability.PowerType;

public class ClientPowerData {
    public static PowerType powerType = PowerType.NONE;

    // Cooldowns
    public static int shiftCd = 0, shiftMaxCd = 1;
    public static int abilityCd = 0, abilityMaxCd = 1;
    public static int ultCd = 0, ultMaxCd = 1;

    // Flags
    public static boolean abilityUnlocked = false, ultUnlocked = false, fireUltActive = false;

    // Class-specific resources
    public static int madness = 0;
    public static int soulCharge = 0;
    public static int creeperCharge = 0;

    // Charges
    public static int shiftCharges = 0, shiftMaxCharges = 0, shiftChargeCd = 0, shiftChargeCdMax = 1;
    public static int abilityCharges = 0, abilityMaxCharges = 0, abilityChargeCd = 0, abilityChargeCdMax = 1;
    public static int ultCharges = 0, ultMaxCharges = 0, ultChargeCd = 0, ultChargeCdMax = 1;

    public static void update(PowerType type,
            int sc, int ac, int uc, int sm, int am, int um,
            boolean au, boolean uu, boolean fua,
            int abilityCharges, int abilityMaxCharges, int abilityChargeCd, int abilityChargeCdMax,
            int ultCharges, int ultMaxCharges, int ultChargeCd, int ultChargeCdMax,
            int shiftCharges, int shiftMaxCharges, int shiftChargeCd, int shiftChargeCdMax,
            int madness, int soulCharge, int creeperCharge) {
        powerType = type;
        shiftCd = sc;
        abilityCd = ac;
        ultCd = uc;
        shiftMaxCd = Math.max(1, sm);
        abilityMaxCd = Math.max(1, am);
        ultMaxCd = Math.max(1, um);
        abilityUnlocked = au;
        ultUnlocked = uu;
        fireUltActive = fua;
        ClientPowerData.abilityCharges = abilityCharges;
        ClientPowerData.abilityMaxCharges = abilityMaxCharges;
        ClientPowerData.abilityChargeCd = abilityChargeCd;
        ClientPowerData.abilityChargeCdMax = Math.max(1, abilityChargeCdMax);
        ClientPowerData.ultCharges = ultCharges;
        ClientPowerData.ultMaxCharges = ultMaxCharges;
        ClientPowerData.ultChargeCd = ultChargeCd;
        ClientPowerData.ultChargeCdMax = Math.max(1, ultChargeCdMax);
        ClientPowerData.shiftCharges = shiftCharges;
        ClientPowerData.shiftMaxCharges = shiftMaxCharges;
        ClientPowerData.shiftChargeCd = shiftChargeCd;
        ClientPowerData.shiftChargeCdMax = Math.max(1, shiftChargeCdMax);
        ClientPowerData.madness = Math.max(0, Math.min(100, madness));
        ClientPowerData.soulCharge = Math.max(0, Math.min(100, soulCharge));
        ClientPowerData.creeperCharge = Math.max(0, Math.min(100, creeperCharge));
    }

    public static float shiftProgress() {
        return shiftMaxCd <= 0 ? 1 : (float)(shiftMaxCd - shiftCd) / shiftMaxCd;
    }

    public static float abilityProgress() {
        return (float)(abilityMaxCd - abilityCd) / abilityMaxCd;
    }

    public static float ultProgress() {
        return (float)(ultMaxCd - ultCd) / ultMaxCd;
    }

    public static boolean shiftReady() { return shiftCd == 0; }
    public static boolean abilityReady() { return abilityCd == 0; }
    public static boolean ultReady() { return ultCd == 0; }
}
