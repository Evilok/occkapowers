package com.occka.occkapowers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.occka.occkapowers.ability.PowerType;
import com.occka.occkapowers.ability.PlayerPowerSync;
import com.occka.occkapowers.form.FormRegistry;
import com.occka.occkapowers.form.PlayerFormData;
import com.occka.occkapowers.network.NetworkHandler;
import com.occka.occkapowers.network.PacketSyncPlayerForm;
import com.occka.occkapowers.network.PacketSyncPowerData;
import com.occka.occkapowers.registry.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class OcckaCommand {

    private static final List<String> POWER_IDS = Arrays.stream(PowerType.values())
            .filter(p -> p != PowerType.NONE).map(PowerType::getId).toList();

    private static final List<String> FORM_IDS = List.copyOf(FormRegistry.FORMS.keySet());

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_POWERS = (ctx, builder) -> {
        POWER_IDS.forEach(builder::suggest);
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_FORMS = (ctx, builder) -> {
        FORM_IDS.forEach(builder::suggest);
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_UNLOCK_TYPE = (ctx, builder) -> {
        List.of("ability", "ultimate").forEach(builder::suggest);
        return builder.buildFuture();
    };

    private static MutableComponent msg(String text, ChatFormatting... fmt) {
        var style = net.minecraft.network.chat.Style.EMPTY;
        for (ChatFormatting f : fmt)
            style = style.applyFormat(f);
        return Component.literal(text).withStyle(style);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("occkapowers")
                .requires(src -> src.hasPermission(2))

                // /occkapowers give <target> <power> [unlocked:bool]
                .then(Commands.literal("give")
                        .then(Commands.argument("target", EntityArgument.players())
                                .then(Commands.argument("power", StringArgumentType.word())
                                        .suggests(SUGGEST_POWERS)
                                        .executes(ctx -> givePower(ctx, false))
                                        .then(Commands.argument("unlocked", BoolArgumentType.bool())
                                                .executes(ctx -> givePower(ctx,
                                                        BoolArgumentType.getBool(ctx, "unlocked")))))))

                .then(Commands.literal("remove")
                        .then(Commands.argument("target", EntityArgument.players())
                                .executes(OcckaCommand::removePower)))

                .then(Commands.literal("info")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(OcckaCommand::getInfo)))

                .then(Commands.literal("unlock")
                        .then(Commands.argument("target", EntityArgument.players())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(SUGGEST_UNLOCK_TYPE)
                                        .executes(OcckaCommand::unlockAbility))))

                // /occkapowers form give <target> <mob>
                // /occkapowers form remove <target>
                .then(Commands.literal("form")
                        .then(Commands.literal("give")
                                .then(Commands.argument("target", EntityArgument.players())
                                        .then(Commands.argument("mob", StringArgumentType.word())
                                                .suggests(SUGGEST_FORMS)
                                                .executes(OcckaCommand::giveForm))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("target", EntityArgument.players())
                                        .executes(OcckaCommand::removeForm)))));
    }

    // ==================== form give ====================

    private static int giveForm(CommandContext<CommandSourceStack> ctx) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "target");
            String mob = StringArgumentType.getString(ctx, "mob").toLowerCase();

            if (!FormRegistry.isValid(mob)) {
                ctx.getSource().sendFailure(msg(
                        "Unknown mob: " + mob + ". Available: " + String.join(", ", FORM_IDS),
                        ChatFormatting.RED));
                return 0;
            }

            for (ServerPlayer player : targets) {
                // Save to NBT (persists through death / relog)
                PlayerFormData.setForm(player, mob);

                // Sync to all clients that can see this player
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                        new PacketSyncPlayerForm(player.getUUID(), mob));

                player.sendSystemMessage(msg(
                        "Your form has been changed to: " + mob, ChatFormatting.GREEN));

                ctx.getSource().sendSuccess(() -> msg(
                        "Set form " + mob + " for " + player.getName().getString(),
                        ChatFormatting.GREEN), true);
            }
            return targets.size();
        } catch (Exception e) {
            ctx.getSource().sendFailure(msg("Error: " + e.getMessage(), ChatFormatting.RED));
            return 0;
        }
    }

    // ==================== form remove ====================

    private static int removeForm(CommandContext<CommandSourceStack> ctx) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "target");

            for (ServerPlayer player : targets) {
                PlayerFormData.clearForm(player);

                // Empty string = clear on client
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                        new PacketSyncPlayerForm(player.getUUID(), ""));

                player.sendSystemMessage(msg("Your form has been reset.", ChatFormatting.GRAY));

                ctx.getSource().sendSuccess(() -> msg(
                        "Removed form from " + player.getName().getString(),
                        ChatFormatting.GREEN), true);
            }
            return targets.size();
        } catch (Exception e) {
            ctx.getSource().sendFailure(msg("Error: " + e.getMessage(), ChatFormatting.RED));
            return 0;
        }
    }

    // ==================== existing commands (unchanged) ====================

    private static int givePower(CommandContext<CommandSourceStack> ctx, boolean unlocked) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "target");
            String powerName = StringArgumentType.getString(ctx, "power");
            PowerType type = PowerType.fromId(powerName);

            if (type == PowerType.NONE) {
                ctx.getSource().sendFailure(msg(
                        "Unknown class: " + powerName + ". Available: " + String.join(", ", POWER_IDS),
                        ChatFormatting.RED));
                return 0;
            }

            for (ServerPlayer player : targets) {
                player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
                    data.setPowerType(type);
                    player.refreshDimensions();

                    if (unlocked) {
                        data.setAbilityUnlocked(true);
                        data.setUltUnlocked(true);
                    }

                    NetworkHandler.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new PacketSyncPowerData(player, data));
                    PlayerPowerSync.syncToTrackingAndSelf(player, data);

                    player.sendSystemMessage(msg("You received class: ", ChatFormatting.GREEN)
                            .append(Component.literal(type.getId().toUpperCase())
                                    .withStyle(type.getColor(), ChatFormatting.BOLD)));

                    if (unlocked) {
                        player.sendSystemMessage(msg(
                                "[All abilities UNLOCKED by admin]", ChatFormatting.GOLD));
                    } else {
                        player.sendSystemMessage(msg(
                                "[R] Shift  [F] Ability  [G] Ult | Unlock ability/ult by pressing the key!",
                                ChatFormatting.GRAY));
                    }
                });

                ctx.getSource().sendSuccess(() -> msg(
                        "Given class " + type.getId().toUpperCase()
                                + " to " + player.getName().getString()
                                + (unlocked ? " [UNLOCKED]" : ""),
                        ChatFormatting.GREEN), true);
            }
            return targets.size();
        } catch (Exception e) {
            ctx.getSource().sendFailure(msg("Error: " + e.getMessage(), ChatFormatting.RED));
            return 0;
        }
    }

    private static int removePower(CommandContext<CommandSourceStack> ctx) {
        try {
            for (ServerPlayer player : EntityArgument.getPlayers(ctx, "target")) {
                player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
                    data.setPowerType(PowerType.NONE);
                    player.refreshDimensions();
                    NetworkHandler.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new PacketSyncPowerData(player, data));
                    PlayerPowerSync.syncToTrackingAndSelf(player, data);
                    player.sendSystemMessage(msg("Your class was reset.", ChatFormatting.GRAY));
                });
                ctx.getSource().sendSuccess(
                        () -> msg("Reset class for " + player.getName().getString(), ChatFormatting.GREEN), true);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int getInfo(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            target.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
                String form = PlayerFormData.getForm(target);
                ctx.getSource().sendSuccess(() -> msg(target.getName().getString(), ChatFormatting.YELLOW)
                        .append(msg(" Class: ", ChatFormatting.GRAY))
                        .append(Component.literal(data.getPowerType().getId().toUpperCase())
                                .withStyle(data.getPowerType().getColor()))
                        .append(msg(" | Ability: " + (data.isAbilityUnlocked() ? "UNLOCKED" : "LOCKED")
                                + " | Ult: " + (data.isUltUnlocked() ? "UNLOCKED" : "LOCKED")
                                + (form.isEmpty() ? "" : " | Form: " + form),
                                ChatFormatting.GRAY)),
                        false);
            });
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int unlockAbility(CommandContext<CommandSourceStack> ctx) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "target");
            String type = StringArgumentType.getString(ctx, "type");
            for (ServerPlayer player : targets) {
                player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
                    if (type.equalsIgnoreCase("ability")) {
                        data.setAbilityUnlocked(true);
                        player.sendSystemMessage(msg("Ability unlocked!", ChatFormatting.GREEN));
                    } else if (type.equalsIgnoreCase("ultimate")) {
                        data.setUltUnlocked(true);
                        player.sendSystemMessage(msg("Ultimate unlocked!", ChatFormatting.GOLD));
                    }
                    NetworkHandler.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new PacketSyncPowerData(player, data));
                });
                ctx.getSource().sendSuccess(
                        () -> msg("Unlocked " + type + " for " + player.getName().getString(),
                                ChatFormatting.GREEN),
                        true);
            }
            return targets.size();
        } catch (Exception e) {
            ctx.getSource().sendFailure(msg("Error: " + e.getMessage(), ChatFormatting.RED));
            return 0;
        }
    }
}
