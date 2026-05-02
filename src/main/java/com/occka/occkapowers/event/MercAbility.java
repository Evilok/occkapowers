package com.occka.occkapowers.event;

import com.occka.occkapowers.ability.PlayerPowerData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class MercAbility {
    private MercAbility() {}

    private static final Random RNG = new Random();

    // ===== NBT ключи =====
    public static final String NBT_MADNESS        = "occka_merc_madness";
    public static final String NBT_NO_DAMAGE_TICKS = "occka_merc_nodmg_ticks";
    public static final String NBT_OVERCOOK_TICKS  = "occka_merc_overcook_ticks";
    public static final String NBT_OVERCOOK_100    = "occka_merc_overcook_100_ticks";

    // Ability combo state
    public static final String NBT_COMBO_STEP      = "occka_merc_combo_step";    // 0 = нет, 1 = ждём 2-е нажатие
    public static final String NBT_COMBO_TARGET    = "occka_merc_combo_target";  // UUID цели
    public static final String NBT_COMBO_TICKS     = "occka_merc_combo_ticks";   // осталось тиков до сброса

    // Ult state
    public static final String NBT_ULT_ACTIVE      = "occka_merc_ult_active";
    public static final String NBT_ULT_TICKS       = "occka_merc_ult_ticks";
    public static final String NBT_DEBT            = "occka_merc_debt";
    public static final String NBT_DEBT_PAY_TICKS  = "occka_merc_debt_pay_ticks";
    public static final String NBT_ULT_SHIFT_BOOST = "occka_merc_ult_shift_boost";

    // ===== MADNESS UTIL =====

    public static int getMadness(ServerPlayer player) {
        return player.getPersistentData().getInt(NBT_MADNESS);
    }

    public static void addMadness(ServerPlayer player, int amount) {
        int m = Math.min(100, getMadness(player) + amount);
        player.getPersistentData().putInt(NBT_MADNESS, m);
    }

    /** Вызывается каждый тик из AbilityEventHandler */
    public static void tickMadness(ServerPlayer player, ServerLevel level) {
        var nbt = player.getPersistentData();
        int madness = nbt.getInt(NBT_MADNESS);

        // Спад madness: -2 в секунду = -2 каждые 10 тиков
        if (player.tickCount % 10 == 0 && madness > 0) {
            madness = Math.max(0, madness - 2);
            nbt.putInt(NBT_MADNESS, madness);
        }

        // Если держится на 100 — накапливаем счётчик overcook
        if (madness >= 100) {
            int c100 = nbt.getInt(NBT_OVERCOOK_100) + 1;
            nbt.putInt(NBT_OVERCOOK_100, c100);
            // 8 секунд = 160 тиков
            if (c100 >= 160) {
                nbt.putInt(NBT_OVERCOOK_100, 0);
                nbt.putInt(NBT_OVERCOOK_TICKS, 100); // 5 сек реген отключён
                player.sendSystemMessage(AbilityCommon.msg("OVERCOOK! Regen suppressed 5s.", ChatFormatting.DARK_RED));
            }
        } else {
            nbt.putInt(NBT_OVERCOOK_100, 0);
        }

        // Berserk-толчок (80+) каждые 60 тиков
        if (madness >= 80 && player.tickCount % 60 == 0) {
            double rx = (RNG.nextDouble() - 0.5) * 0.35;
            double rz = (RNG.nextDouble() - 0.5) * 0.35;
            player.setDeltaMovement(player.getDeltaMovement().add(rx, 0, rz));
            player.hurtMarked = true;
        }

        // Тик Overcook
        int overcook = nbt.getInt(NBT_OVERCOOK_TICKS);
        if (overcook > 0) {
            nbt.putInt(NBT_OVERCOOK_TICKS, overcook - 1);
        }

        // Визуальные эффекты по уровню madness (каждые 60 тиков — уже в spawnAuraParticles)
        // Дополнительные частицы каждые 20 тиков
        if (player.tickCount % 20 == 0) {
            if (madness >= 80) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        player.getX(), player.getY() + 1, player.getZ(),
                        4, 0.4, 0.5, 0.4, 0.05);
            } else if (madness >= 40) {
                level.sendParticles(ParticleTypes.WITCH,
                        player.getX(), player.getY() + 1, player.getZ(),
                        3, 0.3, 0.4, 0.3, 0.05);
            }
        }
    }

    // ===== SHIFT — Cutscene Cancel =====

    public static void activateShift(ServerPlayer player, ServerLevel level) {
        int madness = getMadness(player);

        Vec3 start = player.position();
        Vec3 look = player.getLookAngle().normalize();
        double dashDist = 6.0;

        // I-frame: кратковременное сопротивление
        player.addEffect(AbilityCommon.fx(MobEffects.DAMAGE_RESISTANCE, 3, 4));

        // Рывок
        player.setDeltaMovement(look.scale(1.8).add(0, 0.15, 0));
        player.hurtMarked = true;

        // Проверяем врагов на пути рывка
        Set<java.util.UUID> hit = new HashSet<>();
        Vec3 end = start.add(look.scale(dashDist));
        for (int i = 1; i <= 12; i++) {
            Vec3 p = start.lerp(end, i / 12.0);
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(p, p).inflate(0.9),
                    e -> e != player && e.isAlive());
            for (LivingEntity e : victims) {
                if (hit.add(e.getUUID())) {
                    e.hurt(player.damageSources().magic(), 4.0f);
                    e.addEffect(AbilityCommon.fx(MobEffects.POISON, 40, 0)); // 2 сек яд
                    level.sendParticles(ParticleTypes.CRIT,
                            e.getX(), e.getY() + 1, e.getZ(),
                            8, 0.3, 0.3, 0.3, 0.1);
                }
            }
        }

        // Частицы рывка
        for (int i = 0; i < 18; i++) {
            double t = i / 17.0;
            Vec3 p = start.lerp(end, t);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.8f, 0.05f, 0.05f), 0.7f),
                    p.x, p.y + 1, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
        level.sendParticles(ParticleTypes.POOF, start.x, start.y + 1, start.z, 10, 0.4, 0.3, 0.4, 0.08);

        // Unhinged (40+): afterimage за спиной
        if (madness >= 40) {
            spawnShiftAfterimage(player, level, start);
        }

        player.sendSystemMessage(AbilityCommon.msg(
                madness >= 80 ? "BERSERK CANCEL!" :
                madness >= 40 ? "Unhinged Cancel!" : "Cutscene Cancel!",
                ChatFormatting.DARK_RED));
    }

    /** ArmorStand-afterimage после рывка. Через 15 тиков наносит урон ближайшему врагу */
    private static void spawnShiftAfterimage(ServerPlayer player, ServerLevel level, Vec3 pos) {
        ArmorStand ghost = new ArmorStand(EntityType.ARMOR_STAND, level);
        ghost.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0);
        ghost.setNoGravity(true);
        ghost.setInvulnerable(true);
        ghost.setNoBasePlate(true);
        ghost.setItemSlot(EquipmentSlot.HEAD,     dyedLeather(Items.LEATHER_HELMET,     0x1A0000));
        ghost.setItemSlot(EquipmentSlot.CHEST,    dyedLeather(Items.LEATHER_CHESTPLATE, 0xCC0000));
        ghost.setItemSlot(EquipmentSlot.LEGS,     dyedLeather(Items.LEATHER_LEGGINGS,   0x1A0000));
        ghost.setItemSlot(EquipmentSlot.FEET,     dyedLeather(Items.LEATHER_BOOTS,      0x1A0000));
        ghost.getPersistentData().putInt("occka_merc_afterimage", 15);
        ghost.getPersistentData().putString("occka_merc_afterimage_owner", player.getUUID().toString());
        level.addFreshEntity(ghost);
    }

    /** Тикается в AbilityEventHandler каждый тик */
    public static void tickAfterimages(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            level.getEntitiesOfClass(ArmorStand.class,
                    player.getBoundingBox().inflate(64),
                    e -> e.getPersistentData().contains("occka_merc_afterimage")
                            && e.getPersistentData().getString("occka_merc_afterimage_owner")
                                .equals(player.getUUID().toString()))
            .forEach(stand -> {
                int life = stand.getPersistentData().getInt("occka_merc_afterimage") - 1;
                if (life <= 0) {
                    // Урон ближайшему врагу
                    LivingEntity nearest = null;
                    double minD = Double.MAX_VALUE;
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                            stand.getBoundingBox().inflate(3.0),
                            e -> e != player && e != stand && e.isAlive())) {
                        double d = e.distanceTo(stand);
                        if (d < minD) { minD = d; nearest = e; }
                    }
                    if (nearest != null) {
                        nearest.hurt(level.damageSources().magic(), 6.0f);
                        level.sendParticles(ParticleTypes.CRIT,
                                nearest.getX(), nearest.getY() + 1, nearest.getZ(),
                                12, 0.4, 0.4, 0.4, 0.15);
                    }
                    level.sendParticles(ParticleTypes.POOF,
                            stand.getX(), stand.getY() + 1, stand.getZ(),
                            6, 0.3, 0.3, 0.3, 0.05);
                    stand.discard();
                } else {
                    stand.getPersistentData().putInt("occka_merc_afterimage", life);
                }
            });
        }
    }

    // ===== ABILITY — Script Rewrite (комбо) =====

    public static void activateAbility(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        var nbt = player.getPersistentData();
        int step = nbt.getInt(NBT_COMBO_STEP);

        if (step == 1) {
            // Второе нажатие — выполняем завершение
            executeComboFinish(player, level, data);
        } else {
            // Первое нажатие — рывок к цели + expose
            executeComboFirst(player, level);
        }
    }

    private static void executeComboFirst(ServerPlayer player, ServerLevel level) {
        // Ищем ближайшего врага в радиусе 8
        LivingEntity target = null;
        double minD = Double.MAX_VALUE;
        for (LivingEntity e : AbilityCommon.getNearbyEnemies(player, 8)) {
            double d = e.distanceTo(player);
            if (d < minD) { minD = d; target = e; }
        }

        if (target == null) {
            player.sendSystemMessage(AbilityCommon.msg("No target in range!", ChatFormatting.RED));
            return;
        }

        // Рывок к цели
        Vec3 dir = target.position().subtract(player.position()).normalize();
        player.setDeltaMovement(dir.scale(1.6).add(0, 0.2, 0));
        player.hurtMarked = true;

        // Урон 8
        target.hurt(player.damageSources().playerAttack(player), 6.3f);
        addMadness(player, 10);

        // Expose тег
        target.getPersistentData().putBoolean("merc_exposed", true);
        target.getPersistentData().putInt("merc_exposed_ticks", 60);

        // Unhinged (40+): уничтожаем снаряды рядом
        if (getMadness(player) >= 40) {
            level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                    player.getBoundingBox().inflate(3.0), e -> true)
                .forEach(p -> {
                    level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY(), p.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
                    p.discard();
                });
        }

        // Записываем состояние комбо
        var nbt = player.getPersistentData();
        nbt.putInt(NBT_COMBO_STEP, 1);
        nbt.putUUID(NBT_COMBO_TARGET, target.getUUID());
        nbt.putInt(NBT_COMBO_TICKS, 24); // 1.2 секунды

        // Частицы
        level.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.1f, 0.1f), 1.0f),
                target.getX(), target.getY() + 1, target.getZ(), 20, 0.4, 0.6, 0.4, 0.1);
        level.sendParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + 1, target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);

        player.sendSystemMessage(AbilityCommon.msg(
                getMadness(player) >= 40 ? "[Unhinged] Expose! Press again!" : "Expose! Press again!",
                ChatFormatting.DARK_RED));
    }

    private static void executeComboFinish(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        var nbt = player.getPersistentData();
        nbt.putInt(NBT_COMBO_STEP, 0);

        if (!nbt.hasUUID(NBT_COMBO_TARGET)) {
            data.setAbilityCooldown(data.getPowerType().getAbilityCooldown());
            return;
        }

        java.util.UUID tid = nbt.getUUID(NBT_COMBO_TARGET);
        nbt.remove(NBT_COMBO_TARGET);
        nbt.putInt(NBT_COMBO_TICKS, 0);

        LivingEntity target = null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(30), e -> e.getUUID().equals(tid) && e.isAlive())) {
            target = e; break;
        }

        if (target == null) {
            player.sendSystemMessage(AbilityCommon.msg("Target lost!", ChatFormatting.RED));
            data.setAbilityCooldown(data.getPowerType().getAbilityCooldown());
            return;
        }

        float hpRatio = target.getHealth() / target.getMaxHealth();

        if (hpRatio < 0.30f) {
            // FINISH: прямой урон 18 + knockback вверх
            target.hurt(player.damageSources().playerAttack(player), 10.5f);
            target.setDeltaMovement(target.getDeltaMovement().x, 2.0, target.getDeltaMovement().z);
            target.hurtMarked = true;
            addMadness(player, 15);
            level.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + 1, target.getZ(), 30, 0.5, 0.5, 0.5, 0.2);
            level.sendParticles(ParticleTypes.EXPLOSION,
                    target.getX(), target.getY() + 1, target.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
            player.sendSystemMessage(AbilityCommon.msg("FINISH HIM!", ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        } else {
            // SWAP STEP: телепорт за спину цели + замедление
            Vec3 behind = target.position().subtract(target.getLookAngle().normalize().scale(1.2));
            player.teleportTo(behind.x, behind.y, behind.z);
            target.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SLOWDOWN, 30, 2)); // 1.5 сек
            addMadness(player, 10);
            level.sendParticles(ParticleTypes.PORTAL,
                    target.getX(), target.getY() + 1, target.getZ(), 20, 0.5, 1, 0.5, 0.15);
            level.sendParticles(ParticleTypes.PORTAL,
                    player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 1, 0.5, 0.15);
            player.sendSystemMessage(AbilityCommon.msg("Swap Step!", ChatFormatting.RED));
        }

        data.setAbilityCooldown(data.getPowerType().getAbilityCooldown());
    }

    /** Тик комбо-таймера — вызывается каждый тик */
    public static void tickCombo(ServerPlayer player, PlayerPowerData data) {
        var nbt = player.getPersistentData();
        int step = nbt.getInt(NBT_COMBO_STEP);
        if (step != 1) return;

        int ticks = nbt.getInt(NBT_COMBO_TICKS) - 1;
        if (ticks <= 0) {
            // Сброс комбо, ставим КД
            nbt.putInt(NBT_COMBO_STEP, 0);
            nbt.remove(NBT_COMBO_TARGET);
            nbt.putInt(NBT_COMBO_TICKS, 0);
            data.setAbilityCooldown(data.getPowerType().getAbilityCooldown());
            player.sendSystemMessage(AbilityCommon.msg("Combo window missed.", ChatFormatting.GRAY));
        } else {
            nbt.putInt(NBT_COMBO_TICKS, ticks);
        }
    }

    /** Тикает expose-теги на сущностях рядом. Вызывается из AbilityEventHandler */
    public static void tickExposedEntities(ServerPlayer player, ServerLevel level) {
        level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(30),
                e -> e.getPersistentData().getBoolean("merc_exposed"))
        .forEach(e -> {
            int t = e.getPersistentData().getInt("merc_exposed_ticks") - 1;
            if (t <= 0) {
                e.getPersistentData().putBoolean("merc_exposed", false);
                e.getPersistentData().putInt("merc_exposed_ticks", 0);
            } else {
                e.getPersistentData().putInt("merc_exposed_ticks", t);
                // Частицы метки каждые 10 тиков
                if (t % 10 == 0) {
                    level.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.1f, 0.1f), 0.5f),
                            e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(),
                            2, 0.1, 0.1, 0.1, 0);
                }
            }
        });
    }

    // ===== ULT — Fourth Wall Massacre =====

    public static void activateUlt(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        var nbt = player.getPersistentData();
        boolean boostedShift = Math.min(100, getMadness(player) + 40) >= 80;
        nbt.putBoolean(NBT_ULT_ACTIVE, true);
        nbt.putInt(NBT_ULT_TICKS, 180); // 9 секунд
        nbt.putDouble(NBT_DEBT, 0.0);
        nbt.putBoolean(NBT_ULT_SHIFT_BOOST, boostedShift);

        addMadness(player, 40);

        if (boostedShift) {
            data.setShiftMaxCharges(2);
            data.setShiftChargeCdMax(data.getPowerType().getShiftCooldown(player));
            data.getShiftChargeCdQueue().clear();
            data.setShiftCooldown(0);
        }

        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 1, player.getZ(), 40, 1.5, 1.5, 1.5, 0.2);
        level.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);

        player.sendSystemMessage(AbilityCommon.msg(
                boostedShift
                        ? "FOURTH WALL MASSACRE! 9s | Shift charges x2 | 35% damage deferred"
                        : "FOURTH WALL MASSACRE! 9s | 35% damage deferred as DEBT",
                ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    /** Тикается каждый тик во время ульты */
    public static void tickUlt(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        var nbt = player.getPersistentData();
        if (!nbt.getBoolean(NBT_ULT_ACTIVE)) return;

        // Поддерживаем баффы каждый тик
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SPEED, 5, 1));
        player.addEffect(AbilityCommon.fx(MobEffects.DAMAGE_BOOST, 5, 0));

        int ticks = nbt.getInt(NBT_ULT_TICKS) - 1;
        nbt.putInt(NBT_ULT_TICKS, ticks);

        // Частицы ульты
        if (player.tickCount % 10 == 0) {
            level.sendParticles(new DustParticleOptions(new Vector3f(0.8f, 0.05f, 0.05f), 1.0f),
                    player.getX(), player.getY() + 1, player.getZ(),
                    5, 0.5, 0.5, 0.5, 0.05);
        }

        if (ticks <= 0) {
            // Ульта закончилась — начинаем выплату долга
            nbt.putBoolean(NBT_ULT_ACTIVE, false);
            clearUltShiftBoost(player, data);
            double debt = nbt.getDouble(NBT_DEBT);
            if (debt > 0) {
                nbt.putInt(NBT_DEBT_PAY_TICKS, 80); // 4 секунды = 80 тиков
                player.sendSystemMessage(AbilityCommon.msg(
                        String.format("Ult ended. DEBT: %.1f HP incoming over 4s...", debt / 2f),
                        ChatFormatting.DARK_RED));
            } else {
                player.sendSystemMessage(AbilityCommon.msg("Ult ended. No debt.", ChatFormatting.GREEN));
                clearUltShiftBoost(player, data);
                if (data.getUltCooldown() <= 0) {
                    data.setUltCooldown(data.getPowerType().getUltCooldown());
                }
                AbilityActivator.syncToClient(player, data);
            }
        }
    }

    /** Тикает выплату долга после ульты. Вызывается каждый тик */
    public static void tickDebtPayment(ServerPlayer player, ServerLevel level, PlayerPowerData data) {
        var nbt = player.getPersistentData();
        int payTicks = nbt.getInt(NBT_DEBT_PAY_TICKS);
        if (payTicks <= 0) return;

        double totalDebt = nbt.getDouble(NBT_DEBT);
        if (totalDebt <= 0) {
            nbt.putInt(NBT_DEBT_PAY_TICKS, 0);
            clearUltShiftBoost(player, data);
            if (data.getUltCooldown() <= 0) {
                data.setUltCooldown(data.getPowerType().getUltCooldown());
            }
            AbilityActivator.syncToClient(player, data);
            return;
        }

        // Каждый тик наносим равную долю
        float perTick = (float)(totalDebt / Math.max(1, payTicks));
        player.hurt(player.damageSources().magic(), perTick);

        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                player.getX(), player.getY() + 1, player.getZ(),
                1, 0.2, 0.2, 0.2, 0.05);

        payTicks--;
        nbt.putInt(NBT_DEBT_PAY_TICKS, payTicks);
        // Обновляем оставшийся долг
        nbt.putDouble(NBT_DEBT, Math.max(0, totalDebt - perTick));

        if (payTicks <= 0) {
            nbt.putDouble(NBT_DEBT, 0);
            player.sendSystemMessage(AbilityCommon.msg("Debt paid.", ChatFormatting.GRAY));
            clearUltShiftBoost(player, data);
            if (data.getUltCooldown() <= 0) {
                data.setUltCooldown(data.getPowerType().getUltCooldown());
            }
            AbilityActivator.syncToClient(player, data);
        }
    }

    /** Вызывается когда игрок убивает кого-то во время ульты */
    public static void onKillDuringUlt(ServerPlayer player) {
        var nbt = player.getPersistentData();
        if (!nbt.getBoolean(NBT_ULT_ACTIVE)) return;

        // +30 тиков к ульте
        int ticks = nbt.getInt(NBT_ULT_TICKS);
        nbt.putInt(NBT_ULT_TICKS, ticks + 30);

        // Снижаем долг на 20%
        double debt = nbt.getDouble(NBT_DEBT);
        nbt.putDouble(NBT_DEBT, debt * 0.8);

        addMadness(player, 15);
        player.sendSystemMessage(AbilityCommon.msg("+1.5s ult! Debt -20%", ChatFormatting.RED));
    }

    // ===== PASSIVE — Mouth Runs, Wounds Close =====

    public static void tickPassive(ServerPlayer player, ServerLevel level) {
        var nbt = player.getPersistentData();
        int overcook = nbt.getInt(NBT_OVERCOOK_TICKS);

        // Тикаем no-damage счётчик
        // (обновляется при получении урона через LivingHurtEvent)
        int noDmgTicks = nbt.getInt(NBT_NO_DAMAGE_TICKS);
        if (noDmgTicks > 0) {
            nbt.putInt(NBT_NO_DAMAGE_TICKS, noDmgTicks - 1);
        }

        // Реген: если не получал урон 3 секунды (60 тиков) и overcook не активен
        if (noDmgTicks == 0 && overcook <= 0) {
            player.addEffect(AbilityCommon.fx(MobEffects.REGENERATION, 5, 0));
        }
    }

    /**
     * Вызывается из LivingHurtEvent.
     * amount — урон до модификации.
     * isMelee — true если источник playerAttack (lifesteal).
     * isIncoming — true если игрок получает урон, false если наносит.
     */
    public static float onDamageEvent(ServerPlayer player, PlayerPowerData data,
                                      float amount, boolean isIncoming, boolean isMelee) {
        var nbt = player.getPersistentData();

        if (isIncoming) {
            // Сбрасываем таймер no-damage
            nbt.putInt(NBT_NO_DAMAGE_TICKS, 60);
            addMadness(player, 15);

            // Ульта-долг: 35% урона откладывается
            if (nbt.getBoolean(NBT_ULT_ACTIVE)) {
                float deferred = amount * 0.35f;
                double debt = nbt.getDouble(NBT_DEBT) + deferred;
                nbt.putDouble(NBT_DEBT, debt);
                return amount * 0.65f; // возвращаем уменьшенный урон
            }
        } else {
            // Игрок нанёс урон в ближнем бою
            if (isMelee) {
                addMadness(player, 10);
                // Lifesteal под 35% HP
                if (player.getHealth() / player.getMaxHealth() < 0.35f) {
                    player.heal(1.0f);
                    if (player.level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.HEART,
                                player.getX(), player.getY() + 1.5, player.getZ(),
                                2, 0.2, 0.2, 0.2, 0.05);
                    }
                }
            }
        }
        return amount;
    }

    // ===== HELPERS =====

    private static ItemStack dyedLeather(net.minecraft.world.item.Item item, int rgb) {
        ItemStack stack = new ItemStack(item);
        if (stack.getItem() instanceof DyeableLeatherItem d) d.setColor(stack, rgb);
        return stack;
    }

    private static void clearUltShiftBoost(ServerPlayer player, PlayerPowerData data) {
        var nbt = player.getPersistentData();
        if (!nbt.getBoolean(NBT_ULT_SHIFT_BOOST)) return;

        nbt.putBoolean(NBT_ULT_SHIFT_BOOST, false);
        data.setShiftMaxCharges(data.getPowerType().getShiftMaxCharges());
        data.setShiftChargeCdMax(data.getPowerType().getShiftChargeCooldown());
        data.getShiftChargeCdQueue().clear();
        data.setShiftCooldown(0);
    }
}
