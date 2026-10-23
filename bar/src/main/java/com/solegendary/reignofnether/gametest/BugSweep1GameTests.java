package com.solegendary.reignofnether.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.keybinds.KeyPress;
import com.solegendary.reignofnether.keybinds.KeybindMigration;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitIdleWorkerClientBoundPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.lwjgl.glfw.GLFW;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bug sweep 1 (Lane C, Oct 22): the rules behind the small user-facing key and text bugs. Each test is named for the
 * bug it keeps fixed; the commander-vs-build-menu card check lives in CommanderKeyGameTests.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BugSweep1GameTests {

    private static void verdict(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** Bug 1: a commander ability on Z / X / C / V would never fire (the build lists own those keys for a builder). */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40)
    public static void bugsweep1_commander_abilities_stay_off_the_build_category_keys(GameTestHelper helper) {
        List<EntityType<?>> workers = List.of(
            EntityRegistrar.VILLAGER_UNIT.get(), EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(), EntityRegistrar.GRUNT_UNIT.get(),
            EntityRegistrar.SEEDSHAPER_UNIT.get(), EntityRegistrar.SHIPWRIGHT_UNIT.get(), EntityRegistrar.GEOMANCER_UNIT.get(),
            EntityRegistrar.TINKER_UNIT.get());
        List<String> problems = new ArrayList<>();
        for (EntityType<?> type : workers) {
            var e = type.create(helper.getLevel());
            if (!(e instanceof Unit unit)) {
                problems.add(type.getDescriptionId() + " could not be created");
                continue;
            }
            try {
                CommanderServerEvents.ensureAbility(e);
                var list = unit.getAbilities().get();
                var keys = unit.getAbilities().effectiveHotkeys();
                for (int i = 0; i < list.size(); i++) {
                    // the Villager's Call to Arms is registered on hotkey 1 but drawn on slot 1 too: judged by CommanderKeyGameTests
                    if (list.get(i) instanceof com.solegendary.reignofnether.ability.abilities.CallToArmsUnit)
                        continue;
                    Keybinding kb = keys.get(i);
                    for (Keybinding cat : Keybindings.buildCategoryKeys())
                        if (kb != null && kb.getKey() != -1 && (kb.getKey() == cat.getDefaultKey() || kb.getAliasKey() == cat.getDefaultKey()))
                            problems.add(type.getDescriptionId() + ": " + list.get(i).getClass().getSimpleName() + " is on " + cat.buttonLabel);
                }
            } finally {
                e.discard();
            }
        }
        verdict(helper, problems);
    }

    /** Bug 2: one key press runs one button, the first in card order. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void bugsweep1_one_key_press_does_one_action(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        // (name, key): an ability and a build slot both on Q, then another button on E
        List<Map.Entry<String, Integer>> card = List.of(
            Map.entry("ability", GLFW.GLFW_KEY_Q), Map.entry("build slot", GLFW.GLFW_KEY_Q), Map.entry("other", GLFW.GLFW_KEY_E));
        var q = KeyPress.firstThatFires(card, b -> b.getValue() == GLFW.GLFW_KEY_Q);
        if (q == null || !q.getKey().equals("ability"))
            problems.add("a press of Q must run only the first button on it (ability), got " + (q == null ? "none" : q.getKey()));
        var e = KeyPress.firstThatFires(card, b -> b.getValue() == GLFW.GLFW_KEY_E);
        if (e == null || !e.getKey().equals("other"))
            problems.add("a press of E must run its one button");
        if (KeyPress.firstThatFires(card, b -> b.getValue() == GLFW.GLFW_KEY_R) != null)
            problems.add("a key nothing is bound to must run nothing");
        if (KeyPress.firstThatFires(List.<String>of(), b -> true) != null)
            problems.add("an empty card must run nothing");
        verdict(helper, problems);
    }

    /** Bug 3: an install that saved select-all on K (before it moved to "-") is moved once; a chosen key is not. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void bugsweep1_select_army_k_is_migrated_once(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String name = Keybindings.selectArmy.getTranslationKey();
        if (KeybindMigration.LAYOUT_VERSION < 2)
            problems.add("LAYOUT_VERSION must be bumped for the select-army row");
        // layout 1 install with K saved: moves to the current default
        var moves = KeybindMigration.plan(1, n -> n.equals(name) ? GLFW.GLFW_KEY_K : KeybindMigration.CUSTOM);
        if (moves.size() != 1 || !moves.get(0).binding().equals(name) || moves.get(0).from() != GLFW.GLFW_KEY_K
                || moves.get(0).to() != Keybindings.selectArmy.getDefaultKey())
            problems.add("select army saved on K must move to its default once, got " + moves);
        // an install that never had a layout (0) gets it too
        if (KeybindMigration.plan(0, n -> n.equals(name) ? GLFW.GLFW_KEY_K : KeybindMigration.CUSTOM).stream()
                .noneMatch(m -> m.binding().equals(name)))
            problems.add("layout 0 must move select army too");
        // already current: nothing; a key the player chose: nothing
        if (!KeybindMigration.plan(KeybindMigration.LAYOUT_VERSION, n -> GLFW.GLFW_KEY_K).isEmpty())
            problems.add("an install at the current layout must not move anything");
        if (KeybindMigration.plan(1, n -> n.equals(name) ? GLFW.GLFW_KEY_J : KeybindMigration.CUSTOM).stream()
                .anyMatch(m -> m.binding().equals(name)))
            problems.add("select army on a key the player chose (J) must not move");
        // and the K build slot itself is never touched
        if (KeybindMigration.plan(1, n -> GLFW.GLFW_KEY_K).stream().anyMatch(m -> m.binding().equals(Keybindings.hotkey7.getTranslationKey())))
            problems.add("build slot 7 must stay on K");
        verdict(helper, problems);
    }

    /** Bug 4: the typed/voice "idle builders" group and the button list both skip the commander. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 60)
    public static void bugsweep1_idle_builder_selection_skips_the_commander(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String owner = "gametest_sweep1_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob builder = IdleWorkerGameTests.spawnWorker(helper, owner, 3, 3, made);
        Mob commander = IdleWorkerGameTests.spawnWorker(helper, owner, 9, 3, made);
        if (builder == null || commander == null) {
            made.forEach(Entity::discard);
            helper.fail("could not create the builder and the commander");
            return;
        }
        CommanderServerEvents.makeCommander(commander);
        helper.succeedWhen(() -> {
            if (!UnitIdleWorkerClientBoundPacket.isSelectableIdleBuilder(builder))
                helper.fail("an idle plain builder must be selectable as an idle builder (is it still moving?)");
            if (UnitIdleWorkerClientBoundPacket.isSelectableIdleBuilder(commander))
                helper.fail("the commander must never be picked by 'select idle builders'");
            made.forEach(Entity::discard);
        });
    }

    /** Bug 7: a button's fallback label is its default key's letter (item 6 sat on B but read V). */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void bugsweep1_fallback_labels_name_their_default_key(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (Keybinding kb : Keybindings.all()) {
            int code = kb.getDefaultKey();
            String label = kb.buttonLabel;
            if (kb.getTranslationKey() != null && label != null && label.length() == 1
                    && code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z && !label.equals(String.valueOf((char) code)))
                problems.add(kb.getTranslationKey() + " is on " + (char) code + " but labelled " + label);
        }
        verdict(helper, problems);
    }

    /** Bug 8: the Halberdier's lore names a halberd, and the Master Mason's lines are short enough to never re-wrap. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void bugsweep1_unit_lore_reads_right(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        try (var in = BugSweep1GameTests.class.getResourceAsStream("/assets/reignofnether/lang/en_us.json")) {
            if (in == null) {
                helper.fail("en_us.json not found");
                return;
            }
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String k : List.of("entity.reignofnether.vindicator_unit.tooltip1", "entity.reignofnether.vindicator_unit.tooltip2")) {
                String s = lang.get(k).getAsString().toLowerCase();
                if (s.contains("axe"))
                    problems.add(k + " still says axe: " + s);
            }
            if (!lang.get("entity.reignofnether.vindicator_unit.tooltip1").getAsString().contains("halberd"))
                problems.add("Halberdier lore should name its halberd");
            // the tooltip wraps at 260 px (MyRenderer.TOOLTIP_MAX_WIDTH): a 44+ character hand-broken line wraps and
            // leaves one word alone on the next line ("and" / "improve", TEST-2026-10-21 weakness 4)
            for (int i = 1; i <= 3; i++) {
                String line = lang.get("entity.reignofnether.villager_unit.tooltip" + i).getAsString();
                if (line.length() > 44)
                    problems.add("Master Mason lore line " + i + " is " + line.length() + " characters: it re-wraps");
                if (line.endsWith(" "))
                    problems.add("Master Mason lore line " + i + " ends in a space");
            }
        } catch (Exception ex) {
            problems.add("could not read the lang file: " + ex);
        }
        verdict(helper, problems);
    }
}
