package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.ability.PowerType;
import net.minecraft.world.level.block.Blocks;
import com.occka.occkapowers.event.AbilityCommon;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class IceAbility {
    private IceAbility() {
    }

    public static void activateShift(ServerPlayer player, ServerLevel level) {

        AbilityCommon.getNearbyEnemies(player, 5).forEach(e -> {
            e.hurt(player.damageSources().playerAttack(player), 0.5f);
            e.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
        });

        for (int i = 0; i < 25; i++) {
            double angle = Math.random() * Math.PI * 2;
            double r = Math.random() * 5;

            level.sendParticles(
                    ParticleTypes.SNOWFLAKE,
                    player.getX() + r * Math.cos(angle),
                    player.getY() + Math.random() * 3,
                    player.getZ() + r * Math.sin(angle),
                    1,
                    (Math.random() - 0.5) * 0.2,
                    0.03,
                    (Math.random() - 0.5) * 0.2,
                    0);
        }

        int radius = 5;
        BlockPos center = player.blockPosition();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {

                    BlockPos pos = center.offset(x, y, z);
                    var state = level.getBlockState(pos);

                    if (state.is(net.minecraft.world.level.block.Blocks.WATER)) {

                        level.setBlock(pos,
                                net.minecraft.world.level.block.Blocks.ICE.defaultBlockState(),
                                3);

                        level.sendParticles(
                                ParticleTypes.SNOWFLAKE,
                                pos.getX() + 0.5,
                                pos.getY() + 0.5,
                                pos.getZ() + 0.5,
                                2,
                                0.2, 0.2, 0.2,
                                0.01);
                    }
                }
            }
        }
    }

    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        cageNearestEnemy(player, level);
    }

    private static void cageNearestEnemy(ServerPlayer player, ServerLevel level) {
        LivingEntity target = null;
        double minD = Double.MAX_VALUE;
        for (LivingEntity e : AbilityCommon.getNearbyEnemies(player, 12)) {
            double d = e.distanceTo(player);
            if (d < minD) {
                minD = d;
                target = e;
            }
        }
        if (target == null) {
            player.sendSystemMessage(AbilityCommon.msg("No targets!", ChatFormatting.RED));
            return;
        }

        var center = target.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) == 1 || dy == -1 || dy == 2 || Math.abs(dz) == 1) {
                        var pos = center.offset(dx, dy, dz);
                        var state = level.getBlockState(pos);
                        if (state.isAir() || state.canBeReplaced()) {
                            level.setBlock(pos, Blocks.BLUE_ICE.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
        var snowLow = center;
        var snowHigh = center.above();
        if (level.getBlockState(snowLow).isAir() || level.getBlockState(snowLow).canBeReplaced())
            level.setBlock(snowLow, Blocks.POWDER_SNOW.defaultBlockState(), 3);
        if (level.getBlockState(snowHigh).isAir() || level.getBlockState(snowHigh).canBeReplaced())
            level.setBlock(snowHigh, Blocks.POWDER_SNOW.defaultBlockState(), 3);

        level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1, target.getZ(), 60, 1, 1.5, 1,
                0.15);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getY() + 1, target.getZ(), 25, 0.5, 0.5,
                0.5, 0.2);
        player.sendSystemMessage(AbilityCommon.msg("Ice Cage!", ChatFormatting.AQUA));

        net.minecraft.nbt.ListTag cageList = new net.minecraft.nbt.ListTag();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) == 1 || dy == -1 || dy == 2 || Math.abs(dz) == 1) {
                        net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
                        entry.putLong("p", center.offset(dx, dy, dz).asLong());
                        cageList.add(entry);
                    }
                }
            }
        }
        net.minecraft.nbt.CompoundTag s1 = new net.minecraft.nbt.CompoundTag();
        s1.putLong("p", center.asLong());
        net.minecraft.nbt.CompoundTag s2 = new net.minecraft.nbt.CompoundTag();
        s2.putLong("p", center.above().asLong());
        cageList.add(s1);
        cageList.add(s2);

        player.getPersistentData().put("occka_ice_cage", cageList);
        player.getPersistentData().putLong("occka_ice_cage_remove_at", level.getGameTime() + 246); //remove ice cage
    }

    public static void tickIceCage(ServerPlayer player, ServerLevel level) {
        if (!player.getPersistentData().contains("occka_ice_cage"))
            return;
        if (level.getGameTime() < player.getPersistentData().getLong("occka_ice_cage_remove_at"))
            return;

        net.minecraft.nbt.ListTag list = player.getPersistentData()
                .getList("occka_ice_cage", net.minecraft.nbt.Tag.TAG_COMPOUND);

        for (net.minecraft.nbt.Tag t : list) {
            BlockPos pos = BlockPos.of(((net.minecraft.nbt.CompoundTag) t).getLong("p"));
            var state = level.getBlockState(pos);
            if (state.is(Blocks.BLUE_ICE) || state.is(Blocks.POWDER_SNOW)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        3, 0.2, 0.2, 0.2, 0.02);
            }
        }

        player.getPersistentData().remove("occka_ice_cage");
        player.getPersistentData().remove("occka_ice_cage_remove_at");
    }

    public static void spawnMinions(ServerPlayer player, ServerLevel level) {
        for (int i = 0; i < 2; i++) {
            WitherSkeleton minion = new WitherSkeleton(EntityType.WITHER_SKELETON, level);
            minion.moveTo(player.getX() + (i == 0 ? 3 : -3), player.getY(), player.getZ());
            minion.setCustomName(Component.literal("Ice Guardian").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            minion.setCustomNameVisible(true);
            minion.setPersistenceRequired();
            minion.addEffect(AbilityCommon.fx(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 1));
            minion.addEffect(AbilityCommon.fx(MobEffects.ABSORPTION, Integer.MAX_VALUE, 4));
            minion.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 0));
            minion.getPersistentData().putString("occka_owner", player.getUUID().toString());
            level.addFreshEntity(minion);
            level.sendParticles(ParticleTypes.SNOWFLAKE, minion.getX(), minion.getY() + 1, minion.getZ(), 50, 0.5, 1,
                    0.5, 0.15);
        }
        player.sendSystemMessage(AbilityCommon.msg("Ice Guardians summoned!", ChatFormatting.AQUA));
    }

    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> enemies = AbilityCommon.getNearbyEnemies(player, 15);
        for (LivingEntity entity : enemies) {
            entity.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 300, 10));
            entity.addEffect(AbilityCommon.fx(MobEffects.JUMP, 300, 128));
            entity.addEffect(AbilityCommon.fx(MobEffects.DIG_SLOWDOWN, 300, 10));
            entity.setDeltaMovement(0, entity.getDeltaMovement().y, 0);
            entity.hurtMarked = true;

            level.sendParticles(ParticleTypes.SNOWFLAKE, entity.getX(), entity.getY() + 1, entity.getZ(), 80, 0.8, 1.5,
                    0.8, 0.25);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    entity.getX(), entity.getY() + 1, entity.getZ(), 50, 0.8, 0.8, 0.8, 0.35);
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL, entity.getX(), entity.getY() + 1, entity.getZ(), 20, 0.4,
                    0.4, 0.4, 0.2);
        }

        player.addEffect(AbilityCommon.fx(MobEffects.DAMAGE_RESISTANCE, 300, 4));
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SPEED, 300, 2));
        player.getPersistentData().putInt("occka_ice_snowstorm_ticks", 300);

        for (int deg = 0; deg < 360; deg += 3) {
            for (double r = 1; r <= 25; r += 2) {
                double x = player.getX() + r * Math.cos(Math.toRadians(deg));
                double z = player.getZ() + r * Math.sin(Math.toRadians(deg));
                level.sendParticles(ParticleTypes.SNOWFLAKE, x, player.getY() + 0.2, z, 1, 0, 0.1, 0, 0.03);
            }
        }
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);

        for (Player p : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(30), x -> true)) {
            ((ServerPlayer) p).sendSystemMessage(
                    Component.literal(player.getName().getString() + " unleashed a BLIZZARD!")
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
    }
}
