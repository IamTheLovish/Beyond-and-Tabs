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
                // BAR's grid has 12 keys; a 13th building on is click only (a fourth row without a key), never keyed
                for (int i = BuildGroup.SLOT_KEYS.size(); i < tab.size(); i++)
                    if (f.getBuildHotkeys().get(tab.get(i)) != null)
                        problems.add(who + ": " + tab.get(i).structureName + " is past the 12 grid keys of " + g + " but has a key");
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

    /**
     * BAR's gridmenu keys (Grid profile of keybind_defaults.json): the grid is Q W E R / A S D F / Z X C V in reading
     * order on a 4-wide grid, and Z / X / C / V open Economy / Military / Defence / Tech. The grid keys share keys with
     * the orders on purpose (live only in an open tab, then first: BuildTabs.onKey); each has its own conflict context.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40)
    public static void grid_and_tab_keys_follow_bar_gridmenu(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int[] grid = { org.lwjgl.glfw.GLFW.GLFW_KEY_Q, org.lwjgl.glfw.GLFW.GLFW_KEY_W, org.lwjgl.glfw.GLFW.GLFW_KEY_E,
            org.lwjgl.glfw.GLFW.GLFW_KEY_R, org.lwjgl.glfw.GLFW.GLFW_KEY_A, org.lwjgl.glfw.GLFW.GLFW_KEY_S,
            org.lwjgl.glfw.GLFW.GLFW_KEY_D, org.lwjgl.glfw.GLFW.GLFW_KEY_F, org.lwjgl.glfw.GLFW.GLFW_KEY_Z,
            org.lwjgl.glfw.GLFW.GLFW_KEY_X, org.lwjgl.glfw.GLFW.GLFW_KEY_C, org.lwjgl.glfw.GLFW.GLFW_KEY_V };
        if (BuildGroup.SLOT_KEYS.size() != grid.length || BuildGroup.COLS != 4)
            problems.add(BuildGroup.SLOT_KEYS.size() + " grid keys on " + BuildGroup.COLS + " columns, BAR has 12 on 4");
        for (int i = 0; i < Math.min(grid.length, BuildGroup.SLOT_KEYS.size()); i++) {
            Keybinding kb = BuildGroup.SLOT_KEYS.get(i);
            if (kb.getDefaultKey() != grid[i])
                problems.add("grid cell " + i + " defaults to " + kb.buttonLabel);
            if (!Keybindings.all().contains(kb))
                problems.add(kb.getTranslationKey() + " is not registered (no Controls entry)");
        }
        // the tab keys are the grid's bottom row, by tab ordinal
        for (int i = 0; i < BuildGroup.TAB_KEYS.size(); i++)
            if (BuildGroup.TAB_KEYS.get(i).getDefaultKey() != grid[8 + i])
                problems.add("tab key " + i + " is not the grid's bottom-row key " + i);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " problem(s): " + String.join("; ", problems));
    }
}
