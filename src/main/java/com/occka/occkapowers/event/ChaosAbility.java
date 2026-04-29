// ChaosAbility.java — полная замена
package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class ChaosAbility {

    private static final Random RNG = new Random();

    private static MobEffectInstance fx(net.minecraft.world.effect.MobEffect eff, int dur, int amp) {
        return new MobEffectInstance(eff, dur, amp, false, false);
    }

    // ========== SHIFT (10 вариантов, КД задаётся в PowerType) ==========
    public static void activateShift(ServerPlayer player, ServerLevel level) {
        int roll = RNG.nextInt(10);
        switch (roll) {
            // --- ХОРОШИЕ ---
            case 0 -> {
                player.addEffect(fx(MobEffects.REGENERATION, 100, 2));
                player.addEffect(fx(MobEffects.ABSORPTION, 100, 2));
                level.sendParticles(ParticleTypes.HEART,
                        player.getX(), player.getY() + 2, player.getZ(),
                        12, 0.6, 0.4, 0.6, 0.1);
                player.sendSystemMessage(Component.literal("Chaos Shift: Blessed Regen!")
                        .withStyle(ChatFormatting.GREEN));
            }
            case 1 -> {
                player.addEffect(fx(MobEffects.DAMAGE_BOOST, 120, 2));
                player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 120, 1));
                level.sendParticles(ParticleTypes.FLAME,
                        player.getX(), player.getY() + 1, player.getZ(),
                        20, 0.5, 0.5, 0.5, 0.1);
                player.sendSystemMessage(Component.literal("Chaos Shift: Power Surge!")
                        .withStyle(ChatFormatting.GOLD));
            }
            case 2 -> {
                player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 120, 3));
                level.sendParticles(ParticleTypes.ENCHANT,
                        player.getX(), player.getY() + 1, player.getZ(),
                        25, 0.6, 0.6, 0.6, 0.2);
                player.sendSystemMessage(Component.literal("Chaos Shift: Diamond Skin!")
                        .withStyle(ChatFormatting.AQUA));
            }
            // --- СРЕДНИЕ ---
            case 3 -> {
                player.addEffect(fx(MobEffects.LEVITATION, 60, 1));
                player.addEffect(fx(MobEffects.SLOW_FALLING, 60, 0));
                level.sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() - 0.3, player.getZ(),
                        10, 0.5, 0.1, 0.5, 0.02);
                player.sendSystemMessage(Component.literal("Chaos Shift: Sky Walk!")
                        .withStyle(ChatFormatting.AQUA));
            }
            case 4 -> {
                player.addEffect(fx(MobEffects.INVISIBILITY, 200, 0));
                player.addEffect(fx(MobEffects.NIGHT_VISION, 200, 0));
                level.sendParticles(ParticleTypes.PORTAL,
                        player.getX(), player.getY() + 1, player.getZ(),
                        15, 0.5, 1, 0.5, 0.1);
                player.sendSystemMessage(Component.literal("Chaos Shift: Shadow Form!")
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
            case 5 -> {
                // Телепорт прямо в шифте
                boolean tpOk = chaosTP(player, level);
                if (!tpOk)
                    player.sendSystemMessage(Component.literal("Chaos Shift: Teleport failed!")
                            .withStyle(ChatFormatting.GRAY));
            }
            // --- ПЛОХИЕ ---
            case 6 -> {
                player.addEffect(fx(MobEffects.CONFUSION, 80, 0));
                player.addEffect(fx(MobEffects.BLINDNESS, 60, 0));
                level.sendParticles(ParticleTypes.WITCH,
                        player.getX(), player.getY() + 2, player.getZ(),
                        15, 0.5, 0.5, 0.5, 0.1);
                player.sendSystemMessage(Component.literal("Chaos Shift: Madness!")
                        .withStyle(ChatFormatting.DARK_RED));
            }
            case 7 -> {
                player.hurt(player.damageSources().magic(), 6.0f);
                player.addEffect(fx(MobEffects.POISON, 60, 1));
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        player.getX(), player.getY() + 1, player.getZ(),
                        20, 0.5, 0.5, 0.5, 0.2);
                player.sendSystemMessage(Component.literal("Chaos Shift: Cursed!")
                        .withStyle(ChatFormatting.DARK_RED));
            }
            case 8 -> {
                player.addEffect(fx(MobEffects.WEAKNESS, 100, 1));
                player.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                level.sendParticles(ParticleTypes.SMOKE,
                        player.getX(), player.getY() + 1, player.getZ(),
                        12, 0.4, 0.4, 0.4, 0.05);
                player.sendSystemMessage(Component.literal("Chaos Shift: Weighted...")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            case 9 -> {
                // Взрыв прямо под игроком — урон + отброс вверх, сам ты тоже получаешь
                player.hurt(player.damageSources().explosion(null, null), 8.0f);
                player.setDeltaMovement(player.getDeltaMovement().x, 1.8, player.getDeltaMovement().z);
                player.hurtMarked = true;
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        player.getX(), player.getY(), player.getZ(),
                        1, 0, 0, 0, 0.01);
                player.sendSystemMessage(Component.literal("Chaos Shift: Self Destruct... partial.")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    // ========== ABILITY (10 вариантов) ==========
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        int roll = RNG.nextInt(10);
        switch (roll) {
            // --- ХОРОШИЕ ---
            case 0 -> chaosExplosion(player, level);
            case 1 -> {
                player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 400, 4));
                player.addEffect(fx(MobEffects.REGENERATION, 200, 1));
                level.sendParticles(ParticleTypes.ENCHANT,
                        player.getX(), player.getY() + 1, player.getZ(),
                        40, 1, 1, 1, 0.3);
                player.sendSystemMessage(Component.literal("Chaos Ability: Max Resistance + Regen!")
                        .withStyle(ChatFormatting.BLUE));
            }
            case 2 -> {
                player.getInventory().add(new ItemStack(Items.DIAMOND, 2 + RNG.nextInt(4)));
                player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 5 + RNG.nextInt(10)));
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        player.getX(), player.getY() + 1, player.getZ(),
                        30, 0.6, 0.6, 0.6, 0.15);
                player.sendSystemMessage(Component.literal("Chaos Ability: Jackpot!")
                        .withStyle(ChatFormatting.GOLD));
            }
            case 3 -> {
                // 2 клона с разных сторон
                spawnClone(player, level, 0);
                spawnClone(player, level, 1);
                player.addEffect(fx(MobEffects.INVISIBILITY, 300, 0));
                level.sendParticles(ParticleTypes.PORTAL,
                        player.getX(), player.getY() + 1, player.getZ(),
                        40, 1.5, 1.5, 1.5, 0.15);
                player.sendSystemMessage(Component.literal("Chaos Ability: Decoys + Invisibility!")
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
            // --- СРЕДНИЕ ---
            case 4 -> chaosTP(player, level);
            case 5 -> {
                // Молния на всех врагов в 15 блоках
                AbilityCommon.getNearbyEnemies(player, 15).forEach(e -> {
                    net.minecraft.world.entity.LightningBolt bolt =
                            new net.minecraft.world.entity.LightningBolt(EntityType.LIGHTNING_BOLT, level);
                    bolt.moveTo(e.position());
                    bolt.setVisualOnly(false);
                    level.addFreshEntity(bolt);
                });
                player.sendSystemMessage(Component.literal("Chaos Ability: Lightning Wrath!")
                        .withStyle(ChatFormatting.YELLOW));
            }
            case 6 -> {
                // Рандомные зелья на всех врагов в радиусе
                net.minecraft.world.effect.MobEffect[] debuffs = {
                        MobEffects.POISON, MobEffects.WITHER,
                        MobEffects.WEAKNESS, MobEffects.BLINDNESS,
                        MobEffects.CONFUSION, MobEffects.MOVEMENT_SLOWDOWN
                };
                AbilityCommon.getNearbyEnemies(player, 12).forEach(e -> {
                    e.addEffect(fx(debuffs[RNG.nextInt(debuffs.length)], 200, 1));
                    e.addEffect(fx(debuffs[RNG.nextInt(debuffs.length)], 100, 0));
                    level.sendParticles(ParticleTypes.WITCH,
                            e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.5, 0.3, 0.1);
                });
                player.sendSystemMessage(Component.literal("Chaos Ability: Hex Barrage!")
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
            // --- ПЛОХИЕ ---
            case 7 -> {
                player.hurt(player.damageSources().magic(), 15.0f);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        player.getX(), player.getY() + 1, player.getZ(),
                        40, 0.5, 1, 0.5, 0.2);
                player.sendSystemMessage(Component.literal("Chaos Ability: Self Damage! Ouch!")
                        .withStyle(ChatFormatting.DARK_RED));
            }
            case 8 -> {
                // Скидывает весь инвентарь на пол
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.isEmpty() && RNG.nextFloat() < 0.4f) {
                        player.drop(stack.copy(), false);
                        player.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
                level.sendParticles(ParticleTypes.SMOKE,
                        player.getX(), player.getY() + 1, player.getZ(),
                        25, 0.5, 0.5, 0.5, 0.1);
                player.sendSystemMessage(Component.literal("Chaos Ability: Inventory Explosion!")
                        .withStyle(ChatFormatting.RED));
            }
            case 9 -> {
                // Взрыв вокруг, но игрок получает resistance на время
                player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 40, 4));
                chaosExplosion(player, level);
                player.addEffect(fx(MobEffects.WEAKNESS, 200, 1));
                player.addEffect(fx(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
                player.sendSystemMessage(Component.literal("Chaos Ability: Berserker Bomb! (debuffed after)")
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    // ========== ULT (10 вариантов) ==========
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        int roll = RNG.nextInt(10);
        switch (roll) {
            // --- ХОРОШИЕ ---
            case 0 -> {
                // Берсерк: сила + скорость, но слепота
                player.addEffect(fx(MobEffects.DAMAGE_BOOST, 300, 2));
                player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 300, 2));
                player.addEffect(fx(MobEffects.REGENERATION, 300, 1));
                player.addEffect(fx(MobEffects.BLINDNESS, 300, 0));
                level.sendParticles(ParticleTypes.FLAME,
                        player.getX(), player.getY() + 1, player.getZ(),
                        60, 1.5, 1.5, 1.5, 0.2);
                player.sendSystemMessage(Component.literal("Chaos Ult: BERSERKER MODE! (blinded)")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            }
            case 1 -> {
                // 5 клонов + инвиз
                for (int i = 0; i < 5; i++) spawnClone(player, level, i);
                player.addEffect(fx(MobEffects.INVISIBILITY, 400, 0));
                player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 400, 1));
                level.sendParticles(ParticleTypes.PORTAL,
                        player.getX(), player.getY() + 1, player.getZ(),
                        80, 2, 2, 2, 0.2);
                player.sendSystemMessage(Component.literal("Chaos Ult: 5 Clones + Invisibility!")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
            }
            case 2 -> {
                // Молнии на всех в 40 блоках
                AbilityCommon.getNearbyEnemies(player, 40).forEach(e -> {
                    net.minecraft.world.entity.LightningBolt bolt =
                            new net.minecraft.world.entity.LightningBolt(EntityType.LIGHTNING_BOLT, level);
                    bolt.moveTo(e.position());
                    bolt.setVisualOnly(false);
                    level.addFreshEntity(bolt);
                });
                player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 100, 2));
                player.sendSystemMessage(Component.literal("Chaos Ult: THUNDER FIELD!")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            }
            // --- СРЕДНИЕ ---
            case 3 -> chaosRoulette(player, level);
            case 4 -> {
                // Серия из 5 телепортов подряд с частицами
                for (int i = 0; i < 5; i++) chaosTP(player, level);
                player.sendSystemMessage(Component.literal("Chaos Ult: BLINK STORM! (x5 teleports)")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            }
            case 5 -> {
                // Глобальный дебафф врагам в 50 блоках
                AbilityCommon.getNearbyEnemies(player, 50).forEach(e -> {
                    e.addEffect(fx(MobEffects.WITHER, 200, 1));
                    e.addEffect(fx(MobEffects.WEAKNESS, 200, 1));
                    e.addEffect(fx(MobEffects.BLINDNESS, 100, 0));
                    level.sendParticles(ParticleTypes.WITCH,
                            e.getX(), e.getY() + 1, e.getZ(), 15, 0.4, 0.4, 0.4, 0.1);
                });
                player.addEffect(fx(MobEffects.DAMAGE_BOOST, 300, 1));
                player.sendSystemMessage(Component.literal("Chaos Ult: PLAGUE OF CHAOS!")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
            }
            // --- ПЛОХИЕ / БЕЗУМНЫЕ ---
            case 6 -> {
                // Жертва: лечит всех врагов, даёт игроку серьёзный debuff
                AbilityCommon.getNearbyEnemies(player, 30).forEach(e ->
                        e.heal(e.getMaxHealth() * 0.5f));
                player.hurt(player.damageSources().magic(), 10.0f);
                player.addEffect(fx(MobEffects.POISON, 200, 1));
                player.addEffect(fx(MobEffects.WEAKNESS, 400, 2));
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        player.getX(), player.getY() + 1, player.getZ(),
                        50, 1.5, 1.5, 1.5, 0.2);
                player.sendSystemMessage(Component.literal("Chaos Ult: CURSED SACRIFICE! Enemies healed...")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            }
            case 7 -> {
                // Все эффекты снимаются, набор случайного мусора
                player.removeAllEffects();
                player.addEffect(fx(MobEffects.CONFUSION, 600, 0));
                player.addEffect(fx(MobEffects.BLINDNESS, 200, 0));
                player.hurt(player.damageSources().magic(), 5.0f);
                level.sendParticles(ParticleTypes.WITCH,
                        player.getX(), player.getY() + 1, player.getZ(),
                        40, 1, 1, 1, 0.2);
                player.sendSystemMessage(Component.literal("Chaos Ult: PURGE! All effects wiped... then madness.")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            }
            case 8 -> {
                // Анти-ульта: ТП всех врагов к игроку, потом взрыв
                AbilityCommon.getNearbyEnemies(player, 60).forEach(e ->
                        e.teleportTo(player.getX() + RNG.nextDouble() * 4 - 2,
                                player.getY(),
                                player.getZ() + RNG.nextDouble() * 4 - 2));
                // Небольшая задержка через data — взрыв сразу
                chaosExplosion(player, level);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        player.getX(), player.getY() + 1, player.getZ(),
                        3, 1, 0.5, 1, 0.08);
                player.sendSystemMessage(Component.literal("Chaos Ult: CONVERGENCE BOOM!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            case 9 -> {
                // Лотерея: полное исцеление ИЛИ почти смерть
                if (RNG.nextBoolean()) {
                    player.setHealth(player.getMaxHealth());
                    player.addEffect(fx(MobEffects.ABSORPTION, 600, 9));
                    player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 600, 4));
                    level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                            player.getX(), player.getY() + 1, player.getZ(),
                            80, 1, 2, 1, 0.4);
                    player.sendSystemMessage(Component.literal("Chaos Ult: MIRACLE! Full heal + God Mode 30s!")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                } else {
                    player.hurt(player.damageSources().magic(), player.getHealth() - 0.5f);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            player.getX(), player.getY() + 1, player.getZ(),
                            60, 1, 2, 1, 0.3);
                    player.sendSystemMessage(Component.literal("Chaos Ult: NEAR DEATH EXPERIENCE! Good luck...")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                }
            }
        }
    }

    // ========== HELPERS ==========

    /**
     * Телепорт в случайную безопасную позицию в радиусе 30 блоков.
     * Сканируем сверху вниз, проверяем что два блока воздуха + твёрдый пол.
     * @return true если телепорт успешен
     */
    private static boolean chaosTP(ServerPlayer player, ServerLevel level) {
        for (int attempt = 0; attempt < 30; attempt++) {
            // Случайное смещение по X и Z в диапазоне [-30, +30], не менее 5 блоков
            double ox = (RNG.nextDouble() * 2 - 1) * 30;
            double oz = (RNG.nextDouble() * 2 - 1) * 30;
            if (Math.abs(ox) < 5 && Math.abs(oz) < 5) continue;

            double nx = player.getX() + ox;
            double nz = player.getZ() + oz;

            // Начинаем поиск с высоты игрока + 10, идём вниз
            int startY = Math.min((int) player.getY() + 10, level.getMaxBuildHeight() - 2);
            int minY = level.getMinBuildHeight() + 1;

            for (int ny = startY; ny > minY; ny--) {
                BlockPos floor = new BlockPos((int) nx, ny - 1, (int) nz);
                BlockPos feet = new BlockPos((int) nx, ny, (int) nz);
                BlockPos head = new BlockPos((int) nx, ny + 1, (int) nz);

                boolean floorSolid = level.getBlockState(floor).isSolid();
                boolean feetAir = level.getBlockState(feet).isAir();
                boolean headAir = level.getBlockState(head).isAir();

                if (floorSolid && feetAir && headAir) {
                    // Найдено! Телепортируем
                    level.sendParticles(ParticleTypes.PORTAL,
                            player.getX(), player.getY() + 1, player.getZ(),
                            25, 0.5, 1, 0.5, 0.15);

                    player.teleportTo(nx + 0.5, ny, nz + 0.5);

                    level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                            player.getX(), player.getY() + 1, player.getZ(),
                            25, 0.5, 1, 0.5, 0.15);
                    player.sendSystemMessage(Component.literal("Chaos: Teleport!")
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                    return true;
                }
            }
        }
        player.sendSystemMessage(Component.literal("Chaos: Teleport failed (no safe spot)!")
                .withStyle(ChatFormatting.GRAY));
        return false;
    }

    private static void chaosExplosion(ServerPlayer player, ServerLevel level) {
        var box = player.getBoundingBox().inflate(10);
        level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                        e -> e != player)
                .forEach(e -> e.hurt(player.damageSources().explosion(null, null), 14));

        for (int i = 0; i < 60; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double r = RNG.nextDouble() * 10;
            double h = RNG.nextDouble() * 3;
            level.sendParticles(ParticleTypes.EXPLOSION,
                    player.getX() + r * Math.cos(angle), player.getY() + h,
                    player.getZ() + r * Math.sin(angle),
                    1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                player.getX(), player.getY() + 1, player.getZ(), 3, 1, 0.5, 1, 0.05);
        player.sendSystemMessage(Component.literal("Chaos: Explosion!")
                .withStyle(ChatFormatting.RED));
    }

    /**
     * Спавн клона (ArmorStand).
     * Тег occka_chaos_clone + occka_clone_lifetime = 400 тиков.
     * index 0 → прямо на игроке, остальные — по кругу.
     */
    public static void spawnClone(ServerPlayer player, ServerLevel level, int index) {
        double angle = (index / 5.0) * Math.PI * 2;
        double offsetX = index == 0 ? 0 : 2 * Math.cos(angle);
        double offsetZ = index == 0 ? 0 : 2 * Math.sin(angle);

        ArmorStand clone = new ArmorStand(EntityType.ARMOR_STAND, level);
        clone.moveTo(player.getX() + offsetX, player.getY(), player.getZ() + offsetZ,
                player.getYRot(), 0);
        clone.setCustomName(Component.literal(player.getName().getString())
                .withStyle(ChatFormatting.WHITE));
        clone.setCustomNameVisible(true);
        clone.setNoGravity(false);
        clone.setInvisible(false);

        // Тег для идентификации клона (используется в tickClones)
        clone.getPersistentData().putString("occka_chaos_clone", player.getUUID().toString());
        // Время жизни 400 тиков = 20 сек
        clone.getPersistentData().putInt("occka_clone_lifetime", 400);

        level.addFreshEntity(clone);
        level.sendParticles(ParticleTypes.WITCH,
                clone.getX(), clone.getY() + 1, clone.getZ(),
                15, 0.3, 0.5, 0.3, 0.1);
    }

    private static void chaosRoulette(ServerPlayer player, ServerLevel level) {
        net.minecraft.world.effect.MobEffect[] positive = {
                MobEffects.REGENERATION, MobEffects.ABSORPTION, MobEffects.DAMAGE_BOOST,
                MobEffects.MOVEMENT_SPEED, MobEffects.LUCK, MobEffects.JUMP
        };
        net.minecraft.world.effect.MobEffect[] negative = {
                MobEffects.POISON, MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN,
                MobEffects.WITHER, MobEffects.CONFUSION
        };

        for (int i = 0; i < 3; i++)
            player.addEffect(fx(positive[RNG.nextInt(positive.length)], 600, RNG.nextInt(3)));
        for (int i = 0; i < 2; i++)
            player.addEffect(fx(negative[RNG.nextInt(negative.length)], 300, RNG.nextInt(2)));

        for (int i = 0; i < 100; i++) {
            double a = RNG.nextDouble() * Math.PI * 2, r = RNG.nextDouble() * 5;
            level.sendParticles(i % 2 == 0 ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.WITCH,
                    player.getX() + r * Math.cos(a),
                    player.getY() + RNG.nextDouble() * 3,
                    player.getZ() + r * Math.sin(a),
                    1, 0, 0, 0, 0.1);
        }
        level.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
        player.sendSystemMessage(Component.literal("Chaos Ult: CHAOS ROULETTE! ???")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }
}