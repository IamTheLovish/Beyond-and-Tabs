package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.guiscreen.PauseFlow;
import com.solegendary.reignofnether.keybinds.BuildCategoryKeys;
import com.solegendary.reignofnether.keybinds.ChordKeys;
import com.solegendary.reignofnether.keybinds.ChordKeys.Chord;
import com.solegendary.reignofnether.keybinds.KeyConflicts;
import com.solegendary.reignofnether.hud.CommandCard;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Intuitive keys after BAR's Grid profile (keybinds/BarGridDefaults): Attack on A, Esc cancels before the menu, Tab /
 * Ctrl+C commander, Ctrl+B / Ctrl+Shift+B idle builders, Ctrl+E / Ctrl+W selection, item slot 6's label.
 * The rules are pure (keybinds/ChordKeys, BuildCategoryKeys, guiscreen/PauseFlow), checked headless.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class IntuitiveKeysGameTests {

    private static void verdict(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    private static final int[] GRID_KEYS = {
        GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S,
        GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V };
    private static final int[] TAB_KEYS = { GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V };

    /** What on this unit's card (orders and abilities, primary keys and aliases) sits on {@code code}. */
    private static List<String> onKey(net.minecraft.world.entity.Entity e, int code) {
        List<String> out = new ArrayList<>();
        for (var order : CommandCard.orders(e instanceof AttackerUnit, e instanceof WorkerUnit, true, false)) {
            var kb = order.key();
            if (kb != null && (kb.getKey() == code || kb.getAliasKey() == code))
                out.add("order " + order);
        }
        var abilities = ((Unit) e).getAbilities();
        if (abilities != null) {
            var list = abilities.get();
            var keys = abilities.effectiveHotkeys();
            for (int i = 0; i < list.size(); i++)
                if (keys.get(i) != null && (keys.get(i).getKey() == code || keys.get(i).getAliasKey() == code))
                    out.add("ability " + list.get(i).getClass().getSimpleName());
        }
        return out;
    }

    /**
     * BAR Grid (BarGridDefaults): A is Attack and X is the Military tab, so a builder's X opens a menu and a fighter's X
     * does nothing. Before BAC-007b Attack was X and A panned the camera (WASD pan is gone).
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40)
    public static void ac1_a_attacks_x_opens_military_only_in_a_builders_menu(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        boolean[] usable = new boolean[12];
        java.util.Arrays.fill(usable, true);
        int gridA = BuildCategoryKeys.indexOf(GLFW.GLFW_KEY_A, GRID_KEYS);
        int gridX = BuildCategoryKeys.indexOf(GLFW.GLFW_KEY_X, GRID_KEYS);
        int tabX = BuildCategoryKeys.indexOf(GLFW.GLFW_KEY_X, TAB_KEYS);
        var commander = EntityRegistrar.VILLAGER_UNIT.get().create(helper.getLevel());
        var fighter = EntityRegistrar.VINDICATOR_UNIT.get().create(helper.getLevel());
        try {
            if (!(commander instanceof Unit) || !(fighter instanceof Unit)) {
                helper.fail("could not create the commander / fighter units");
                return;
            }
            CommanderServerEvents.ensureAbility(commander);
            List<String> onA = onKey(commander, GLFW.GLFW_KEY_A);
            if (!onA.equals(List.of("order ATTACK")))
                problems.add("a builder-commander's card must have exactly Attack on A, has " + onA);
            // the builder's menu at home: A is no tab key and no grid cell is live, so it does nothing there
            var a = BuildCategoryKeys.press(true, false, false, BuildCategoryKeys.indexOf(GLFW.GLFW_KEY_A, TAB_KEYS), gridA, usable);
            if (a.action() != BuildCategoryKeys.Action.NONE)
                problems.add("A in a builder's menu at home must do nothing there, is " + a);
            var x = BuildCategoryKeys.press(true, false, false, tabX, gridX, usable);
            if (x.action() != BuildCategoryKeys.Action.OPEN_TAB || x.index() != 1)
                problems.add("X in a builder's menu at home opens the Military tab (index 1), is " + x);
            if (!onKey(commander, GLFW.GLFW_KEY_X).isEmpty())
                problems.add("nothing on a builder-commander's card may sit on X, has " + onKey(commander, GLFW.GLFW_KEY_X));
            // a fighter has no build menu: X is nothing at all
            var fx = BuildCategoryKeys.press(false, false, false, tabX, gridX, usable);
            if (fx.action() != BuildCategoryKeys.Action.NONE)
                problems.add("X on a fighter must open no menu, is " + fx);
            if (!onKey(fighter, GLFW.GLFW_KEY_X).isEmpty())
                problems.add("nothing on a fighter's card may sit on X, has " + onKey(fighter, GLFW.GLFW_KEY_X));
            if (!onKey(fighter, GLFW.GLFW_KEY_A).equals(List.of("order ATTACK")))
                problems.add("a fighter's A is Attack, is " + onKey(fighter, GLFW.GLFW_KEY_A));
            if (Keybindings.attack.getDefaultKey() != GLFW.GLFW_KEY_A)
                problems.add("Attack ships on A, is " + Keybindings.attack.getDefaultKey());
        } finally {
            if (commander != null)
                commander.discard();
            if (fighter != null)
                fighter.discard();
        }
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void ac2_esc_clears_placement_then_selection_before_the_menu(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (Keybindings.pause.getKey() != GLFW.GLFW_KEY_ESCAPE)
            problems.add("Esc stays the raw pause key");
        PauseFlow.EscAction a;
        if ((a = PauseFlow.onRtsEsc(true, false, true, false)) != PauseFlow.EscAction.DROP_BUILDING)
            problems.add("placing with units selected: the first Esc drops the placement, got " + a);
        if ((a = PauseFlow.onRtsEsc(false, false, true, false)) != PauseFlow.EscAction.DESELECT)
            problems.add("then Esc clears the selection, got " + a);
        for (boolean placing : new boolean[] {true, false})
            if (PauseFlow.onRtsEsc(placing, false, !placing, false) == PauseFlow.EscAction.OPEN_MENU)
                problems.add("Esc with something to clear must not open the menu");
        if (PauseFlow.onRtsEsc(false, false, false, true) != PauseFlow.EscAction.OPEN_MENU)
            problems.add("Shift+Esc opens the menu");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void ac3_ctrl_c_selects_and_focuses_the_commander(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (ChordKeys.of(GLFW.GLFW_KEY_C, true, false, false) != Chord.SELECT_COMMANDER)
            problems.add("Ctrl+C is select commander");
        if (ChordKeys.of(GLFW.GLFW_KEY_C, true, true, false) != Chord.SELECT_COMMANDER)
            problems.add("Ctrl+Shift+C is still select commander");
        if (ChordKeys.of(GLFW.GLFW_KEY_C, false, false, false) != Chord.NONE)
            problems.add("plain C stays the build tab / build slot");
        if (ChordKeys.of(GLFW.GLFW_KEY_C, true, false, true) != Chord.NONE)
            problems.add("Ctrl+Alt+C is no chord");
        if (Keybindings.selectCommander.getChord() != Chord.SELECT_COMMANDER)
            problems.add("the commander button shows Ctrl+C");
        if (Keybindings.selectCommander.getDefaultKey() != GLFW.GLFW_KEY_TAB)
            problems.add("select commander ships on Tab (BAR: tab selectcomm focus)");
        if (Keybindings.selectCommander.getDefaultKey() == GLFW.GLFW_KEY_END)
            problems.add("select commander must be off End (pause unit production)");
        for (Keybinding kb : Keybindings.all())
            if (kb.getTranslationKey() != null && kb.getDefaultKey() == GLFW.GLFW_KEY_END)
                problems.add(kb.getTranslationKey() + " still ships on End");
        if (!"^C".equals(ChordKeys.shortLabel(Chord.SELECT_COMMANDER)))
            problems.add("button corner label is ^C, got " + ChordKeys.shortLabel(Chord.SELECT_COMMANDER));
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void ac4_ctrl_b_next_idle_builder_and_ctrl_shift_b_idle_t2(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (ChordKeys.of(GLFW.GLFW_KEY_B, true, false, false) != Chord.IDLE_BUILDER)
            problems.add("Ctrl+B is the next idle builder");
        if (ChordKeys.of(GLFW.GLFW_KEY_B, true, true, false) != Chord.IDLE_T2_BUILDER)
            problems.add("Ctrl+Shift+B is the next idle T2 builder");
        if (ChordKeys.of(GLFW.GLFW_KEY_B, false, false, false) != Chord.NONE)
            problems.add("plain B stays Build / item slot 6");
        if (Keybindings.idleWorker.getDefaultKey() != GLFW.GLFW_KEY_PERIOD)
            problems.add("'.' stays the idle-worker key");
        if (Keybindings.idleT2Builder.getDefaultKey() == GLFW.GLFW_KEY_HOME)
            problems.add("idle T2 builder must be off Home (production monitor)");
        if (Keybindings.idleT2Builder.getChord() != Chord.IDLE_T2_BUILDER)
            problems.add("the idle T2 button shows Ctrl+Shift+B");
        for (Keybinding kb : Keybindings.all())
            if (kb.getTranslationKey() != null && kb.getDefaultKey() == GLFW.GLFW_KEY_HOME)
                problems.add(kb.getTranslationKey() + " still ships on Home");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void ac5_ctrl_e_all_units_and_ctrl_w_same_type(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (ChordKeys.of(GLFW.GLFW_KEY_E, true, false, false) != Chord.SELECT_ALL_UNITS)
            problems.add("Ctrl+E selects all units");
        if (ChordKeys.of(GLFW.GLFW_KEY_W, true, false, false) != Chord.SELECT_SAME_TYPE)
            problems.add("Ctrl+W selects the same types");
        for (Chord c : Chord.values())
            if (c.name().equals("SELECT_ARMY"))
                problems.add("the select-army chord is gone");
        if (ChordKeys.of(GLFW.GLFW_KEY_A, true, false, false) != Chord.NONE || ChordKeys.of(GLFW.GLFW_KEY_Z, true, false, false) != Chord.NONE)
            problems.add("Ctrl+A and Ctrl+Z are no chords any more");
        for (int d = GLFW.GLFW_KEY_0; d <= GLFW.GLFW_KEY_9; d++)
            if (ChordKeys.of(d, true, false, false) != Chord.NONE)
                problems.add("Ctrl+" + (char) d + " must stay a control group");
        // no two chords on one (key, shift)
        for (int i = 0; i < ChordKeys.ALL.size(); i++)
            for (int j = i + 1; j < ChordKeys.ALL.size(); j++) {
                ChordKeys.Def a = ChordKeys.ALL.get(i), b = ChordKeys.ALL.get(j);
                if (a.key() == b.key() && (a.shift() == null || b.shift() == null || a.shift().equals(b.shift())))
                    problems.add(a.label() + " and " + b.label() + " overlap");
            }
        // Ctrl+W: own units of the selected types (type = the name without digits here)
        List<String> owned = List.of("knight1", "archer1", "worker1", "knight2", "archer2");
        List<String> sel = List.of("knight9");
        List<String> same = ChordKeys.sameTypes(owned, sel, s -> s.replaceAll("\\d", ""));
        if (!same.equals(List.of("knight1", "knight2")))
            problems.add("Ctrl+W with a knight selected picks every own knight, got " + same);
        List<String> two = ChordKeys.sameTypes(owned, List.of("archer0", "worker0"), s -> s.replaceAll("\\d", ""));
        if (!two.equals(List.of("archer1", "worker1", "archer2")))
            problems.add("Ctrl+W with two types picks both, got " + two);
        if (!ChordKeys.sameTypes(owned, List.<String>of(), s -> s).isEmpty())
            problems.add("Ctrl+W with nothing selected selects nothing");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void ac6_item_slot_labels_match_their_keys(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (Keybindings.item6.getDefaultKey() != GLFW.GLFW_KEY_B || !"B".equals(Keybindings.item6.buttonLabel))
            problems.add("item slot 6 is on B and must read B, reads " + Keybindings.item6.buttonLabel);
        // every single-letter label names its own key
        for (Keybinding kb : Keybindings.all()) {
            int k = kb.getDefaultKey();
            String l = kb.buttonLabel;
            if (kb.getTranslationKey() != null && k >= GLFW.GLFW_KEY_A && k <= GLFW.GLFW_KEY_Z
                    && l != null && l.length() == 1 && Character.isLetter(l.charAt(0)) && l.charAt(0) != (char) k)
                problems.add(kb.getTranslationKey() + " is on " + (char) k + " but labelled " + l);
        }
        verdict(helper, problems);
    }
}
