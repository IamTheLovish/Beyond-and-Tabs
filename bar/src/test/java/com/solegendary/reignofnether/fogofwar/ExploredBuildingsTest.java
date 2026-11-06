package com.solegendary.reignofnether.fogofwar;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ATTACK_BUILDING under the fog (Oct 28 list on #35, "Fog leaks"; Command Center Eris.17, Nov 6): a player may order an
 * attack on a building they can see now or have seen before (explored memory, like an RTS ghost building), never on one
 * they never saw; fog off, anything. "Seen" is marked on every server visibility update where the building is visible,
 * so a glimpse of one update counts.
 */
class ExploredBuildingsTest {

    /** Stand-ins for BuildingPlacement: identity is what counts (a rebuilt building at the same spot is a new one). */
    static final class B {
        final String name;

        B(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof B b && b.name.equals(name);   // equal by value, so the memory must not use equals
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    @Test
    void aBuildingNeverSeenIsNotKnownAndItsAttackIsRefused() {
        ExploredBuildings<String, B> e = new ExploredBuildings<>();
        B keep = new B("keep");
        e.update("me", List.of(keep), b -> false);
        assertFalse(e.hasSeen("me", keep));
        assertFalse(ExploredBuildings.mayTarget(true, false, e.hasSeen("me", keep)));
    }

    @Test
    void oneUpdateWithTheBuildingInViewIsEnoughAndItStaysKnownOnceFogged() {
        ExploredBuildings<String, B> e = new ExploredBuildings<>();
        B keep = new B("keep");
        e.update("me", List.of(keep), b -> true);    // a glimpse: one visibility update
        e.update("me", List.of(keep), b -> false);   // fogged again
        e.update("me", List.of(keep), b -> false);
        assertTrue(e.hasSeen("me", keep));
        assertTrue(ExploredBuildings.mayTarget(true, false, e.hasSeen("me", keep)));
    }

    @Test
    void aBuildingInViewNowMayBeAttackedWhateverTheMemory() {
        assertTrue(ExploredBuildings.mayTarget(true, true, false));
    }

    @Test
    void withFogOffEveryAttackIsAllowed() {
        assertTrue(ExploredBuildings.mayTarget(false, false, false));
    }

    @Test
    void oneSidesMemoryIsNotAnothers() {
        ExploredBuildings<String, B> e = new ExploredBuildings<>();
        B keep = new B("keep");
        e.update("me", List.of(keep), b -> true);
        e.update("them", List.of(keep), b -> false);
        assertTrue(e.hasSeen("me", keep));
        assertFalse(e.hasSeen("them", keep));
    }

    @Test
    void aRebuiltBuildingAtTheSameSpotIsNotTheOneSeen() {
        ExploredBuildings<String, B> e = new ExploredBuildings<>();
        B first = new B("keep"), rebuilt = new B("keep");
        e.update("me", List.of(first), b -> true);
        assertTrue(e.hasSeen("me", first));
        assertFalse(e.hasSeen("me", rebuilt));
    }

    @Test
    void aNewMatchForgetsEverything() {
        ExploredBuildings<String, B> e = new ExploredBuildings<>();
        B keep = new B("keep");
        e.update("me", Set.of(keep), b -> true);
        e.forget();
        assertFalse(e.hasSeen("me", keep));
    }
}
