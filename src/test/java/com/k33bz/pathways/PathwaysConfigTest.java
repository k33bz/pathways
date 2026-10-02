package com.k33bz.pathways;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tick handler trusts every config field, so whatever the file says, sanitize() must leave
 * values the tick can run on. A null list or a zero cadence in the file used to throw (or divide)
 * inside the server tick, which takes the whole server down.
 */
class PathwaysConfigTest {

    private static PathwaysConfig parse(String json) {
        PathwaysConfig cfg = new Gson().fromJson(json, PathwaysConfig.class);
        cfg.sanitize();
        return cfg;
    }

    @Test
    void defaultsSurviveSanitizeUnchanged() {
        PathwaysConfig cfg = new PathwaysConfig();
        cfg.sanitize();
        assertEquals(2, cfg.insideLevel);
        assertEquals(384.0, cfg.ringBlocks);
        assertEquals(40, cfg.lingerTicks);
        assertEquals(5, cfg.checkEveryTicks);
        assertEquals(0.4, cfg.stepUpBonus);
        assertEquals(List.of("minecraft:overworld"), cfg.dimensions);
    }

    @Test
    void nullDimensionsFallBackToTheOverworld() {
        PathwaysConfig cfg = parse("{\"dimensions\": null}");
        assertEquals(List.of("minecraft:overworld"), cfg.dimensions);
    }

    @Test
    void blankAndNullDimensionEntriesAreDropped() {
        PathwaysConfig cfg = parse("{\"dimensions\": [\"minecraft:overworld\", null, \"  \", \"minecraft:the_nether\"]}");
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"), cfg.dimensions);
    }

    @Test
    void outOfRangeNumbersAreClampedToTheCommandRanges() {
        PathwaysConfig cfg = parse("{\"insideLevel\": 999, \"minLevel\": -5, \"ringBlocks\": 0,"
                + " \"maxBeyondBlocks\": -1, \"stepUpBonus\": 1000, \"lingerTicks\": 0, \"checkEveryTicks\": -3}");
        assertEquals(PathwaysConfig.MAX_LEVEL, cfg.insideLevel);
        assertEquals(0, cfg.minLevel);
        assertEquals(PathwaysConfig.RING_BLOCKS_MIN, cfg.ringBlocks);
        assertEquals(0.0, cfg.maxBeyondBlocks);
        assertEquals(PathwaysConfig.STEP_UP_MAX, cfg.stepUpBonus);
        assertEquals(PathwaysConfig.LINGER_MIN, cfg.lingerTicks);
        assertEquals(PathwaysConfig.CHECK_EVERY_MIN, cfg.checkEveryTicks);
    }

    @Test
    void nanFallsBackToTheDefaultInsteadOfPoisoningTheFalloff() {
        PathwaysConfig cfg = new PathwaysConfig();
        cfg.ringBlocks = Double.NaN;
        cfg.maxBeyondBlocks = Double.NaN;
        cfg.stepUpBonus = Double.NaN;
        cfg.sanitize();
        assertEquals(384.0, cfg.ringBlocks);
        assertEquals(4096.0, cfg.maxBeyondBlocks);
        assertEquals(0.4, cfg.stepUpBonus);
        assertFalse(Double.isNaN(BoostMath.level(100, cfg.insideLevel, cfg.ringBlocks, cfg.minLevel, cfg.maxBeyondBlocks)));
    }

    @Test
    void missingKeysKeepTheirDefaults() {
        PathwaysConfig cfg = parse("{\"insideLevel\": 3}");
        assertEquals(3, cfg.insideLevel);
        assertEquals(384.0, cfg.ringBlocks);
        assertTrue(cfg.enabled);
    }
}
