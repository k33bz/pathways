package com.k33bz.pathways;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * GSON-backed config, written to {@code config/pathways.json} on first run.
 * Every balance lever lives here so the falloff can be tuned without recompiling.
 */
public class PathwaysConfig {

    // Knob ranges, shared by /pathways set and sanitize() so the file and the command agree.
    public static final int MAX_LEVEL = 10;
    public static final double RING_BLOCKS_MIN = 1, RING_BLOCKS_MAX = 100_000;
    public static final double MAX_BEYOND_MAX = 1_000_000;
    public static final double STEP_UP_MAX = 10;
    public static final int LINGER_MIN = 1, LINGER_MAX = 1200;
    public static final int CHECK_EVERY_MIN = 1, CHECK_EVERY_MAX = 100;

    public boolean enabled = true;

    // Boost strength. Levels are human-style (2 = Speed II); 0 disables.
    public int insideLevel = 2;      // on a path inside a sanctuary safe zone
    public int noSanctuaryLevel = 2; // fallback when sanctuary is absent (or its config is unavailable)

    // Wilds falloff: each ring this many blocks wide beyond the zone edge costs one level.
    // Defaults: Speed II in the zone, Speed I for the first 384 wild blocks, nothing past 768.
    public double ringBlocks = 384.0;
    public int minLevel = 0;             // floor the falloff here instead of fading to nothing
    public double maxBeyondBlocks = 4096.0; // hard cutoff (only matters when minLevel > 0)

    // Step-up: walk up full-block ledges while path-boosted, like stairs. Applied as a
    // TRANSIENT attribute modifier (never touches the base value, never stomps other mods),
    // players only, same linger window as the speed boost.
    public boolean stepUpEnabled = true;
    public double stepUpBonus = 0.4; // vanilla step height 0.6 + 0.4 = 1.0 → one full block

    // Feel. Linger keeps the boost across grass gaps in natural-looking paths and sprint-jumps.
    public int lingerTicks = 40;    // 2s after stepping off a path block
    public int checkEveryTicks = 5; // player scan cadence

    public List<String> dimensions = defaultDimensions();

    private static List<String> defaultDimensions() {
        List<String> dims = new ArrayList<>();
        dims.add("minecraft:overworld");
        return dims;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("pathways.json");
    }

    /** Persist the current values to the standard config path. */
    public void save() {
        save(path());
    }

    public static PathwaysConfig load() {
        Path path = path();
        if (Files.exists(path)) {
            try {
                PathwaysConfig cfg = GSON.fromJson(Files.readString(path), PathwaysConfig.class);
                if (cfg != null) {
                    cfg.sanitize();
                    cfg.save(path); // re-write so newly added keys appear with defaults
                    return cfg;
                }
            } catch (IOException | RuntimeException e) {
                // Never overwrite a file we could not read: an admin's typo must not silently
                // reset every knob to defaults. Keep the broken file, run on defaults in memory.
                Pathways.LOGGER.error("[pathways] Could not read {}; running on defaults until it is fixed "
                        + "(then /pathways reload). The file was left untouched.", path, e);
                PathwaysConfig cfg = new PathwaysConfig();
                cfg.sanitize();
                return cfg;
            }
        }
        PathwaysConfig cfg = new PathwaysConfig();
        cfg.save(path);
        return cfg;
    }

    /**
     * Pull every value back into the range {@code /pathways set} allows.
     *
     * <p>The tick handler trusts these fields. A hand-edited {@code "dimensions": null} used to
     * throw inside the server tick, which crashes the server; a negative or zero cadence, NaN
     * ring width or absurd step-up came straight from the file. Called on every load.
     */
    public void sanitize() {
        insideLevel = clampLevel(insideLevel);
        noSanctuaryLevel = clampLevel(noSanctuaryLevel);
        minLevel = clampLevel(minLevel);
        ringBlocks = clamp(ringBlocks, RING_BLOCKS_MIN, RING_BLOCKS_MAX, 384.0);
        maxBeyondBlocks = clamp(maxBeyondBlocks, 0, MAX_BEYOND_MAX, 4096.0);
        stepUpBonus = clamp(stepUpBonus, 0, STEP_UP_MAX, 0.4);
        lingerTicks = Math.clamp(lingerTicks, LINGER_MIN, LINGER_MAX);
        checkEveryTicks = Math.clamp(checkEveryTicks, CHECK_EVERY_MIN, CHECK_EVERY_MAX);
        if (dimensions == null) {
            dimensions = defaultDimensions();
        } else {
            dimensions = new ArrayList<>(dimensions);
            dimensions.removeIf(d -> d == null || d.isBlank());
        }
    }

    private static int clampLevel(int level) {
        return Math.clamp(level, 0, MAX_LEVEL);
    }

    // NaN (only reachable from the file) falls back to the default instead of poisoning the math
    private static double clamp(double value, double min, double max, double fallback) {
        return Double.isNaN(value) ? fallback : Math.clamp(value, min, max);
    }

    public void save(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            Pathways.LOGGER.warn("[pathways] Failed to write config", e);
        }
    }
}
