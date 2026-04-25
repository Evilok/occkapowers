package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class AdeptAbility {

    private static final Random RNG = new Random();

    private static MobEffectInstance fx(net.minecraft.world.effect.MobEffect eff, int dur, int amp) {
        return new MobEffectInstance(eff, dur, amp, false, false);
    }

    // ===== SHIFT: Зелёные частицы + тошнота в радиусе 3 + смешные сообщения =====
    public static void activateShift(ServerPlayer player, ServerLevel level) {
        // Зелёные частицы вокруг игрока
        for (int i = 0; i < 20; i++) {
            double angle = (i / 20.0) * Math.PI * 2;
            double r = 1.5 + RNG.nextDouble();
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX() + r * Math.cos(angle),
                    player.getY() + 0.5 + RNG.nextDouble() * 1.5,
                    player.getZ() + r * Math.sin(angle),
                    1, 0.05, 0.05, 0.05, 0.01);
        }
        level.sendParticles(ParticleTypes.COMPOSTER,
                player.getX(), player.getY() + 1, player.getZ(),
                15, 0.5, 0.5, 0.5, 0.05);

        // Тошнота всем в радиусе 3 (кроме самого игрока)
        AABB box = player.getBoundingBox().inflate(3);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player);
        for (LivingEntity entity : nearby) {
            entity.addEffect(fx(MobEffects.CONFUSION, 60, 0)); // 3 секунды
        }

        // Смешные сообщения от лица всех игроков на сервере
        String[] laughs = { "ХАХААХАХ", "ахахахах", "хааххахах" };
        String adeptName = player.getName().getString();
        String laugh = laughs[RNG.nextInt(laughs.length)];
        String message = laugh + " " + adeptName + " ты такой крутой";

        // Имитируем сообщение от каждого игрока в радиусе 15 блоков
        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(
                ServerPlayer.class,
                player.getBoundingBox().inflate(15),
                p -> p != player);

        for (ServerPlayer victim : nearbyPlayers) {
            // Отправляем всем на сервере сообщение, будто написал этот игрок
            Component fakeMsg = Component.literal("<" + victim.getName().getString() + "> " + message)
                    .withStyle(ChatFormatting.GREEN);
            // Рассылаем всем игрокам на сервере
            level.getServer().getPlayerList().getPlayers().forEach(p ->
                    p.sendSystemMessage(fakeMsg));
        }

        // Если рядом никого нет — хотя бы сам игрок видит эффект
        if (nearbyPlayers.isEmpty()) {
            player.sendSystemMessage(Component.literal("(Никого рядом нет, кто мог бы восхититься...)")
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    // ===== ABILITY: Спавн 4–8 попугаев вокруг (умирают через 20 сек) =====
    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        int count = 4 + RNG.nextInt(5); // 4..8

        // Цвета попугаев
        Parrot.Variant[] variants = Parrot.Variant.values();

        for (int i = 0; i < count; i++) {
            Parrot parrot = new Parrot(EntityType.PARROT, level);

            double angle = (i / (double) count) * Math.PI * 2;
            double r = 1.5 + RNG.nextDouble() * 1.5;

            parrot.moveTo(
                    player.getX() + r * Math.cos(angle),
                    player.getY() + 0.5,
                    player.getZ() + r * Math.sin(angle),
                    RNG.nextFloat() * 360, 0);

            // Случайный цвет
            parrot.setVariant(variants[RNG.nextInt(variants.length)]);
            parrot.setTame(true);
            parrot.setOwnerUUID(player.getUUID()); // привязываем к игроку

            // Помечаем для автоудаления
            parrot.getPersistentData().putBoolean("occka_adept_parrot", true);
            // Таймер жизни: 400 тиков = 20 секунд
            parrot.getPersistentData().putInt("occka_parrot_lifetime", 400);

            level.addFreshEntity(parrot);

            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    parrot.getX(), parrot.getY() + 0.5, parrot.getZ(),
                    5, 0.2, 0.2, 0.2, 0.05);
        }

        player.sendSystemMessage(Component.literal("Свита попугаев! (" + count + " шт, 20 сек)")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    // ===== ULT: Превращение в панду на 15 секунд =====
    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        // Даём эффекты "формы панды"
        player.addEffect(fx(MobEffects.INVISIBILITY, 300, 0));       // 15 сек инвиз
        player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 300, 4));  // Сопротивление V (макс)

        // Спавним панду прямо на игрока
        Panda panda = new Panda(EntityType.PANDA, level);
        panda.moveTo(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        panda.setNoAi(true);
        panda.setInvulnerable(true);
        panda.setSilent(true);
        panda.setNoGravity(false);

        // Помечаем панду как "ульта адепта" и сохраняем UUID игрока
        panda.getPersistentData().putString("occka_adept_panda", player.getUUID().toString());
        panda.getPersistentData().putInt("occka_panda_lifetime", 300); // 15 сек

        level.addFreshEntity(panda);

        // Частицы трансформации
        for (int i = 0; i < 40; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double r = RNG.nextDouble() * 1.5;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX() + r * Math.cos(angle),
                    player.getY() + RNG.nextDouble() * 2,
                    player.getZ() + r * Math.sin(angle),
                    1, 0, 0, 0, 0.05);
        }
        level.sendParticles(ParticleTypes.COMPOSTER,
                player.getX(), player.getY() + 1, player.getZ(),
                30, 1, 1, 1, 0.1);

        player.sendSystemMessage(Component.literal("ПАНДА ФОРМА! 15 секунд...")
                .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD));
    }

    // ===== TICK: вызывается каждые 20 тиков из AbilityEventHandler =====
    public static void tick(ServerPlayer player, ServerLevel level) {
        tickParrots(player, level);
        tickPanda(player, level);
    }

    // Убиваем попугаев по таймеру
    private static void tickParrots(ServerPlayer player, ServerLevel level) {
        level.getEntitiesOfClass(Parrot.class,
                player.getBoundingBox().inflate(50),
                e -> e.getPersistentData().getBoolean("occka_adept_parrot")
                        && e.isOwnedBy(player))
        .forEach(parrot -> {
            int life = parrot.getPersistentData().getInt("occka_parrot_lifetime") - 20;
            if (life <= 0) {
                // Прощальные частицы
                level.sendParticles(ParticleTypes.POOF,
                        parrot.getX(), parrot.getY() + 0.5, parrot.getZ(),
                        5, 0.3, 0.3, 0.3, 0.05);
                parrot.discard();
            } else {
                parrot.getPersistentData().putInt("occka_parrot_lifetime", life);
                // Следим за панадой — телепортируем попугаев к игроку если далеко
                if (parrot.distanceTo(player) > 10) {
                    double angle = RNG.nextDouble() * Math.PI * 2;
                    parrot.teleportTo(
                            player.getX() + 1.5 * Math.cos(angle),
                            player.getY() + 0.5,
                            player.getZ() + 1.5 * Math.sin(angle));
                }
            }
        });
    }

    // Телепортируем панду за игроком и убиваем по таймеру
    private static void tickPanda(ServerPlayer player, ServerLevel level) {
        String playerUUID = player.getUUID().toString();

        level.getEntitiesOfClass(Panda.class,
                player.getBoundingBox().inflate(60),
                e -> playerUUID.equals(e.getPersistentData().getString("occka_adept_panda")))
        .forEach(panda -> {
            int life = panda.getPersistentData().getInt("occka_panda_lifetime") - 20;
            if (life <= 0) {
                // Конец ульты
                player.removeEffect(MobEffects.INVISIBILITY);
                player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                player.sendSystemMessage(Component.literal("Панда форма завершена.")
                        .withStyle(ChatFormatting.GREEN));
                level.sendParticles(ParticleTypes.POOF,
                        panda.getX(), panda.getY() + 1, panda.getZ(),
                        20, 0.5, 0.5, 0.5, 0.05);
                panda.discard();
            } else {
                panda.getPersistentData().putInt("occka_panda_lifetime", life);
                // Телепортируем панду к игроку (чуть сбоку чтоб не застряли)
                panda.teleportTo(
                        player.getX() + 0.6,
                        player.getY(),
                        player.getZ() + 0.6);
                // Поворачиваем морду панды в ту же сторону что и игрок
                panda.setYRot(player.getYRot());
                panda.yHeadRot = player.getYRot();
            }
        });
    }
}