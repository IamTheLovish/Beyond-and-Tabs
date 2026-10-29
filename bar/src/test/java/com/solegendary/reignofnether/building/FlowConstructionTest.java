package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * lovish, Oct 29: "the more workers the faster something should build". Build powers add (BAR): N builders raise a
 * site in 1/N of one builder's time and drain N times the energy a second (the cost per site is fixed). Before this,
 * blocks went in at one a tick behind a two-block window, so past ~3 blocks a tick of paid progress a crowded site rose
 * no faster - a Brassworks extractor (113 blocks over 40 timed) topped out near 7 build power.
 */
class FlowConstructionTest {
    // an extractor: 40 timed blocks x 21 ticks; art 70 blocks (Sunforged) and 113 (Brassworks)
    static final float MEX_TICKS = 40 * 21f;

    @Test
    void progressPerTickIsTheSummedBuildPowerOverTheBuildTicks() {
        assertEquals(1f / MEX_TICKS, FlowConstruction.progressPerTick(1f, MEX_TICKS), 1e-9);
        assertEquals(7f / MEX_TICKS, FlowConstruction.progressPerTick(7f, MEX_TICKS), 1e-9);
        assertEquals(0f, FlowConstruction.progressPerTick(0f, MEX_TICKS), 0f);
    }

    @Test
    void twoBuildersFinishInHalfTheTimeOfOne() {
        for (int art : new int[] { 70, 113, 273 }) {
            int one = FlowConstruction.ticksToBuild(1f, MEX_TICKS, art);
            int two = FlowConstruction.ticksToBuild(2f, MEX_TICKS, art);
            assertEquals(0.5, (double) two / one, 0.03, art + " blocks: one builder " + one + " ticks, two " + two);
        }
    }

    @Test
    void aCrowdedSiteStillRisesWithItsBuildPower() {
        // the commander and four workers (7), a dozen workers (12), a crowd of T2 constructors (24) on the Brassworks rig
        int seven = FlowConstruction.ticksToBuild(7f, MEX_TICKS, 113);
        int twelve = FlowConstruction.ticksToBuild(12f, MEX_TICKS, 113);
        int twentyFour = FlowConstruction.ticksToBuild(24f, MEX_TICKS, 113);
        assertEquals(MEX_TICKS / 7f, seven, MEX_TICKS / 7f * 0.05, "7 build power: " + seven + " ticks");
        assertEquals(7.0 / 12.0, (double) twelve / seven, 0.05, "12 vs 7 build power: " + twelve + " vs " + seven);
        assertEquals(0.5, (double) twentyFour / twelve, 0.05, "24 vs 12 build power: " + twentyFour + " vs " + twelve);
    }

    @Test
    void blocksGoInOnlyAsFarAsTheProgressPaidAndNeverMoreThanTheCapATick() {
        assertEquals(0, FlowConstruction.blocksToLay(10, 10, 0));
        assertEquals(3, FlowConstruction.blocksToLay(13, 10, 0));
        assertEquals(2, FlowConstruction.blocksToLay(13, 10, 1), "a queued block counts as laid");
        assertEquals(FlowConstruction.MAX_BLOCKS_PER_TICK, FlowConstruction.blocksToLay(100, 10, 0));
        assertEquals(0, FlowConstruction.blocksToLay(5, 10, 0), "a site ahead of its progress (rebuilt blocks) lays none");
    }

    @Test
    void progressRunsOnlyALittleAheadOfTheStandingBlocks() {
        float ahead = FlowConstruction.maxProgress(10, 0, 1, 100) - 0.10f;
        assertTrue(ahead > 0 && ahead <= (2 + FlowConstruction.MAX_BLOCKS_PER_TICK) / 100f + 1e-6, "window " + ahead);
        assertEquals(1f, FlowConstruction.maxProgress(99, 0, 1, 100), 0f);
    }
}
