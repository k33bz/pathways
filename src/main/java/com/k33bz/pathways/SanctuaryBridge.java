package com.k33bz.pathways;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Reflection-only bridge to the sanctuary mod, so this repo builds and runs standalone.
 *
 * <p>Resolves {@code Sanctuary.blocksBeyondNearestAnchor(SanctuaryConfig, double, double)} and
 * the {@code Sanctuary.CONFIG} static once, via MethodHandles (invocation cost is then native).
 * That helper is the same geometry sanctuary's own mob scaling uses — config anchors (spawn)
 * merged with placed crystal anchors, dormant anchors excluded — so path boosts and world danger
 * always agree about where civilization ends.
 *
 * <p>If sanctuary is absent, or its internals ever rename, everything degrades to
 * {@link Double#NaN} and the caller falls back to {@code noSanctuaryLevel}.
 */
public final class SanctuaryBridge {

    private static volatile boolean resolved;
    private static MethodHandle configGetter; // () -> SanctuaryConfig (static field CONFIG)
    private static MethodHandle beyond;       // (SanctuaryConfig, double, double) -> double

    private SanctuaryBridge() {
    }

    public static boolean isPresent() {
        return FabricLoader.getInstance().isModLoaded("sanctuary");
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!isPresent()) {
            return;
        }
        try {
            Class<?> sanctuary = Class.forName("com.k33bz.sanctuary.Sanctuary");
            Class<?> config = Class.forName("com.k33bz.sanctuary.SanctuaryConfig");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            configGetter = lookup.findStaticGetter(sanctuary, "CONFIG", config);
            beyond = lookup.findStatic(sanctuary, "blocksBeyondNearestAnchor",
                    MethodType.methodType(double.class, config, double.class, double.class));
            Pathways.LOGGER.info("[pathways] sanctuary bridge resolved");
        } catch (Throwable t) {
            configGetter = null;
            beyond = null;
            Pathways.LOGGER.warn("[pathways] sanctuary present but bridge failed to resolve; "
                    + "falling back to noSanctuaryLevel", t);
        }
    }

    /**
     * Blocks beyond the nearest safe-zone edge at (x, z); negative inside a zone.
     *
     * @return {@link Double#NaN} when sanctuary (or its config) is unavailable
     */
    public static double blocksBeyondSafe(double x, double z) {
        resolve();
        MethodHandle get = configGetter;
        MethodHandle fn = beyond;
        if (get == null || fn == null) {
            return Double.NaN;
        }
        try {
            Object cfg = get.invoke();
            if (cfg == null) {
                return Double.NaN; // sanctuary loaded but not initialized yet
            }
            return (double) fn.invoke(cfg, x, z);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }
}
