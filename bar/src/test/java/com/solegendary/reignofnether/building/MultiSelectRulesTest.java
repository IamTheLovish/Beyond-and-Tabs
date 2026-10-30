package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules behind a command card over several selected buildings (tester, #38 items 41-42): an upgrade button must
 * not vanish while any selected lab can still take it, and a queued unit goes to a factory that can build it now.
 */
class MultiSelectRulesTest {

    // ---------------------------------------------------------------- firstVisible

    @Test
    void theShownBuildingKeepsTheButtonWhileItCanTakeIt() {
        assertEquals("a", MultiSelectRules.firstVisible("a", List.of("a", "b", "c"), s -> true));
    }

    @Test
    void theButtonMovesToTheFirstSelectedBuildingThatCanStillTakeIt() {
        // the shown lab is maxed out (the upgrade is hidden for it) but b and c are not: the card must keep the button
        Set<String> canUpgrade = Set.of("b", "c");
        assertEquals("b", MultiSelectRules.firstVisible("a", List.of("a", "b", "c"), canUpgrade::contains));
        // selection order decides, not the alphabet
        assertEquals("c", MultiSelectRules.firstVisible("a", List.of("a", "c", "b"), canUpgrade::contains));
    }

    @Test
    void theButtonIsHiddenOnlyWhenNoSelectedBuildingCanTakeIt() {
        assertNull(MultiSelectRules.firstVisible("a", List.of("a", "b", "c"), s -> false));
        assertNull(MultiSelectRules.firstVisible("a", List.of(), s -> false));
    }

    @Test
    void withNothingShownTheFirstVisibleGroupMemberIsUsed() {
        assertEquals("b", MultiSelectRules.firstVisible(null, List.of("a", "b", "c"), s -> !s.equals("a")));
        assertNull(MultiSelectRules.firstVisible(null, List.of("a"), s -> false));
    }

    @Test
    void theShownBuildingIsNotAskedTwice() {
        List<String> asked = new ArrayList<>();
        MultiSelectRules.firstVisible("a", List.of("a", "b"), s -> {
            asked.add(s);
            return false;
        });
        assertEquals(List.of("a", "b"), asked);
    }

    // ---------------------------------------------------------------- pickRoundRobin

    @Test
    void noFactoriesMeansNobodyTakesTheUnit() {
        assertEquals(-1, MultiSelectRules.pickRoundRobin(0, 0, i -> true, i -> true));
        assertEquals(-1, MultiSelectRules.pickRoundRobin(-3, 0, i -> true, i -> true));
    }

    @Test
    void theWalkStartsAtTheStartIndexAndWraps() {
        assertEquals(2, MultiSelectRules.pickRoundRobin(4, 2, i -> true, i -> true));
        // 6 % 4 = 2, and a negative start wraps from the end (floorMod): -1 is the last factory
        assertEquals(2, MultiSelectRules.pickRoundRobin(4, 6, i -> true, i -> true));
        assertEquals(3, MultiSelectRules.pickRoundRobin(4, -1, i -> true, i -> true));
        // the start factory refuses: the walk goes on to the next, wrapping past the end
        assertEquals(0, MultiSelectRules.pickRoundRobin(4, 3, i -> true, i -> i == 0));
    }

    @Test
    void feedingOneAtATimeSpreadsUnitsEvenly() {
        int n = 3;
        int[] got = new int[n];
        int start = 0;
        for (int unit = 0; unit < 9; unit++) {
            int picked = MultiSelectRules.pickRoundRobin(n, start, i -> true, i -> true);
            got[picked]++;
            start = picked + 1;   // how the card advances the cursor
        }
        assertEquals(3, got[0]);
        assertEquals(3, got[1]);
        assertEquals(3, got[2]);
    }

    @Test
    void aFactoryThatCanBuildNowBeatsOneThatIsBusyUpgrading() {
        // factory 0 is mid-upgrade (not preferred) but is next in the walk: it must only get the unit as a last resort
        Set<Integer> canBuildNow = Set.of(1, 2);
        assertEquals(1, MultiSelectRules.pickRoundRobin(3, 0, canBuildNow::contains, i -> true));
    }

    @Test
    void anUpgradingFactoryIsUsedWhenNoOtherFactoryTakesTheUnit() {
        // 1 and 2 are preferred but full; 0 (upgrading) accepts
        assertEquals(0, MultiSelectRules.pickRoundRobin(3, 1, i -> i != 0, i -> i == 0));
    }

    @Test
    void notPreferredFactoriesAreNotAskedInTheFirstPass() {
        List<Integer> asked = new ArrayList<>();
        int picked = MultiSelectRules.pickRoundRobin(3, 0, i -> i == 2, i -> {
            asked.add(i);
            return true;
        });
        assertEquals(2, picked);
        assertEquals(List.of(2), asked);
    }

    @Test
    void whenEveryFactoryRefusesTheUnitIsNotQueuedAnywhere() {
        assertEquals(-1, MultiSelectRules.pickRoundRobin(5, 2, i -> true, i -> false));
        assertEquals(-1, MultiSelectRules.pickRoundRobin(5, 2, i -> false, i -> false));
    }

    @Test
    void aSingleFactoryIsAlwaysTheOneChosenWhenItAccepts() {
        for (int start = -3; start <= 3; start++)
            assertTrue(MultiSelectRules.pickRoundRobin(1, start, i -> true, i -> true) == 0);
    }
}
