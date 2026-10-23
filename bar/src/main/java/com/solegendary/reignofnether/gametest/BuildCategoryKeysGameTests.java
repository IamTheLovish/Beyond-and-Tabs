package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.keybinds.BuildCategoryKeys;
import com.solegendary.reignofnether.keybinds.BuildCategoryKeys.Category;
import com.solegendary.reignofnether.keybinds.KeybindMigration;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * BAR's build-category keys (keybinds/BuildCategoryKeys): Z metal, X energy, C defence, V factories. The pure rules -
 * the order of each list, what the builder can build, the cycle and its wrap, who owns the key - plus a sweep over
 * every registered building so a new one cannot silently fall out of its category.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildCategoryKeysGameTests {

    private static void verdict(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void category_keys_default_to_bar_zxcv(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (Keybindings.buildMetal.getDefaultKey() != GLFW.GLFW_KEY_Z) problems.add("metal is not Z");
        if (Keybindings.buildEnergy.getDefaultKey() != GLFW.GLFW_KEY_X) problems.add("energy is not X");
        if (Keybindings.buildDefence.getDefaultKey() != GLFW.GLFW_KEY_C) problems.add("defence is not C");
        if (Keybindings.buildFactory.getDefaultKey() != GLFW.GLFW_KEY_V) problems.add("factory is not V");
        for (Keybinding kb : List.of(Keybindings.buildMetal, Keybindings.buildEnergy, Keybindings.buildDefence, Keybindings.buildFactory))
            if (!Keybindings.all().contains(kb))
                problems.add(kb.getTranslationKey() + " is not registered");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void category_key_cycles_in_bar_order_and_wraps(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        // card order is scrambled on purpose: the list is BAR's order, not the card's
        List<String> card = List.of("energy_converter_tide", "geothermal_plant_tide", "wind_generator_tide",
            "advanced_solar_tide", "solar_collector_tide", "metal_extractor_tide", "storage_vault_tide");
        List<String> energy = BuildCategoryKeys.listFor(Category.ENERGY, card, Set.of());
        List<String> want = List.of("solar_collector_tide", "wind_generator_tide", "advanced_solar_tide",
            "geothermal_plant_tide", "energy_converter_tide");
        if (!energy.equals(want))
            problems.add("energy list " + energy + " should be " + want);
        BuildCategoryKeys.Cycle cycle = new BuildCategoryKeys.Cycle();
        List<String> got = new ArrayList<>();
        for (int i = 0; i < want.size() + 2; i++)
            got.add(cycle.press(Category.ENERGY, energy));
        List<String> expect = new ArrayList<>(want);
        expect.add(want.get(0));   // the wrap
        expect.add(want.get(1));
        if (!got.equals(expect))
            problems.add("seven presses gave " + got + ", expected " + expect);
        // a different key in between restarts the list
        cycle.press(Category.ENERGY, energy);
        cycle.press(Category.METAL, List.of("metal_extractor_tide"));
        if (!cycle.press(Category.ENERGY, energy).equals(want.get(0)))
            problems.add("energy after another key should start at the first entry");
        // reset (the ghost was placed or dropped) restarts it too
        cycle.press(Category.ENERGY, energy);
        cycle.reset();
        if (!cycle.press(Category.ENERGY, energy).equals(want.get(0)))
            problems.add("energy after a reset should start at the first entry");
        // an empty list picks nothing and leaves the cycle alone
        if (cycle.press(Category.DEFENCE, List.of()) != null)
            problems.add("an empty list must pick nothing");
        // a one-entry list (the extractor) keeps picking itself
        BuildCategoryKeys.Cycle one = new BuildCategoryKeys.Cycle();
        for (int i = 0; i < 3; i++)
            if (!"metal_extractor_tide".equals(one.press(Category.METAL, List.of("metal_extractor_tide"))))
                problems.add("a one-entry list should repeat its entry");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void category_key_lists_only_what_the_builder_can_build(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        // a T1 builder's card: no flak (T2), no Lab Annex yet
        List<String> t1 = List.of("raid_tower_villagers", "sky_watch_villagers", "watchtower", "metal_extractor_villagers");
        List<String> defence = BuildCategoryKeys.listFor(Category.DEFENCE, t1, Set.of());
        if (!defence.equals(List.of("raid_tower_villagers", "watchtower", "sky_watch_villagers")))
            problems.add("T1 defence list was " + defence);
        // the T2 builder also gets flak, after the anti-air towers
        List<String> t2 = new ArrayList<>(t1);
        t2.add("flak_villagers");
        t2.add("anti_t3_villagers");
        List<String> defence2 = BuildCategoryKeys.listFor(Category.DEFENCE, t2, Set.of());
        if (!defence2.equals(List.of("raid_tower_villagers", "watchtower", "sky_watch_villagers", "flak_villagers", "anti_t3_villagers")))
            problems.add("T2 defence list was " + defence2);
        // nothing buildable in the category: empty (the key keeps its old meaning)
        if (!BuildCategoryKeys.listFor(Category.FACTORY, List.of("metal_extractor_villagers"), Set.of()).isEmpty())
            problems.add("a builder with no factory in reach must get an empty factory list");
        // factory: the nano-like annex first, then the lab
        List<String> factory = BuildCategoryKeys.listFor(Category.FACTORY, List.of("barracks", "lab_annex_villagers", "castle"), Set.of());
        if (!factory.equals(List.of("lab_annex_villagers", "barracks")))
            problems.add("factory list was " + factory + " (the Castle is a T3 lab: it keeps its own key)");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void category_key_keeps_unmapped_buildings_on_their_slot_key(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        // the stockpile has no BAR key and sits on Z: it joins the end of the Z cycle instead of losing its key
        List<String> card = List.of("oak_stockpile", "metal_extractor_villagers", "storage_vault_villagers", "oak_bridge");
        List<String> metal = BuildCategoryKeys.listFor(Category.METAL, card, Set.of("oak_stockpile"));
        if (!metal.equals(List.of("metal_extractor_villagers", "oak_stockpile")))
            problems.add("Z list was " + metal);
        // the bridge sits on C, not Z: it must not leak into the Z list, the storage vault (on neither) stays out
        List<String> defence = BuildCategoryKeys.listFor(Category.DEFENCE, card, Set.of("oak_bridge"));
        if (!defence.equals(List.of("oak_bridge")))
            problems.add("C list was " + defence);
        // a categorised building is never doubled into another key's tail even if its slot key is that key
        List<String> tail = BuildCategoryKeys.listFor(Category.DEFENCE, List.of("portal_basic"), Set.of("portal_basic"));
        if (!tail.isEmpty())
            problems.add("a factory on the C slot must not appear in the C list: " + tail);
        for (String unmapped : List.of("storage_vault_villagers", "wall_villagers", "wall_gate_villagers", "trap_spikes_villagers",
                "castle", "stronghold", "fortress", "oak_stockpile"))
            if (BuildCategoryKeys.categoryOf(unmapped) != null)
                problems.add(unmapped + " has no BAR key and must stay uncategorised");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void category_keys_belong_to_builders_only(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        List<String> some = List.of("metal_extractor_villagers");
        if (!BuildCategoryKeys.claimsKey(true, false, some))
            problems.add("a selected builder must own Z / X / C / V");
        if (BuildCategoryKeys.claimsKey(false, false, some))
            problems.add("without a builder the keys keep their old meaning (ability slot 2, Attack, ...)");
        if (BuildCategoryKeys.claimsKey(true, true, some))
            problems.add("Ctrl / Alt + key is not a build key");
        if (BuildCategoryKeys.claimsKey(true, false, List.of()))
            problems.add("an empty list must give the key back");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void category_key_migration_leaves_the_new_bindings_alone(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        // worst case: every saved binding sits on Z (X, C, V). No layout move may target a category binding, and an
        // install already at the current layout moves nothing.
        for (int key : new int[] { GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V }) {
            for (KeybindMigration.Move m : KeybindMigration.plan(0, name -> key))
                if (m.binding().startsWith("key.reignofnether.build_"))
                    problems.add("migration moves " + m.binding());
            if (!KeybindMigration.plan(KeybindMigration.LAYOUT_VERSION, name -> key).isEmpty())
                problems.add("an install at the current layout must not move anything");
        }
        // a player's own choice for a category binding (a modifier combo, say) is never touched
        if (!KeybindMigration.plan(0, name -> KeybindMigration.CUSTOM).isEmpty())
            problems.add("custom bindings must not move");
        verdict(helper, problems);
    }

    /** BAR's Ctrl+W selects the army: "-" stays its card key, and W (pan up) is the only binding Ctrl+W could touch. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void ctrl_w_select_army_does_not_clash_with_pan_or_build_keys(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (Keybindings.selectArmy.getDefaultKey() != GLFW.GLFW_KEY_MINUS)
            problems.add("select army keeps '-' as its own key");
        if (Keybindings.panUp.getDefaultKey() != GLFW.GLFW_KEY_W)
            problems.add("W is pan up (a held W pans; only Ctrl+W selects the army)");
        for (Keybinding kb : List.of(Keybindings.buildMetal, Keybindings.buildEnergy, Keybindings.buildDefence, Keybindings.buildFactory))
            if (kb.getDefaultKey() == GLFW.GLFW_KEY_W)
                problems.add(kb.getTranslationKey() + " must not sit on W");
        verdict(helper, problems);
    }

    /** Every registered building lands in the category its job says, for every faction. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void category_keys_cover_every_factions_economy_and_defence(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Map<String, Map<Category, List<String>>> byFaction = new TreeMap<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Building>, Building> e : ReignOfNetherRegistries.BUILDING.entrySet()) {
            Building b = e.getValue();
            if (b.getFaction() == null || b.getFaction().key == null)
                continue;
            String path = e.getKey().location().getPath();
            Category c = BuildCategoryKeys.categoryOf(path);
            if (c != null)
                byFaction.computeIfAbsent(b.getFaction().getName(), k -> new TreeMap<>()).computeIfAbsent(c, k -> new ArrayList<>()).add(path);
        }
        for (String faction : List.of("villagers", "monsters", "piglins", "verdant_court", "tidewrought", "stormcallers", "brassworks")) {
            Map<Category, List<String>> cats = byFaction.get(faction);
            if (cats == null) {
                problems.add(faction + " has no categorised building at all");
                continue;
            }
            List<String> metal = cats.getOrDefault(Category.METAL, List.of());
            if (metal.size() != 1 || !metal.get(0).startsWith("metal_extractor_"))
                problems.add(faction + " Z list " + metal);
            List<String> energy = BuildCategoryKeys.listFor(Category.ENERGY, cats.getOrDefault(Category.ENERGY, List.of()), Set.of());
            if (energy.size() != 5 || !energy.get(0).startsWith("solar_collector_") || !energy.get(4).startsWith("energy_converter_"))
                problems.add(faction + " X list " + energy);
            List<String> defence = BuildCategoryKeys.listFor(Category.DEFENCE, cats.getOrDefault(Category.DEFENCE, List.of()), Set.of());
            if (defence.size() < 6 || !defence.get(0).startsWith("raid_tower_") || defence.stream().noneMatch(p -> p.startsWith("sky_watch_")))
                problems.add(faction + " C list " + defence);
            List<String> factory = BuildCategoryKeys.listFor(Category.FACTORY, cats.getOrDefault(Category.FACTORY, List.of()), Set.of());
            if (factory.size() < 2 || !factory.get(0).startsWith("lab_annex_"))
                problems.add(faction + " V list " + factory);
        }
        verdict(helper, problems);
    }

    /** Z is the box's same-type filter only while a box drag is in progress; a Z press at any other time is the metal key. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void category_key_z_is_a_box_filter_only_during_a_drag(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (com.solegendary.reignofnether.unit.SmartSelect.Mods.held(false, false, false, false, true, false).sameType())
            problems.add("Z held with no box drag must not set the same-type filter");
        if (!com.solegendary.reignofnether.unit.SmartSelect.Mods.held(true, false, false, false, true, false).sameType())
            problems.add("Z held during a box drag is the same-type filter");
        if (com.solegendary.reignofnether.unit.SmartSelect.Mods.held(true, false, false, false, false, false).sameType())
            problems.add("no Z, no filter");
        // the other modifiers pass through either way
        var m = com.solegendary.reignofnether.unit.SmartSelect.Mods.held(false, true, true, true, true, true);
        if (!m.shift() || !m.ctrl() || !m.alt() || !m.idleOnly() || m.sameType())
            problems.add("shift/ctrl/alt/space pass through, only Z is gated: " + m);
        verdict(helper, problems);
    }
}
