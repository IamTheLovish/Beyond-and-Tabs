package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.T3Cap;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Slice T3b-1 of the second Tier 3 (second-t3-design.md): the FRAME. Each faction's T3 lab gets a second T3 slot (slot
 * 5 / T) and one alive of EACH T3 type per player is allowed (T3Cap), queued ones counted. The second type is a
 * placeholder body until its real unit ships, so these tests prove the slot, the cap and the price, not the unit.
 * Own class, one test per acceptance criterion; everything inside the test's own arena.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SecondT3FrameGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** Each faction's T3 lab, in T3Cap.pairs() order. */
    static List<ProductionBuilding> labs() {
        return List.of(Buildings.CASTLE, Buildings.STRONGHOLD, Buildings.FORTRESS, Buildings.HEART_OF_THE_WILD,
            Buildings.ABYSSAL_SHIPYARD, Buildings.HEAVENS_GATE, Buildings.GREAT_FORGE);
    }

    static String owner(GameTestHelper helper, String tag, int i) {
        return "gametest_t3b_" + tag + i + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static ProductionPlacement placement(GameTestHelper helper, ProductionBuilding lab, String owner) {
        var bp = BuildingUtils.getNewBuildingPlacement(lab, helper.getLevel(),
            helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
        return bp instanceof ProductionPlacement pp ? pp : null;
    }

    static void grantTiers(String owner) {
        ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_2);
        ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_3);
    }

    @SuppressWarnings("unchecked")
    static Mob spawnBody(GameTestHelper helper, T3Cap.Pair pair, String owner, boolean second, double x) {
        Mob m = BigUnitGameTests.spawn(helper, (EntityType<? extends Mob>) pair.firstType(), owner, x, 8.5);
        if (m != null && second)
            m.getPersistentData().putBoolean(T3Cap.TAG_SECOND, true);
        return m;
    }

    /** AC-1: every faction's T3 lab offers the second T3 on slot 5 (T), beside the first on slot 4 (R). */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void second_t3_slot_exists_for_all_seven_factions(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var pairs = T3Cap.pairs();
        var labs = labs();
        if (pairs.size() != 7 || labs.size() != 7)
            problems.add("expected 7 factions, found " + pairs.size() + " pairs / " + labs.size() + " labs");
        for (int i = 0; i < Math.min(pairs.size(), labs.size()); i++) {
            var pair = pairs.get(i);
            var lab = labs.get(i);
            if (lab.productions.forHotkey(Keybindings.abilitySlot4) != pair.first())
                problems.add(pair.faction() + ": slot 4 (R) is not the first T3");
            if (lab.productions.forHotkey(Keybindings.abilitySlot5) != pair.second())
                problems.add(pair.faction() + ": slot 5 (T) is not the second T3");
            if (!pair.second().getItemName().contains("T3b (placeholder)") || pair.second().getItemName().equals(pair.first().getItemName()))
                problems.add(pair.faction() + ": the second T3 has no distinct placeholder name: " + pair.second().getItemName());
            if (com.solegendary.reignofnether.api.ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(pair.second()) == null)
                problems.add(pair.faction() + ": the second T3 is not registered");
            long dupes = lab.productions.hotkeys().stream().filter(k -> k == Keybindings.abilitySlot5).count();
            if (dupes != 1)
                problems.add(pair.faction() + ": slot 5 is bound " + dupes + " times in the lab");
        }
        T3RoleGameTests.finish(helper, problems);
    }

    /** AC-2: a second of the same type is refused while one lives or is queued (the message is the T3 limit). */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void second_t3_cap_refuses_a_second_of_the_same_type(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var pairs = T3Cap.pairs();
        var labs = labs();
        List<Mob> bodies = new ArrayList<>();
        List<String> owners = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            var pair = pairs.get(i);
            // alive: the first type, then the second type, each on its own owner
            for (int pass = 0; pass < 3; pass++) {
                boolean second = pass == 1;
                ProductionItem item = second ? pair.second() : pair.first();
                String owner = owner(helper, "cap" + pass + "_", i);
                owners.add(owner);
                grantTiers(owner);
                ProductionPlacement pp = placement(helper, labs.get(i), owner);
                if (pp == null) {
                    problems.add(pair.faction() + ": no placement");
                    continue;
                }
                if (T3Cap.productionError(pp, item) != null)
                    problems.add(pair.faction() + " pass " + pass + ": refused with nothing alive or queued");
                if (pass < 2) {
                    Mob body = spawnBody(helper, pair, owner, second, 3.5 + i);
                    if (body == null)
                        problems.add(pair.faction() + ": could not spawn the body");
                    else
                        bodies.add(body);
                } else {
                    // queued: the first type is already in this lab's queue
                    pp.productionQueue.add(new ActiveProduction(item, false, owner));
                    if (T3Cap.productionError(pp, item) == null)
                        problems.add(pair.faction() + ": a queued T3 did not count toward the cap");
                    int before = pp.productionQueue.size();
                    pp.startProductionItem(item);
                    if (pp.productionQueue.size() != before)
                        problems.add(pair.faction() + ": the server queued a second T3 of the same type while one was queued");
                }
            }
        }
        // the live bodies must be in the unit list before the cap sees them
        helper.runAfterDelay(3, () -> {
            for (int i = 0; i < pairs.size(); i++) {
                var pair = pairs.get(i);
                for (int pass = 0; pass < 2; pass++) {
                    boolean second = pass == 1;
                    ProductionItem item = second ? pair.second() : pair.first();
                    String owner = owner(helper, "cap" + pass + "_", i);
                    ProductionPlacement pp = placement(helper, labs.get(i), owner);
                    if (pp == null)
                        continue;
                    if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, item)))
                        problems.add(pair.faction() + (second ? " second" : " first") + ": a live T3 did not block a second of its type");
                    pp.startProductionItem(item);
                    if (!pp.productionQueue.isEmpty())
                        problems.add(pair.faction() + (second ? " second" : " first") + ": the server queued a T3 while one was alive");
                }
            }
            cleanup(bodies, owners);
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /** AC-3: the first and second T3 types are capped independently of each other. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void first_and_second_t3_types_are_capped_independently(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var pairs = T3Cap.pairs();
        var labs = labs();
        List<Mob> bodies = new ArrayList<>();
        List<String> owners = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            var pair = pairs.get(i);
            // owner A: a live FIRST body; owner B: a live SECOND body (same entity type, told apart by the tag)
            for (int pass = 0; pass < 2; pass++) {
                String owner = owner(helper, "ind" + pass + "_", i);
                owners.add(owner);
                grantTiers(owner);
                Mob body = spawnBody(helper, pair, owner, pass == 1, 3.5 + i);
                if (body == null)
                    problems.add(pair.faction() + ": could not spawn the body");
                else
                    bodies.add(body);
            }
        }
        helper.runAfterDelay(3, () -> {
            for (int i = 0; i < pairs.size(); i++) {
                var pair = pairs.get(i);
                ProductionPlacement a = placement(helper, labs.get(i), owner(helper, "ind0_", i));
                ProductionPlacement b = placement(helper, labs.get(i), owner(helper, "ind1_", i));
                if (a == null || b == null) {
                    problems.add(pair.faction() + ": no placement");
                    continue;
                }
                if (T3Cap.productionError(a, pair.first()) == null)
                    problems.add(pair.faction() + ": a live first T3 did not block another first");
                if (T3Cap.productionError(a, pair.second()) != null)
                    problems.add(pair.faction() + ": a live FIRST T3 blocked the SECOND type");
                if (T3Cap.productionError(b, pair.second()) == null)
                    problems.add(pair.faction() + ": a live second T3 did not block another second");
                if (T3Cap.productionError(b, pair.first()) != null)
                    problems.add(pair.faction() + ": a live SECOND T3 blocked the FIRST type");
                // and queueing one type leaves the other free
                b.productionQueue.add(new ActiveProduction(pair.first(), false, b.ownerName));
                if (T3Cap.productionError(b, pair.first()) == null)
                    problems.add(pair.faction() + ": a queued first T3 did not block another first");
            }
            cleanup(bodies, owners);
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /** AC-4: the second T3's cost is its own config entry and equals the first T3's (1840 metal / 1740 energy, 20 pop). */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void second_t3_cost_equals_config_and_the_first(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var configured = List.of(com.solegendary.reignofnether.resources.ResourceCosts.SUN_T3B,
            com.solegendary.reignofnether.resources.ResourceCosts.GRAVE_T3B,
            com.solegendary.reignofnether.resources.ResourceCosts.HORDE_T3B,
            com.solegendary.reignofnether.resources.ResourceCosts.VERDANT_T3B,
            com.solegendary.reignofnether.resources.ResourceCosts.TIDE_T3B,
            com.solegendary.reignofnether.resources.ResourceCosts.STORM_T3B,
            com.solegendary.reignofnether.resources.ResourceCosts.BRASS_T3B);
        var pairs = T3Cap.pairs();
        for (int i = 0; i < pairs.size(); i++) {
            var pair = pairs.get(i);
            ResourceCost second = pair.second().getCost(false, "nobody");
            ResourceCost first = pair.first().getCost(false, "nobody");
            if (second != configured.get(i))
                problems.add(pair.faction() + ": the second T3 does not use its own config cost entry");
            if (second == first)
                problems.add(pair.faction() + ": the second T3 shares the first's cost object (no own config entry)");
            if (second.metal() != first.metal() || second.energy() != first.energy()
                || second.population != first.population || second.ticks != first.ticks)
                problems.add(pair.faction() + ": cost " + second.metal() + "/" + second.energy() + "/" + second.population + "/"
                    + second.ticks + " differs from the first T3's " + first.metal() + "/" + first.energy() + "/"
                    + first.population + "/" + first.ticks);
            if (second.metal() <= 0 || second.population != 20)
                problems.add(pair.faction() + ": the second T3 is not priced like a T3 (" + second.metal() + "m, " + second.population + " pop)");
        }
        T3RoleGameTests.finish(helper, problems);
    }

    static void cleanup(List<Mob> bodies, List<String> owners) {
        for (Mob m : bodies)
            m.discard();
        for (String o : owners)
            ResearchServerEvents.removeAllResearchFor(o);
    }
}
