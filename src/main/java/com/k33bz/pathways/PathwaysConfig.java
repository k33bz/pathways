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

    public boolean enabled = true;

    // Boost strength. Levels are human-style (2 = Speed II); 0 disables.
    public int insideLevel = 2;      // on a path inside a sanctuary safe zone
    public int noSanctuaryLevel = 2; // fallback when sanctuary is absent (or its config is unavailable)

    // Wilds falloff: each ring this many blocks wide beyond the zone edge costs one level.
    // Defaults: Speed II in the zone, Speed I for the first 384 wild blocks, nothing past 768.
    public double ringBlocks = 384.0;
    public int minLevel = 0;             // floor the falloff here instead of fading to nothing
    public double maxBeyondBlocks = 4096.0; // hard cutoff (only matters when minLevel > 0)

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
        try {
            if (Files.exists(path)) {
                PathwaysConfig cfg = GSON.fromJson(Files.readString(path), PathwaysConfig.class);
                if (cfg != null) {
                    cfg.save(path); // re-write so newly added keys appear with defaults
                    return cfg;
                }
            }
        } catch (IOException | RuntimeException e) {
            Pathways.LOGGER.warn("[pathways] Failed to read config; using defaults", e);
        }
        PathwaysConfig cfg = new PathwaysConfig();
        cfg.save(path);
        return cfg;
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
