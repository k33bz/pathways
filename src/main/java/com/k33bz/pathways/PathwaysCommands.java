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
        // Ranges come from PathwaysConfig so a hand-edited file is held to the same limits
        knob("insideLevel", () -> cfg().insideLevel, v -> cfg().insideLevel = (int) Math.round(v), 0, PathwaysConfig.MAX_LEVEL);
        knob("noSanctuaryLevel", () -> cfg().noSanctuaryLevel, v -> cfg().noSanctuaryLevel = (int) Math.round(v), 0, PathwaysConfig.MAX_LEVEL);
        knob("ringBlocks", () -> cfg().ringBlocks, v -> cfg().ringBlocks = v, PathwaysConfig.RING_BLOCKS_MIN, PathwaysConfig.RING_BLOCKS_MAX);
        knob("minLevel", () -> cfg().minLevel, v -> cfg().minLevel = (int) Math.round(v), 0, PathwaysConfig.MAX_LEVEL);
        knob("maxBeyondBlocks", () -> cfg().maxBeyondBlocks, v -> cfg().maxBeyondBlocks = v, 0, PathwaysConfig.MAX_BEYOND_MAX);
        knob("stepUpEnabled", () -> cfg().stepUpEnabled ? 1 : 0, v -> cfg().stepUpEnabled = v >= 0.5, 0, 1);
        knob("stepUpBonus", () -> cfg().stepUpBonus, v -> cfg().stepUpBonus = v, 0, PathwaysConfig.STEP_UP_MAX);
        knob("lingerTicks", () -> cfg().lingerTicks, v -> cfg().lingerTicks = (int) Math.round(v), PathwaysConfig.LINGER_MIN, PathwaysConfig.LINGER_MAX);
        knob("checkEveryTicks", () -> cfg().checkEveryTicks, v -> cfg().checkEveryTicks = (int) Math.round(v), PathwaysConfig.CHECK_EVERY_MIN, PathwaysConfig.CHECK_EVERY_MAX);
    }

    /** "inside a sanctuary zone" / "N blocks into the wilds" / no-sanctuary fallback. */
    private static String zoneText(double beyond) {
        return Double.isNaN(beyond) ? "no sanctuary data (fallback level applies)"
                : beyond <= 0 ? "inside a sanctuary zone"
                : String.format(Locale.ROOT, "%.0f blocks into the wilds", beyond);
    }

    private static String boostText(int level) {
        return level <= 0 ? "none" : "Speed " + "I".repeat(Math.min(level, 3)) + (level > 3 ? " (lvl " + level + ")" : "");
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
                String zone = zoneText(beyond);
                String boost = boostText(level);
                String stepUp = !cfg.stepUpEnabled ? "off"
                        : PathBoost.hasStepUp(player)
                                ? String.format(Locale.ROOT, "+%.1f active", cfg.stepUpBonus)
                                : "idle";
                ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "[pathways] %s | %s | path boost here: %s | step-up: %s%s",
                        onPath ? "on a path" : "not on a path", zone, boost, stepUp,
                        cfg.enabled ? "" : " | DISABLED")), false);
                return Command.SINGLE_SUCCESS;
            }));

            // What a path would grant at any (x, z), from anywhere, including the console. For
            // admins planning roads, and for CI, which has no player to stand on a path.
            // Read-only, but it reveals where sanctuary zones end, so it is op-only.
            root.then(Commands.literal("at").requires(Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                            .then(Commands.argument("z", DoubleArgumentType.doubleArg()).executes(ctx -> {
                                double x = DoubleArgumentType.getDouble(ctx, "x");
                                double z = DoubleArgumentType.getDouble(ctx, "z");
                                PathwaysConfig cfg = cfg();
                                double beyond = SanctuaryBridge.blocksBeyondSafe(x, z);
                                int level = PathBoost.levelAt(cfg, x, z);
                                ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                                        "[pathways] at %.0f %.0f: %s | path boost: %s | level=%d beyond=%s%s",
                                        x, z, zoneText(beyond), boostText(level), level,
                                        Double.isNaN(beyond) ? "none" : String.format(Locale.ROOT, "%.1f", beyond),
                                        cfg.enabled ? "" : " | DISABLED")), false);
                                return Command.SINGLE_SUCCESS;
                            }))));

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
