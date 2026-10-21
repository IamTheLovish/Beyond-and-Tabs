package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.faction.Factions;
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
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A commander's abilities are added at match start (CommanderServerEvents.ensureAbility), after the registry scan in
 * no_two_buttons_on_a_command_card_share_a_hotkey has run, and used to take the slot defaults Q / Z / E / R: six
 * factions had a commander ability on a build-menu key (Lane C tester, Oct 21). This builds the commander of every
 * faction the way a match does and checks its whole card.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class CommanderKeyGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40)
    public static void commander_abilities_share_no_key_with_the_build_menu(GameTestHelper helper) {
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
                String who = type.getDescriptionId();
                Map<Integer, String> taken = new HashMap<>();
                List<String> clashes = new ArrayList<>();
                java.util.function.BiConsumer<Keybinding, String> claim = (kb, what) -> {
                    if (kb == null)
                        return;
                    for (int code : new int[] { kb.getKey(), kb.getAliasKey() }) {
                        if (code == -1)
                            continue;
                        String prev = taken.putIfAbsent(code, what);
                        if (prev != null && !prev.equals(what))
                            clashes.add(prev + " and " + what + " both on " + kb.buttonLabel);
                        if (what.startsWith("ability") && (code == GLFW.GLFW_KEY_A || code == GLFW.GLFW_KEY_F
                                || code == GLFW.GLFW_KEY_W || code == GLFW.GLFW_KEY_S || code == GLFW.GLFW_KEY_D
                                || code >= GLFW.GLFW_KEY_RIGHT && code <= GLFW.GLFW_KEY_UP))
                            clashes.add(what + " is on a key the camera or attack-move owns: " + kb.buttonLabel);
                    }
                };
                for (var order : CommandCard.orders(e instanceof AttackerUnit, e instanceof WorkerUnit, true, false))
                    claim.accept(order.key(), "order " + order);
                var list = unit.getAbilities().get();
                var keys = unit.getAbilities().effectiveHotkeys();
                boolean dgun = false;
                for (int i = 0; i < list.size(); i++) {
                    dgun |= list.get(i) instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun;
                    // the Villager's Call to Arms is drawn on hotkey 1 (HudClientEvents)
                    boolean callToArms = list.get(i) instanceof com.solegendary.reignofnether.ability.abilities.CallToArmsUnit;
                    claim.accept(callToArms ? Keybindings.hotkey1 : keys.get(i), "ability " + list.get(i).getClass().getSimpleName());
                }
                var faction = Factions.getFaction(unit);
                if (faction == null || faction.getBuildHotkeys().isEmpty())
                    problems.add(who + ": no faction build menu to check against");
                else
                    for (var entry : faction.getBuildHotkeys().entrySet())
                        claim.accept(entry.getValue(), "build " + entry.getKey().structureName);
                if (!dgun)
                    problems.add(who + ": the commander got no D-gun");
                for (String c : clashes)
                    problems.add(who + ": " + c);
            } finally {
                e.discard();
            }
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(25, problems.size()))));
    }
}
