package com.occka.occkapowers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.occka.occkapowers.ability.PowerType;
//import com.occka.occkapowers.item.DeathNoteItem;
import com.occka.occkapowers.network.NetworkHandler;
import com.occka.occkapowers.network.PacketSyncPowerData;
import com.occka.occkapowers.registry.ModCapabilities;
//import com.occka.occkapowers.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.network.chat.MutableComponent;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class OcckaCommand {

    private static final List<String> POWER_IDS = Arrays.stream(PowerType.values())
            .filter(p -> p != PowerType.NONE).map(PowerType::getId).toList();

    private static final List<String> RELIC_IDS = List.of("death_note", "flying_axe", "pupunya_helmet",
            "phoenix_feather", "ender_eye_artifact");

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_POWERS = (ctx, builder) -> {
        POWER_IDS.forEach(builder::suggest);
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_RELICS = (ctx, builder) -> {
        RELIC_IDS.forEach(builder::suggest);
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

                .then(Commands.literal("give")
                        .then(Commands.argument("target", EntityArgument.players())
                                .then(Commands.argument("power", StringArgumentType.word())
                                        .suggests(SUGGEST_POWERS)
                                        .executes(OcckaCommand::givePower))))

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

        // .then(Commands.literal("relic")
        // .then(Commands.argument("target", EntityArgument.players())
        // .then(Commands.argument("relic", StringArgumentType.word())
        // .suggests(SUGGEST_RELICS)
        // .executes(OcckaCommand::giveRelic))))
        //
        // .then(Commands.literal("deathnote")
        // .then(Commands.argument("target", EntityArgument.player())
        // .executes(OcckaCommand::deathNoteKill)))
        );
    }

    private static int givePower(CommandContext<CommandSourceStack> ctx) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "target");
            String powerName = StringArgumentType.getString(ctx, "power");
            PowerType type = PowerType.fromId(powerName);

            if (type == PowerType.NONE) {
                ctx.getSource()
                        .sendFailure(msg("Unknown class: " + powerName + ". Available: " + String.join(", ", POWER_IDS),
                                ChatFormatting.RED));
                return 0;
            }

            for (ServerPlayer player : targets) {
                player.getCapability(ModCapabilities.PLAYER_POWER).ifPresent(data -> {
                    data.setPowerType(type);
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new PacketSyncPowerData(data));
                    player.sendSystemMessage(msg("You received class: ", ChatFormatting.GREEN)
                            .append(Component.literal(type.getId().toUpperCase()).withStyle(type.getColor(),
                                    ChatFormatting.BOLD)));
                    player.sendSystemMessage(
                            msg("[R] Shift  [F] Ability  [G] Ult | Unlock ability/ult by pressing the key!",
                                    ChatFormatting.GRAY));
                });
                ctx.getSource()
                        .sendSuccess(() -> msg(
                                "Given class " + type.getId().toUpperCase() + " to " + player.getName().getString(),
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
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new PacketSyncPowerData(data));
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
                ctx.getSource().sendSuccess(() -> msg(target.getName().getString(), ChatFormatting.YELLOW)
                        .append(msg(" Class: ", ChatFormatting.GRAY))
                        .append(Component.literal(data.getPowerType().getId().toUpperCase())
                                .withStyle(data.getPowerType().getColor()))
                        .append(msg(" | Ability: " + (data.isAbilityUnlocked() ? "UNLOCKED" : "LOCKED")
                                + " | Ult: " + (data.isUltUnlocked() ? "UNLOCKED" : "LOCKED"), ChatFormatting.GRAY)),
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
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new PacketSyncPowerData(data));
                });
                ctx.getSource().sendSuccess(
                        () -> msg("Unlocked " + type + " for " + player.getName().getString(), ChatFormatting.GREEN),
                        true);
            }
            return targets.size();
        } catch (Exception e) {
            ctx.getSource().sendFailure(msg("Error: " + e.getMessage(), ChatFormatting.RED));
            return 0;
        }
    }

    // private static int giveRelic(CommandContext<CommandSourceStack> ctx) {
    // try {
    // Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "target");
    // String relicId = StringArgumentType.getString(ctx, "relic");
    //
    // for (ServerPlayer player : targets) {
    // ItemStack relic = buildRelic(relicId, player);
    // if (relic == null) {
    // ctx.getSource().sendFailure(msg("Unknown relic: " + relicId,
    // ChatFormatting.RED));
    // return 0;
    // }
    // player.getInventory().add(relic);
    // ctx.getSource().sendSuccess(() -> msg("Given relic " + relicId + " to " +
    // player.getName().getString(), ChatFormatting.GREEN), true);
    // }
    // return targets.size();
    // } catch (Exception e) {
    // ctx.getSource().sendFailure(msg("Error: " + e.getMessage(),
    // ChatFormatting.RED));
    // return 0;
    // }
    // }

    // private static ItemStack buildRelic(String id, ServerPlayer player) {
    // return switch (id) {
    // case "death_note" -> {
    // ItemStack stack = new ItemStack(ModItems.DEATH_NOTE.get());
    // yield stack;
    // }
    // case "flying_axe" -> {
    // ItemStack stack = new ItemStack(ModItems.FLYING_AXE.get());
    // stack.enchant(Enchantments.VANISHING_CURSE, 1);
    // stack.getOrCreateTag().putBoolean("Unbreakable", true);
    // yield stack;
    // }
    // case "pupunya_helmet" -> {
    // ItemStack stack = new ItemStack(ModItems.PUPUNYA_HELMET.get());
    // stack.enchant(Enchantments.VANISHING_CURSE, 1);
    // stack.enchant(Enchantments.BINDING_CURSE, 1);
    // stack.getOrCreateTag().putBoolean("Unbreakable", true);
    // // Netherite-level armor is already in ArmorMaterials.NETHERITE
    // yield stack;
    // }
    // case "phoenix_feather" -> new ItemStack(ModItems.PHOENIX_FEATHER.get());
    // case "ender_eye_artifact" -> new ItemStack(ModItems.ENDER_EYE_ART.get());
    // default -> null;
    // };
    // }

    // private static int deathNoteKill(CommandContext<CommandSourceStack> ctx) {
    // try {
    // ServerPlayer source = ctx.getSource().getPlayerOrException();
    // ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
    //
    // // Check source has death note and not on cooldown
    // boolean hasNote = source.getInventory().items.stream().anyMatch(s ->
    // s.getItem() == ModItems.DEATH_NOTE.get());
    // if (!hasNote) {
    // ctx.getSource().sendFailure(msg("You don't have a Death Note!",
    // ChatFormatting.RED));
    // return 0;
    // }
    // if (source.getCooldowns().isOnCooldown(ModItems.DEATH_NOTE.get())) {
    // ctx.getSource().sendFailure(msg("Death Note is on cooldown!",
    // ChatFormatting.RED));
    // return 0;
    // }
    // if (!(source.level() instanceof ServerLevel level)) return 0;
    //
    // // DeathNoteItem.kill(source, target, level);
    // ctx.getSource().sendSuccess(() -> msg("You wrote " +
    // target.getName().getString() + "'s name in the Death Note...",
    // ChatFormatting.DARK_RED), false);
    // return 1;
    // } catch (Exception e) {
    // ctx.getSource().sendFailure(msg("Error: " + e.getMessage(),
    // ChatFormatting.RED));
    // return 0;
    // }
    // }
}
