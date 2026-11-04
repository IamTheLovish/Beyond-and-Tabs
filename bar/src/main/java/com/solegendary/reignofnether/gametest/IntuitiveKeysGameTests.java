package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.guiscreen.PauseFlow;
import com.solegendary.reignofnether.keybinds.BuildCategoryKeys;
import com.solegendary.reignofnether.keybinds.ChordKeys;
import com.solegendary.reignofnether.keybinds.ChordKeys.Chord;
import com.solegendary.reignofnether.keybinds.KeyConflicts;
import com.solegendary.reignofnether.keybinds.KeyTapTracker;
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
 * Intuitive keys after BAR's Legacy profile (keybind_defaults.json "Legacy"): Attack as a tap of A, Esc cancels before
 * the menu, Ctrl+C commander, Ctrl+B / Ctrl+Shift+B idle builders, Ctrl+A / Ctrl+Z selection, item slot 6's label.
 * The rules are pure (keybinds/ChordKeys, KeyTapTracker, BuildCategoryKeys, guiscreen/PauseFlow), checked headless.
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

    private static long ms(long m) {
        return m * 1_000_000L;
    }

    /** A press held {@code heldMs} at 120 fps: true when the tap/hold rule calls it a hold (a pan). */
    private static boolean isHold(int key, long heldMs) {
        KeyTapTracker t = new KeyTapTracker(GLFW.GLFW_KEY_LAST + 1);
        t.press(key, ms(1000));
        for (long at = 1008; at <= 1000 + heldMs; at += 8)
            t.frame(ms(at));
        return t.isHold(key, ms(1000 + heldMs));
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void ac1_attack_stays_on_x_and_a_stays_pure_pan(GameTestHelper helper) {
        // BAR Legacy has Attack on A, but #39 (lovish) took it off A on purpose: a hold of A waited before panning. Oct 22
        // (lovish, #201: "we dont need f and x, only f"): the Attack order is merged into Fight / Attack-move on F, so no
        // binding is Attack any more and nothing but the pan fires on A
        List<String> problems = new ArrayList<>();
        for (var kb : Keybindings.all())
            if ("key.reignofnether.attack".equals(kb.getTranslationKey()))
                problems.add("the Attack binding must be gone (merged into Fight)");
        if (Keybindings.fight.getDefaultKey() != GLFW.GLFW_KEY_F)
            problems.add("Fight / Attack-move must ship on F, is " + Keybindings.fight.getDefaultKey());
        if (Keybindings.fight.matches(GLFW.GLFW_KEY_A))
            problems.add("A must not fire Fight");
        if (Keybindings.panLeft.getDefaultKey() != GLFW.GLFW_KEY_A)
            problems.add("A is still pan-left");
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
    public static void ac5_ctrl_a_all_units_and_ctrl_z_same_type(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (ChordKeys.of(GLFW.GLFW_KEY_A, true, false, false) != Chord.SELECT_ALL_UNITS)
            problems.add("Ctrl+A selects all units");
        if (ChordKeys.of(GLFW.GLFW_KEY_Z, true, false, false) != Chord.SELECT_SAME_TYPE)
            problems.add("Ctrl+Z selects the same types");
        if (ChordKeys.of(GLFW.GLFW_KEY_W, true, false, false) != Chord.SELECT_ARMY)
            problems.add("Ctrl+W stays select army");
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
        // Ctrl+Z: own units of the selected types (type = the name without digits here)
        List<String> owned = List.of("knight1", "archer1", "worker1", "knight2", "archer2");
        List<String> sel = List.of("knight9");
        List<String> same = ChordKeys.sameTypes(owned, sel, s -> s.replaceAll("\\d", ""));
        if (!same.equals(List.of("knight1", "knight2")))
            problems.add("Ctrl+Z with a knight selected picks every own knight, got " + same);
        List<String> two = ChordKeys.sameTypes(owned, List.of("archer0", "worker0"), s -> s.replaceAll("\\d", ""));
        if (!two.equals(List.of("archer1", "worker1", "archer2")))
            problems.add("Ctrl+Z with two types picks both, got " + two);
        if (!ChordKeys.sameTypes(owned, List.<String>of(), s -> s).isEmpty())
            problems.add("Ctrl+Z with nothing selected selects nothing");
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
