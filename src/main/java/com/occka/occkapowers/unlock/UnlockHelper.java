package com.occka.occkapowers.unlock;

import com.occka.occkapowers.ability.PowerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class UnlockHelper {

    public static boolean tryConsumeAbility(Player player, PowerType type) {
        return switch (type) {
            case FIRE -> consume(player, Items.LAVA_BUCKET, 1);
            case ADEPT -> consume(player, Items.SUGAR, 8);
            case AIR -> consume(player, Items.FEATHER, 10);
            case WATER -> consume(player, Items.WATER_BUCKET, 1);
            case ICE -> consume(player, Items.SNOWBALL, 1);
            case LIGHTNING -> consume(player, Items.IRON_BOOTS, 1);
            case LASER -> consume(player, Items.BOW, 1);
            case GEO -> consume(player, Items.DIRT, 64);
            case VOID -> consume(player, Items.STRING, 12);
            case LIGHT -> consume(player, Items.TORCH, 16);
            case GRAVITY -> consume(player, Items.GRAVEL, 12);
            case ECHO -> consume(player, Items.LEATHER, 10);
            case SUPERFORCE -> consume(player, Items.IRON_BLOCK, 4);
            case CHAOS -> consume(player, Items.NETHER_STAR, 1);
            case FLASH -> consume(player, Items.RABBIT_FOOT, 1);
            case SPIDER -> consume(player, Items.STRING, 32);
            default -> false;
        };
    }

    public static boolean tryConsumeUlt(Player player, PowerType type) {
        return switch (type) {
            case FIRE -> consume(player, Items.BLAZE_ROD, 15);
            case AIR -> consume(player, Items.DIAMOND, 10);
            case WATER -> consume(player, Items.COOKED_SALMON, 30);
            case ICE -> consume(player, Items.IRON_BLOCK, 1);
            case LIGHTNING -> consume(player, Items.LIGHTNING_ROD, 1);
            case LASER -> consume(player, Items.TNT, 16);
            case GEO -> consume(player, Items.GOLD_BLOCK, 2);
            case ADEPT -> consume(player, Items.BAMBOO, 16);
            case VOID -> consume(player, Items.GHAST_TEAR, 1);
            case LIGHT -> consume(player, Items.GLOWSTONE, 32);
            case GRAVITY -> consume(player, Items.ANVIL, 1);
            case ECHO -> consumePotion(player);
            case CHAOS -> consume(player, Items.DRAGON_EGG, 1);
            case SUPERFORCE -> consume(player, Items.BEACON, 1);
            case FLASH -> consumeSpeedPotion(player);
            case SPIDER -> consume(player, Items.COBWEB, 25);
            default -> false;
        };
    }

    private static boolean consume(Player player, net.minecraft.world.item.Item item, int amount) {
        int found = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == item)
                found += s.getCount();
        }
        if (found < amount)
            return false;

        int toRemove = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && toRemove > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == item) {
                int remove = Math.min(s.getCount(), toRemove);
                s.shrink(remove);
                toRemove -= remove;
            }
        }
        return true;
    }

    private static boolean consumePotion(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == Items.POTION) {
                var tag = s.getTag();
                if (tag != null && tag.getString("Potion").contains("night_vision")) {
                    s.shrink(1);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean consumeSpeedPotion(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == Items.POTION) {
                var tag = s.getTag();
                if (tag != null && tag.getString("Potion").contains("swiftness")) {
                    s.shrink(1);
                    return true;
                }
            }
        }
        return false;
    }
}
