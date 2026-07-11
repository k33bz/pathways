package com.k33bz.pathways;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * {@code /pathways} — status for everyone, live-tunable knobs (sanctuary-style) for ops.
 *
 * <p>Knob setters mutate {@link Pathways#CONFIG} in place and the tick handler reads it fresh
 * every pass, so changes apply on the next tick. {@code save} persists, {@code reload} re-reads.
 */
public final class PathwaysCommands {

    private PathwaysCommands() {
    }

    private record Knob(DoubleSupplier get, DoubleConsumer set, double min, double max) {
    }

    private static final Map<String, Knob> KNOBS = new LinkedHashMap<>();

    private static PathwaysConfig cfg() {
        return Pathways.CONFIG;
    }

    private static void knob(String key, DoubleSupplier g, DoubleConsumer s, double min, double max) {
        KNOBS.put(key, new Knob(g, s, min, max));
    }

    static {
        knob("insideLevel", () -> cfg().insideLevel, v -> cfg().insideLevel = (int) Math.round(v), 0, 10);
        knob("noSanctuaryLevel", () -> cfg().noSanctuaryLevel, v -> cfg().noSanctuaryLevel = (int) Math.round(v), 0, 10);
        knob("ringBlocks", () -> cfg().ringBlocks, v -> cfg().ringBlocks = v, 1, 100000);
        knob("minLevel", () -> cfg().minLevel, v -> cfg().minLevel = (int) Math.round(v), 0, 10);
        knob("maxBeyondBlocks", () -> cfg().maxBeyondBlocks, v -> cfg().maxBeyondBlocks = v, 0, 1000000);
        knob("lingerTicks", () -> cfg().lingerTicks, v -> cfg().lingerTicks = (int) Math.round(v), 1, 1200);
        knob("checkEveryTicks", () -> cfg().checkEveryTicks, v -> cfg().checkEveryTicks = (int) Math.round(v), 1, 100);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pathways");

            root.then(Commands.literal("status").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                PathwaysConfig cfg = cfg();
                boolean onPath = player.level().getBlockState(player.getOnPos()).is(PathBoost.PATHS);
                double beyond = SanctuaryBridge.blocksBeyondSafe(player.getX(), player.getZ());
                int level = PathBoost.levelAt(cfg, player.getX(), player.getZ());
                String zone = Double.isNaN(beyond) ? "no sanctuary data (fallback level applies)"
                        : beyond <= 0 ? "inside a sanctuary zone"
                        : String.format(Locale.ROOT, "%.0f blocks into the wilds", beyond);
                String boost = level <= 0 ? "none" : "Speed " + "I".repeat(Math.min(level, 3))
                        + (level > 3 ? " (lvl " + level + ")" : "");
                ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "[pathways] %s | %s | path boost here: %s%s",
                        onPath ? "on a path" : "not on a path", zone, boost,
                        cfg.enabled ? "" : " | DISABLED")), false);
                return Command.SINGLE_SUCCESS;
            }));

            root.then(Commands.literal("toggle").requires(Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                cfg().enabled = !cfg().enabled;
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "[pathways] " + (cfg().enabled ? "enabled" : "disabled")), true);
                return Command.SINGLE_SUCCESS;
            }));

            root.then(Commands.literal("set").requires(Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("knob", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(KNOBS.keySet(), b))
                            .then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> {
                                String key = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "knob");
                                double value = DoubleArgumentType.getDouble(ctx, "value");
                                Knob knob = KNOBS.get(key);
                                if (knob == null) {
                                    ctx.getSource().sendFailure(Component.literal(
                                            "[pathways] unknown knob: " + key + " (" + String.join(", ", KNOBS.keySet()) + ")"));
                                    return 0;
                                }
                                double clamped = Math.clamp(value, knob.min(), knob.max());
                                knob.set().accept(clamped);
                                ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                                        "[pathways] %s = %s (live; /pathways save to persist)", key, clamped)), true);
                                return Command.SINGLE_SUCCESS;
                            }))));

            root.then(Commands.literal("save").requires(Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                cfg().save();
                ctx.getSource().sendSuccess(() -> Component.literal("[pathways] config saved"), true);
                return Command.SINGLE_SUCCESS;
            }));

            root.then(Commands.literal("reload").requires(Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                Pathways.CONFIG = PathwaysConfig.load();
                ctx.getSource().sendSuccess(() -> Component.literal("[pathways] config reloaded"), true);
                return Command.SINGLE_SUCCESS;
            }));

            dispatcher.register(root);
        });
    }
}
