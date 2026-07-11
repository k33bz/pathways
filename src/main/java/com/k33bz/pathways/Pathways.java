package com.k33bz.pathways;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pathways — built paths grant a native Speed effect that follows civilization.
 *
 * <p>Standing on a block in {@code #pathways:paths} applies vanilla Speed, refreshed while you
 * stay on the path and lingering ~2s after you step off (so natural-looking paths with grass
 * gaps, and sprint-jumps, keep the boost). When the sanctuary mod is present, the boost level
 * follows its safety geometry: full strength inside a sanctuary zone, dropping one level per
 * ring of wilds beyond the zone edge — a built road between sanctuaries outruns a straight line.
 *
 * <p>Server-authoritative: vanilla clients see a plain Speed effect icon, nothing to install.
 */
public class Pathways implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("pathways");

    /** Live config; commands mutate it in place, tick handler reads it fresh every pass. */
    public static PathwaysConfig CONFIG;

    @Override
    public void onInitialize() {
        CONFIG = PathwaysConfig.load();
        PathBoost.register();
        PathwaysCommands.register();
        LOGGER.info("[pathways] initialized (sanctuary detected: {})", SanctuaryBridge.isPresent());
    }
}
