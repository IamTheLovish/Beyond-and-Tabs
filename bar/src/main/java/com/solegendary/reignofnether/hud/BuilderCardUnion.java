package com.solegendary.reignofnether.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
}
