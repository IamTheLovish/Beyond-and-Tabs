package com.solegendary.reignofnether.unit.goals;

import org.junit.jupiter.api.Test;

import static com.solegendary.reignofnether.unit.goals.RepathProgress.Verdict.HOLD;
import static com.solegendary.reignofnether.unit.goals.RepathProgress.Verdict.PROGRESS;
import static com.solegendary.reignofnether.unit.goals.RepathProgress.Verdict.STUCK;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The plain move goal's repath verdict: only the unit's own advance toward its target resets its give-up. */
class RepathProgressTest {

    @Test
    void theUnitGettingCloserToTheTargetIsProgressWhateverTheEnd() {
        assertEquals(PROGRESS, RepathProgress.judge(100, 100, 2.0, 2.0));
        assertEquals(PROGRESS, RepathProgress.judge(100, 120, 5.0, 6.0));
        assertEquals(PROGRESS, RepathProgress.judge(Double.NaN, Double.NaN, 3.0, 3.0));
    }

    @Test
    void aCloserEndTheUnitHasNotWalkedHoldsTheCountWithoutResettingIt() {
        // stress 40748556949: at the pen's wall (end x 13), a partial path ending outside the pen (x 30): 17 closer,
        // and it used to reset the give-up
        assertEquals(HOLD, RepathProgress.judge(3987, 3970, 0.0, 0.0));
        assertEquals(HOLD, RepathProgress.judge(Double.NaN, 50, 0.0, 0.0));
    }

    @Test
    void walkingAwayFromTheTargetHoldsTheCount() {
        // a detour delivered in segments walks away from the target first: no reset, but no step toward the give-up
        assertEquals(HOLD, RepathProgress.judge(100, 100, -5.0, 5.0));
        // stress 40748556949: the unit walked 6 blocks west along the wall trying to follow the path out of the pen;
        // that used to count as progress ("walked 2 blocks", any way) and reset the give-up again
        assertEquals(HOLD, RepathProgress.judge(3970, 3970, -4.3, 6.0));
    }

    @Test
    void noCloserEndAndAUnitThatStoodIsStuck() {
        // the same 60-node path again, the unit standing where it stopped on it
        assertEquals(STUCK, RepathProgress.judge(3970, 3970, -0.1, 0.1));
        // a unit at the wall shifted a block sideways: an end one block over is no closer
        assertEquals(STUCK, RepathProgress.judge(3987, 3986.6, 0.0, 1.0));
        assertEquals(STUCK, RepathProgress.judge(3987, 3990, 0.0, 0.0));
        assertEquals(STUCK, RepathProgress.judge(3987, Double.NaN, 0.0, 0.0));
        // no path at all is stuck even after a walk away
        assertEquals(STUCK, RepathProgress.judge(3987, Double.NaN, -5.0, 5.0));
    }
}
