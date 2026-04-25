package com.occka.occkapowers.ability;

import net.minecraft.nbt.CompoundTag;

public class PlayerPowerData {
    private PowerType powerType = PowerType.NONE;
    private double fireUltOldGravity;
    private int shiftCooldown = 0;
    private int abilityCooldown = 0;
    private int ultCooldown = 0;
    private boolean abilityUnlocked = false;
    private boolean ultUnlocked = false;
    private boolean fireUltActive = false;
    private int fireUltTicks = 0;
    private double fireUltOriginX, fireUltOriginY, fireUltOriginZ;
    private int fireUltFireballCooldown = 0;
    private boolean fireUltShouldShoot = false;
    // Флаг: нужно вызвать endFireUlt на следующем тике
    private boolean fireUltJustEnded = false;

    public boolean isFireUltJustEnded() { return fireUltJustEnded; }
    public void clearFireUltJustEnded() { fireUltJustEnded = false; }

    public boolean shouldShootFireball() { return fireUltShouldShoot; }
    public void setShouldShootFireball(boolean value) { this.fireUltShouldShoot = value; }

    public PowerType getPowerType() { return powerType; }

    public void setPowerType(PowerType t) {
        this.powerType = t;
        this.abilityUnlocked = false;
        this.ultUnlocked = false;
        this.abilityCooldown = 0;
        this.ultCooldown = 0;
        this.shiftCooldown = 0;
        this.fireUltActive = false;
        this.fireUltTicks = 0;
        this.fireUltShouldShoot = false;
        this.fireUltJustEnded = false;
    }

    public int getShiftCooldown() { return shiftCooldown; }
    public void setShiftCooldown(int v) { shiftCooldown = Math.max(0, v); }

    public int getAbilityCooldown() { return abilityCooldown; }
    public void setAbilityCooldown(int v) { abilityCooldown = Math.max(0, v); }

    public int getUltCooldown() { return ultCooldown; }
    public void setUltCooldown(int v) { ultCooldown = Math.max(0, v); }

    public double getFireUltOldGravity() { return fireUltOldGravity; }
    public void setFireUltOldGravity(double value) { this.fireUltOldGravity = value; }

    public boolean isAbilityUnlocked() { return abilityUnlocked; }
    public void setAbilityUnlocked(boolean v) { abilityUnlocked = v; }

    public boolean isUltUnlocked() { return ultUnlocked; }
    public void setUltUnlocked(boolean v) { ultUnlocked = v; }

    public boolean isFireUltActive() { return fireUltActive; }
    public void setFireUltActive(boolean v) { fireUltActive = v; }

    public int getFireUltTicks() { return fireUltTicks; }
    public void setFireUltTicks(int v) { fireUltTicks = v; }

    public double getFireUltOriginX() { return fireUltOriginX; }
    public double getFireUltOriginY() { return fireUltOriginY; }
    public double getFireUltOriginZ() { return fireUltOriginZ; }
    public void setFireUltOrigin(double x, double y, double z) {
        fireUltOriginX = x; fireUltOriginY = y; fireUltOriginZ = z;
    }

    public int getFireUltFireballCooldown() { return fireUltFireballCooldown; }
    public void setFireUltFireballCooldown(int v) { fireUltFireballCooldown = Math.max(0, v); }

    public void tick() {
        if (shiftCooldown > 0) shiftCooldown--;
        if (abilityCooldown > 0) abilityCooldown--;
        if (ultCooldown > 0) ultCooldown--;

        if (fireUltActive) {
            if (fireUltTicks > 0) {
                fireUltTicks--;
                // Автострельба фаерболами каждые 2 секунды
                if (fireUltFireballCooldown > 0) {
                    fireUltFireballCooldown--;
                } else {
                    fireUltFireballCooldown = 40;
                    fireUltShouldShoot = true;
                }
            } else {
                // Время ульты вышло — помечаем для завершения
                fireUltActive = false;
                fireUltJustEnded = true;
            }
        }
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("powerType", powerType.getId());
        tag.putInt("shiftCooldown", shiftCooldown);
        tag.putInt("abilityCooldown", abilityCooldown);
        tag.putInt("ultCooldown", ultCooldown);
        tag.putBoolean("abilityUnlocked", abilityUnlocked);
        tag.putBoolean("ultUnlocked", ultUnlocked);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        powerType = PowerType.fromId(tag.getString("powerType"));
        shiftCooldown = tag.getInt("shiftCooldown");
        abilityCooldown = tag.getInt("abilityCooldown");
        ultCooldown = tag.getInt("ultCooldown");
        abilityUnlocked = tag.getBoolean("abilityUnlocked");
        ultUnlocked = tag.getBoolean("ultUnlocked");
    }
}