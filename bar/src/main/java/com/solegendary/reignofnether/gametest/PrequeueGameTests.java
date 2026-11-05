package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-queueing (Joisco, 2026-10-23: "you should be able to pre queue units in workshops while the building is being
 * built"): every production building takes a queue while it is still going up; the queue waits whole (no progress,
 * nothing paid) until isBuilt, and is refunded through the normal cancel path if the player cancels an item or the
 * unfinished building. Run alone with BAT_GAMETEST_ONLY=test:prequeue
 * <p>
 * Own holder. Placements are made with getNewBuildingPlacement and only put in the world's building list inside one
 * synchronous call (and taken out again).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PrequeueGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    private static ProductionPlacement unbuiltBarracks(GameTestHelper helper, String owner) {
        var bp = BuildingUtils.getNewBuildingPlacement(Buildings.BARRACKS, helper.getLevel(),
            helper.absolutePos(new BlockPos(3, 2, 3)), Rotation.NONE, owner, false);
        if (!(bp instanceof ProductionPlacement pp))
            return null;
        pp.isBuilt = false;
        return pp;
    }

    /** The barracks' first item that takes time to make (a zero-tick item would hide a progress bug). */
    private static ProductionItem timedItem(String owner) {
        for (ProductionItem item : ((ProductionBuilding) Buildings.BARRACKS).productions.get())
            if (item.getCost(false, owner).ticks > 0)
                return item;
        return null;
    }

    @GameTest(template = ARENA, timeoutTicks = 40, batch = "prequeue")
    public static void prequeue_item_waits_on_an_unbuilt_production_building_then_advances(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = "gametest_prequeue_a_" + Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        Resources pool = new Resources(owner, 0, 5000, 5000);
        ResourcesServerEvents.resourcesList.add(pool);
        try {
            var pp = unbuiltBarracks(helper, owner);
            ProductionItem item = timedItem(owner);
            if (pp == null || item == null) {
                helper.fail("no barracks placement or no timed item (" + pp + ", " + item + ")");
                return;
            }
            if (!pp.allowProdWhileBuilding)
                problems.add("allowProdWhileBuilding is false on a barracks");
            if (!pp.startProductionItem(item) || pp.productionQueue.size() != 1) {
                problems.add("an unbuilt barracks refused " + item.getItemName());
            } else {
                ActiveProduction ap = pp.productionQueue.get(0);
                float start = ap.ticksLeft;
                float metalBefore = pool.getMetal();
                for (int i = 0; i < 20; i++)
                    item.tick(pp, ap);
                if (ap.ticksLeft != start)
                    problems.add("the item advanced " + (start - ap.ticksLeft) + " ticks while unbuilt");
                if (ap.completed || ap.metalSpent > 0 || pool.getMetal() != metalBefore)
                    problems.add("the item completed or was paid for while unbuilt");
                pp.isBuilt = true;
                for (int i = 0; i < 20; i++)
                    item.tick(pp, ap);
                if (!(ap.ticksLeft < start))
                    problems.add("the item did not advance once the building was built (ticksLeft " + ap.ticksLeft + ")");
            }
        } finally {
            ResourcesServerEvents.resourcesList.remove(pool);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = ARENA, timeoutTicks = 40, batch = "prequeue")
    public static void prequeue_cancelling_the_unbuilt_building_refunds_the_queue(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = "gametest_prequeue_b_" + Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        Resources pool = new Resources(owner, 0, 100, 100);
        ResourcesServerEvents.resourcesList.add(pool);
        BuildingPlacement added = null;
        try {
            var pp = unbuiltBarracks(helper, owner);
            ProductionItem item = timedItem(owner);
            if (pp == null || item == null) {
                helper.fail("no barracks placement or no timed item (" + pp + ", " + item + ")");
                return;
            }
            BuildingServerEvents.getBuildings().add(pp);
            added = pp;
            if (!pp.startProductionItem(item) || pp.productionQueue.size() != 1) {
                problems.add("an unbuilt barracks refused " + item.getItemName());
            } else {
                // flow economy pays as an item is produced, so an unbuilt building has paid nothing; pretend a payment
                // happened (a queue that was started, then the building put back to unbuilt is not possible, so this
                // checks the refund path itself) and require it comes back
                pp.productionQueue.get(0).metalSpent = 40;
                pp.productionQueue.get(0).energySpent = 25;
                BuildingServerEvents.cancelBuilding(pp, owner);
                added = null;
                if (!pp.productionQueue.isEmpty())
                    problems.add("the queue was not emptied when the unbuilt building was cancelled");
                if (Math.round(pool.getMetal()) != 140 || Math.round(pool.getEnergy()) != 125)
                    problems.add("refund after cancelling the building: metal " + pool.getMetal() + " (want 140), energy "
                        + pool.getEnergy() + " (want 125)");
            }
        } finally {
            if (added != null)
                BuildingServerEvents.getBuildings().remove(added);
            ResourcesServerEvents.resourcesList.remove(pool);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = ARENA, timeoutTicks = 40, batch = "prequeue")
    public static void prequeue_cancelling_a_prequeued_item_refunds_it(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = "gametest_prequeue_c_" + Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        Resources pool = new Resources(owner, 0, 100, 100);
        ResourcesServerEvents.resourcesList.add(pool);
        try {
            var pp = unbuiltBarracks(helper, owner);
            ProductionItem item = timedItem(owner);
            if (pp == null || item == null) {
                helper.fail("no barracks placement or no timed item (" + pp + ", " + item + ")");
                return;
            }
            if (!pp.startProductionItem(item) || !pp.startProductionItem(item) || pp.productionQueue.size() != 2) {
                problems.add("an unbuilt barracks did not take two queued items (queue " + pp.productionQueue.size() + ")");
            } else {
                pp.productionQueue.get(1).metalSpent = 30;
                pp.productionQueue.get(1).energySpent = 10;
                // the back item (cancel-back, a not-yet-started item) then the front one
                if (!pp.cancelProductionItem(item, false) || pp.productionQueue.size() != 1)
                    problems.add("cancelling the back pre-queued item failed");
                // cancel-back removes the FIRST unstarted match, i.e. index 0, whose spent is 0: nothing refunded yet
                if (Math.round(pool.getMetal()) != 100)
                    problems.add("metal changed by cancelling an item that had paid nothing: " + pool.getMetal());
                pp.productionQueue.get(0).metalSpent = 30;
                pp.productionQueue.get(0).energySpent = 10;
                if (!pp.cancelProductionItem(item, true) || !pp.productionQueue.isEmpty())
                    problems.add("cancelling the front pre-queued item failed");
                if (Math.round(pool.getMetal()) != 130 || Math.round(pool.getEnergy()) != 110)
                    problems.add("refund after cancel: metal " + pool.getMetal() + " (want 130), energy "
                        + pool.getEnergy() + " (want 110)");
            }
        } finally {
            ResourcesServerEvents.resourcesList.remove(pool);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
