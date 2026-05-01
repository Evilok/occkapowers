package com.occka.occkapowers.ability;

import net.minecraft.ChatFormatting;

public enum PowerType {
    NONE("none", ChatFormatting.WHITE),
    FIRE("fire", ChatFormatting.RED),
    AIR("air", ChatFormatting.AQUA),
    WATER("water", ChatFormatting.BLUE),
    ICE("ice", ChatFormatting.WHITE),
    LIGHTNING("lightning", ChatFormatting.YELLOW),
    LASER("laser", ChatFormatting.DARK_RED),
    GEO("geo", ChatFormatting.GOLD),
    VOID("void", ChatFormatting.DARK_PURPLE),
    LIGHT("light", ChatFormatting.YELLOW),
    GRAVITY("gravity", ChatFormatting.DARK_GRAY),
    CHAOS("chaos", ChatFormatting.DARK_RED),
    ECHO("echo", ChatFormatting.GREEN),
    SUPERFORCE("superforce", ChatFormatting.GOLD),
    ADEPT("adept", ChatFormatting.DARK_GREEN),
    FLASH("flash", ChatFormatting.LIGHT_PURPLE),
    FLOWER("flower", ChatFormatting.GREEN),
    VADER("vader", ChatFormatting.DARK_RED),
    CREEPER("creeper", ChatFormatting.DARK_GREEN),
    SPIDER("spider", ChatFormatting.WHITE),
    MERC("merc", ChatFormatting.DARK_RED);

    private final String id;
    private final ChatFormatting color;

    PowerType(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
    }

    public String getId() {
        return id;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public static PowerType fromId(String id) {
        for (PowerType t : values())
            if (t.id.equalsIgnoreCase(id))
                return t;
        return NONE;
    }

    public int getAbilityCooldown() {
        return switch (this) {
            case FIRE -> 40;
            case AIR -> 200; // кд восстановления 1 заряда (10 сек)
            case WATER -> 280;
            case FLOWER -> 800;
            case ICE -> 440; // 22 сек
            case LIGHTNING -> 200;
            case VADER -> 300; // 15 сек
            case LASER -> 382;
            case GEO -> 240;
            case CREEPER -> 300;
            case VOID -> 300;
            case LIGHT -> 650;
            case ADEPT -> 500;
            case GRAVITY -> 340;
            case ECHO -> 600;
            case SUPERFORCE -> 200;
            case CHAOS -> 200;
            case FLASH -> 160;
            case SPIDER -> 160;
            case MERC -> 120;
            default -> 0;
        };
    }

    public String getAbilityUnlockHint() {
        return switch (this) {
            case FIRE -> "1x Lava Bucket";
            case AIR -> "10x Feather";
            case WATER -> "1x Water Bucket";
            case ICE -> "1x Snowball";
            case LIGHTNING -> "1x Lightning Rod";
            case CREEPER -> "1x End Crystal";
            case LASER -> "1x Bow";
            case GEO -> "64x Dirt";
            case VADER -> "1x Skeleton Skull";
            case ADEPT -> "8x Sugar";
            case VOID -> "12x String";
            case LIGHT -> "16x Torch";
            case GRAVITY -> "12x Gravel";
            case ECHO -> "10x Leather";
            case FLOWER -> "16x Bone Meal";
            case SUPERFORCE -> "10x Iron Block";
            case CHAOS -> "1x Nether Star";
            case FLASH -> "1x Rabbit Foot";
            case SPIDER -> "32x String";
            case MERC -> "2x Iron Sword";
            default -> "?";
        };
    }

    public String getUltUnlockHint() {
        return switch (this) {
            case FIRE -> "15x Blaze Rod";
            case AIR -> "10x Diamond";
            case WATER -> "30x Cooked Salmon";
            case ADEPT -> "16x Bamboo";
            case ICE -> "1x Iron Block";
            case LIGHTNING -> "1x Lodestone";
            case LASER -> "32x TNT Block";
            case GEO -> "2x Gold Block";
            case CREEPER -> "1x Creeper Head";
            case VOID -> "1x Ghast Tear";
            case LIGHT -> "32x Glowstone";
            case GRAVITY -> "1x Anvil";
            case ECHO -> "1x Night Vision Potion";
            case SUPERFORCE -> "1x Beacon";
            case VADER -> "5x Netherite Ingot";
            case CHAOS -> "1x Dragon Egg";
            case FLOWER -> "1x Wither Rose";
            case FLASH -> "1x Speed Potion";
            case SPIDER -> "25x Cobweb";
            case MERC -> "1x Netherite Sword";
            default -> "?";
        };
    }

    public int getShiftMaxCharges() {
        return switch (this) {
            default -> 0;
        };
    }

    /** Максимальное количество зарядов. 0 = система зарядов не используется. */
    public int getAbilityMaxCharges() {
        return switch (this) {
            case AIR -> 2;
            default -> 0;
        };
    }

    /** Максимальное количество зарядов ульты. 0 = обычный КД. */
    public int getUltMaxCharges() {
        return switch (this) {
            default -> 0;
        };
    }

    public int getShiftCooldown() {
        return switch (this) {
            case ECHO -> 300;
            case FIRE -> 400;
            case CHAOS -> 200;
            case SUPERFORCE -> 150; //7.5
            case MERC -> 80;
            default -> 0;
        };
    }

    public int getShiftCooldown(net.minecraft.server.level.ServerPlayer player) { // ДЛЯ ФОРМЫ И ЕСЛИ В БУДУЩЕМ БУДУТ ФОРМЫ ТО ТУТ КД ДЛЯ СКИЛЛОВ С ФОРМОЙ.
        if (this == FIRE) {
            return player.getPersistentData()
                    .getBoolean("occka_fire_form_active") ? 100 : 400;
        }
        return getShiftCooldown(); // все остальные делегируют в старый
    }

    public int getUltCooldown() {
        return switch (this) {
            case SUPERFORCE -> 650;
            default -> 2400; // 120 сек для всех
        };
    }

    /** КД восстановления одного заряда ульты. По умолчанию = getUltCooldown(). */
    public int getUltChargeCooldown() {
        return switch (this) {
            default -> getUltCooldown();
        };
    }

    /** КД восстановления одного заряда шифта. По умолчанию = getShiftCooldown(). */
    public int getShiftChargeCooldown() {
        return switch (this) {
            default -> getShiftCooldown();
        };
    }
}
