package com.occka.occkapowers.ability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private boolean fireUltJustEnded = false;
    private int abilityMaxCdOverride = 0;

    // Charges
    private int abilityMaxCharges = 0;
    private int abilityChargeCdMax = 0;
    private List<Integer> abilityChargeCdQueue = new ArrayList<>();

    private int ultMaxCharges = 0;
    private int ultChargeCdMax = 0;
    private List<Integer> ultChargeCdQueue = new ArrayList<>();

    private int shiftMaxCharges = 0;
    private int shiftChargeCdMax = 0;
    private List<Integer> shiftChargeCdQueue = new ArrayList<>();

    public void setPowerType(PowerType t) {
        this.powerType = t;
        this.abilityUnlocked = false;
        this.ultUnlocked = false;
        this.abilityCooldown = 0;
        this.abilityMaxCdOverride = 0;
        this.ultCooldown = 0;
        this.shiftCooldown = 0;
        this.fireUltActive = false;
        this.fireUltTicks = 0;
        this.fireUltShouldShoot = false;
        this.fireUltJustEnded = false;
        this.abilityMaxCharges = t.getAbilityMaxCharges();
        this.abilityChargeCdMax = t.getAbilityCooldown();
        this.abilityChargeCdQueue = new ArrayList<>();
        this.ultMaxCharges = t.getUltMaxCharges();
        this.ultChargeCdMax = t.getUltChargeCooldown();
        this.ultChargeCdQueue = new ArrayList<>();
        this.shiftMaxCharges = t.getShiftMaxCharges();
        this.shiftChargeCdMax = t.getShiftChargeCooldown();
        this.shiftChargeCdQueue = new ArrayList<>();
    }

    // ===== TICK =====

    private void tickChargeQueue(List<Integer> queue) {
        queue.replaceAll(t -> t - 1);
        queue.removeIf(t -> t <= 0);
    }

    public void tick() {
        if (shiftMaxCharges == 0 && shiftCooldown > 0)
            shiftCooldown--;
        if (abilityMaxCharges == 0 && abilityCooldown > 0)
            abilityCooldown--;
        if (ultMaxCharges == 0 && ultCooldown > 0)
            ultCooldown--;

        tickChargeQueue(abilityChargeCdQueue);
        tickChargeQueue(ultChargeCdQueue);
        tickChargeQueue(shiftChargeCdQueue);

        if (fireUltActive) {
            if (fireUltTicks > 0) {
                fireUltTicks--;
                if (fireUltFireballCooldown > 0) {
                    fireUltFireballCooldown--;
                } else {
                    fireUltFireballCooldown = 40;
                    fireUltShouldShoot = true;
                }
            } else {
                fireUltActive = false;
                fireUltJustEnded = true;
            }
        }
    }

    // ===== NBT =====

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("powerType", powerType.getId());
        tag.putInt("shiftCooldown", shiftCooldown);
        tag.putInt("abilityCooldown", abilityCooldown);
        tag.putInt("ultCooldown", ultCooldown);
        tag.putBoolean("abilityUnlocked", abilityUnlocked);
        tag.putBoolean("ultUnlocked", ultUnlocked);
        tag.put("abilityChargeCdQueue", serializeQueue(abilityChargeCdQueue));
        tag.put("ultChargeCdQueue", serializeQueue(ultChargeCdQueue));
        tag.put("shiftChargeCdQueue", serializeQueue(shiftChargeCdQueue));
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        powerType = PowerType.fromId(tag.getString("powerType"));
        shiftCooldown = tag.getInt("shiftCooldown");
        abilityCooldown = tag.getInt("abilityCooldown");
        ultCooldown = tag.getInt("ultCooldown");
        abilityUnlocked = tag.getBoolean("abilityUnlocked");
        ultUnlocked = tag.getBoolean("ultUnlocked");

        abilityMaxCharges = powerType.getAbilityMaxCharges();
        abilityChargeCdMax = powerType.getAbilityCooldown();
        abilityChargeCdQueue = deserializeQueue(tag, "abilityChargeCdQueue");

        ultMaxCharges = powerType.getUltMaxCharges();
        ultChargeCdMax = powerType.getUltChargeCooldown();
        ultChargeCdQueue = deserializeQueue(tag, "ultChargeCdQueue");

        shiftMaxCharges = powerType.getShiftMaxCharges();
        shiftChargeCdMax = powerType.getShiftChargeCooldown();
        shiftChargeCdQueue = deserializeQueue(tag, "shiftChargeCdQueue");
    }

    private ListTag serializeQueue(List<Integer> queue) {
        ListTag list = new ListTag();
        for (int t : queue) {
            CompoundTag e = new CompoundTag();
            e.putInt("t", t);
            list.add(e);
        }
        return list;
    }

    private List<Integer> deserializeQueue(CompoundTag tag, String key) {
        List<Integer> queue = new ArrayList<>();
        if (!tag.contains(key))
            return queue;
        for (Tag t : tag.getList(key, Tag.TAG_COMPOUND))
            queue.add(((CompoundTag) t).getInt("t"));
        return queue;
    }

    // ===== ABILITY CHARGES =====

    public int getAbilityCharges() {
        return Math.max(0, abilityMaxCharges - abilityChargeCdQueue.size());
    }

    public int getAbilityMaxCharges() {
        return abilityMaxCharges;
    }

    public void setAbilityMaxCharges(int v) {
        abilityMaxCharges = v;
    }

    public int getAbilityChargeCd() {
        return abilityChargeCdQueue.isEmpty() ? 0 : Collections.min(abilityChargeCdQueue);
    }

    public int getAbilityChargeCdMax() {
        return abilityChargeCdMax;
    }

    public void setAbilityChargeCdMax(int v) {
        abilityChargeCdMax = v;
    }

    public List<Integer> getAbilityChargeCdQueue() {
        return abilityChargeCdQueue;
    }

    // ===== ULT CHARGES =====

    public int getUltCharges() {
        return Math.max(0, ultMaxCharges - ultChargeCdQueue.size());
    }

    public int getUltMaxCharges() {
        return ultMaxCharges;
    }

    public void setUltMaxCharges(int v) {
        ultMaxCharges = v;
    }

    public int getUltChargeCd() {
        return ultChargeCdQueue.isEmpty() ? 0 : Collections.min(ultChargeCdQueue);
    }

    public int getUltChargeCdMax() {
        return ultChargeCdMax;
    }

    public void setUltChargeCdMax(int v) {
        ultChargeCdMax = v;
    }

    public List<Integer> getUltChargeCdQueue() {
        return ultChargeCdQueue;
    }

    // ===== SHIFT CHARGES =====

    public int getShiftCharges() {
        return Math.max(0, shiftMaxCharges - shiftChargeCdQueue.size());
    }

    public int getShiftMaxCharges() {
        return shiftMaxCharges;
    }

    public void setShiftMaxCharges(int v) {
        shiftMaxCharges = v;
    }

    public int getShiftChargeCd() {
        return shiftChargeCdQueue.isEmpty() ? 0 : Collections.min(shiftChargeCdQueue);
    }

    public int getShiftChargeCdMax() {
        return shiftChargeCdMax;
    }

    public void setShiftChargeCdMax(int v) {
        shiftChargeCdMax = v;
    }

    public List<Integer> getShiftChargeCdQueue() {
        return shiftChargeCdQueue;
    }

    // ===== COOLDOWNS =====

    public int getShiftCooldown() {
        return shiftCooldown;
    }

    public void setShiftCooldown(int v) {
        shiftCooldown = Math.max(0, v);
    }

    public int getAbilityCooldown() {
        return abilityCooldown;
    }

    public void setAbilityCooldown(int v) {
        abilityCooldown = Math.max(0, v);
    }

    public int getUltCooldown() {
        return ultCooldown;
    }

    public void setUltCooldown(int v) {
        ultCooldown = Math.max(0, v);
    }

    // ===== MISC =====

    public int getAbilityMaxCdOverride() {
        return abilityMaxCdOverride;
    }

    public void setAbilityMaxCdOverride(int v) {
        abilityMaxCdOverride = v;
    }

    public PowerType getPowerType() {
        return powerType;
    }

    public boolean isFireUltJustEnded() {
        return fireUltJustEnded;
    }

    public void clearFireUltJustEnded() {
        fireUltJustEnded = false;
    }

    public boolean shouldShootFireball() {
        return fireUltShouldShoot;
    }

    public void setShouldShootFireball(boolean value) {
        this.fireUltShouldShoot = value;
    }

    public double getFireUltOldGravity() {
        return fireUltOldGravity;
    }

    public void setFireUltOldGravity(double value) {
        this.fireUltOldGravity = value;
    }

    public boolean isAbilityUnlocked() {
        return abilityUnlocked;
    }

    public void setAbilityUnlocked(boolean v) {
        abilityUnlocked = v;
    }

    public boolean isUltUnlocked() {
        return ultUnlocked;
    }

    public void setUltUnlocked(boolean v) {
        ultUnlocked = v;
    }

    public boolean isFireUltActive() {
        return fireUltActive;
    }

    public void setFireUltActive(boolean v) {
        fireUltActive = v;
    }

    public int getFireUltTicks() {
        return fireUltTicks;
    }

    public void setFireUltTicks(int v) {
        fireUltTicks = v;
    }

    public double getFireUltOriginX() {
        return fireUltOriginX;
    }

    public double getFireUltOriginY() {
        return fireUltOriginY;
    }

    public double getFireUltOriginZ() {
        return fireUltOriginZ;
    }

    public void setFireUltOrigin(double x, double y, double z) {
        fireUltOriginX = x;
        fireUltOriginY = y;
        fireUltOriginZ = z;
    }

    public int getFireUltFireballCooldown() {
        return fireUltFireballCooldown;
    }

    public void setFireUltFireballCooldown(int v) {
        fireUltFireballCooldown = Math.max(0, v);
    }
}