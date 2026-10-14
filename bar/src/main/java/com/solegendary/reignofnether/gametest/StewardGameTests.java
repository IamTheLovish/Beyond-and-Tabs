package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.StewardServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;

/**
 * The Steward (research at every capitol, StewardServerEvents): the capitol trains a worker on its own while its owner
 * is under the tier's quota - counting workers in training - and only while its own queue is empty; the HUD toggle
 * pauses it per capitol. The placements are never put in the world (the produce gate is enough), the villagers that
 * fill the quota stand on a glass pad at a height no other test uses and are discarded in a finally.
 */
@net.minecraftforge.gametest.GameTestHolder(ReignOfNether.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public class StewardGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** Every capitol keeps its worker on abilitySlot1 - the Steward's lookup - and it is the worker the bots train. */
    @GameTest(template = ARENA)
    public static void every_capitol_offers_the_steward_and_its_worker_on_slot_1(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Object[][] capitols = {
            { Buildings.TOWN_CENTRE, Factions.VILLAGERS }, { Buildings.MAUSOLEUM, Factions.MONSTERS },
            { Buildings.CENTRAL_PORTAL, Factions.PIGLINS }, { Buildings.HEARTWOOD_HALL, Factions.VERDANT_COURT },
            { Buildings.WRECK_HARBOUR, Factions.TIDEWROUGHT }, { Buildings.JADE_PAGODA, Factions.STORMCALLERS },
            { Buildings.FOUNDRY_HALL, Factions.BRASSWORKS } };
        List<String> problems = new ArrayList<>();
        for (Object[] row : capitols) {
            Building b = (Building) row[0];
            Faction f = (Faction) row[1];
            if (!b.isCapitol)
                problems.add(b.name + " is not a capitol");
            if (!b.productions.get().contains(ProductionItems.RESEARCH_STEWARD))
                problems.add(b.name + " does not offer the Steward");
            var bp = BuildingUtils.getNewBuildingPlacement(b, level, helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, "gametest_steward_slots", false);
            ProductionItem worker = bp instanceof ProductionPlacement pp ? StewardServerEvents.workerItem(pp) : null;
            var kit = BotPlayer.kitFor(f);
            if (worker == null)
                problems.add(b.name + " has nothing on slot 1");
            else if (kit != null && kit.worker() != worker)
                problems.add(b.name + " slot 1 is " + worker.getItemName() + ", the bot trains " + kit.worker().getItemName());
        }
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void steward_keeps_the_owner_at_the_worker_quota(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_steward_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Resources pool = null;
        for (var r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                pool = r;
        if (pool == null) {
            pool = new Resources(owner, 0, 0, 0);
            ResourcesServerEvents.resourcesList.add(pool);
        }
        var bp = BuildingUtils.getNewBuildingPlacement(Buildings.TOWN_CENTRE, level, helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
        if (!(bp instanceof ProductionPlacement capitol)) {
            helper.fail("the Town Centre is not a production placement: " + bp);
            return;
        }
        capitol.isBuilt = true;
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        int padY = 244;   // a height no other test's pad or band uses
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                level.setBlock(new BlockPos(corner.getX() + dx, padY, corner.getZ() + dz), Blocks.GLASS.defaultBlockState(), 3);
        List<Entity> made = new ArrayList<>();
        final Resources res = pool;
        Runnable cleanup = () -> {
            made.forEach(Entity::discard);
            ResearchServerEvents.removeAllResearchFor(owner);
            ResourcesServerEvents.resourcesList.remove(res);
        };
        ResearchServerEvents.removeAllResearchFor(owner);
        try {
            List<ProductionPlacement> mine = List.of(capitol);
            if (StewardServerEvents.eligible(capitol))
                helper.fail("a capitol is tended without the Steward researched");
            ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_STEWARD);
            if (!StewardServerEvents.eligible(capitol))
                helper.fail("the capitol is not eligible after the research");
            if (StewardServerEvents.quota(owner) != StewardServerEvents.QUOTA_T1)
                helper.fail("Tier 1 quota " + StewardServerEvents.quota(owner));
            if (StewardServerEvents.tend(owner, mine) != 1 || capitol.productionQueue.size() != 1
                    || capitol.productionQueue.get(0).item != ProductionItems.VILLAGER)
                helper.fail("the Steward did not queue one Villager at an empty capitol: queue " + capitol.productionQueue.size());
            if (StewardServerEvents.tend(owner, mine) != 0 || capitol.productionQueue.size() != 1)
                helper.fail("the Steward queued into a capitol whose queue was not empty");
            capitol.productionQueue.clear();
            // the queued worker counts toward the quota; so does one already in training anywhere the owner builds
            if (StewardServerEvents.workersHeld(owner) != 0)
                helper.fail("workers held with nothing alive or queued: " + StewardServerEvents.workersHeld(owner));
            capitol.setStewardPaused(true);
            if (StewardServerEvents.eligible(capitol))
                helper.fail("a paused capitol is still tended");
            capitol.setStewardPaused(false);
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
        // fill the quota with live villagers: the Steward must then stop, and start again when Tier 2 raises it
        for (int i = 0; i < StewardServerEvents.QUOTA_T1; i++) {
            var v = EntityRegistrar.VILLAGER_UNIT.get().create(level);
            if (v == null)
                continue;
            v.moveTo(corner.getX() + 1.5 + (i % 8) * 1.5, padY + 1, corner.getZ() + 2.5 + (i / 8) * 2, 0, 0);
            v.setOwnerName(owner);
            level.addFreshEntity(v);
            made.add(v);
        }
        helper.runAfterDelay(5, () -> {
            try {
                List<ProductionPlacement> mine = List.of(capitol);
                int held = StewardServerEvents.workersHeld(owner);
                if (held != StewardServerEvents.QUOTA_T1)
                    helper.fail("workers held " + held + " with " + made.size() + " villagers spawned");
                if (StewardServerEvents.tend(owner, mine) != 0 || !capitol.productionQueue.isEmpty())
                    helper.fail("the Steward trained past the quota");
                ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_2);
                if (StewardServerEvents.quota(owner) != StewardServerEvents.QUOTA_T2)
                    helper.fail("Tier 2 quota " + StewardServerEvents.quota(owner));
                if (StewardServerEvents.tend(owner, mine) != 1 || capitol.productionQueue.size() != 1)
                    helper.fail("the Steward did not resume when Tier 2 raised the quota");
                // the worker in training counts once the capitol is among the owner's buildings (in the world it is)
                var all = com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings();
                all.add(capitol);
                int heldNow = StewardServerEvents.workersHeld(owner);
                all.remove(capitol);
                if (heldNow != StewardServerEvents.QUOTA_T1 + 1)
                    helper.fail("the worker in training is not counted: " + heldNow);
                for (var u : made)
                    if (u instanceof Unit uu && !owner.equals(uu.getOwnerName()))
                        helper.fail("a villager lost its owner");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }
}
