package com.solegendary.reignofnether.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BAR's control groups as pure rules (no Minecraft classes, so JUnit covers them: ControlGroupRulesTest). Sources:
 * BAR's default key set (common/configs/keybind_defaults.json), the Recoil engine's group commands
 * (rts/Game/UI/Groups/GroupHandler.cpp, CUnit::SetGroup) and BAR's Auto Group widget (luaui/Widgets/unit_auto_group.lua):
 * <ul>
 *   <li>N: "group select N" - the selection becomes the group (nothing happens for an empty group);</li>
 *   <li>N, N (double tap): "group focus N" - the camera centres on the group;</li>
 *   <li>Ctrl+N: "group set N" - the group becomes the selection (an empty selection empties it);</li>
 *   <li>Ctrl+Shift+N: "group add N" - the selection joins the group;</li>
 *   <li>Shift+N: "group selectadd N" - the group joins the selection;</li>
 *   <li>Alt+N: "add_to_autogroup N" - the selected unit TYPES are bound to N: every own unit of those types joins
 *       group N and the selection, and new units of those types join N as they are made;</li>
 *   <li>Alt+`: "remove_from_autogroup"; Ctrl+`: "group unset" (the selection leaves its groups).</li>
 * </ul>
 * A unit is in at most one group (CUnit::SetGroup removes it from its old group first).
 */
public final class ControlGroupRules {
    private ControlGroupRules() { }

    // GLFW modifier bits (GLFW_MOD_SHIFT / CONTROL / ALT): either side's key sets them
    public static final int MOD_SHIFT = 0x1;
    public static final int MOD_CONTROL = 0x2;
    public static final int MOD_ALT = 0x4;

    public enum Action { SELECT, SET, ADD, SELECT_ADD, AUTOGROUP, NONE }

    /** What a number key does with these modifiers held (BAR's default key set). */
    public static Action actionFor(boolean ctrl, boolean shift, boolean alt) {
        if (alt)
            return ctrl || shift ? Action.NONE : Action.AUTOGROUP;   // Shift+Alt+N loads an autogroup preset: not built
        if (ctrl)
            return shift ? Action.ADD : Action.SET;
        return shift ? Action.SELECT_ADD : Action.SELECT;
    }

    public static Action actionFor(int glfwMods) {
        return actionFor((glfwMods & MOD_CONTROL) != 0, (glfwMods & MOD_SHIFT) != 0, (glfwMods & MOD_ALT) != 0);
    }

    /** What the deselect key (`) does with these modifiers: BAR's Ctrl+` and Alt+`. */
    public enum UnsetAction { DESELECT, UNSET, REMOVE_AUTOGROUP, NONE }

    public static UnsetAction unsetActionFor(int glfwMods) {
        boolean ctrl = (glfwMods & MOD_CONTROL) != 0, alt = (glfwMods & MOD_ALT) != 0;
        if (ctrl && alt)
            return UnsetAction.NONE;
        if (alt)
            return UnsetAction.REMOVE_AUTOGROUP;
        if (ctrl)
            return UnsetAction.UNSET;
        return UnsetAction.DESELECT;
    }

    /**
     * Puts members into group n of groups (one list per group). Each member leaves every other group first (one group
     * per unit); replace=true is "set" (group n is emptied first), false is "add" (appended, no duplicates).
     */
    public static <T> void assign(List<? extends List<T>> groups, int n, Collection<T> members, boolean replace) {
        Set<T> moving = new HashSet<>(members);
        for (int i = 0; i < groups.size(); i++)
            if (i != n)
                groups.get(i).removeIf(moving::contains);
        List<T> target = groups.get(n);
        if (replace)
            target.clear();
        Set<T> have = new HashSet<>(target);
        for (T m : members)
            if (have.add(m))
                target.add(m);
    }

    /** Takes members out of every group ("group unset"). */
    public static <T> void unassign(List<? extends List<T>> groups, Collection<T> members) {
        Set<T> leaving = new HashSet<>(members);
        for (List<T> g : groups)
            g.removeIf(leaving::contains);
    }

    /** The group a member is in, or -1. */
    public static <T> int groupOf(List<? extends List<T>> groups, T member) {
        for (int i = 0; i < groups.size(); i++)
            if (groups.get(i).contains(member))
                return i;
        return -1;
    }

    /** "selectadd": the selection followed by the group's members it does not have yet, order kept. */
    public static <T> List<T> selectAdd(List<T> selection, List<T> group) {
        List<T> out = new ArrayList<>(selection);
        Set<T> have = new HashSet<>(selection);
        for (T m : group)
            if (have.add(m))
                out.add(m);
        return out;
    }

    /** Double tap of the same group key inside the window (BAR's "N,N" key chord); a different key starts over. */
    public static final class TapTracker {
        private int lastGroup = -1;
        private long lastMs = Long.MIN_VALUE / 2;

        public boolean press(int group, long nowMs, long windowMs) {
            boolean dbl = group == lastGroup && nowMs - lastMs <= windowMs;
            lastGroup = dbl ? -1 : group;   // a third tap is a new first tap, as a chord is
            lastMs = nowMs;
            return dbl;
        }

        public void reset() {
            lastGroup = -1;
        }
    }

    /**
     * Unit (or building) type -> autogroup number, saved across matches (BAR's Auto Group widget keeps them in its
     * config data). One line per type: "type=n".
     */
    public static final class AutoGroups {
        private final Map<String, Integer> byType = new LinkedHashMap<>();

        public Integer groupFor(String type) {
            return byType.get(type);
        }

        public void bind(Collection<String> types, int n) {
            for (String t : types)
                byType.put(t, n);
        }

        public void unbind(Collection<String> types) {
            for (String t : types)
                byType.remove(t);
        }

        public Map<String, Integer> view() {
            return java.util.Collections.unmodifiableMap(byType);
        }

        public String serialize() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : byType.entrySet())
                sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
            return sb.toString();
        }

        /** Reads serialize()'s text; a malformed line is an error, not skipped (the caller logs it). */
        public static AutoGroups parse(String text) {
            AutoGroups a = new AutoGroups();
            for (String raw : text.split("\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                int eq = line.lastIndexOf('=');
                if (eq <= 0)
                    throw new IllegalArgumentException("autogroup line without '=': " + line);
                int n = Integer.parseInt(line.substring(eq + 1).trim());
                if (n < 0 || n > 9)
                    throw new IllegalArgumentException("autogroup number out of 0-9: " + line);
                a.byType.put(line.substring(0, eq).trim(), n);
            }
            return a;
        }
    }

    /**
     * New members (never seen before) of an autogrouped type that are in no group join their type's group, as BAR's
     * UnitCreated does. A member seen before is left alone, so one the player took out of its group stays out.
     */
    public static <T> void joinNew(List<? extends List<T>> groups, Map<T, String> current, Set<T> seen, AutoGroups auto) {
        for (Map.Entry<T, String> e : current.entrySet()) {
            if (!seen.add(e.getKey()))
                continue;
            Integer n = auto.groupFor(e.getValue());
            if (n != null && n >= 0 && n < groups.size() && groupOf(groups, e.getKey()) < 0)
                groups.get(n).add(e.getKey());
        }
    }
}
