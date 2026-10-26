package com.solegendary.reignofnether.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * BAR mixed builder selection (tester: "when you have multiple builders including different types and tell them to
 * build a structure it only sends one to do it"; notes/plans/bar-mex-blueprints.md item C): the command card showed
 * only the build menu of hudSelectedEntity, so reaching another type's building meant sub-selecting that type, which
 * dropped every other builder from the order. With several builder types selected the card now shows the union of
 * their build menus.
 * <p>
 * Pure rule, no client classes (GameTested in BuilderCardUnionGameTests): given the primary card (hudSelectedEntity's
 * buttons) and, in selection order, the other builder types' building buttons, returns the buttons to add after the
 * primary ones:
 * <ul>
 *   <li>a building already on the card (same id) is not added again, nor twice from two other types;</li>
 *   <li>order: the other types in selection order, each in its own menu order;</li>
 *   <li>an added button keeps its key only if no button already on the card (the reserved keys, the primary buttons,
 *       the buttons added before it) uses that key; otherwise it is shown without a key (clicking still works).</li>
 * </ul>
 */
public final class BuilderCardUnion {
    private BuilderCardUnion() { }

    /** keyOf's value for a button without a hotkey. */
    public static final int NO_KEY = -1;

    /** A button to add to the card, and whether it keeps its own hotkey. */
    public record Added<T>(T item, boolean keepsKey) { }

    /**
     * @param primary      the card's own buttons (hudSelectedEntity's); idOf may return null for non-building buttons
     * @param others       the other builder types' building buttons, one list per type, in selection order
     * @param idOf         the building id of a button (null = not a building button: never added from others)
     * @param keyOf        the button's hotkey, or {@link #NO_KEY}
     * @param reservedKeys keys already used on the card besides primary's (the order buttons), may be empty
     */
    public static <T> List<Added<T>> added(List<T> primary, List<? extends List<T>> others, Function<T, String> idOf,
                                           ToIntFunction<T> keyOf, Collection<Integer> reservedKeys) {
        Set<String> ids = new HashSet<>();
        Set<Integer> usedKeys = new HashSet<>(reservedKeys);
        for (T t : primary) {
            String id = idOf.apply(t);
            if (id != null)
                ids.add(id);
            int k = keyOf.applyAsInt(t);
            if (k != NO_KEY)
                usedKeys.add(k);
        }
        List<Added<T>> out = new ArrayList<>();
        for (List<T> menu : others) {
            for (T t : menu) {
                String id = idOf.apply(t);
                if (id == null || !ids.add(id))
                    continue;
                int k = keyOf.applyAsInt(t);
                boolean keeps = k != NO_KEY && usedKeys.add(k);
                out.add(new Added<>(t, keeps));
            }
        }
        return out;
    }

    /**
     * {@link #added} for a tabbed build menu (BuildTabs): slot keys are the same in every tab and only the selected
     * tab's are live, so a key clashes only with the buttons of its own tab and with the untabbed ones (tabOf null:
     * the unit's own abilities, always on the card). Every added button stays in its own tab (the caller keeps its
     * tab). A building already on the card in any tab is not added again. The result is in the order of
     * {@link #added}: the other types in selection order, each in its own menu order.
     *
     * @param tabOf the tab of a button, or null for an untabbed one
     */
    public static <T> List<Added<T>> addedByTab(List<T> primary, List<? extends List<T>> others,
                                                Function<T, String> idOf, ToIntFunction<T> keyOf,
                                                Function<T, ?> tabOf, Collection<Integer> reservedKeys) {
        Set<String> primaryIds = new HashSet<>();
        List<T> untabbed = new ArrayList<>();
        for (T t : primary) {
            String id = idOf.apply(t);
            if (id != null)
                primaryIds.add(id);
            if (tabOf.apply(t) == null)
                untabbed.add(t);
        }
        // the other types' buttons per tab, in first-seen tab order (LinkedHashMap takes the null tab too)
        Map<Object, List<List<T>>> othersByTab = new LinkedHashMap<>();
        for (int m = 0; m < others.size(); m++) {
            for (T t : others.get(m)) {
                String id = idOf.apply(t);
                if (id == null || primaryIds.contains(id))
                    continue;
                List<List<T>> menus = othersByTab.computeIfAbsent(tabOf.apply(t), k -> new ArrayList<>());
                while (menus.size() <= m)
                    menus.add(new ArrayList<>());
                menus.get(m).add(t);
            }
        }
        Map<T, Boolean> keeps = new IdentityHashMap<>();
        for (Map.Entry<Object, List<List<T>>> e : othersByTab.entrySet()) {
            List<T> tabPrimary = new ArrayList<>(untabbed);
            if (e.getKey() != null)
                for (T t : primary)
                    if (Objects.equals(tabOf.apply(t), e.getKey()))
                        tabPrimary.add(t);
            for (Added<T> a : added(tabPrimary, e.getValue(), idOf, keyOf, reservedKeys))
                keeps.put(a.item(), a.keepsKey());
        }
        // back in selection / menu order; a building offered in two tabs by two types is added once (the first)
        List<Added<T>> out = new ArrayList<>();
        Set<String> addedIds = new HashSet<>();
        for (List<T> menu : others)
            for (T t : menu) {
                Boolean k = keeps.get(t);
                if (k != null && addedIds.add(idOf.apply(t)))
                    out.add(new Added<>(t, k));
            }
        return out;
    }

    /**
     * Grid cell of each button of one tab, from its slot (-1 = none): the slot when no earlier button holds it, else
     * after the tab's last slot, in order. A keyless copy from another builder type (slot -1) and a second building
     * on an already held slot both go there, so no two buttons share a cell.
     */
    public static int[] tabCells(int[] slots) {
        int next = 0;
        for (int s : slots)
            if (s >= 0)
                next = Math.max(next, s + 1);
        Set<Integer> held = new HashSet<>();
        int[] out = new int[slots.length];
        for (int i = 0; i < slots.length; i++)
            out[i] = slots[i] >= 0 && held.add(slots[i]) ? slots[i] : next++;
        return out;
    }
}
