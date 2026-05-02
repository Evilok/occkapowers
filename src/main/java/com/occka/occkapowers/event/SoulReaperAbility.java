package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

public final class SoulReaperAbility {
    private SoulReaperAbility() {}

    public static final String NBT_SOUL_CHARGE       = "occka_reaper_soul_charge";
    public static final String NBT_FORM_ACTIVE       = "occka_reaper_form_active";
    public static final String NBT_DRAIN_TARGET      = "occka_reaper_drain_target";
    public static final String NBT_DRAIN_TICKS       = "occka_reaper_drain_ticks";
    public static final String NBT_HORSE_DATA        = "occka_reaper_horse_data";

    public static final int SOUL_MAX              = 100;
    public static final int FORM_DRAIN_INTERVAL   = 24; 
    public static final int SOUL_PER_HIT          = 0;   
    public static final int SOUL_DRAIN_ABILITY    = 20; 
    public static final int SOUL_CHAIN_THRESHOLD  = 50; 
    public static final int SOUL_CHAIN_COST       = 40;  
    public static final int AURA_DAMAGE_INTERVAL  = 35;  
    public static final float AURA_DAMAGE         = 1.5f;
    public static final double AURA_RADIUS        = 5.0;
    public static final int DRAIN_DURATION        = 40;  

    public static final int PASSIVE_REGEN_INTERVAL_OUT = 10;
    public static final int PASSIVE_REGEN_INTERVAL_IN  = 40;

    // ===== SOUL CHARGE UTIL =====

    public static int getSoulCharge(ServerPlayer player) {
        return player.getPersistentData().getInt(NBT_SOUL_CHARGE);
    }

    public static void setSoulCharge(ServerPlayer player, int amount) {
        player.getPersistentData().putInt(NBT_SOUL_CHARGE,
                Math.max(0, Math.min(SOUL_MAX, amount)));
    }

    public static void addSoulCharge(ServerPlayer player, int amount) {
        setSoulCharge(player, getSoulCharge(player) + amount);
    }

    public static boolean isFormActive(ServerPlayer player) {
        return player.getPersistentData().getBoolean(NBT_FORM_ACTIVE);
    }

    public static void clearForm(Player player) {
        player.getPersistentData().putBoolean(NBT_FORM_ACTIVE, false);
        player.getPersistentData().remove(NBT_DRAIN_TARGET);
        player.getPersistentData().putInt(NBT_DRAIN_TICKS, 0);
    }

    // Проверяем — является ли сущность нежитью (скелет, скелет-лошадь и т.п.)
    private static boolean isUndead(LivingEntity entity) {
        return entity instanceof AbstractSkeleton
                || entity instanceof SkeletonHorse
                || entity instanceof net.minecraft.world.entity.monster.Zombie
                || entity instanceof net.minecraft.world.entity.monster.ZombieVillager
                || entity instanceof net.minecraft.world.entity.monster.Drowned
                || entity instanceof net.minecraft.world.entity.monster.Husk
                || entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || entity instanceof net.minecraft.world.entity.monster.WitherSkeleton
                || entity instanceof net.minecraft.world.entity.monster.ZombifiedPiglin
                || entity instanceof net.minecraft.world.entity.monster.Phantom
                || entity instanceof net.minecraft.world.entity.monster.Stray;
    }

    // ===== SHIFT — Hellfire Form (переключатель) =====

    public static void activateShift(ServerPlayer player, ServerLevel level) {
        boolean active = isFormActive(player);
        if (!active) {
            enableForm(player, level);
        } else {
            disableForm(player, level);
        }
    }

    private static void enableForm(ServerPlayer player, ServerLevel level) {

        int currentSoul = getSoulCharge(player);
        if (currentSoul <= 0) {
            setSoulCharge(player, SOUL_MAX);
        }

        player.getPersistentData().putBoolean(NBT_FORM_ACTIVE, true);

        player.getPersistentData().putString("occka_reaper_old_helmet",
                net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(player.getItemBySlot(EquipmentSlot.HEAD).getItem()).toString());
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.WITHER_SKELETON_SKULL));

        if (player.getVehicle() instanceof Horse horse) {
            transformHorseToSkeleton(player, horse, level);
        }

        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 1, player.getZ(),
                30, 0.6, 0.8, 0.6, 0.12);
        level.sendParticles(ParticleTypes.SOUL,
                player.getX(), player.getY() + 1, player.getZ(),
                20, 0.5, 0.5, 0.5, 0.1);
        level.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1, player.getZ(),
                1, 0, 0, 0, 0);

        player.sendSystemMessage(AbilityCommon.msg(
                "HELLFIRE FORM: ON  [Soul: " + getSoulCharge(player) + "]",
                ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
    }

    private static void disableForm(ServerPlayer player, ServerLevel level) {
        player.getPersistentData().putBoolean(NBT_FORM_ACTIVE, false);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);

        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                player.getX(), player.getY() + 1, player.getZ(),
                15, 0.5, 0.5, 0.5, 0.05);

        player.sendSystemMessage(AbilityCommon.msg(
                "Hellfire Form: OFF  [Soul saved: " + getSoulCharge(player) + "]",
                ChatFormatting.DARK_GRAY));
    }

    // ===== TICK формы — аура урона + расход Soul Charge =====

    public static void tickForm(ServerPlayer player, ServerLevel level) {
        if (!isFormActive(player)) return;

        if (player.tickCount % FORM_DRAIN_INTERVAL == 0) {
            int soul = getSoulCharge(player);
            soul = Math.max(0, soul - 1);
            setSoulCharge(player, soul);

            if (soul <= 0) {
                disableForm(player, level);
                player.sendSystemMessage(AbilityCommon.msg(
                        "Soul charge depleted! Form disabled.", ChatFormatting.DARK_GRAY));
                return;
            }
        }

        if (player.tickCount % PASSIVE_REGEN_INTERVAL_IN == 0) {
            // Регенерируем только если заряд не полный и не идёт расход до нуля
            int soul = getSoulCharge(player);
            if (soul > 0 && soul < SOUL_MAX) {
                setSoulCharge(player, soul + 1);
            }
        }

        if (player.tickCount % AURA_DAMAGE_INTERVAL == 0) {
            AABB box = player.getBoundingBox().inflate(AURA_RADIUS);
            List<LivingEntity> nearby = level.getEntitiesOfClass(
                    LivingEntity.class, box, e -> e != player && e.isAlive());

            for (LivingEntity entity : nearby) {
                if (isUndead(entity)) continue;
                entity.hurt(player.damageSources().magic(), AURA_DAMAGE);
            }

            List<Mob> undeadMobs = level.getEntitiesOfClass(
                    Mob.class, player.getBoundingBox().inflate(20),
                    mob -> isUndead(mob) && mob.getTarget() != null
                            && mob.getTarget().getUUID().equals(player.getUUID()));
            for (Mob mob : undeadMobs) {
                mob.setTarget(null);
            }
        }

        if (player.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 1, player.getZ(),
                    2, 0.3, 0.4, 0.3, 0.04);
        }

        if (player.getVehicle() instanceof SkeletonHorse horse && player.tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    horse.getX(), horse.getY() + 0.5, horse.getZ(),
                    3, 0.4, 0.3, 0.4, 0.06);
        }
    }

    public static void tickPassive(ServerPlayer player, ServerLevel level) {
        if (isFormActive(player)) return; // в форме своя регенерация в tickForm

        // Восстановление вне формы: +1 каждые PASSIVE_REGEN_INTERVAL_OUT тиков
        if (player.tickCount % PASSIVE_REGEN_INTERVAL_OUT == 0) {
            addSoulCharge(player, 1);
        }
    }

    public static void onPlayerHitEntity(ServerPlayer player) {
        addSoulCharge(player, SOUL_PER_HIT);
    }

    // ===== ABILITY (F) — Soul Drain =====

    public static boolean activateAbility(ServerPlayer player, ServerLevel level) {
        if (getSoulCharge(player) < SOUL_DRAIN_ABILITY) {
            player.sendSystemMessage(AbilityCommon.msg(
                    "Not enough soul charge! Need " + SOUL_DRAIN_ABILITY, ChatFormatting.DARK_GRAY));
            return false;
        }

        LivingEntity target = null;
        double minD = Double.MAX_VALUE;
        for (LivingEntity e : AbilityCommon.getNearbyEnemies(player, 3.5)) {
            if (isUndead(e)) continue; // нежить пропускаем
            double d = e.distanceTo(player);
            if (d < minD) { minD = d; target = e; }
        }

        if (target == null) {
            player.sendSystemMessage(AbilityCommon.msg(
                    "No target nearby! (3 blocks)", ChatFormatting.DARK_GRAY));
            return false;
        }

        addSoulCharge(player, -SOUL_DRAIN_ABILITY);

        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, DRAIN_DURATION, 127));
        target.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, DRAIN_DURATION, 127));
        target.addEffect(AbilityCommon.fx(MobEffects.WEAKNESS, DRAIN_DURATION, 127));

        player.getPersistentData().putUUID(NBT_DRAIN_TARGET, target.getUUID());
        player.getPersistentData().putInt(NBT_DRAIN_TICKS, DRAIN_DURATION);

        level.sendParticles(ParticleTypes.SOUL,
                target.getX(), target.getY() + 1, target.getZ(),
                25, 0.4, 0.6, 0.4, 0.1);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(), target.getY() + 1, target.getZ(),
                15, 0.3, 0.5, 0.3, 0.08);
        level.sendParticles(ParticleTypes.FLASH,
                target.getX(), target.getY() + 1, target.getZ(),
                1, 0, 0, 0, 0);

        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 1, player.getZ(),
                10, 0.3, 0.5, 0.3, 0.06);

        player.sendSystemMessage(AbilityCommon.msg(
                "Soul Drain!", ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
        return true;
    }

    /** Тикает высасывание HP каждый тик. Вызывается из AbilityEventHandler */
    public static void tickDrain(ServerPlayer player, ServerLevel level) {
        var nbt = player.getPersistentData();
        int ticks = nbt.getInt(NBT_DRAIN_TICKS);
        if (ticks <= 0) return;

        ticks--;
        nbt.putInt(NBT_DRAIN_TICKS, ticks);

        if (!nbt.hasUUID(NBT_DRAIN_TARGET)) return;

        java.util.UUID tid = nbt.getUUID(NBT_DRAIN_TARGET);
        LivingEntity target = null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(20),
                e -> e.getUUID().equals(tid) && e.isAlive())) {
            target = e; break;
        }

        if (target == null) {
            nbt.putInt(NBT_DRAIN_TICKS, 0);
            nbt.remove(NBT_DRAIN_TARGET);
            return;
        }

        if (ticks % 4 == 0) {
            target.hurt(player.damageSources().magic(), 1.2f);
            player.heal(1.2f);
            addSoulCharge(player, 3);
        }

        Vec3 from = target.position().add(0, target.getBbHeight() * 0.7, 0);
        Vec3 to   = player.position().add(0, player.getBbHeight() * 0.7, 0);
        int steps = Math.max(4, (int)(from.distanceTo(to) / 0.4));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 p = from.lerp(to, t);

            level.sendParticles(ParticleTypes.SOUL,
                    p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);

            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.02);
            }
        }

        if (ticks % 8 == 0) {
            level.sendParticles(new DustParticleOptions(new Vector3f(0.3f, 0.0f, 0.6f), 1.2f),
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    6, 0.2, 0.2, 0.2, 0);
        }

        if (ticks % 6 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 1, player.getZ(),
                    3, 0.2, 0.3, 0.2, 0.04);
        }

        if (ticks <= 0) {
            nbt.remove(NBT_DRAIN_TARGET);
            player.sendSystemMessage(AbilityCommon.msg("Drain complete.", ChatFormatting.DARK_GRAY));
        }
    }

    // ===== ULT (G) — Chain of Judgment =====

    public static boolean activateUlt(ServerPlayer player, ServerLevel level) {
        int soul = getSoulCharge(player);

        if (soul < SOUL_CHAIN_COST) {
            player.sendSystemMessage(AbilityCommon.msg(
                    "Not enough soul charge! Need " + SOUL_CHAIN_COST, ChatFormatting.DARK_GRAY));
            return false;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        double range = 20.0;

        boolean multitarget = soul >= SOUL_CHAIN_THRESHOLD;

        if (multitarget) {
            List<LivingEntity> targets = AbilityCommon.getNearbyEnemies(player, 15);
            targets.removeIf(e -> isUndead(e));
            targets.sort((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)));
            int count = Math.min(3, targets.size());

            if (count == 0) {
                player.sendSystemMessage(AbilityCommon.msg("No targets!", ChatFormatting.DARK_GRAY));
                return false;
            }

            setSoulCharge(player, soul - SOUL_CHAIN_COST);

            for (int i = 0; i < count; i++) {
                LivingEntity t = targets.get(i);
                pullTarget(player, t, level);
                drawChain(level, player.getEyePosition(), t.position().add(0, 1, 0));
            }

            player.sendSystemMessage(AbilityCommon.msg(
                    "CHAIN OF JUDGMENT! x" + count + " targets!",
                    ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
            return true;
        } else {
            LivingEntity target = findTargetInBeam(player, level, eye, dir, range);

            if (target == null) {
                player.sendSystemMessage(AbilityCommon.msg("No target in sight!", ChatFormatting.DARK_GRAY));
                return false;
            }

            setSoulCharge(player, soul - SOUL_CHAIN_COST);
            pullTarget(player, target, level);
            drawChain(level, eye, target.position().add(0, 1, 0));

            player.sendSystemMessage(AbilityCommon.msg(
                    "Chain of Judgment!", ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
            return true;
        }
    }

    private static void pullTarget(ServerPlayer player, LivingEntity target, ServerLevel level) {
        Vec3 dir = player.position().add(0, 1, 0)
                .subtract(target.position()).normalize();
        double speed = Math.min(2.8, 0.6 + target.distanceTo(player) * 0.15);
        target.setDeltaMovement(dir.x * speed, Math.max(dir.y * speed, 0.3), dir.z * speed);
        target.hurtMarked = true;
        target.hurt(player.damageSources().magic(), 10.0f);
        target.setSecondsOnFire(4);
        target.addEffect(AbilityCommon.fx(MobEffects.WITHER, 60, 0));

        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(), target.getY() + 1, target.getZ(),
                15, 0.4, 0.5, 0.4, 0.08);
    }

    private static void drawChain(ServerLevel level, Vec3 from, Vec3 to) {
        int steps = (int)(from.distanceTo(to) / 0.5);
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : i / (double) steps;
            Vec3 p = from.lerp(to, t);
            level.sendParticles(ParticleTypes.SOUL,
                    p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.02);
            }
        }
    }

    private static LivingEntity findTargetInBeam(ServerPlayer player, ServerLevel level,
                                                   Vec3 eye, Vec3 dir, double range) {
        AABB box = player.getBoundingBox().inflate(range + 2);
        java.util.List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class, box,
                e -> e != player && e.isAlive() && !isUndead(e)); // нежить исключена

        LivingEntity closest = null;
        double closestDot = Double.MAX_VALUE;

        for (LivingEntity e : candidates) {
            Vec3 toE = e.getEyePosition().subtract(eye);
            double dot = toE.dot(dir);
            if (dot > 0 && dot < range) {
                Vec3 proj = eye.add(dir.scale(dot));
                double hitR = 1.4 + e.getBbWidth() * 0.5;
                if (proj.distanceTo(e.getEyePosition()) < hitR && dot < closestDot) {
                    closestDot = dot;
                    closest = e;
                }
            }
        }
        return closest;
    }

    // ===== ЛОШАДЬ → СКЕЛЕТ-ЛОШАДЬ =====

    private static void transformHorseToSkeleton(ServerPlayer player, Horse horse, ServerLevel level) {
        Vec3 pos = horse.position();
        float yRot = horse.getYRot();

        player.getPersistentData().putUUID(NBT_HORSE_DATA, horse.getUUID());

        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                pos.x, pos.y + 1, pos.z, 20, 0.5, 0.5, 0.5, 0.08);
        horse.discard();

        SkeletonHorse skelHorse = new SkeletonHorse(EntityType.SKELETON_HORSE, level);
        skelHorse.moveTo(pos.x, pos.y, pos.z, yRot, 0);
        skelHorse.setTamed(true);
        skelHorse.setOwnerUUID(player.getUUID());
        skelHorse.setPersistenceRequired();
        var maxHealth = skelHorse.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(40);
        }
        skelHorse.setHealth(40);
        skelHorse.getPersistentData().putString("occka_reaper_skelhorse", player.getUUID().toString());
        level.addFreshEntity(skelHorse);

        player.startRiding(skelHorse, true);

        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                pos.x, pos.y + 1, pos.z, 30, 0.5, 0.7, 0.5, 0.1);
        level.sendParticles(ParticleTypes.SOUL,
                pos.x, pos.y + 1, pos.z, 20, 0.4, 0.5, 0.4, 0.08);

        player.sendSystemMessage(AbilityCommon.msg(
                "Hellhorse summoned!", ChatFormatting.DARK_GRAY));
    }
}