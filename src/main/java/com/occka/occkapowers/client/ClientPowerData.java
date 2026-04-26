package com.occka.occkapowers.client;

import com.occka.occkapowers.ability.PowerType;

public class ClientPowerData {
    public static PowerType powerType = PowerType.NONE;
    public static int shiftCd = 0, abilityCd = 0, ultCd = 0;
    public static int shiftMaxCd = 1, abilityMaxCd = 1, ultMaxCd = 1;
    public static boolean abilityUnlocked = false, ultUnlocked = false, fireUltActive = false, laserUltActive = false;
    public static int laserUltTicks = 0, laserUltMaxTicks = 80;

    public static void update(PowerType type, int sc, int ac, int uc, int sm, int am, int um, boolean au, boolean uu,
            boolean fua, boolean lua, int lut, int lumt) {
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
        laserUltActive = lua;
        laserUltTicks = Math.max(0, lut);
        laserUltMaxTicks = Math.max(1, lumt);
    }

    public static float shiftProgress() {
        return shiftMaxCd <= 0 ? 1 : (float) (shiftMaxCd - shiftCd) / shiftMaxCd;
    }

    public static float abilityProgress() {
        return (float) (abilityMaxCd - abilityCd) / abilityMaxCd;
    }

    public static float ultProgress() {
        return (float) (ultMaxCd - ultCd) / ultMaxCd;
    }

    public static boolean shiftReady() {
        return shiftCd == 0;
    }

    public static boolean abilityReady() {
        return abilityCd == 0;
    }

    public static boolean ultReady() {
        return ultCd == 0;
    }

    public static float laserUltProgress() {
        return laserUltMaxTicks <= 0 ? 0f : (float) laserUltTicks / laserUltMaxTicks;
    }
}
