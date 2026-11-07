package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** BAR Grid key rules that need the unit registry (moved out of SkirmishGameTests: no new tests in the shared hot file). */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BarGridKeysGameTests {

    /**
     * "." and "," are BAR's selection cycle (cycleselected next / prev, BAC-007b 2a): no command card button (orders,
     * abilities) and no other HUD binding may ship on them, or the cycle would fire with a card action.
     */
    @GameTest(template = SkirmishGameTests.ARENA)
    public static void no_card_or_hud_button_ships_on_dot_or_comma(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (var type : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null || !ReignOfNether.MOD_ID.equals(key.getNamespace()))
                continue;
            net.minecraft.world.entity.Entity e = null;
            try {
                e = type.create(level);
                if (!(e instanceof com.solegendary.reignofnether.unit.interfaces.Unit unit))
                    continue;
                checked++;
                java.util.List<com.solegendary.reignofnether.keybinds.Keybinding> keys = new ArrayList<>();
                for (var order : com.solegendary.reignofnether.hud.CommandCard.orders(
                        e instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit,
                        e instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit, unit.canGarrison(), false))
                    keys.add(order.key());
                var abilities = unit.getAbilities();
                if (abilities != null)
                    keys.addAll(abilities.effectiveHotkeys());
                for (var kb : keys)
                    if (kb != null && (isDotOrComma(kb.getKey()) || isDotOrComma(kb.getAliasKey())))
                        problems.add(key.getPath() + ": a card button is on " + kb.buttonLabel);
            } catch (Exception ex) {
                problems.add(key.getPath() + ": threw " + ex);
            } finally {
                if (e != null)
                    e.discard();
            }
        }
        if (checked < 60)
            problems.add("only " + checked + " unit types were checked - the registry scan is broken");
        // building productions, the idle buttons and the rest of the HUD: every other binding keeps off both keys
        for (var kb : com.solegendary.reignofnether.keybinds.Keybindings.all()) {
            String name = kb.getTranslationKey();
            if (name == null || name.equals(com.solegendary.reignofnether.keybinds.Keybindings.tab.getTranslationKey())
                    || name.equals("key.reignofnether.cycle_prev"))
                continue;
            if (isDotOrComma(kb.getDefaultKey()) || isDotOrComma(kb.getAliasKey()))
                problems.add(name + " ships on . or ,");
        }
        if (!problems.isEmpty())
            helper.fail(problems.size() + " clash(es): " + String.join("; ", problems.subList(0, Math.min(25, problems.size()))));
        else
            helper.succeed();
    }

    private static boolean isDotOrComma(int glfwKey) {
        return glfwKey == org.lwjgl.glfw.GLFW.GLFW_KEY_PERIOD || glfwKey == org.lwjgl.glfw.GLFW.GLFW_KEY_COMMA;
    }
}
