package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.resources.ResourceCost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-place building levels (design/building_upgrades.md): lovish wanted fewer, stronger labs. The numbers a match
 * lives on - how much faster and tougher a lab gets per level, what the upgrade costs and what a reclaim hands
 * back - and the structure-name rule the whole save / late-join sync relies on ("the level is read back from the
 * structure a placement wears").
 */
class BuildingLevelsTest {

    // ---------------------------------------------------------------- production speed and toughness

    @Test
    void buildPowerFollowsTheDesignedCurve() {
        assertEquals(1.0f, BuildingLevels.buildPowerAt(1), 0f);
        assertEquals(1.75f, BuildingLevels.buildPowerAt(2), 0f);
        assertEquals(2.5f, BuildingLevels.buildPowerAt(3), 0f);
    }

    @Test
    void hitPointsFollowTheDesignedCurve() {
        assertEquals(1.0f, BuildingLevels.hpMultiplier(1), 0f);
        assertEquals(1.5f, BuildingLevels.hpMultiplier(2), 0f);
        assertEquals(2.0f, BuildingLevels.hpMultiplier(3), 0f);
    }

    @Test
    void anOutOfRangeLevelIsClampedNotACrash() {
        // a corrupt save or a stale packet must never index past the table
        for (int level : new int[] { Integer.MIN_VALUE, -1, 0 }) {
            assertEquals(BuildingLevels.buildPowerAt(1), BuildingLevels.buildPowerAt(level), 0f);
            assertEquals(BuildingLevels.hpMultiplier(1), BuildingLevels.hpMultiplier(level), 0f);
        }
        for (int level : new int[] { 4, 9, 1000, Integer.MAX_VALUE }) {
            assertEquals(BuildingLevels.buildPowerAt(BuildingLevels.MAX_LEVEL), BuildingLevels.buildPowerAt(level), 0f);
            assertEquals(BuildingLevels.hpMultiplier(BuildingLevels.MAX_LEVEL), BuildingLevels.hpMultiplier(level), 0f);
        }
    }

    @Test
    void everyLevelIsStrictlyBetterThanTheOneBelow() {
        for (int level = 2; level <= BuildingLevels.MAX_LEVEL; level++) {
            assertTrue(BuildingLevels.buildPowerAt(level) > BuildingLevels.buildPowerAt(level - 1), "speed at L" + level);
            assertTrue(BuildingLevels.hpMultiplier(level) > BuildingLevels.hpMultiplier(level - 1), "hp at L" + level);
        }
    }

    @Test
    void aTwoAnnexLabCannotOutrunALevelThreeLabByMuchButAnnexesStillMatter() {
        // the annex cap: two annexes add 2 x 0.4 = +0.8 of the base speed, a full level-3 lab is +1.5
        assertEquals(0.8f, BuildingLevels.ANNEX_BUILD_POWER * BuildingLevels.ANNEX_SLOTS, 1e-6f);
        assertTrue(BuildingLevels.ANNEX_BUILD_POWER * BuildingLevels.ANNEX_SLOTS
            < BuildingLevels.buildPowerAt(BuildingLevels.MAX_LEVEL) - BuildingLevels.buildPowerAt(1));
    }

    // ---------------------------------------------------------------- the price of a level

    @Test
    void upgradeCostIsTheNextLevelsAndNullAtTheTop() {
        assertEquals(BuildingLevels.UPGRADE_L2_COST, BuildingLevels.upgradeCost(1));
        assertEquals(BuildingLevels.UPGRADE_L3_COST, BuildingLevels.upgradeCost(2));
        assertNull(BuildingLevels.upgradeCost(BuildingLevels.MAX_LEVEL));
        assertNull(BuildingLevels.upgradeCost(0));
        assertNull(BuildingLevels.upgradeCost(7));
    }

    @Test
    void upgradesAreFlowPaidAndLevelThreeCostsMoreThanLevelTwo() {
        ResourceCost l2 = BuildingLevels.UPGRADE_L2_COST, l3 = BuildingLevels.UPGRADE_L3_COST;
        assertEquals(90, l2.metal());
        assertEquals(360, l2.energy());
        assertEquals(180, l3.metal());
        assertEquals(720, l3.energy());
        assertTrue(l3.metal() > l2.metal() && l3.energy() > l2.energy() && l3.ticks > l2.ticks);
        // an upgrade takes build time but holds no population and no old-style food
        assertEquals(0, l2.population);
        assertEquals(0, l2.food);
        assertEquals(40 * ResourceCost.TICKS_PER_SECOND, l2.ticks);
    }

    @Test
    void reclaimRefundsTheMetalPutIntoEveryLevelUpToTheCurrentOne() {
        assertEquals(0, BuildingLevels.levelMetalInvested(1));
        assertEquals(BuildingLevels.UPGRADE_L2_COST.metal(), BuildingLevels.levelMetalInvested(2));
        assertEquals(BuildingLevels.UPGRADE_L2_COST.metal() + BuildingLevels.UPGRADE_L3_COST.metal(),
            BuildingLevels.levelMetalInvested(3));
        assertEquals(0, BuildingLevels.levelMetalInvested(0));
        assertEquals(BuildingLevels.levelMetalInvested(3), BuildingLevels.levelMetalInvested(8));   // nothing past L3 to refund
    }

    // ---------------------------------------------------------------- level <- structure name

    @Test
    void theLevelIsReadBackFromTheStructureName() {
        String[][] table = {
            // structure name, level
            { "barracks_l2", "2" }, { "graveyard_l3", "3" }, { "sunforged_barracks_l2", "2" },
            { "barracks", "1" }, { "barracks_l1", "1" },
            { "x_l2", "2" }, { "x_l9", "9" },
        };
        for (String[] row : table)
            assertEquals(Integer.parseInt(row[1]), BuildingLevels.levelOfStructure(row[0]), row[0]);
    }

    @Test
    void anythingThatIsNotALevelSuffixIsLevelOne() {
        String[] notLevels = {
            null, "", "l2", "_l2", "a_l", "barracks_l0", "barracks_lx", "barracks_L2", "barracks_l2x", "barracks_l22",
            "barracks l2", "barracks-l2", "barracks_2", "barracks2", "level2",
        };
        for (String name : notLevels)
            assertEquals(1, BuildingLevels.levelOfStructure(name), String.valueOf(name));
    }
}
