package com.solegendary.reignofnether.resources;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The height a metal patch's 7x7 pad is levelled to (Oct 29, PR #404's build 40986252435, shard 8:
 * every_stamped_patch_takes_every_factions_extractor_where_the_bot_looks - "1 of 43 patches refuse an extractor ...
 * nearest patch 5 ... -3,-3 +1 dirt ... player refused"). Two patches 5 apart share two rows of pad; the leveller
 * leaves an earlier pad's cells alone, so when the later pad sat one block lower those rows stayed a step above it and
 * a player's 5x5 extractor clipped them (the bot only got by on its raised-origin retries). A pad that overlaps an
 * earlier one now takes that pad's height, so overlapping pads are one flat floor.
 */
class PadHeightTest {

    @Test
    void aLonePadKeepsItsOwnGround() {
        assertEquals(63, PadHeight.of(100, 100, 63, List.of()));
        assertEquals(63, PadHeight.of(100, 100, 63, List.of(new int[] { 107, 63 + 1, 100 })), "7 apart: no shared cell");
    }

    @Test
    void anOverlappingPadTakesTheEarlierPadsHeight() {
        // the CI case: a neighbour 5 away, one block higher
        assertEquals(64, PadHeight.of(2400, 6594, 63, List.of(new int[] { 2400, 64, 6589 })));
        // diagonal overlap counts too (Chebyshev: both axes within the two pads' reach)
        assertEquals(61, PadHeight.of(0, 0, 63, List.of(new int[] { 6, 61, -6 })));
    }

    @Test
    void aCliffBetweenThemIsNotLevelledAway() {
        // more than MAX_SNAP apart in height: keep the patch's own ground (a pad must not climb a cliff - drySpot's rule)
        assertEquals(63, PadHeight.of(0, 0, 63, List.of(new int[] { 5, 63 + PadHeight.MAX_SNAP + 1, 0 })));
        assertEquals(63 + PadHeight.MAX_SNAP, PadHeight.of(0, 0, 63, List.of(new int[] { 5, 63 + PadHeight.MAX_SNAP, 0 })));
    }

    @Test
    void theNearestOverlappingPadDecides() {
        assertEquals(62, PadHeight.of(0, 0, 63, List.of(new int[] { 6, 65, 0 }, new int[] { 3, 62, 0 })));
    }
}
