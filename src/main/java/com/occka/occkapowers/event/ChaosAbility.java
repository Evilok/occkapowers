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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class ChaosAbility {

        private static final Random RNG = new Random();

        private static MobEffectInstance fx(net.minecraft.world.effect.MobEffect eff, int dur, int amp) {
                return new MobEffectInstance(eff, dur, amp, false, false);
        }

        //
        public static void activateShift(ServerPlayer player, ServerLevel level) {
                int roll = RNG.nextInt(10);
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
                        case 4 -> {
                                player.addEffect(fx(MobEffects.DAMAGE_BOOST, 80, 1));
                                player.sendSystemMessage(Component.literal("Chaos: Rage Surge!")
                                                .withStyle(ChatFormatting.GOLD));
                        }
                        case 5 -> {
                                player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 100, 2));
                                player.sendSystemMessage(Component.literal("Chaos: Hyper Speed!")
                                                .withStyle(ChatFormatting.AQUA));
                        }
                        case 6 -> {
                                player.addEffect(fx(MobEffects.WEAKNESS, 80, 1));
                                player.sendSystemMessage(Component.literal("Chaos: Weakness...")
                                                .withStyle(ChatFormatting.GRAY));
                        }
                        case 7 -> {
                                player.addEffect(fx(MobEffects.SLOW_FALLING, 120, 0));
                                player.sendSystemMessage(Component.literal("Chaos: Slow Falling")
                                                .withStyle(ChatFormatting.WHITE));
                        }
                        case 8 -> {
                                player.addEffect(fx(MobEffects.GLOWING, 120, 0));
                                player.sendSystemMessage(Component.literal("Chaos: You Glow!")
                                                .withStyle(ChatFormatting.YELLOW));
                        }
                        case 9 -> {
                                player.addEffect(fx(MobEffects.DIG_SPEED, 120, 1));
                                player.sendSystemMessage(Component.literal("Chaos: Haste!")
                                                .withStyle(ChatFormatting.GREEN));
                        }
                }
        }

        //
        public static void activateAbility(ServerPlayer player, ServerLevel level) {
                int roll = RNG.nextInt(10);
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
                        case 6 -> {
                                player.addEffect(fx(MobEffects.DAMAGE_BOOST, 220, 2));
                                player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 220, 1));
                                player.sendSystemMessage(Component.literal("Chaos: Battle Rush!")
                                                .withStyle(ChatFormatting.GOLD));
                        }
                        case 7 -> {
                                player.addEffect(fx(MobEffects.POISON, 120, 1));
                                player.addEffect(fx(MobEffects.WEAKNESS, 120, 0));
                                player.sendSystemMessage(Component.literal("Chaos: Toxic Crash!")
                                                .withStyle(ChatFormatting.DARK_GREEN));
                        }
                        case 8 -> {
                                player.getInventory().add(new ItemStack(Items.GOLDEN_APPLE, 1));
                                player.sendSystemMessage(Component.literal("Chaos: Free Golden Apple!")
                                                .withStyle(ChatFormatting.YELLOW));
                        }
                        case 9 -> {
                                spawnClone(player, level, 1);
                                spawnClone(player, level, 3);
                                player.sendSystemMessage(Component.literal("Chaos: Double Clones!")
                                                .withStyle(ChatFormatting.LIGHT_PURPLE));
                        }
                }
        }

        //
        public static void activateUlt(ServerPlayer player, ServerLevel level) {
                int roll = RNG.nextInt(10);
                switch (roll) {
                        case 0 -> {
                                //
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
                                //
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
                        case 3 -> {
                                player.addEffect(fx(MobEffects.ABSORPTION, 500, 4));
                                player.addEffect(fx(MobEffects.REGENERATION, 200, 2));
                                player.sendSystemMessage(Component.literal("Chaos Ult: Juggernaut!")
                                                .withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD));
                        }
                        case 4 -> {
                                player.addEffect(fx(MobEffects.WITHER, 200, 1));
                                player.addEffect(fx(MobEffects.DAMAGE_BOOST, 260, 3));
                                player.sendSystemMessage(Component.literal("Chaos Ult: Cursed Power!")
                                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                        }
                        case 5 -> {
                                chaosExplosion(player, level);
                                spawnClone(player, level, 1);
                                spawnClone(player, level, 2);
                                player.sendSystemMessage(Component.literal("Chaos Ult: Cataclysm Echoes!")
                                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                        }
                        case 6 -> {
                                for (int i = 0; i < 3; i++) {
                                        chaosTP(player, level);
                                }
                                player.sendSystemMessage(Component.literal("Chaos Ult: Triple Warp!")
                                                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
                        }
                        case 7 -> {
                                player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 420, 3));
                                player.addEffect(fx(MobEffects.JUMP, 420, 2));
                                player.addEffect(fx(MobEffects.WEAKNESS, 420, 1));
                                player.sendSystemMessage(Component.literal("Chaos Ult: Frenzy Legs!")
                                                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                        }
                        case 8 -> {
                                player.hurt(player.damageSources().magic(), 10.0f);
                                player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 320, 2));
                                player.addEffect(fx(MobEffects.DAMAGE_BOOST, 320, 2));
                                player.sendSystemMessage(Component.literal("Chaos Ult: Blood Pact!")
                                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                        }
                        case 9 -> {
                                player.addEffect(fx(MobEffects.BLINDNESS, 240, 0));
                                player.addEffect(fx(MobEffects.INVISIBILITY, 240, 0));
                                player.addEffect(fx(MobEffects.MOVEMENT_SPEED, 240, 1));
                                player.sendSystemMessage(Component.literal("Chaos Ult: Phantom Rush!")
                                                .withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));
                        }
                }
        }

        // === HELPERS ===

        //
        private static void chaosTP(ServerPlayer player, ServerLevel level) {
                for (int attempt = 0; attempt < 20; attempt++) {
                        double ox = (RNG.nextDouble() - 0.5) * 60; // -30..+30
                        double oz = (RNG.nextDouble() - 0.5) * 60;
                        int nx = (int) Math.floor(player.getX() + ox);
                        int nz = (int) Math.floor(player.getZ() + oz);
                        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz);
                        BlockPos pos = new BlockPos(nx, groundY, nz);

                        if (pos.getY() >= level.getMinBuildHeight() + 1
                                        && pos.getY() < level.getMaxBuildHeight() - 1
                                        && level.getBlockState(pos).isAir()
                                        && level.getBlockState(pos.above()).isAir()
                                        && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                                //
                                level.sendParticles(ParticleTypes.PORTAL,
                                                player.getX(), player.getY() + 1, player.getZ(),
                                                30, 0.5, 1, 0.5, 0.15);
                                //
                                player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                                //
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

        public static void tickClones(ServerLevel level) {
                level.getEntitiesOfClass(ArmorStand.class,
                                new net.minecraft.world.phys.AABB(
                                                -3.0E7, level.getMinBuildHeight(), -3.0E7,
                                                3.0E7, level.getMaxBuildHeight(), 3.0E7),
                                e -> e.getPersistentData().contains("occka_clone_lifetime"))
                                .forEach(stand -> {
                                        int life = stand.getPersistentData().getInt("occka_clone_lifetime") - 20;
                                        if (life <= 0) {
                                                stand.discard();
                                        } else {
                                                stand.getPersistentData().putInt("occka_clone_lifetime", life);
                                        }
                                });
        }

        //
        private static void chaosExplosion(ServerPlayer player, ServerLevel level) {
                var box = player.getBoundingBox().inflate(10);
                level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                                e -> e != player)
                                .forEach(e -> e.hurt(player.damageSources().explosion(null, null), 12));

                //
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

        //
        private static void spawnClone(ServerPlayer player, ServerLevel level, int index) {
                 public class ChaosAbility {
                for (int i = 0; i < 3; i++) {
                        player.addEffect(fx(positive[RNG.nextInt(positive.length)],
                                        600, RNG.nextInt(3)));
                }
                for (int i = 0; i < 2; i++) {
                        player.addEffect(fx(negative[RNG.nextInt(negative.length)],
                                        300, RNG.nextInt(2)));
                }

                //
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