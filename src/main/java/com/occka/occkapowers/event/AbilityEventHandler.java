package com.occka.occkapowers.event;

import com.occka.occkapowers.OcckaPowers;
import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.ability.PowerType;
import com.occka.occkapowers.network.NetworkHandler;
import com.occka.occkapowers.network.PacketSyncPowerData;
import com.occka.occkapowers.registry.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import com.occka.occkapowers.event.IceAbility;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import com.occka.occkapowers.event.GeoOrbitHandler;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingFallEvent;

@Mod.EventBusSubscriber(modid = OcckaPowers.MOD_ID)
public class AbilityEventHandler {

    private static MobEffectInstance fx(net.minecraft.world.effect.MobEffect eff, int dur, int amp) {
        return new MobEffectInstance(eff, dur, amp, false, false);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (!(event.player instanceof ServerPlayer player))
            return;
        if (!(player.level() instanceof ServerLevel level))
            return;

        player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
            // 1. Тик данных (кулдауны, fire ult таймер)
            data.tick();

            // 2. Пассивки
            applyConstantPassives(player, data, data.getPowerType(), level);

            // 3. Fire ult: автострельба
            if (data.isFireUltActive() && data.shouldShootFireball()) {
                AbilityActivator.fireUltShoot(player, data);
                data.setShouldShootFireball(false);
            }

            // 4. Fire ult: завершение
            if (data.isFireUltJustEnded()) {
                data.clearFireUltJustEnded();
                AbilityActivator.endFireUlt(player, data);
            }

            // 5. Ice snowstorm тикер
            tickIceSnowstorm(player, level);

            // 6. Echo ult тикер
            tickEchoUlt(player);

            // 7. Gravity ult — таймер падения
            tickGravityUlt(player, level);

            // 8. Chaos/Echo клон тикер — каждые 20 тиков
            PowerType type = data.getPowerType();
            if (type == PowerType.CHAOS || type == PowerType.ECHO) {
                tickClones(player, level);
            }

            if (type == PowerType.FIRE) {
                boolean fireForm = player.getPersistentData().getBoolean("occka_fire_form_active");

                if (fireForm) {
                    if (!player.onGround() && !player.isInWater()) {

                        if (!player.isFallFlying()) {
                            player.startFallFlying();
                        }
                        SuperforceAbility.applyElytraFlight(player, level);
                    }
                    if (player.tickCount % 3 == 0) {
                        level.sendParticles(ParticleTypes.FLAME,
                                player.getX(), player.getY(), player.getZ(),
                                2, 0.2, 0.1, 0.2, 0.03);
                    }
                }
            }

            if (type == PowerType.ICE) {
                IceAbility.tickIceCage(player, level);
            }

            if (type == PowerType.FLASH) {
                if (data.isFlashUltActive()) {
                    FlashAbility.tickFlashUlt(player, level, data);
                }
            }

            if (type == PowerType.SUPERFORCE) {
                SuperforceAbility.applyElytraFlight(player, level); // элитра-движение
                SuperforceAbility.tickFlyAbility(player, level);
                SuperforceAbility.tickUlt(player, level); // ульт только у superforce
            }

            if (type == PowerType.SPIDER) {
                SpiderAbility.tick(player, level);
                player.fallDistance = 0.0f;
            }

            if (type == PowerType.FLOWER) {
                FlowerAbility.tickFlowerForm(player, level);
                FlowerAbility.tickFlowerTrailCleanup(player, level);
                FlowerAbility.tickUlt(player, level);
                if (player.tickCount % 20 == 0) {
                    FlowerAbility.tickWolves(player, level);
                }
            }

            // 10. Fire form tick — огонь, плавление льда (пока форма активна)
            if (type == PowerType.FIRE) {
                FireAbility.tickFireForm(player, level);
            }

            // 11. Adept tick
            if (type == PowerType.ADEPT && player.tickCount % 20 == 0) {
                AdeptAbility.tick(player, level);
            }

            // 12. Синхронизация HUD каждые 10 тиков
            if (player.tickCount % 10 == 0) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new PacketSyncPowerData(data));
            }
        });
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            GeoOrbitHandler.tick(level);
            FlashAbility.tickAfterimages(level);
        }
    }

    private static void tickIceSnowstorm(ServerPlayer player, ServerLevel level) {
        int snowTicks = player.getPersistentData().getInt("occka_ice_snowstorm_ticks");
        if (snowTicks <= 0)
            return;

        player.getPersistentData().putInt("occka_ice_snowstorm_ticks", snowTicks - 1);
        if (snowTicks % 8 == 0) {
            for (int i = 0; i < 12; i++) {
                double ox = (Math.random() - 0.5) * 36;
                double oz = (Math.random() - 0.5) * 36;
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        player.getX() + ox, player.getY() + 12 + Math.random() * 4,
                        player.getZ() + oz, 1, 0, -0.25, 0, 0.08);
            }
        }
    }

    private static void tickEchoUlt(ServerPlayer player) {
        int echoTicks = player.getPersistentData().getInt("occka_echo_ult_ticks");
        if (echoTicks <= 0)
            return;

        player.getPersistentData().putInt("occka_echo_ult_ticks", echoTicks - 1);
        if (echoTicks == 1) {
            player.setGameMode(GameType.SURVIVAL);
            player.sendSystemMessage(Component.literal("Echo Phase ended.")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    private static void tickClones(ServerPlayer player, ServerLevel level) {
        // Ищем ВСЕ armor stand в радиусе с нашими тегами
        level.getEntitiesOfClass(
                net.minecraft.world.entity.decoration.ArmorStand.class,
                player.getBoundingBox().inflate(60),
                e -> {
                    var pd = e.getPersistentData();
                    String chaosOwner = pd.getString("occka_chaos_clone");
                    String echoOwner = pd.getString("occka_echo_clone");
                    return pd.contains("occka_clone_lifetime") &&
                            (!chaosOwner.isEmpty() || !echoOwner.isEmpty());
                }).forEach(stand -> {
                    int life = stand.getPersistentData().getInt("occka_clone_lifetime") - 1;
                    if (life <= 0) {
                        level.sendParticles(ParticleTypes.POOF,
                                stand.getX(), stand.getY() + 1, stand.getZ(),
                                8, 0.3, 0.5, 0.3, 0.05);
                        stand.discard();
                    } else {
                        stand.getPersistentData().putInt("occka_clone_lifetime", life);
                    }
                });
    }

    private static void applyConstantPassives(ServerPlayer player, PlayerPowerData data,
            PowerType type, ServerLevel level) {
        if (player.tickCount % 100 == 0) {
            switch (type) {
                case FIRE -> player.addEffect(fx(MobEffects.FIRE_RESISTANCE, 200, 0));
                case AIR -> {
                    AttributeInstance hp = player.getAttribute(Attributes.MAX_HEALTH);
                    if (hp != null && hp.getBaseValue() != 18.0)
                        hp.setBaseValue(18.0);
                    player.addEffect(fx(MobEffects.SLOW_FALLING, 25, 0));
                }
                case WATER -> {
                    player.addEffect(fx(MobEffects.WATER_BREATHING, 200, 0));
                    player.addEffect(fx(MobEffects.DOLPHINS_GRACE, 200, 0));
                }
                case GEO -> {
                    AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
                    if (attr != null && attr.getBaseValue() < 40.0)
                        attr.setBaseValue(40.0);
                }
                case LIGHT -> player.addEffect(fx(MobEffects.LUCK, 200, 4));
                case VOID -> {
                    AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
                    if (attr != null && attr.getBaseValue() != 16.0)
                        attr.setBaseValue(16.0);
                }
                case GRAVITY -> player.addEffect(fx(MobEffects.JUMP, 200, 1));
                case CHAOS -> {
                    net.minecraft.core.particles.SimpleParticleType[] types = {
                            ParticleTypes.WITCH, ParticleTypes.PORTAL,
                            ParticleTypes.FLAME, ParticleTypes.ENCHANT
                    };
                    level.sendParticles(types[new java.util.Random().nextInt(types.length)],
                            player.getX(), player.getY() + 1, player.getZ(),
                            3, 0.4, 0.4, 0.4, 0.05);
                }
                default -> {
                }
            }
        }

        if (type == PowerType.AIR && player.fallDistance > 1.5f) {
            player.addEffect(fx(MobEffects.SLOW_FALLING, 40, 0));
        }

        if (player.tickCount % 60 == 0) {
            spawnAuraParticles(player, type, level);
        }

        if (player.tickCount % 200 == 0 && type != PowerType.NONE) {
            applyNickColor(player, type);
        }
    }

    private static void spawnAuraParticles(ServerPlayer player, PowerType type, ServerLevel level) {
        switch (type) {
            case FIRE -> level.sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 0.5, player.getZ(), 2, 0.4, 0.4, 0.4, 0.01);
            case AIR -> level.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() - 0.3, player.getZ(), 2, 0.3, 0.05, 0.3, 0.005);
            case WATER -> level.sendParticles(ParticleTypes.DRIPPING_WATER,
                    player.getX(), player.getY() + 2.1, player.getZ(), 3, 0.3, 0.1, 0.3, 0.01);
            case ICE -> level.sendParticles(ParticleTypes.SNOWFLAKE,
                    player.getX(), player.getY() + 0.5, player.getZ(), 3, 0.4, 0.4, 0.4, 0.01);
            case LIGHTNING -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1, player.getZ(), 2, 0.3, 0.5, 0.3, 0.1);
            case LASER -> level.sendParticles(ParticleTypes.CRIT,
                    player.getX(), player.getY() + 1, player.getZ(), 1, 0.2, 0.3, 0.2, 0.02);
            case GEO -> level.sendParticles(
                    new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.FALLING_DUST,
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),
                    player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
            case VOID -> level.sendParticles(ParticleTypes.PORTAL,
                    player.getX(), player.getY() + 1, player.getZ(), 3, 0.3, 0.5, 0.3, 0.02);
            case LIGHT -> level.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1, player.getZ(), 3, 0.4, 0.4, 0.4, 0.02);
            case GRAVITY -> level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    player.getX(), player.getY() + 1, player.getZ(), 2, 0.3, 0.3, 0.3, 0.01);
            case ECHO -> level.sendParticles(ParticleTypes.PORTAL,
                    player.getX(), player.getY() + 1, player.getZ(), 1, 0.2, 0.3, 0.2, 0.01);
            case FLASH -> {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        player.getX(), player.getY() + 1, player.getZ(), 2, 0.4, 0.4, 0.4, 0.1);
                level.sendParticles(ParticleTypes.FLAME,
                        player.getX(), player.getY() + 0.7, player.getZ(), 1, 0.25, 0.2, 0.25, 0.02);
            }

            case SPIDER -> {
                level.sendParticles(ParticleTypes.SPIT,
                        player.getX(), player.getY() + 1, player.getZ(), 2, 0.3, 0.3, 0.3, 0.01);
                level.sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 0.3, player.getZ(), 1, 0.2, 0.06, 0.2, 0.01);
            }
            case SUPERFORCE -> {
                level.sendParticles(ParticleTypes.CRIT,
                        player.getX(), player.getY() + 1, player.getZ(), 2, 0.5, 0.5, 0.5, 0.1);
                if (player.getAbilities().flying) {
                    level.sendParticles(ParticleTypes.CLOUD,
                            player.getX(), player.getY(), player.getZ(), 3, 0.3, 0.1, 0.3, 0.03);
                }
            }
            default -> {
            }
        }
    }

    private static void applyNickColor(ServerPlayer player, PowerType type) {
        var scoreboard = player.getServer().getScoreboard();
        String teamName = "occka_" + type.getId();

        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            team.setColor(type.getColor());
            team.setNameTagVisibility(Team.Visibility.ALWAYS);
        }

        var currentTeam = scoreboard.getPlayersTeam(player.getScoreboardName());
        String currentName = currentTeam == null ? "" : currentTeam.getName();

        if (!teamName.equals(currentName)) {
            if (currentTeam != null && currentTeam.getName().startsWith("occka_")) {
                scoreboard.removePlayerFromTeam(player.getScoreboardName(), currentTeam);
            }
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        }
    }

    // === СОБЫТИЯ ЖИЗНЕННОГО ЦИКЛА ===

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
            if (data.getPowerType() == PowerType.SPIDER) {
                event.setCanceled(true);
                player.fallDistance = 0.0f;
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(ModCapabilities.PLAYER_POWER)
                .ifPresent(oldData -> event.getEntity().getCapability(ModCapabilities.PLAYER_POWER)
                        .ifPresent(newData -> newData.deserializeNBT(oldData.serializeNBT())));
        event.getOriginal().invalidateCaps();

        // Сбрасываем огненную форму после смерти — mayfly не должен оставаться
        if (event.isWasDeath()) {
            FireAbility.clearFireForm(event.getEntity());
            FlowerAbility.clearFlowerForm(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncPowerData(data));
            if (data.getPowerType() != PowerType.NONE) {
                applyNickColor(player, data.getPowerType());
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Сбрасываем огненную форму при выходе
            FireAbility.clearFireForm(player);

            if (player.level() instanceof ServerLevel level) {
                GeoOrbitHandler.clearPlayer(player.getUUID(), level);
            }
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketSyncPowerData(data)));
    }

    private static void tickGravityUlt(ServerPlayer player, ServerLevel level) {
        int ticks = player.getPersistentData().getInt("occka_gravity_ult_ticks");
        if (ticks <= 0)
            return;

        ticks--;
        player.getPersistentData().putInt("occka_gravity_ult_ticks", ticks);

        if (ticks == 0) {
            double radius = player.getPersistentData().getDouble("occka_gravity_ult_radius");
            AABB box = player.getBoundingBox().inflate(radius);
            List<net.minecraft.world.entity.LivingEntity> targets = level.getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class,
                    box, e -> e != player);

            for (net.minecraft.world.entity.LivingEntity entity : targets) {
                entity.removeEffect(MobEffects.LEVITATION);
                entity.setDeltaMovement(
                        entity.getDeltaMovement().x,
                        -3.5,
                        entity.getDeltaMovement().z);
                entity.hurtMarked = true;
            }

            level.sendParticles(ParticleTypes.PORTAL,
                    player.getX(), player.getY() + 5, player.getZ(),
                    40, 10, 5, 10, 0.2);

            player.sendSystemMessage(
                    Component.literal("Gravity restored!").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
