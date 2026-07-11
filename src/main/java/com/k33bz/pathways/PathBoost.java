package com.k33bz.pathways;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The once-per-{@code checkEveryTicks} player pass: on a {@code #pathways:paths} block, apply
 * vanilla Speed at the level {@link BoostMath} derives from sanctuary distance.
 *
 * <p>The effect duration is {@code lingerTicks + checkEveryTicks}: while you stay on the path it
 * is refreshed before it can expire; step off and it survives ~{@code lingerTicks} more — which
 * also carries you over grass gaps in natural-looking paths and over sprint-jumps.
 *
 * <p>Vanilla {@code addEffect} semantics do the conflict-resolution for free: a stronger Speed
 * (beacon, potion) is never downgraded, and our refresh only extends a same-level effect.
 */
public final class PathBoost {

    public static final TagKey<Block> PATHS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("pathways", "paths"));

    private PathBoost() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PathwaysConfig cfg = Pathways.CONFIG;
            if (cfg == null || !cfg.enabled) {
                return;
            }
            int cadence = Math.max(1, cfg.checkEveryTicks);
            if (server.getTickCount() % cadence != 0) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isSpectator()) {
                    continue;
                }
                if (!cfg.dimensions.contains(player.level().dimension().identifier().toString())) {
                    continue;
                }
                BlockState on = player.level().getBlockState(player.getOnPos());
                if (!on.is(PATHS)) {
                    continue;
                }
                int level = levelAt(cfg, player.getX(), player.getZ());
                if (level <= 0) {
                    continue;
                }
                player.addEffect(new MobEffectInstance(MobEffects.SPEED,
                        Math.max(1, cfg.lingerTicks) + cadence, level - 1,
                        true /* ambient */, false /* no particles */, true /* show icon */));
            }
        });
    }

    /** Speed level a path block grants at (x, z) — sanctuary falloff or the standalone fallback. */
    public static int levelAt(PathwaysConfig cfg, double x, double z) {
        double beyond = SanctuaryBridge.blocksBeyondSafe(x, z);
        if (Double.isNaN(beyond)) {
            return cfg.noSanctuaryLevel;
        }
        return BoostMath.level(beyond, cfg.insideLevel, cfg.ringBlocks, cfg.minLevel, cfg.maxBeyondBlocks);
    }
}
