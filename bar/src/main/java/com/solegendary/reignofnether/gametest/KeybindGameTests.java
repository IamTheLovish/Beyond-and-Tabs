package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Default keys of the always-present HUD buttons never share a key with the command card's slots: "select all military
 * units" sat on build slot 7 (K), so K both picked a building and selected the army (Lane C tester, Oct 21).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class KeybindGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void select_army_key_is_off_the_card_slots(GameTestHelper helper) {
        Keybinding[] slots = {
            Keybindings.hotkey1, Keybindings.hotkey2, Keybindings.hotkey3, Keybindings.hotkey4, Keybindings.hotkey5,
            Keybindings.hotkey6, Keybindings.hotkey7, Keybindings.hotkey8, Keybindings.hotkey9, Keybindings.hotkey10,
            Keybindings.hotkey11, Keybindings.hotkey12,
            Keybindings.abilitySlot1, Keybindings.abilitySlot2, Keybindings.abilitySlot3, Keybindings.abilitySlot4,
            Keybindings.abilitySlot5, Keybindings.abilitySlot6, Keybindings.abilitySlot7, Keybindings.abilitySlot8,
            Keybindings.abilitySlot9, Keybindings.abilitySlot10 };
        List<String> problems = new ArrayList<>();
        int army = Keybindings.selectArmy.getDefaultKey();
        for (Keybinding s : slots)
            if (s.getDefaultKey() == army)
                problems.add("select army shares its default key with " + s.getTranslationKey());
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
