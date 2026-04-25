package com.occka.occkapowers.event;

import com.occka.occkapowers.OcckaPowers;
import com.occka.occkapowers.ability.PlayerPowerData;
import com.occka.occkapowers.ability.PowerType;
import com.occka.occkapowers.network.NetworkHandler;
import com.occka.occkapowers.network.PacketSyncPowerData;
import com.occka.occkapowers.registry.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

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

        player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
            boolean wasFireUltActive = data.isFireUltActive();
            data.tick();

            // Ice snowstorm ticker
            int snowTicks = player.getPersistentData().getInt("occka_ice_snowstorm_ticks");
            if (snowTicks > 0 && player.level() instanceof ServerLevel snowLevel) {
                player.getPersistentData().putInt("occka_ice_snowstorm_ticks", snowTicks - 1);
                // Every 8 ticks: snowfall (optimized)
                if (snowTicks % 8 == 0) {
                    for (int i = 0; i < 12; i++) {
                        double ox = (Math.random() - 0.5) * 36;
                        double oz = (Math.random() - 0.5) * 36;
                        snowLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                                player.getX() + ox, player.getY() + 12 + Math.random() * 4,
                                player.getZ() + oz, 1, 0, -0.25, 0, 0.08);
                    }
                }
            }

            // Tick chaos/echo clones lifetime - only for CHAOS or ECHO players
            if (player.tickCount % 20 == 0 &&
                    (type == PowerType.CHAOS || type == PowerType.ECHO)) {
                player.level().getEntitiesOfClass(
                        net.minecraft.world.entity.decoration.ArmorStand.class,
                        player.getBoundingBox().inflate(40),
                        e -> e.getPersistentData().contains("occka_clone_lifetime")).forEach(stand -> {
                            int life = stand.getPersistentData().getInt("occka_clone_lifetime") - 20;
                            if (life <= 0) stand.discard();
                            else stand.getPersistentData().putInt("occka_clone_lifetime", life);
                        });
            }
            // Echo ult spectator timer
            int echoTicks = player.getPersistentData().getInt("occka_echo_ult_ticks");
            if (echoTicks > 0) {
                player.getPersistentData().putInt("occka_echo_ult_ticks", echoTicks - 1);
                if (echoTicks == 1) {
                    player.setGameMode(GameType.SURVIVAL);
                    player.sendSystemMessage(Component.literal("Echo Phase ended.")
                            .withStyle(ChatFormatting.GREEN));
                }
            }

            // Superforce crash handled in tickUlt (land detection)

            if (data.isFireUltActive() && data.shouldShootFireball()) {
                AbilityActivator.fireUltShoot(player, data);
                data.setShouldShootFireball(false);
            }

            // Sync HUD every 10 ticks (2.5x less packets, still smooth at ~2 updates/sec)
            if (player.tickCount % 10 == 0) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new PacketSyncPowerData(data));
            }
        });
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickEmpty event) {
        System.out.println("LMB CLICK DETECTED");
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
            AbilityActivator.fireUltShoot(player, data);
        });
    }

    private static void applyConstantPassives(ServerPlayer player, PlayerPowerData data, PowerType type) {
        if (!(player.level() instanceof ServerLevel level))
            return;

        // Every tick: superforce flight passive
        if (type == PowerType.SUPERFORCE) {
            SuperforceAbility.applyPassive(player);
        }
        // Every tick: superforce ult tick
        if (type == PowerType.SUPERFORCE && player.level() instanceof ServerLevel sfLevel) {
            SuperforceAbility.tickUlt(player, sfLevel);
        }

        // Refreshed every 5s (100 ticks) - permanent passives regardless of shift
        if (player.tickCount % 100 == 0) {
            switch (type) {
                case FIRE -> player.addEffect(fx(MobEffects.FIRE_RESISTANCE, 200, 0));
                case AIR -> player.addEffect(fx(MobEffects.SLOW_FALLING, 25, 0)); // very short - only prevents fall
                                                                                  // damage when near ground
                case SUPERFORCE -> {} // handled every tick above
                case CHAOS -> {
                    //
                    net.minecraft.core.particles.SimpleParticleType[] types = {
                            ParticleTypes.WITCH, ParticleTypes.PORTAL,
                            ParticleTypes.FLAME, ParticleTypes.ENCHANT
                    };
                    level.sendParticles(types[new java.util.Random().nextInt(types.length)],
                            player.getX(), player.getY() + 1, player.getZ(), 3, 0.4, 0.4, 0.4, 0.05);
                }
                case WATER -> {
                    player.addEffect(fx(MobEffects.WATER_BREATHING, 200, 0));
                    player.addEffect(fx(MobEffects.DOLPHINS_GRACE, 200, 0));
                }
                case GEO -> {
                    AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
                    if (attr != null && attr.getBaseValue() < 40.0) {
                        attr.setBaseValue(40.0);
                    }
                }
                case LIGHT -> player.addEffect(fx(MobEffects.LUCK, 200, 4)); // Luck V
                case VOID -> {
                    // -2 hearts: use negative health boost via attribute (health boost -1 = -2hp
                    // per level)
                    // In 1.20.1 we simulate with absorbing health differently
                    // Simplest approach: cap max hp by checking regularly
                    if (player.getMaxHealth() > 16.0f) { // if above 8 hearts
                        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                                .setBaseValue(16.0); // 8 hearts (vanilla 20, -4hp)
                    }
                }

                case ICE -> {
                    player.removeAllEffects();
                }
                default -> {
                }
            }

            // Air: -1 heart (set max HP to 18)
            if (type == PowerType.AIR) {
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                        .setBaseValue(18.0); // 9 hearts
            }

            // Void: restore if class changed
        }

        // Air: no fall damage (slow_falling on any fall)
        if (type == PowerType.AIR && player.fallDistance > 1.5f) {
            player.addEffect(fx(MobEffects.SLOW_FALLING, 40, 0));
        }

        // Ambient aura particles every 2s
        if (player.tickCount % 60 == 0) { // ambient aura - every 3s
            switch (type) {
                case FIRE -> level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.5, player.getZ(),
                        2, 0.4, 0.4, 0.4, 0.01);
                case AIR -> level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() - 0.3, player.getZ(),
                        2, 0.3, 0.05, 0.3, 0.005);
                case WATER -> level.sendParticles(ParticleTypes.DRIPPING_WATER, player.getX(), player.getY() + 2.1,
                        player.getZ(), 3, 0.3, 0.1, 0.3, 0.01);
                case ICE -> level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 0.5,
                        player.getZ(), 3, 0.4, 0.4, 0.4, 0.01);
                case LIGHTNING -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1,
                        player.getZ(), 2, 0.3, 0.5, 0.3, 0.1);
                case LASER -> level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(),
                        1, 0.2, 0.3, 0.2, 0.02);
                case GEO -> level.sendParticles(
                        new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.FALLING_DUST,
                                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),
                        player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
                case VOID -> level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(),
                        3, 0.3, 0.5, 0.3, 0.02);
                case LIGHT -> level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1,
                        player.getZ(), 3, 0.4, 0.4, 0.4, 0.02);
                case GRAVITY -> level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1,
                        player.getZ(), 2, 0.3, 0.3, 0.3, 0.01);
                case ECHO -> level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(),
                        1, 0.2, 0.3, 0.2, 0.01);
                case SUPERFORCE -> {
                    level.sendParticles(ParticleTypes.CRIT,
                            player.getX(), player.getY() + 1, player.getZ(), 2, 0.5, 0.5, 0.5, 0.1);
                    if (player.getAbilities().flying) {
                        // Flight trail
                        level.sendParticles(ParticleTypes.CLOUD,
                                player.getX(), player.getY(), player.getZ(), 3, 0.3, 0.1, 0.3, 0.03);
                    }
                }
                default -> {
                }
            }
        }

        // Nick coloring via scoreboard team (every 10s)
        if (player.tickCount % 200 == 0 && type != PowerType.NONE) {
            applyNickColor(player, type);
        }
    }

    // Color player name via scoreboard team
    private static void applyNickColor(ServerPlayer player, PowerType type) {
        var scoreboard = player.getServer().getScoreboard();
        String teamName = "occka_" + type.getId();

        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            team.setColor(type.getColor());
            team.setNameTagVisibility(Team.Visibility.ALWAYS);
        }

        // Move player to the correct team if not already there
        if (!teamName.equals(scoreboard.getPlayersTeam(player.getScoreboardName()) == null ? ""
                : scoreboard.getPlayersTeam(player.getScoreboardName()).getName())) {
            // Remove from old occka team first
            var currentTeam = scoreboard.getPlayersTeam(player.getScoreboardName());
            if (currentTeam != null && currentTeam.getName().startsWith("occka_")) {
                scoreboard.removePlayerFromTeam(player.getScoreboardName(), currentTeam);
            }
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        }
    }

    // === CRITICAL: persist class across death ===
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // This fires on death/respawn AND dimension travel
        // Always copy capability from original to new player
        event.getOriginal().reviveCaps(); // needed to access caps after death
        event.getOriginal().getCapability(ModCapabilities.PLAYER_POWER)
                .ifPresent(oldData -> event.getEntity().getCapability(ModCapabilities.PLAYER_POWER)
                        .ifPresent(newData -> newData.deserializeNBT(oldData.serializeNBT())));
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new PacketSyncPowerData(data));
                // Re-apply nick color on login
                if (data.getPowerType() != PowerType.NONE) {
                    applyNickColor(player, data.getPowerType());
                }
            });
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> NetworkHandler.CHANNEL
                    .send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncPowerData(data)));
        }
    }
}
