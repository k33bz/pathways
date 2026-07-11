package com.k33bz.pathways;

/**
 * Pure falloff math — no Minecraft types, unit-tested without a game runtime.
 */
public final class BoostMath {

    private BoostMath() {
    }

    /**
     * Speed level for a path block at {@code beyond} blocks past the nearest safe-zone edge.
     *
     * <p>{@code beyond <= 0} means inside a zone and yields {@code insideLevel}. Outside, the
     * wilds are sliced into rings {@code ringBlocks} wide; each ring costs one level, floored at
     * {@code minLevel}. Past {@code maxBeyond} the boost is gone regardless (a hard frontier —
     * only reachable when {@code minLevel} keeps the falloff from fading out on its own).
     *
     * @return human-style speed level (2 = Speed II); {@code <= 0} means no effect
     */
    public static int level(double beyond, int insideLevel, double ringBlocks, int minLevel, double maxBeyond) {
        if (beyond <= 0) {
            return insideLevel;
        }
        if (beyond > maxBeyond) {
            return 0;
        }
        double ring = Math.max(1.0, ringBlocks);
        int rings = (int) Math.ceil(beyond / ring);
        return Math.max(insideLevel - rings, Math.max(minLevel, 0));
    }
}
