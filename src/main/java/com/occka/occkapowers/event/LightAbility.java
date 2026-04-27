package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class LightAbility {
    private LightAbility() {}

    // SHIFT (held): удобрение урожая + насыщение игроков рядом
    public static void activateShift(ServerPlayer player, ServerLevel level) {
        BlockPos center = player.blockPosition();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof CropBlock) {
                    boolean grew = BoneMealItem.applyBonemeal(
                            new ItemStack(Items.BONE_MEAL), level, pos, player);
                    if (grew)
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                                3, 0.3, 0.3, 0.3, 0);
                }
            }
        }
        AABB box = player.getBoundingBox().inflate(5);
        player.level().getEntitiesOfClass(Player.class, box, p -> true)
                .forEach(p -> p.addEffect(AbilityCommon.fx(MobEffects.SATURATION, 25, 1)));
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1, player.getZ(),
                15, 1.5, 1.5, 1.5, 0.05);
    }

    // ABILITY: свечение + ускорение добычи всем в радиусе
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(15);
        player.level().getEntitiesOfClass(LivingEntity.class, box, e -> true).forEach(e -> {
            e.addEffect(AbilityCommon.fx(MobEffects.GLOWING, 200, 0));
            if (e instanceof Player)
                e.addEffect(AbilityCommon.fx(MobEffects.DIG_SPEED, 200, 2));
        });
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1, player.getZ(),
                60, 7, 3, 7, 0.15);
        level.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1, player.getZ(),
                1, 0, 0, 0, 0);
        player.sendSystemMessage(AbilityCommon.msg("Light Flash!", ChatFormatting.YELLOW));
    }

    // ULT: жертва света — союзники получают поглощение/реген, сам игрок получает
    // тяжёлые дебаффы
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(12);
        List<Player> nearbyPlayers = player.level().getEntitiesOfClass(Player.class, box, p -> true);

        for (Player p : nearbyPlayers) {
            p.addEffect(AbilityCommon.fx(MobEffects.ABSORPTION, 400, 4));
            p.addEffect(AbilityCommon.fx(MobEffects.REGENERATION, 200, 2));
            p.addEffect(AbilityCommon.fx(MobEffects.DAMAGE_RESISTANCE, 200, 1));
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    p.getX(), p.getY() + 1, p.getZ(),
                    40, 0.5, 1, 0.5, 0.3);
        }

        // Тяжёлые дебаффы на себя
        player.addEffect(AbilityCommon.fx(MobEffects.BLINDNESS, 200, 0));
        player.addEffect(AbilityCommon.fx(MobEffects.CONFUSION, 200, 0));
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 200, 3));
        player.addEffect(AbilityCommon.fx(MobEffects.WEAKNESS, 200, 3));
        player.addEffect(AbilityCommon.fx(MobEffects.POISON, 200, 1));
        player.addEffect(AbilityCommon.fx(MobEffects.DIG_SLOWDOWN, 200, 3));

        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1, player.getZ(),
                100, 1, 2, 1, 0.5);
        player.sendSystemMessage(
                AbilityCommon.msg("LIGHT SACRIFICE! Allies protected!", ChatFormatting.YELLOW, ChatFormatting.BOLD));
    }
}
