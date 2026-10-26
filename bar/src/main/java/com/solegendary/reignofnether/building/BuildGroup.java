package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;

import java.util.List;

/**
 * The four tabs of a worker's build menu (design/mockups/mockup_a_tabs.png, groups in design/mockups/groups.md).
 * Every building registered with Factions.registerBuildings names its tab. Slot keys are positional and the same in
 * every tab: the Nth building of a tab is on SLOT_KEYS[N], so only the selected tab's keys are live.
 */
public enum BuildGroup {
    ECONOMY("economy"),
    MILITARY("military"),
    DEFENCE("defence"),
    TECH("tech");

    /** Tab-switch keys: Alt + the first four slot keys (Alt + 1..4 is taken by control groups and hero ranks). */
    public static final List<Keybinding> TAB_KEYS = List.of(
        Keybindings.abilitySlot1, Keybindings.abilitySlot2, Keybindings.abilitySlot3, Keybindings.abilitySlot4);

    /**
     * Row 1: Q Z E R T Y, row 2: U I O L F C, row 3: H J K. None of them is a worker order (X M S B P G N), a worker
     * ability (. and ,), Return Resources (;), V (Call to Arms) or a camera key. The mock-up had ; as the 13th slot;
     * Return Resources owns it, so the third row starts on H.
     */
    public static final List<Keybinding> SLOT_KEYS = List.of(
        Keybindings.abilitySlot1, Keybindings.abilitySlot2, Keybindings.abilitySlot3, Keybindings.abilitySlot4,
        Keybindings.abilitySlot5, Keybindings.abilitySlot6,
        Keybindings.abilitySlot7, Keybindings.abilitySlot8, Keybindings.abilitySlot9, Keybindings.hotkey2,
        Keybindings.hotkey3, Keybindings.hotkey4,
        Keybindings.hotkey10, Keybindings.hotkey6, Keybindings.hotkey7);

    public final String id;

    BuildGroup(String id) {
        this.id = id;
    }

    /** Lang key of the tab's title. */
    public String langKey() {
        return "hud.reignofnether.build_tab." + id;
    }

    /** The key of the nth slot of a tab, or null past the last (click only). */
    public static Keybinding slotKey(int index) {
        return index >= 0 && index < SLOT_KEYS.size() ? SLOT_KEYS.get(index) : null;
    }
}
