package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.CommandCard;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The worker's "Return resources" button was on hotkey5 (;), the metal extractor's build slot on all seven factions
 * (Lane C tester, Oct 21: "the metal extractor's key does nothing"). The button now reads Keybindings.returnResources;
 * this keeps that key off every card it can appear on: the orders, the abilities (a commander's included) and, for a
 * worker, its faction's whole build menu. Also off the camera and attack-move keys.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReturnResourcesKeyGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 60)
    public static void return_resources_key_is_off_every_unit_card_and_faction_build_menu(GameTestHelper helper) {
        Keybinding ret = Keybindings.returnResources;
        int code = ret.getKey();
        int alias = ret.getAliasKey();
        List<String> problems = new ArrayList<>();
        if (code == GLFW.GLFW_KEY_A || code == GLFW.GLFW_KEY_F || code == GLFW.GLFW_KEY_W || code == GLFW.GLFW_KEY_S
                || code == GLFW.GLFW_KEY_D || (code >= GLFW.GLFW_KEY_RIGHT && code <= GLFW.GLFW_KEY_UP))
            problems.add("Return resources is on a key the camera or attack-move owns: " + ret.buttonLabel);
        int checked = 0;
        int workers = 0;
        for (var type : ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (id == null || !ReignOfNether.MOD_ID.equals(id.getNamespace()))
                continue;
            Entity e = null;
            try {
                e = type.create(helper.getLevel());
                if (!(e instanceof Unit unit))
                    continue;
                checked++;
                boolean worker = e instanceof WorkerUnit;
                if (worker) {
                    workers++;
                    // a commander's abilities are added at match start; the worker card then carries them too
                    CommanderServerEvents.ensureAbility(e);
                }
                String who = id.getPath();
                for (var order : CommandCard.orders(e instanceof AttackerUnit, worker, true, false))
                    if (shares(order.key(), code, alias))
                        problems.add(who + ": order " + order + " is on " + ret.buttonLabel);
                var abilities = unit.getAbilities();
                if (abilities != null) {
                    var list = abilities.get();
                    var keys = abilities.effectiveHotkeys();
                    for (int i = 0; i < list.size(); i++)
                        if (shares(keys.get(i), code, alias))
                            problems.add(who + ": ability " + list.get(i).getClass().getSimpleName() + " is on " + ret.buttonLabel);
                }
                if (worker) {
                    var faction = Factions.getFaction(unit);
                    if (faction == null || faction.getBuildHotkeys().isEmpty())
                        problems.add(who + ": no faction build menu to check against");
                    else
                        for (var entry : faction.getBuildHotkeys().entrySet())
                            if (shares(entry.getValue(), code, alias))
                                problems.add(who + ": build " + entry.getKey().structureName + " is on " + ret.buttonLabel);
                }
            } finally {
                if (e != null)
                    e.discard();
            }
        }
        if (workers < 7)
            problems.add("only " + workers + " worker types checked, expected the 7 factions' workers (" + checked + " units)");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(25, problems.size()))));
    }

    private static boolean shares(Keybinding kb, int code, int alias) {
        if (kb == null)
            return false;
        for (int k : new int[] { kb.getKey(), kb.getAliasKey() })
            if (k != -1 && (k == code || k == alias))
                return true;
        return false;
    }
}
