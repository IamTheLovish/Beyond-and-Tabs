package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.T4Cap;
import com.solegendary.reignofnether.unit.units.piglins.WorldbreakerProd;
import com.solegendary.reignofnether.unit.units.piglins.WorldbreakerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Slice H5a (hero-and-tier4-design.md): the <b>Tier 4 frame</b> for the Horde. One test per acceptance criterion, all in
 * the test's own arena: one Worldbreaker alive or queued per player (AC-1), the Ascension research gates it and needs the
 * finished Fortress (AC-2), the start and arrival announcements reach every player (AC-3); plus the slots and the prices.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class T4FrameGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_t4_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static ProductionPlacement fortress(GameTestHelper helper, String owner) {
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.FORTRESS, owner);
        if (pp != null)
            pp.isBuilt = true;
        return pp;
    }

    static void grantAscension(String owner) {
        SecondT3FrameGameTests.grantTiers(owner);
        ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_ASCENSION);
    }

    /** AC-1: a second Worldbreaker is refused while one lives or is queued (same player); another player may have one. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void second_worldbreaker_is_refused_while_one_lives(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String a = owner(helper, "capA"), b = owner(helper, "capB");
        grantAscension(a);
        grantAscension(b);
        ProductionPlacement ppA = fortress(helper, a), ppB = fortress(helper, b);
        if (ppA == null || ppB == null) {
            T3RoleGameTests.finish(helper, List.of("no Fortress placement"));
            return;
        }
        if (T4Cap.productionError(ppA, ProductionItems.WORLDBREAKER) != null || ProductionItems.WORLDBREAKER.getProduceErrorMsg(ppA) != null)
            problems.add("a Worldbreaker was refused with none alive");
        Mob alive = MagmaHurlerGameTests.put(helper, EntityRegistrar.WORLDBREAKER_UNIT.get(), a, 3.5, 8.5);
        if (alive == null) {
            T3RoleGameTests.finish(helper, List.of("could not create the Worldbreaker"));
            return;
        }
        helper.runAfterDelay(3, () -> {
            if (!T4Cap.LIMIT_MSG.equals(T4Cap.productionError(ppA, ProductionItems.WORLDBREAKER)))
                problems.add("a living Worldbreaker did not block a second");
            if (!T4Cap.LIMIT_MSG.equals(ProductionItems.WORLDBREAKER.getProduceErrorMsg(ppA)))
                problems.add("the production item did not report the T4 limit");
            ppA.startProductionItem(ProductionItems.WORLDBREAKER);
            if (!ppA.productionQueue.isEmpty())
                problems.add("the server queued a Worldbreaker while one was alive");
            if (T4Cap.productionError(ppB, ProductionItems.WORLDBREAKER) != null)
                problems.add("another player's Worldbreaker blocked this player's");
            // queued counts too: the living one gone, a queued one in the lab blocks a second
            alive.discard();
            ppB.productionQueue.add(new ActiveProduction(ProductionItems.WORLDBREAKER, false, b));
            if (!T4Cap.LIMIT_MSG.equals(T4Cap.productionError(ppB, ProductionItems.WORLDBREAKER)))
                problems.add("a queued Worldbreaker did not count toward the cap");
            int before = ppB.productionQueue.size();
            ppB.startProductionItem(ProductionItems.WORLDBREAKER);
            if (ppB.productionQueue.size() != before)
                problems.add("the server queued a second Worldbreaker while one was queued");
            ppB.productionQueue.clear();
            ResearchServerEvents.removeAllResearchFor(a);
            ResearchServerEvents.removeAllResearchFor(b);
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /** AC-2: no Worldbreaker without the Ascension research; the research needs the finished Fortress. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void worldbreaker_needs_ascension_and_ascension_needs_a_finished_lab(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = owner(helper, "gate");
        SecondT3FrameGameTests.grantTiers(owner);
        ProductionPlacement pp = fortress(helper, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Fortress placement"));
            return;
        }
        if (!WorldbreakerProd.NEEDS_ASCENSION_MSG.equals(ProductionItems.WORLDBREAKER.getProduceErrorMsg(pp)))
            problems.add("a Worldbreaker was not refused without the Ascension research");
        pp.startProductionItem(ProductionItems.WORLDBREAKER);
        if (!pp.productionQueue.isEmpty())
            problems.add("the server queued a Worldbreaker without Ascension");
        // the research: refused at a building site, allowed at a finished Fortress
        pp.isBuilt = false;
        if (!com.solegendary.reignofnether.research.researchItems.ResearchAscension.LAB_UNFINISHED_MSG
                .equals(ProductionItems.RESEARCH_ASCENSION.getProduceErrorMsg(pp)))
            problems.add("Ascension was not refused at an unfinished Fortress");
        pp.isBuilt = true;
        if (ProductionItems.RESEARCH_ASCENSION.getProduceErrorMsg(pp) != null)
            problems.add("Ascension was refused at a finished Fortress");
        // completing the research item grants it, and the Worldbreaker is then allowed
        ProductionItems.RESEARCH_ASCENSION.onComplete.accept(helper.getLevel(), pp);
        if (!ResearchServerEvents.playerHasResearch(owner, ProductionItems.RESEARCH_ASCENSION))
            problems.add("completing Ascension did not grant the research");
        if (ProductionItems.WORLDBREAKER.getProduceErrorMsg(pp) != null)
            problems.add("a Worldbreaker was still refused after Ascension: " + ProductionItems.WORLDBREAKER.getProduceErrorMsg(pp));
        ResearchServerEvents.removeAllResearchFor(owner);
        T3RoleGameTests.finish(helper, problems);
    }

    /** The slots and the prices: Ascension on Y, the Worldbreaker on U, the config prices of the design. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void fortress_slots_and_t4_prices(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (Buildings.FORTRESS.productions.forHotkey(Keybindings.abilitySlot6) != ProductionItems.RESEARCH_ASCENSION)
            problems.add("slot 6 of the Fortress is not Ascension");
        if (Buildings.FORTRESS.productions.forHotkey(Keybindings.abilitySlot7) != ProductionItems.WORLDBREAKER)
            problems.add("slot 7 of the Fortress is not the Worldbreaker");
        if (Buildings.FORTRESS.productions.forHotkey(Keybindings.abilitySlot4) != ProductionItems.WAR_MAMMOTH
                || Buildings.FORTRESS.productions.forHotkey(Keybindings.abilitySlot5) != ProductionItems.HORDE_T3B)
            problems.add("the T3 slots moved");
        var wb = ProductionItems.WORLDBREAKER.getCost(false, "nobody");
        if (wb.metal() != 3500 || wb.energy() != 9000 || wb.ticks != 120 * 20)
            problems.add("Worldbreaker costs " + wb.metal() + "M/" + wb.energy() + "E/" + wb.ticks / 20 + " s, expected 3500/9000/120");
        var res = ProductionItems.RESEARCH_ASCENSION.getCost(false, "nobody");
        if (res.ticks != 90 * 20 || res.energy() != 15000)
            problems.add("Ascension costs " + res.metal() + "M/" + res.energy() + "E/" + res.ticks / 20 + " s, expected 90 s and 15000 energy");
        if (com.solegendary.reignofnether.api.ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(ProductionItems.WORLDBREAKER) == null
                || com.solegendary.reignofnether.api.ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(ProductionItems.RESEARCH_ASCENSION) == null)
            problems.add("the T4 items are not registered");
        T3RoleGameTests.finish(helper, problems);
    }

    /** AC-3: queueing a Worldbreaker and its arrival are each announced to every player online. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void start_and_arrival_announcements_reach_every_player(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = owner(helper, "ann");
        var level = helper.getLevel();
        ServerPlayer p1 = MatchLifecycleCases.fake(level, "gt_t4_ann1"), p2 = MatchLifecycleCases.fake(level, "gt_t4_ann2");
        Resources pool = new Resources(owner, 0, 1000, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        grantAscension(owner);
        ProductionPlacement pp = fortress(helper, owner);
        if (pp == null) {
            PlayerServerEvents.players.remove(p1);
            PlayerServerEvents.players.remove(p2);
            T3RoleGameTests.finish(helper, List.of("no Fortress placement"));
            return;
        }
        T4Cap.lastStartedOwner = null;
        T4Cap.lastArrivedOwner = null;
        if (!pp.startProductionItem(ProductionItems.WORLDBREAKER))
            problems.add("the Worldbreaker could not be queued: " + ProductionItems.WORLDBREAKER.getProduceErrorMsg(pp));
        int online = PlayerServerEvents.players.size();
        if (!owner.equals(T4Cap.lastStartedOwner))
            problems.add("queueing the Worldbreaker announced nothing");
        else if (T4Cap.lastStartedRecipients != online || online < 2)
            problems.add("the start line went to " + T4Cap.lastStartedRecipients + " of " + online + " players");
        pp.productionQueue.clear();
        Mob body = MagmaHurlerGameTests.put(helper, EntityRegistrar.WORLDBREAKER_UNIT.get(), owner, 3.5, 8.5);
        helper.runAfterDelay(12, () -> {
            if (!owner.equals(T4Cap.lastArrivedOwner))
                problems.add("the arrival of the Worldbreaker announced nothing");
            else if (T4Cap.lastArrivedRecipients != PlayerServerEvents.players.size() || T4Cap.lastArrivedRecipients < 2)
                problems.add("the arrival line went to " + T4Cap.lastArrivedRecipients + " of " + PlayerServerEvents.players.size() + " players");
            if (body != null)
                body.discard();
            PlayerServerEvents.players.remove(p1);
            PlayerServerEvents.players.remove(p2);
            ResourcesServerEvents.resourcesList.remove(pool);
            ResearchServerEvents.removeAllResearchFor(owner);
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /** The body of the item is the Worldbreaker (a real type, not a tagged placeholder). */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void completing_the_item_trains_a_worldbreaker(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = owner(helper, "train");
        ProductionPlacement pp = fortress(helper, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Fortress placement"));
            return;
        }
        ProductionItems.WORLDBREAKER.onComplete.accept(helper.getLevel(), pp);
        helper.runAfterDelay(3, () -> {
            var box = new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(80);
            var made = helper.getLevel().getEntitiesOfClass(WorldbreakerUnit.class, box, u -> owner.equals(u.getOwnerName()));
            if (made.size() != 1)
                problems.add("completing the item made " + made.size() + " Worldbreakers");
            made.forEach(Mob::discard);
            T3RoleGameTests.finish(helper, problems);
        });
    }
}
