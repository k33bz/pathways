package com.k33bz.pathways;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The once-per-{@code checkEveryTicks} player pass: on a {@code #pathways:paths} block, apply
 * vanilla Speed at the level {@link BoostMath} derives from sanctuary distance, plus (optional)
 * a step-height bonus so the path flows up full-block rises like stairs.
 *
 * <p>The speed duration is {@code lingerTicks + checkEveryTicks}: while you stay on the path it
 * is refreshed before it can expire; step off and it survives ~{@code lingerTicks} more — which
 * also carries you over grass gaps in natural-looking paths and over sprint-jumps.
 *
 * <p>Vanilla {@code addEffect} semantics do the conflict-resolution for free: a stronger Speed
 * (beacon, potion) is never downgraded, and our refresh only extends a same-level effect.
 *
 * <p>Step-up is a TRANSIENT attribute modifier (own id, additive) — it never touches the base
 * step-height value or anyone else's modifiers, is not persisted to disk, and is removed by the
 * expiry sweep on the same linger window the speed effect uses.
 */
public final class PathBoost {

    public static final TagKey<Block> PATHS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("pathways", "paths"));

    /** Modifier id for the step-height bonus; also how admins can spot it in /attribute output. */
    public static final Identifier STEP_UP_ID = Identifier.fromNamespaceAndPath("pathways", "step_up");

    /** Boosted players' step-up expiry (server tick). Transient, like the modifier itself. */
    private static final Map<UUID, Integer> STEP_UP_UNTIL = new HashMap<>();

    private PathBoost() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PathwaysConfig cfg = Pathways.CONFIG;
            if (cfg == null) {
                return;
            }
            int cadence = Math.max(1, cfg.checkEveryTicks);
            if (server.getTickCount() % cadence != 0) {
                return;
            }
            if (cfg.enabled) {
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
                    int window = Math.max(1, cfg.lingerTicks) + cadence;
                    player.addEffect(new MobEffectInstance(MobEffects.SPEED, window, level - 1,
                            true /* ambient */, false /* no particles */, true /* show icon */));
                    if (cfg.stepUpEnabled && cfg.stepUpBonus > 0) {
                        AttributeInstance attr = player.getAttribute(Attributes.STEP_HEIGHT);
                        if (attr != null) {
                            // addOrUpdate keeps live /pathways set stepUpBonus changes seamless.
                            attr.addOrUpdateTransientModifier(new AttributeModifier(STEP_UP_ID,
                                    cfg.stepUpBonus, AttributeModifier.Operation.ADD_VALUE));
                            STEP_UP_UNTIL.put(player.getUUID(), server.getTickCount() + window);
                        }
                    }
                }
            }
            // Expiry sweep — runs even when the mod (or step-up) is toggled off, so no
            // modifier is ever stranded on a player.
            boolean removeAll = !cfg.enabled || !cfg.stepUpEnabled;
            for (Iterator<Map.Entry<UUID, Integer>> it = STEP_UP_UNTIL.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Integer> e = it.next();
                if (!removeAll && server.getTickCount() <= e.getValue()) {
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
                if (player != null) {
                    AttributeInstance attr = player.getAttribute(Attributes.STEP_HEIGHT);
                    if (attr != null) {
                        attr.removeModifier(STEP_UP_ID);
                    }
                }
                it.remove(); // offline players lose transient modifiers on their own
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

    /** Whether this player currently carries the step-up modifier (for /pathways status). */
    public static boolean hasStepUp(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.STEP_HEIGHT);
        return attr != null && attr.hasModifier(STEP_UP_ID);
    }
}
