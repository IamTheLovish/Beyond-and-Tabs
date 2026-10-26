package com.solegendary.reignofnether.hud;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The mixed-builder card on a tabbed build menu (BuilderCardUnion.addedByTab / tabCells, BuildTabs): slot keys repeat
 * in every tab, so a clash counts only inside a tab; an added building stays in its own tab; a second building on a
 * held slot goes keyless after the tab's last slot.
 */
class BuilderCardUnionTabsTest {

    /** A card button: building id (null = an ability), key (-1 = none), tab (null = untabbed), slot (-1 = none). */
    record B(String id, int key, String tab, int slot) { }

    static List<BuilderCardUnion.Added<B>> added(List<B> primary, List<List<B>> others) {
        return BuilderCardUnion.addedByTab(primary, others, B::id, B::key, B::tab, Set.of());
    }

    /** The cells of one tab as the HUD lays it out: primary's buttons of the tab, then the added ones (keyless = no slot). */
    static int[] cells(List<B> primary, List<BuilderCardUnion.Added<B>> added, String tab) {
        List<Integer> slots = new ArrayList<>();
        for (B b : primary)
            if (tab.equals(b.tab()))
                slots.add(b.slot());
        for (BuilderCardUnion.Added<B> a : added)
            if (tab.equals(a.item().tab()))
                slots.add(a.keepsKey() ? a.item().slot() : -1);
        return BuilderCardUnion.tabCells(slots.stream().mapToInt(Integer::intValue).toArray());
    }

    @Test
    void sameSlotFromTwoBuilderTypesInOneTabGivesOneKeyedOneAppendedKeyless() {
        // slot 0 of Economy is Q (key 81) for both types; the primary's farm holds it, the other type's mill goes keyless
        List<B> primary = List.of(new B("farm", 81, "economy", 0), new B("hut", 90, "economy", 1));
        List<List<B>> others = List.of(List.of(new B("mill", 81, "economy", 0)));
        List<BuilderCardUnion.Added<B>> added = added(primary, others);
        assertEquals(1, added.size());
        assertEquals("mill", added.get(0).item().id());
        assertEquals(false, added.get(0).keepsKey());
        // farm on cell 0, hut on 1, the keyless mill after the tab's last slot
        assertArrayEquals(new int[] {0, 1, 2}, cells(primary, added, "economy"));
    }

    @Test
    void aBuildingStaysInItsOwnTabAndKeepsItsKeyThere() {
        // Military slot 0 is Q too, but only the selected tab's keys are live: no clash with Economy's Q
        List<B> primary = List.of(new B("farm", 81, "economy", 0));
        List<List<B>> others = List.of(List.of(new B("barracks", 81, "military", 0)));
        List<BuilderCardUnion.Added<B>> added = added(primary, others);
        assertEquals(1, added.size());
        assertEquals("military", added.get(0).item().tab());
        assertEquals(true, added.get(0).keepsKey());
        assertArrayEquals(new int[] {0}, cells(primary, added, "military"));
        assertArrayEquals(new int[] {0}, cells(primary, added, "economy"));
    }

    @Test
    void anUntabbedAbilityKeyClashesInEveryTab() {
        List<B> primary = List.of(new B(null, 88, null, -1), new B("farm", 81, "economy", 0));
        List<List<B>> others = List.of(List.of(new B("tower", 88, "defence", 3)));
        List<BuilderCardUnion.Added<B>> added = added(primary, others);
        assertEquals(1, added.size());
        assertEquals(false, added.get(0).keepsKey());
    }

    @Test
    void aBuildingAlreadyOnTheCardIsNotAddedAgain() {
        List<B> primary = List.of(new B("farm", 81, "economy", 0));
        List<List<B>> others = List.of(List.of(new B("farm", 81, "economy", 0)), List.of(new B("mill", 90, "economy", 1)),
            List.of(new B("mill", 90, "economy", 1)));
        List<BuilderCardUnion.Added<B>> added = added(primary, others);
        assertEquals(1, added.size());
        assertEquals("mill", added.get(0).item().id());
        assertEquals(true, added.get(0).keepsKey());
    }

    @Test
    void aHeldSlotGoesAfterTheLastSlot() {
        assertArrayEquals(new int[] {2, 0, 3, 4}, BuilderCardUnion.tabCells(new int[] {2, 0, 2, -1}));
    }
}
