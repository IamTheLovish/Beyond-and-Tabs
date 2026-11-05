package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.hud.ControlGroupRules.Action;
import com.solegendary.reignofnether.hud.ControlGroupRules.AutoGroups;
import com.solegendary.reignofnether.hud.ControlGroupRules.UnsetAction;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.solegendary.reignofnether.hud.ControlGroupRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** BAR's control-group keys (ControlGroupRules): one test per behaviour. */
class ControlGroupRulesTest {

    private static List<List<Integer>> groups() {
        List<List<Integer>> g = new ArrayList<>();
        for (int i = 0; i < 10; i++)
            g.add(new ArrayList<>());
        return g;
    }

    @Test
    void numberKeyModifiersMapToBarActions() {
        assertEquals(Action.SELECT, actionFor(0));
        assertEquals(Action.SET, actionFor(MOD_CONTROL));
        assertEquals(Action.ADD, actionFor(MOD_CONTROL | MOD_SHIFT));
        assertEquals(Action.SELECT_ADD, actionFor(MOD_SHIFT));
        assertEquals(Action.AUTOGROUP, actionFor(MOD_ALT));
        assertEquals(Action.NONE, actionFor(MOD_ALT | MOD_SHIFT));   // autogroup presets: not built
        assertEquals(Action.NONE, actionFor(MOD_ALT | MOD_CONTROL));
    }

    @Test
    void deselectKeyModifiersMapToUnsetAndRemoveAutogroup() {
        assertEquals(UnsetAction.DESELECT, unsetActionFor(0));
        assertEquals(UnsetAction.UNSET, unsetActionFor(MOD_CONTROL));
        assertEquals(UnsetAction.REMOVE_AUTOGROUP, unsetActionFor(MOD_ALT));
    }

    @Test
    void setReplacesTheGroupAndMovesUnitsOutOfOtherGroups() {
        List<List<Integer>> g = groups();
        assign(g, 1, List.of(10, 11), true);
        assign(g, 2, List.of(11, 12), true);
        assertEquals(List.of(10), g.get(1));
        assertEquals(List.of(11, 12), g.get(2));
        assign(g, 2, List.of(13), true);
        assertEquals(List.of(13), g.get(2));
        assertEquals(1, groupOf(g, 10));
        assertEquals(-1, groupOf(g, 12));
    }

    @Test
    void setWithEmptySelectionEmptiesTheGroup() {
        List<List<Integer>> g = groups();
        assign(g, 3, List.of(1, 2), true);
        assign(g, 3, List.of(), true);
        assertTrue(g.get(3).isEmpty());
    }

    @Test
    void addAppendsWithoutDuplicates() {
        List<List<Integer>> g = groups();
        assign(g, 4, List.of(1, 2), true);
        assign(g, 4, List.of(2, 3), false);
        assertEquals(List.of(1, 2, 3), g.get(4));
    }

    @Test
    void unsetTakesMembersOutOfEveryGroup() {
        List<List<Integer>> g = groups();
        assign(g, 1, List.of(1, 2), true);
        assign(g, 2, List.of(3), true);
        unassign(g, List.of(2, 3));
        assertEquals(List.of(1), g.get(1));
        assertTrue(g.get(2).isEmpty());
    }

    @Test
    void selectAddAppendsGroupToSelectionInOrder() {
        assertEquals(List.of(5, 1, 2), selectAdd(List.of(5, 1), List.of(1, 2)));
    }

    @Test
    void doubleTapSameKeyInsideWindowFocuses() {
        TapTracker t = new TapTracker();
        assertFalse(t.press(1, 1000, 400));
        assertTrue(t.press(1, 1300, 400));
        assertFalse(t.press(1, 1400, 400));   // third tap starts over
        assertFalse(t.press(2, 1500, 400));
        assertFalse(t.press(1, 1600, 400));   // other key in between
        assertFalse(t.press(1, 2100, 400));   // too slow
    }

    @Test
    void autogroupsRoundTripAndRejectBadLines() {
        AutoGroups a = new AutoGroups();
        a.bind(List.of("unit:reignofnether:zombie_unit", "building:reignofnether:barracks"), 3);
        a.bind(List.of("unit:reignofnether:villager_unit"), 7);
        AutoGroups b = AutoGroups.parse(a.serialize());
        assertEquals(a.view(), b.view());
        a.unbind(List.of("unit:reignofnether:villager_unit"));
        assertNull(a.groupFor("unit:reignofnether:villager_unit"));
        assertThrows(IllegalArgumentException.class, () -> AutoGroups.parse("nonsense"));
        assertThrows(IllegalArgumentException.class, () -> AutoGroups.parse("t=12"));
    }

    @Test
    void newUnitsOfABoundTypeJoinTheirGroupOnlyOnce() {
        List<List<Integer>> g = groups();
        AutoGroups a = new AutoGroups();
        a.bind(List.of("zombie"), 5);
        Set<Integer> seen = new HashSet<>();
        Map<Integer, String> now = new LinkedHashMap<>();
        now.put(1, "zombie");
        now.put(2, "villager");
        joinNew(g, now, seen, a);
        assertEquals(List.of(1), g.get(5));
        unassign(g, List.of(1));          // the player took it out
        joinNew(g, now, seen, a);
        assertTrue(g.get(5).isEmpty(), "a unit taken out of its group stays out");
        assign(g, 2, List.of(3), true);   // already grouped units are not moved
        now.put(3, "zombie");
        joinNew(g, now, seen, a);
        assertEquals(List.of(3), g.get(2));
    }
}
