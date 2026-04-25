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
    ADEPT("adept", ChatFormatting.DARK_GREEN);

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

    // Shift: 0 = continuous (no cd), else ticks
    public int getShiftCooldown() {
        return switch (this) {
            case ECHO -> 600; // 30s
            default -> 0; // continuous
        };
    }

    public int getAbilityCooldown() {
        return switch (this) {
            case FIRE -> 150;
            case AIR -> 100;
            case WATER -> 280;
            case ICE -> 260;
            case LIGHTNING -> 200;
            case LASER -> 240;
            case GEO -> 240;
            case VOID -> 300;
            case LIGHT -> 200;
            case ADEPT -> 500;
            case GRAVITY -> 340;
            case ECHO -> 200;
            case SUPERFORCE -> 200; // 10s
            case CHAOS -> 200; // 15 sec
            default -> 0;
        };
    }

    public int getUltCooldown() {
        return 2400;
    }

    // Unlock resources displayed in chat
    public String getAbilityUnlockHint() {
        return switch (this) {
            case FIRE -> "1x Lava Bucket";
            case AIR -> "10x Feather";
            case WATER -> "1x Water Bucket";
            case ICE -> "1x Snowball";
            case LIGHTNING -> "1x Iron Boots";
            case LASER -> "1x Bow";
            case GEO -> "64x Dirt";
            case ADEPT -> "8x Sugar";
            case VOID -> "12x String";
            case LIGHT -> "16x Torch";
            case GRAVITY -> "12x Gravel";
            case ECHO -> "10x Leather";
            case SUPERFORCE -> "10x Iron Block";
            case CHAOS -> "1x Nether Star";
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
            case LIGHTNING -> "1x Lightning Rod";
            case LASER -> "16x TNT Block";
            case GEO -> "2x Gold Block";
            case VOID -> "1x Ghast Tear";
            case LIGHT -> "32x Glowstone";
            case GRAVITY -> "1x Anvil";
            case ECHO -> "1x Night Vision Potion";
            case SUPERFORCE -> "1x Beacon";
            case CHAOS -> "1x Dragon Egg";
            default -> "?";
        };
    }
}
