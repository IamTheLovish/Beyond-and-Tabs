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

    /**
     * The flow step as BuildingPlacement.tickFlowConstruction runs it, on the helpers it uses (the world side - which
     * block, a chunk loaded - left out): ask for the build power's share, clamp to the window, queue the shortfall, lay
     * what is paid (layNow, plus the building tick's own one). Ticks until every block stands.
     */
    static int ticksToBuild(float buildPower, float buildTicks, int artBlocks) {
        float progress = 0;
        int placed = 0, queued = 0;
        for (int tick = 1; tick < 1_000_000; tick++) {
            float max = FlowConstruction.maxProgress(placed, queued, 1, artBlocks);
            progress = Math.min(max, progress + FlowConstruction.progressPerTick(buildPower, buildTicks));
            if (progress > 0.9999f)
                progress = 1f;
            int due = FlowConstruction.blocksDue(progress, artBlocks);
            queued += FlowConstruction.blocksToLay(due, placed, queued);
            int lay = Math.min(queued, FlowConstruction.layNow(due, placed));
            queued -= lay;
            placed += lay;
            if (queued > 0) {   // the building tick's own buildNextBlock pass
                queued--;
                placed++;
            }
            if (placed >= artBlocks)
                return tick;
        }
        return -1;
    }

    @Test
    void progressPerTickIsTheSummedBuildPowerOverTheBuildTicks() {
        assertEquals(1f / MEX_TICKS, FlowConstruction.progressPerTick(1f, MEX_TICKS), 1e-9);
        assertEquals(7f / MEX_TICKS, FlowConstruction.progressPerTick(7f, MEX_TICKS), 1e-9);
        assertEquals(0f, FlowConstruction.progressPerTick(0f, MEX_TICKS), 0f);
    }

    @Test
    void twoBuildersFinishInHalfTheTimeOfOne() {
        for (int art : new int[] { 70, 113, 273 }) {
            int one = ticksToBuild(1f, MEX_TICKS, art);
            int two = ticksToBuild(2f, MEX_TICKS, art);
            assertEquals(0.5, (double) two / one, 0.03, art + " blocks: one builder " + one + " ticks, two " + two);
        }
    }

    @Test
    void aCrowdedSiteStillRisesWithItsBuildPower() {
        // the commander and four workers (7), a dozen workers (12), a crowd of T2 constructors (24) on the Brassworks rig
        int seven = ticksToBuild(7f, MEX_TICKS, 113);
        int twelve = ticksToBuild(12f, MEX_TICKS, 113);
        int twentyFour = ticksToBuild(24f, MEX_TICKS, 113);
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
    void theStepLaysWhatIsPaidLessTheTicksOwnBlockAndNeverMoreThanTheCap() {
        assertEquals(0, FlowConstruction.layNow(10, 10));
        assertEquals(0, FlowConstruction.layNow(11, 10), "one due: the building tick's own pass lays it");
        assertEquals(2, FlowConstruction.layNow(13, 10));
        assertEquals(FlowConstruction.MAX_BLOCKS_PER_TICK - 1, FlowConstruction.layNow(100, 10));
    }

    @Test
    void progressRunsOnlyALittleAheadOfTheStandingBlocks() {
        float ahead = FlowConstruction.maxProgress(10, 0, 1, 100) - 0.10f;
        assertTrue(ahead > 0 && ahead <= (2 + FlowConstruction.MAX_BLOCKS_PER_TICK) / 100f + 1e-6, "window " + ahead);
        assertEquals(1f, FlowConstruction.maxProgress(99, 0, 1, 100), 0f);
    }
}
