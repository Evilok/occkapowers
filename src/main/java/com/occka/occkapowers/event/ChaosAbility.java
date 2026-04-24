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

        // ===== SHIFT - один из 4 =====
        public static void activateShift(ServerPlayer player, ServerLevel level) {
                int roll = RNG.nextInt(4);
                switch (roll) {
                        case 0 -> {
                                player.addEffect(fx(MobEffects.REGENERATION, 25, 1));
                                level.sendParticles(ParticleTypes.HEART,
                                                player.getX(), player.getY() + 2, player.getZ(),
                                                6, 0.5, 0.3, 0.5, 0.1);
                                player.sendSystemMessage(Component.literal("Chaos: Regeneration!")
                                                .withStyle(ChatFormatting.GREEN));
                        }
                        case 1 -> {
                                player.addEffect(fx(MobEffects.CONFUSION, 25, 0));
                                player.addEffect(fx(MobEffects.BLINDNESS, 25, 0));
                                level.sendParticles(ParticleTypes.WITCH,
                                                player.getX(), player.getY() + 2, player.getZ(),
                                                10, 0.5, 0.5, 0.5, 0.1);
                                player.sendSystemMessage(Component.literal("Chaos: Madness!")
                                                .withStyle(ChatFormatting.DARK_RED));
                        }
                        case 2 -> {
                                player.addEffect(fx(MobEffects.LEVITATION, 25, 1));
                                level.sendParticles(ParticleTypes.CLOUD,
                                                player.getX(), player.getY() - 0.3, player.getZ(),
                                                8, 0.5, 0.1, 0.5, 0.01);
                                player.sendSystemMessage(Component.literal("Chaos: Levitation!")
                                                .withStyle(ChatFormatting.AQUA));
                        }
                        case 3 -> {
                                player.hurt(player.damageSources().magic(), 4.0f); // instant damage II = 4hp
                                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                                                player.getX(), player.getY() + 1, player.getZ(),
                                                15, 0.5, 0.5, 0.5, 0.2);
                                player.sendSystemMessage(Component.literal("Chaos: Instant Damage!")
                                                .withStyle(ChatFormatting.RED));
                        }
                }
        }

        // ===== ABILITY - один из 6 =====
        public static void activateAbility(ServerPlayer player, ServerLevel level) {
                int roll = RNG.nextInt(6);
                switch (roll) {
                        case 0 -> chaosTP(player, level);
                        case 1 -> chaosExplosion(player, level);
                        case 2 -> spawnClone(player, level, 1);
                        case 3 -> {
                                player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 200, 4));
                                level.sendParticles(ParticleTypes.ENCHANT,
                                                player.getX(), player.getY() + 1, player.getZ(),
                                                30, 1, 1, 1, 0.3);
                                player.sendSystemMessage(Component.literal("Chaos: Max Resistance!")
                                                .withStyle(ChatFormatting.BLUE));
                        }
                        case 4 -> {
                                player.hurt(player.damageSources().magic(), 15.0f);
                                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                                player.getX(), player.getY() + 1, player.getZ(),
                                                40, 0.5, 1, 0.5, 0.2);
                                player.sendSystemMessage(Component.literal("Chaos: Self Damage! Ouch!")
                                                .withStyle(ChatFormatting.DARK_RED));
                        }
                        case 5 -> {
                                player.getInventory().add(new ItemStack(Items.DIAMOND, 1));
                                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                                player.getX(), player.getY() + 1, player.getZ(),
                                                20, 0.5, 0.5, 0.5, 0.1);
                                player.sendSystemMessage(Component.literal("Chaos: Free Diamond!")
                                                .withStyle(ChatFormatting.AQUA));
                        }
                }
        }

        // ===== ULT - один из 3 =====
        public static void activateUlt(ServerPlayer player, ServerLevel level) {
                int roll = RNG.nextInt(3);
                switch (roll) {
                        case 0 -> {
                                // Сила + скорость + слепота 15 сек
                                player.addEffect(fx(MobEffects.DAMAGE_BOOST, 300, 2));
                                player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 300, 2));
                                player.addEffect(fx(MobEffects.BLINDNESS, 300, 0));
                                level.sendParticles(ParticleTypes.FLAME,
                                                player.getX(), player.getY() + 1, player.getZ(),
                                                50, 1.5, 1.5, 1.5, 0.2);
                                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                                player.getX(), player.getY() + 1, player.getZ(),
                                                30, 1, 1, 1, 0.3);
                                player.sendSystemMessage(Component.literal("Chaos Ult: BERSERKER MODE! (blinded)")
                                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                        }
                        case 1 -> {
                                // 5 клонов + невидимость
                                for (int i = 0; i < 5; i++)
                                        spawnClone(player, level, i);
                                player.addEffect(fx(MobEffects.INVISIBILITY, 400, 0));
                                level.sendParticles(ParticleTypes.PORTAL,
                                                player.getX(), player.getY() + 1, player.getZ(),
                                                80, 2, 2, 2, 0.2);
                                player.sendSystemMessage(Component.literal("Chaos Ult: 5 Clones + Invisibility!")
                                                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
                        }
                        case 2 -> chaosRoulette(player, level);
                }
        }

        // === HELPERS ===

        // Хорус-телепорт: не попадает в блоки
        private static void chaosTP(ServerPlayer player, ServerLevel level) {
                for (int attempt = 0; attempt < 20; attempt++) {
                        double ox = (RNG.nextDouble() - 0.5) * 60; // -30..+30
                        double oz = (RNG.nextDouble() - 0.5) * 60;
                        double nx = player.getX() + ox;
                        double nz = player.getZ() + oz;

                        // Ищем безопасную Y
                        BlockPos pos = BlockPos.containing(nx, player.getY() + 10, nz);
                        while (pos.getY() > level.getMinBuildHeight() && !level.getBlockState(pos).isAir()) {
                                pos = pos.below();
                        }
                        // Проверяем что нога и голова свободны
                        if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                                        && !level.getBlockState(pos.below()).isAir()) {
                                // Партиклы на старом месте
                                level.sendParticles(ParticleTypes.PORTAL,
                                                player.getX(), player.getY() + 1, player.getZ(),
                                                30, 0.5, 1, 0.5, 0.15);
                                // Тп
                                player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                                // Партиклы на новом месте
                                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                                                player.getX(), player.getY() + 1, player.getZ(),
                                                30, 0.5, 1, 0.5, 0.15);
                                player.sendSystemMessage(Component.literal("Chaos: Teleport!")
                                                .withStyle(ChatFormatting.LIGHT_PURPLE));
                                return;
                        }
                }
                player.sendSystemMessage(Component.literal("Chaos: Teleport failed (no safe spot)!")
                                .withStyle(ChatFormatting.GRAY));
        }

        // Взрыв частиц + урон всем вокруг
        private static void chaosExplosion(ServerPlayer player, ServerLevel level) {
                var box = player.getBoundingBox().inflate(10);
                level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                                e -> e != player)
                                .forEach(e -> e.hurt(player.damageSources().explosion(null, null), 12));

                // Красивый взрыв частиц
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

        // Спавн клона (armor stand с именем игрока)
        private static void spawnClone(ServerPlayer player, ServerLevel level, int index) {
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
                // Помечаем как хаос-клон чтобы удалять потом
                clone.getPersistentData().putString("occka_chaos_clone",
                                player.getUUID().toString());
                // Удалить через 20 секунд
                clone.getPersistentData().putInt("occka_clone_lifetime", 400);

                level.addFreshEntity(clone);
                level.sendParticles(ParticleTypes.WITCH,
                                clone.getX(), clone.getY() + 1, clone.getZ(),
                                15, 0.3, 0.5, 0.3, 0.1);
        }

        // Ульта 3: случайные эффекты рулетка
        private static void chaosRoulette(ServerPlayer player, ServerLevel level) {
                // Даёт 5 случайных эффектов: 3 позитивных и 2 негативных вперемешку
                net.minecraft.world.effect.MobEffect[] positive = {
                                MobEffects.REGENERATION, MobEffects.ABSORPTION, MobEffects.DAMAGE_BOOST,
                                MobEffects.MOVEMENT_SPEED, MobEffects.LUCK, MobEffects.JUMP
                };
                net.minecraft.world.effect.MobEffect[] negative = {
                                MobEffects.POISON, MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN,
                                MobEffects.WITHER, MobEffects.CONFUSION
                };

                for (int i = 0; i < 3; i++) {
                        player.addEffect(fx(positive[RNG.nextInt(positive.length)],
                                        600, RNG.nextInt(3)));
                }
                for (int i = 0; i < 2; i++) {
                        player.addEffect(fx(negative[RNG.nextInt(negative.length)],
                                        300, RNG.nextInt(2)));
                }

                // Радуга частиц
                for (int i = 0; i < 100; i++) {
                        double a = RNG.nextDouble() * Math.PI * 2;
                        double r = RNG.nextDouble() * 5;
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