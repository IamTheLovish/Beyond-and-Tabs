package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.keybinds.BuildCategoryKeys;
import com.solegendary.reignofnether.keybinds.BuildCategoryKeys.Action;
import com.solegendary.reignofnether.keybinds.BuildCategoryKeys.Press;
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

/**
 * BAR's gridmenu keys (keybinds/BuildCategoryKeys, BuildTabs): Z / X / C / V open a build tab, and in an open tab the
 * grid keys Q W E R / A S D F / Z X C V pick the building in their cell. The pure rules, headless.
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

    private static boolean[] cells(int... used) {
        boolean[] out = new boolean[12];
        for (int c : used)
            out[c] = true;
        return out;
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void build_tab_keys_default_to_bar_zxcv(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (Keybindings.buildTabEconomy.getDefaultKey() != GLFW.GLFW_KEY_Z) problems.add("Economy is not Z");
        if (Keybindings.buildTabMilitary.getDefaultKey() != GLFW.GLFW_KEY_X) problems.add("Military is not X");
        if (Keybindings.buildTabDefence.getDefaultKey() != GLFW.GLFW_KEY_C) problems.add("Defence is not C");
        if (Keybindings.buildTabTech.getDefaultKey() != GLFW.GLFW_KEY_V) problems.add("Tech is not V");
        for (Keybinding kb : List.of(Keybindings.buildTabEconomy, Keybindings.buildTabMilitary, Keybindings.buildTabDefence, Keybindings.buildTabTech))
            if (!Keybindings.all().contains(kb))
                problems.add(kb.getTranslationKey() + " is not registered");
        verdict(helper, problems);
    }

    /** Home: a tab key opens its tab; a grid key that is not a tab key does nothing (S stays Stop). */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void build_keys_at_home_open_a_tab_and_leave_other_keys_alone(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        boolean[] full = cells(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        Press x = BuildCategoryKeys.press(true, false, false, 1, 9, full);   // X: tab 1 and grid cell 9
        if (!x.equals(new Press(Action.OPEN_TAB, 1)))
            problems.add("X at home should open Military, got " + x);
        if (BuildCategoryKeys.press(true, false, false, -1, 5, full) != Press.NONE)
            problems.add("S at home must stay Stop / pan");
        verdict(helper, problems);
    }

    /** Open: a grid key picks its cell; on an empty cell it falls through, or switches tab for a tab key. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void build_keys_in_an_open_tab_pick_the_cell(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        boolean[] some = cells(0, 5, 9);
        Press s = BuildCategoryKeys.press(true, false, true, -1, 5, some);
        if (!s.equals(new Press(Action.PICK_CELL, 5)))
            problems.add("S in an open tab should pick cell 5, got " + s);
        if (!BuildCategoryKeys.press(true, false, true, 1, 9, some).equals(new Press(Action.PICK_CELL, 9)))
            problems.add("X on a used cell must pick it, not switch tab");
        if (!BuildCategoryKeys.press(true, false, true, 2, 10, some).equals(new Press(Action.OPEN_TAB, 2)))
            problems.add("C on an empty cell should switch to Defence");
        if (BuildCategoryKeys.press(true, false, true, -1, 1, some) != Press.NONE)
            problems.add("W on an empty cell must fall through");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void build_keys_need_the_build_menu_and_no_ctrl_or_alt(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        boolean[] full = cells(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        if (BuildCategoryKeys.press(false, false, false, 0, 8, full) != Press.NONE)
            problems.add("without the build menu Z keeps its old meaning");
        if (BuildCategoryKeys.press(true, true, true, -1, 0, full) != Press.NONE)
            problems.add("Ctrl / Alt + key is not a build key");
        verdict(helper, problems);
    }

    /** The open tab decides the building: a cell maps to whichever usable button holds it in the drawn tab. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void build_grid_key_maps_to_the_drawn_tabs_cell(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int[] keys = { GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R };
        if (BuildCategoryKeys.indexOf(GLFW.GLFW_KEY_E, keys) != 2 || BuildCategoryKeys.indexOf(-1, keys) != -1)
            problems.add("indexOf finds a key's position, never -1");
        int[] tabCells = { 0, 1, 4 };
        boolean[] usable = { false, true, true };
        if (BuildCategoryKeys.buttonAt(4, tabCells, usable) != 2)
            problems.add("cell 4 should be button 2");
        if (BuildCategoryKeys.buttonAt(0, tabCells, usable) != -1)
            problems.add("a disabled button is not picked");
        boolean[] u = BuildCategoryKeys.usableCells(12, tabCells, usable);
        if (u[0] || !u[1] || !u[4] || u[2])
            problems.add("usable cells wrong");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void build_key_migration_leaves_the_new_bindings_alone(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (int key : new int[] { GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A }) {
            for (KeybindMigration.Move m : KeybindMigration.plan(0, name -> key))
                if (m.binding().startsWith("key.reignofnether.build_"))
                    problems.add("migration moves " + m.binding());
            if (!KeybindMigration.plan(KeybindMigration.LAYOUT_VERSION, name -> key).isEmpty())
                problems.add("an install at the current layout must not move anything");
        }
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
        for (Keybinding kb : List.of(Keybindings.buildTabEconomy, Keybindings.buildTabMilitary, Keybindings.buildTabDefence, Keybindings.buildTabTech))
            if (kb.getDefaultKey() == GLFW.GLFW_KEY_W)
                problems.add(kb.getTranslationKey() + " must not sit on W");
        verdict(helper, problems);
    }

    /** Z is the box's same-type filter only while a box drag is in progress; a Z press at any other time is the tab key. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void category_key_z_is_a_box_filter_only_during_a_drag(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (com.solegendary.reignofnether.unit.SmartSelect.Mods.held(false, false, false, false, true, false).sameType())
            problems.add("Z held with no box drag must not set the same-type filter");
        if (!com.solegendary.reignofnether.unit.SmartSelect.Mods.held(true, false, false, false, true, false).sameType())
            problems.add("Z held during a box drag is the same-type filter");
        if (com.solegendary.reignofnether.unit.SmartSelect.Mods.held(true, false, false, false, false, false).sameType())
            problems.add("no Z, no filter");
        var m = com.solegendary.reignofnether.unit.SmartSelect.Mods.held(false, true, true, true, true, true);
        if (!m.shift() || !m.ctrl() || !m.alt() || !m.idleOnly() || m.sameType())
            problems.add("shift/ctrl/alt/space pass through, only Z is gated: " + m);
        verdict(helper, problems);
    }
}
