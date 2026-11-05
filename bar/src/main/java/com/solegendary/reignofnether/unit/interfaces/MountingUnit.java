package com.solegendary.reignofnether.unit.interfaces;

import com.solegendary.reignofnether.unit.goals.MountGoal;

import javax.annotation.Nullable;

/**
 * A unit with a {@link MountGoal}: one a Mount order (Mount Spider / Ravager / Hoglin) walks to its mount. While the
 * goal has a mount to go to the unit has an order and is not idle ({@link Unit#isIdle}), the same rule a move order
 * follows, so idle auto-aggression does not pick a fight for it on the way (#507).
 */
public interface MountingUnit {
    /** This unit's mount goal, or null if it has none. */
    @Nullable
    MountGoal getMountGoal();

    /** Is this unit on its way to a mount (its mount goal has a living target)? */
    static boolean isGoingToMount(Object unit) {
        if (!(unit instanceof MountingUnit m))
            return false;
        MountGoal goal = m.getMountGoal();
        return goal != null && goal.getTarget() != null && goal.getTarget().isAlive();
    }
}
