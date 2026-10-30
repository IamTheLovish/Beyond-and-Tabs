package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.building.production.UnitProductionPause;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * "Pause unit production" (UnitProductionPause; Joisco, Oct 23: a button to pause production while stalling metal so
 * the upgrades that matter get it). One flag per owner: their unit items make no progress and spend nothing, research
 * and upgrades run on, and another player's queues are untouched. Placements are never put in the world; the queue is
 * run by calling the item's tick directly, as the lab-level test does.
 */
@net.minecraftforge.gametest.GameTestHolder(ReignOfNether.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public class PauseUnitsGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int TICKS = 60;

    private static Resources pool(String owner) {
        Resources pool = new Resources(owner, 0, 1000, 600);
        ResourcesServerEvents.resourcesList.add(pool);
        return pool;
    }

    private static ProductionPlacement barracks(ServerLevel level, GameTestHelper helper, String owner, int dx) {
        var bp = BuildingUtils.getNewBuildingPlacement(Buildings.BARRACKS, level,
            helper.absolutePos(new BlockPos(2 + dx, 2, 2)), Rotation.NONE, owner, false);
        if (!(bp instanceof ProductionPlacement pp))
            throw new IllegalStateException("the Barracks is not a production placement: " + bp);
        pp.isBuilt = true;
        return pp;
    }

    private static void tickFront(ProductionPlacement pp, int n) {
        for (int i = 0; i < n && !pp.productionQueue.isEmpty(); i++) {
            ActiveProduction a = pp.productionQueue.get(0);
            ProductionItem item = a.item;
            item.tick(pp, a);
        }
    }

    @GameTest(template = ARENA, batch = "pauseunits")
    public static void pauseunits_paused_owner_unit_makes_no_progress_and_spends_nothing_then_resumes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_pauseunits_a_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Resources pool = pool(owner);
        try {
            ProductionPlacement pp = barracks(level, helper, owner, 0);
            if (!pp.startProductionItem(ProductionItems.VINDICATOR)) {
                helper.fail("the Barracks refused a Vindicator");
                return;
            }
            ActiveProduction a = pp.productionQueue.get(0);
            float t0 = a.ticksLeft, m0 = pool.getMetal(), e0 = pool.getEnergy();
            UnitProductionPause.set(owner, true);
            tickFront(pp, TICKS);
            if (a.ticksLeft != t0)
                helper.fail("a paused unit advanced " + (t0 - a.ticksLeft) + " ticks");
            else if (pool.getMetal() != m0 || pool.getEnergy() != e0 || a.metalSpent != 0 || a.energySpent != 0)
                helper.fail("a paused unit spent resources: metal " + (m0 - pool.getMetal()) + ", energy " + (e0 - pool.getEnergy()));
            UnitProductionPause.set(owner, false);
            tickFront(pp, TICKS);
            if (a.ticksLeft >= t0)
                helper.fail("the unit did not resume after unpausing: " + a.ticksLeft + " of " + t0);
            else if (pool.getMetal() >= m0)
                helper.fail("the resumed unit spent no metal");
            else
                helper.succeed();
        } finally {
            UnitProductionPause.clearOwner(owner);
            ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    @GameTest(template = ARENA, batch = "pauseunits")
    public static void pauseunits_research_at_a_lab_keeps_progressing_while_paused(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_pauseunits_r_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Resources pool = pool(owner);
        try {
            ProductionPlacement pp = barracks(level, helper, owner, 0);
            UnitProductionPause.set(owner, true);
            if (!pp.startProductionItem(ProductionItems.UPGRADE_LAB_L2)) {
                helper.fail("the Level 2 upgrade was refused");
                return;
            }
            ActiveProduction a = pp.productionQueue.get(0);
            float t0 = a.ticksLeft, m0 = pool.getMetal();
            tickFront(pp, TICKS);
            if (a.ticksLeft >= t0)
                helper.fail("a lab upgrade stood still while units were paused");
            else if (pool.getMetal() >= m0)
                helper.fail("a lab upgrade spent no metal while units were paused");
            else
                helper.succeed();
        } finally {
            UnitProductionPause.clearOwner(owner);
            ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    // A's review of #228 (reviews/pr-228.md, finding 1): the queue only ticked its head, so a paused unit in front held
    // up the research behind it. Runs the building's own queue step, not the item's tick.
    @GameTest(template = ARENA, batch = "pauseunits")
    public static void pauseunits_research_behind_a_paused_unit_in_the_same_queue_keeps_progressing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_pauseunits_q_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Resources pool = pool(owner);
        try {
            ProductionPlacement pp = barracks(level, helper, owner, 0);
            if (!pp.startProductionItem(ProductionItems.VINDICATOR) || !pp.startProductionItem(ProductionItems.UPGRADE_LAB_L2)) {
                helper.fail("the Barracks refused a Vindicator then a Level 2 upgrade: queue " + pp.productionQueue.size());
                return;
            }
            ActiveProduction unit = pp.productionQueue.get(0), upgrade = pp.productionQueue.get(1);
            float tU = unit.ticksLeft, tR = upgrade.ticksLeft;
            UnitProductionPause.set(owner, true);
            for (int i = 0; i < TICKS; i++)
                pp.tickProductionQueue(level);
            if (unit.ticksLeft != tU)
                helper.fail("the paused unit at the head advanced " + (tU - unit.ticksLeft) + " ticks");
            else if (upgrade.ticksLeft >= tR)
                helper.fail("the upgrade behind a paused unit stood still: " + upgrade.ticksLeft + " of " + tR);
            else if (pp.productionQueue.get(0) != unit)
                helper.fail("the paused unit lost its place at the head of the queue");
            else {
                UnitProductionPause.set(owner, false);
                pp.tickProductionQueue(level);
                if (unit.ticksLeft >= tU)
                    helper.fail("the unit at the head did not resume after unpausing");
                else
                    helper.succeed();
            }
        } finally {
            UnitProductionPause.clearOwner(owner);
            ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    @GameTest(template = ARENA, batch = "pauseunits")
    public static void pauseunits_another_players_production_is_unaffected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long salt = helper.absolutePos(BlockPos.ZERO).asLong();
        String paused = "gametest_pauseunits_p_" + salt, other = "gametest_pauseunits_o_" + salt;
        Resources poolP = pool(paused), poolO = pool(other);
        try {
            ProductionPlacement ppP = barracks(level, helper, paused, 0), ppO = barracks(level, helper, other, 6);
            if (!ppP.startProductionItem(ProductionItems.VINDICATOR) || !ppO.startProductionItem(ProductionItems.VINDICATOR)) {
                helper.fail("a Barracks refused a Vindicator");
                return;
            }
            ActiveProduction aP = ppP.productionQueue.get(0), aO = ppO.productionQueue.get(0);
            float tP = aP.ticksLeft, tO = aO.ticksLeft, mO = poolO.getMetal();
            UnitProductionPause.set(paused, true);
            if (!UnitProductionPause.isPaused(paused) || UnitProductionPause.isPaused(other))
                helper.fail("the flag is not per player");
            tickFront(ppP, TICKS);
            tickFront(ppO, TICKS);
            if (aP.ticksLeft != tP)
                helper.fail("the paused player's unit advanced");
            else if (aO.ticksLeft >= tO || poolO.getMetal() >= mO)
                helper.fail("another player's unit was held up: " + aO.ticksLeft + " of " + tO + ", metal " + poolO.getMetal() + " of " + mO);
            else
                helper.succeed();
        } finally {
            UnitProductionPause.clearOwner(paused);
            ResourcesServerEvents.resourcesList.remove(poolP);
            ResourcesServerEvents.resourcesList.remove(poolO);
        }
    }
}
