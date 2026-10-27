package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.keybinds.ChordKeys;
import com.solegendary.reignofnether.keybinds.ChordKeys.Chord;
import com.solegendary.reignofnether.keybinds.KeyConflicts;
import com.solegendary.reignofnether.keybinds.KeybindMigration;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.keybinds.RtsConflictContext;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Tester's BAR selection and camera keys (keybind_defaults.json, Legacy): Ctrl+Q keeps half the selection
 * (SelectPart_50), Alt+Q keeps the damaged units (below 60% health), Ctrl+. / Ctrl+, centre the camera on the next /
 * previous selected unit (cycleselected; plain . and , are the idle-worker cycle and build slot 9 here), and the camera
 * anchors on F5-F8 (Ctrl stores; F1-F4 stay hide HUD / screenshot / debug / stats). The selection rules run on real
 * entities; the client glue (HelperButtons.runChord, OrthoviewClientEvents) needs a client and is not run here.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SelectCameraGameTests {

    private static void verdict(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    private static List<LivingEntity> zombies(GameTestHelper helper, float... health) {
        List<LivingEntity> out = new ArrayList<>();
        for (float h : health) {
            LivingEntity z = EntityType.ZOMBIE.create(helper.getLevel());
            z.setHealth(h * z.getMaxHealth());
            out.add(z);
        }
        return out;
    }

    /** No two Ctrl chords on one (key, shift), the new Ctrl+Q / Ctrl+. / Ctrl+, included. */
    private static void noOverlap(List<String> problems) {
        for (int i = 0; i < ChordKeys.ALL.size(); i++)
            for (int j = i + 1; j < ChordKeys.ALL.size(); j++) {
                ChordKeys.Def a = ChordKeys.ALL.get(i), b = ChordKeys.ALL.get(j);
                if (a.key() == b.key() && (a.shift() == null || b.shift() == null || a.shift().equals(b.shift())))
                    problems.add(a.label() + " and " + b.label() + " overlap");
            }
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void ac1_ctrl_q_keeps_every_other_selected_unit(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (ChordKeys.of(GLFW.GLFW_KEY_Q, true, false, false) != Chord.SELECT_HALF)
            problems.add("Ctrl+Q is not select half");
        if (ChordKeys.of(GLFW.GLFW_KEY_Q, false, false, false) != Chord.NONE)
            problems.add("plain Q must stay ability slot 1");
        noOverlap(problems);
        List<LivingEntity> sel = zombies(helper, 1, 1, 1, 1, 1);
        List<LivingEntity> half = ChordKeys.half(sel);
        if (!half.equals(List.of(sel.get(0), sel.get(2), sel.get(4))))
            problems.add("5 selected: keep 1st, 3rd, 5th, got " + half.size());
        if (ChordKeys.half(sel.subList(0, 1)).size() != 1)
            problems.add("1 selected: keep it");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void ac2_alt_q_keeps_the_damaged_units(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (ChordKeys.of(GLFW.GLFW_KEY_Q, false, false, true) != Chord.SELECT_DAMAGED)
            problems.add("Alt+Q is not select damaged");
        if (ChordKeys.of(GLFW.GLFW_KEY_Q, true, false, true) != Chord.NONE)
            problems.add("Ctrl+Alt+Q must be no chord");
        List<LivingEntity> sel = zombies(helper, 1.0f, 0.5f, 0.6f, 0.2f);
        List<LivingEntity> hurt = ChordKeys.damaged(sel, u -> u.getHealth() / u.getMaxHealth());
        if (!hurt.equals(List.of(sel.get(1), sel.get(3))))
            problems.add("keep the units below 60% health (50%, 20%), got " + hurt.size());
        if (!ChordKeys.damaged(zombies(helper, 1.0f, 0.6f), u -> u.getHealth() / u.getMaxHealth()).isEmpty())
            problems.add("none damaged must leave the selection unchanged (empty result)");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void ac3_ctrl_period_and_comma_cycle_the_selected_units(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (ChordKeys.of(GLFW.GLFW_KEY_PERIOD, true, false, false) != Chord.CYCLE_SELECTED_NEXT)
            problems.add("Ctrl+. is not next selected");
        if (ChordKeys.of(GLFW.GLFW_KEY_COMMA, true, false, false) != Chord.CYCLE_SELECTED_PREV)
            problems.add("Ctrl+, is not previous selected");
        if (Keybindings.idleWorker.getDefaultKey() != GLFW.GLFW_KEY_PERIOD || Keybindings.hotkey9.getDefaultKey() != GLFW.GLFW_KEY_COMMA)
            problems.add("plain . / , must stay the idle-worker cycle and build slot 9");
        // three selected: next goes 0, 1, 2, 0; previous from 0 goes to 2
        int at = -1;
        StringBuilder walk = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            at = ChordKeys.cycleIndex(at, 3, 1);
            walk.append(at);
        }
        if (!"0120".contentEquals(walk))
            problems.add("next walk is " + walk);
        if (ChordKeys.cycleIndex(0, 3, -1) != 2)
            problems.add("previous from the first is the last");
        if (ChordKeys.cycleIndex(0, 0, 1) != -1)
            problems.add("empty selection: nothing to look at");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void ac4_camera_anchors_on_f5_to_f8(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int[] want = { GLFW.GLFW_KEY_F5, GLFW.GLFW_KEY_F6, GLFW.GLFW_KEY_F7, GLFW.GLFW_KEY_F8 };
        for (int i = 0; i < 4; i++)
            if (Keybindings.cameraAnchors[i].getDefaultKey() != want[i])
                problems.add("camera anchor " + (i + 1) + " is not on F" + (i + 5));
        if (Keybindings.getFnum(1).getDefaultKey() != GLFW.GLFW_KEY_F1 || Keybindings.statsTable.getAliasKey() != GLFW.GLFW_KEY_F4)
            problems.add("F1 (hide HUD) and F4 (stats) must stay");
        if (RtsConflictContext.CAMERA_ANCHOR.conflicts(KeyConflictContext.IN_GAME))
            problems.add("anchors share the IN_GAME context of fn_5..fn_8: Controls would mark them red");
        int f6 = Keybindings.getFnum(6).getDefaultKey(), f7 = Keybindings.getFnum(7).getDefaultKey(), f8 = Keybindings.getFnum(8).getDefaultKey();
        if (!KeyConflicts.anchorYields(f6, true, f6, f7, false, f8) || KeyConflicts.anchorYields(f6, false, f6, f7, false, f8))
            problems.add("F6 must answer an open resign vote and jump otherwise");
        if (!KeyConflicts.anchorYields(f8, false, f6, f7, true, f8))
            problems.add("F8 must cycle a spectator's view");
        List<KeybindMigration.Move> moves = KeybindMigration.plan(2,
            name -> name.startsWith("key.reignofnether.camera_anchor_") ? GLFW.GLFW_KEY_UNKNOWN : KeybindMigration.CUSTOM);
        if (moves.size() != 4)
            problems.add("layout 3 must move the 4 saved unbound anchors, moved " + moves.size());
        if (!KeyConflicts.panOverlaps(name -> {
                for (Keybinding kb : Keybindings.all())
                    if (name.equals(kb.getTranslationKey()))
                        return kb.getDefaultKey();
                return KeybindMigration.CUSTOM;
            }).isEmpty())
            problems.add("a default sits on a pan key");
        com.google.gson.JsonObject lang = SkirmishGameTests.enUsLang();
        for (String k : new String[] { "select_half_ctrl_q", "select_damaged_alt_q", "cycle_selected",
                "camera_anchor_jump", "camera_anchor_store" })
            if (!lang.has("onboarding.reignofnether.controls." + k))
                problems.add("controls sheet row " + k + " has no en_us text");
        verdict(helper, problems);
    }
}
