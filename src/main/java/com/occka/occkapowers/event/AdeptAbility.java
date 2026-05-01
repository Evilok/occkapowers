package com.occka.occkapowers.event;

import com.occka.occkapowers.network.NetworkHandler;
import com.occka.occkapowers.network.PacketSyncAdeptPandaForm;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Random;

public class AdeptAbility {
    private static final Random RNG = new Random();
    private static final String NBT_PANDA_FORM_TICKS = "occka_adept_panda_form_ticks";

    private static MobEffectInstance fx(net.minecraft.world.effect.MobEffect eff, int dur, int amp) {
        return new MobEffectInstance(eff, dur, amp, false, false);
    }

    public static void activateShift(ServerPlayer player, ServerLevel level) {
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

        AABB box = player.getBoundingBox().inflate(3);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player);
        for (LivingEntity entity : nearby) {
            entity.addEffect(fx(MobEffects.CONFUSION, 60, 0));
        }

        String[] laughs = { "HAHAHAHA", "ahahahaha", "haahaahaa" };
        String adeptName = player.getName().getString();
        String laugh = laughs[RNG.nextInt(laughs.length)];
        String message = laugh + " " + adeptName + " ты такой крутой";

        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(
                ServerPlayer.class,
                player.getBoundingBox().inflate(15),
                p -> p != player);

        for (ServerPlayer victim : nearbyPlayers) {
            Component fakeMsg = Component.literal("<" + victim.getName().getString() + "> " + message)
                    .withStyle(ChatFormatting.GREEN);
            level.getServer().getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(fakeMsg));
        }

        if (nearbyPlayers.isEmpty()) {
            player.sendSystemMessage(Component.literal("(Никого рядом нет, кто мог бы восхититься...)")
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        int count = 4 + RNG.nextInt(5);
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

            parrot.setVariant(variants[RNG.nextInt(variants.length)]);
            parrot.setTame(true);
            parrot.setOwnerUUID(player.getUUID());
            parrot.getPersistentData().putBoolean("occka_adept_parrot", true);
            parrot.getPersistentData().putInt("occka_parrot_lifetime", 400);

            level.addFreshEntity(parrot);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    parrot.getX(), parrot.getY() + 0.5, parrot.getZ(),
                    5, 0.2, 0.2, 0.2, 0.05);
        }

        player.sendSystemMessage(Component.literal("Свита попугаев! (" + count + " шт, 20 сек)")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        player.addEffect(fx(MobEffects.DAMAGE_RESISTANCE, 300, 4));
        player.getPersistentData().putInt(NBT_PANDA_FORM_TICKS, 300);
        syncPandaForm(player, true);

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

    public static void tick(ServerPlayer player, ServerLevel level) {
        tickParrots(player, level);
        tickPandaForm(player, level);
    }

    private static void tickParrots(ServerPlayer player, ServerLevel level) {
        level.getEntitiesOfClass(Parrot.class,
                player.getBoundingBox().inflate(50),
                e -> e.getPersistentData().getBoolean("occka_adept_parrot") && e.isOwnedBy(player))
                .forEach(parrot -> {
                    int life = parrot.getPersistentData().getInt("occka_parrot_lifetime") - 20;
                    if (life <= 0) {
                        level.sendParticles(ParticleTypes.POOF,
                                parrot.getX(), parrot.getY() + 0.5, parrot.getZ(),
                                5, 0.3, 0.3, 0.3, 0.05);
                        parrot.discard();
                    } else {
                        parrot.getPersistentData().putInt("occka_parrot_lifetime", life);
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

    private static void tickPandaForm(ServerPlayer player, ServerLevel level) {
        int life = player.getPersistentData().getInt(NBT_PANDA_FORM_TICKS);
        if (life <= 0) return;

        life -= 20;
        if (life <= 0) {
            player.getPersistentData().putInt(NBT_PANDA_FORM_TICKS, 0);
            player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            syncPandaForm(player, false);
            player.sendSystemMessage(Component.literal("Панда форма завершена.")
                    .withStyle(ChatFormatting.GREEN));
            level.sendParticles(ParticleTypes.POOF,
                    player.getX(), player.getY() + 1, player.getZ(),
                    20, 0.5, 0.5, 0.5, 0.05);
        } else {
            player.getPersistentData().putInt(NBT_PANDA_FORM_TICKS, life);
            syncPandaForm(player, true);
        }
    }

    private static void syncPandaForm(ServerPlayer player, boolean active) {
        NetworkHandler.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new PacketSyncAdeptPandaForm(player.getUUID(), active));
    }
}
