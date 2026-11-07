package com.solegendary.reignofnether.keybinds;

import com.solegendary.reignofnether.hud.CommandCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * No two default keys collide inside one state of the command card, and none with a HUD-wide binding that does not
 * yield to the card. States, not Forge contexts: the unit card of a fighter, a worker at home (no build tab open) and
 * an open build tab. The unit-by-unit check against every real card stays
 * SkirmishGameTests.no_two_buttons_on_a_command_card_share_a_hotkey.
 */
class CardContextKeysTest {

    // HUD-wide: act whatever the card shows
    private static final List<Keybinding> HUD_WIDE = List.of(
        Keybindings.selectCommander, Keybindings.selectArmy, Keybindings.chat, Keybindings.commandBar, Keybindings.deselect);

    private static void assertDistinct(String state, List<Keybinding> keys) {
        Map<Integer, Keybinding> seen = new HashMap<>();
        for (Keybinding kb : keys) {
            int code = kb.getDefaultKey();
            if (code == -1)
                continue;
            Keybinding prev = seen.put(code, kb);
            assertNull(prev, state + ": " + (prev == null ? "" : prev.getTranslationKey()) + " and " + kb.getTranslationKey()
                + " both on key " + code);
        }
    }

    private static List<Keybinding> withHudWide(List<Keybinding> card) {
        List<Keybinding> all = new ArrayList<>(card);
        all.addAll(HUD_WIDE);
        return all;
    }

    private static List<Keybinding> orderKeys(boolean attacker, boolean worker) {
        List<Keybinding> keys = new ArrayList<>();
        for (CommandCard.Order o : CommandCard.orders(attacker, worker, true, false))
            if (o.key() != null)
                keys.add(o.key());
        return keys;
    }

    @Test
    void aFightersCardHasDistinctKeysAndAFireStateKey() {
        assertNotNull(CommandCard.Order.STANCE.key(), "the fire state has a key (BAR sc_l firestate)");
        List<Keybinding> card = orderKeys(true, false);
        assertTrue(card.contains(CommandCard.Order.STANCE.key()), "a fighter's card shows the fire state");
        card.addAll(List.of(Keybindings.abilitySlot1, Keybindings.abilitySlot2, Keybindings.abilitySlot3, Keybindings.abilitySlot4,
            Keybindings.abilitySlot5, Keybindings.abilitySlot6, Keybindings.abilitySlot7));
        assertDistinct("fighter card", withHudWide(card));
    }

    @Test
    void aWorkerAtHomeHasDistinctKeys() {
        List<Keybinding> card = orderKeys(true, true);
        // ability slot 2 is Z, the Economy tab key: the tab key wins while the build menu shows (BuildTabs.onKey; the
        // commander's added abilities stay off it), so slot 2 is not a separate button in this state
        card.addAll(List.of(Keybindings.abilitySlot1, Keybindings.abilitySlot3, Keybindings.abilitySlot4,
            Keybindings.abilitySlot5, Keybindings.abilitySlot6));
        card.addAll(List.of(Keybindings.buildTabEconomy, Keybindings.buildTabMilitary, Keybindings.buildTabDefence, Keybindings.buildTabTech));
        assertDistinct("worker at home", withHudWide(card));
    }

    @Test
    void anOpenTabHasTwelveDistinctGridKeys() {
        assertEquals(12, Keybindings.buildGrid.length);
        assertDistinct("open build tab", withHudWide(List.of(Keybindings.buildGrid)));
    }
}
