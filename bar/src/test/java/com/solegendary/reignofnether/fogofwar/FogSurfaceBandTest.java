package com.solegendary.reignofnether.fogofwar;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The surface band FogRemesh re-meshes on a fog change (Oct 21 perf plan, PR R1). A 1.20 world: sections -4..19. */
class FogSurfaceBandTest {

    private static final int MIN = -4, MAX = 20;   // maxSection is exclusive, as Level.getMaxSection

    private static int low(int lowestTop, Set<Integer> seeThrough) {
        return FogSurfaceBand.low(lowestTop, MIN, MAX, seeThrough::contains);
    }

    @Test
    void grasslandKeepsOneSectionUnderTheGround() {
        // ground at y 64-70: section 4 (64-79), the margin is section 3 (48-63); the 7 sections under it are left alone
        assertEquals(3, low(64, Set.of()));
        assertEquals(3, low(70, Set.of()));
        assertEquals(2, low(63, Set.of()));
    }

    @Test
    void aSeaIsSeenToItsBed() {
        // surface at 62 (section 3), water down to a bed at 40 (section 2): the bed's section and one under it
        assertEquals(1, low(62, Set.of(3, 2)));
        // a shallow pond inside the surface section costs nothing extra
        assertEquals(2, low(62, Set.of(3)));
    }

    @Test
    void aCanopyIsSeenThroughToTheGround() {
        // jungle canopy at y 100 (section 6), leaves down through 5 and 4 to the ground at 64: margin section 3
        assertEquals(3, low(100, Set.of(6, 5, 4)));
        // without the leaves the band would stop at section 5 and the ground under hidden leaves keep an old tint
        assertEquals(5, low(100, Set.of()));
    }

    @Test
    void theWalkThroughWaterIsCapped() {
        // an aquifer chain under a lake stops after MAX_SEE_THROUGH_SECTIONS
        Set<Integer> wet = Set.of(4, 3, 2, 1, 0, -1, -2, -3);
        assertEquals(4 - FogSurfaceBand.MAX_SEE_THROUGH_SECTIONS, low(64, wet));
    }

    @Test
    void theBandNeverLeavesTheWorld() {
        assertEquals(MIN, low(-64, Set.of()));                 // a column down to bedrock
        assertEquals(MIN, low(-65, Set.of()));                 // an empty (void) column: the whole height
        assertEquals(MIN, low(-60, Set.of(-4)));
        assertEquals(MAX - 2, low(400, Set.of()));             // above the build limit: clamped, then the margin
    }

    @Test
    void theTopIsTheHighestSectionWithBlocks() {
        assertEquals(6, FogSurfaceBand.high(6, 3, MAX));       // a tree or a tower up to section 6
        assertEquals(3, FogSurfaceBand.high(Integer.MIN_VALUE, 3, MAX));   // nothing at all: never under low
        assertEquals(3, FogSurfaceBand.high(1, 3, MAX));
        assertEquals(MAX - 1, FogSurfaceBand.high(40, 3, MAX));
    }
}
