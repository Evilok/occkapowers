package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class FlashAbility {
    private FlashAbility() {
    }

    public static void activateShift(ServerPlayer player, ServerLevel level) {
        tickHeldShift(player, level);
    }

    /**
     * Called by Shift-hold packet to create Flash trail and maintain speed buff.
     */
    public static void tickHeldShift(ServerPlayer player, ServerLevel level) {
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SPEED, 45, 7));
        player.addEffect(AbilityCommon.fx(MobEffects.JUMP, 45, 1));

        spawnRotatingAfterimage(player, level);

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 0.8, player.getZ(),
                3, 0.25, 0.25, 0.25, 0.08);
        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.2, player.getZ(),
                3, 0.25, 0.06, 0.25, 0.02);
    }

    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        Vec3 start = player.position();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 dash = look.scale(15.0);
        Vec3 target = start.add(dash);
        Vec3 horizontal = new Vec3(look.x * 2.7, Math.max(0.15, look.y * 1.2), look.z * 2.7);
        player.setDeltaMovement(horizontal);
        player.hurtMarked = true;
        player.addEffect(AbilityCommon.fx(MobEffects.DAMAGE_RESISTANCE, 25, 3));

        Set<java.util.UUID> hit = new HashSet<>();
        for (int i = 1; i <= 22; i++) {
            Vec3 p = start.lerp(target, i / 22.0);
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(p, p).inflate(1.1), e -> e != player && e.isAlive());
            for (LivingEntity entity : victims) {
                if (hit.add(entity.getUUID())) {
                    entity.hurt(player.damageSources().magic(), 10.0f);
                }
            }
        }

        for (int i = 0; i < 36; i++) {
            double t = i / 35.0;
            Vec3 p = start.lerp(target, t);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    p.x, p.y + 1.0, p.z, 2, 0.05, 0.08, 0.05, 0.02);
            level.sendParticles(ParticleTypes.CLOUD,
                    p.x, p.y + 0.4, p.z, 1, 0.04, 0.02, 0.04, 0.01);
        }

        level.sendParticles(ParticleTypes.FLASH, target.x, target.y + 1, target.z, 1, 0, 0, 0, 0);
        player.sendSystemMessage(AbilityCommon.msg("Afterimage Dash!", ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        AABB area = player.getBoundingBox().inflate(30.0);

        // Было: Mob.class — не захватывает игроков и некоторых мобов
        // Стало: LivingEntity.class с исключением самого игрока
        for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class, area, e -> e != player && e.isAlive())) {
            entity.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 200, 254));
            entity.addEffect(AbilityCommon.fx(MobEffects.DIG_SLOWDOWN, 200, 4));
        }

        // "x5" fantasy boost на себя
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SPEED, 200, 9));
        player.addEffect(AbilityCommon.fx(MobEffects.DIG_SPEED, 200, 9));
        player.addEffect(AbilityCommon.fx(MobEffects.REGENERATION, 200, 9));

        for (int i = 0; i < 100; i++) {
            double a = Math.random() * Math.PI * 2;
            double r = Math.random() * 30;
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(1f, 0.75f, 0.05f), 1.0f),
                    player.getX() + Math.cos(a) * r,
                    player.getY() + 0.5 + Math.random() * 2.0,
                    player.getZ() + Math.sin(a) * r,
                    1, 0, 0, 0, 0);
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "BULLET TIME: all entities slowed for 10s!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    public static void tickAfterimages(ServerLevel level) {
        Set<java.util.UUID> processed = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, player.getBoundingBox().inflate(64),
                    e -> e.getPersistentData().contains("occka_flash_afterimage"))) {
                if (!processed.add(stand.getUUID())) {
                    continue;
                }
                int life = stand.getPersistentData().getInt("occka_flash_afterimage") - 1;
                if (life <= 0) {
                    stand.discard();
                } else {
                    stand.getPersistentData().putInt("occka_flash_afterimage", life);
                }
            }
        }
    }

    private static ArmorStand spawnAfterimage(ServerLevel level, Vec3 base, float yRot, float xRot, int rgb,
            Vec3 offset) {
        ArmorStand ghost = new ArmorStand(EntityType.ARMOR_STAND, level);
        ghost.moveTo(base.x + offset.x, base.y + offset.y, base.z + offset.z, yRot, xRot);
        ghost.setNoGravity(true);
        ghost.setInvisible(false);
        ghost.setInvulnerable(true);
        ghost.setNoBasePlate(true);
        ghost.getPersistentData().putBoolean("NoArmorTake", true);

        // Copy a simple silhouette using dyed leather armor
        ghost.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, dyedLeather(Items.LEATHER_HELMET, rgb));
        ghost.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, dyedLeather(Items.LEATHER_CHESTPLATE, rgb));
        ghost.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, dyedLeather(Items.LEATHER_LEGGINGS, rgb));
        ghost.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, dyedLeather(Items.LEATHER_BOOTS, rgb));

        ghost.setCustomNameVisible(false);
        ghost.getPersistentData().putInt("occka_flash_afterimage", 38);

        level.addFreshEntity(ghost);
        return ghost;
    }

    private static void spawnRotatingAfterimage(ServerPlayer player, ServerLevel level) {
        int slot = Math.floorMod(player.getPersistentData().getInt("occka_flash_slot"), 3);
        int[] colors = { 0xFFD200, 0xFF7A00, 0xFF2A00 };

        String leastKey = "occka_flash_afterimage_" + slot + "_least";
        String mostKey = "occka_flash_afterimage_" + slot + "_most";

        if (player.getPersistentData().contains(leastKey) && player.getPersistentData().contains(mostKey)) {
            UUID prevId = new UUID(
                    player.getPersistentData().getLong(mostKey),
                    player.getPersistentData().getLong(leastKey));
            var prev = level.getEntity(prevId);
            if (prev != null) {
                prev.discard();
            }
        }

        Vec3 look = player.getLookAngle().normalize();
        Vec3 base = player.position();
        Vec3 offset = look.scale(-1.3);
        ArmorStand newest = spawnAfterimage(level, base, player.getYRot(), player.getXRot(), colors[slot], offset);
        player.getPersistentData().putLong(leastKey, newest.getUUID().getLeastSignificantBits());
        player.getPersistentData().putLong(mostKey, newest.getUUID().getMostSignificantBits());

        player.getPersistentData().putInt("occka_flash_slot", (slot + 1) % 3);
    }

    private static ItemStack dyedLeather(net.minecraft.world.item.Item item, int rgb) {
        ItemStack stack = new ItemStack(item);
        if (stack.getItem() instanceof DyeableLeatherItem dyeable) {
            dyeable.setColor(stack, rgb);
        }
        return stack;
    }

}