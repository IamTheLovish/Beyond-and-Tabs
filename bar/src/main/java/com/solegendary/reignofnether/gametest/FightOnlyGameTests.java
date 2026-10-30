package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildGroup;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.keybinds.FightArm;
import com.solegendary.reignofnether.keybinds.KeyPress;
import com.solegendary.reignofnether.keybinds.KeybindMigration;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.unit.UnitAction;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fight (F) is the only attack order (lovish, Oct 22: "we dont need f and x, only f"; and "I have to double click twice
 * with F to attack move"). Checks the pure rules behind it: FightArm (one press flips the armed state once), KeyPress
 * (the inventory slot keys stand down when the card has a button on the key), the bindings (no Attack, Fight on F), the
 * workers' build menus (F only as a build-grid cell) and KeybindMigration's carry of a custom Attack key to Fight.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FightOnlyGameTests {

    private static void verdict(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void fightonly_one_f_press_arms_a_second_disarms_and_press_release_counts_once(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        UnitAction armed = FightArm.press(null);
        if (armed != UnitAction.ATTACK_MOVE) problems.add("first press gave " + armed);
        UnitAction off = FightArm.press(armed);
        if (off != null) problems.add("second press gave " + off);
        UnitAction swapped = FightArm.press(UnitAction.MOVE);
        if (swapped != UnitAction.ATTACK_MOVE) problems.add("F while Move is armed gave " + swapped);
        // press + release is ONE press: the handler runs once per key release, so a single call leaves it armed
        UnitAction afterOnePressRelease = FightArm.press(null);
        if (afterOnePressRelease != UnitAction.ATTACK_MOVE) problems.add("press+release did not arm exactly once");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void fightonly_inventory_key_stands_down_when_a_card_button_shares_its_key(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (KeyPress.inventoryKeyMayFire(true)) problems.add("inventory key fired with a card button on the key");
        if (!KeyPress.inventoryKeyMayFire(false)) problems.add("inventory key stood down with no card button on the key");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void fightonly_no_attack_binding_exists_and_fight_is_on_f(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (Keybinding kb : Keybindings.all())
            if ("key.reignofnether.attack".equals(kb.getTranslationKey()))
                problems.add("a binding named key.reignofnether.attack still exists");
        if (Keybindings.fight.getDefaultKey() != GLFW.GLFW_KEY_F) problems.add("Fight is not on F");
        if (!"key.reignofnether.fight".equals(Keybindings.fight.getTranslationKey())) problems.add("Fight has the wrong translation key");
        if (!Keybindings.all().contains(Keybindings.fight)) problems.add("Fight is not registered");
        verdict(helper, problems);
    }

    /**
     * Main's build menu is BAR's grid (BuildGroup): the 8th cell of every tab is on the grid key F, live only while a tab
     * is open (BuildTabs.onKey runs first and eats the key). So F is never two things: no tab open, F is Fight; a tab
     * open, F picks the cell. A build slot on F that is NOT a grid key (the old hotkey3 slots) would fire with Fight.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void fightonly_f_is_a_build_slot_only_as_a_grid_cell(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Faction f : ReignOfNetherRegistries.FACTIONS) {
            for (Map.Entry<Building, Keybinding> e : f.getBuildHotkeys().entrySet()) {
                checked++;
                Keybinding kb = e.getValue();
                if (kb == null) continue;
                boolean onF = kb.getDefaultKey() == Keybindings.fight.getDefaultKey() || kb.getAliasKey() == Keybindings.fight.getDefaultKey();
                if (kb == Keybindings.hotkey3 || (onF && !BuildGroup.SLOT_KEYS.contains(kb)))
                    problems.add(f.getName() + ": " + e.getKey().name + " is on the Fight key outside the build grid");
            }
        }
        if (checked == 0) problems.add("no faction recorded any build hotkey (nothing was checked)");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void fightonly_migration_carries_a_custom_attack_key_to_fight_and_leaves_defaults(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        final String attack = "key.reignofnether.attack";
        final String fight = Keybindings.fight.getTranslationKey();
        final int fKey = Keybindings.fight.getDefaultKey();

        // custom Attack key (G) with Fight still on F: moves to Fight
        var custom = KeybindMigration.plan(2, k -> k.equals(attack) ? GLFW.GLFW_KEY_G : k.equals(fight) ? fKey : KeybindMigration.CUSTOM);
        long carried = custom.stream().filter(m -> m.binding().equals(fight) && m.to() == GLFW.GLFW_KEY_G && m.from() == fKey).count();
        if (carried != 1) problems.add("custom Attack G did not move to Fight: " + custom);

        // default saves (A, then X) change nothing on Fight
        for (int old : new int[] {GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_X}) {
            var none = KeybindMigration.plan(2, k -> k.equals(attack) ? old : k.equals(fight) ? fKey : KeybindMigration.CUSTOM);
            if (none.stream().anyMatch(m -> m.binding().equals(fight))) problems.add("old default " + old + " moved Fight: " + none);
        }
        // already at layout 3: no carry
        var done = KeybindMigration.plan(KeybindMigration.LAYOUT_VERSION,
            k -> k.equals(attack) ? GLFW.GLFW_KEY_G : k.equals(fight) ? fKey : KeybindMigration.CUSTOM);
        if (done.stream().anyMatch(m -> m.binding().equals(fight))) problems.add("carry ran again at the current layout");
        verdict(helper, problems);
    }
}
