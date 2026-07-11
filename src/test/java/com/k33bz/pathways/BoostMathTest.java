package com.k33bz.pathways;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoostMathTest {

    // Defaults: insideLevel 2, ring 384, minLevel 0, maxBeyond 4096
    private static int lvl(double beyond) {
        return BoostMath.level(beyond, 2, 384.0, 0, 4096.0);
    }

    @Test
    void insideZoneGetsFullLevel() {
        assertEquals(2, lvl(-500.0));
        assertEquals(2, lvl(0.0));
    }

    @Test
    void firstRingDropsOneLevel() {
        assertEquals(1, lvl(0.001));
        assertEquals(1, lvl(200.0));
        assertEquals(1, lvl(384.0)); // ring boundary is inclusive
    }

    @Test
    void secondRingFadesOut() {
        assertEquals(0, lvl(384.001));
        assertEquals(0, lvl(768.0));
        assertEquals(0, lvl(3000.0));
    }

    @Test
    void minLevelFloorsTheFalloff() {
        // Frontier roads: keep Speed I out to the hard cutoff.
        assertEquals(1, BoostMath.level(3000.0, 2, 384.0, 1, 4096.0));
        assertEquals(0, BoostMath.level(4097.0, 2, 384.0, 1, 4096.0));
    }

    @Test
    void higherInsideLevelGetsMoreRings() {
        assertEquals(3, BoostMath.level(-1.0, 3, 384.0, 0, 4096.0));
        assertEquals(2, BoostMath.level(100.0, 3, 384.0, 0, 4096.0));
        assertEquals(1, BoostMath.level(500.0, 3, 384.0, 0, 4096.0));
        assertEquals(0, BoostMath.level(900.0, 3, 384.0, 0, 4096.0));
    }

    @Test
    void degenerateRingWidthIsSafe() {
        // ring width clamps to 1 block; no divide-by-zero, falloff just becomes steep
        assertEquals(0, BoostMath.level(5.0, 2, 0.0, 0, 4096.0));
    }
}
