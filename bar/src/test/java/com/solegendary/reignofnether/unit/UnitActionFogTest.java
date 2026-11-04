package com.solegendary.reignofnether.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Backlog 5b (Command Center Eris.11, Nov 4): the server refuses an ATTACK or FOLLOW order on an entity the sender's
 * fog hides (Mode A review on #35, Oct 28, "ATTACK/FOLLOW/ATTACK_BUILDING targets aren't fog-checked on the server").
 * This pins which orders carry an entity target that must be seen; UnitActionServerboundPacket.accept applies it with
 * FogOfWarServerEvents.isEntityVisibleTo.
 */
class UnitActionFogTest {

    @Test
    void attackAndFollowNeedASeenTarget() {
        assertTrue(UnitAction.ATTACK.needsASeenTarget());
        assertTrue(UnitAction.FOLLOW.needsASeenTarget());
    }

    @Test
    void ordersOnAPlaceAreNotRefusedForFog() {
        // a move or attack-move into fog is how fog gets scouted, and ATTACK_BUILDING aims at a block position (a
        // remembered building in fog is a fair target)
        for (UnitAction a : new UnitAction[] { UnitAction.MOVE, UnitAction.ATTACK_MOVE, UnitAction.ATTACK_BUILDING,
                UnitAction.STOP, UnitAction.HOLD })
            assertFalse(a.needsASeenTarget(), a.name());
    }
}
