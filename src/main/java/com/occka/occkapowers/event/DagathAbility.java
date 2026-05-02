package com.occka.occkapowers.event;

import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.form.PlayerFormData;
import com.occka.occkapowers.form.PlayerFormSync;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DagathAbility {
    private DagathAbility() {
    }

    public static final String NBT_BOAR_TICKS = "occka_dagath_boar_ticks";

    private static final String NBT_RIDE_PIG = "occka_dagath_ride_pig";
    private static final String NBT_HOMING_PIG = "occka_dagath_homing_pig";
    private static final String NBT_OWNER = "occka_dagath_owner";
    private static final String NBT_TARGET = "occka_dagath_target";
    private static final String NBT_LIFE = "occka_dagath_life";
    private static final String NBT_TEMP_CARROT = "occka_dagath_temp_carrot";
    private static final String NBT_PREV_FORM = "occka_dagath_prev_form";
    private static final String NBT_HAD_PREV_FORM = "occka_dagath_had_prev_form";

    private static final int NORMAL_PIG_COUNT = 3;
    private static final int BOAR_PIG_COUNT = 6;
    private static final double HOMING_RANGE = 25.0;
    private static final double HOMING_SPEED = 1.3;
    private static final double HOMING_DETONATE_RANGE = 2;
    private static final float HOMING_DAMAGE = 12.0f;

    public static int getAbilityCooldown(ServerPlayer player) {
        return isBoarForm(player) ? 120 : 400;
    }

    public static void tick(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        applyPassives(player, level);
        tickRidePig(player);
        tickBoarForm(player, level, data);
    }

    public static void tickHomingPigs(ServerLevel level) {
        Set<UUID> ticked = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            for (Pig pig : level.getEntitiesOfClass(Pig.class, player.getBoundingBox().inflate(96),
                    pig -> pig.getPersistentData().getBoolean(NBT_HOMING_PIG))) {
                if (ticked.add(pig.getUUID())) {
                    tickHomingPig(level, pig);
                }
            }
        }
    }

    public static void activateShift(ServerPlayer player, ServerLevel level) {
        if (isBoarForm(player)) {
            boarDash(player, level);
            return;
        }

        clearRidePig(player);
        Pig pig = EntityType.PIG.create(level);
        if (pig == null) {
            return;
        }

        pig.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
        pig.equipSaddle(SoundSource.PLAYERS);
        pig.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60, 4, false, false));
        pig.setPersistenceRequired();
        pig.getPersistentData().putBoolean(NBT_RIDE_PIG, true);
        pig.getPersistentData().putUUID(NBT_OWNER, player.getUUID());
        if (pig.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            pig.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(1.8);
        }
        level.addFreshEntity(pig);

        ensureCarrotOnAStick(player);
        player.startRiding(pig, true);
        player.sendSystemMessage(AbilityCommon.msg("Dagath mount!", ChatFormatting.DARK_GREEN));
    }

    public static boolean activateAbility(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> targets = findTargets(player, level, HOMING_RANGE);
        if (targets.isEmpty()) {
            player.sendSystemMessage(AbilityCommon.msg("No target for homing pigs.", ChatFormatting.GRAY));
            return false;
        }

        int count = isBoarForm(player) ? BOAR_PIG_COUNT : NORMAL_PIG_COUNT;
        spawnHomingPigsMultiTarget(player, level, targets, count);
        return true;
    }

    public static void activateUlt(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        CompoundTag tag = player.getPersistentData();
        if (tag.getInt(NBT_BOAR_TICKS) > 0) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 4, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 350, 1, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 350, 1, false, false));

        String previousForm = PlayerFormData.getForm(player);
        tag.putBoolean(NBT_HAD_PREV_FORM, !previousForm.isEmpty());
        tag.putString(NBT_PREV_FORM, previousForm);
        PlayerFormData.setForm(player, "ravager");
        PlayerFormSync.syncToTrackingAndSelf(player);

        tag.putInt(NBT_BOAR_TICKS, 300);
        setMaxHealth(player, 60.0);
        player.setHealth(60.0f);

        data.setShiftCooldown(0);
        data.setAbilityCooldown(0);
        player.sendSystemMessage(AbilityCommon.msg("BOAR FORM! 15s", ChatFormatting.DARK_GREEN, ChatFormatting.BOLD));
    }

    public static void clear(ServerPlayer player) {
        clearRidePig(player);
        clearTempCarrot(player);
        if (player.getPersistentData().getInt(NBT_BOAR_TICKS) > 0) {
            restoreForm(player);
            player.getPersistentData().putInt(NBT_BOAR_TICKS, 0);
        }
    }

    private static void applyPassives(ServerPlayer player, ServerLevel level) {
        BlockState under = level.getBlockState(player.blockPosition().below());
        if (under.is(Blocks.SAND) || under.is(Blocks.RED_SAND) || under.is(Blocks.SOUL_SAND)
                || under.is(Blocks.DIRT) || under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.COARSE_DIRT)
                || under.is(Blocks.ROOTED_DIRT) || under.is(Blocks.PODZOL)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 10, 1, false, false));
        }
    }

    private static void tickRidePig(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Pig pig && pig.getPersistentData().getBoolean(NBT_RIDE_PIG)) {
            pig.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 4, false, false));
            return;
        }
        clearRidePig(player);
    }

    private static void tickBoarForm(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        int ticks = player.getPersistentData().getInt(NBT_BOAR_TICKS);
        if (ticks <= 0) {
            return;
        }

        setMaxHealth(player, 60.0);
        ticks--;
        player.getPersistentData().putInt(NBT_BOAR_TICKS, ticks);
        if (player.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    player.getX(), player.getY() + 1.8, player.getZ(),
                    2, 0.4, 0.3, 0.4, 0.02);
        }

        if (ticks <= 0) {
            restoreForm(player);
            data.setUltCooldown(data.getPowerType().getUltCooldown());
            AbilityActivator.syncToClient(player, data);
            player.sendSystemMessage(AbilityCommon.msg("Boar form ended.", ChatFormatting.GRAY));
        }
    }

    private static void boarDash(ServerPlayer player, ServerLevel level) {
        Vec3 dir = horizontal(player.getLookAngle(), player.getYRot());
        player.setDeltaMovement(dir.x * 2.2, 0.08, dir.z * 2.2);
        player.hurtMarked = true;
        player.resetFallDistance();

        breakDashBlocks(player, level, dir);
        Vec3 front = player.position().add(dir.scale(2.5));
        AABB box = new AABB(front.x - 2.0, player.getY() - 0.5, front.z - 2.0,
                front.x + 2.0, player.getY() + 2.5, front.z + 2.0);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !(e instanceof Pig p && isDagathPig(p)))) {
            entity.setDeltaMovement(dir.x * 1.8, 0.55, dir.z * 1.8);
            entity.hurtMarked = true;
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false));
        }
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.5, player.getZ(),
                12, 0.5, 0.2, 0.5, 0.08);
    }

    // private static void spawnHomingPigs(ServerPlayer player, ServerLevel level,
    // LivingEntity target, int count) {
    // for (int i = 0; i < count; i++) {
    // Pig pig = EntityType.PIG.create(level);
    // if (pig == null) {
    // continue;
    // }
    //
    // double angle = (Math.PI * 2.0 * i) / count;
    // Vec3 offset = new Vec3(Math.cos(angle) * 1.2, 1.0, Math.sin(angle) * 1.2);
    // pig.moveTo(player.getX() + offset.x, player.getY() + offset.y, player.getZ()
    // + offset.z,
    // player.getYRot(), 0.0f);
    // pig.setNoAi(true);
    // pig.setNoGravity(true);
    // pig.setInvulnerable(true);
    // pig.getPersistentData().putBoolean(NBT_HOMING_PIG, true);
    // pig.getPersistentData().putUUID(NBT_OWNER, player.getUUID());
    // pig.getPersistentData().putUUID(NBT_TARGET, target.getUUID());
    // pig.getPersistentData().putInt(NBT_LIFE, 80);
    // level.addFreshEntity(pig);
    // }
    // }

    private static void spawnHomingPigsMultiTarget(ServerPlayer player, ServerLevel level,
            List<LivingEntity> targets, int count) {
        for (int i = 0; i < count; i++) {
            // round-robin по целям
            LivingEntity target = targets.get(i % targets.size());

            Pig pig = EntityType.PIG.create(level);
            if (pig == null)
                continue;

            double angle = (Math.PI * 2.0 * i) / count;
            Vec3 offset = new Vec3(Math.cos(angle) * 1.2, 1.0, Math.sin(angle) * 1.2);
            pig.moveTo(player.getX() + offset.x, player.getY() + offset.y, player.getZ() + offset.z,
                    player.getYRot(), 0.0f);
            pig.setNoAi(true);
            pig.setNoGravity(true);
            pig.setInvulnerable(true);
            pig.getPersistentData().putBoolean(NBT_HOMING_PIG, true);
            pig.getPersistentData().putUUID(NBT_OWNER, player.getUUID());
            pig.getPersistentData().putUUID(NBT_TARGET, target.getUUID());
            pig.getPersistentData().putInt(NBT_LIFE, 80);
            level.addFreshEntity(pig);
        }
    }

    private static void tickHomingPig(ServerLevel level, Pig pig) {
        CompoundTag tag = pig.getPersistentData();
        int life = tag.getInt(NBT_LIFE) - 1;
        tag.putInt(NBT_LIFE, life);
        if (life <= 0 || !tag.hasUUID(NBT_TARGET)) {
            pig.discard();
            return;
        }

        Entity targetEntity = level.getEntity(tag.getUUID(NBT_TARGET));
        if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
            pig.discard();
            return;
        }

        Vec3 toTarget = target.getEyePosition().subtract(pig.position());
        if (toTarget.length() <= HOMING_DETONATE_RANGE) {
            explodeHomingPig(level, pig, target);
            return;
        }

        Vec3 vel = toTarget.normalize().scale(HOMING_SPEED);
        pig.setDeltaMovement(vel);
        pig.setPos(pig.getX() + vel.x, pig.getY() + vel.y, pig.getZ() + vel.z);
        pig.hurtMarked = true;
        level.sendParticles(new DustParticleOptions(new Vector3f(0.35f, 0.9f, 0.25f), 0.9f),
                pig.getX(), pig.getY() + 0.4, pig.getZ(), 2, 0.15, 0.15, 0.15, 0.02);
    }

    private static void explodeHomingPig(ServerLevel level, Pig pig, LivingEntity target) {
        level.explode(pig, pig.getX(), pig.getY(), pig.getZ(), 1.8f, false, Level.ExplosionInteraction.NONE);
        target.hurt(level.damageSources().explosion(pig, pig), HOMING_DAMAGE);
        level.sendParticles(ParticleTypes.EXPLOSION, pig.getX(), pig.getY() + 0.5, pig.getZ(),
                4, 0.3, 0.3, 0.3, 0.04);
        pig.discard();
    }

    // Возвращает список: сначала игроки, потом мобы, отсортированы по дистанции
    private static List<LivingEntity> findTargets(ServerPlayer player, ServerLevel level, double range) {
        List<LivingEntity> all = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range),
                e -> e != player && e.isAlive()
                        && !(e instanceof Pig p && isDagathPig(p))
                        && !(e instanceof Player p && p.isAlliedTo(player))
                        && hasLineOfSight(level, player.getEyePosition(), e));

        all.sort(Comparator
                .<LivingEntity, Integer>comparing(e -> e instanceof Player ? 0 : 1)
                .thenComparingDouble(e -> e.distanceToSqr(player)));

        return all;
    }

    private static boolean hasLineOfSight(ServerLevel level, Vec3 from, LivingEntity to) {
        net.minecraft.world.phys.BlockHitResult hit = level.clip(
                new net.minecraft.world.level.ClipContext(
                        from,
                        to.getEyePosition(),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        to));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private static void clearRidePig(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        UUID owner = player.getUUID();
        List<Pig> pigs = level.getEntitiesOfClass(Pig.class, player.getBoundingBox().inflate(64),
                pig -> pig.getPersistentData().getBoolean(NBT_RIDE_PIG)
                        && pig.getPersistentData().hasUUID(NBT_OWNER)
                        && pig.getPersistentData().getUUID(NBT_OWNER).equals(owner));
        for (Pig pig : pigs) {
            pig.discard();
        }
        clearTempCarrot(player);
    }

    private static void ensureCarrotOnAStick(ServerPlayer player) {
        if (player.getMainHandItem().is(Items.CARROT_ON_A_STICK)
                || player.getOffhandItem().is(Items.CARROT_ON_A_STICK)) {
            return;
        }

        ItemStack stack = new ItemStack(Items.CARROT_ON_A_STICK);
        stack.getOrCreateTag().putBoolean(NBT_TEMP_CARROT, true);
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        } else {
            player.getInventory().add(stack);
        }
    }

    private static void clearTempCarrot(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.CARROT_ON_A_STICK) && stack.hasTag()
                    && stack.getTag().getBoolean(NBT_TEMP_CARROT)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }

        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(8),
                item -> item.getItem().is(Items.CARROT_ON_A_STICK)
                        && item.getItem().hasTag()
                        && item.getItem().getTag().getBoolean(NBT_TEMP_CARROT))) {
            item.discard();
        }
    }

    private static void restoreForm(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        if (tag.getBoolean(NBT_HAD_PREV_FORM)) {
            PlayerFormData.setForm(player, tag.getString(NBT_PREV_FORM));
        } else {
            PlayerFormData.clearForm(player);
        }
        tag.remove(NBT_HAD_PREV_FORM);
        tag.remove(NBT_PREV_FORM);
        setMaxHealth(player, 20.0);
        PlayerFormSync.syncToTrackingAndSelf(player);
    }

    private static void setMaxHealth(ServerPlayer player, double maxHealth) {
        if (player.getAttribute(Attributes.MAX_HEALTH) != null
                && player.getAttribute(Attributes.MAX_HEALTH).getBaseValue() != maxHealth) {
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
            if (player.getHealth() > maxHealth) {
                player.setHealth((float) maxHealth);
            }
        }
    }

    private static void breakDashBlocks(ServerPlayer player, ServerLevel level, Vec3 dir) {
        Vec3 perp = new Vec3(-dir.z, 0, dir.x).normalize();
        int baseY = player.blockPosition().getY();
        for (double forward = 0.5; forward <= 5.0; forward += 0.5) {
            for (double side = -1.0; side <= 1.0; side += 1.0) {
                for (int dy = 0; dy <= 2; dy++) {
                    Vec3 p = player.position().add(dir.scale(forward)).add(perp.scale(side));
                    BlockPos bp = BlockPos.containing(p.x, baseY + dy, p.z);
                    BlockState state = level.getBlockState(bp);
                    if (state.isAir() || state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)
                            || state.getDestroySpeed(level, bp) < 0) {
                        continue;
                    }
                    level.removeBlock(bp, false);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5,
                            3, 0.2, 0.2, 0.2, 0.08);
                }
            }
        }
    }

    private static Vec3 horizontal(Vec3 dir, float fallbackYaw) {
        Vec3 horizontal = new Vec3(dir.x, 0, dir.z);
        if (horizontal.lengthSqr() > 1.0E-4) {
            return horizontal.normalize();
        }

        double yaw = Math.toRadians(fallbackYaw);
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).normalize();
    }

    private static boolean isBoarForm(ServerPlayer player) {
        return player.getPersistentData().getInt(NBT_BOAR_TICKS) > 0;
    }

    private static boolean isDagathPig(Pig pig) {
        return pig.getPersistentData().getBoolean(NBT_RIDE_PIG)
                || pig.getPersistentData().getBoolean(NBT_HOMING_PIG);
    }
}
