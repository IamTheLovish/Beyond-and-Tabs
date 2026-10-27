package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildGroup;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.CommandCard;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The worker's build menu is four tabs (Economy, Military, Defence, Tech; BuildGroup) with positional slot keys. This
 * checks every faction's registration: each building is in exactly one tab, a tab never gives two buildings the same
 * key (nor more buildings than keys), and no slot key is also one of the worker's orders, abilities or its Return
 * Resources button. The cross-tab keys repeat on purpose; only the selected tab is on screen.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildGroupGameTests {

    // a method, not a field: the client loads this class before the factions are registered (a static list is null there)
    private static List<Faction> factions() {
        return List.of(
            Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT,
            Factions.TIDEWROUGHT, Factions.STORMCALLERS, Factions.BRASSWORKS);
    }

    private static Set<Integer> codes(Keybinding kb) {
        Set<Integer> out = new HashSet<>();
        if (kb == null)
            return out;
        out.add(kb.getKey());
        if (kb.getAliasKey() != -1)
            out.add(kb.getAliasKey());
        return out;
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40)
    public static void every_building_of_every_faction_sits_in_exactly_one_tab(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (Faction f : factions()) {
            String who = f.getName();
            Map<Building, BuildGroup> groups = f.getBuildGroups();
            if (groups.isEmpty()) {
                problems.add(who + ": no buildings registered into tabs");
                continue;
            }
            int counted = 0;
            Set<Building> seen = new HashSet<>();
            for (BuildGroup g : BuildGroup.values())
                for (Building b : f.getBuildingsIn(g)) {
                    counted++;
                    if (!seen.add(b))
                        problems.add(who + ": " + b.structureName + " is in more than one tab");
                }
            if (counted != groups.size())
                problems.add(who + ": " + groups.size() + " buildings but " + counted + " tab entries");
            // every key-bearing building of the faction's menu has a tab, and the other way round
            for (Building b : f.getBuildHotkeys().keySet())
                if (!groups.containsKey(b))
                    problems.add(who + ": " + b.structureName + " has a key but no tab");
            for (Building b : groups.keySet())
                // the Beacon is one shared building in three menus (its faction is whichever registered last)
                if (b.getFaction() != f && b != com.solegendary.reignofnether.building.Buildings.BEACON)
                    problems.add(who + ": " + b.structureName + " is registered in the menu of another faction");
            if (f.capitolBuilding == null || !groups.containsKey(com.solegendary.reignofnether.api.ReignOfNetherRegistries.BUILDING.get(f.capitolBuilding))
                || groups.get(com.solegendary.reignofnether.api.ReignOfNetherRegistries.BUILDING.get(f.capitolBuilding)) != BuildGroup.ECONOMY)
                problems.add(who + ": the capitol is not the Economy tab's first building");
            for (BuildGroup g : BuildGroup.values()) {
                List<Building> tab = f.getBuildingsIn(g);
                if (tab.size() > BuildGroup.SLOT_KEYS.size())
                    problems.add(who + ": the " + g + " tab has " + tab.size() + " buildings for " + BuildGroup.SLOT_KEYS.size() + " keys");
                for (int i = 0; i < tab.size() && i < BuildGroup.SLOT_KEYS.size(); i++)
                    if (f.getBuildHotkeys().get(tab.get(i)) != BuildGroup.SLOT_KEYS.get(i))
                        problems.add(who + ": " + tab.get(i).structureName + " is not on slot key " + i + " of " + g);
            }
        }
        // the Kingdom follows design/mockups/groups.md
        Faction k = Factions.VILLAGERS;
        int[] expected = { 11, 5, 15, 6 };
        for (BuildGroup g : BuildGroup.values())
            if (k.getBuildingsIn(g).size() != expected[g.ordinal()])
                problems.add("villagers: the " + g + " tab has " + k.getBuildingsIn(g).size() + " buildings, groups.md says " + expected[g.ordinal()]);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(25, problems.size()))));
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40)
    public static void no_tab_has_two_buildings_on_one_key(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (Faction f : factions())
            for (BuildGroup g : BuildGroup.values()) {
                Map<Integer, String> taken = new HashMap<>();
                for (Building b : f.getBuildingsIn(g)) {
                    for (int code : codes(f.getBuildHotkeys().get(b))) {
                        String prev = taken.putIfAbsent(code, b.structureName);
                        if (prev != null)
                            problems.add(f.getName() + "/" + g + ": " + prev + " and " + b.structureName + " both on key " + code);
                    }
                }
            }
        // the positional keys themselves are distinct
        Set<Integer> slotCodes = new HashSet<>();
        for (Keybinding kb : BuildGroup.SLOT_KEYS)
            for (int code : codes(kb))
                if (!slotCodes.add(code))
                    problems.add("two slot keys share key " + code);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(25, problems.size()))));
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40)
    public static void no_slot_key_clashes_with_the_workers_orders_or_abilities(GameTestHelper helper) {
        List<EntityType<?>> workers = List.of(
            EntityRegistrar.VILLAGER_UNIT.get(), EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(), EntityRegistrar.GRUNT_UNIT.get(),
            EntityRegistrar.SEEDSHAPER_UNIT.get(), EntityRegistrar.SHIPWRIGHT_UNIT.get(), EntityRegistrar.GEOMANCER_UNIT.get(),
            EntityRegistrar.TINKER_UNIT.get(),
            // the elder workers share their base worker's menu and add abilities of their own
            EntityRegistrar.ELDER_DRUID_UNIT.get(), EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(),
            EntityRegistrar.HARBOURMASTER_UNIT.get(), EntityRegistrar.LEY_MASTER_UNIT.get(),
            EntityRegistrar.EMBALMER_UNIT.get(), EntityRegistrar.BONEWRIGHT_UNIT.get(),
            EntityRegistrar.ENGINEER_CAPTAIN_UNIT.get(), EntityRegistrar.WRECKER_UNIT.get());
        List<String> problems = new ArrayList<>();
        for (EntityType<?> type : workers) {
            var e = type.create(helper.getLevel());
            if (!(e instanceof Unit unit)) {
                problems.add(type.getDescriptionId() + " could not be created");
                continue;
            }
            try {
                String who = type.getDescriptionId();
                Map<Integer, String> others = new HashMap<>();
                java.util.function.BiConsumer<Keybinding, String> other = (kb, what) -> {
                    for (int code : codes(kb))
                        others.putIfAbsent(code, what);
                };
                for (var order : CommandCard.orders(e instanceof AttackerUnit, e instanceof WorkerUnit, true, true))
                    other.accept(order.key(), "order " + order);
                for (var order : CommandCard.orders(e instanceof AttackerUnit, e instanceof WorkerUnit, true, false))
                    other.accept(order.key(), "order " + order);
                var list = unit.getAbilities().get();
                var keys = unit.getAbilities().effectiveHotkeys();
                for (int i = 0; i < list.size(); i++) {
                    boolean callToArms = list.get(i) instanceof com.solegendary.reignofnether.ability.abilities.CallToArmsUnit;
                    other.accept(callToArms ? Keybindings.hotkey1 : keys.get(i), "ability " + list.get(i).getClass().getSimpleName());
                }
                // the Return Resources button next to the portrait (HudClientEvents) is on hotkey 5
                other.accept(Keybindings.hotkey5, "Return Resources");
                for (int i = 0; i < BuildGroup.SLOT_KEYS.size(); i++)
                    for (int code : codes(BuildGroup.SLOT_KEYS.get(i))) {
                        String hit = others.get(code);
                        if (hit != null)
                            problems.add(who + ": slot key " + BuildGroup.SLOT_KEYS.get(i).buttonLabel + " is also " + hit);
                    }
            } finally {
                e.discard();
            }
        }
        // each tab switch key (Alt + slot key 1-4) is itself a slot key, and Alt + digit stays with control groups
        for (int i = 0; i < BuildGroup.TAB_KEYS.size(); i++)
            if (BuildGroup.TAB_KEYS.get(i) != BuildGroup.SLOT_KEYS.get(i))
                problems.add("tab key " + i + " is not slot key " + i);
        // dedupe (7 workers x 15 keys repeat the same hit)
        List<String> unique = new ArrayList<>(new java.util.LinkedHashSet<>(problems));
        if (unique.isEmpty())
            helper.succeed();
        else
            helper.fail(unique.size() + " problem(s): " + String.join("; ", unique.subList(0, Math.min(25, unique.size()))));
    }
}
