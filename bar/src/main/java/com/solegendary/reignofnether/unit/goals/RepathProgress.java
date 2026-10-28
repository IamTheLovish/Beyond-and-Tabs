package com.solegendary.reignofnether.unit.goals;

/**
 * How a plain move goal reads one of its repaths (MoveToTargetBlockGoal.judgedRepath): did it get the unit anywhere?
 * Plain doubles, so the rule is unit-tested without a level (RepathProgressTest).
 */
public final class RepathProgress {
    private RepathProgress() {}

    public enum Verdict {
        /** the unit itself got closer to its target: resets the stuck count and the backoff */
        PROGRESS,
        /** the new path ends closer, or the unit walked (not toward the target): the count neither grows nor resets */
        HOLD,
        /** no closer end and no closer unit (or no path): counts toward the give-up */
        STUCK
    }

    /** A path's end must be this much closer (horizontally) to the target than the one it replaced to be promising. */
    public static final double END_CLOSER_MIN = 1.0;
    /** The unit must have got this much closer to its target since the last repath for the repath to be progress. */
    public static final double ADVANCED_MIN = 2.0;

    /**
     * @param oldEndDist the replaced path's end's horizontal distance to the target, NaN when it had none
     * @param newEndDist the new path's end's distance, NaN when the repath found no path
     * @param advanced   how much closer to the target the unit itself got since the last repath (negative: farther)
     * @param walked     how far the unit moved since the last repath, any way (a detour delivered in segments walks away
     *                   from the target first: that holds the count rather than counting toward the give-up)
     *
     * Only the unit's own advance resets the count. A closer end alone used to as well: a partial path out of a closed
     * pen (A* ending it outside, x 30 for a pen ending at x 14) read as progress and reset the give-up, though the unit
     * could not follow it and walked away from the target instead (CI stress 40748556949).
     */
    public static Verdict judge(double oldEndDist, double newEndDist, double advanced, double walked) {
        if (advanced >= ADVANCED_MIN)
            return Verdict.PROGRESS;
        if (Double.isNaN(newEndDist))
            return Verdict.STUCK;
        if (Double.isNaN(oldEndDist) || oldEndDist - newEndDist >= END_CLOSER_MIN || walked >= ADVANCED_MIN)
            return Verdict.HOLD;
        return Verdict.STUCK;
    }
}
