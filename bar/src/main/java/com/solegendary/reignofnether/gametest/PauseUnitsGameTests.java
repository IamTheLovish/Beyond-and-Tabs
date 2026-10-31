package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.HeroProductionItem;
import com.solegendary.reignofnether.hero.HeroKit;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.hero.HeroProd;
import com.solegendary.reignofnether.hero.HeroTypes;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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

    // Codex adversarial review of the fix above (20261030, finding 1): a hero is not a paused unit, so it can now finish
    // behind one. The hero cap must leave out the hero being finished there too, not only at the head, or the spawn is
    // refused and its cost is lost. Registered placement, as HeroGameTests.hero_spawns_when_its_training_finishes.
    @GameTest(template = ARENA, batch = "pauseunits")
    public static void pauseunits_a_hero_finishing_behind_a_paused_unit_spawns_once(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HeroKit kit = HeroKits.DAWN_PALADIN;
        HeroProd prod = HeroProd.forKit(kit);
        var type = HeroTypes.typeOf(kit);
        String owner = "gametest_pauseunits_h_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Resources pool = pool(owner);
        ProductionPlacement pp = barracks(level, helper, owner, 0);
        BuildingServerEvents.getBuildings().add(pp);
        int heroes = 0;
        String problem = null;
        try {
            if (prod == null || type == null)
                problem = "no hero production item for " + kit.id;
            else if (!pp.startProductionItem(ProductionItems.VINDICATOR) || !pp.startProductionItem(prod))
                problem = "the Barracks refused a Vindicator then the hero: " + prod.getProduceErrorMsg(pp);
            else {
                ActiveProduction unit = pp.productionQueue.get(0), hero = pp.productionQueue.get(1);
                UnitProductionPause.set(owner, true);
                hero.ticksLeft = 0;   // trained: the next queue step completes it, behind the paused unit
                pp.tickProductionQueue(level);
                java.util.List<Mob> spawned = new java.util.ArrayList<>();
                for (LivingEntity le : UnitServerEvents.getAllUnits())
                    if (le instanceof Mob m && m.getType() == type && m instanceof Unit u && owner.equals(u.getOwnerName()))
                        spawned.add(m);
                heroes = spawned.size();
                spawned.forEach(Mob::discard);   // after the walk: a discard changes the unit list
                if (heroes != 1)
                    problem = "a hero finished behind a paused unit spawned " + heroes + " times (cap check: "
                        + HeroProductionItem.heroesCountedAgainstCap(pp, prod) + " counted)";
                else if (pp.productionQueue.size() != 1 || pp.productionQueue.get(0) != unit)
                    problem = "the paused unit is not alone at the head after the hero finished: queue " + pp.productionQueue.size();
            }
        } finally {
            UnitProductionPause.clearOwner(owner);
            pp.productionQueue.clear();
            BuildingServerEvents.getBuildings().remove(pp);
            ResourcesServerEvents.resourcesList.remove(pool);
        }
        if (problem != null)
            helper.fail(problem);
        else
            helper.succeed();
    }

    // Codex adversarial review (finding 2): research started behind a paused unit is not at the head, so its cancel
    // button sends a back-of-queue cancel, which only took items that had not started. It must cancel, refund what it
    // spent and leave the paused unit at the head.
    @GameTest(template = ARENA, batch = "pauseunits")
    public static void pauseunits_research_started_behind_a_paused_unit_can_be_cancelled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_pauseunits_c_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Resources pool = pool(owner);
        try {
            ProductionPlacement pp = barracks(level, helper, owner, 0);
            if (!pp.startProductionItem(ProductionItems.VINDICATOR) || !pp.startProductionItem(ProductionItems.UPGRADE_LAB_L2)) {
                helper.fail("the Barracks refused a Vindicator then a Level 2 upgrade");
                return;
            }
            ActiveProduction unit = pp.productionQueue.get(0), upgrade = pp.productionQueue.get(1);
            UnitProductionPause.set(owner, true);
            for (int i = 0; i < TICKS; i++)
                pp.tickProductionQueue(level);
            // the refund is what the item recorded as spent, in whole units (refundProduction -> EconomyServerEvents.refund
            // rounds, as for any cancel)
            float spentM = Math.round(upgrade.metalSpent), spentE = Math.round(upgrade.energySpent);
            float m0 = pool.getMetal() + spentM, e0 = pool.getEnergy() + spentE;
            if (spentM <= 0)
                helper.fail("the upgrade behind the paused unit never started, so this proves nothing");
            else if (!pp.cancelProductionItem(ProductionItems.UPGRADE_LAB_L2, false))
                helper.fail("the started upgrade behind a paused unit could not be cancelled");
            else if (pp.productionQueue.size() != 1 || pp.productionQueue.get(0) != unit)
                helper.fail("cancelling the upgrade did not leave the paused unit alone at the head: queue " + pp.productionQueue.size());
            else if (Math.abs(pool.getMetal() - m0) > 0.01f || Math.abs(pool.getEnergy() - e0) > 0.01f)
                helper.fail("the cancelled upgrade was not refunded what it spent (" + spentM + " metal, " + spentE + " energy): metal "
                    + pool.getMetal() + " of " + m0 + ", energy " + pool.getEnergy() + " of " + e0);
            else
                helper.succeed();
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
