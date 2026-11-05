package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.gameender.WardSpirePlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Lane C gameplay fixes (tester reports): Tier 2 research must be bought once per player, and commanders heal to
 * their raised maximum like any other unit. Own holder, own owners, nothing global.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class LaneCGameplayGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /**
     * "Tier 2 research ran twice": the server's one-off rule (ProdDupeRule.DISALLOW) only looked at the queues, so once
     * the first Tier 2 finished a second could be queued and paid for (the client merely hides the button). Through the
     * real server path (ProductionPlacement.startProductionItem, which the packet, the batch card and the bot all call):
     * a second capitol cannot queue it while the first holds it, and neither can after it completes. One queue entry,
     * one research record.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void tier_2_research_is_bought_once_per_player(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_lanec_t2_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        Resources pool = new Resources(owner, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        ResourceLocation key = ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(ProductionItems.RESEARCH_TIER_2);
        List<String> problems = new ArrayList<>();
        List<BuildingPlacement> added = new ArrayList<>();
        try {
            var a = BuildingUtils.getNewBuildingPlacement(Buildings.TOWN_CENTRE, level, base, Rotation.NONE, owner, false);
            var b = BuildingUtils.getNewBuildingPlacement(Buildings.TOWN_CENTRE, level, base.offset(30, 0, 0), Rotation.NONE, owner, false);
            if (!(a instanceof ProductionPlacement capA) || !(b instanceof ProductionPlacement capB) || key == null) {
                helper.fail("could not make two capitols");
                return;
            }
            for (ProductionPlacement pp : List.of(capA, capB)) {
                pp.isBuilt = true;
                BuildingServerEvents.getBuildings().add(pp);
                added.add(pp);
            }
            if (!capA.startProductionItem(ProductionItems.RESEARCH_TIER_2))
                problems.add("the first Tier 2 was refused");
            if (capB.startProductionItem(ProductionItems.RESEARCH_TIER_2))
                problems.add("a second capitol queued Tier 2 while the first holds it");
            if (capA.startProductionItem(ProductionItems.RESEARCH_TIER_2))
                problems.add("the same capitol queued Tier 2 twice");
            if (capA.productionQueue.size() + capB.productionQueue.size() != 1)
                problems.add("expected exactly one queued Tier 2, found " + (capA.productionQueue.size() + capB.productionQueue.size()));

            // the first one finishes through the real completion path
            capA.productionQueue.clear();
            ProductionItems.RESEARCH_TIER_2.onComplete.accept(level, capA);
            if (ResearchServerEvents.recordedCount(owner, key) != 1)
                problems.add("after completion the research is recorded " + ResearchServerEvents.recordedCount(owner, key) + " times, not once");

            // the bug: now nothing was in any queue, so the server said yes again
            if (capA.startProductionItem(ProductionItems.RESEARCH_TIER_2) || capB.startProductionItem(ProductionItems.RESEARCH_TIER_2))
                problems.add("Tier 2 was queued again after it was researched");
            if (!capA.productionQueue.isEmpty() || !capB.productionQueue.isEmpty())
                problems.add("a finished Tier 2 left a queue entry (a second charge)");

            // other researches of the same kind are not blocked by it: Tier 3 still queues
            if (!capA.startProductionItem(ProductionItems.RESEARCH_TIER_3))
                problems.add("Tier 3 was refused although Tier 2 is done and Tier 3 is not");
            else
                capA.cancelProductionItem(ProductionItems.RESEARCH_TIER_3, true);
        } catch (Exception ex) {
            problems.add("threw " + ex);
        } finally {
            BuildingServerEvents.getBuildings().removeAll(added);
            ResearchServerEvents.removeResearch(owner, key);
            ResourcesServerEvents.resourcesList.remove(pool);
        }
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        helper.succeed();
    }

    /**
     * "Make sure commanders heal": a commander is a normal unit with +80 max health; nothing skips it. Healing goes
     * through the same LivingEntity.heal every source uses (fountain, medic, mending rite, food), so it must climb past
     * the base maximum and stop exactly at the raised one.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void commander_heals_up_to_its_raised_maximum(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_lanec_cmd_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        Mob cmdr = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (cmdr == null) {
            helper.fail("no villager unit");
            return;
        }
        cmdr.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        cmdr.setNoGravity(true);
        ((Unit) cmdr).setOwnerName(owner);
        ((Unit) cmdr).setHoldPosition(true);
        level.addFreshEntity(cmdr);
        try {
            float baseMax = cmdr.getMaxHealth();
            CommanderServerEvents.makeCommander(cmdr);
            float max = cmdr.getMaxHealth();
            List<String> problems = new ArrayList<>();
            if (max < baseMax + 79f)
                problems.add("the commander's max health is " + max + ", base " + baseMax);
            if (cmdr.getHealth() != max)
                problems.add("a new commander is not at full health");
            cmdr.setHealth(10f);
            cmdr.heal(5f);
            if (Math.abs(cmdr.getHealth() - 15f) > 0.01f)
                problems.add("heal(5) from 10 gave " + cmdr.getHealth());
            cmdr.heal(10_000f);
            if (Math.abs(cmdr.getHealth() - max) > 0.01f)
                problems.add("a big heal ended at " + cmdr.getHealth() + ", not the raised maximum " + max);
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
        } finally {
            cmdr.discard();
        }
        helper.succeed();
    }

    /** The Ward Spire umbrella is 64 blocks: a strike aimed 60 blocks from a charged ward is stopped, 70 is not. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void ward_spire_range_is_64(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String victim = "gametest_lanec_ward_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        List<String> problems = new ArrayList<>();
        if (WardSpirePlacement.RANGE != 64)
            problems.add("WardSpirePlacement.RANGE is " + WardSpirePlacement.RANGE);
        var w = BuildingUtils.getNewBuildingPlacement(Buildings.WARD_SPIRE_VILLAGERS, level, at, Rotation.NONE, victim, false);
        if (!(w instanceof WardSpirePlacement ward)) {
            helper.fail("could not make a ward");
            return;
        }
        ward.isBuilt = true;
        ward.charges = WardSpirePlacement.MAX_CHARGES;
        var spire = BuildingUtils.getNewBuildingPlacement(Buildings.DOOM_FORGE, level, at.offset(0, 0, 100), Rotation.NONE, "gametest_lanec_foe", false);
        if (!(spire instanceof com.solegendary.reignofnether.building.gameender.GameEnderPlacement ge)) {
            helper.fail("could not make a game ender");
            return;
        }
        ge.pool = new ArrayList<>(List.of(ward));
        if (ge.interceptor(ward.centrePos.offset(60, 0, 0), victim) == null)
            problems.add("a strike 60 blocks from the ward was not intercepted");
        if (ge.interceptor(ward.centrePos.offset(70, 0, 0), victim) != null)
            problems.add("a strike 70 blocks from the ward was intercepted");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        helper.succeed();
    }
}
